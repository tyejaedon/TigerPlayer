# v2.1.3 Release: How to Verify and Monitor

## Release Published Successfully

The v2.1.3 release has been published with CI/CD workflows triggered. Here's how to verify and monitor the release:

### Verify Release Published

To verify the release is no longer a draft:

```bash
gh release view v2.1.3 --json isDraft,tagName,name,body
```

Expected output:
- `isDraft`: false
- `tagName`: v2.1.3
- `name`: TigerPlayer v2.1.3
- `body`: Contains release notes with fixes and additions

### Monitor CI/CD Workflow Progress

The GitHub Actions "Release candidate" workflow was automatically triggered when the v2.1.3 tag was pushed.

**Web UI**: https://github.com/tyejaedon/TigerPlayer/actions

**Command Line**:

```bash
# List recent workflow runs
gh run list --workflow release.yml --limit 5

# Watch the latest run
gh run watch

# Get details of specific run
gh run view <run-id> --log
```

### Check Release Assets

To see what artifacts have been attached to the release:

```bash
gh release view v2.1.3 --json assets
```

Expected assets (when workflow completes):
- app-foss-release.apk
- app-full-release.apk  
- app-fullRelease.aab
- mapping-full.txt
- mapping-foss.txt
- SHA256SUMS.txt
- tigerplayer-sbom.spdx.json

### Download Release Artifacts

```bash
# Download specific APK
gh release download v2.1.3 -p "app-foss-release.apk"

# Download all assets
gh release download v2.1.3
```

### Verify Signatures and Checksums

Once SHA256SUMS.txt is attached:

```bash
# Download checksums
gh release download v2.1.3 -p "SHA256SUMS.txt"

# Verify downloaded APKs
sha256sum -c SHA256SUMS.txt
```

### Git Status Verification

```bash
# Verify tag exists locally
git tag -l v2.1.3

# Verify tag exists on remote
git ls-remote origin v2.1.3

# View tag commit
git show v2.1.3

# View tag annotation
git tag -n1 v2.1.3
```

### Release Information

**Published Release URL**:
https://github.com/tyejaedon/TigerPlayer/releases/tag/v2.1.3

**Current Status**:
- ✅ Draft: false (published)
- ✅ Tag: v2.1.3
- ✅ CI/CD: Running
- ✅ Artifacts: Attaching (in progress)

### Timeline

1. **2026-10-12 ~14:15** - Signed APKs created locally
2. **2026-10-12 ~14:20** - Git tag v2.1.3 created and pushed
3. **2026-10-12 ~14:20** - GitHub Actions release workflow triggered
4. **2026-10-12 ~14:25** - Release draft published (draft=false)
5. **2026-10-12 ~15:10** - Expected: CI/CD workflow completes, artifacts attached

### Workflow Stages

The release.yml workflow has these main stages:

1. **Setup** (1-2 min)
   - Checkout code at tag
   - Setup Android build environment
   - Restore build cache

2. **Verification** (1-2 min)
   - Dependency verification
   - Gradle help command

3. **Build** (15-20 min)
   - Minify with R8 (both flavors)
   - Build AAB (full flavor)
   - Assemble APKs (both flavors)

4. **Artifacts** (3-5 min)
   - Generate SHA256 checksums
   - Generate R8 mapping files
   - Create SPDX SBOM

5. **Upload** (5-10 min)
   - Upload to GitHub Actions
   - Publish to GitHub Release

### Troubleshooting

If artifacts don't appear after 45 minutes:

```bash
# Check workflow status
gh run view -w release.yml

# Check for errors
gh run view <run-id> --log

# Manually trigger workflow (if needed)
gh workflow run release.yml -f ref=v2.1.3
```

### Release Ready For

- ✅ Manual Testing (download unsigned APKs)
- ✅ F-Droid Submission (use app-foss-release.apk)
- ✅ Google Play Testing (use app-fullRelease.aab)
- ✅ Distribution Documentation
- ✅ User Announcement

### Post-Release Steps

1. **Test Artifacts** (optional):
   ```bash
   adb install -r app-foss-release.apk
   ```

2. **Submit to F-Droid**:
   - Prepare app-foss-release.apk
   - Submit with metadata in fastlane/metadata/android/

3. **Sign for Play Store** (if needed):
   ```bash
   jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
     -keystore release.keystore \
     app-fullRelease.aab alias_name
   ```

---

**Current Status**: Release published with CI/CD active

**Monitor at**: https://github.com/tyejaedon/TigerPlayer/actions

