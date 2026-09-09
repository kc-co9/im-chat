# Common Pagination Implementation Plan

## Final deviation summary

The follow-up [pagination query condition plan](2026-08-24-pagination-query-condition.md)
separates `Paging` from filter-only `QueryCondition` values. The shared pagination
types and public contract boundaries introduced by this plan remain unchanged;
the current internal query shape is defined by the follow-up plan and the
[admin design](../../design-docs/2026-08-24-im-admin-user-management-design.md).

> **For agentic workers:** Execute sequentially in the current worktree. The user explicitly requested no sub-agents; do not commit unless explicitly requested.

**Goal:** Introduce framework-neutral shared page-number models and migrate the ordinary-user management internal flow without changing public RPC or HTTP contracts.

**Architecture:** `im-common` owns immutable `Paging` and `PagingResult<T>` values. Account and Admin domain/application internals use those values, while Facade DTOs and HTTP responses retain their current flattened fields and are converted at boundaries.

**Tech Stack:** Java 21, records, MapStruct, MyBatis-Plus, Maven, JUnit 5, AssertJ.

---

## Objective and non-goals

- Add validated, immutable shared page-number values with mapping and navigation behavior.
- Replace `UserAdminPage` and `ManagedUserPage` in the current user-management internal chain.
- Keep `UserPageQuery`, `AccountUserPageDTO`, `ManagedUserPageResponse`, JSON fields, and Dubbo serialization unchanged.
- Do not introduce `IdCursor` until a real cursor-based query exists.
- Do not migrate administrator, role, or audit pagination in this change.

## Design reference

- [IM Admin 用户与权限管理设计](../../design-docs/2026-08-24-im-admin-user-management-design.md), section 4.3.

## Affected modules and ownership

- `im-common`: framework-neutral `Paging` and `PagingResult<T>` values and focused tests.
- `im-account-server`: Account user-management criteria, repository result, persistence conversion, and application transformer.
- `im-management/im-admin`: managed-user criteria/result, Account adapter, application service, and HTTP transformer.

## Ordered implementation tasks

### Task 1: Add shared pagination values

- [x] Add failing tests for validation, immutable record copies, total pages, previous/next page, empty results, and element mapping.
- [x] Create `com.co.kc.imchat.common.model.page.Paging` and `PagingResult<T>` without Spring, Jackson, Lombok, or persistence dependencies.
- [x] Run `mvn -q -pl im-common -am test`.

### Task 2: Migrate the current user-management internal flow

- [x] Update Account and Admin tests first to require `Paging` and `PagingResult<T>` while retaining Facade/HTTP output shapes.
- [x] Embed `Paging` in `UserAdminCriteria` and `ManagedUserCriteria`.
- [x] Change internal repositories/adapters/application services to return `PagingResult<UserAdminSnapshot>` or `PagingResult<ManagedUser>`.
- [x] Update MapStruct boundary mappings and remove `UserAdminPage` and `ManagedUserPage`.
- [x] Run focused Account/Admin tests and confirm contract serialization is unchanged.

### Task 3: Verify and close

- [x] Run `./scripts/verify.sh quick`.
- [x] Run `./scripts/verify.sh full`.
- [x] Run `git diff --check`, update this plan, then move it unchanged to `docs/exec-plans/completed` and update plan indexes.

## Compatibility and rollback

This is an internal source refactor. Existing RPC and HTTP field names, defaults, bounds, and response shapes remain unchanged. Rollback restores the two domain-specific page records and their boundary mappings; no data migration is involved.

## Completion criteria

- Shared pagination behavior is covered by deterministic unit tests.
- Account/Admin user-management internals no longer declare duplicate page result records.
- Facade and HTTP contract tests remain green without compatibility overloads.
- Quick and full repository verification pass.
