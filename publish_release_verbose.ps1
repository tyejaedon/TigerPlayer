# Release Publishing Log
Start-Transcript -Path C:\TigerPlayer\release_publish.log -Append

Write-Output "=== Release Publishing Script ==="
Write-Output "Time: $(Get-Date)"
Write-Output ""

Set-Location C:\TigerPlayer
Write-Output "Current Directory: $(Get-Location)"

# Step 1: Check git status
Write-Output ""
Write-Output "Step 1: Checking git status"
& git status --short | Out-String

# Step 2: Check if tag exists
Write-Output ""
Write-Output "Step 2: Checking v2.1.3 tag"
$tagExists = & git tag -l v2.1.3
Write-Output "Tag exists: $tagExists"

# Step 3: Publish the release
Write-Output ""
Write-Output "Step 3: Publishing release v2.1.3"
try {
    & gh release edit v2.1.3 --draft=false 2>&1
    Write-Output "Release edit command executed"
} catch {
    Write-Output "Error executing release edit: $_"
}

# Step 4: Verify release status
Write-Output ""
Write-Output "Step 4: Verifying release status"
try {
    & gh release view v2.1.3 2>&1
    Write-Output "Release view command executed"
} catch {
    Write-Output "Error viewing release: $_"
}

Write-Output ""
Write-Output "=== Script Complete ==="
Write-Output "Time: $(Get-Date)"

Stop-Transcript

