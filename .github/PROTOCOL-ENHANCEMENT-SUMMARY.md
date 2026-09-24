# Agent Branch & Issue Protocol Enhancement Summary

**Date:** September 24, 2026  
**Status:** ✅ Complete

---

## Overview

Enhanced the TigerPlayer repository protocols to enforce **issue-first workflow** with mandatory branch protection, milestone tracking, and proper PR closure semantics. Agents are now explicitly required to:

1. ✅ Create or link a formal GitHub issue **before** writing any code
2. ✅ Tie the issue to a milestone (create one if necessary)
3. ✅ Work on a feature branch derived from the issue ID
4. ✅ Never commit directly to `master`
5. ✅ Close issues properly using `closes #<ID>` syntax in PRs

---

## Files Updated

### 1. `.github/instructions/issue-resolution-protocol.instructions.md`

**Added Phase 0: Issue Validation & Milestone Assignment**

- **Requirement:** Issues must exist before any branching or coding begins
- **Gate:** Issue must be:
  - Formally described with acceptance criteria
  - Assigned to the agent
  - Tied to a milestone (create one using semantic versioning if needed)
- **Enforcement:** Cannot proceed to Phase 1 without completing Phase 0
- **Impact:** Makes "no code without an issue" a hard protocol requirement

**Enhanced Phase 4: Atomic Commit & Delivery**

- Updated PR template to include milestone section
- Explicit note that `closes #<ID>` syntax must be present in both:
  - PR title: `fix(<scope>): description (closes #<ID>)`
  - PR body: `Closes #<ID>` on its own line
- Verification step added to confirm issue is bound to correct milestone before PR creation

### 2. `.github/instructions/git-workflow.instructions.md`

**Added Critical Principle Header**

```
Branches exist to implement issues, not the other way around. 
Every branch must correspond to a single formal issue with a known issue ID.
```

**Enhanced Rule 1: Issue-First Requirement**

- **Pre-branch verification checklist:**
  - Issue exists and is formally written (not casual discussion)
  - Issue is assigned to the agent
  - Issue includes acceptance criteria
  - Issue is tied to a milestone

- **Enforcement:** If on `master` branch without an issue, agent must STOP and return to Phase 0 of AIRP
- **Clear error message:** "You have skipped this step — stop, return to Phase 0 of AIRP, and create or link to the issue."

**Enhanced Rule 4: PR with Explicit Issue Linkage**

- Must include `closes #<ID>` in PR title
- Must include `Closes #<ID>` or `Fixes #<ID>` in PR body
- Triggers GitHub's automatic issue closure on merge
- Never open a PR without explicit closure syntax

### 3. `AGENTS.md`

**Added ⚠️ MANDATORY START HERE Section**

```markdown
⚠️ MANDATORY START HERE
- .github/instructions/AGENT-WORKFLOW-CHECKLIST.md — quick reference (read this first)
- .github/instructions/issue-resolution-protocol.instructions.md — AIRP lifecycle (Phase 0 mandatory)
- .github/instructions/git-workflow.instructions.md — branch protection & master guardrails
```

**Added ⛔ CRITICAL Callout**

Moved issue-first requirement to the top, immediately after project identity section.

**Enhanced Section 5: Guardrails**

- Moved issue-first protocol to item 0 (highest priority)
- Added explicit reference to AGENT-WORKFLOW-CHECKLIST.md
- Linked Phase 0 requirement in the master branch guardrail (item 9)
- Made the workflow requirement non-negotiable

### 4. **NEW FILE:** `.github/instructions/AGENT-WORKFLOW-CHECKLIST.md`

Created comprehensive quick-reference guide for agents with:

**✋ STOP: Before You Write Any Code**
- Mandatory 5-point checklist before implementation:
  1. Issue exists
  2. Issue assigned to agent
  3. Issue has criteria
  4. Milestone is set
  5. Issue ID is known

**✅ Workflow: Issue → Branch → Commit → PR → Merge**

Step-by-step guide covering:
1. Environment preparation (`git status`, sync with remote)
2. Branch creation with naming rules
3. Implementation with tests
4. Validation (`./gradlew lint test`)
5. Commit with proper formatting and `Closes #<ID>`
6. PR creation with GitHub CLI
7. Automatic issue closure verification

**Example commands:**
- Branch creation: `git checkout -b fix/issue-42-null-auth-token`
- Commit format with `Closes #<ID>`
- PR creation with explicit issue linkage

**❌ Anti-Patterns Table**

Documents forbidden actions:
- Writing code without issue
- Committing on `master`
- Pushing directly to `master`
- Branch without issue ID
- Misspelled closure syntax
- Force-push to `master`
- Bulk `git add .`
- Out-of-scope refactoring
- Skipping milestone assignment

**🔍 Quick Reference: Four Sacred Artifacts**

Maps the four required elements:
1. GitHub Issue (`#<ID>`)
2. Git Branch (`fix/issue-<ID>-<kebab-case-slug>`)
3. Commit Message (with `Closes #<ID>`)
4. Pull Request (title includes `closes #<ID>`)

**📞 Debugging Workflow Failures**

Common issues and solutions:
- `GH013` error → Use feature branch + PR
- Issue didn't auto-close → Add `Closes #<ID>` properly
- Branch naming mistakes → Rename locally before push
- Accidental master commits → Use recovery procedure
- Missing milestone → Create via GitHub UI or CLI

---

## Core Principles Enforced

| Principle | Enforcement | File(s) |
|-----------|------------|---------|
| **Issue-First** | Phase 0 of AIRP must complete before any code work | AIRP, Git Workflow, AGENTS.md, Checklist |
| **No Master Commits** | Rule 1 of Git Workflow, emphasized in AIRP | All instruction files |
| **Milestone Binding** | Phase 0 requirement; verified in Phase 4 | AIRP, Checklist |
| **Explicit Closure** | `closes #<ID>` syntax required in PR | Git Workflow Rule 4, AIRP Phase 4, Checklist |
| **Branch Derivation** | Branch name must include issue ID | All files |
| **PR-Only Merges** | Direct pushes to master rejected by GitHub | Git Workflow, all files |

---

## Implementation Checklist

- ✅ AIRP Phase 0 added with hard gate
- ✅ Git Workflow Rule 1 enhanced with pre-branch verification
- ✅ Git Workflow Rule 4 clarified for issue closure syntax
- ✅ AGENTS.md updated with ⚠️ and ⛔ callouts
- ✅ New AGENT-WORKFLOW-CHECKLIST.md created as quick reference
- ✅ All files cross-linked for consistency
- ✅ Anti-pattern documentation added
- ✅ Real-world examples and debugging guide included

---

## Expected Agent Behavior After Update

Agents will now:

1. **Before any work:** Check for formal issue with ID, acceptance criteria, and milestone
2. **If issue missing:** Stop work and either create issue or link existing one
3. **If milestone missing:** Create semantic milestone and assign issue to it
4. **At branch creation:** Derive branch name from issue ID
5. **Before commit:** Verify no work on `master` branch
6. **In commit message:** Include `Closes #<ID>` footer
7. **At PR creation:** Include `closes #<ID>` in title and `Closes #<ID>` in body
8. **After merge:** Verify GitHub auto-closed the issue

**Result:** Complete traceability from issue → branch → commit → PR → closure, with no orphaned work and all changes tied to formal requirements.

---

## Files Modified

```
.github/instructions/
  ├── issue-resolution-protocol.instructions.md  [MODIFIED] Added Phase 0, enhanced Phase 4
  ├── git-workflow.instructions.md               [MODIFIED] Added principle, enhanced Rules 1 & 4
  └── AGENT-WORKFLOW-CHECKLIST.md                [CREATED] New quick reference

AGENTS.md                                         [MODIFIED] Added mandatory callouts & checklist
```

---

## Validation

All enhanced instructions:
- ✅ Reference each other consistently
- ✅ Provide clear examples and commands
- ✅ Enforce hard gates (cannot skip Phase 0)
- ✅ Include recovery procedures for accidents
- ✅ Cover Windows PowerShell environment
- ✅ Match TigerPlayer project conventions

---

## Next Steps for Agents

When starting a task:

1. **Read first:** `AGENT-WORKFLOW-CHECKLIST.md`
2. **Reference:** `issue-resolution-protocol.instructions.md` (AIRP phases)
3. **Check:** `git-workflow.instructions.md` (branch rules)
4. **Follow:** The 6-step workflow in the Checklist
5. **Verify:** All 4 Sacred Artifacts exist before completion

**Remember:** No code without an issue. Ever.
