# Query Condition Convergence Implementation Plan

> **For agentic workers:** Execute sequentially in the current worktree. The user explicitly requested no worktree and no sub-agents; do not commit unless explicitly requested.

**Goal:** Converge internal query filters on `*QueryCondition`, separate pagination from filters, and replace nullable optional filters with non-null `Optional<T>`.

**Architecture:** Application CQRS contracts remain unchanged. Application services normalize public nullable inputs, repositories accept `Paging` separately from filter-only condition records, and infrastructure query conditions unwrap Optional values only at MyBatis boundaries. Empty role filters are removed instead of replaced with an empty condition type.

**Tech Stack:** Java 21 records, MyBatis-Plus, MyBatis XML, JUnit 5, Mockito, Maven.

---

## Design sources

- [Dynamic Harness feedback loop design](../../design-docs/2026-08-25-dynamic-harness-feedback-loop-design.md)
- [Coding Guide](../../references/CODING_GUIDE.md)

### Task 1: Converge Account user query conditions

- [x] Rename `UserAdminQueryCondition` to `UserQueryCondition`.
- [x] Represent omissible domain and deleted-user persistence filters with non-null `Optional<T>`.
- [x] Update RPC Transformer, application query models, Repository implementation, MyBatis service/XML, and focused tests.
- [x] Preserve the dedicated SQL path that can read logically deleted users.

### Task 2: Converge Admin managed-user adapter conditions

- [x] Change `ManagedUserQueryCondition` and its deleted counterpart to non-null Optional components.
- [x] Normalize nullable application query values when constructing conditions.
- [x] Unwrap the domain conditions only while creating Account Facade queries.
- [x] Update focused application and adapter tests.

### Task 3: Separate Admin account and audit pagination

- [x] Rename `AdminAccountCriteria` to `AdminAccountQueryCondition` and `AuditCriteria` to `AuditQueryCondition`.
- [x] Change Repository page methods to accept `Paging` separately.
- [x] Rename `DbAdminUserQuery` and `DbAdminAuditQuery` to `*QueryCondition` and use non-null Optional components.
- [x] Update MyBatis XML/Mapper bindings and focused tests without changing query behavior.

### Task 4: Remove the empty role condition

- [x] Delete `RoleCriteria`.
- [x] Change role pagination to accept `Paging` directly.
- [x] Update application, Repository implementation, Mapper call, and focused tests.

### Task 5: Verify and close

- [x] Run focused Account and Admin tests.
- [x] Run `./scripts/verify.sh quick` and `./scripts/verify.sh full`.
- [x] Run `git diff --check`, record evidence, and move this plan to completed.

## Completion criteria

- No production type ending in `Criteria` remains in Account/Admin query flows.
- Persistence filter records use the `*QueryCondition` suffix rather than `*Query`.
- Pagination is absent from internal query-condition records.
- Omissible internal filter components are non-null `Optional<T>`.
- Public HTTP/RPC contracts and logically deleted user visibility remain unchanged.

## Verification evidence

- `mvn -q -pl im-service/im-account/im-account-server,im-management/im-admin -am test`
- `./scripts/verify.sh quick`
- `./scripts/verify.sh full`
- `git diff --check`
- Repository search confirmed that Account/Admin query flows contain no production `*Criteria`
  type or persistence filter record ending in bare `*Query`.
