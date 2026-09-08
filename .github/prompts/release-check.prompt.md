---
mode: agent
description: Pre-release readiness audit - verifies the release blockers, build config, secrets hygiene and store requirements before cutting a build.
---

# Release readiness check

Audit the repository for release readiness. **Report findings; do not fix anything** unless
explicitly asked. Produce a go / no-go verdict.

## 1. Release blockers (must all be resolved)

Check the state of each and report open/closed:

```powershell
gh issue list --milestone "v2.1 Foundation" --label P0-blocker --state all
```

| Issue | Blocker |
|---|---|
| #42 | Listening stats inflated — analytics present fabricated data |
| #43 | Room has no migrations — upgrade crash for every existing user |
| #44 | Navidrome URLs malformed + expiring tokens cached |
| #45 | `versionCode = 1`, `applicationId` is `com.example.tigerplayer` |
| #46 | Spotify client secret shipped in the APK |

## 2. Build configuration

Inspect `app/build.gradle.kts` and confirm:

- [ ] `applicationId` does **not** start with `com.example` — stores reject it
- [ ] `versionCode` is greater than any previously published build and increases monotonically
- [ ] `versionName` matches the intended release
- [ ] `isMinifyEnabled` and `isShrinkResources` are `true` for release
- [ ] No `buildConfigField` contains an authentication secret (API keys used only for rate limiting are acceptable)
- [ ] No new `resolutionStrategy { force(...) }` entries were added (issue #75)

## 3. Secrets & signing hygiene

```powershell
git ls-files | Select-String -Pattern 'secrets\.properties|local\.properties|\.jks$|\.keystore$|google-services\.json'
git --no-pager log --all --oneline -- secrets.properties local.properties "*.jks"
```

- [ ] Nothing matched above — no credential material tracked, now or historically
- [ ] `.gitignore` still contains the pattern-based security block
- [ ] Signing credentials come from environment variables or an untracked `keystore.properties`

## 4. Manifest & security

- [ ] `usesCleartextTraffic` is not enabled app-wide (issue #47)
- [ ] Permissions are minimal and justified; each dangerous permission has a runtime rationale
- [ ] Receivers registered in code use an explicit export flag (issue #48)
- [ ] `foregroundServiceType="mediaPlayback"` is declared with the matching permission

## 5. Quality gate

```powershell
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleRelease
```

- [ ] All three pass
- [ ] **Release build installed and smoke-tested on a physical device** — R8 stripping and missing
      ProGuard keep rules do not surface in debug builds
- [ ] Playback, Navidrome sync, and Spotify auth verified on device (CI uses placeholder secrets,
      so a green CI run does **not** prove cloud integrations work)

## 6. Data safety

- [ ] If the Room `version` changed, a migration is registered **and** a data-survival test passes
- [ ] Upgrade tested by installing the previous release, generating data, then installing the new build

## Output

A table of every check with pass / fail / not-applicable, followed by an explicit
**GO** or **NO-GO** verdict. List any NO-GO item as a numbered blocker with the file and line.

