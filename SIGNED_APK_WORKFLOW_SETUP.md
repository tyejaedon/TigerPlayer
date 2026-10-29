# v2.1.3 Signed APK Generation - Workflow Implementation

## Status: SIGNING WORKFLOW TRIGGERED ✅

### What Was Done

#### 1. Created Signing Workflow
**File**: `.github/workflows/sign-release.yml`
**Purpose**: Automatically sign APKs and upload them to the GitHub release

**Workflow Features**:
- Triggers on successful "Release candidate" workflow completion (OR manual dispatch)
- Downloads unsigned APKs from GitHub release
- Decodes keystore from base64 secret
- Signs both full and foss flavor APKs using jarsigner
- Verifies signatures after signing
- Uploads signed APKs back to the release (with `-signed` suffix)

#### 2. Configured Signing Secrets
The following secrets are configured in GitHub:
- `RELEASE_KEYSTORE_PASSWORD` - Keystore password
- `RELEASE_KEY_PASSWORD` - Key password
- `RELEASE_KEY_ALIAS` - Key alias name
- `RELEASE_KEYSTORE_B64` - Base64-encoded keystore file (required)

#### 3. Triggered Workflow
**Workflow Run ID**: 38302030169
**Trigger Time**: October 12, 2026, ~16:30 UTC
**Command**: `gh workflow run 382520288 -f tag=v2.1.3`
**URL**: https://github.com/tyejaedon/TigerPlayer/actions/runs/38302030169

### Workflow Steps

The signing workflow will execute these steps:

1. **Checkout Code**
   - Clones repository at master branch
   - Fetches all refs for tag resolution

2. **Setup JDK & Android SDK**
   - Sets up Java 17
   - Configures Android SDK tools
   - Restores Gradle cache

3. **Determine Tag**
   - Resolves tag from workflow_dispatch input (v2.1.3)

4. **Download Unsigned APKs**
   - Downloads from v2.1.3 release:
     - app-full-release*.apk
     - app-foss-release*.apk

5. **Prepare Keystore**
   - Decodes RELEASE_KEYSTORE_B64 secret from base64
   - Creates release.keystore file

6. **Sign APKs**
   - Signs full release APK with:
     - Algorithm: SHA256withRSA
     - Digest: SHA-256
     - Keystore: release.keystore
     - Credentials from secrets
   - Signs foss release APK with same configuration
   - Renames to `-signed.apk` suffix

7. **Verify Signatures**
   - Validates full release APK signature
   - Validates foss release APK signature
   - Displays certificate information

8. **Upload Signed APKs**
   - Uploads to v2.1.3 release:
     - app-full-release-signed.apk
     - app-foss-release-signed.apk
   - Uses --clobber to replace if files exist

9. **Cleanup**
   - Removes keystore file from runner

### Expected Artifacts on v2.1.3 Release

When the workflow completes successfully, the release will contain:

**Unsigned APKs** (from Release candidate workflow):
- app-foss-release-unsigned.apk
- app-full-release-unsigned.apk

**Signed APKs** (from Sign workflow - THESE ARE NEW):
- **app-full-release-signed.apk** ✅ Ready for Google Play
- **app-foss-release-signed.apk** ✅ Ready for F-Droid

**Supporting Files**:
- app-full-release.aab (unsigned bundle)
- mapping-full.txt
- mapping-foss.txt
- SHA256SUMS.txt
- tigerplayer-sbom.spdx.json

### Monitoring the Workflow

**View Workflow Status**:
```bash
gh run view 38302030169
gh run view 38302030169 --log
```

**Watch Real-time Progress**:
```bash
gh run watch 38302030169
```

**List Recent Signing Workflows**:
```bash
gh run list --workflow="sign-release.yml" --limit 5
```

**Check Release Assets**:
```bash
gh release view v2.1.3 --json assets
```

**Download Signed APKs**:
```bash
gh release download v2.1.3 -p "*-signed.apk"
```

### Workflow URL
https://github.com/tyejaedon/TigerPlayer/actions/runs/38302030169

### Expected Completion Time

- **Estimated Duration**: 5-10 minutes
- **Current Time**: ~16:30 UTC, Oct 12, 2026
- **Expected Completion**: ~16:35-16:40 UTC, Oct 12, 2026

### Troubleshooting

If the workflow fails:

**Common Issues**:
1. **Missing RELEASE_KEYSTORE_B64 secret**
   - Solution: Ensure base64-encoded keystore is in GitHub Secrets

2. **Wrong RELEASE_KEY_ALIAS**
   - Solution: Verify alias matches the key in keystore

3. **Wrong passwords**
   - Solution: Verify RELEASE_KEYSTORE_PASSWORD and RELEASE_KEY_PASSWORD

4. **APKs not found in release**
   - Solution: Ensure Release candidate workflow completed first

**Manual Retry**:
```bash
gh run rerun 38302030169
```

### Next Steps After Signing Completes

1. **Verify Signatures**:
   ```bash
   gh release download v2.1.3 -p "*-signed.apk"
   jarsigner -verify -verbose *-signed.apk
   ```

2. **F-Droid Submission**:
   - Use `app-foss-release-signed.apk` (or F-Droid will build from source)

3. **Google Play Upload**:
   - Use `app-fullRelease.aab` (already built by Release workflow)

4. **User Announcement**:
   - Release is ready with signed APKs

### Summary

✅ **Signing Workflow Created**: `.github/workflows/sign-release.yml`
✅ **Workflow Registered**: Available in GitHub Actions
✅ **Secrets Configured**: RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEYSTORE_B64
✅ **Workflow Triggered**: Run ID 38302030169 for v2.1.3
⏳ **Status**: In Progress
⏳ **Expected**: Signed APKs will be attached to v2.1.3 release within 5-10 minutes

---

**Monitor at**: https://github.com/tyejaedon/TigerPlayer/actions/runs/38302030169
**Release Page**: https://github.com/tyejaedon/TigerPlayer/releases/tag/v2.1.3

---

Generated: October 12, 2026, ~16:30 UTC

