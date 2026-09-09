# Dynamic Harness Feedback Loop Implementation Plan

> **For agentic workers:** Execute sequentially in the current worktree. The user explicitly requested no worktree and no sub-agents; do not commit unless explicitly requested.

**Goal:** Make reusable engineering conclusions feed back into repository specifications and Harness on every task, and document the internal `QueryCondition` naming and optional-filter conventions.

**Architecture:** Coding Guide owns the code-structure conventions, Harness Guide owns rule lifecycle and feedback selection, and root `AGENTS.md` makes the assessment mandatory in the Agent workflow and Definition of Done. The query-condition rules remain Review-only until the repository baseline converges; no checker whitelist is introduced.

**Tech Stack:** Markdown documentation, repository drift checks, Harness quick/full verification.

---

## Design source

- [Dynamic Harness feedback loop design](../../design-docs/2026-08-25-dynamic-harness-feedback-loop-design.md)

### Task 1: Document code-structure conventions

**Files:**
- Modify: `docs/references/CODING_GUIDE.md`
- Modify: `docs/references/HARNESS_GUIDE.md`

- [x] Add the `*Query` versus `*QueryCondition` naming boundary.
- [x] Require pagination to remain separate from filter-only query conditions.
- [x] Define non-null `Optional<T>` components for omissible internal filters and direct types for required filters.
- [x] Add the rule to the Harness matrix as Review-only.
- [x] Define the same-task feedback assessment and its possible outcomes.

### Task 2: Make feedback assessment part of Agent completion

**Files:**
- Modify: `AGENTS.md`

- [x] Add a post-edit Harness feedback assessment step.
- [x] Add reusable-convention synchronization to the Definition of Done.
- [x] Keep human-facing context in references rather than duplicating detailed rules in `AGENTS.md`.

### Task 3: Verify and close

**Files:**
- Move: `docs/exec-plans/active/2026-08-25-dynamic-harness-feedback-loop.md`
- Modify: `docs/exec-plans/active/README.md`
- Modify: `docs/exec-plans/completed/README.md`

- [x] Run `./scripts/check-drift.sh` and `git diff --check`.
- [x] Run `./scripts/verify.sh quick`.
- [x] Run `./scripts/verify.sh full`.
- [x] Record verification evidence, complete all checkboxes, and move this plan to completed.

## Completion criteria

- Agents must evaluate Harness impact whenever a reusable convention is confirmed.
- The evaluation distinguishes automated, Review-only, and deferred rules without overstating enforcement.
- Query-condition naming, paging separation, and optional-filter conventions have a clear specification owner.
- Existing `DbDeletedUserQueryCondition` requires no whitelist or unrelated migration.
- Full repository verification passes.

## Verification evidence

- `./scripts/check-drift.sh`: passed on 2026-08-25.
- `git diff --check`: passed on 2026-08-25.
- `./scripts/verify.sh quick`: passed on 2026-08-25.
- `./scripts/verify.sh full`: passed on 2026-08-25.
