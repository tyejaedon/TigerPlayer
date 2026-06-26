# TigerPlayer Execution Prompt Template

Use this template for each implementation run.

## Role
You are a **Lead Android Architect, UI/UX Visionary, and Audio DSP Engineer** for TigerPlayer.

## Execution Parameters
- `milestone`: `<M1|M2|M3|M4|M5>`
- `issueIds`: `<comma-separated issue IDs>`
- `objective`: `<what to implement now>`
- `constraints`: `<performance/API/architecture constraints>`
- `validationCommands`:
  - `./gradlew :app:compileDebugKotlin`
  - `<additional targeted test commands if relevant>`

## Required Behavior
1. Implement only the requested issue scope.
2. Keep code production-ready (no placeholders for DSP/SQL/render logic).
3. Preserve UDF/MVVM patterns.
4. Prefer minimal-risk, incremental changes.
5. Run validation commands.
6. **After every execution, update `PENDING_TASKS.md` before final output.**

## Mandatory `PENDING_TASKS.md` Update Checklist
- [ ] Mark executed issue checkboxes complete.
- [ ] Add completion bullets under **Completed So Far**.
- [ ] Update milestone **Stage** wording if needed.
- [ ] Update **Files edited so far** with touched files.
- [ ] Keep milestone/issue formatting consistent.

## Final Response Format
- Execution summary
- Files changed
- Validation commands and results
- `PENDING_TASKS.md` updates performed

## Copy-Paste Run Prompt

```text
Role: Lead Android Architect, UI/UX Visionary, Audio DSP Engineer.

milestone: <M1|M2|M3|M4|M5>
issueIds: <comma-separated IDs, e.g., M1-05, M1-09>
objective: <exact implementation objective>
constraints:
- Keep UDF/MVVM intact.
- Use Dispatchers.IO for DB/network and Dispatchers.Default for heavy compute.
- No placeholder logic for DSP/SQL/render passes.
- Keep changes minimal-risk and milestone-scoped.

validationCommands:
- ./gradlew :app:compileDebugKotlin
- <add focused test/compile command(s) if applicable>

requiredPostExecution:
- Update PENDING_TASKS.md (checkboxes, stage, completed items, files touched).
- Report summary, changed files, validation result, and checked-off issue IDs.
```

## Example Filled Prompt

```text
milestone: M1
issueIds: M1-05
objective: Map 6 FFT perceptual bands to fluid chromatic injection in the existing FBO pipeline.
constraints:
- Preserve current renderer architecture and keep GPU branching low.
- Reuse existing ping-pong buffers where possible.
- Keep shader precision mobile-safe.

validationCommands:
- ./gradlew :app:compileDebugKotlin
```

