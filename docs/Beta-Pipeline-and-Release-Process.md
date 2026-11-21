# Beta Pipeline & Release Process

This document outlines the architecture, branch promotion mechanics, semantic version coding, and signing workflow for pre-production beta releases in TigerPlayer.

---

## 1. Pipeline Architecture & Branch Topology

TigerPlayer uses a three-tier branch lifecycle:

```
[sandbox/experimentation]
         │
         │  (Promotion: .github/workflows/promote-to-beta.yml or PR)
         ▼
     [beta] ────► Quality Gates (android-ci.yml, security.yml)
         │  ────► Beta Production Release (.github/workflows/beta-release.yml)
         │        • Calculates monotonic versionCode & versionName
         │        • Builds FOSS & Full Release APKs + AAB
         │        • Signs with official release key (v2 scheme)
         │        • Verifies SHA-256 certificate fingerprint
         │        • Publishes GitHub Pre-Release with SBOM & Checksums
         │
         │  (Hardened Promotion: PR to master)
         ▼
     [master] ──► Production Release (v* tags)
```

### Roles of Branches:
1. **`sandbox/experimentation`**: Staging ground for exploratory adjustments, DSP spikes, and UI/UX experiments (Issue #269). Fast iteration without blocking master.
2. **`beta`**: Pre-production staging branch. Houses candidate features undergoing stabilization and external tester validation.
3. **`master`**: Protected production branch. Direct pushes are disabled; all merges require passing CI checks and PR reviews.

---

## 2. Beta Version Coding Specification

Android OS and Google Play require that `versionCode` is a positive 32-bit integer ($1 \le \text{versionCode} \le 2{,}147{,}483{,}647$) that strictly increases with every installed APK.

To ensure beta builds can be continuously updated by testers and cleanly superseded by official production releases, TigerPlayer employs the **SemVer Multiplier Formula**:

$$\text{versionCode} = \text{MAJOR} \times 1{,}000{,}000 + \text{MINOR} \times 10{,}000 + \text{PATCH} \times 100 + \text{BUILD\_INDEX}$$

- For candidate betas (`-beta.X`), `BUILD_INDEX` is the 1-based beta index $1 \le \text{BUILD\_INDEX} \le 98$.
- For production releases, `BUILD_INDEX` defaults to `99` so production builds cleanly supersede candidate betas.
- **Historical Downgrade Protection Floor:** Pre-release builds `v2.2.0-beta.1` through `v2.2.0-beta.19` occupied `2020001`–`2020019`, and production releases `v2.1.6`–`v2.1.9` occupied `2020020`–`2020030`. Accordingly, a strict historical floor of **`2020030`** is enforced across `app/build.gradle.kts` and `release.yml`, and `beta-release.yml` / `app/build.gradle.kts` shift `2.2.0-beta.20+` by `+11` (`2020011 + betaNum`, strictly below `2020099`) so `v2.2.0-beta.20` (`versionCode = 2020031`) monotonically supersedes `v2.1.9` (`2020030`) without triggering Android OS `[INSTALL_FAILED_VERSION_DOWNGRADE]`, while upcoming `v2.2.0` production (`versionCode = 2020099`) and `v2.3.0` (`versionCode = 2030099`) cleanly supersede all betas.

### Example Progression:

| Release Phase | Tag / Version Name | Calculated `versionCode` | Android Upgrade Compatibility |
| :--- | :--- | :--- | :--- |
| **Legacy Baseline** | `v2.1.5` | `6` | Installed baseline |
| **Beta 1** | `v2.2.0-beta.1` | `2020001` | Upgradeable over baseline ($2{,}020{,}001 > 6$) |
| **Beta 19** | `v2.2.0-beta.19` | `2020019` | Legacy pre-`v2.1.6` beta ceiling |
| **Production v2.1.6** | `v2.1.6` | `2020020` | Supersedes Beta 19 via floor ($2{,}020{,}020 > 2{,}020{,}019$) |
| **Production v2.1.9** | `v2.1.9` | `2020030` | Supersedes v2.1.8.5 via floor ($2{,}020{,}030 > 2{,}020{,}025$) |
| **Beta 20+** | `v2.2.0-beta.20` | `2020031` | Supersedes v2.1.9 via `+11` shift ($2{,}020{,}031 > 2{,}020{,}030$, $< 2{,}020{,}099$) |
| **Production Final**| `v2.2.0` | `2020099` | Supersedes v2.1.9 and all preceding Betas ($2{,}020{,}099 > 2{,}020{,}031$) |
| **Future Production**| `v2.3.0` | `2030099` | Monotonically supersedes v2.2.0 ($2{,}030{,}099 > 2{,}020{,}099$) |

In Gradle (`app/build.gradle.kts`), version codes and names can be injected via:
- Gradle CLI properties: `-PVERSION_CODE=2020031 -PVERSION_NAME=2.2.0-beta.20`
- Environment variables: `BETA_VERSION_CODE` / `VERSION_CODE` and `BETA_VERSION_NAME` / `VERSION_NAME`
- Automatic dynamic derivation in `app/build.gradle.kts` using the SemVer multiplier formula with the `2020030` historical floor and `2.2.0-beta.20+` shift.

---

## 3. Promoting Changes: Sandbox to Beta

Promotion from `sandbox/experimentation` to `beta` is automated via GitHub Actions:

### Automated Promotion via GitHub Actions
1. Navigate to **Actions** $\rightarrow$ **Promote to Beta** (`promote-to-beta.yml`).
2. Click **Run workflow**.
3. Options:
   - `source_branch`: `sandbox/experimentation` (default)
   - `target_branch`: `beta` (default)
   - `auto_merge`: `false` (creates/updates PR for review) or `true` (fast-forward merges if tests pass).

### Manual Promotion via Git CLI
```powershell
git fetch origin
git checkout beta
git merge origin/sandbox/experimentation
git push origin beta
```

---

## 4. Beta Production Release Workflow

The workflow `.github/workflows/beta-release.yml` triggers automatically on:
- Pushing new commits to the `beta` branch.
- Pushing a beta tag matching `v*-beta*` (e.g. `git tag v2.2.0-beta.1 && git push origin v2.2.0-beta.1`).
- Manual trigger via **Run workflow** (`workflow_dispatch`).

### Security & Signing Guardrails:
1. **Keystore Integrity**: Decodes `RELEASE_KEYSTORE_B64` into a temporary `release.keystore`.
2. **Certificate Verification**: Extracts the alias matching expected SHA-256 fingerprint:
   ```
   0aabbc79e9db6666f1f814487d5607865f2fc643ca940217ff941947c0fb0c9b
   ```
3. **Signature Scheme**: Uses `apksigner` enforcing `--v1-signing-enabled false --v2-signing-enabled true`.
4. **Post-Sign Audit**: Re-verifies the signed APKs with `apksigner verify --verbose --print-certs` before publishing.
5. **Leak Prevention**: Executes `rm -f release.keystore` in an `always()` cleanup step.

---

## 5. Artifact Distribution

Each published Beta release creates a **GitHub Pre-Release** containing:
- `TigerPlayer-<version>-full-signed.apk`: Sideloadable APK including Spotify App Remote.
- `TigerPlayer-<version>-foss-signed.apk`: Fully open-source sideloadable APK with zero proprietary binaries.
- `TigerPlayer-<version>-full.aab`: Distributable Android App Bundle.
- `SHA256SUMS-beta-signed.txt`: Cryptographic checksums of all signed assets.
- `tigerplayer-beta-sbom.spdx.json`: SPDX-compliant Software Bill of Materials.
