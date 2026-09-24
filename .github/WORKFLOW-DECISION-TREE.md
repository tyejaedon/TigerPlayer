# Agent Workflow Decision Tree & Visual Reference

```
╔═══════════════════════════════════════════════════════════════════════════════╗
║                     AGENT WORK AUTHORIZATION CHECKLIST                         ║
║                                                                                 ║
║  START HERE: Am I ready to write code?                                         ║
╚═══════════════════════════════════════════════════════════════════════════════╝

   ┌─────────────────────────────────┐
   │ Does a formal GitHub issue      │
   │ (#<ID>) already exist?          │
   └────────────┬────────────────────┘
                │
        ┌───────┴────────┐
        │                │
       NO               YES
        │                │
        ▼                ▼
   ┌─────────────┐   ┌────────────────────────────┐
   │ CREATE it   │   │ Is it assigned to me?      │
   │ (or find    │   └────────────┬───────────────┘
   │  existing)  │                │
   └─────────────┘        ┌───────┴─────────┐
        │                 │                 │
        │                NO               YES
        │                 │                 │
        │                 ▼                 ▼
        │            ┌──────────┐   ┌────────────────────┐
        │            │ Ask for  │   │ Does it have       │
        │            │ assign   │   │ acceptance         │
        │            │ment      │   │ criteria?          │
        │            └──────────┘   └──────┬─────────────┘
        │                 │                 │
        │                 └─────────┬───────┘
        │                           │
        │                    ┌──────┴──────┐
        │                    │             │
        │                   NO            YES
        │                    │             │
        │                    ▼             ▼
        │            ┌──────────────┐  ┌──────────────┐
        │            │ Add criteria │  │ Is it tied   │
        │            │ to issue     │  │ to milestone?│
        │            └──────────────┘  └──────┬───────┘
        │                 │                    │
        │                 └─────┬──────┬───────┘
        │                       │      │
        │                       │    NO
        │                       │      │
        │                       ▼      ▼
        │                      YES  ┌──────────────────┐
        │                       │   │ Create milestone │
        │                       │   │ (semantic ver)   │
        │                       │   │ and assign issue │
        │                       │   └──────────────────┘
        │                       │      │
        └───────────────────────┴──────┘
                                │
                ┌───────────────▼───────────────┐
                │  PHASE 0 COMPLETE             │
                │  ✅ Issue exists              │
                │  ✅ Assigned to you           │
                │  ✅ Has criteria              │
                │  ✅ Tied to milestone         │
                │  ✅ Issue ID known            │
                └───────────────┬───────────────┘
                                │
                                ▼
                ┌───────────────────────────────┐
                │  git checkout master          │
                │  git pull --ff-only           │
                │  git checkout -b              │
                │    fix/issue-<ID>-<slug>      │
                └───────────────┬───────────────┘
                                │
                                ▼
                ┌───────────────────────────────┐
                │  IMPLEMENT THE FIX            │
                │  • Write code                 │
                │  • Add tests                  │
                │  • Follow architecture        │
                │  • Run quality gate tools     │
                └───────────────┬───────────────┘
                                │
                                ▼
                ┌───────────────────────────────┐
                │  ./gradlew lint test          │
                │  ✅ All pass?                 │
                └────────────┬──────────────────┘
                             │
                     ┌───────┴────────┐
                     │                │
                    NO              YES
                     │                │
                     ▼                ▼
                ┌─────────────┐  ┌─────────────────┐
                │ Fix errors  │  │ git add <path>  │
                │ Re-run      │  │ git commit -m   │
                │ tests       │  │ (with Closes)   │
                └─────┬───────┘  └────────┬────────┘
                      │                   │
                      └─────────┬─────────┘
                                │
                                ▼
                ┌────────────────────────────────┐
                │  git push -u origin            │
                │    fix/issue-<ID>-<slug>       │
                └────────────┬───────────────────┘
                             │
                             ▼
                ┌─────────────────────────────────┐
                │  gh pr create                   │
                │  --title "... (closes #<ID>)"   │
                │  --body "... Closes #<ID> ..."  │
                │  --base master                  │
                └────────────┬────────────────────┘
                             │
                             ▼
                ┌─────────────────────────────────┐
                │  GitHub auto-closes issue       │
                │  ✅ DONE                        │
                └─────────────────────────────────┘
```

---

## Quick Reference: Branch Naming

```
fix/issue-<ID>-<kebab-case-slug>
│    │      │   │
│    │      │   └─ Descriptive, lowercase, hyphens only
│    │      └────── GitHub issue number (e.g., 42)
│    └───────────── Required keyword: "issue-"
└────────────────── Type: fix, feat, docs, chore, refactor, etc.
```

**Examples:**
- `fix/issue-42-null-auth-token`
- `feat/issue-99-waveform-visualizer`
- `docs/issue-15-update-readme`
- `chore/issue-7-upgrade-gradle`

---

## Commit Message Template

```
fix(scope): concise imperative description

- Key change 1
- Key change 2
- Root cause if applicable

Closes #<ID>
```

**Example:**
```
fix(auth): prevent null auth token from caching

- Store auth token check at interceptor, not in cache layer
- Add test covering missing token edge case
- Resolves silent playback failures on token rotation

Closes #42
```

**Rules:**
- First line: < 50 characters
- Line 2: blank
- Lines 3+: Wrapped at 72 characters
- **Last line MUST be:** `Closes #<ID>`
- Types: `fix`, `feat`, `docs`, `chore`, `refactor`, `perf`, `test`

---

## PR Title & Body Template

```powershell
gh pr create \
  --title "fix(scope): description (closes #<ID>)" \
  --body "### Summary of Changes
- Change 1
- Change 2

### Verification
- All linters, unit tests, and regression suites passed locally.

### Milestone
- Tied to issue milestone for release tracking.

Closes #<ID>" \
  --base master
```

**Critical Points:**
- Title MUST include `(closes #<ID>)`
- Body MUST include `Closes #<ID>` on its own line
- GitHub sees this and auto-closes the issue on merge

---

## The Four Sacred Artifacts

Every completed task creates these four linked items:

```
┌─────────────────────────────────────────────────┐
│  Artifact 1: GitHub Issue                       │
│  • Number: #<ID>                               │
│  • Status: Formal description + criteria       │
│  • Assigned: To the agent                       │
│  • Milestone: Set (not null)                    │
└────────────────┬────────────────────────────────┘
                 │
                 └──→ Issue ID determines branch name
                      │
                      ▼
┌─────────────────────────────────────────────────┐
│  Artifact 2: Git Branch                         │
│  • Name: fix/issue-<ID>-<slug>                 │
│  • Source: Created locally from master         │
│  • Status: Contains all implementation commits  │
└────────────────┬────────────────────────────────┘
                 │
                 └──→ Branch pushed to remote
                      │
                      ▼
┌─────────────────────────────────────────────────┐
│  Artifact 3: Git Commit(s)                      │
│  • Message: Conventional format                │
│  • Footer: Closes #<ID>                        │
│  • Staging: Explicit paths, no bulk `add .`    │
└────────────────┬────────────────────────────────┘
                 │
                 └──→ Commits referenced in PR
                      │
                      ▼
┌─────────────────────────────────────────────────┐
│  Artifact 4: Pull Request                       │
│  • Title: Includes (closes #<ID>)             │
│  • Body: Includes Closes #<ID> line           │
│  • Base: master                                 │
│  • Status: Merges via GitHub (no direct push)  │
└────────────────┬────────────────────────────────┘
                 │
                 └──→ GitHub auto-closes issue
                      │
                      ▼
         ✅ Issue marked CLOSED
         ✅ Milestone count updated
         ✅ Full traceability: Issue→Branch→Commit→PR
```

**If ANY artifact is missing or malformed, the workflow breaks.**

---

## Do Not Do This

| ❌ | Violation | Impact |
|---|---|---|
| ❌ | `git add .` | Leaks secrets/keystores/generated files |
| ❌ | `git commit -m "..."` (no Closes) | Issue stays open after PR merge |
| ❌ | `git push origin master` | Rejected by GitHub; PR required |
| ❌ | Branch with no issue ID | Lost traceability |
| ❌ | PR without `closes #` | Issue not auto-closed |
| ❌ | Code on `master` branch | Repository rule violations |
| ❌ | Missing milestone | Release planning fails |
| ❌ | Unrelated refactoring | Out-of-scope (AIRP Phase 2 violation) |

---

## Workflow State Machine

```
START
  │
  ├─ Phase 0: Issue validation
  │  └─ Issue exists? ✅
  │  └─ Assigned? ✅
  │  └─ Has criteria? ✅
  │  └─ Has milestone? ✅
  │  └─ ID known? ✅
  │
  ├─ Phase 1: Environment setup
  │  └─ Checkout master
  │  └─ Sync remote
  │  └─ Create branch from issue ID
  │
  ├─ Phase 2: Implementation
  │  └─ Write code
  │  └─ Add tests
  │  └─ Minimal scope
  │
  ├─ Phase 3: Verification
  │  └─ Run linters
  │  └─ Run tests
  │  └─ Compile checks
  │  └─ Zero failures ✅
  │
  ├─ Phase 4: Commit & PR
  │  └─ git add <paths>
  │  └─ git commit (with Closes #<ID>)
  │  └─ git push
  │  └─ gh pr create (with closes #<ID>)
  │  └─ GitHub auto-closes issue
  │
  ├─ Phase 5: Cleanup
  │  └─ Local tree clean
  │  └─ Emit success status
  │
END
  │
  └─ Issue closed
  └─ Branch merged to master
  └─ Full traceability complete
```

---

## Common Errors & Recovery

| Error | Cause | Recovery |
|---|---|---|
| `GH013: Repository rule violations` | Tried to push to master | Use feature branch + PR |
| `Issue #42 didn't auto-close` | Missing/misspelled `Closes #42` | Re-push PR with corrected body |
| `I committed on master` | Skipped Phase 0 → Phase 1 | See git-workflow.instructions.md Rule 3 |
| `No milestone for this issue` | Skipped Phase 0 milestone check | Create via GitHub, assign issue, re-push |
| `Branch name doesn't have issue ID` | Created branch without issue | Rename locally, re-push (before review) |

---

## File References

- **Quick start:** `AGENT-WORKFLOW-CHECKLIST.md`
- **Full lifecycle:** `issue-resolution-protocol.instructions.md`
- **Git rules:** `git-workflow.instructions.md`
- **Project guide:** `AGENTS.md`
- **This file:** `.github/WORKFLOW-DECISION-TREE.md`

