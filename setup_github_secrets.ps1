# TigerPlayer v2.1.3 - Set GitHub Release Signing Secrets
# This script securely adds the keystore and credentials to GitHub Secrets
# Run this script locally on your machine

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "TigerPlayer Release Signing Secrets Setup" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

# Check if GitHub CLI is installed
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "❌ GitHub CLI not found. Install it from: https://cli.github.com/" -ForegroundColor Red
    exit 1
}

Write-Host "✅ GitHub CLI found" -ForegroundColor Green
Write-Host ""

# Verify user is logged in
$auth = gh auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Not authenticated with GitHub. Run: gh auth login" -ForegroundColor Red
    exit 1
}

Write-Host "✅ GitHub authentication verified" -ForegroundColor Green
Write-Host ""

# Step 1: Encode keystore
$keystorePath = "C:\TigerPlayer\tigerplayer_release.jks"
if (-not (Test-Path $keystorePath)) {
    Write-Host "❌ Keystore not found at: $keystorePath" -ForegroundColor Red
    exit 1
}

Write-Host "📦 Encoding keystore to base64..." -ForegroundColor Yellow
$fileBytes = [System.IO.File]::ReadAllBytes($keystorePath)
$base64 = [Convert]::ToBase64String($fileBytes)
Write-Host "✅ Keystore encoded (${0} characters)" -f $base64.Length -ForegroundColor Green
Write-Host ""

# Step 2: Prompt for credentials
Write-Host "🔐 Now I need your signing credentials." -ForegroundColor Cyan
Write-Host "   These will be securely added to GitHub Secrets." -ForegroundColor Cyan
Write-Host ""

$keystorePassword = Read-Host "Enter RELEASE_KEYSTORE_PASSWORD" -AsSecureString
$keyPassword = Read-Host "Enter RELEASE_KEY_PASSWORD" -AsSecureString
$keyAlias = Read-Host "Enter RELEASE_KEY_ALIAS (default: 'tigerplayer')"
if ([string]::IsNullOrWhitespace($keyAlias)) {
    $keyAlias = "tigerplayer"
}

# Convert secure strings to plain text for passing to gh
$keystorePasswordPlain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [System.Runtime.InteropServices.Marshal]::SecureStringToCoTaskMemUnicode($keystorePassword)
)
$keyPasswordPlain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [System.Runtime.InteropServices.Marshal]::SecureStringToCoTaskMemUnicode($keyPassword)
)

Write-Host ""
Write-Host "🔑 Setting GitHub secrets..." -ForegroundColor Cyan
Write-Host ""

# Step 3: Set secrets in GitHub
try {
    # RELEASE_KEYSTORE_B64
    Write-Host "  1️⃣  Setting RELEASE_KEYSTORE_B64..." -ForegroundColor Yellow
    $base64 | gh secret set RELEASE_KEYSTORE_B64 --repo tyejaedon/TigerPlayer 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "     ✅ RELEASE_KEYSTORE_B64 set" -ForegroundColor Green
    } else {
        Write-Host "     ❌ Failed to set RELEASE_KEYSTORE_B64" -ForegroundColor Red
        exit 1
    }

    # RELEASE_KEYSTORE_PASSWORD
    Write-Host "  2️⃣  Setting RELEASE_KEYSTORE_PASSWORD..." -ForegroundColor Yellow
    $keystorePasswordPlain | gh secret set RELEASE_KEYSTORE_PASSWORD --repo tyejaedon/TigerPlayer 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "     ✅ RELEASE_KEYSTORE_PASSWORD set" -ForegroundColor Green
    } else {
        Write-Host "     ❌ Failed to set RELEASE_KEYSTORE_PASSWORD" -ForegroundColor Red
        exit 1
    }

    # RELEASE_KEY_PASSWORD
    Write-Host "  3️⃣  Setting RELEASE_KEY_PASSWORD..." -ForegroundColor Yellow
    $keyPasswordPlain | gh secret set RELEASE_KEY_PASSWORD --repo tyejaedon/TigerPlayer 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "     ✅ RELEASE_KEY_PASSWORD set" -ForegroundColor Green
    } else {
        Write-Host "     ❌ Failed to set RELEASE_KEY_PASSWORD" -ForegroundColor Red
        exit 1
    }

    # RELEASE_KEY_ALIAS
    Write-Host "  4️⃣  Setting RELEASE_KEY_ALIAS..." -ForegroundColor Yellow
    $keyAlias | gh secret set RELEASE_KEY_ALIAS --repo tyejaedon/TigerPlayer 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "     ✅ RELEASE_KEY_ALIAS set" -ForegroundColor Green
    } else {
        Write-Host "     ❌ Failed to set RELEASE_KEY_ALIAS" -ForegroundColor Red
        exit 1
    }

    Write-Host ""
    Write-Host "✅ All secrets successfully configured!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next step: Run the signing workflow" -ForegroundColor Cyan
    Write-Host "Command: gh workflow run sign-release.yml -f tag=v2.1.3 --repo tyejaedon/TigerPlayer" -ForegroundColor White
    Write-Host ""
    Write-Host "Or visit: https://github.com/tyejaedon/TigerPlayer/actions/workflows/sign-release.yml" -ForegroundColor White

}
catch {
    Write-Host ""
    Write-Host "❌ Error: $_" -ForegroundColor Red
    exit 1
}
finally {
    # Clear sensitive variables
    $keystorePasswordPlain = ""
    $keyPasswordPlain = ""
}

