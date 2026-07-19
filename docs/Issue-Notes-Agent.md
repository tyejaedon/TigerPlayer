# Issue Notes Agent

## Purpose

Maintain a reliable, evidence-based status and notes log for all TigerPlayer GitHub issues.

Primary tracking file:
- `docs/Issue-Status-Rundown-2026-07-18.md`

## Agent Contract

When updating issue notes, always do the following:

1. Sync board status from GitHub issue list/project board (Done/In Progress/Todo).
2. Verify implementation evidence in code (file paths, symbols, tests, build output).
3. Update `Comments/Notes` for each issue with short, factual progress text.
4. Keep duplicate trackers explicitly marked (currently #33/#34 and #38/#39).
5. Never mark `Done` without at least one code/test/build evidence pointer.

## Required Update Checklist

- [ ] Refresh status column from latest board snapshot.
- [ ] For each issue, add or refresh one-line note in `Comments/Notes`.
- [ ] If status changed to `Done`, include evidence in the note:
  - code path(s), and
  - validation command/result where possible.
- [ ] If status is `In Progress`, include current implementation area and next step.
- [ ] If status is `Todo`, include planned starting point (file/module).
- [ ] Recompute summary counts and milestone rollups at top of rundown file.
- [ ] Stamp date in the document title or top section when changed.

## Notes Style Guide

Use short notes in this pattern:
- `Done`: `Implemented in <path>; validated via <command/task>.`
- `In Progress`: `Working in <path/module>; next step: <next step>.`
- `Todo`: `Planned in <path/module>; not started.`

Keep notes to 1-2 lines each.

## Evidence Rules

Minimum evidence by status:

- `Done`
  - At least one code reference path, and
  - One validation result (`assembleDebug`, `testDebugUnitTest`, `lintDebug`, or feature-specific test)
- `In Progress`
  - One active code area being touched
- `Todo`
  - One intended code area for future implementation

## Suggested Commands

Use these as default verification commands after updates:

```zsh
cd /Users/tyejaedon/StudioProjects/TigerPlayer
./gradlew :app:assembleDebug --console=plain
./gradlew :app:testDebugUnitTest --console=plain
./gradlew :app:lintDebug --console=plain
```

For release-hardening issues:

```zsh
cd /Users/tyejaedon/StudioProjects/TigerPlayer
./gradlew :app:minifyReleaseWithR8 --console=plain
./gradlew :app:assembleRelease --console=plain
```

## Update Cadence

Run this update process:
- after any feature merge,
- after any issue status change,
- before release candidate cuts.

