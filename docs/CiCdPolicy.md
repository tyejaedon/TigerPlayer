# CI/CD and Repository Policy
Configure GitHub protection before a production release.
## Required pull-request checks
Protect `master`, require pull requests, stale-approval dismissal, conversation resolution, and block force-pushes and branch deletion. Require:
- `Android CI / Repository hygiene`
- `Android CI / Lint`
- `Android CI / Unit tests, R8, and bundle`
- `Android CI / Android instrumentation smoke`
- `Supply-chain security / Secret content scan`
- `Supply-chain security / Dependency review`
- `Supply-chain security / Dependency checksum verification`
## Supply-chain and Android build policy
- Third-party Actions use immutable commit SHAs annotated with release tags; Dependabot updates Actions and Gradle dependencies weekly.
- CI uses JDK 17 and `platforms;android-36`, matching `compileSdk = 36`.
- Pull requests pass lint, JVM tests, R8, an unsigned release bundle, and deterministic Room/app-context instrumentation smoke tests.
- Gradle checksum verification is mandatory in CI using `--dependency-verification=strict`; update `gradle/verification-metadata.xml` only with an intentional reviewed dependency change.
- Pull-request workflows must not receive release-signing keys or production credentials.
## Release process
`Release candidate` runs only for a `v*` tag or manual dispatch. It produces unsigned evidence: a `full`-flavor release APK and AAB, a `foss`-flavor release APK, R8 mapping files for both flavors, a SHA-256 manifest, and an SPDX JSON SBOM.
Create a protected `release-candidate` Environment with a release-manager reviewer. Add a `v*` tag ruleset restricting create/update/delete permissions to release managers and blocking force-updates. Tags must point to commits that passed required PR checks.
For a `v*` tag push, the same unsigned APK/AAB/mapping/checksum/SBOM set for both flavors is also attached to the GitHub Release for that tag (created if it doesn't already exist), clearly labeled as unsigned evidence — not a production distribution artifact. `workflow_dispatch` runs still upload the workflow artifact only, since there is no tag to attach to.
The workflow never reads keystores or signing passwords. Any future signed delivery must use a reviewer-protected `production` Environment, narrowly scoped secrets, `always()` cleanup, and an explicit approval after internal-track verification.
## Incident response
A secret-scan finding blocks the pull request. Revoke exposed credentials first, then remove the secret. Never suppress a real credential finding merely to restore a green check.
