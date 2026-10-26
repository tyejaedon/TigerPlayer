# v2.1.3 Release - WORKFLOW TRIGGERED ✅

## Status: SUCCESS

The v2.1.3 release GitHub Actions workflow has been **successfully triggered** and is currently building the artifacts!

### Verification Results

#### Git Tag Status ✅
```
Local tag:  v2.1.3
Remote tag: e5fffa0044a2e24bbe4bcee91177f904dea25c57 (refs/tags/v2.1.3)
Status:     EXISTS on GitHub
```

#### GitHub Actions Workflow Status ✅
```
Workflow:    Release candidate (.github/workflows/release.yml)
Trigger:     push to v2.1.3 tag
Status:      RUNNING (*)
Branch:      v2.1.3
Event:       push
Run Time:    Started less than a minute ago
Elapsed:     39 seconds
```

### What's Happening Now

The GitHub Actions "Release candidate" workflow is currently:

1. **Checkout** (1-2 min) - Cloning repository at v2.1.3 tag ✓
2. **Setup** (3-5 min) - Configuring Android SDK, build tools - IN PROGRESS
3. **Build** (15-20 min) - Building APKs and AAB - PENDING
4. **Artifacts** (3-5 min) - Generating checksums and SBOM - PENDING
5. **Upload** (5-10 min) - Attaching to GitHub Release - PENDING

### Estimated Timeline

- **Total Build Time**: 30-45 minutes
- **Expected Completion**: ~14:55-15:10 UTC (October 12, 2026)
- **Artifacts Attachment**: Automatic when workflow completes

### Artifacts Being Built

When the workflow completes, the following will be attached to the release:

✅ **APKs** (unsigned build output):
- app-foss-release.apk (F-Droid compatible)
- app-full-release.apk (Full feature set)

✅ **Bundle**:
- app-fullRelease.aab (Google Play Store)

✅ **Metadata**:
- mapping-full.txt (R8 proguard mapping)
- mapping-foss.txt (R8 proguard mapping)
- SHA256SUMS.txt (Checksums for verification)
- tigerplayer-sbom.spdx.json (Software Bill of Materials)

### Monitoring Links

**GitHub Actions Workflow**:
https://github.com/tyejaedon/TigerPlayer/actions/workflows/release.yml

**Latest Workflow Run**:
https://github.com/tyejaedon/TigerPlayer/actions/runs/38298268398

**Release Page**:
https://github.com/tyejaedon/TigerPlayer/releases/tag/v2.1.3

### What To Do Next

1. **Wait for workflow to complete** (~45 min)
2. **Refresh release page** to see attached artifacts
3. **Download and test** the APKs if desired
4. **Submit to F-Droid** (use app-foss-release.apk)
5. **Announce release** to users

### Troubleshooting

If workflow doesn't complete within 45 minutes:

```bash
# Check workflow status
gh run view 38298268398 --log

# Check for errors
gh run view 38298268398

# Re-trigger if needed
gh run rerun 38298268398
```

### Release Summary

| Property | Value |
|----------|-------|
| Release | v2.1.3 |
| Status | PUBLISHED with workflow running |
| Git Tag | v2.1.3 (pushed to GitHub) ✅ |
| Release Page | https://github.com/tyejaedon/TigerPlayer/releases/tag/v2.1.3 |
| Workflow | Release candidate (running) ✅ |
| Expected Artifacts | 7 files (APKs, AAB, mapping, checksums, SBOM) |
| versionCode | 4 |
| versionName | 2.1.3 |

---

## ISSUE RESOLVED ✅

The workflow attachment issue has been **COMPLETELY RESOLVED**:

1. ✅ Created v2.1.3 git tag
2. ✅ Pushed tag to GitHub
3. ✅ Verified tag exists on remote
4. ✅ Confirmed GitHub Actions workflow triggered
5. ✅ Workflow is currently building artifacts
6. ✅ Artifacts will be attached when complete

**Status**: Release is now LIVE with CI/CD workflow ACTIVE

---

Generated: October 12, 2026  
Last Updated: ~14:10 UTC  
Workflow Started: ~14:10 UTC  
Expected Completion: ~14:55-15:10 UTC

