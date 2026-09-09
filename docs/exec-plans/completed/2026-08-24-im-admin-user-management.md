# IM Admin User Management Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver an independently authenticated and audited `im-admin` application for ordinary-user management, administrator accounts, custom roles, permissions, and audit-log queries.

**Architecture:** Keep ordinary-user facts and state transitions in `im-account-server`, expose them through the contract-only `im-account-admin-facade`, and let `im-admin` own administrator identity, Redis sessions, RBAC, audit persistence, HTTP APIs, and its Vue UI. Browser traffic terminates at `im-admin`; the application never reads Account tables or Sessions directly.

**Tech Stack:** Java 21, Spring Boot 3.5, Dubbo, MyBatis-Plus/MySQL, Redis, BCrypt, MapStruct, Lombok, Maven, JUnit 5, AssertJ, MockMvc, Vue 3, TypeScript, Vite, Vitest, Element Plus, Axios.

---

## Objective and non-goals

Implement the approved [IM Admin 用户与权限管理设计](../../design-docs/2026-08-24-im-admin-user-management-design.md).

The first version provides:

- ordinary-user list, detail, profile update, password reset, ban, unban, and logical delete;
- deleted-user visibility without disabling Account's global logical-delete behavior;
- independent administrator accounts and Redis-backed browser sessions;
- fixed permission codes, built-in roles, custom roles, and administrator-role assignment;
- fail-closed authorization and audit-first dangerous commands;
- an independent Vue administration UI bundled into `im-admin.jar`.

It does not reuse ordinary Account authentication, physically delete ordinary users, restore deleted users, cascade-delete business history, expose credentials or hashes, add MFA, implement multi-tenancy/data scopes/approval flows, or move administration behavior into `im-monitor`.

## Current-state reconciliation (2026-08-24)

- `im-management/im-admin` is currently a POM/README placeholder. This plan turns it into one Spring Boot runtime and keeps its source, configuration, session, Controller, and UI independent from `im-monitor`.
- `db_user` currently has no business-status column. The Account migration adds `NORMAL/BANNED`; `DELETED` remains a management projection derived from `is_deleted`, not a third stored business status.
- Account's normal repository is filtered by MyBatis logical deletion. Management queries use a dedicated read Mapper that explicitly selects deleted rows; they must not disable the global filter for ordinary repositories.
- `SessionRepository` currently exposes only `find/save`. Administrative revocation is expressed as a Session domain transition under the existing Account Session lock and followed by connection close; no Redis key or compare-and-set detail enters the application contract.
- The repository has no shared pagination contract suitable for the new Facade. The Admin Facade therefore owns small serializable page query/result records with page-size validation and no dependency on MyBatis types.
- The project does not currently use Spring Security. The first version uses explicit `im-admin` authentication/CSRF filters or interceptors and permission annotations around a Redis Session store, without introducing a second opaque security model. These components remain in `interfaces`/`infrastructure`; administrator and role rules stay in domain/application code.
- API style follows the approved project convention: GET for queries and POST for commands. Logical delete remains `POST /api/users/delete`, not HTTP `DELETE`.
- The existing worktree contains completed Broker monitoring changes. Implementation must preserve those changes, use `verify.sh affected` while iterating, and must not stage or commit unless the user explicitly requests it.
- The user requested local execution without sub-agents and without a worktree. Execute the tasks sequentially in the current worktree, updating this plan as each task completes.

## Affected modules and ownership

- `im-service/im-account/im-account-admin-facade`: high-privilege ordinary-user management contracts only; no Spring, MyBatis, Redis, Account Server, or Admin runtime dependency.
- `im-service/im-account/im-account-server`: owns user state, management queries/commands, password hashing, cache invalidation, Session revocation, and Dubbo implementation of the Admin Facade.
- `im-management/im-admin`: owns administrator domain/application, Admin MySQL/Redis persistence, bootstrap, browser authentication, RBAC, audit, HTTP APIs, and Vue UI.
- `sql/ddl.sql`: Account user-status baseline DDL. `sql/admin-ddl.sql`: independently owned Admin schema baseline without `DROP TABLE` outside the permitted local rebuild file.
- `im-architecture`: Admin Facade and Admin runtime dependency/layer boundaries.
- `scripts`, root/module READMEs, `ARCHITECTURE.md`, `SECURITY.md`, and `RELIABILITY.md`: affected-module mapping, ownership, configuration, security, audit, and operational behavior.

## Ordered implementation tasks

### Task 1: Lock module and architecture boundaries

**Files:**

- Modify `im-service/im-account/pom.xml`
- Create `im-service/im-account/im-account-admin-facade/pom.xml`
- Modify `im-service/im-account/im-account-server/pom.xml`
- Modify `im-management/im-admin/pom.xml`
- Modify `im-architecture/pom.xml`
- Modify `im-architecture/src/test/java/com/co/kc/imchat/architecture/ModuleBoundaryTest.java`
- Modify `im-architecture/src/test/java/com/co/kc/imchat/architecture/LayerBoundaryTest.java`
- Modify `im-architecture/src/test/java/com/co/kc/imchat/architecture/RuntimeDependencyPolicyTest.java`
- Modify `scripts/affected-modules.sh`
- Modify `scripts/test-harness.sh`

- [x] Add failing ArchUnit/build-policy tests proving that the Admin Facade cannot depend on server/runtime packages, `im-admin` cannot depend on Account Server implementations, Admin domain cannot depend on Spring/MyBatis/Redis/HTTP/Dubbo, and no module except `im-admin` and Account Server may depend on `im-account-admin-facade`.
- [x] Add failing affected-module fixtures proving Admin Facade changes select the Facade, Account Server, `im-admin`, and architecture verification, while Admin runtime changes select only `im-admin` plus architecture.
- [x] Add the new Facade module and convert `im-admin` from `pom` packaging to a runnable jar with only required dependencies: common, Admin Facade, Dubbo, datasource, Redis, web/validation, BCrypt, MapStruct, Lombok, and tests. Do not add a dependency on `im-account-server` or `im-monitor`.
- [x] Add a minimal `ImAdminApplication` context test with Nacos, Dubbo, MySQL, Redis, and bootstrap integrations deterministically disabled or replaced by test configuration.
- [x] Run `mvn -q -pl im-service/im-account/im-account-admin-facade,im-management/im-admin,im-architecture -am test`; expected first RED from missing module/application boundaries, then GREEN after skeleton wiring.

### Task 2: Define the Account Admin Facade contract

**Files:**

- Create `im-service/im-account/im-account-admin-facade/src/main/java/com/co/kc/imchat/service/account/admin/facade/AccountAdminService.java`
- Create records/enums under `.../command`, `.../query`, and `.../dto`
- Create `im-service/im-account/im-account-admin-facade/src/test/java/.../AccountAdminContractTest.java`

- [x] Write failing contract tests for Java serialization, null/blank/bounds validation, page defaults (`20`) and maximum (`100`), stable user states, stable management error codes, and absence of password/hash/Token/Session fields.
- [x] Define one input object and one explicit output object per public use case: page/detail queries; update, reset-password, ban, unban, and delete commands. Keep Facade values framework-neutral and use records for immutable carriers.
- [x] Define a Facade-owned page result rather than exposing MyBatis `Page/IPage`. Keep `DELETED` visible in DTO state while preserving `NORMAL/BANNED` as Account's stored domain states.
- [x] Document public methods and state/error semantics. Do not expose overloads with scalar parameters or compatibility wrappers.
- [x] Run `mvn -q -pl im-service/im-account/im-account-admin-facade -am test`.

### Task 3: Add Account user state and deleted-user management queries

**Files:**

- Modify `sql/ddl.sql`
- Modify `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/domain/user/model/User.java`
- Create `.../domain/user/model/UserStatus.java`
- Modify `.../infrastructure/mybatis/entity/DbUser.java`
- Modify `.../infrastructure/mybatis/mapper/DbUserMapper.java`
- Modify/create `.../infrastructure/mybatis/service/DbUserService.java` and `src/main/resources/mapper/DbUserMapper.xml`
- Modify `.../transformer/domain/UserDomainTransformer.java`
- Add focused domain, Mapper, service, and transformer tests under matching test packages

- [x] Write failing domain tests for `NORMAL -> BANNED -> NORMAL`, repeated ban idempotency, and invalid transitions leaving aggregate state unchanged. `DELETED` is not an Account aggregate status; its terminal management-command behavior is covered in Task 5 against the logical-delete projection.
- [x] Write failing persistence tests/fixtures for page ordering, exact user ID/email, username search, status filtering, and explicit inclusion of deleted rows. Prove ordinary `UserRepository` queries still exclude deleted rows.
- [x] Add a non-null `status` column with a backward-compatible `NORMAL` default and a query-supporting index to `db_user`; update entity and MapStruct mapping.
- [x] Implement a dedicated management read path with explicit column lists and bounded pagination. Mapper SQL must contain no `SELECT *`, `${...}`, unbounded static write, or global logical-delete disablement.
- [x] Keep password/hash out of management projections even though the ordinary aggregate still owns password state.
- [x] Run `bash scripts/test-sql-harness.sh` and focused Account domain/MyBatis tests.

### Task 4: Enforce user state in ordinary authentication

**Files:**

- Modify `.../domain/user/service/UserService.java`
- Modify `.../application/SessionAppService.java`
- Modify related Account Transformers only when boundary conversion changes
- Modify `.../domain/user/service/UserServiceTest.java`
- Modify `.../application/SessionAppServiceTest.java`

- [x] Add failing tests proving `BANNED` and logically deleted users cannot sign in, refresh, or pass Access Token online authentication; `NORMAL` users retain existing behavior.
- [x] Place the durable eligibility rule in the User aggregate/domain service. Application code may coordinate the user lookup but must not duplicate status predicates across sign-in, refresh, and access authentication.
- [x] Ensure Session revocation/connection close remains an acceleration mechanism: authentication must still fail from Account user state if Redis cleanup or Broker notification fails.
- [x] Preserve current two-hour Access Token and refresh behavior for eligible users; this task changes authorization state, not token format or TTL.
- [x] Run `mvn -q -pl im-service/im-account/im-account-server -am -Dtest='UserServiceTest,SessionAppServiceTest' -DfailIfNoTests=false test`.

### Task 5: Implement Account management commands and RPC service

**Files:**

- Create `.../application/UserAdminAppService.java`
- Create `.../model/cqrs/command/*Admin*Cmd.java` and `.../model/cqrs/query/*Admin*Query.java`
- Create `.../model/cqrs/dto/UserAdminDTO.java` and page result models
- Create/modify `.../transformer/application/AccountAdminTransformer.java`
- Create `.../interfaces/rpc/AccountAdminRpcService.java`
- Modify Account Bean configuration and `application.yml`
- Modify cache/repository infrastructure only where invalidation or deleted-row reads require it
- Add application/RPC/transformer tests

- [x] Add failing application tests for profile update, duplicate email, reset password, ban, unban, logical delete, repeated commands, deleted-user conflicts, missing users, and per-user serialization.
- [x] Add failing tests proving password reset, ban, and delete revoke the authenticated Session and close the relevant user's connections after persistence succeeds; unban does not restore an old Session.
- [x] Use the existing Account Session lock scene and domain transitions. Do not expose Redis keys, locks, or connection parameters through Repository or Facade contracts.
- [x] Keep Account cache invalidation explicit for user-ID and email lookup keys after profile/status/delete changes. Failed persistence must not revoke Sessions or report success. `ManagedUserAppService` schedules Session kick-out after the Account transaction commits, acquires the Session write lock, updates the Redis-backed Session state, and immediately asks the Account adapter to close the revoked version's connections without opening an unrelated database transaction.
- [x] Implement a thin Dubbo service that converts Admin Facade objects, delegates to the application service, and maps stable errors without exposing Account entities.
- [x] Run focused Account Admin application/RPC tests and the full Account module test suite.

### Task 6: Create the independent Admin schema and persistence adapters

**Files:**

- Create `sql/admin-ddl.sql`
- Create Admin domain packages under `im-management/im-admin/src/main/java/com/co/kc/imchat/management/admin/domain/{administrator,role,audit}`
- Create repository interfaces beneath the owning domains
- Create MyBatis entities, mappers, XML, services, repository implementations, and MapStruct transformers under `.../infrastructure/mybatis`
- Add domain, repository, transformer, and schema tests

- [x] Write failing domain tests for administrator activation/disablement/temporary lock, password replacement, role assignment, built-in role protection, custom-role lifecycle, permission composition, last-active-super-admin protection, and audit state transitions.
- [x] Write failing persistence tests for username/role-code uniqueness, administrator-role and role-permission relations, referenced-role deletion protection, audit ordering/filtering, and no sensitive columns in read projections.
- [x] Add `db_admin_user`, `db_admin_role`, `db_admin_permission`, `db_admin_user_role`, `db_admin_role_permission`, and `db_admin_audit_log` with explicit primary keys, comments, indexes, logical-delete semantics where applicable, and no forbidden `DROP TABLE` outside `sql/ddl.sql`.
- [x] Seed only fixed permission codes and built-in role definitions through an idempotent startup component or explicit initialization logic; do not embed an administrator password in SQL.
- [x] Keep domain identifiers/statuses as value objects/enums. Infrastructure entities may use primitive values and must convert at the boundary.
- [x] Run SQL Harness and focused Admin domain/persistence tests.

### Task 7: Implement administrator authentication, Redis Session, CSRF, and bootstrap

**Files:**

- Create typed properties under `.../config/properties` for security, cookie, Redis namespace, and one-time bootstrap
- Create password implementation under `.../infrastructure/domain/service`
- Create Redis Session store under `.../infrastructure/redis`
- Create authentication/CSRF context, annotations, filters/interceptors, and advice under `.../interfaces/http/security`
- Create `.../application/AdminAuthAppService.java` and CQRS models
- Create `.../lifecycle/AdminBootstrap.java`
- Create authentication Controller and IO models
- Add configuration, domain, store, application, filter/MockMvc, bootstrap, and startup tests

- [x] Add failing deterministic tests for successful login/logout/session lookup, wrong-password counting, five-failure lock, 15-minute unlock, 30-minute idle expiry, 8-hour absolute expiry, disabled admins, password-reset Session revocation, and administrator-wide Session revocation.
- [x] Add failing HTTP tests for `HttpOnly`, `SameSite=Strict`, production `Secure`, no browser credential in response bodies, CSRF success/failure, unauthenticated `401`, and permission failure `403`.
- [x] Implement cryptographically random opaque Session and CSRF identifiers, Redis keys under `im:admin:session:*`, administrator-to-session indexes, and renewal capped by absolute expiry. Never log IDs or Tokens.
- [x] Keep authentication infrastructure out of the Admin domain. Filters resolve an authenticated principal/context; application services enforce account state and domain behavior.
- [x] Implement one-time `SUPER_ADMIN` bootstrap only when the administrator table is empty. BCrypt-hash the configured password immediately; reject weak/bootstrap-invalid configuration, ignore-and-warn after initialization, and provide no public registration endpoint.
- [x] Use typed `@ConfigurationProperties`; do not add test-only constructors, embedded real credentials, or environment-variable placeholders for secrets.
- [x] Run focused security/bootstrap tests and `mvn -q -pl im-management/im-admin -am test`.

### Task 8: Implement RBAC administration use cases

**Files:**

- Create `.../application/AdminAccountAppService.java`
- Create `.../application/RoleAppService.java`
- Create CQRS models and application Transformers
- Add focused application tests

- [x] Add failing tests for administrator list/detail/create/update/reset-password/enable/disable and role list/detail/create/update/enable/disable/delete.
- [x] Cover fixed permission-catalog validation, built-in role immutability, referenced custom-role deletion conflict, disabled-role behavior, self-disable protection, self-removal of final super-admin permission, and at least one active super-admin invariant.
- [x] Revoke all Sessions after administrator disablement, password reset, or effective-role changes. Do not revoke ordinary Account Sessions from administrator-domain changes.
- [x] Keep permission checks explicit at the use-case boundary; UI visibility is not authorization. Application services accept one Command/Query object and return DTO/Result objects or `void`.
- [x] Run focused RBAC application tests.

### Task 9: Implement audit-first command execution

**Files:**

- Create `.../domain/audit/service/AdminAuditService.java` only if it carries the PENDING/SUCCESS/FAILED state rule rather than acting as a repository delegate
- Create audit context/redaction helpers under the narrow owning package
- Integrate audit transitions into authentication, ordinary-user, administrator, and role application services
- Add audit application/repository tests

- [x] Add failing tests proving dangerous commands persist `PENDING` before their external/state-changing operation, refuse execution when initial audit persistence fails, and transition to `SUCCESS` or `FAILED` with stable safe summaries.
- [x] Prove final audit-update failure preserves the original business result/exception, leaves a queryable `PENDING` record, and emits an operational warning. Read-audit failure must log and preserve the read response.
- [x] Add a redaction test matrix proving passwords, hashes, cookies, Session/CSRF IDs, ordinary-user Tokens, and stack traces cannot enter audit details.
- [x] Audit login success/failure/lock/logout, ordinary-user writes, administrator writes, and role writes. Include actor snapshot, target, permission, IP, bounded User-Agent summary, stable error code, and timestamp.
- [x] Avoid a generic exception-swallowing annotation for security/audit code; failure policy must remain visible and specific to the audit boundary.

### Task 10: Integrate Account Admin Facade and expose ordinary-user HTTP APIs

**Files:**

- Create `.../adapter/account/AccountAdminAdapter.java`
- Create `.../application/UserManagementAppService.java`
- Create `.../interfaces/http/user/UserManagementController.java`
- Create HTTP request/response models and boundary Transformer
- Add adapter, application, MockMvc, advice, and partial-failure tests

- [x] Add failing adapter tests proving Dubbo DTOs stay inside the adapter and browser/application models do not depend on Account Facade classes.
- [x] Add failing MockMvc tests for all approved ordinary-user endpoints, query bounds, deleted-user visibility, stable HTTP mapping (`400/401/403/404/409/502/503`), required permissions, CSRF, and dangerous-operation audit behavior.
- [x] Implement `GET /api/users/page`, `GET /api/users/detail`, and POST commands for update, reset-password, ban, unban, and delete. Controllers only validate, resolve context, delegate, and transform.
- [x] Preserve explicit target/impact confirmation as a UI concern while treating the server-side command and permission check as authoritative.
- [x] Run focused adapter/application/MockMvc tests.

### Task 11: Expose administrator, role, permission, and audit HTTP APIs

**Files:**

- Create Controllers under `.../interfaces/http/{administrator,role,audit}`
- Create IO records and MapStruct Transformers
- Extend centralized Admin HTTP advice
- Add MockMvc and serialization tests

- [x] Add failing tests for the approved GET-query/POST-command endpoint matrix, permission annotations, CSRF, pagination bounds, conflict/status mapping, and sensitive-field absence.
- [x] Implement administrator account page/detail/create/update/reset-password/enable/disable endpoints.
- [x] Implement role page/detail/create/update/enable/disable/delete and fixed permission-list endpoints.
- [x] Implement audit-log page query with bounded filters and newest-first ordering. Never expose raw internal exception text.
- [x] Add an endpoint inventory test proving there is no ordinary administrator registration endpoint, physical ordinary-user delete endpoint, deleted-user restore endpoint, arbitrary permission-create endpoint, or browser endpoint exposing password/hash/Session/Token values.
- [x] Run the complete `im-admin` backend test suite.

### Task 12: Build the independent Admin UI

**Files:**

- Create `im-management/im-admin/ui/package.json`, lockfile, Vite/TypeScript/Vitest configuration, and `index.html`
- Create `ui/src` application shell, Router, typed API clients, authentication state, and views/components
- Create `ui/tests` behavior tests
- Modify `im-management/im-admin/pom.xml` to build UI assets into `target/classes/static`

- [x] Start with failing Vitest tests for login/session expiry, navigation permissions, ordinary-user filtering/pagination/detail/deleted state, profile edit, reset-password, ban/unban/delete confirmations, administrator accounts, role composition, audit list, and loading/empty/error/forbidden/conflict states.
- [x] Implement navigation exactly as approved: `用户管理`, `权限管理 / 管理账号`, `权限管理 / 角色配置`, and `审计日志`.
- [x] Keep Session credentials in HttpOnly cookies; keep only non-sensitive principal/permission UI state in memory. Axios must send same-origin credentials and the CSRF header sourced from the non-HttpOnly CSRF delivery mechanism without using `localStorage` for credentials.
- [x] Disable all writes for deleted ordinary users, display DELETED distinctly, and require a second confirmation that names the target and impact for dangerous actions.
- [x] Hide/disable unauthorized routes and controls for usability while handling server `403` as the final authorization result.
- [x] Configure Maven production output to `target/classes/static`; do not copy generated assets into source resources or track `node_modules`, coverage, or `dist`.
- [x] Run `npm run test:unit`, `npm run typecheck`, `npm run build`, package the jar, and verify the static entry/assets are present in the jar.

### Task 13: Documentation, migration validation, and closure

**Files:**

- Modify `ARCHITECTURE.md`, root `README.md`, `im-management/AGENTS.md`, `im-management/README.md`, `im-management/im-admin/README.md`, and Account module README
- Modify `docs/SECURITY.md` and `docs/RELIABILITY.md`
- Modify design/plan indexes and relevant Harness fixtures/documentation only when a new low-false-positive rule is justified
- Update this plan during execution and move it to `completed` only after every gate passes

- [x] Document independent administrator identity, Admin/Account ownership, schema/configuration, local startup, bootstrap removal, cookie/CSRF behavior, RBAC catalog, audit failure policy, ordinary-user state semantics, and UI build/run workflow.
- [x] Add a production migration checklist for backfilling existing `db_user.status=NORMAL`, creating Admin schema/indexes, deploying Account before Admin, bootstrapping once, deleting Nacos bootstrap credentials, and limiting port `18091` to the operations network.
- [x] Review changed code against Coding, SQL, Unit Test, Security, DDD, Facade, Adapter, CQRS, Lombok/record, package-cohesion, and configuration/Bean Harness rules. Add mechanical checks only for deterministic low-noise gaps discovered by this implementation; do not encode business-specific class names or weaken existing rules.
- [x] Run `bash scripts/test-harness.sh`, `bash scripts/test-sql-harness.sh`, focused backend/frontend tests, `./scripts/verify.sh architecture`, and `./scripts/verify.sh affected` while iterating.
- [x] Run `./scripts/verify.sh quick` and `./scripts/verify.sh full` before claiming completion.
- [x] Run `git diff --check`; inspect `git status --short`, the complete diff, generated files, local secrets, UI artifacts, and ensure the earlier Broker monitoring work remains intact.
- [x] Move this plan to `docs/exec-plans/completed` and update active/completed/design indexes only after all completion criteria pass. Do not create a commit unless the user explicitly requests it.

## Completion evidence

- Admin backend and dependencies: `mvn -q -pl im-management/im-admin -am test` passed.
- Frontend: 4 Vitest files / 6 tests passed; `npm run typecheck` and `npm run build` passed.
- Static assets were packaged under `BOOT-INF/classes/static` in the runnable Admin jar.
- SQL Harness, Java/Harness fixtures, architecture, affected, quick, and full repository gates passed.
- `git diff --check` passed; generated UI dependencies/build output are ignored and no Admin bootstrap credential is stored in the repository.

## Test and verification strategy

Use strict RED-GREEN-REFACTOR within each task. A regression test must fail for the intended missing behavior before production implementation is added. Unit tests use real domain/value objects and mock only direct process-external collaborators; Spring, Dubbo, Redis, MySQL, and HTTP wiring are integration boundaries with deterministic test configuration.

Primary commands:

```bash
mvn -q -pl im-service/im-account/im-account-admin-facade -am test
mvn -q -pl im-service/im-account/im-account-server -am test
mvn -q -pl im-management/im-admin -am test

bash scripts/test-sql-harness.sh
bash scripts/test-harness.sh
./scripts/verify.sh architecture
./scripts/verify.sh affected

cd im-management/im-admin/ui
npm run test:unit
npm run typecheck
npm run build

cd /Users/kc/Code/private/im-chat
./scripts/verify.sh quick
./scripts/verify.sh full
git diff --check
```

Do not replace repository gates with focused tests. No test may depend on a real Nacos, Redis, MySQL, Dubbo registry, fixed network port, wall-clock sleep, or a production credential.

## Rollout, compatibility, and rollback

- Apply the Account user-status migration and backfill existing rows to `NORMAL` before deploying Account code that reads the field. Apply `admin-ddl.sql` to the independent Admin schema before starting `im-admin`.
- Deploy the Account Server with Admin Facade first, then `im-admin`. An older Account without the Facade is reported as management service unavailable; it must not trigger direct database fallback.
- Enable bootstrap for exactly one controlled start, verify the first `SUPER_ADMIN`, then remove the Nacos password and disable bootstrap. Do not keep plaintext fallback in local or production configuration.
- Expose Admin HTTP only on the operations network with HTTPS at the ingress. Production Cookie `Secure` is mandatory.
- Rolling back `im-admin` leaves ordinary-user runtime behavior intact. Rolling back Account requires retaining the added status column and ensuring all active rows contain a value understood by the older binary; do not remove the column during an application rollback.
- If Admin writes must be stopped while queries remain available, revoke administrator Sessions or disable the Admin deployment. Do not bypass audit/RBAC or let `im-admin` write Account tables directly as an emergency workaround.

## Completion criteria

- `im-account-admin-facade` is contract-only and used only by Account Server and `im-admin`; architecture tests enforce the boundary.
- Ordinary users can be queried including deleted records, and all approved writes obey status, uniqueness, cache, lock, Session-revocation, and connection-close rules.
- Banned/deleted users cannot sign in, refresh, or pass Access Token authentication even if cleanup notifications fail.
- Administrator identity, password, Session, roles, permissions, and audit data are independent from ordinary Account authentication and storage.
- RBAC protects every endpoint and preserves built-in-role, self-management, role-reference, and last-super-admin invariants.
- Dangerous commands are audit-first; sensitive values never enter API responses, logs, or audit details.
- The Vue UI covers all approved navigation and behavior and is bundled only in `im-admin.jar`.
- SQL, architecture, affected, quick, full, frontend, and diff checks pass with no generated artifacts or secrets tracked.
- Documentation and plan indexes describe the delivered runtime and migration path, and the plan is archived only after verification.
