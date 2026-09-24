---
applyTo: "**"
---

# Git Workflow Guardrails

**CRITICAL PRINCIPLE:** Branches exist to implement issues, not the other way around. Every branch must correspond to a single formal issue with a known issue ID. Before creating a branch, ensure the issue exists, is formally described, assigned to you, and tied to a milestone. See `issue-resolution-protocol.instructions.md`, Phase 0.

`master` is protected on GitHub: direct pushes are rejected (`GH013: Repository rule violations`),
and merges require an open pull request plus passing status checks (CodeQL, etc.). Agents must
work within that constraint rather than fight it.

## Rules

1. **Always work from a formal issue with a known ID.** Before creating a branch, verify:
   - The issue exists and is formally written (not a mental note or casual discussion).
   - The issue is assigned to you and includes acceptance criteria.
   - The issue is tied to a milestone (create one if necessary).
   - Then create a branch named `fix/issue-<ID>-<kebab-case-slug>` (or `docs/`, `chore/`, etc. as appropriate).
   
   **Never commit directly on `master`.** Check the current branch (`git branch --show-current`). If it is `master` (or the checked-out default branch), you have skipped this step — stop, return to Phase 0 of AIRP, and create or link to the issue.

   ```powershell
   git checkout -b fix/issue-<ID>-<kebab-case-slug>   # or docs/..., chore/..., etc.
   ```

   Only edit files after switching. Do not stage or commit changes while on `master`.

2. **Never run `git push origin master`** (or push directly to whatever the default branch is).
   All changes reach `master` only through a pull request.

3. **If a change was accidentally committed on `master` locally**, do not force it through. Recover
   without touching the remote:

   ```powershell
   git branch <new-branch-name>        # capture the commit(s) on a new branch
   git reset --hard origin/master      # restore local master to match remote
   git checkout <new-branch-name>
   git push -u origin <new-branch-name>
   ```

4. **Always open a pull request with explicit issue linkage.** Use `gh pr create --base <default-branch>` with a title that includes `closes #<ID>`. This ensures:
   - The PR title reads: `fix(<scope>): <description> (closes #<ID>)`.
   - The PR body includes `Closes #<ID>` or `Fixes #<ID>` on its own line to trigger GitHub's auto-close on merge.
   - The issue is automatically closed when the PR merges to the default branch.
   
   Follow the branch naming and commit conventions in `issue-resolution-protocol.instructions.md`. Never open a PR without explicitly linking to an issue via `closes` syntax.

5. **Never use `--force` / `--force-with-lease` against `master`** or any shared branch. Force-push
   only to a feature branch you created, and only if it has not been reviewed yet.

6. Treat a rejected push (`push declined due to repository rule violations`,
   `protected branch`, etc.) as an expected signal to switch to the branch-and-PR flow above —
   not as an error to bypass with elevated permissions, alternate remotes, or `--force`.

