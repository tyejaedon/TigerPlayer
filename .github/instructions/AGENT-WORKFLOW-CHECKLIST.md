# Agent Workflow Checklist: Issue-First Protocol

**This is the mandatory execution sequence for all agents. Do not deviate.**

---

## ✋ STOP: Before You Write Any Code

### Mandatory Pre-Implementation Steps

- [ ] **Issue Exists:** A formal GitHub issue with a number (e.g., `#42`) exists and describes the work.
- [ ] **Issue Is Assigned:** The issue is assigned to you (or explicitly awaiting your work).
- [ ] **Issue Has Criteria:** The issue description includes clear acceptance criteria and/or reproduction steps.
- [ ] **Milestone Is Set:** The issue is tied to a milestone. If no milestone exists:
  - Create one (e.g., `v2.1.0`, `v2.1.1-hotfix`).
  - Assign the issue to it.
- [ ] **You Have the Issue ID:** You know the exact number — e.g., `#123` or `#1042`.

**If any of these is false, STOP. Do not create a branch. Do not write code. Go back and create or link the issue first.**

---

## ✅ Workflow: Issue → Branch → Commit → PR → Merge

### Step 1: Prepare Your Environment

```powershell
# Check you're not already in the middle of work
git status --porcelain
# Should be clean. If not, complete or stash prior work.

# Sync with remote
git checkout master
git fetch origin
git pull --ff-only origin master
```

### Step 2: Create a Branch (Only After Validating the Issue)

**Branch naming rule:** `<type>/issue-<ID>-<kebab-case-slug>`

- `<type>` = `fix`, `feat`, `docs`, `chore`, `refactor`, etc.
- `<ID>` = The GitHub issue number (e.g., `42`).
- `<kebab-case-slug>` = Lowercase, hyphens, descriptive (e.g., `null-auth-token`).

**Example:** `fix/issue-42-null-auth-token`

```powershell
git checkout -b fix/issue-<ID>-<kebab-case-slug>
```

### Step 3: Implement the Fix

- Follow the architecture and patterns in the codebase.
- Add tests (unit or integration) as required by the instructions for that layer.
- Do not refactor unrelated code.
- Do not upgrade dependencies out of scope.
- When done, verify locally:
  ```powershell
  ./gradlew lint test
  # Fix any errors. Zero failures allowed.
  ```

### Step 4: Commit with Issue Linkage

**Conventional Commits format with `Closes #<ID>`:**

```text
fix(scope): concise imperative description

- Key change 1
- Key change 2
- Root cause if applicable

Closes #<ID>
```

Example:
```text
fix(auth): prevent null auth token from caching

- Store auth token check at interceptor, not in cache layer
- Add test covering missing token edge case
- Resolves silent playback failures on token rotation

Closes #42
```

**Staging rule:** Explicitly stage files by path, never bulk-add:
```powershell
git add app/src/main/java/com/example/tigerplayer/data/AuthInterceptor.kt
git add app/src/test/java/com/example/tigerplayer/data/AuthInterceptorTest.kt
git commit -m "fix(auth): prevent null auth token from caching

- Store auth token check at interceptor, not in cache layer
- Add test covering missing token edge case
- Resolves silent playback failures on token rotation

Closes #42"
```

### Step 5: Push and Open a Pull Request

```powershell
git push -u origin <branch-name>
```

Open the PR with explicit issue linkage:

```powershell
gh pr create \
  --title "fix(auth): prevent null auth token from caching (closes #42)" \
  --body "### Summary of Changes
- Store auth token check at interceptor, not in cache layer
- Add test covering missing token edge case

### Verification
- All linters, unit tests, and regression suites passed locally.

### Milestone
- Tied to the issue milestone for release tracking.

Closes #42" \
  --base master
```

**Critical:** The PR title MUST include `(closes #<ID>)` and the body must include `Closes #<ID>`. This triggers GitHub's auto-close on merge.

### Step 6: Verify Automatic Issue Closure

After the PR is merged to `master`:
- GitHub automatically closes issue `#<ID>`.
- The milestone is updated (issue count decreases).
- The branch can be deleted.

---

## ❌ Anti-Patterns: Never Do This

| ❌ | Do NOT | Why |
|---|---|---|
| ❌ | Write code without an issue | No traceability, violates AIRP Phase 0 |
| ❌ | Commit on `master` branch | `master` is protected; CI will reject it |
| ❌ | Push directly to `master` | Same — only PRs reach `master` |
| ❌ | Create a branch without an issue ID | Branch name loses meaning; PR cannot auto-close issue |
| ❌ | Use `closes #<ID>` wrong | Misspelled or missing = issue stays open after PR merge |
| ❌ | Force-push to `master` | Never — violates branch protection |
| ❌ | Bulk `git add .` | Risk of leaking secrets, generated files, or build artifacts |
| ❌ | Refactor unrelated code while fixing an issue | Out-of-scope changes pollute the fix; violate AIRP Phase 2 |
| ❌ | Skip milestone assignment | Breaks release planning and issue tracking |

---

## 🔍 Quick Reference: The Four Sacred Artifacts

Every piece of work creates these four artifacts in lockstep:

1. **GitHub Issue** (`#<ID>`)
   - Formal description, acceptance criteria, milestone assignment.
   - Assigned to the agent doing the work.

2. **Git Branch** (`fix/issue-<ID>-<kebab-case-slug>`)
   - Derived from the issue ID.
   - Checked out locally; all coding happens here.

3. **Commit Message** (with `Closes #<ID>`)
   - Atomic, conventional format.
   - Explicitly references the issue.

4. **Pull Request** (title includes `closes #<ID>`)
   - Opens against `master`.
   - Auto-closes the issue on merge.
   - GitHub enforces status checks before merge.

**If any of these four is missing or malformed, the workflow breaks.**

---

## 📞 Debugging Workflow Failures

| Problem | Solution |
|---|---|
| "I pushed but got `GH013: Repository rule violations`" | You tried to push to `master` directly. Use a feature branch + PR instead. |
| "My PR is open but the issue didn't auto-close" | PR title/body does not include `Closes #<ID>` exactly. Add it and re-push. |
| "I created a branch but forgot the issue ID in the name" | Rename it (locally, hasn't been pushed): `git branch -m old-name fix/issue-<ID>-new-slug` |
| "I accidentally committed on `master`" | Use the recovery procedure in `git-workflow.instructions.md`, Rule 3. |
| "The milestone doesn't exist" | Create one: `gh api repos/<owner>/<repo>/milestones --input milestone.json` or via GitHub UI, then assign the issue. |

---

## 📚 Full References

- **Detailed workflow:** `issue-resolution-protocol.instructions.md` (5 phases)
- **Git rules:** `git-workflow.instructions.md` (6 rules)
- **This checklist:** `AGENT-WORKFLOW-CHECKLIST.md` (you are here)

**Do not skip Phase 0.** Do not write code without an issue. This is not negotiable.

