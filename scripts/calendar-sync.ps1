<#
.SYNOPSIS
    Syncs completed GitHub issues and milestones to Google Calendar as green task checklists via the gcalendar skill.

.DESCRIPTION
    When an issue or milestone is completed, this script:
    1. Looks up the issue/milestone metadata via gh CLI.
    2. Searches for matching calendar events on Google Calendar using gcalendar.
    3. Pivots the event into a completed task/checklist:
       - Summary: ✅ [CLOSED] #<ID> <Title> or 🏁 [COMPLETED] Milestone: <Title>
       - Color: 10 (Basil / Green)
       - Transparency: transparent (does not block schedule)
       - Description: Markdown task checklist with completion date and links
    4. If the event does not exist on the calendar, creates a new all-day completion event.

.PARAMETER IssueId
    The GitHub issue number (e.g. 72, 251, 275).

.PARAMETER MilestoneTitle
    The title of the GitHub milestone (e.g. "Cover Screen Correctness (v2.1.2)").

.PARAMETER SyncRecentClosed
    Number of recently closed issues to sync in batch.

.EXAMPLE
    .\scripts\calendar-sync.ps1 -IssueId 72
    .\scripts\calendar-sync.ps1 -MilestoneTitle "Cover Screen Correctness (v2.1.2)"
    .\scripts\calendar-sync.ps1 -SyncRecentClosed 5
#>

[CmdletBinding()]
param (
    [Parameter(Mandatory = $false)]
    [int]$IssueId,

    [Parameter(Mandatory = $false)]
    [string]$MilestoneTitle,

    [Parameter(Mandatory = $false)]
    [int]$SyncRecentClosed = 0
)

# Locate gcalendar executable
$gcalExe = "$env:USERPROFILE\.gemini\antigravity\plugin_data\gcalendar\bin\gcalendar-986250228.exe"
if (-not (Test-Path $gcalExe)) {
    $gcalExe = "$env:USERPROFILE\.gemini\config\plugins\gcalendar\skills\gcalendar\scripts\gcalendar.cmd"
}

if (-not (Test-Path $gcalExe)) {
    Write-Error "gcalendar executable not found. Ensure gcalendar plugin is installed and authenticated."
    exit 1
}

function Sync-Issue {
    param ([int]$Id)

    Write-Host "Fetching GitHub issue #$Id..." -ForegroundColor Cyan
    $issueJson = gh issue view $Id --json number,title,state,closedAt,milestone,labels | ConvertFrom-Json
    if (-not $issueJson) {
        Write-Warning "Could not find issue #$Id on GitHub."
        return
    }

    $title = $issueJson.title
    $state = $issueJson.state
    $closedAt = if ($issueJson.closedAt) { $issueJson.closedAt.Substring(0, 10) } else { (Get-Date).ToString("yyyy-MM-dd") }
    $milestone = if ($issueJson.milestone) { $issueJson.milestone.title } else { "None" }

    if ($state -ne "CLOSED") {
        Write-Host "Issue #$Id is still $state. Only closed/completed issues are pivoted to completed calendar tasks." -ForegroundColor Yellow
        return
    }

    $newSummary = "✅ [CLOSED] #$Id $title"
    $taskChecklist = @"
Status: COMPLETED ✅
Issue URL: https://github.com/tyejaedon/TigerPlayer/issues/$Id
Milestone: $milestone
Closed Date: $closedAt

Task Checklist:
- [x] Phase 0: Issue reproduction and baseline validation
- [x] Phase 1-2: Architecture implementation & scoped fixes
- [x] Phase 3: Quality toolchain passed (lint, unit tests, assemble)
- [x] Phase 4-5: PR merged into master, issue closed
"@

    # Search for existing calendar event matching issue ID
    Write-Host "Searching Google Calendar for event matching #$Id..." -ForegroundColor Cyan
    $searchResults = & $gcalExe readonly search "$Id" --json | ConvertFrom-Json
    $matchingEvents = @($searchResults | Where-Object { $_.summary -match "#$Id\b" })

    if ($matchingEvents.Count -gt 0) {
        foreach ($evt in $matchingEvents) {
            Write-Host "Updating existing event '$($evt.summary)' (ID: $($evt.id))..." -ForegroundColor Green
            & $gcalExe mutate update $($evt.id) `
                --summary $newSummary `
                --color 10 `
                --transparency transparent `
                --description $taskChecklist `
                --send-updates none
        }
    } else {
        Write-Host "No calendar event found for #$Id. Creating new all-day completed task event on $closedAt..." -ForegroundColor Green
        $endDate = (Get-Date $closedAt).AddDays(1).ToString("yyyy-MM-dd")
        & $gcalExe mutate create `
            --summary $newSummary `
            --all-day `
            --start $closedAt `
            --end $endDate `
            --color 10 `
            --transparency transparent `
            --description $taskChecklist `
            --send-updates none
    }
}

function Sync-Milestone {
    param ([string]$Name)

    Write-Host "Fetching GitHub milestone '$Name'..." -ForegroundColor Cyan
    $milestones = gh api repos/tyejaedon/TigerPlayer/milestones --jq '.[]' | ConvertFrom-Json
    $targetMs = $milestones | Where-Object { $_.title -like "*$Name*" } | Select-Object -First 1

    if (-not $targetMs) {
        Write-Warning "Milestone '$Name' not found."
        return
    }

    $msTitle = $targetMs.title
    $openCount = $targetMs.open_issues
    $closedCount = $targetMs.closed_issues
    $totalCount = $openCount + $closedCount
    $isComplete = ($openCount -eq 0)

    Write-Host "Milestone: '$msTitle' ($closedCount/$totalCount closed)" -ForegroundColor Cyan
    $issues = gh issue list --milestone "$msTitle" --state all --json number,title,state | ConvertFrom-Json

    $issueLines = foreach ($iss in $issues) {
        $mark = if ($iss.state -eq "CLOSED") { "[x]" } else { "[ ]" }
        "- $mark #$($iss.number) $($iss.title)"
    }
    $checklistText = $issueLines -join "`n"

    $statusHeader = if ($isComplete) { "Status: 100% COMPLETED ($closedCount/$totalCount closed) ✅" } else { "Status: IN PROGRESS ($closedCount/$totalCount closed, $openCount remaining)" }
    $eventSummary = if ($isComplete) { "🏁 [COMPLETED] Milestone: $msTitle" } else { "[Milestone] $msTitle" }
    $colorId = if ($isComplete) { "10" } else { "3" }

    $desc = @"
$statusHeader
Milestone: $msTitle
Due Date: $($targetMs.due_on)

Milestone Checklist:
$checklistText
"@

    Write-Host "Searching Google Calendar for milestone '$msTitle'..." -ForegroundColor Cyan
    $searchResults = & $gcalExe readonly search "$msTitle" --json | ConvertFrom-Json
    $matchingEvents = @($searchResults | Where-Object { $_.summary -match "\[Milestone\]" -or $_.summary -match "\[COMPLETED\] Milestone" })

    if ($matchingEvents.Count -gt 0) {
        foreach ($evt in $matchingEvents) {
            Write-Host "Updating milestone event '$($evt.summary)'..." -ForegroundColor Green
            & $gcalExe mutate update $($evt.id) `
                --summary $eventSummary `
                --color $colorId `
                --transparency transparent `
                --description $desc `
                --send-updates none
        }
    } else {
        Write-Host "No calendar event found for milestone. Creating new event..." -ForegroundColor Green
        $today = (Get-Date).ToString("yyyy-MM-dd")
        $tomorrow = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")
        & $gcalExe mutate create `
            --summary $eventSummary `
            --all-day `
            --start $today `
            --end $tomorrow `
            --color $colorId `
            --transparency transparent `
            --description $desc `
            --send-updates none
    }
}

if ($IssueId -gt 0) {
    Sync-Issue -Id $IssueId
} elseif ($MilestoneTitle) {
    Sync-Milestone -Name $MilestoneTitle
} elseif ($SyncRecentClosed -gt 0) {
    Write-Host "Fetching $SyncRecentClosed recently closed issues from GitHub..." -ForegroundColor Cyan
    $recentClosed = gh issue list --state closed --limit $SyncRecentClosed --json number | ConvertFrom-Json
    foreach ($item in $recentClosed) {
        Sync-Issue -Id $item.number
    }
} else {
    Write-Host "Usage:"
    Write-Host "  .\scripts\calendar-sync.ps1 -IssueId <ID>"
    Write-Host "  .\scripts\calendar-sync.ps1 -MilestoneTitle '<Title>'"
    Write-Host "  .\scripts\calendar-sync.ps1 -SyncRecentClosed <Count>"
}
