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
- CI uses JDK 17 and `platforms;android-37`, matching `compileSdk = 37`.
- Pull requests pass lint, JVM tests, R8, an unsigned release bundle, and deterministic Room/app-context instrumentation smoke tests.
- Gradle checksum verification is mandatory in CI using `--dependency-verification=strict`; update `gradle/verification-metadata.xml` only with an intentional reviewed dependency change.
- Pull-request workflows must not receive release-signing keys or production credentials.
## Release process
`Release candidate` runs only for a `v*` tag or manual dispatch. It produces unsigned evidence: a release AAB, R8 mapping file, SHA-256 manifest, and SPDX JSON SBOM.
Create a protected `release-candidate` Environment with a release-manager reviewer. Add a `v*` tag ruleset restricting create/update/delete permissions to release managers and blocking force-updates. Tags must point to commits that passed required PR checks.
The workflow never reads keystores or signing passwords. Any future signed delivery must use a reviewer-protected `production` Environment, narrowly scoped secrets, `always()` cleanup, and an explicit approval after internal-track verification.
## Incident response
A secret-scan finding blocks the pull request. Revoke exposed credentials first, then remove the secret. Never suppress a real credential finding merely to restore a green check.
