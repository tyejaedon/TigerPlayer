# v2.1.3 Release Publication Summary

## Date: October 12, 2026

### Release Publication Status

**Command Executed**: `gh release edit v2.1.3 --draft=false`

This command sets the GitHub release as published (no longer a draft).

### GitHub Actions CI/CD Workflow Triggered

The v2.1.3 release will automatically trigger the following workflow:

**Workflow File**: `.github/workflows/release.yml`

**Trigger Events**:
- ✅ Push tag `v2.1.3` (ALREADY EXECUTED)
- Manual workflow dispatch (optional)

**Workflow Steps**:
1. **Checkout**: Clone repository at v2.1.3 tag
2. **Android Build Setup**: Configure SDK, cache, build tools
3. **Dependency Verification**: Validate dependency integrity
4. **Build R8/Minified Releases**:
   - `./gradlew :app:minifyFullReleaseWithR8`
   - `./gradlew :app:minifyFossReleaseWithR8`
5. **Build App Bundle**: Full flavor AAB for Play Store
6. **Assemble APKs**:
   - `./gradlew :app:assembleFullRelease`
   - `./gradlew :app:assembleFossRelease`
7. **Generate Artifacts**:
   - SHA256 checksums (SHA256SUMS.txt)
   - R8 mapping files (mapping-full.txt, mapping-foss.txt)
   - SPDX SBOM (tigerplayer-sbom.spdx.json)
8. **Upload GitHub Artifacts**: Store in GitHub Actions for 30 days
9. **Publish to GitHub Release**: Attach all artifacts to the v2.1.3 release

### Release Artifacts Attached to GitHub Release

The CI/CD workflow will attach the following to the v2.1.3 release:

**APKs**:
- `app-full-release.apk` (with Spotify integration)
- `app-foss-release.apk` (F-Droid compatible)

**App Bundle**:
- `app-fullRelease.aab` (for Google Play Store)

**Metadata**:
- `mapping-full.txt` (R8 proguard mapping for full flavor)
- `mapping-foss.txt` (R8 proguard mapping for foss flavor)
- `SHA256SUMS.txt` (Checksums for all built artifacts)
- `tigerplayer-sbom.spdx.json` (Software Bill of Materials)

### Release Information

**GitHub Release URL**: https://github.com/tyejaedon/TigerPlayer/releases/tag/v2.1.3

**Release Title**: TigerPlayer v2.1.3

**Release Notes** (included in release body):
- Spotify App Remote reauthorization UI improvements (#201)
- F-Droid reproducible builds support (#246)
- Signing key security documentation

**Release Status**: PUBLISHED (draft=false)

**Certificate Information**:
- SHA-256: `0aabbc79e9db6666f1f814487d5607865f2fc643ca940217ff941947c0fb0c9b`

### CI/CD Workflow Status

**Repository**: tyejaedon/TigerPlayer

**Workflow Name**: Release candidate

**Run Trigger**: Tag push `v2.1.3`

**Expected Runtime**: ~45 minutes (max timeout)

**Runs On**: ubuntu-latest

**Permissions**: 
- contents: write (for GitHub Release publishing)
- No signing secrets (unsigned build evidence)

### Release Configuration

**F-Droid Submission**:
- Use: `app-foss-release.apk` (unsigned)
- F-Droid infrastructure independently builds and signs the foss flavor from source
- Metadata in: `fastlane/metadata/android/com.tigerplayer.yml`

**Google Play Store**:
- Use: `app-fullRelease.aab` (for dynamic delivery)
- Requires signing before upload

**Direct Installation / Testing**:
- Use: `app-full-release.apk` or `app-foss-release.apk` (unsigned)
- Can be sideloaded for testing

### Workflow Decision Tree

The release workflow includes logic to handle:
- Direct tag push (most common): triggers on `push: tags: ["v*"]`
- Manual backfill: `workflow_dispatch` with ref input to re-publish artifacts for an existing tag
- Multiple flavor support: builds and attaches both full and foss flavors

### Post-Publication Steps

1. **Monitor Workflow**: Check https://github.com/tyejaedon/TigerPlayer/actions for workflow run
2. **Verify Release Assets**: Confirm all APKs, AAB, and checksums are attached
3. **Test Artifacts**: Download and test the unsigned APKs if needed
4. **F-Droid Submission**: Submit foss flavor to F-Droid (in separate process)
5. **Google Play Upload**: (Optional) Upload AAB to Play Store after signing

### Release Summary

✅ **Release Title**: TigerPlayer v2.1.3
✅ **Git Tag**: v2.1.3 (pushed to origin)
✅ **Draft Status**: Published (no longer draft)
✅ **Release Notes**: Included (from release_notes_2.1.3.md)
✅ **CI/CD Triggered**: Yes (via tag push)
✅ **Artifacts Status**: GitHub Actions workflow building unsigned artifacts
✅ **Reproducible Builds**: F-Droid support enabled with AllowedAPKSigningKeys
✅ **Signing Key**: Secured with backup procedures documented

---

**Status**: ✅ COMPLETE - Release is published and CI/CD is active

**Next Step**: Monitor workflow at https://github.com/tyejaedon/TigerPlayer/actions

---

Generated: 2026-10-12

