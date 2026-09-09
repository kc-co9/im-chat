# Pagination Query Condition Implementation Plan

> **For agentic workers:** Execute sequentially in the current worktree. The user explicitly requested no sub-agents; do not commit unless explicitly requested.

**Goal:** Separate pagination bounds from user filter conditions across the Account/Admin internal query flow.

**Architecture:** Repository and Adapter page methods accept `Paging` and a focused `QueryCondition` as separate arguments. Public Facade and HTTP contracts remain unchanged, and MyBatis-specific conditions stay in infrastructure.

**Tech Stack:** Java 21 records, MapStruct, MyBatis-Plus, Maven, JUnit 5, Mockito.

---

## Objective and non-goals

- Replace combined `*Criteria` objects with filter-only `*QueryCondition` records.
- Pass `Paging` separately through Account Repository, persistence service, and Admin Adapter boundaries.
- Keep RPC/HTTP request and response contracts unchanged.
- Do not migrate administrator, role, or audit queries in this change.

## Design reference

- [IM Admin 用户与权限管理设计](../../design-docs/2026-08-24-im-admin-user-management-design.md), section 4.3.

## Ordered implementation tasks

### Task 1: Drive the signature change through tests

- [x] Update Account repository/application tests and Admin HTTP tests to use separate paging and query conditions.
- [x] Run focused tests and confirm compilation fails against the old combined criteria signatures.

### Task 2: Implement focused query conditions

- [x] Replace `UserAdminCriteria` with `UserAdminQueryCondition` and change `UserAdminRepository.page` to accept separate arguments.
- [x] Replace `ManagedUserCriteria` with `ManagedUserQueryCondition` and change `AccountAdminAdapter.page` to accept separate arguments.
- [x] Rename `DbUserAdminQuery` to `DbUserAdminQueryCondition` and keep it infrastructure-only.
- [x] Update MapStruct/manual boundary conversions and remove obsolete criteria types.
- [x] Run focused and complete Account/Admin tests.

### Task 3: Verify and close

- [x] Run `./scripts/verify.sh quick`.
- [x] Run `./scripts/verify.sh full`.
- [x] Run `git diff --check`, move this plan to completed, and update indexes.

## Compatibility and rollback

This is an internal source refactor with no persistence or serialized-contract migration. Rollback restores the combined criteria records and method signatures.

## Completion criteria

- Pagination values are absent from all user query-condition records.
- Account/Admin internal query boundaries receive `Paging` separately.
- Existing Facade and HTTP contract tests remain green.
- Full repository verification passes.
