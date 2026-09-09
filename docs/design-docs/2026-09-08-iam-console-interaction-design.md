# IAM Console Interaction Design

## Context

The IAM console exposes the required management operations, but its interaction model is fragmented:

- application and role creation forms are permanently inserted above or below tables;
- password and role editors appear at the bottom of the administrator page, away from the selected row;
- OAuth clients can be registered, rotated and disabled but cannot be listed after creation;
- browser-native confirmation dialogs, page-level loading and unstructured error paragraphs provide weak feedback;
- every row exposes many equally weighted actions, including destructive actions;
- application roles and permissions live on a separate page without a coherent application configuration context.

The console is a security administration tool. The design prioritizes clear ownership, efficient scanning, explicit consequences and recoverable operations over dashboard decoration.

## Selected direction

The console keeps a conventional left-navigation management shell. Lists remain the primary workspace. Editors open in a right-side drawer so the user retains page and selection context. Low-frequency or destructive row actions move into a `More` menu, while the most common safe action stays visible.

An application now has a dedicated detail page with three tabs:

1. OAuth clients;
2. application roles;
3. permission catalog.

This replaces the current split between the application list, an inline client form and a separate role/permission route. The application header consistently shows its name, `appKey`, business ID and status.

Rejected alternatives:

- an application-list drawer was rejected because clients, roles and permissions would remain fragmented and the drawer would become a second page;
- expandable nested table rows were rejected because the client model has too many fields for a stable nested-table layout;
- centered dialogs were rejected for long forms;
- a multi-step client wizard was rejected because client registration is one bounded operation and experienced administrators should complete it without artificial steps.

## Page structure

### Shared shell

The left navigation retains Management Accounts, Applications and Online Sessions. It gains consistent active state, section labels and a footer identity/logout area. The content area uses a shared page header, breadcrumb, action slot and bounded content width. On narrow screens the navigation collapses and tables remain horizontally scrollable without hiding actions.

The implementation remains local Vue 3 and CSS. It does not introduce Element Plus or another design-system dependency. Small reusable UI components are justified only for behavior repeated across pages:

- `AppDrawer` for focus-aware right-side editing;
- `ConfirmDialog` for explicit destructive confirmation;
- `ToastRegion` for success feedback;
- `StatusBadge` for consistent status semantics;
- `DataState` for loading, empty and retry states.

### Applications

The application page becomes a scan-first table. Register Application moves to a right drawer. Clicking an application name or its primary Manage action opens the application detail route using the stable `appId`; the page loads authoritative application data through `GET /api/iam/applications/detail?appId=...` rather than trusting query-string display values. The Controller constructs `ApplicationGetQuery`, `ApplicationAppService.get` loads the aggregate through `ApplicationRepository.find(AppId)`, and the existing application DTO/HTTP transformer returns the detail or a stable not-found error.

The application detail page keeps one application context while switching tabs. Each tab owns its loading, error, empty and paging state so failure in one tab does not erase the other tabs.

### OAuth clients

The OAuth Client tab displays client name, Client ID, target application, grant mode, status and actions. Expanded detail or secondary text shows scopes and redirect URIs. Client Secret and its persisted digest are never returned or displayed.

Register Client opens a right drawer. A Browser/Machine segmented choice determines the protocol fields:

- Browser preselects Authorization Code and Refresh Token, explains PKCE and requires login/logout redirect URIs;
- Machine preselects Client Credentials and omits redirect fields;
- both require a name, Client ID, one target application, explicit scopes and a one-time Secret input.

The drawer keeps input after a server failure. Native and local validation appear next to the affected field; server failures appear in the drawer without closing it. Successful registration closes the drawer, displays a toast and reloads the client tab. Secret rotation uses a smaller drawer with the same one-time-input warning, followed by a confirmation that names the Client and states that the previous deployed credential becomes invalid immediately. Disable uses a destructive confirmation dialog and updates only the affected row while pending.

### Roles and permission catalog

The existing role and permission data move into separate tabs on the same application detail page. Role creation uses a drawer with role code, name and a searchable permission checklist. The checklist reuses `GET /api/iam/permissions/page`, extended with an optional server-side `keyword`; `ApplicationPermissionPageQuery` carries `appId`, keyword and `Paging`, and the Repository applies the filter before paging with permission code and business ID as stable ordering. The drawer keeps selected permission IDs across result pages and keyword changes and shows selected items separately, so creating a role never depends on only the currently visible page. The endpoint remains protected by `PERMISSION_READ` and rejects an unknown application through the existing application-service check. The permission-catalog tab uses the same paged read model and remains read-only. A role-list failure does not prevent viewing the permission catalog when its request succeeds.

### Management accounts

The administrator table keeps Assign Roles as the primary action. Enable/Disable, Reset Password, Revoke Sessions and Delete move into a `More` menu, with availability determined by permission and current status. Reset Password and Assign Roles use drawers tied to the selected administrator. Delete, Disable and Revoke Sessions use explicit confirmation dialogs containing the administrator name and consequence. Pending state is scoped to the selected row or open drawer.

The current raw role-ID input remains a known API limitation unless an IAM internal-role read model is added in the same implementation plan. The plan must not present a fake selector without authoritative role data.

### Online sessions

The session list receives the shared table, status and feedback behavior. Revoke is a destructive row action with an explicit confirmation. Timestamp formatting and paging semantics remain unchanged.

## Backend read model

The missing client list requires one new read flow:

```text
GET /api/iam/oauth-clients/page?appId=...&pageNo=...&pageSize=...
    -> OAuthClientController
    -> OAuthClientAppService.page(...)
    -> OAuthClientRepository.pageByApplication(...)
    -> MyBatis service
```

The query uses a dedicated application-layer `OAuthClientPageQuery` containing `appId` and `Paging`. The Repository receives `AppId` and `Paging`, returns a page of `OAuthClient` aggregates and does not expose MyBatis types. The application service verifies that the owner application exists, maps clients to a read DTO and resolves non-sensitive audience application display information.

Repository paging orders by normalized OAuth Client ID ascending and then database ID ascending. Client ID is currently unique, while the database-ID tie-breaker keeps ordering deterministic if uniqueness rules change or normalized values collide in a future migration.

The HTTP response includes:

- Client ID and display name;
- owner and audience application IDs;
- audience `appKey` for display;
- grant types, scopes, redirect and post-logout redirect URIs;
- active/disabled status.

The response explicitly excludes raw Secret, encoded Secret, authorization records and Tokens. The list is protected by the existing application-read permission because it is a subordinate read model of the selected application; registration, rotation and disable continue to require client-write permission. No new permission code or role migration is introduced.

Application, administrator and session list filtering is not added unless the corresponding server query supports correct full-result filtering. Their visual search controls are deferred rather than implemented as misleading current-page-only filters. Permission keyword search is included because its Repository filter executes before paging.

## Interaction state model

Every page distinguishes these states:

- initial loading: table skeleton or loading placeholder;
- loaded with records: table and paging controls;
- loaded empty: contextual empty state with a permitted primary action;
- failed: compact error state with Retry, preserving the previous successful data when available;
- mutating: only the submitting drawer or selected row is disabled;
- mutation success: toast plus authoritative reload;
- mutation failure: drawer/row stays in context and displays the error without discarding input.

Rapid repeated submissions are prevented with a per-operation pending flag. Page changes are applied only after a successful load; a failed previous/next request does not leave the pager pointing at an unloaded page. Drawers warn before closing only when the form is dirty.

## Accessibility and responsive behavior

Drawers and confirmation dialogs use dialog semantics, move focus inside on open, close on Escape when safe, restore focus to the triggering control and expose labelled close buttons. Toasts use a polite live region. Status is conveyed with text as well as color. Keyboard users can reach row menus and actions without hover.

At narrow widths the shell becomes a top header with collapsible navigation. Tables scroll as a unit; operation cells stay readable and drawers become full-width. No business action disappears solely because of viewport width.

## Testing

Backend tests cover:

- authoritative application detail loading and not-found behavior;
- page-by-application Repository filtering and deterministic paging;
- application-service rejection of an unknown application;
- response mapping of grant, scope, URI, audience and status fields;
- absence of every Secret/digest field in DTO and HTTP response;
- Controller authorization for application-read versus client-write operations.
- permission keyword filtering before paging, stable ordering, unknown-application rejection and `PERMISSION_READ` authorization.

Frontend tests cover:

- navigation and application-detail routing by stable `appId`;
- successful, empty and failed Client list states;
- Browser/Machine form field switching and payload construction;
- drawer input preservation on failure and reset on success;
- row-scoped pending state, destructive confirmation and success toast, including Secret-rotation cancellation and confirmation;
- role/permission tabs loading independently;
- permission search paging while retaining selected permission IDs across pages;
- administrator action visibility by permission/status;
- IAM API client contracts for application detail, OAuth Client paging and permission paging keyword parameters, including `WireLong` values;
- keyboard close/focus restoration for drawer and confirmation components.

The IAM UI quality gates remain `npm run lint`, `npm run format:check`, `npm run test:unit`, `npm run typecheck` and `npm run build`. Server and architecture verification use the repository quick and full gates.

## Rollout and compatibility

The application-detail and client-page endpoints are additive. The existing permission-page endpoint gains only an optional keyword and remains backward compatible. Existing create, rotate, disable, role and session APIs remain stable. The old `/permissions/applications/access` hash route reads its existing `appId` query parameter and redirects explicitly to `/permissions/applications/{appId}?tab=roles`; a missing `appId` redirects to the application list. The old `appKey` display parameter is discarded because the detail endpoint is authoritative. No duplicate page implementation is retained.

The UI and IAM Server ship together in the same JAR, so the new page never runs against a server lacking its client-list endpoint. Rollback restores the previous bundled UI and server together.

## Role assignment and editable access configuration

IAM internal roles and application roles remain visibly separate:

- Management Accounts loads the assignable IAM internal-role catalog and the selected administrator's current internal-role IDs before opening its role drawer. Saving continues to use the internal administrator-role relation only.
- Application Detail adds a Members tab. It pages administrators, loads the selected administrator's roles for the current application and replaces only that application's assignments. Other applications and IAM internal roles are not changed.

Application role rows expose Edit. The editor loads the role's current permission IDs, retains paged/search selections and uses the existing role-update use case to replace name and permissions. Updating application-role assignments or permissions revokes the affected administrator OAuth sessions so a new access token carries the new permission snapshot.

OAuth Client rows expose Edit Configuration. The use case may replace scopes and, for browser clients, redirect and post-logout redirect URI sets; client identity, owner, audience and grant family remain immutable. The aggregate validates the complete revised protocol configuration. Every successful client configuration update revokes active authorizations issued to that client, because existing tokens otherwise retain removed scopes or old redirect assumptions.
