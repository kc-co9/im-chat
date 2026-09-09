# Raw User Page Implementation Plan

> **For agentic workers:** Execute sequentially in the current worktree. The user explicitly requested no worktree and no sub-agents; do not commit unless explicitly requested.

**Goal:** Replace the separate active/deleted user lists with one administrator page that reads all user rows and marks deleted records in the result.

**Architecture:** Keep the existing public `UserPageQuery` and business filters. Route it through a raw MyBatis query that deliberately bypasses logic-delete filtering, remove the dedicated deleted-user contract and UI mode, and retain ordinary logic-delete filtering for non-administrative Account queries.

**Tech Stack:** Java 21, MyBatis-Plus, MyBatis XML, Vue 3, Vitest, JUnit 5, Maven.

---

### Task 1: Prove unified Account pagination

- [x] Change tests first so `pageUsers` expects raw rows containing both deleted and non-deleted users.
- [x] Verify RED because `pageRawUsers` and its SQL statement do not exist.
- [x] Add `pageRawUsers`, reuse `DbUserQueryCondition`, and remove all deletion predicates; retain the original logic-delete-aware `pageUsers`.
- [x] Remove deleted-only Account domain, CQRS, Facade and RPC contracts.
- [x] Verify focused Account tests pass.

### Task 2: Remove the Admin deleted-user branch

- [x] Change Admin application and HTTP tests first to expect one user-page route only.
- [x] Verify RED against the existing deleted route and adapter method.
- [x] Remove deleted-user conditions, application method, adapter method and `/users/deleted/page` endpoint.
- [x] Verify focused Admin tests pass.

### Task 3: Simplify the Admin UI

- [x] Change the Users view test first to expect deleted rows in the normal response and no mode switch.
- [x] Verify RED against the existing `deletedUsers` API and toolbar action.
- [x] Remove the deleted API method, mode state, alternate parameters and toolbar action; retain deleted row labels and disabled writes.
- [x] Run UI unit tests and production build.

### Task 4: Documentation and verification

- [x] Update the accepted design and Admin README to document unified raw pagination.
- [x] Record the implementation deviation in the previous completed plan without rewriting its historical tasks.
- [x] Run focused tests, `./scripts/verify.sh quick`, `./scripts/verify.sh full`, and `git diff --check`.
- [x] Archive this plan after all gates pass.

## Completion criteria

- The administrator user list contains both ordinary and logically deleted users.
- No deletion-state filter or deleted-only list endpoint remains.
- Deleted rows remain visibly marked and cannot be modified.
- Ordinary Account runtime queries still exclude logically deleted users.

## Completion evidence

- Account and Admin module test suites passed.
- Admin UI unit tests, type checking, and production build passed.
- `./scripts/verify.sh quick` and `./scripts/verify.sh full` completed with exit code 0.
- `git diff --check` passed, and no deleted-only production contract or UI mode remains.
