---
applyTo: '**'
---

# Autonomous Issue Resolution Protocol (AIRP)

Execute the following phased lifecycle for each assigned issue in strict sequential order. Do not proceed to subsequent phases until all phase gates and criteria are verified.

---

## Phase 0: Issue Validation & Milestone Assignment

**AGENTS MUST NEVER WRITE CODE WITHOUT A FORMAL ISSUE FIRST.**

- **Issue Existence & Assignment:** Verify the issue exists, is formally described with acceptance criteria, and is assigned to you.
- **Milestone Binding:** The issue must be tied to a milestone (existing or newly created). If no milestone exists for the work:
  - Create one via GitHub UI or CLI: `gh api repos/<owner>/<repo>/milestones --input milestone.json`
  - Use semantic versioning naming (e.g., `v2.1.0`, `v2.1.1-hotfix`).
  - Assign the issue to that milestone.
- **Gate:** Do not proceed to Phase 1 until the issue ID is known, the issue is formally described, and the milestone is set. Attempting to branch without an issue is a protocol violation.

---

## Phase 1: Environment Sanitation & Branching

- **Working Tree Audit:** Inspect repository state using `git status --porcelain`. Abort immediately if untracked changes, unstaged edits, or merge conflicts exist.
- **Base Synchronization:**
  - Determine the canonical default branch (`main` or `master`).
  - Checkout and rebase onto remote head: `git checkout <default-branch> && git fetch origin && git pull --ff-only origin <default-branch>`.
- **Branch Provisioning:**
  - Derive branch name using the pattern: `fix/issue-<ID>-<kebab-case-slug>`.
  - Ensure the slug is descriptive, lowercase, and strictly alphanumeric with hyphens (e.g., `fix/issue-142-null-auth-token`).
  - Create and switch to the branch: `git checkout -b <branch-name>`.
  - **`master` is branch-protected.** Never write code, stage, or commit while checked out on
    `master`, and never push directly to it — GitHub rejects it (`GH013: Repository rule
    violations`). All work happens on the branch created above; it reaches `master` only through
    the pull request opened in Phase 4. See `.github/instructions/git-workflow.instructions.md`.

---

## Phase 2: Scope Analysis & Targeted Implementation

- **Context Ingestion:** Parse the issue specification, reproducing steps, error traces, and acceptance criteria. Read all related source files, dependencies, and adjacent unit tests.
- **Surgical Modification:**
  - Apply the minimal viable code changes strictly required to satisfy the issue specification.
  - Adhere to the host repository's architectural patterns, formatting conventions, and linting standards.
  - Prohibit out-of-scope refactoring, dependency upgrades, or whitespace-only alterations across unrelated files.
- **Regression Prevention:** Add or update unit/integration tests to programmatically assert the fix and guard against future regressions.

---

## Phase 3: Verification & Quality Gates

- **Deterministic Validation:** Run the project-defined quality toolchain in order:
  1. Static analysis, linter, and type checker (e.g., `npm run lint`, `flake8`, `tsc`).
  2. Targeted unit tests associated with the modified modules.
  3. Full regression test suite.
- **Zero-Failure Invariant:** Under no circumstance may code be committed if any test fails, linter warns (under strict mode), or build artifacts fail to compile.
- **Clean State Confirmation:** Ensure no temporary logs, debugger statements, debugging artifacts, or untracked cache files remain.

---

## Phase 4: Atomic Commit & Delivery

- **Selective Staging:** Explicitly stage modified assets by path (`git add <path/to/file>`). Never execute bulk staging commands (`git add .` or `git add -A`) to prevent secret or artifact leakage.
- **Conventional Commit Formulation:**
  - Write an atomic commit message adhering to Conventional Commits:

    ```text
    fix(<scope>): <concise-imperative-summary>

    - <Key change details>
    - <Root cause resolved>

    Closes #<ID>
    ```

- **Remote Dispatch & Pull Request:**
  - Push the branch upstream: `git push -u origin <branch-name>`.
  - Dispatch a Pull Request via GitHub CLI, explicitly closing the issue:

    ```bash
    gh pr create \
      --title "fix(<scope>): <short description> (closes #<ID>)" \
      --body "### Summary of Changes"$'\n'"- Implemented targeted fix for issue #<ID>."$'\n\n'"### Verification"$'\n'"- All linters, unit tests, and regression suites passed locally."$'\n\n'"### Milestone"$'\n'"Tied to issue milestone for release tracking." \
      --base <default-branch>
    ```

  - **Critical:** The `closes #<ID>` text in the PR title and/or body **must** reference the issue ID. GitHub will automatically close the issue when the PR merges.
  - Verify the issue is bound to the correct milestone before opening the PR (it will be inherited from the issue).

---

## Phase 5: Teardown & Context Handover

- **State Reset:** Ensure local working tree is left clean.
- **Status Exit:** Emit a structured completion payload (`{"status": "SUCCESS", "issue": "<ID>", "pr_url": "<URL>"}`) and exit with code `0` to signal the orchestrator to advance the queue.

