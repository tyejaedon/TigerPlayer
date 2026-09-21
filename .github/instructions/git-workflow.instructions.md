---
applyTo: "**"
---

# Git Workflow Guardrails

`master` is protected on GitHub: direct pushes are rejected (`GH013: Repository rule violations`),
and merges require an open pull request plus passing status checks (CodeQL, etc.). Agents must
work within that constraint rather than fight it.

## Rules

1. **Never commit directly on `master`.** Before making any code or documentation change, check
   the current branch (`git branch --show-current`). If it is `master` (or the checked-out default
   branch), create and switch to a new branch **first**:

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

4. **Always open a pull request** for the branch (`gh pr create --base <default-branch> ...`)
   instead of asking a human to merge a direct push. Follow the branch naming and commit
   conventions in `issue-resolution-protocol.instructions.md`.

5. **Never use `--force` / `--force-with-lease` against `master`** or any shared branch. Force-push
   only to a feature branch you created, and only if it has not been reviewed yet.

6. Treat a rejected push (`push declined due to repository rule violations`,
   `protected branch`, etc.) as an expected signal to switch to the branch-and-PR flow above —
   not as an error to bypass with elevated permissions, alternate remotes, or `--force`.

