# IAM Console Interaction Execution Plan

Status: completed after focused, quick and full verification.

## Objective and non-goals

Turn the IAM administration UI into a scan-first management console with recoverable drawer workflows, an authoritative application detail workspace and a visible OAuth Client list.

The work does not introduce a third-party UI framework, expose Client Secrets or persisted Secret digests, add OAuth Client update/reactivation behavior, change OAuth grants, or add an IAM internal-role list/read model. Administrator role replacement remains an explicit role-ID operation in this iteration, but moves into a contextual drawer with clearer guidance.

## Design references

- `docs/design-docs/2026-09-08-iam-console-interaction-design.md`
- `docs/design-docs/2026-08-26-management-iam-design.md`
- `docs/references/CODING_GUIDE.md`
- `docs/references/UNIT_TEST_GUIDE.md`
- `docs/SECURITY.md`

## Affected modules and ownership boundaries

- `im-iam-server` owns application, OAuth Client, permission and administrator read/write use cases and HTTP contracts.
- `im-iam-server/ui` owns the bundled Vue console, routing and browser interaction state.
- Domain repositories continue to return aggregates and never HTTP/MyBatis models.
- Client-list output is a non-sensitive read DTO; Web Client, Catalog Client and authorization Secret material remain server-only.

## Ordered implementation tasks

### 1. Add authoritative application detail

**Files**

- Create `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/model/cqrs/query/ApplicationGetQuery.java`.
- Modify `.../application/ApplicationAppService.java` with `get(ApplicationGetQuery)`.
- Modify `.../interfaces/http/ApplicationController.java` with `GET /api/iam/applications/detail?appId=...`, protected by `APPLICATION_READ`.
- Modify `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/application/ApplicationAppServiceTest.java`.
- Create `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/interfaces/http/ApplicationControllerTest.java`.

**TDD steps**

- [x] Add failing tests for a known application, stable not-found behavior and HTTP mapping using a `WireLong`-safe response.
- [x] Implement Query, application-service load and Controller delegation without introducing a duplicate detail DTO.
- [x] Run focused application service and Controller tests.

### 2. Add OAuth Client page-by-application read flow

**Files**

- Create `.../model/cqrs/query/OAuthClientPageQuery.java`.
- Create `.../model/cqrs/dto/OAuthClientListDTO.java`.
- Create `.../model/io/OAuthClientListResponse.java`.
- Modify `.../domain/application/repository/OAuthClientRepository.java`.
- Modify `.../infrastructure/domain/repository/MysqlOAuthClientRepository.java`.
- Modify `.../application/OAuthClientAppService.java`.
- Modify `.../interfaces/http/OAuthClientController.java`.
- Modify `.../transformer/application/OAuthClientAppTransformer.java` and `.../transformer/interfaces/OAuthClientHttpTransformer.java`.
- Modify `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/infrastructure/domain/repository/MysqlOAuthClientRepositoryTest.java`.
- Modify `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/application/OAuthClientAppServiceTest.java`.
- Create `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/interfaces/http/OAuthClientControllerTest.java`.
- Create `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/transformer/interfaces/OAuthClientHttpTransformerTest.java`.

**Behavior**

- [x] Add failing Repository tests proving owner `AppId` filtering, `oauth_client_id ASC, id ASC` ordering and deterministic paging.
- [x] Add failing application-service tests proving owner existence, audience display resolution and exact mapping of grant types, scopes, redirect URIs and status.
- [x] Add failing boundary tests proving `GET /api/iam/oauth-clients/page` requires `APPLICATION_READ` and never exposes raw/encoded Secret or authorization data.
- [x] Implement `OAuthClientRepository.page(AppId, Paging)` and application orchestration.
- [x] Run focused OAuth Client tests.

### 3. Add permission keyword paging for the role drawer

**Files**

- Modify `.../model/cqrs/query/ApplicationPermissionPageQuery.java`.
- Create `.../domain/authorization/model/ApplicationPermissionQueryCondition.java` with non-null `Optional<String> keyword`; its constructor trims blank input to `Optional.empty()`.
- Modify `.../domain/authorization/repository/ApplicationPermissionRepository.java`.
- Modify `.../infrastructure/domain/repository/MysqlApplicationPermissionRepository.java`.
- Modify `.../application/ApplicationPermissionAppService.java`.
- Modify `.../interfaces/http/ApplicationPermissionController.java`.
- Create `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/model/cqrs/query/ApplicationPermissionPageQueryTest.java`.
- Modify `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/infrastructure/domain/repository/MysqlApplicationPermissionRepositoryTest.java`.
- Modify `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/application/ApplicationPermissionAppServiceTest.java`.
- Create `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/interfaces/http/ApplicationPermissionControllerTest.java`.

**Behavior**

- [x] Add failing Query tests proving nullable/blank keywords normalize to `Optional.empty()` and nonblank keywords are trimmed before `ApplicationPermissionAppService` creates `ApplicationPermissionQueryCondition`.
- [x] Add failing Repository tests for code/name matching before paging and permission-code/business-ID ascending ordering through `page(AppId, ApplicationPermissionQueryCondition, Paging)`.
- [x] Add failing Controller/application tests for unknown application, `PERMISSION_READ`, explicit keyword propagation and omission of keyword remaining backward compatible.
- [x] Implement the Query, condition and Wrapper changes without accepting nullable `Optional` containers.
- [x] Run focused permission tests.

### 4. Establish local UI interaction primitives

**Files**

- Create `ui/src/components/AppDrawer.vue`.
- Create `ui/src/components/ConfirmDialog.vue`.
- Create `ui/src/components/ToastRegion.vue`.
- Create `ui/src/components/StatusBadge.vue`.
- Create `ui/src/components/DataState.vue`.
- Create `ui/src/state/feedback.ts` only if shared toast state cannot remain in `App.vue` without prop drilling.
- Add component tests under `ui/tests`.

**Behavior**

- [x] Add failing tests for drawer Escape/overlay behavior, dirty close guard, focus entry/restoration and pending lock.
- [x] Add failing tests for confirmation cancellation/confirmation, labelled target/consequence and destructive styling.
- [x] Add failing tests for polite toast live region and loading/empty/error Retry states.
- [x] Add failing tests proving `StatusBadge` always renders status text, `DataState` preserves prior successful records during retry failure, and row menus are keyboard reachable without hover.
- [x] Add responsive structure assertions/CSS checks for collapsed narrow navigation, horizontally scrollable table containers and full-width mobile drawers.
- [x] Implement accessible components with no new production dependency.
- [x] Run UI unit tests, lint and typecheck.

### 5. Rebuild the shell and application list

**Files**

- Modify `ui/src/App.vue`, `ui/src/style.css`, `ui/src/navigation.ts` and `ui/src/views/ApplicationsView.vue`.
- Extend `ui/tests/App.spec.ts`, navigation tests and add/update Applications view tests.

**Behavior**

- [x] Add failing tests for active navigation, identity/logout footer, register-application drawer, success toast, retained form on failure and successful authoritative reload.
- [x] Add Applications page integration tests for initial loading, loaded records, contextual empty action, failed Retry and preservation of prior records when a reload fails.
- [x] Implement the selected standard console shell and scan-first application table.
- [x] Keep search/status controls out until server-side filtering exists.
- [x] Run affected UI tests, lint, format check and typecheck.

### 6. Build the application detail workspace and API contracts

**Files**

- Replace `ui/src/views/ApplicationAccessView.vue` with `ui/src/views/ApplicationDetailView.vue`.
- Modify `ui/src/router.ts`, `ui/src/api/iam.ts` and `ui/src/style.css`.
- Replace/extend `ui/tests/ApplicationAccessView.spec.ts`, `Router.spec.ts` and `IamApi.spec.ts`.

**Behavior**

- [x] Add failing API seam tests for `/applications/detail`, `/oauth-clients/page` and `/permissions/page` with explicit/omitted keyword parameters and string `WireLong` values.
- [x] Add failing API seam tests for OAuth Client registration, Secret rotation and disable POST URLs and exact request bodies; view mocks alone do not satisfy this contract.
- [x] Add failing routing tests for `/permissions/applications/:appId`, authoritative header loading and tab query synchronization.
- [x] Add legacy-route tests proving `appId` is preserved, `appKey` is discarded, `tab=roles` is selected, and a missing `appId` returns to the application list.
- [x] Add failing Client tab tests for loading, empty, Retry, deterministic page changes and detail rendering without Secret fields.
- [x] Add failing register drawer tests for Browser/Machine field switching; Browser grant defaults, required redirect fields and PKCE explanation; Machine Client Credentials default and omitted redirect fields; shared required fields; one-time Secret guidance; exact payload; duplicate-submit prevention; failure retention and success reset/toast.
- [x] Add failing Secret-rotation and disable tests for cancellation, consequence text, row-scoped pending and reload.
- [x] Add failing role-drawer tests for required code/name validation, exact create payload, server-paged permission search, retained selections across pages/search, failure input retention, success reset/toast and independent role/permission error states.
- [x] Add page-level integration tests for Roles and Permission Catalog loading, records, contextual empty actions, failed Retry and preservation of the other tab's successful data.
- [x] Implement Client, Roles and Permission Catalog tabs with independent state.
- [x] Implement permission keyword paging with selected IDs retained across pages/search; do not fake a current-page-only search.
- [x] Run affected UI tests, lint, format check and typecheck.

### 7. Improve administrator and session workflows

**Files**

- Modify `ui/src/views/AdministratorsView.vue`, `ui/src/views/SessionsView.vue` and `ui/src/style.css`.
- Add/extend administrator and session view tests.

**Behavior**

- [x] Add failing tests for permission/status-aware primary and More actions, reset/role drawers, retained failure input and row-scoped pending state.
- [x] Add failing tests for Disable/Delete/Revoke consequence confirmation and success toast.
- [x] Add Administrator and Session page integration tests for initial loading, records, contextual empty state, failed Retry and prior-data preservation.
- [x] Add Session tests proving existing timestamp formatting and paging request semantics remain unchanged.
- [x] Ensure a failed page transition leaves the previous paging state and data intact.
- [x] Implement the workflows without changing server mutation APIs.
- [x] Run affected UI tests, lint, format check and typecheck.

### 8. Documentation, Harness and verification

**Files**

- Modify `im-management/im-iam/im-iam-server/README.md`.
- Modify `docs/references/HARNESS_GUIDE.md` if Review establishes a reusable UI-state or sensitive-read-model convention.
- Update this plan and execution-plan indexes.

**Steps**

- [x] Document the application detail workspace, OAuth Client list contract and non-sensitive response boundary.
- [x] Run `npm run lint`, `npm run format:check`, `npm run test:unit`, `npm run typecheck`, and `npm run build` in the IAM UI.
- [x] Run focused IAM Server tests.
- [x] Run `./scripts/verify.sh quick`.
- [x] Run `./scripts/verify.sh full`.
- [x] Move this plan to `docs/exec-plans/completed` only after every gate passes.

## Implementation findings

- OAuth Client list mapping initially resolved each audience application separately. The final implementation adds one bounded `ApplicationRepository.find(Set<AppId>)` call and maps distinct audience IDs without an N+1 query.
- Repository paging behavior is covered through narrow H2/MyBatis integration tests with mixed owners and adjacent pages. JaCoCo cannot instrument JSQLParser's generated oversized method and prints a non-fatal warning; test and repository gates still execute and must exit successfully.
- Responsive layout remains a Review/build concern because jsdom does not compute CSS layout. The rejected test only scanned CSS text and did not prove browser behavior; the implementation instead keeps explicit mobile media rules, scroll containers and full-width drawers, with visual review plus production build as the current sensor.
- At the user's request, implementation continued without subagents after the permission-paging task; remaining TDD, review and verification were completed in the primary session.

## Test and verification strategy

```bash
mvn -q -pl im-management/im-iam/im-iam-server -am test
cd im-management/im-iam/im-iam-server/ui
npm run lint
npm run format:check
npm run test:unit
npm run typecheck
npm run build
cd /Users/kc/Code/private/im-chat
./scripts/verify.sh quick
./scripts/verify.sh full
```

Tests use deterministic fake repositories, mocked HTTP adapters and controlled Vue promises. They do not use sleeps, production no-op implementations or current-page-only assertions that bypass server paging semantics.

## Rollout, compatibility and rollback

The two new GET endpoints are additive and permission paging remains backward-compatible: application detail and Client page are new; permission page only gains an optional keyword. Existing mutations remain unchanged. The bundled UI and IAM Server ship in one JAR.

The legacy `/permissions/applications/access?appId=...` route redirects to `/permissions/applications/{appId}?tab=roles`; missing IDs return to the application list. Rollback restores the previous bundled UI and server together. No database migration is required.

## Completion criteria

- Applications, OAuth Clients, roles and permissions form one coherent application workspace.
- OAuth Clients are queryable by owner application with deterministic paging and no Secret exposure.
- Drawer operations preserve context/input on failure and scope pending state to the active operation.
- Destructive operations and Secret rotation require explicit consequence confirmation.
- Every list has meaningful loading, empty, error and Retry states.
- Legacy application-access links retain their role-management intent.
- Backend, UI, quick and full verification pass.
