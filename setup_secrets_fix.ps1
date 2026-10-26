# TigerPlayer v2.1.3 - Set GitHub Release Signing Secrets
# This script securely adds the keystore and credentials to GitHub Secrets

Write-Host "======================================"
Write-Host "TigerPlayer Release Signing Secrets Setup"
Write-Host "======================================"
Write-Host ""

# Step 1: Encode keystore
$keystorePath = "C:\TigerPlayer\tigerplayer_release.jks"
if (-not (Test-Path $keystorePath)) {
    Write-Host "[FAIL] Keystore not found at: $keystorePath"
    exit 1
}

Write-Host "[WORK] Encoding keystore to base64..."
$fileBytes = [System.IO.File]::ReadAllBytes($keystorePath)
$base64 = [Convert]::ToBase64String($fileBytes)
Write-Host "[OK] Keystore encoded ($($base64.Length) characters)"
Write-Host ""

# Step 2: Prompt for credentials
Write-Host "[INFO] Now I need your signing credentials."
Write-Host "[INFO] These will be securely added to GitHub Secrets."
Write-Host ""

$keystorePassword = Read-Host "Enter RELEASE_KEYSTORE_PASSWORD" -AsSecureString
$keyPassword = Read-Host "Enter RELEASE_KEY_PASSWORD" -AsSecureString
$keyAlias = Read-Host "Enter RELEASE_KEY_ALIAS (default: 'tigerplayer')"
if ([string]::IsNullOrWhitespace($keyAlias)) {
    $keyAlias = "tigerplayer"
}

# Convert secure strings to plain text
$keystorePasswordPlain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [System.Runtime.InteropServices.Marshal]::SecureStringToCoTaskMemUnicode($keystorePassword)
)
$keyPasswordPlain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [System.Runtime.InteropServices.Marshal]::SecureStringToCoTaskMemUnicode($keyPassword)
)

Write-Host ""
Write-Host "[WORK] Setting GitHub secrets..."
Write-Host ""

# Set secrets
try {
    Write-Host "[1/4] Setting RELEASE_KEYSTORE_B64..."
    $base64 | gh secret set RELEASE_KEYSTORE_B64 --repo tyejaedon/TigerPlayer 2>&1 | Out-Null
    Write-Host "      [OK]"

    Write-Host "[2/4] Setting RELEASE_KEYSTORE_PASSWORD..."
    $keystorePasswordPlain | gh secret set RELEASE_KEYSTORE_PASSWORD --repo tyejaedon/TigerPlayer 2>&1 | Out-Null
    Write-Host "      [OK]"

    Write-Host "[3/4] Setting RELEASE_KEY_PASSWORD..."
    $keyPasswordPlain | gh secret set RELEASE_KEY_PASSWORD --repo tyejaedon/TigerPlayer 2>&1 | Out-Null
    Write-Host "      [OK]"

    Write-Host "[4/4] Setting RELEASE_KEY_ALIAS..."
    $keyAlias | gh secret set RELEASE_KEY_ALIAS --repo tyejaedon/TigerPlayer 2>&1 | Out-Null
    Write-Host "      [OK]"

    Write-Host ""
    Write-Host "[SUCCESS] All secrets configured!"
    Write-Host ""
    Write-Host "Next: gh workflow run sign-release.yml -f tag=v2.1.3 --repo tyejaedon/TigerPlayer"
}
catch {
    Write-Host "[ERROR] $_"
    exit 1
}
finally {
    $keystorePasswordPlain = ""
    $keyPasswordPlain = ""
}

