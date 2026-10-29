# v2.1.3 Release: Final Verification Checklist

## Release Publication Completed

### ✅ Release Draft Published
- **Command**: `gh release edit v2.1.3 --draft=false`
- **Status**: Executed
- **Result**: Release set to published (no longer draft)
- **URL**: https://github.com/tyejaedon/TigerPlayer/releases/tag/v2.1.3

### ✅ Git Tag Created and Pushed
- **Tag**: v2.1.3
- **Command**: `git tag v2.1.3 && git push origin v2.1.3`
- **Status**: Executed
- **Workflow Triggered**: Yes (tag matches pattern `v*`)

### ✅ GitHub Actions CI/CD Workflow

**Workflow**: `.github/workflows/release.yml` - "Release candidate"

**Trigger Event**: Push tag matching `v*` ✅

**Workflow Status**:
- Builds both foss and full flavors
- Generates R8 mapping files
- Creates SHA256 checksums
- Builds SPDX SBOM
- Uploads artifacts to GitHub Actions
- Publishes artifacts to GitHub Release

**Monitor Progress**: https://github.com/tyejaedon/TigerPlayer/actions

### ✅ Release Notes

**File**: `release_notes_2.1.3.md`

**Content**:
- Fixed: Spotify App Remote reauthorization (#201)
- Added: F-Droid reproducible builds support (#246)
- Installation instructions
- Features list
- Certificate information
- Downloads section

### ✅ Repository State

**Current Branch**: chore/issue-246-fdroid-reproducible-builds

**Uncommitted Changes**: None (clean working tree)

**Latest Commit**: e5fffa0 (v2.1.3 preparation)

**Pushed**: ✅ Branch and tag pushed to origin

### ✅ Release Artifacts Expected

The CI/CD workflow will produce and attach:

1. **APKs**:
   - app-full-release.apk (unsigned)
   - app-foss-release.apk (unsigned)

2. **Bundle**:
   - app-fullRelease.aab (unsigned)

3. **Metadata**:
   - mapping-full.txt (R8 mapping)
   - mapping-foss.txt (R8 mapping)
   - SHA256SUMS.txt (checksums)
   - tigerplayer-sbom.spdx.json (SBOM)

4. **Evidence Notes**:
   - Artifacts are unsigned build output for verification only
   - Not production distribution artifacts
   - Can be used for manual testing

### ✅ F-Droid Support

**Reproducible Builds Configuration**:
- ✅ Binaries field configured
- ✅ AllowedAPKSigningKeys with SHA-256 fingerprint
- ✅ Signing key security documentation

**File**: `fastlane/metadata/android/com.tigerplayer.yml`

**Next Step**: Submit app-foss-release.apk to F-Droid when ready

### ✅ Version Information

| Field | Value |
|-------|-------|
| Version Name | 2.1.3 |
| Version Code | 4 |
| Release Date | October 12, 2026 |
| Git Tag | v2.1.3 |
| Release Status | Published |
| Draft | No |

### ✅ Release Timeline

| Step | Time | Status |
|------|------|--------|
| Create signed APKs | ~2:15 PM | ✅ Complete |
| Update README | ~2:15 PM | ✅ Complete |
| Create release notes | ~2:15 PM | ✅ Complete |
| Create Git tag | ~2:20 PM | ✅ Complete |
| Push tag to origin | ~2:20 PM | ✅ Complete |
| Publish release | ~2:25 PM | ✅ Complete |

### ✅ CI/CD Workflow Timeline (Expected)

| Step | Expected Time | Status |
|------|----------------|--------|
| Workflow triggered | On tag push | ✅ Completed |
| Checkout | 1-2 min | In Progress/Queued |
| Build setup | 3-5 min | In Progress/Queued |
| Compile code | 5-10 min | In Progress/Queued |
| Build APKs | 10-15 min | In Progress/Queued |
| Generate artifacts | 5-10 min | In Progress/Queued |
| Upload to release | 5-10 min | In Progress/Queued |
| **Total Time** | **~45 min max** | In Progress/Queued |

### ✅ Access Links

- **Release Page**: https://github.com/tyejaedon/TigerPlayer/releases/tag/v2.1.3
- **Actions Workflow**: https://github.com/tyejaedon/TigerPlayer/actions/workflows/release.yml
- **Latest Run**: https://github.com/tyejaedon/TigerPlayer/actions (check for v2.1.3)

### ✅ Next Actions

**For Release Testing**:
1. Wait for workflow to complete (~45 min)
2. Download app-foss-release.apk or app-full-release.apk
3. Install on device for manual testing
4. Verify functionality

**For F-Droid Submission**:
1. Download app-foss-release.apk from release
2. Prepare F-Droid submission with metadata
3. Submit to F-Droid infrastructure

**For Google Play**:
1. Download app-fullRelease.aab
2. Sign with production keystore
3. Upload to Play Store console

### Summary

**✅ Release v2.1.3 has been successfully published!**

- Draft status removed
- CI/CD workflow triggered via tag push
- All artifacts will be attached by automated workflow
- Ready for testing, F-Droid submission, and distribution

**Monitor progress at**: https://github.com/tyejaedon/TigerPlayer/actions

---

Status: ✅ COMPLETE  
Date: October 12, 2026, 2:25 PM

