<#
.SYNOPSIS
    Prunes stale/merged Git branches and completed AI agent worktrees.

.DESCRIPTION
    Safely cleans up the local (and optionally remote) repository state by:
    1. Pruning stale remote-tracking references (`git fetch --prune`).
    2. Identifying and removing AI agent worktrees (Antigravity, Antigravity CLI,
       Copilot, Claude, Cursor) that have already been utilized (merged into base
       branch, zero commits ahead, squash-merged PR, upstream gone, or stale).
    3. Running `git worktree prune` to clean up dangling worktree metadata.
    4. Deleting local branches that are merged into the base branch, squash-merged
       via GitHub PR, have a `[gone]` upstream, or are older than `-StaleDays`.
    5. Optionally deleting merged/stale remote branches when `-IncludeRemote` is set.

    Protected branches (`master`, `main`, `beta`, `STABLE-RELEASE`, `Experimental`,
    `Feature-Experimentations`, and any currently checked-out branch) are never
    deleted. Dirty worktrees are skipped unless `-Force` is specified.

.PARAMETER BaseBranch
    The base branch to check merge status against. Defaults to "master".

.PARAMETER Remote
    The Git remote name to prune and compare against. Defaults to "origin".

.PARAMETER StaleDays
    Number of days since the last commit after which a branch or AI worktree
    is considered stale. Defaults to 30.

.PARAMETER DryRun
    Preview all actions without modifying branches, worktrees, or remotes.

.PARAMETER IncludeStale
    Include branches whose last commit is older than `-StaleDays` even if not
    yet merged. Defaults to `$true`. Pass `-IncludeStale:$false` to only remove
    merged, squash-merged, and `[gone]` branches.

.PARAMETER IncludeRemote
    Also delete merged or stale branches on `-Remote` (excluding protected
    branches). Defaults to `$false`.

.PARAMETER Force
    Force removal of dirty AI worktrees (with uncommitted/untracked changes)
    and force-delete unmerged stale branches.

.PARAMETER ProtectedBranches
    Array of branch names that must never be deleted.

.EXAMPLE
    .\scripts\prune-branches-and-worktrees.ps1 -DryRun
    Previews all AI worktrees and branches that would be pruned without changing anything.

.EXAMPLE
    .\scripts\prune-branches-and-worktrees.ps1
    Prunes remote-tracking refs, removes utilized AI worktrees, and deletes merged/stale local branches.

.EXAMPLE
    .\scripts\prune-branches-and-worktrees.ps1 -StaleDays 14 -IncludeRemote -WhatIf
    Previews cleanup including 14-day stale branches and remote branches.
#>

[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'Medium')]
param (
    [Parameter(Mandatory = $false)]
    [string]$BaseBranch = "master",

    [Parameter(Mandatory = $false)]
    [string]$Remote = "origin",

    [Parameter(Mandatory = $false)]
    [int]$StaleDays = 30,

    [Parameter(Mandatory = $false)]
    [switch]$DryRun,

    [Parameter(Mandatory = $false)]
    [bool]$IncludeStale = $true,

    [Parameter(Mandatory = $false)]
    [switch]$IncludeRemote,

    [Parameter(Mandatory = $false)]
    [switch]$Force,

    [Parameter(Mandatory = $false)]
    [string[]]$ProtectedBranches = @(
        "master",
        "main",
        "beta",
        "STABLE-RELEASE",
        "Experimental",
        "Feature-Experimentations"
    )
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$isSimulation = $DryRun.IsPresent -or $WhatIfPreference

function Write-Section {
    param([string]$Title)
    Write-Host ""
    Write-Host "=== $Title ===" -ForegroundColor Cyan
}

function Write-ActionItem {
    param(
        [string]$Status,
        [string]$Message,
        [string]$Color = "Gray"
    )
    $prefix = if ($isSimulation) { "[DRY-RUN] " } else { "" }
    Write-Host "  $prefix[$Status] $Message" -ForegroundColor $Color
}

function Test-IsAiWorktree {
    param(
        [string]$WorktreePath,
        [string]$BranchName
    )
    $normalizedPath = $WorktreePath -replace '\\', '/'
    $aiPathPatterns = @(
        '\.worktrees/',
        '/\.gemini/antigravity/worktrees/',
        '/\.gemini/antigravity-cli/worktrees/',
        '/\.claude/worktrees/',
        '/\.cursor/worktrees/'
    )
    foreach ($pattern in $aiPathPatterns) {
        if ($normalizedPath -match $pattern) {
            return $true
        }
    }
    if ($BranchName -match '^copilot/worktree-') {
        return $true
    }
    return $false
}

function Get-GitWorktrees {
    $raw = git worktree list --porcelain 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to list git worktrees: $raw"
    }

    $worktrees = @()
    $current = $null
    foreach ($line in $raw) {
        if ($line -match '^worktree\s+(.+)$') {
            if ($null -ne $current) {
                $worktrees += [PSCustomObject]$current
            }
            $current = @{
                Path     = $Matches[1].Trim()
                Head     = ""
                Branch   = ""
                Detached = $false
                Prunable = $false
            }
        }
        elseif ($null -ne $current) {
            if ($line -match '^HEAD\s+(.+)$') {
                $current.Head = $Matches[1].Trim()
            }
            elseif ($line -match '^branch\s+refs/heads/(.+)$') {
                $current.Branch = $Matches[1].Trim()
            }
            elseif ($line -eq 'detached') {
                $current.Detached = $true
            }
            elseif ($line -match '^prunable') {
                $current.Prunable = $true
            }
        }
    }
    if ($null -ne $current) {
        $worktrees += [PSCustomObject]$current
    }
    return $worktrees
}

function Get-PrBranchesByState {
    param(
        [ValidateSet("merged", "open")]
        [string]$State
    )
    $branchSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $ghCmd = Get-Command gh -ErrorAction SilentlyContinue
    if ($null -eq $ghCmd) {
        return $branchSet
    }
    try {
        $json = gh pr list --state $State --limit 200 --json headRefName 2>$null
        if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($json)) {
            $prs = $json | ConvertFrom-Json
            foreach ($pr in $prs) {
                if (-not [string]::IsNullOrWhiteSpace($pr.headRefName)) {
                    [void]$branchSet.Add($pr.headRefName)
                }
            }
        }
    }
    catch {
        Write-Verbose "Unable to query GitHub $State PRs via gh CLI: $_"
    }
    return $branchSet
}

function Test-BranchMergedIntoBase {
    param(
        [string]$Ref,
        [string]$BaseRef
    )
    git merge-base --is-ancestor $Ref $BaseRef 2>$null
    return ($LASTEXITCODE -eq 0)
}

function Get-CommitsAhead {
    param(
        [string]$Ref,
        [string]$BaseRef
    )
    $count = git rev-list --count "$BaseRef..$Ref" 2>$null
    if ($LASTEXITCODE -ne 0) {
        return -1
    }
    return [int]$count
}

# Verify we are inside a git repository
$repoRoot = (git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
    Write-Error "Not inside a Git repository."
    exit 1
}
$repoRootNormalized = ($repoRoot.Trim() -replace '\\', '/').TrimEnd('/')
$currentBranch = (git branch --show-current 2>$null).Trim()
$cutoffDate = (Get-Date).AddDays(-$StaleDays)

Write-Host "Repository: $repoRootNormalized" -ForegroundColor White
Write-Host "Base branch: $BaseBranch | Remote: $Remote | Stale threshold: $StaleDays days (before $($cutoffDate.ToString('yyyy-MM-dd')))" -ForegroundColor DarkGray
if ($isSimulation) {
    Write-Host "Mode: DRY-RUN (no changes will be made)" -ForegroundColor Yellow
}

# 1. Fetch & prune remote-tracking refs
Write-Section "1. Remote Tracking Ref Prune"
$remoteExists = (git remote 2>$null) -contains $Remote
if ($remoteExists) {
    if ($isSimulation) {
        $prunePreview = git remote prune $Remote --dry-run 2>&1
        if ($prunePreview) {
            foreach ($line in $prunePreview) {
                Write-ActionItem -Status "PRUNE-REF" -Message $line -Color Yellow
            }
        }
        else {
            Write-ActionItem -Status "OK" -Message "No stale remote-tracking refs on '$Remote'." -Color DarkGray
        }
    }
    elseif ($PSCmdlet.ShouldProcess($Remote, "Fetch and prune stale remote-tracking references")) {
        git fetch $Remote --prune --quiet
        if ($LASTEXITCODE -eq 0) {
            Write-ActionItem -Status "PRUNED" -Message "Fetched and pruned stale remote-tracking refs from '$Remote'." -Color Green
        }
        else {
            Write-Warning "git fetch $Remote --prune exited with code $LASTEXITCODE"
        }
    }
}
else {
    Write-ActionItem -Status "SKIP" -Message "Remote '$Remote' not found." -Color Yellow
}

# Resolve base ref for comparisons
$baseRef = $BaseBranch
git rev-parse --verify "$Remote/$BaseBranch" 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) {
    $baseRef = "$Remote/$BaseBranch"
}

$mergedPrBranches = Get-PrBranchesByState -State "merged"
$openPrBranches = Get-PrBranchesByState -State "open"

# 2. Prune utilized AI worktrees
Write-Section "2. AI Worktree Cleanup"
$worktrees = Get-GitWorktrees
$removedWorktreeBranches = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$activeWorktreeBranches = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)

$stats = @{
    WorktreesRemoved = 0
    WorktreesSkipped = 0
    BranchesDeleted  = 0
    BranchesSkipped  = 0
    RemoteDeleted    = 0
}

foreach ($wt in $worktrees) {
    $wtNorm = ($wt.Path -replace '\\', '/').TrimEnd('/')
    if ($wtNorm -ieq $repoRootNormalized) {
        if ($wt.Branch) {
            [void]$activeWorktreeBranches.Add($wt.Branch)
        }
        continue
    }

    $isAi = Test-IsAiWorktree -WorktreePath $wt.Path -BranchName $wt.Branch
    if (-not $isAi) {
        if ($wt.Branch) {
            [void]$activeWorktreeBranches.Add($wt.Branch)
        }
        Write-ActionItem -Status "KEEP" -Message "Non-AI worktree: $($wt.Path) [$($wt.Branch)]" -Color DarkGray
        continue
    }

    $reason = $null
    if ($wt.Prunable -or -not (Test-Path $wt.Path)) {
        $reason = "missing directory (prunable)"
    }
    elseif ($wt.Branch -and ($ProtectedBranches -contains $wt.Branch)) {
        [void]$activeWorktreeBranches.Add($wt.Branch)
        Write-ActionItem -Status "PROTECTED" -Message "$($wt.Path) [$($wt.Branch)]" -Color DarkGray
        continue
    }
    else {
        $targetRef = if ($wt.Branch) { $wt.Branch } else { $wt.Head }
        if ($wt.Branch -and (Test-BranchMergedIntoBase -Ref $wt.Branch -BaseRef $BaseBranch)) {
            $reason = "merged into $BaseBranch"
        }
        elseif ($wt.Branch -and $mergedPrBranches.Contains($wt.Branch)) {
            $reason = "PR merged on GitHub"
        }
        elseif ($targetRef -and (Get-CommitsAhead -Ref $targetRef -BaseRef $baseRef) -eq 0) {
            $reason = "0 commits ahead of $baseRef"
        }
        elseif ($IncludeStale -and $targetRef) {
            $commitIso = git log -1 --format="%cI" $targetRef 2>$null
            if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($commitIso)) {
                $commitDate = [DateTimeOffset]::Parse($commitIso).LocalDateTime
                if ($commitDate -lt $cutoffDate) {
                    $reason = "stale (last commit $($commitDate.ToString('yyyy-MM-dd')))"
                }
            }
        }
    }

    if ($null -eq $reason) {
        if ($wt.Branch) {
            [void]$activeWorktreeBranches.Add($wt.Branch)
        }
        Write-ActionItem -Status "KEEP" -Message "Active AI worktree: $($wt.Path) [$($wt.Branch)]" -Color DarkGray
        continue
    }

    # Check if worktree has uncommitted changes
    $isDirty = $false
    if (Test-Path $wt.Path) {
        $prevPref = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $wtStatus = git -c "safe.directory=*" -C $wt.Path status --porcelain 2>$null
        $statusExit = $LASTEXITCODE
        $ErrorActionPreference = $prevPref
        if ($statusExit -ne 0) {
            Write-ActionItem -Status "SKIP-UNKNOWN" -Message "$($wt.Path) [$($wt.Branch)] (unable to read status; exit code $statusExit)" -Color Yellow
            $stats.WorktreesSkipped++
            if ($wt.Branch) {
                [void]$activeWorktreeBranches.Add($wt.Branch)
            }
            continue
        }
        if (-not [string]::IsNullOrWhiteSpace(($wtStatus -join ""))) {
            $isDirty = $true
        }
    }

    if ($isDirty -and -not $Force.IsPresent) {
        if ($wt.Branch) {
            [void]$activeWorktreeBranches.Add($wt.Branch)
        }
        $stats.WorktreesSkipped++
        Write-ActionItem -Status "SKIP-DIRTY" -Message "$($wt.Path) [$($wt.Branch)] ($reason, has uncommitted changes; use -Force to override)" -Color Yellow
        continue
    }

    if ($isSimulation) {
        if ($wt.Branch) {
            [void]$removedWorktreeBranches.Add($wt.Branch)
        }
        $stats.WorktreesRemoved++
        Write-ActionItem -Status "REMOVE" -Message "$($wt.Path) [$($wt.Branch)] ($reason)" -Color Green
    }
    elseif ($PSCmdlet.ShouldProcess($wt.Path, "Remove AI worktree ($reason)")) {
        $removeArgs = @("worktree", "remove")
        if ($Force.IsPresent) {
            $removeArgs += "--force"
        }
        $removeArgs += $wt.Path
        $output = & git @removeArgs 2>&1
        if ($LASTEXITCODE -eq 0) {
            if ($wt.Branch) {
                [void]$removedWorktreeBranches.Add($wt.Branch)
            }
            $stats.WorktreesRemoved++
            Write-ActionItem -Status "REMOVED" -Message "$($wt.Path) [$($wt.Branch)] ($reason)" -Color Green
        }
        else {
            if ($wt.Branch) {
                [void]$activeWorktreeBranches.Add($wt.Branch)
            }
            $stats.WorktreesSkipped++
            Write-Warning "Failed to remove worktree '$($wt.Path)': $output"
        }
    }
}

if (-not $isSimulation -and $PSCmdlet.ShouldProcess("worktrees", "Prune stale worktree administrative files")) {
    git worktree prune 2>$null
}

# 3. Prune merged, gone, and stale local branches
Write-Section "3. Local Branch Cleanup"
$branchFormat = "%(refname:short)|%(committerdate:iso-strict)|%(upstream:track)"
$rawBranches = git for-each-ref --format="$branchFormat" refs/heads 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "Failed to list local branches: $rawBranches"
}

foreach ($entry in $rawBranches) {
    if ([string]::IsNullOrWhiteSpace($entry)) { continue }
    $parts = $entry -split '\|', 3
    $branchName = $parts[0].Trim()
    $dateStr = if ($parts.Count -ge 2) { $parts[1].Trim() } else { "" }
    $track = if ($parts.Count -ge 3) { $parts[2].Trim() } else { "" }

    if ($ProtectedBranches -contains $branchName -or $branchName -eq $currentBranch) {
        continue
    }

    if ($activeWorktreeBranches.Contains($branchName)) {
        $stats.BranchesSkipped++
        Write-ActionItem -Status "SKIP-IN-USE" -Message "$branchName (checked out in an active worktree)" -Color Yellow
        continue
    }

    $reason = $null
    $useForceDelete = $false

    if (Test-BranchMergedIntoBase -Ref $branchName -BaseRef $BaseBranch) {
        $reason = "merged into $BaseBranch"
    }
    elseif ($mergedPrBranches.Contains($branchName)) {
        $reason = "PR merged on GitHub"
        $useForceDelete = $true
    }
    elseif ($track -match '\[gone\]') {
        $reason = "upstream gone"
        $useForceDelete = $true
    }
    elseif ($IncludeStale -and -not [string]::IsNullOrWhiteSpace($dateStr)) {
        $commitDate = [DateTimeOffset]::Parse($dateStr).LocalDateTime
        if ($commitDate -lt $cutoffDate) {
            $reason = "stale (last commit $($commitDate.ToString('yyyy-MM-dd')))"
            $useForceDelete = $true
        }
    }

    if ($null -eq $reason) {
        continue
    }

    $deleteFlag = if ($useForceDelete -or $Force.IsPresent) { "-D" } else { "-d" }

    if ($isSimulation) {
        $stats.BranchesDeleted++
        Write-ActionItem -Status "DELETE" -Message "$branchName ($reason)" -Color Green
    }
    elseif ($PSCmdlet.ShouldProcess($branchName, "Delete local branch ($reason)")) {
        $delOut = git branch $deleteFlag $branchName 2>&1
        if ($LASTEXITCODE -eq 0) {
            $stats.BranchesDeleted++
            Write-ActionItem -Status "DELETED" -Message "$branchName ($reason)" -Color Green
        }
        else {
            $stats.BranchesSkipped++
            Write-Warning "Could not delete branch '$branchName': $delOut"
        }
    }
}

# 4. Optional remote branch cleanup
if ($IncludeRemote.IsPresent -and $remoteExists) {
    Write-Section "4. Remote Branch Cleanup ($Remote)"
    $rawRemoteBranches = git for-each-ref --format="%(refname:short)|%(committerdate:iso-strict)" "refs/remotes/$Remote" 2>&1
    foreach ($entry in $rawRemoteBranches) {
        if ([string]::IsNullOrWhiteSpace($entry)) { continue }
        $parts = $entry -split '\|', 2
        $fullRemoteRef = $parts[0].Trim()
        $dateStr = if ($parts.Count -ge 2) { $parts[1].Trim() } else { "" }

        if ($fullRemoteRef -eq "$Remote/HEAD" -or -not $fullRemoteRef.StartsWith("$Remote/")) {
            continue
        }
        $remoteBranchName = $fullRemoteRef.Substring($Remote.Length + 1)
        if ($ProtectedBranches -contains $remoteBranchName -or $remoteBranchName -eq $currentBranch) {
            continue
        }
        if ($openPrBranches.Contains($remoteBranchName) -and -not $Force.IsPresent) {
            Write-ActionItem -Status "SKIP-OPEN-PR" -Message "$fullRemoteRef (has an open PR on GitHub; use -Force to override)" -Color Yellow
            continue
        }

        $reason = $null
        if (Test-BranchMergedIntoBase -Ref $fullRemoteRef -BaseRef $baseRef) {
            $reason = "merged into $baseRef"
        }
        elseif ($mergedPrBranches.Contains($remoteBranchName)) {
            $reason = "PR merged on GitHub"
        }
        elseif ($IncludeStale -and -not [string]::IsNullOrWhiteSpace($dateStr)) {
            $commitDate = [DateTimeOffset]::Parse($dateStr).LocalDateTime
            if ($commitDate -lt $cutoffDate) {
                $reason = "stale (last commit $($commitDate.ToString('yyyy-MM-dd')))"
            }
        }

        if ($null -eq $reason) {
            continue
        }

        if ($isSimulation) {
            $stats.RemoteDeleted++
            Write-ActionItem -Status "DELETE-REMOTE" -Message "$fullRemoteRef ($reason)" -Color Green
        }
        elseif ($PSCmdlet.ShouldProcess($fullRemoteRef, "Delete remote branch ($reason)")) {
            $pushOut = git push $Remote --delete $remoteBranchName 2>&1
            if ($LASTEXITCODE -eq 0) {
                $stats.RemoteDeleted++
                Write-ActionItem -Status "DELETED-REMOTE" -Message "$fullRemoteRef ($reason)" -Color Green
            }
            else {
                Write-Warning "Failed to delete remote branch '$fullRemoteRef': $pushOut"
            }
        }
    }
}

# Summary
Write-Section "Summary"
$modeLabel = if ($isSimulation) { "Would remove/delete" } else { "Removed/deleted" }
Write-Host "  AI Worktrees $modeLabel : $($stats.WorktreesRemoved) (skipped: $($stats.WorktreesSkipped))" -ForegroundColor White
Write-Host "  Local Branches $modeLabel : $($stats.BranchesDeleted) (skipped: $($stats.BranchesSkipped))" -ForegroundColor White
if ($IncludeRemote.IsPresent) {
    Write-Host "  Remote Branches $modeLabel : $($stats.RemoteDeleted)" -ForegroundColor White
}
