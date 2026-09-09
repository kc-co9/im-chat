# Audit Repository Convergence Implementation Plan

> **For agentic workers:** Execute in the current workspace without subagents, worktrees, commits, or compatibility code, following the user's repository workflow.

**Goal:** Align Audit persistence conversion and query code with the project's stateless Transformer and MyBatis service conventions.

**Architecture:** `AuditDomainTransformer` is a stateless MapStruct singleton accessed through `INSTANCE`. `MysqlAuditEventRepository` depends only on `DbAuditEventService`, uses `getFirst` for a single result, and builds the complete paged query directly in the `page` call.

**Tech Stack:** Java 21, MapStruct, MyBatis-Plus, JUnit 5, Mockito, AssertJ.

---

### Task 1: Lock the repository API and query behavior

**Files:**

- Modify: `im-management/im-audit/im-audit-server/src/test/java/com/co/kc/imchat/management/audit/infrastructure/domain/repository/MysqlAuditEventRepositoryTest.java`
- Modify: `im-management/im-audit/im-audit-server/src/test/java/com/co/kc/imchat/management/audit/transformer/domain/AuditDomainTransformerTest.java`

- [x] Change tests to use `AuditDomainTransformer.INSTANCE` and construct the repository with only `DbAuditEventService`.
- [x] Add focused assertions for `getFirst` lookup and paged query mapping.
- [x] Run the focused tests and confirm compilation fails because the production API has not converged yet.

### Task 2: Converge production implementation

**Files:**

- Modify: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/transformer/domain/AuditDomainTransformer.java`
- Modify: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/infrastructure/domain/repository/MysqlAuditEventRepository.java`
- Modify: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/infrastructure/config/beans/AuditRepositoryBeans.java`

- [x] Replace Spring component-model mapping with the project-standard `INSTANCE` singleton.
- [x] Remove the Transformer dependency from the Repository and Bean factory.
- [x] Replace `getOne(... LIMIT 1)` with `getFirst`.
- [x] Inline all query conditions into the single `auditEventService.page(...)` expression and delete `queryFrom`.
- [x] Run focused and module tests.

### Task 3: Harness feedback and verification

- [x] Confirm whether the existing Transformer convention already covers stateless singleton access; update the specification/Harness only if a reusable rule is missing.
- [x] Run `./scripts/verify.sh quick`.
- [x] Run `git diff --check`.
- [x] Move this plan to `docs/exec-plans/completed` after all checks pass.
