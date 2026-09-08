---
applyTo: '**'
---

# Autonomous Issue Resolution Protocol (AIRP)

Execute the following phased lifecycle for each assigned issue in strict sequential order. Do not proceed to subsequent phases until all phase gates and criteria are verified.

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
  - Dispatch a Pull Request via GitHub CLI:

    ```bash
    gh pr create \
      --title "fix(<scope>): <short description> (closes #<ID>)" \
      --body "### Summary of Changes"$'\n'"- Implemented targeted fix for issue #<ID>."$'\n\n'"### Verification"$'\n'"- All linters, unit tests, and regression suites passed locally." \
      --base <default-branch>
    ```

---

## Phase 5: Teardown & Context Handover

- **State Reset:** Ensure local working tree is left clean.
- **Status Exit:** Emit a structured completion payload (`{"status": "SUCCESS", "issue": "<ID>", "pr_url": "<URL>"}`) and exit with code `0` to signal the orchestrator to advance the queue.

