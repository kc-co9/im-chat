# User Deletion Fact Separation Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans in the current worktree. The user explicitly requested no sub-agents and no commit.

**Goal:** Remove `DELETED` from user business-status enums and provide independent normal-user and deleted-user pagination flows.

**Architecture:** Account models logical deletion as a boolean fact alongside the retained `NORMAL/BANNED` business status. Facade, Repository, persistence, Admin HTTP, and UI expose separate pagination operations rather than switching implementation through a synthetic status value.

**Tech Stack:** Java 21 records, Dubbo Facade contracts, MapStruct, MyBatis-Plus/MyBatis XML, Vue 3, TypeScript, Vitest, Maven, JUnit 5, Mockito.

---

## Objective and non-goals

- Remove `DELETED` from Account, Facade, and Admin user-status enums.
- Add a `deleted` fact to management snapshots and response DTOs.
- Split normal and deleted pagination at every internal and public boundary.
- Keep deleted users visible from the existing Admin UI toolbar entry.
- Do not add restore, physical delete, or compatibility overloads.

## Design reference

- [IM Admin 用户与权限管理设计](../../design-docs/2026-08-24-im-admin-user-management-design.md), sections 4 and 8.

## Affected modules and ownership

- `im-account-admin-facade`: separate deleted-user query contract and deletion flag in DTOs.
- `im-account-server`: Account query/application/domain/persistence split and deletion checks.
- `im-management/im-admin`: separate Adapter/application/HTTP flow and frontend endpoint selection.
- `docs`: accepted design and execution-plan lifecycle only.

## Ordered implementation tasks

### Task 1: Drive the contract and domain shape through tests

- [x] Update Facade serialization/contract tests to require `NORMAL/BANNED` only, `deleted`, and `pageDeletedUsers(DeletedUserPageQuery)`.
- [x] Update Account application/repository/service/Mapper tests to require separate `pageUsers` and `pageDeletedUsers` methods with no status branch.
- [x] Update Admin application/controller/UI tests to call the deleted-user endpoint instead of sending `status=DELETED`.
- [x] Run focused tests and confirm failures are caused by the old status and combined query shape.

### Task 2: Separate Account deletion facts and pagination

- [x] Remove `DELETED` from `UserAdminStatus`; add `deleted` to `UserAdminSnapshot` and CQRS DTOs.
- [x] Add filter-only deleted-user query objects and `UserAdminRepository.pageDeletedUsers`.
- [x] Split `DbUserService.pageUsers` and `pageDeletedUsers`; keep explicit deleted SQL only in the latter.
- [x] Change write guards and delete idempotency from status comparison to `snapshot.deleted()`.
- [x] Run focused Account tests.

### Task 3: Separate Facade and Admin query flows

- [x] Add `DeletedUserPageQuery` and `AccountAdminService.pageDeletedUsers`; remove `DELETED` from Facade status.
- [x] Add `deleted` to Facade/Admin domain and HTTP response models and boundary transformers.
- [x] Add Admin application and `/api/users/deleted/page` query entry; remove `DELETED` from Admin status.
- [x] Update the Vue API and user page to call the separate endpoint while retaining the deleted-list toolbar mode.
- [x] Run focused Facade, Account, Admin, and UI tests.

### Task 4: Verify and close

- [x] Confirm no `status=DELETED`, `UserAdminStatus.DELETED`, or pagination status branch remains.
- [x] Run `./scripts/verify.sh quick`.
- [x] Run `./scripts/verify.sh full`.
- [x] Run `git diff --check`, move this plan to completed, and update indexes.

## Test and verification strategy

- Focused backend: `mvn -q -pl im-service/im-account/im-account-admin-facade,im-service/im-account/im-account-server,im-management/im-admin -am -Dtest='*User*Test,*AccountAdmin*Test' -Dsurefire.failIfNoSpecifiedTests=false test`.
- Focused UI: `npm run test:unit -- --run UsersView.spec.ts` from `im-management/im-admin/ui`.
- Repository gates: `./scripts/verify.sh quick`, `./scripts/verify.sh full`, and `git diff --check`.

## Compatibility and rollback

This intentionally changes the uncommitted Admin Facade and HTTP contract under development; no compatibility layer is retained. Database schema and persisted status values remain unchanged. Rollback restores the synthetic deleted status and combined pagination path, with no data migration.

## Completion criteria

- Business-status enums contain only `NORMAL` and `BANNED`.
- Deleted rows retain their original business status and expose `deleted=true`.
- Normal and deleted pagination are distinct from Facade through Mapper/UI.
- Deleted users remain visible and read-only in the Admin page.
- Focused and full verification pass.

## Subsequent decision

On 2026-08-25 the separate normal/deleted pagination introduced by this plan was intentionally
converged into one administrator list. The deletion fact remains independent from business status,
but `AccountAdminService.pageUsers` now reads all rows through `DbUserService.pageRawUsers` and the
Admin UI marks deleted rows in place. `DbUserService.pageUsers` remains available for ordinary
MyBatis-Plus queries that must continue to honor logical deletion.
