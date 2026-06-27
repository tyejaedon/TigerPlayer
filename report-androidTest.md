# Android Test Report

Date: 2026-06-27
Type: androidTest

## Scope
This report summarizes androidTest compilation/packaging validation run during M2 queue testing updates.

## Runs So Far

1. Command:
```zsh
./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest --tests com.example.tigerplayer.engine.AdaptiveDspEngineSafetyTest
```
Result: PASS (`BUILD SUCCESSFUL`)
Notes: `compileDebugAndroidTestKotlin` completed successfully. Compose test-rule deprecation warnings were reported for existing android tests.

2. Command:
```zsh
./gradlew :app:assembleDebugAndroidTest
```
Result: Mixed history during broader workspace churn.
Notes:
- Earlier runs (outside the scoped queue/routing files) showed temporary compile/resource merge issues in unrelated in-flight androidTest work.
- A later run in the same period completed successfully (`BUILD SUCCESSFUL`).

## Instrumentation Execution
- No connected-device instrumentation execution was run in this pass.
- This report only reflects androidTest compile/assemble validation outcomes.

## Key Warnings Seen
- Compose test API deprecation warning for `createAndroidComposeRule` in existing tests.
- General AGP deprecation warnings unrelated to scoped queue/routing changes.

