# Audit Domain Package Convergence Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [x]`) syntax for tracking. Do not create a worktree, dispatch subagents, or commit code for this task.

**Goal:** Remove the redundant `domain.audit` package level from `im-audit-server` while preserving domain boundaries and audit-persistence ownership checks.

**Architecture:** Because Audit Server currently owns one domain capability, its domain types use `domain/model` and `domain/repository`. The architecture check identifies Audit repositories by repository responsibility and type name instead of requiring the historical `domain/audit/repository` path. Package depth remains a Review decision so modules can introduce capability grouping only after multiple independent domains emerge.

**Tech Stack:** Java 21, Maven, AssertJ, repository Java/Harness verification scripts.

---

### Task 1: Generalize audit-persistence ownership detection

**Files:**
- Modify: `im-architecture/src/test/java/com/co/kc/imchat/architecture/RuntimeDependencyPolicyTest.java`

- [x] Add a failing fixture proving `domain/repository/AuditEventRepository.java` is recognized as local Audit persistence.
- [x] Run the focused architecture test and confirm the new fixture fails because the checker only recognizes `domain/audit/repository`.
- [x] Generalize the predicate to recognize an Audit-named repository beneath `domain/**/repository`, while retaining positive grouped-package and negative model/support fixtures.
- [x] Run the focused architecture test and confirm it passes.

### Task 2: Flatten the Audit Server domain package

**Files:**
- Move: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/domain/audit/model/*.java` to `.../domain/model/`
- Move: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/domain/audit/repository/AuditEventRepository.java` to `.../domain/repository/`
- Move: `im-management/im-audit/im-audit-server/src/test/java/com/co/kc/imchat/management/audit/domain/audit/model/AuditEventTest.java` to `.../domain/model/`
- Modify: imports in Audit Server production and test sources.

- [x] Move domain source and test files without changing behavior.
- [x] Replace package declarations and imports from `domain.audit.model/repository` to `domain.model/repository`.
- [x] Search the Audit module and confirm no historical package reference remains.
- [x] Run Audit Server focused tests and confirm they pass.

### Task 3: Record the reusable package-depth convention

**Files:**
- Modify: `docs/references/CODING_GUIDE.md`
- Modify: `docs/references/HARNESS_GUIDE.md`

- [x] State that a module with one domain capability uses `domain/model`, `domain/repository`, and `domain/service`; capability grouping is introduced only for multiple independent domain capabilities.
- [x] Keep the rule Review-only because domain independence cannot be inferred reliably from directory counts.
- [x] State the automation condition: structured ownership metadata or another reliable way to distinguish independent domain capabilities, with flat/grouped positive and false-positive fixtures.

### Task 4: Verify and close the plan

**Files:**
- Move: this plan from `docs/exec-plans/active` to `docs/exec-plans/completed` after all gates pass.

- [x] Run `./scripts/verify.sh quick`.
- [x] Run `./scripts/verify.sh full`.
- [x] Run `git diff --check` for the affected files.
- [x] Review the final diff for unrelated changes.
- [x] Mark all plan items complete and move the plan to `completed` without committing.
