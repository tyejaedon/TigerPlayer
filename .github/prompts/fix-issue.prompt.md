---
mode: agent
description: Resolve a GitHub issue end-to-end under the Autonomous Issue Resolution Protocol (AIRP).
---

# Fix issue #${input:issueNumber:Issue number to resolve (e.g. 42)}

Resolve issue **#${input:issueNumber}** in the TigerPlayer repository by executing the
**Autonomous Issue Resolution Protocol** defined in
`.github/instructions/issue-resolution-protocol.instructions.md`.

Follow all five phases in strict order. Do not skip a phase gate.

## Before you start

1. Read the issue: `gh issue view ${input:issueNumber} --comments`
2. Read `AGENTS.md` for the quality toolchain commands and repository guardrails.
3. Check the issue for a **"Blocked by"** comment. If a blocking issue is still open,
   **stop and report** rather than working around it.

## Phase reminders specific to this repository

**Phase 1 —** The working tree must be clean. This repo contains an untracked release keystore and
`secrets.properties`; never resolve a dirty tree with `git add -A` or `git stash` of those files.

**Phase 2 —** Respect the layering in `AGENTS.md` section 4:
`UI -> ViewModel -> Engine -> Repository -> DataSource`. Consult the scoped instruction file for the
area you are touching (`audio-playback`, `data-layer`, `gradle-build`, `testing`).

If the change touches a Room entity, the migration gate in
`.github/instructions/data-layer.instructions.md` is mandatory — version bump, committed schema,
registered migration, and a migration test asserting data survives.

**Phase 3 —** Run on Windows with `.\gradlew.bat`:

```powershell
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

A failing test is a finding. Never delete, `@Ignore`, or weaken a test to make the gate pass.

**Phase 4 —** Stage explicit paths only. The commit message must end with `Closes #${input:issueNumber}`.

**Phase 5 —** Leave the tree clean and emit the structured completion payload.

## Output

Report which phase you reached, the verification results, and the PR URL.
If you stopped early, state the exact gate that failed and why.

