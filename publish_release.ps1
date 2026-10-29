# PowerShell script to publish v2.1.3 release
Set-Location C:\TigerPlayer

# Try to publish the release
try {
    Write-Host "Publishing v2.1.3 release..."
    & gh release edit v2.1.3 --draft=false
    Write-Host "Release published successfully!"

    # Verify the release is published
    Write-Host "Verifying release status..."
    $releaseInfo = & gh release view v2.1.3 --json isDraft,tagName,name
    Write-Host $releaseInfo

} catch {
    Write-Host "Error: $_"
    Write-Host "Release may already exist or error occurred"
}

# Also check via API
Write-Host "`nChecking release via API..."
$apiResponse = & gh api repos/tyejaedon/TigerPlayer/releases/tags/v2.1.3
Write-Host "Release API check complete"

