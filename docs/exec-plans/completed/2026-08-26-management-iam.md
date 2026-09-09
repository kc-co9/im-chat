# Management IAM Implementation Plan

> **For agentic workers:** REQUIRED: Use `superpowers:executing-plans` to implement this plan. The user has explicitly prohibited subagents for this task. Track every checkbox in this file and do not create Git commits without explicit user authorization.

**Goal:** Build a centralized management IAM with OAuth2/OIDC SSO, application-scoped RBAC, standard Token Introspection and bounded stale-cache fallback, then migrate `im-admin` and protect `im-monitor` without retaining the old Admin authentication path.

**Architecture:** `im-management/im-iam/im-iam-server` owns administrator identity, registered applications, permission catalogs, roles, grants, SSO sessions, OAuth2 authorizations and security audits. `im-management/im-iam/im-iam-sdk` provides the Spring Security BFF integration used by Admin and Monitor; each application keeps its own permission definitions and final `@PreAuthorize` checks. Browser credentials remain same-origin HttpOnly cookies, while usable OAuth2 tokens are encrypted inside each application's Redis session.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Authorization Server, Spring Security OAuth2 Client/Resource Server, Spring MVC, MyBatis-Plus/MySQL, Spring Data Redis, AES-GCM, MapStruct, Lombok, Vue 3, TypeScript, Vitest.

---

## 1. Objective and non-goals

### Objective

- Add the `im-iam` aggregate with executable `im-iam-server` and reusable `im-iam-sdk` modules under `im-management`.
- Centralize management administrator identity, SSO, applications, permissions, roles and grants in IAM.
- Use Authorization Code + PKCE and server-side BFF sessions for Admin and Monitor.
- Return the Access Token permission snapshot through standard Token Introspection on every protected request and validate Audience at the resource application.
- Fall back to the last successful Redis result only for IAM connection failures, timeouts and `5xx`, for at most five minutes and never beyond Access Token expiry.
- Move Admin account/role/security-audit pages to IAM while preserving Admin ordinary-user management and business audit.
- Protect all Monitor APIs and UI routes with IAM permissions.
- Remove the old Admin password-login, Session and RBAC implementation after cutover.

### Non-goals

- No reuse of ordinary Account identity or Session.
- No JWT Access Token, MFA, CAPTCHA, external identity provider, organization or data-scope authorization.
- No long-lived compatibility filter, dual-write or fallback to old Admin authentication.
- No shared Admin/Monitor business UI, Controller or application service.
- No automatic Git commit.

## 2. Design references

- [Management IAM design](../../design-docs/2026-08-26-management-iam-design.md)
- [IM Admin user management design](../../design-docs/2026-08-24-im-admin-user-management-design.md)
- [IM Admin audit and security context](../../design-docs/2026-08-26-im-admin-audit-aop-design.md)
- [IM Admin DDD structure alignment](../../design-docs/2026-08-26-im-admin-ddd-structure-alignment-design.md)
- [Security guarantees](../../SECURITY.md)
- [Reliability guarantees](../../RELIABILITY.md)
- [Harness lifecycle](../../references/HARNESS_GUIDE.md)
- [Unit test guide](../../references/UNIT_TEST_GUIDE.md)

## 3. Ownership and dependency boundaries

```text
im-management/im-iam/im-iam-server
    owns administrator/application/authorization/session/security-audit facts

im-management/im-iam/im-iam-sdk
    owns protocol contracts and reusable Spring Security BFF integration
    does not depend on im-iam-server implementation

im-management/im-admin
    owns ordinary-user administration and Admin business audit
    depends on im-iam-sdk and im-account-admin-facade

im-management/im-monitor
    owns read-only runtime diagnostics
    depends on im-iam-sdk
```

Architecture tests must prohibit `im-iam-sdk -> im-iam-server`, `im-iam-server -> im-admin/im-monitor`, and management applications defining a second administrator password-authentication implementation.

## 4. Locked protocol and SDK contracts

OAuth2/OIDC endpoints use Spring Authorization Server standard paths and wire names:

```text
/oauth2/authorize
/oauth2/token
/oauth2/introspect
/oauth2/revoke
/userinfo
/.well-known/openid-configuration
/connect/logout
```

The Token client determines the trusted source `appKey`; the configured target application becomes standard `aud`. Introspection resolves both sides from IAM persistence and returns active only when the caller application's `appKey` matches `aud`.
Standard wire fields remain `client_id`, `redirect_uri` and `access_token`. Custom JSON fields use small camel case.

The SDK boundary starts with immutable transport records equivalent to:

```java
public record IamPrincipal(
        Long administratorId,
        String username,
        String appKey,
        Set<String> authorities
) {
}

public record IamPermissionDefinition(
        String code,
        String name,
        String description
) {
}

public record IamIntrospectionResult(
        Boolean active,
        Long administratorId,
        String username,
        String appKey,
        String clientId,
        Set<String> authorities,
        Instant expiresAt
) {
}
```

Boundary fields use wrapper types where absence must not silently become a primitive default. Constructors validate required values with `AssertUtils`; IAM domain code converts transport primitives into domain values inside the application boundary.

## 5. Ordered implementation tasks

### Task 1: Add module skeletons and managed dependencies

**Files:**

- Modify: `im-management/pom.xml`
- Modify: `pom.xml`
- Create: `im-management/im-iam/pom.xml`
- Create: `im-management/im-iam/im-iam-server/pom.xml`
- Create: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/ImIamApplication.java`
- Create: `im-management/im-iam/im-iam-server/src/main/resources/application.yml`
- Create: `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/ImIamApplicationTest.java`
- Create: `im-management/im-iam/im-iam-sdk/pom.xml`
- Create: `im-management/im-iam/im-iam-sdk/src/main/java/com/co/kc/imchat/management/iam/sdk/ImIamSdkAutoConfiguration.java`
- Create: `im-management/im-iam/im-iam-sdk/src/test/java/com/co/kc/imchat/management/iam/sdk/ImIamSdkAutoConfigurationTest.java`

- [x] Add the `im-iam` aggregate to `im-management/pom.xml`, add Server/SDK children to its POM and manage `im-iam-sdk` from the root dependency management.
- [x] Add Spring Authorization Server to IAM; add OAuth2 Client, Spring Security, Redis and configuration-processor dependencies to the SDK.
- [x] Write context tests first: IAM starts with test-only datasource/Redis replacements; SDK auto-configuration remains disabled unless `im.iam.client.enabled=true`.
- [x] Run `mvn -q -pl im-management/im-iam/im-iam-server,im-management/im-iam/im-iam-sdk -am test` and confirm the skeleton tests pass.

### Task 2: Define IAM schema and persistence boundaries

**Files:**

- Create: `sql/iam-ddl.sql`
- Create: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/mybatis/entity/DbIamAdministrator.java`
- Create: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/mybatis/entity/DbIamApplication.java`
- Create: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/mybatis/entity/DbIamPermission.java`
- Create: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/mybatis/entity/DbIamRole.java`
- Create: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/mybatis/entity/DbIamOAuthAuthorization.java`
- Create: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/mybatis/entity/DbIamSecurityAudit.java`
- Create corresponding Mapper files under `infrastructure/mybatis`; create persistence services, domain Repository implementations and MapStruct transformers with their first caller in Tasks 3–6.
- Test: `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/infrastructure/mybatis/IamSchemaTest.java`
- Test: `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/infrastructure/mybatis/IamPersistenceMappingTest.java`

- [x] Write failing schema tests for lower-snake-case names, explicit primary keys, InnoDB, comments, application-scoped unique keys and required indexes.
- [x] Define IAM tables for administrators, applications, permissions, roles, administrator-role links, role-permission links, OAuth2 authorizations and security audit.
- [x] Store only password/client-secret/Token digests; never add a raw Token column.
- [x] Model application identity as `appId` and OAuth client identity as `clientId`; use snake case only in SQL columns and OAuth wire parameters.
- [x] Add persistence-mapping tests and schema assertions proving application-scoped unique keys; add behavioral isolation queries with the authorization Repository in Task 4 and opaque Token digest lookup in Task 5.
- [x] Run `bash scripts/test-sql-harness.sh` and the focused IAM persistence tests.

Task-order note: persistence Service, domain Repository and Transformer work originally listed here is intentionally deferred to Tasks 3–6 so persistence does not introduce temporary domain types or unused compatibility abstractions. Application-isolation SQL remains in this task; opaque Token digest lookup belongs to Task 5 with the authorization domain.

### Task 3: Move administrator identity and login policy into IAM

**Files:**

- Create domain files under `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/domain/administrator/{model,repository,service}` based on the currently verified Admin administrator model.
- Create: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/application/AuthenticationAppService.java`
- Create: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/application/AdministratorAppService.java`
- Create CQRS Command, Query and DTO files under `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/model/cqrs`.
- Create: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/config/properties/IamLoginProperties.java`
- Create: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/lifecycle/IamBootstrap.java`
- Test domain, application and bootstrap behavior under matching test packages.

- [x] Port the existing password value objects, password codec boundary and account states into the IAM namespace; do not make IAM depend on Admin source.
- [x] Write failing tests for success, uniform invalid-credential errors, five-failure lock, 15-minute unlock, disabled rejection and login-failure reset. Logical-deletion rejection remains owned by the MyBatis logical-delete boundary and is covered with the real repository integration in Task 4.
- [x] Implement username-or-email login without revealing whether an account exists.
- [x] Keep administrator authentication in `AuthenticationAppService`; keep administrator management and role assignment in `AdministratorAppService`.
- [x] Implement controlled first-super-administrator bootstrap; reject or warn when data already exists and never expose a bootstrap HTTP endpoint. Bootstrap requires the `imIam` application and its built-in `SUPER_ADMIN` role to be initialized first, so an administrator is never created without its required grant.
- [x] Keep password policy configuration typed and validate it at property binding/startup, not inside Bean factory methods.
- [x] Run `mvn -q -pl im-management/im-iam/im-iam-server -am -Dtest='*Administrator*,*Login*,*Bootstrap*' -Dsurefire.failIfNoSpecifiedTests=false test`.

Bootstrap ordering note: the controlled deployment initialization must create the `imIam` application and built-in `SUPER_ADMIN` role before enabling administrator bootstrap. The lifecycle fails closed when either prerequisite is absent; Task 4 owns these application-scoped role facts and the last-super-administrator invariant.

### Task 4: Implement applications, permission catalogs and application-scoped roles

**Files:**

- Create domain files under `domain/application` and `domain/authorization`.
- Create: `application/ApplicationAppService.java`
- Create: `application/PermissionAppService.java`
- Create: `application/RoleAppService.java`
- Create: `interfaces/http/catalog/PermissionCatalogController.java`
- Create matching CQRS and HTTP request/response transformer files.
- Test: application, catalog, role and Controller tests in matching packages.

- [x] Write failing tests proving a role can reference permissions from exactly one `appId`.
- [x] Implement application registration with unique `appId`, unique `clientId`, exact redirect URI allowlist and hashed client secret.
- [x] Implement authenticated, unversioned full permission-catalog synchronization. Repeated delivery of the same snapshot keeps stable permission business identities. The HTTP boundary derives `clientId` from Spring Security `Authentication`; the application resolves `appId` from that trusted client identity.
- [x] Keep HTTP as the single permission-catalog synchronization boundary; do not maintain a parallel Dubbo RPC contract for the same use case.
- [x] Mark absent permissions inactive; reject deletion while a role still references them.
- [x] Implement custom application roles and administrator-role grants while retaining at least one active IAM super administrator. Super-role changes use one annotation-driven distributed-lock scene so concurrent removals cannot both pass the invariant.
- [x] Return a stable conflict when a role attempts to combine permissions from different applications.
- [x] Run the focused authorization domain and HTTP tests.

### Task 5: Configure Authorization Server, opaque Tokens and OIDC keys

**Files:**

- Create: `infrastructure/config/beans/IamAuthorizationServerConfig.java`
- Create: `infrastructure/config/properties/IamAuthorizationProperties.java`
- Create: `infrastructure/security/IamRegisteredClientRepository.java`
- Create: `infrastructure/security/IamAuthorizationService.java`
- Create: `infrastructure/security/IamTokenGenerator.java`
- Create: `infrastructure/security/IamJwkSource.java`
- Create: `infrastructure/security/IamUserDetailsService.java`
- Test: `interfaces/security/IamAuthorizationServerTest.java`
- Test: `infrastructure/security/IamTokenPersistenceTest.java`

- [x] Write failing integration tests for Authorization Code + PKCE, exact redirect matching, single-use Code, opaque Access Token, refresh and OIDC discovery/JWK endpoints.
- [x] Back Spring Authorization Server with IAM application and authorization repositories rather than in-memory production stores.
- [x] Issue 15-minute Access Tokens and 8-hour rotating Refresh Tokens; revoke the previous Refresh Token after successful use.
- [x] Authenticate Introspection and revocation callers with their registered `clientId`; derive source `appKey` and target `aud` from persisted client relationships and reject callers outside the audience.
- [x] Persist only token digests and metadata. Compare digests using a constant-time operation where applicable.
- [x] Load the OIDC signing key from an externally mounted keystore configured through typed properties; production startup must fail when key material is missing or invalid.
- [x] Keep local/test key generation inside test fixtures or an ignored local developer workflow, never in production source or committed configuration.
- [x] Run the Authorization Server integration and persistence tests.

Implementation checkpoint: `IamRegisteredClientRepository` now maps active IAM applications to confidential Authorization Code + PKCE clients with exact redirect allowlists, opaque 15-minute Access Tokens and non-reused 8-hour Refresh Tokens. `Sha256TokenDigester` provides deterministic 64-character digests and constant-time comparison. The remaining Task 5 work is the digest-backed `OAuth2AuthorizationService`, external-keystore JWK loading, login/SecurityFilterChain wiring and end-to-end protocol tests.

### Task 6: Implement IAM SSO sessions, logout and security audit

**Files:**

- Create domain files under `domain/session` and `domain/audit`.
- Create: `application/OAuthSessionAppService.java`
- Create: `application/SecurityAuditAppService.java`
- Create: `interfaces/http/session/IamSessionController.java`
- Create: `support/audit/IamSecurityAudited.java`
- Create: `support/audit/IamSecurityAuditAspect.java`
- Create Redis Session repository and MySQL audit repository implementations.
- Test session, revocation, logout and audit behavior in matching packages.

- [x] Write failing tests for 8-hour idle, 24-hour absolute SSO expiry and concurrent multi-device sessions.
- [x] Implement current-application logout, platform logout, all-device logout and specified-session revocation.
- [x] Revoke all authorizations when an administrator is disabled, deleted or changes password.
- [x] Keep role/permission changes online and let subsequent Introspection resolve them without forced logout.
- [x] Record login, failure, lock, refresh, logout and revocation audits without password, Cookie, Code or Token values.
- [x] Add finite-cardinality metrics for authorization, introspection, refresh, revocation and audit failures.
- [x] Run the focused Session and audit tests.

### Task 7: Build the IAM SDK configuration and encrypted application Session

**Files:**

- Create: `im-management/im-iam/im-iam-sdk/src/main/java/com/co/kc/imchat/management/iam/sdk/properties/IamClientProperties.java`
- Create model records under `sdk/model` for `IamPrincipal`, `IamTokenSet`, `IamApplicationSession` and introspection results.
- Create: `sdk/security/IamCsrfTokenRepository.java`
- Create: `sdk/session/IamApplicationSessionRepository.java`
- Create: `sdk/session/RedisIamApplicationSessionRepository.java`
- Create: `sdk/crypto/IamSessionCipher.java`
- Create: `sdk/crypto/AesGcmIamSessionCipher.java`
- Create: `sdk/security/IamSecurityFilter.java`
- Create: `sdk/security/IamSecurityContext.java`
- Test configuration binding, encryption, Redis keys and security context behavior.

- [x] Write failing tests for required `appId`, `clientId`, client secret, redirect URI, Cookie and encryption-key configuration.
- [x] Validate related settings through typed `@ConfigurationProperties`; do not scatter `@Value` fields.
- [x] Encrypt Access and Refresh Token values with AES-GCM before writing the application Session to Redis; use a fresh nonce for every write and reject tampered values.
- [x] Keep only a random Session ID in the browser Cookie and enforce `HttpOnly`, `Secure` in non-local environments and the selected `SameSite` policy.
- [x] Bind a CSRF Token to the application Session and require matching Cookie/header values for every non-GET/HEAD/OPTIONS request.
- [x] Build a Spring Security Authentication containing `IamPrincipal` and current authorities; clear request security context reliably.
- [x] Run `mvn -q -pl im-management/im-iam/im-iam-sdk -am test`.

### Task 8: Implement realtime Introspection and stale-if-error behavior

**Files:**

- Create: `sdk/introspection/IamIntrospectionClient.java`
- Create: `sdk/introspection/HttpIamIntrospectionClient.java`
- Create: `sdk/introspection/IamIntrospectionService.java`
- Create: `sdk/introspection/IamIntrospectionCache.java`
- Create: `sdk/introspection/RedisIamIntrospectionCache.java`
- Create: `sdk/introspection/IamAvailabilityCircuit.java`
- Test: `sdk/introspection/IamIntrospectionServiceTest.java`
- Test: `sdk/introspection/RedisIamIntrospectionCacheTest.java`
- Test: `sdk/introspection/IamAvailabilityCircuitTest.java`

- [x] Write failing tests showing every healthy request calls live Introspection.
- [x] Cache only successful `active=true` responses under `tokenDigest + appKey`; never place a raw Token in a key or log.
- [x] Permit stale fallback only for connection failures, timeouts and explicit `5xx`.
- [x] Reject fallback for `active=false`, `401`, `403`, client/app/audience mismatch, malformed response, cache age over five minutes or expired Access Token.
- [x] Open the circuit after three consecutive availability failures and probe recovery every five seconds; immediately resume live requests after a successful probe.
- [x] Add deterministic time control inside tests without adding production constructors used only by tests.
- [x] Run the focused SDK Introspection tests.

### Task 9: Implement server-side refresh and permission catalog registration in the SDK

**Files:**

- Create: `sdk/oauth/IamAuthorizationClient.java`
- Create: `sdk/oauth/IamAuthorizedSessionService.java`
- Create: `sdk/catalog/IamPermissionDefinition.java`
- Create: `sdk/catalog/IamPermissionCatalog.java`
- Create: `sdk/catalog/IamPermissionCatalogRegistrar.java`
- Create: `sdk/health/IamCatalogHealthIndicator.java`
- Test OAuth callback, refresh rotation, catalog synchronization and readiness behavior.

- [x] Write failing tests for callback state/PKCE verification, one-time Code exchange and server-side Token storage.
- [x] Refresh before Access Token expiry; replace the rotated Refresh Token atomically in the encrypted Session.
- [x] On explicit invalid refresh, delete the application Session and restart authorization; on IAM availability failure, retain a still-valid Access Token but deny once it expires.
- [x] Implement current-application logout by revoking its Token pair and deleting its local Session; implement platform logout through the IAM logout endpoint.
- [x] Register a versioned full permission snapshot with IAM using the application's client credentials.
- [x] Keep the process running but report readiness down until the current catalog version is synchronized.
- [x] Run all SDK tests and verify logs contain no credentials.

### Task 10: Add IAM login and management UI

**Files:**

- Create: `im-management/im-iam/im-iam-server/ui/package.json` and standard Vue/Vite configuration.
- Create UI API/state/router files under `im-management/im-iam/im-iam-server/ui/src`.
- Create views for login, administrators, roles, applications, online sessions and security audit.
- Modify: `im-management/im-iam/im-iam-server/pom.xml` to build the UI into the application artifact.
- Test: Vitest suites for login redirect, role isolation, session revocation and destructive confirmation.

- [x] Build the login page around Authorization Server continuation state; never expose tokens to JavaScript.
- [x] Implement the “权限管理” navigation with management account, role, application, online-session and security-audit entries.
- [x] Restrict role permission selection to the selected application's registered active catalog.
- [x] Add confirmation for disable/delete/password-reset/revoke-all operations.
- [x] Run `npm run test:unit`, `npm run typecheck` and `npm run build` inside `im-management/im-iam/im-iam-server/ui`.

### Task 11: Protect Monitor with IAM

**Files:**

- Modify: `im-management/im-monitor/pom.xml`
- Modify: `im-management/im-monitor/src/main/resources/application.yml`
- Create: `im-management/im-monitor/src/main/java/com/co/kc/imchat/management/monitor/domain/authorization/model/MonitorPermission.java`
- Create: `im-management/im-monitor/src/main/java/com/co/kc/imchat/management/monitor/infrastructure/config/MonitorSecurityConfig.java`
- Modify: `im-management/im-monitor/src/main/java/com/co/kc/imchat/management/monitor/interfaces/http/MonitorController.java`
- Modify Monitor UI router, HTTP handling and navigation files.
- Test: Monitor security, permission catalog and UI redirect tests.

- [x] Write failing tests proving anonymous requests return `401` and missing Monitor permission returns `403`.
- [x] Define bounded Monitor permissions such as overview, Broker, Gateway, connection and diagnostics read; do not reuse Admin permission codes.
- [x] Depend on `im-iam-sdk`, enable method security and annotate each Monitor endpoint with the owning permission.
- [x] Add standard login redirect, current-principal display and logout behavior to the UI.
- [x] Run Monitor Maven and UI test/typecheck/build commands.

### Task 12: Cut Admin over to IAM and retain only Admin business ownership

**Files:**

- Modify: `im-management/im-admin/pom.xml`
- Modify: `im-management/im-admin/src/main/resources/application.yml`
- Modify: `infrastructure/config/beans/AdminHttpConfig.java`
- Modify: `support/audit/AdminAuditAspect.java`
- Modify: `domain/role/model/PermissionCode.java` to retain only Admin-owned business permissions, or replace it with an Admin-specific permission catalog type.
- Delete Admin authentication/administrator/role application, domain, persistence, Controller, DTO and test files after replacement is green.
- Delete: `interfaces/http/security/AdminAuthenticationFilter.java`
- Delete Admin login/account/role UI views and replace navigation with IAM links.
- Keep ordinary-user management, Account adapter and Admin business audit files.

- [x] Write failing security tests using SDK `IamPrincipal`, including Admin business audit actor resolution.
- [x] Register only Admin-owned permissions such as ordinary-user read/update/password-reset/ban/delete and business-audit read.
- [x] Replace the custom Admin authentication filter with SDK security integration while preserving `@PreAuthorize` as the final authorization boundary.
- [x] Update the audit aspect to read the authenticated administrator from Spring Security's `IamPrincipal`; do not copy identity into Commands or TTL context.
- [x] Move management-account, role, session and security-audit navigation to IAM; preserve Admin user-management and business-audit pages.
- [x] Remove old `AdminAuthAppService`, administrator/RBAC repositories, Redis Admin Session and their unused configuration/tests only after all replacements pass.
- [x] Run Admin Maven and UI test/typecheck/build commands.

### Task 13: Remove obsolete Admin identity DDL

**Files:**

- Delete: `sql/admin-ddl.sql`
- Modify: `im-management/im-iam/im-iam-server/README.md` with fresh-install instructions.

- [x] Keep Admin business audit data in the Admin schema; do not move it into IAM security audit.
- [x] Delete the empty Admin DDL after administrator, role, permission and audit table ownership moved to IAM and Audit.
- [x] Use fresh IAM DDL and `IamBootstrap`; do not retain an unreleased historical-data migration path.
- [x] Document fresh installation without embedding real credentials or fixed business data.
- [x] Remove the no-op Admin schema test and run SQL Harness against the remaining owned schemas.

### Task 14: Update architecture, security, reliability and Harness

**Files:**

- Modify: `ARCHITECTURE.md`
- Modify: `docs/SECURITY.md`
- Modify: `docs/RELIABILITY.md`
- Modify: `README.md`
- Modify: `im-management/AGENTS.md`
- Modify: `im-management/README.md`
- Modify: `im-management/im-iam/im-iam-server/README.md`
- Create: `im-management/im-iam/im-iam-sdk/README.md`
- Modify: `im-management/im-admin/README.md`
- Modify: `im-management/im-monitor/README.md`
- Modify: `docs/references/CODING_GUIDE.md`
- Modify: `docs/references/HARNESS_GUIDE.md`
- Modify architecture tests and the narrowest reliable Harness scripts/fixtures.

- [x] Replace current documentation claims that Admin owns administrator identity, Session and RBAC with IAM ownership; preserve historical design documents and link the superseding decision.
- [x] Document Token/session lifetimes, 5-minute stale authorization risk, key management, logout semantics and operational recovery.
- [x] Add architecture rules for IAM/SDK dependency directions and management authentication ownership.
- [x] Add low-false-positive checks preventing management applications from implementing password-authentication/session repositories outside IAM and preventing SDK dependencies on IAM implementation.
- [x] Add secret-leak fixtures for Token values in logs/cache keys where mechanically reliable; mark semantic authorization and encryption decisions Review-only when static detection would be noisy.
- [x] Record each new convention in the Harness matrix with owner, source, fixture and removal condition.
- [x] Run `./scripts/verify.sh architecture`, Harness tests and documentation drift checks.

### Task 15: End-to-end verification and plan completion

**Files:**

- Modify this plan as tasks complete and record material design deviations.
- Move this plan to `docs/exec-plans/completed/2026-08-26-management-iam.md` only after every gate passes.
- Update `docs/exec-plans/active/README.md`, `docs/exec-plans/completed/README.md` and `docs/design-docs/index.md` at completion.

- [x] Verify first login, cross-application SSO, application isolation, Token refresh, current-app logout, platform logout, all-device logout and specified-session revocation.
- [x] Verify healthy IAM causes live Introspection on every protected request.
- [x] Verify only connection/timeout/`5xx` uses cache and the maximum authorization staleness is five minutes.
- [x] Verify IAM recovery immediately resumes live Introspection and revoked/disabled accounts fail without cache fallback.
- [x] Verify no password, client secret, Code, Cookie, Access Token or Refresh Token is emitted to logs, responses, cache keys or business DTOs.
- [x] Run focused backend tests:

  ```bash
  mvn -q -pl im-management/im-iam/im-iam-server,im-management/im-iam/im-iam-sdk,im-management/im-admin,im-management/im-monitor -am test
  ```

- [x] Run each management UI gate:

  ```bash
  cd im-management/im-iam/im-iam-server/ui && npm run test:unit && npm run typecheck && npm run build
  cd im-management/im-admin/ui && npm run test:unit && npm run typecheck && npm run build
  cd im-management/im-monitor/ui && npm run test:unit && npm run typecheck && npm run build
  ```

- [x] Run repository gates:

  ```bash
  ./scripts/verify.sh affected
  ./scripts/verify.sh quick
  ./scripts/verify.sh full
  git diff --check
  ```

- [x] Review the complete diff for unrelated changes and credentials.
- [x] Move the plan to completed only after the full gate passes. Do not commit unless the user explicitly requests it.

### Task 16: Converge IAM security, DDD and Harness boundaries after Review

**Phase A — security correctness**

**Files:**

- Modify IAM management authorities, Controller authorization and focused security tests.
- Modify `application/IamManagementAppService.java` or its split command services and corresponding tests.
- Modify administrator login persistence and concurrency tests.

- [x] Write failing endpoint-list tests proving every IAM management endpoint declares its own minimum permission, a plain authenticated administrator receives `403`, and a suitably authorized administrator is accepted.
- [x] Replace the blanket `IAM_USER` management authorization with IAM-owned permission codes granted through application-scoped roles; retain `IAM_USER` only as an authenticated identity marker when needed by the login flow.
- [x] Write a failing transaction test proving administrator mutation rolls back when authorization revocation fails, then make disable, delete and password reset atomic.
- [x] Write a failing concurrent-login test proving five simultaneous invalid attempts cannot collapse into fewer persisted failures, then serialize or atomically update the account state.
- [x] Keep privileged write audit coverage explicit and verify success/failure records without credentials or Token values.

**Phase B — DDD and persistence convergence**

**Files:**

- Split `application/IamManagementAppService.java` into focused query/command use cases using Repository boundaries.
- Add IAM query models, application Transformers and HTTP response Transformers.
- Add `infrastructure/mybatis/service` classes and migrate IAM Repository implementations away from direct Mapper injection.
- Modify `Administrator`, `RegisteredApplication`, `Role` and persistence Transformers for shared aggregate identity.
- Delete the redundant `infrastructure/config/beans/IamDatasourceConfig.java` if all IAM Mappers remain individually annotated.
- Modify `im-architecture/.../LayerBoundaryTest.java`, `docs/references/HARNESS_GUIDE.md` and the narrowest reliable Harness fixtures.

- [x] Write failing application-layer architecture tests for `management..application -> management..infrastructure` and domain-boundary tests that include IAM.
- [x] Move management list queries behind Repository/query Repository boundaries and replace `IamPageDTO` full-table loading with `Paging` and `PagingResult`.
- [x] Move declarative entity/DTO conversions to MapStruct Transformers.
- [x] Add table-level MyBatis Services and make domain Repository implementations depend on Services rather than Mappers.
- [x] Make declared IAM aggregate roots inherit `Identification`, restore technical primary keys through Transformers, and add identity/equality regression tests.
- [x] Remove duplicate Mapper scanning and update the Harness matrix; correct the declarative-lock-key matrix wording so it matches the Coding Guide and checker.
- [x] Run focused IAM tests, Java-style Harness, Architecture, affected, quick and full verification before marking this task complete.

Checkpoint (2026-08-28): Phase B and the permission/audit parts of Phase A are complete. IAM module tests,
Java-style Harness, Drift, Architecture, affected, quick and full verification pass. Administrator mutations
declare one rollback boundary and authentication uses a transactional `FOR UPDATE` read, with focused contract
tests. The two behavior-level checklist items above remain open until a real transactional database test proves
rollback on revocation failure and a concurrent integration test drives five simultaneous invalid logins.

### Task 17: Normalize OAuth application identity and client registration

**Files:**

- Modify `docs/design-docs/2026-08-26-management-iam-design.md` and this plan.
- Replace `domain/application/model/RegisteredApplication.java` with `Application.java`.
- Replace `domain/application/model/ServiceClient.java` and its ID/name/status types with the unified `OAuthClient` aggregate and OAuth-specific value types.
- Rename application/client repositories, application services, Transformers and persistence Services to the `Application` / `OAuthClient` vocabulary.
- Replace `DbIamApplication` / `DbIamServiceClient` with `DbIamApp` / `DbIamOAuthClient`.
- Modify IAM authorization persistence, registered-client lookup, Token Claims and bootstrap wiring.
- Modify `sql/iam-ddl.sql`, IAM README, Coding Guide/Harness matrix and focused fixtures.
- Update matching domain, application, persistence and Spring Authorization Server tests.

- [x] Write failing domain and schema tests proving `id` is an auto-increment technical key restored only as `Identification.pkId`, `app_id` is a Snowflake business ID, `app_key` is a distinct unique application code, and all IAM relationships use business IDs rather than `pkId`.
- [x] Write failing OAuth client tests proving browser and machine clients share one aggregate while enforcing their distinct Grant Type, redirect URI and Scope invariants.
- [x] Replace `RegisteredApplication` with `Application`; retain `AppId` and `AppKey` with distinct meanings and leave automatic `AppKey` generation out of scope.
- [x] Replace `ServiceClient` with `OAuthClient`; use `OAuthClientId` as its stable business identity and remove the duplicate service-client identity hierarchy.
- [x] Use `ApplicationAppService` to register only `Application`; use `OAuthClientAppService` to register browser or machine clients in a separate command and transaction after the application exists.
- [x] Replace `db_iam_application` / `db_iam_service_client` with `db_iam_app` / `db_iam_oauth_client`; add explicit business ID columns for IAM aggregates and make relation columns reference those business IDs rather than table primary keys.
- [x] Collapse Authorization Server lookup and authorization persistence onto `OAuthClientId`; remove application-vs-service-client branching and expose `appKey`, not a misleading `appId`, in external Claims.
- [x] Update bootstrap configuration, management contracts and README without adding compatibility aliases or migration branches.
- [x] Record the reusable technical-primary-key/business-ID rule and the aggregate/client separation rule in the Coding Guide and Harness matrix; automate only low-noise entity/schema cases.
- [x] Run focused IAM tests, SQL/Java Harness, Architecture, affected, quick and full verification before marking the task complete.

Checkpoint (2026-08-31): Task 17 is complete. IAM now models `Application` and `OAuthClient` as
separate aggregate roots, keeps database row identity in `Identification.pkId`, and uses `AppId`,
`AppKey` and `OAuthClientId` for their distinct business purposes. Browser and machine clients share
the OAuth client aggregate, Authorization Server persistence uses `OAuthClientId`, and external
claims expose `appKey`. Focused IAM tests, SQL/Java Harness, Architecture, affected, quick and full
verification pass. The plan remains active for the earlier integration, migration and rollout tasks.

Checkpoint (2026-09-01): Application registration no longer creates a browser OAuth client in the
same transaction. The management contract and UI first register the application and then register
browser or machine clients through the independent OAuth client use case. Focused tests prove both
client shapes and that application registration saves only the application aggregate.

### Task 18: Remove the premature permission catalog revision

**Files:**

- Modify IAM permission-catalog domain, CQRS, HTTP, SDK registration and persistence models.
- Modify `sql/iam-ddl.sql`, IAM README and IAM design documentation.
- Add focused domain and application regression tests.

- [x] Write a failing structural test proving permission-catalog revision fields are absent from domain, persistence and transport models.
- [x] Delete `CatalogRevision`, `catalogRevision` and `lastSeenRevision` throughout the IAM Server, SDK consumers and SQL schema.
- [x] Keep synchronization as an authenticated full snapshot; repeated snapshots retain existing permission business identities while absent permissions become inactive.
- [x] Document that the first phase does not reject an older rolling-deployment instance synchronizing after a newer instance; introduce a build-derived revision only when that deployment requirement becomes real.
- [x] Run focused IAM, SDK and management-consumer tests, SQL/Java Harness checks, and the affected, quick and full repository gates.

Checkpoint (2026-08-31): Task 18 removes a revision whose producer had no reliable monotonic source.
Permission catalogs are now unversioned full snapshots, and repeated synchronization preserves existing
permission IDs without skipping processing. The IAM design explicitly records the rolling-deployment
ordering limitation. This is a business protocol decision rather than a reusable coding convention, so
no new Harness rule is required.

### Task 19: Split IAM internal roles from application roles

**Files:**

- Modify IAM authorization domain repositories, administrator rules, authentication authority resolution and bootstrap.
- Split IAM role, administrator-role and role-permission MyBatis entities and tables.
- Modify `sql/iam-ddl.sql`, IAM README, IAM design, Coding Guide and Harness matrix.
- Add focused domain, authentication, schema and persistence mapping tests.

- [x] Give external application role persistence explicit `ApplicationRole` table, Entity, Mapper and Service names.
- [x] Add independent `IamRole` and IAM administrator-role repositories for IAM management authorization.
- [x] Move super-administrator protection, administrator role replacement and bootstrap grants to the IAM-internal role chain.
- [x] Remove `SUPER_ADMIN` and super-administrator behavior from the external application `Role` model.
- [x] Prove internal and external role tables are physically isolated and run focused IAM role/authentication tests.
- [x] Resolve unrelated pre-existing IAM suite failures, then run affected, quick and full repository gates.

Checkpoint (2026-09-02): IAM management permissions now resolve from `db_iam_internal_role` and
`db_iam_internal_administrator_role`. External application authorization continues to use
`db_iam_application_role`, `db_iam_application_permission`,
`db_iam_application_role_permission` and `db_iam_application_administrator_role`.
The split is protected by focused schema, persistence mapping, bootstrap and authentication tests.

### Task 20: Make authorization model ownership explicit in type names

**Files:**

- Rename external authorization types under `domain/authorization/model` from generic
  `Permission*` / `Role*` names to `ApplicationPermission*` / `ApplicationRole*`.
- Add IAM-owned `IamRoleCode` and `IamRoleName` value objects instead of sharing application role
  value objects with `IamRole`.
- Update domain services, repositories, application services, Transformers, persistence adapters,
  security integration and focused tests that consume the renamed types.
- Rename CQRS and HTTP boundary models to `ApplicationPermission*` / `ApplicationRole*`, and keep
  nested permission synchronization items in the DTO package rather than presenting them as Commands.
- Modify IAM README and IAM design documentation to state the naming boundary.

- [x] Update existing focused tests to reference the new domain vocabulary and verify RED at test compilation.
- [x] Rename the production domain types without changing their invariants or behavior.
- [x] Update all consumers and remove the ambiguous generic authorization type names.
- [x] Run focused IAM authorization tests and repository Java/Harness verification.
- [x] Keep `domain/session` as the OAuth session boundary and align its identity, CQRS, HTTP and
  Transformer vocabulary on the `OAuthSession*` prefix without renaming persistence protocol fields.

The naming convention is Review-only: whether two similarly named types represent distinct bounded
contexts requires domain knowledge and cannot be inferred reliably by a lexical checker. A mechanical
ban on generic `Permission` or `Role` names would reject valid modules that own only one such concept.

### Task 21: Use the native Introspection Provider chain

**Files:**

- Replace the custom Introspection success handler with administrator and machine-client
  `AuthenticationProvider` implementations.
- Split the shared Introspection application use case and CQRS query by authorization subject.
- Modify OAuth authorization restoration, Access Token Claims, IAM README, security design and Harness matrix.
- Add focused Provider and application-service tests.

- [x] Prove Authorization Code and Client Credentials Access Tokens enter different Providers.
- [x] Select the Provider from the server-side Grant Type rather than an administrator identity Claim.
- [x] Keep the administrator business ID in persisted authorization state and remove it from Access Token Claims.
- [x] Remove the framework default Introspection Provider so unsupported grants cannot bypass IAM application and Audience rules.
- [x] Convert CQRS boundary values to domain identifiers at each split application-service entry.
- [x] Run focused IAM tests and the full repository verification gate.

Checkpoint (2026-09-03): Introspection now uses Spring Authorization Server's native
`AuthenticationProvider` chain. Authorization Code and Client Credentials Access Tokens are handled
by separate Providers and application use cases, the framework default Provider is removed, and
unsupported or unknown authorization state returns inactive. Administrator identity remains a
server-side authorization attribute rather than an Access Token Claim. Focused IAM tests, affected
verification and the full repository gate pass.

### Task 22: Route the OAuth authorization SPI through the application boundary

- [x] Treat Spring Authorization Server's `OAuth2AuthorizationService` as an inbound framework SPI.
- [x] Add `OAuthAuthorizationAppService` for final authorization-state persistence, credential lookup,
  replay revocation and trusted principal resolution.
- [x] Add the `OAuthAuthorization` aggregate and `OAuthAuthorizationRepository`, implemented by
  `MysqlOAuthAuthorizationRepository` through table-level MyBatis Services.
- [x] Keep `OAuthAuthorizationServiceAdapter` limited to Spring type conversion, use-case routing and protocol
  exception mapping; remove its direct domain Repository and MyBatis dependencies.
- [x] Keep the authorization CQRS boundary aggregate-free: the SPI exchanges application DTOs,
  the application service constructs `OAuthAuthorization`, and replay handling uses command semantics because
  it may revoke authorization state.
- [x] Keep aggregate construction in `OAuthAuthorizationAppService`; let
  `OAuthAuthorizationService` validate the OAuth principal across client and administrator aggregates
  while Spring Authorization Server owns protocol exchange and refresh processing.
- [x] Flatten Spring authorization write state into `OAuthAuthorizationSaveCmd`; do not nest the
  read-side `OAuthAuthorizationDTO` or accept a caller-supplied application ID, because the
  application service derives application ownership from the validated OAuth Client.
- [x] Separate OAuth authorization input mappings into `OAuthAuthorizationDomainTransformer` and
  keep `OAuthAuthorizationAppTransformer` limited to application DTO output mappings.
- [x] Preserve focused digest-only persistence, Refresh Token history and replay-revocation tests.
- [x] Route Spring's final authorization-state callback through one `save` application use case;
  remove duplicate exchange/refresh commands, methods and dispatch metadata.
- [x] Keep `findByToken` read-only; move Authorization Code and rotated Refresh Token replay
  revocation into a Token Endpoint authentication Provider and explicit Command use case.
- [x] Run repository quick and full verification gates.

Checkpoint (2026-09-05): Spring Authorization Server's authorization-save SPI callback now passes
the completed protocol state through one `OAuthAuthorizationSaveCmd`. The application service keeps
explicit boundary-to-domain conversion, creates or updates the loaded aggregate, and saves it once.
Authorization lookup now uses pure `OAuthAuthorizationIdQuery` and `OAuthTokenQuery` inputs.
`OAuthCredentialReuseProvider` runs
before the Spring Authorization Code and Refresh Token Providers, invokes distinct code-reuse or
refresh-token-reuse revocation Commands only for consumed credentials, and otherwise lets the
default Provider continue.
The Repository reads the previous Refresh Token digest before updating and records it in history.
Focused IAM tests and the repository quick and full verification gates pass.

### Task 23: Make OAuth Authorization a real aggregate root

- [x] Add focused tests for aggregate construction, validation and lifecycle state.
- [x] Replace public setters with a private constructor and hand-written Builder.
- [x] Group authorization request and token state into immutable domain value objects.
- [x] Keep explicit revocation and replay predicates in the aggregate while accepting Spring's final
  credential state through the unified save boundary.
- [x] Update Spring OAuth conversion and persistence mapping without changing protocol behavior.
- [x] Update the aggregate guidance and run focused, quick and full verification gates.

Checkpoint (2026-09-03): `OAuthAuthorization` now uses a private constructor and hand-written Builder,
validates its complete state, exposes no public aggregate setters, and owns revocation and
authorization-code consumption behavior. Authorization request state and OAuth credentials are
grouped into immutable value objects. Spring owns Authorization Code exchange and Refresh Token
validation; the aggregate retains revocation and replay predicates, while the Repository persists
the final state and Refresh Token history. Focused IAM tests and repository quick and full
verification gates pass.

### Task 24: Align OAuth authorization naming and persistence mapping

- [x] Rename the authorization aggregate, identity, status, request, principal and credential-validity
  types to explicit OAuth authorization terms.
- [x] Name the OAuth credential issuance/expiry interval `OAuthCredentialPeriod` and use `period`
  consistently at credential boundaries.
- [x] Keep `OAuthSession` as a read-only management projection over `OAuthAuthorizationId`; remove the
  duplicate `OAuthSessionId` and the authorization-ID-derived SSO digest.
- [x] Let authorization-code expiry represent a session before Access/Refresh Tokens are issued.
- [x] Represent authorization subjects as a typed principal (`principalType` + stable `principal` value),
  removing the administrator-specific authorization field and claim.
- [x] Map OAuth Client status to its numeric `TINYINT` value and map JSON columns through MyBatis
  `JacksonTypeHandler` into structured Entity values.
- [x] Exclude administrator password, OAuth Client Secret and Token digests from Entity `toString()`.
- [x] Run focused IAM tests and repository quick/full verification gates.

### Task 25: Separate OAuth Client Secret validation from administrator passwords

- [x] Add a shared UTF-8 SHA-256 primitive and replace repeated IAM hashing implementations.
- [x] Keep OAuth Token digest formatting in the IAM security adapter.
- [x] Configure Spring Authorization Server Client Secret validation independently from the
  administrator password domain service.
- [x] Prove the persisted OAuth Client Secret encoding is compatible with Spring client
  authentication.
- [x] Run focused IAM tests and repository quick/full verification gates.

Checkpoint (2026-09-06): UTF-8 SHA-256 hashing now uses `HashUtils` across IAM Token persistence,
Introspection cache keys and PKCE challenge generation. OAuth Token adapters retain protocol-specific
hex/Base64 encoding. Spring Authorization Server now validates OAuth Client Secrets with its own
BCrypt `PasswordEncoder`; administrator authentication continues through `PasswordService`, and the
obsolete administrator-to-Spring encoder adapter has been removed. Compatibility, focused IAM tests
and repository quick/full verification gates pass.

### Task 26: Simplify OAuth Token Introspection Provider structure

- [x] Replace the abstract Introspection template and its two ordered subclasses with one concrete
  `OAuthTokenIntrospectionProvider`.
- [x] Dispatch administrator and machine Access Token Introspection by authorization Grant Type
  inside the single protocol adapter.
- [x] Remove the `handlesFallback` ordering contract and return the standard inactive response for
  unknown, unsupported or rejected Token state.
- [x] Keep `OAuthCredentialReuseProvider` independent because it belongs to the Token Endpoint and
  supports different Spring Authentication types.
- [x] Run focused IAM tests and repository quick/full verification gates.

This intermediate structure was superseded before release by Task 27. The single custom Provider
proved simpler than the inheritance hierarchy but still duplicated Spring's standard Introspection
processing.

### Task 27: Restore Spring default OAuth credential providers

- [x] Restore Spring Authorization Server's default Token Introspection Provider.
- [x] Capture administrator authorities in Access Token Claims at issuance and refresh time.
- [x] Map standard `sub`, `client_id` and `aud` fields in the IAM SDK and reject Audience mismatch at
  the resource boundary.
- [x] Remove custom Introspection application/domain models and Provider implementations.
- [x] Remove custom Authorization Code/Refresh Token reuse Provider, Commands and aggregate
  predicates; rely on Spring's Authorization Code single-use and Refresh Token rotation behavior.
- [x] Remove the pre-release Refresh Token history table and persistence components.
- [x] Remove the pass-through Token generator wrapper and compose Spring's
  `DelegatingOAuth2TokenGenerator` directly.
- [x] Run focused IAM tests and repository quick/full verification gates.

Checkpoint (2026-09-06): IAM now uses Spring Authorization Server's default Token Introspection,
Authorization Code and Refresh Token Providers. Administrator permissions are captured in opaque
Access Token Claims during issuance and refresh. Management SDK and Audit machine-resource adapters
validate the standard `aud` claim at the resource boundary. The custom Introspection and credential
reuse Providers, their application/domain models, and the pre-release Refresh Token history table
have been removed. Token generation now directly uses Spring's `DelegatingOAuth2TokenGenerator`.
Reused old Refresh Tokens still receive `invalid_grant`, but no longer revoke the current
authorization family. Focused IAM/Audit tests and repository quick/full gates pass.

### Task 28: Isolate IAM browser and OAuth Client API security chains

- [x] Keep OAuth2/OIDC protocol endpoints in `oauthSecurityFilterChain`.
- [x] Add `iamClientApiSecurityFilterChain` for permission-catalog synchronization with stateless
  Opaque Bearer Token authentication.
- [x] Remove Opaque Resource Server authentication from `iamWebSecurityFilterChain`, leaving form
  login and IAM SSO Session only.
- [x] Run focused IAM tests and repository quick/full verification gates.

### Task 29: Move Token Claims resolution behind the application boundary

- [x] Add separate `OAuthClientAppService.getApplicationTokenClaims` and
  `getAdministratorTokenClaims` CQRS boundaries so Application Token input/output does not carry
  nullable administrator fields.
- [x] Resolve OAuth Client, source application, Audience application, administrator and permission
  snapshot in the application use case.
- [x] Keep `OAuthTokenClaimsCustomizer` limited to Spring context conversion and Claims writing.
- [x] Run focused IAM tests and repository quick/full verification gates.

Checkpoint (2026-09-06): IAM now separates OAuth protocol, OAuth Client API and administrator Web
traffic into `oauthSecurityFilterChain`, `iamClientApiSecurityFilterChain` and
`iamWebSecurityFilterChain`. Permission-catalog synchronization is stateless Bearer traffic, while
administrator management uses form login and IAM SSO only. Token Claims resolution now enters
`OAuthClientAppService` through separate Application and Administrator Token Claims queries; the
Spring customizer no longer accesses repositories or domain services directly. Focused IAM tests
and the repository full verification gate pass.

### Task 30: Move Registered Client lookup behind the application boundary

- [x] Add `OAuthClientAppService.queryRegistration` with a framework-neutral CQRS DTO.
- [x] Move OAuth Client, owner Application and Audience Application availability checks out of the
  Spring `RegisteredClientRepository` adapter.
- [x] Keep Spring `RegisteredClient`, Grant Type, PKCE and TokenSettings mapping in the dedicated
  `OAuthRegisteredClientTransformer`.
- [x] Redact the encoded Client Secret from the registration DTO string representation.
- [x] Run focused IAM tests and repository quick/full verification gates.

Checkpoint (2026-09-06): `OAuthRegisteredClientRepository` now delegates OAuth Client and
Application availability lookup to `OAuthClientAppService.queryRegistration`. The application DTO
is framework-neutral and redacts the encoded Client Secret; `OAuthRegisteredClientTransformer`
owns Spring `RegisteredClient`, Grant Type, PKCE and TokenSettings construction, leaving the
Repository as a thin SPI adapter. Focused IAM tests and the repository full verification gate pass.

### Task 31: Align IAM security package ownership

- [x] Move `OAuthJwkSource` and `OAuthTokenClaimsCustomizer` from the OAuth protocol package to
  `infrastructure.security.token` alongside Token digest capabilities.
- [x] Keep `AdministratorAuthenticationProvider` as a direct Spring `AuthenticationProvider`
  adapter because the complete administrator authentication use case remains in the application
  service rather than Spring `UserDetailsService` hooks.
- [x] Run focused IAM tests and repository quick/full verification gates.

### Task 32: Share Client Credentials resource identity through IAM SDK

- [x] Add generic `IamApplicationPrincipal` and `IamOpaqueTokenAuthenticationConverter` SDK
  capabilities with strict Audience validation.
- [x] Replace Audit-local machine Principal and Opaque Token Converter with IAM SDK types.
- [x] Rename `AuditSecurityConfig` to `AuditSecurityBeans` and move the permission-catalog Bean to
  `AuditServiceBeans`.
- [x] Rename Admin's generic `SecurityConfig` to `AdminSecurityBeans`.
- [x] Run focused IAM SDK, Audit and Admin tests and repository quick/full verification gates.

### Completion checkpoint (2026-09-07)

The remaining protocol and consistency acceptance work is complete. A deterministic Spring
Authorization Server integration test now covers the first form login, Authorization Code + PKCE,
exact redirect matching, cross-application SSO, application redirect isolation, opaque Access Token
issuance, Authorization Code single use, Refresh Token rotation, OIDC Discovery and JWK endpoints.
Focused SDK and Server tests cover current-application logout, platform logout, all-session and
specified-session revocation. Administrator disable, delete and password reset are verified through
an annotation-driven transaction proxy to roll back when authorization revocation fails. Five
simultaneous invalid login attempts are preserved through the single atomic Redis Lua operation.

The IAM/SDK/Admin/Monitor module suite, all management UI gates, `affected`, `quick`, `full` and
`git diff --check` pass. The implementation plan can therefore move to `completed`; no Git commit is
created without explicit user authorization.

## 6. Rollout, compatibility and rollback

1. Deploy IAM and register Admin/Monitor clients without switching application traffic.
2. Synchronize both permission catalogs and configure application-scoped roles.
3. Back up the current Admin identity/RBAC tables and execute the idempotent migration.
4. Validate counts, password-digest continuity, role grants and at least one active super administrator.
5. Enable Monitor IAM integration and verify SSO/read permissions.
6. Switch Admin to IAM and make IAM the only writer of administrator and role facts.
7. Remove old Admin authentication/RBAC runtime code and fresh-install DDL after cutover verification.

There is no runtime downgrade to old Admin authentication. Rollback restores the previous application deployment and Admin identity/RBAC database backup as one controlled operation. Do not allow both systems to modify administrator or role data concurrently.

## 7. Completion criteria

- IAM and SDK modules compile and all focused tests pass.
- Admin and Monitor use standard redirect login and SDK-managed BFF sessions.
- IAM is the sole owner of administrator identity, application-scoped roles and security sessions.
- Admin no longer contains password login, administrator account, role persistence or Redis Admin Session code.
- Every protected request uses live Introspection when IAM is healthy; bounded fallback matches the approved matrix.
- Cross-application permissions cannot be reused.
- Migration and rollback are documented and tested without real credentials.
- Architecture, Harness, SQL, frontend and full repository verification pass.
- Documentation reflects the target runtime ownership and the plan is moved to completed.
