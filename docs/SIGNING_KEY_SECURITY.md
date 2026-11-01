# Signing Key Security & Backup Procedures

## Overview

This document outlines secure procedures for managing the TigerPlayer release signing keystore (`tigerplayer_release.jks`). The signing key is critical for APK distribution on F-Droid and Google Play Store.

## Signing Certificate Information

**Package Name:** `com.tigerplayer`

**Current Release Certificate Details:**
- **CN (Common Name):** Jaedon
- **OU (Organizational Unit):** Software Dev
- **O (Organization):** Freelancer
- **L (Locality):** Nairobi
- **ST (State):** Kenya
- **C (Country):** KE

**Certificate SHA-256 Fingerprint:**
```
0aabbc79e9db6666f1f814487d5607865f2fc643ca940217ff941947c0fb0c9b
```

**Certificate SHA-1 Fingerprint:**
```
d5ddf3f72629092de598f7c6ff2229afabd296b8
```

**Certificate MD5 Fingerprint:**
```
645638db5455ad4876a5008f26e74374
```

## File Locations & Permissions

### Primary Keystore
- **Path:** `tigerplayer_release.jks`
- **Status:** ✅ Already in repository (local-only, not committed to git)
- **Git Status:** Tracked in `.gitignore` to prevent accidental commits
- **Access:** Local development and CI/CD only

### Keystore Configuration
- **Path:** `keystore.properties` (if exists)
- **Status:** ⚠️ MUST NOT be committed to version control
- **Contents:** Keystore password and key password (referenced in CI secrets)

### Sensitive Information Storage
- Keystore password → Environment variable: `KEYSTORE_PASSWORD`
- Key password → Environment variable: `KEY_PASSWORD`
- Key alias → Environment variable: `KEY_ALIAS` (or hardcoded if non-sensitive)

## Extracting Certificate Information

### Using apksigner (Recommended)
```powershell
# Extract certificate from signed APK
./gradlew assembleFullRelease
apksigner verify --print-certs app/build/outputs/apk/full/release/app-full-release.apk

# Output:
# Signer #1 certificate DN: CN=Jaedon, OU=Software Dev, O=Freelancer, L=Nairobi, ST=Kenya, C=KE
# Signer #1 certificate SHA-256 digest: 0aabbc79e9db6666f1f814487d5607865f2fc643ca940217ff941947c0fb0c9b
```

### Using keytool
```bash
# Extract certificate details from keystore
keytool -list -v -keystore tigerplayer_release.jks -alias tigerplayer

# Extract from signed APK
keytool -printcert -jarfile app/release/app-release.apk | sed -n 's/[[:space:]]*SHA256: //p' | tr -d ':' | tr '[:upper:]' '[:lower:]'
```

## Backup Procedures

### 1. Local Encrypted Backup (Developer Machine)

**Frequency:** Weekly or after any release build

**Steps:**
1. Create backup of keystore:
   ```powershell
   $backupPath = "C:\Secure\Backups\tigerplayer_release_backup_$(Get-Date -Format 'yyyy-MM-dd_HHmmss').jks"
   Copy-Item "tigerplayer_release.jks" -Destination $backupPath
   ```

2. Encrypt backup using 7-Zip or similar:
   ```powershell
   # Using 7-Zip (requires password entry via interactive prompt)
   & "C:\Program Files\7-Zip\7z.exe" a -t7z -p $backupPath.7z $backupPath
   ```

3. Verify backup integrity:
   ```powershell
   # List contents without extraction
   & "C:\Program Files\7-Zip\7z.exe" l $backupPath.7z
   ```

4. Store encrypted backup securely:
   - **Primary:** External USB drive in safe location
   - **Secondary:** Encrypted cloud storage (OneDrive, Google Drive) with strong password
   - **Keep offline copies:** At least one copy should be stored offline

### 2. Secure Cloud Backup

**Provider:** Choose one with strong encryption and MFA support
- Bitwarden (recommended for secrets management)
- 1Password
- LastPass
- End-to-end encrypted cloud providers

**Steps:**
1. Encrypt keystore with GPG:
   ```bash
   gpg --symmetric --cipher-algo AES256 tigerplayer_release.jks
   # Creates: tigerplayer_release.jks.gpg
   ```

2. Upload encrypted file to cloud storage
3. Store decryption password in separate secure location
4. Document access procedures

### 3. CI/CD Secret Management

**GitHub Actions Secrets:**
- Store keystore as base64-encoded secret
- Store passwords as separate secrets with restricted access
- Never print or log secrets in build output

**Setup:**
1. Encode keystore:
   ```powershell
   # PowerShell
   $fileBytes = [System.IO.File]::ReadAllBytes("tigerplayer_release.jks")
   $base64 = [Convert]::ToBase64String($fileBytes)
   $base64 | Set-Clipboard
   ```

2. Add to GitHub secrets:
   - `KEYSTORE_RELEASE_B64` → Base64-encoded keystore
   - `KEYSTORE_PASSWORD` → Keystore password
   - `KEY_PASSWORD` → Key password
   - `KEY_ALIAS` → Key alias name

3. Decode in CI workflow:
   ```yaml
   - name: Decode release keystore
     run: |
       echo "${{ secrets.KEYSTORE_RELEASE_B64 }}" | base64 -d > tigerplayer_release.jks
   ```

## Key Rotation Procedures

**When to rotate:**
- If keystore password is compromised
- If signing machine is compromised
- Every 2-3 years as security best practice (optional)

**Important:** ⚠️ Rotating the signing key will break all future updates on Google Play Store and F-Droid. Plan rotation carefully and communicate with users.

**Steps:**
1. Create new keystore with identical certificate details
2. Sign a test build with new key
3. Create a new GitHub secret for the new keystore
4. Update CI/CD workflows to use new secret
5. Archive old keystore with secure documentation
6. Create migration plan for affected platforms

## Emergency Recovery Procedures

### Lost Keystore File

**If `tigerplayer_release.jks` is lost or corrupted:**

1. **Check backups immediately:**
   - Local encrypted backups
   - Cloud storage backups
   - CI/CD caches (may retain copies)

2. **Restore from backup:**
   ```powershell
   # From encrypted backup
   & "C:\Program Files\7-Zip\7z.exe" x $backupPath.7z -p
   # Enter password when prompted
   ```

3. **Verify restored keystore:**
   ```bash
   keytool -list -v -keystore tigerplayer_release.jks
   ```

4. **If no backups exist:** 💥 **CRITICAL**
   - Cannot recover the same signing key
   - Must create new key with different certificate details
   - Will break update path on stores (this is intentional - prevents unauthorized key replacement)
   - Document incident thoroughly

### Lost Keystore Password

1. **Keystore file intact, password lost:**
   - Cannot recover password - keystore is inaccessible
   - Must restore from encrypted backup with password
   - If no backup available, keystore is permanently unusable

2. **Prevention:**
   - Store password in password manager (Bitwarden, 1Password)
   - Keep copy in secure envelope in physical safe
   - Document in team wiki with restricted access

### Compromised Keystore

**If keystore file or password is compromised:**

1. **Immediate actions:**
   - Revoke the compromised secret from GitHub
   - Rotate the secret to CI/CD pipeline
   - Disable any offline/external access to the keystore

2. **Assessment:**
   - Review git logs and CI/CD logs for any exposure
   - Check for unauthorized APK signings

3. **Long-term:**
   - Consider key rotation if compromise window was significant
   - Update security audit logs
   - Brief team on incident

## Verification & Testing

### Monthly Verification Checklist

- [ ] Verify keystore file exists and is readable
- [ ] Test extracting certificate fingerprint via apksigner
- [ ] Verify encrypted backups are accessible and valid
- [ ] Check that CI/CD secrets are correctly configured
- [ ] Review access logs to keystore backups
- [ ] Confirm GitHub secret expiration dates (set reminders)

### Release Build Verification

Before every release:

1. Build release APK:
   ```bash
   ./gradlew assembleFullRelease
   ```

2. Verify signing:
   ```bash
   apksigner verify --print-certs app/build/outputs/apk/full/release/app-full-release.apk
   ```

3. Confirm fingerprint matches expected:
   ```
   Expected: 0aabbc79e9db6666f1f814487d5607865f2fc643ca940217ff941947c0fb0c9b
   ```

4. If fingerprints don't match → **STOP** - investigate before releasing

## Security Best Practices

1. **Never commit keystore to git**
   - Already in `.gitignore`
   - Verify with: `git ls-files | grep keystore` (should be empty)

2. **Never log sensitive data**
   - Review CI/CD logs for any printed passwords or key material
   - Mask secrets in output: `${{ secrets.KEYSTORE_PASSWORD }}` → `****`

3. **Minimize human access**
   - Release builds automated via CI/CD
   - Manual builds only on secure, isolated machines
   - Document who has accessed keystore and when

4. **Multi-factor authentication**
   - GitHub account: Enable MFA on all contributor accounts
   - Cloud storage: MFA on backup storage services
   - CI/CD: Restrict secret access to core team members only

5. **Principle of least privilege**
   - Only core maintainers have keystore access
   - GitHub secrets accessible only to release workflows
   - Separate read-only and write access where possible

## Documentation & Communication

- [ ] Update CONTRIBUTING.md with signing key guidelines
- [ ] Brief all contributors on these procedures
- [ ] Maintain a secure shared document with emergency contacts
- [ ] Record decision rationale for any key rotation
- [ ] Archive old backup procedures for historical reference

## Related Issues & References

- F-Droid Build Metadata: https://f-droid.org/docs/Build_Metadata_Reference/#allowedapksigningkeys
- Google Play App Signing: https://developer.android.com/studio/publish/app-signing
- Android APKSigner Documentation: https://developer.android.com/studio/command-line/apksigner
- GitHub Encrypted Secrets: https://docs.github.com/en/actions/security-guides/encrypted-secrets
- TigerPlayer Issue #246: Add Binaries, binary, and AllowedAPKSigningKeys for reproducible builds

