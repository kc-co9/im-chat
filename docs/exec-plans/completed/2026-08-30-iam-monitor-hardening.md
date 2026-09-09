# IAM And Monitor Hardening Implementation Plan

> **For agentic workers:** REQUIRED: Use `superpowers:executing-plans` to implement this plan. The user has explicitly prohibited subagents for this task. Track every checkbox and do not create Git commits without explicit user authorization.

**Goal:** Make IAM revocation and administrator invariants correct under real runtime conditions, converge IAM on repository/time/aggregate conventions, and give Monitor bounded execution plus isolated CQRS and HTTP boundaries.

**Architecture:** Preserve the accepted IAM and Broker management protocols. Apply behavior fixes before structural refactors, keep OAuth2 framework persistence in infrastructure, keep Monitor as one flat domain, and remove old development-stage models instead of adding compatibility paths.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Authorization Server, Spring Security, MyBatis-Plus, MapStruct, Lombok, Vue 3, TypeScript, Vitest.

---

## 1. Objective and non-goals

### Objective

- Make revoked IAM authorizations immediately inactive for local and remote token validation.
- Serialize every operation that can remove the last active IAM super administrator.
- Remove direct Mapper orchestration from the OAuth2 authorization service.
- Rebuild IAM aggregates through validated handwritten builders without persistence IDs in constructors.
- Use `Instant` internally and epoch milliseconds at browser HTTP boundaries.
- Require an explicit, range-valid IAM Snowflake node configuration outside local development.
- Bound Monitor worker threads and queued work, with deterministic overload behavior.
- Separate Monitor Broker protocol records, domain facts, CQRS DTOs, and HTTP responses.
- Format Monitor timestamps in the browser's IANA time zone.

### Non-goals

- No OAuth2/OIDC protocol redesign or lifetime changes.
- No common Snowflake lease service and no changes to Account, Social, or Message ID configuration.
- No Broker management endpoint or Gossip behavior changes.
- No Monitor write APIs, metrics storage, or alert engine.
- No compatibility constructors, duplicate DTOs kept only for migration, Git commit, or subagent work.

## 2. Design references

- [IAM and Monitor hardening design](../../design-docs/2026-08-30-iam-monitor-hardening-design.md)
- [Management IAM design](../../design-docs/2026-08-26-management-iam-design.md)
- [Broker business monitoring design](../../design-docs/2026-08-13-broker-business-monitoring-design.md)
- [Security guarantees](../../SECURITY.md)
- [Reliability guarantees](../../RELIABILITY.md)
- [Coding guide](../../references/CODING_GUIDE.md)
- [Harness guide](../../references/HARNESS_GUIDE.md)
- [Unit test guide](../../references/UNIT_TEST_GUIDE.md)

## 3. Affected ownership boundaries

```text
im-iam-server
  owns administrator invariants, OAuth2 authorization state, aggregate reconstruction,
  IAM browser API and IAM Snowflake configuration

im-monitor
  owns Broker discovery/adaptation, cluster aggregation, Monitor CQRS/HTTP responses,
  bounded query execution and Monitor UI time rendering

im-common / im-plugin
  remain unchanged unless verification exposes a true shared defect
```

## 4. Ordered TDD tasks

### Task 1: Make revoked authorizations inactive

**Files:**

- Create: `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/infrastructure/security/IamAuthorizationServiceTest.java`
- Create or modify: `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/infrastructure/security/IamLocalOpaqueTokenIntrospectorTest.java`
- Modify: `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/interfaces/security/IamAuthorizationServerTest.java`
- Modify: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/security/IamAuthorizationService.java`

- [x] Add a focused test that persists an unexpired revoked authorization, resolves its raw Access Token, and expects no active authorization.
- [x] Add a local introspector test that expects `BadOpaqueTokenException` for the same revoked Access Token.
- [x] Add an authorization-server test that expects standard Introspection to return `active=false` after revocation.
- [x] Run the focused tests and confirm RED because status is currently ignored during token reconstruction.
- [x] Reject `REVOKED` in `findByToken`; when `findById` must reconstruct framework state, mark every present Token invalidated through `OAuth2Authorization.Token.INVALIDATED_METADATA_NAME`.
- [x] Run the three focused tests and the complete IAM security test package; confirm GREEN.

Command:

```bash
mvn -q -pl im-management/im-iam/im-iam-server -am \
  -Dtest='IamAuthorizationServiceTest,IamLocalOpaqueTokenIntrospectorTest,IamAuthorizationServerTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

### Task 2: Serialize the super-administrator invariant

**Files:**

- Modify: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/support/lock/IamLockScene.java`
- Modify: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/application/AdministratorManagementAppService.java`
- Modify: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/application/AdministratorRoleAppService.java`
- Modify: `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/application/AdministratorManagementAppServiceTest.java`
- Modify: `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/application/AdministratorRoleAppServiceTest.java`
- Add a Spring/AOP concurrency test beside the application tests if direct unit tests cannot exercise the lock.

- [x] Add a RED structure test proving disable, delete, and super-role removal do not yet share one lock scene/key.
- [x] Verify the existing lock aspect behavior test together with the application mapping test; avoid a scheduler-sensitive duplicate concurrency fixture.
- [x] Introduce one `SUPER_ADMIN_WRITE` scene and global key; apply the same `@DistributeLock` to every decrement operation.
- [x] Keep the count/read/write inside the locked transaction and do not lock enable or unrelated administrator mutations.
- [x] Run application, lock aspect, and IAM authorization tests; confirm GREEN.

Command:

```bash
mvn -q -pl im-management/im-iam/im-iam-server -am \
  -Dtest='AdministratorManagementAppServiceTest,AdministratorRoleAppServiceTest,*SuperAdministrator*' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

### Task 3: Put OAuth2 persistence behind the MyBatis Service

**Files:**

- Modify: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/mybatis/service/DbIamAuthorizationService.java`
- Modify: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/security/IamAuthorizationService.java`
- Modify: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/config/beans/IamAuthorizationServerConfig.java`
- Modify: `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/infrastructure/security/IamAuthorizationServiceTest.java`
- Modify Harness fixtures only if the existing Repository-only Mapper rule can be safely expanded to infrastructure persistence adapters.

- [x] Add a RED construction/architecture test that requires `IamAuthorizationService` to accept `DbIamAuthorizationService`, not `DbIamAuthorizationMapper`.
- [x] Add explicit persistence-service methods for first-by-authorization-ID, first-by-token-digest, insert, update, and revoke; keep Wrapper construction inside that service.
- [x] Replace every Mapper call in `IamAuthorizationService` and its Bean declaration.
- [x] Preserve token digest, single-use Code, and revoked-state behavior from Task 1.
- [x] Run IAM persistence/security tests and architecture checks; confirm GREEN.

### Task 4: Replace IAM aggregate Lombok builders

**Files:**

- Modify aggregate and tests for:
  - `domain/administrator/model/Administrator.java`
  - `domain/client/model/RegisteredApplication.java`
  - `domain/client/model/ServiceClient.java`
  - `domain/authorization/model/Role.java`
- Modify their domain Transformers under `transformer/domain`.
- Modify Repository reconstruction tests under `infrastructure/domain/repository`.

- [x] For each aggregate, add RED tests proving `build()` rejects missing required state and Repository reconstruction restores `pkId` after build.
- [x] Remove Lombok `@Builder` and long constructors.
- [x] Add a private no-arg constructor and handwritten `Builder` that stores one aggregate instance, writes fields directly, and invokes aggregate validation in `build()`.
- [x] Remove `pkId` from every Builder API; let Repository/Transformer call `setPkId` only after a valid aggregate has been built.
- [x] Update callers without compatibility constructors or wrapper factories.
- [x] Run all four domain and Repository test groups after each aggregate, then run the complete IAM module.

### Task 5: Normalize IAM time and HTTP models

**Files:**

- Modify IAM MyBatis Entities containing absolute `LocalDateTime`, especially `DbIamAuthorization`, `DbIamAdministrator`, `DbIamAdministratorRole`, and `DbIamRolePermission`.
- Modify IAM domain/application Transformers that currently convert through `ZoneOffset.UTC`.
- Modify `model/cqrs/dto/IamSessionDTO.java` to use `Instant`.
- Modify `model/io/IamSessionResponse.java` to use `Long` epoch milliseconds.
- Add missing `*Response` models for application, service-client, and role create operations.
- Modify `interfaces/http/management/IamManagementController.java`.
- Modify `transformer/interfaces/IamManagementHttpTransformer.java`.
- Modify related MyBatis, Transformer, and Controller tests.

- [x] Add RED mapping tests for Entity `Instant`, application DTO `Instant`, and HTTP epoch milliseconds.
- [x] Add RED Controller tests proving create endpoints return interface Response types instead of application DTOs.
- [x] Convert absolute Entity fields to `Instant` and remove fixed-offset helper conversions.
- [x] Keep `LocalDateTime` only if a reviewed field has true local-calendar semantics.
- [x] Add MapStruct mappings for Request-to-Command and DTO-to-Response; write Controller methods as input conversion, use-case call, output conversion.
- [x] Run IAM Transformer, persistence, Controller, schema, and full module tests.

### Task 6: Configure the IAM Snowflake node

**Files:**

- Create: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/config/properties/IamSnowflakeProperties.java`
- Modify: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/config/beans/IamServiceBeans.java`
- Modify: `im-management/im-iam/im-iam-server/src/main/resources/application.yml`
- Modify: `im-management/im-iam/im-iam-server/README.md`
- Create: `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/infrastructure/config/properties/IamSnowflakePropertiesTest.java`
- Modify: `IamServiceBeansTest.java` and application context tests.

- [x] Add RED property tests for null, negative, and greater-than-31 data-center/machine IDs.
- [x] Add RED context tests proving non-local startup fails without explicit values and valid values construct `SnowflakeId`.
- [x] Bind `im.iam.snowflake.data-center-id` and `machine-id`; validate using `AssertUtils` and Snowflake bit limits.
- [x] Permit documented local defaults only under the local profile; do not use production fallback values.
- [x] Document deployment-owned uniqueness and the absence of an automatic lease protocol.
- [x] Run property, Bean, context, and complete IAM tests.

### Task 7: Bound Monitor query execution

**Files:**

- Create: `im-management/im-monitor/src/main/java/com/co/kc/imchat/management/monitor/config/properties/MonitorQueryProperties.java`
- Modify: `im-management/im-monitor/src/main/java/com/co/kc/imchat/management/monitor/config/MonitorExecutorBeans.java`
- Create: `im-management/im-monitor/src/main/java/com/co/kc/imchat/management/monitor/application/MonitorOverloadedException.java` or place the boundary exception under existing module `support` if reused outside the application service.
- Modify: `MonitorQueryService.java` before its Task 8 rename.
- Create: `MonitorExecutorBeansTest.java` and modify `MonitorQueryServiceTest.java`.
- Modify: `im-management/im-monitor/src/main/resources/application.yml` and README configuration documentation.

- [x] Add RED tests that saturate all workers and the queue, then assert the next query fails immediately with a stable overload exception.
- [x] Add RED tests proving one rejected/failed node does not erase already completed healthy-node results when the aggregate can still complete.
- [x] Replace `Executors.newFixedThreadPool` with a configured `ThreadPoolExecutor`, bounded `ArrayBlockingQueue`, named thread factory, and `AbortPolicy`.
- [x] Validate positive thread and queue sizes through typed properties.
- [x] Preserve per-node timeout and partial-success semantics; map complete scheduling failure through unified Web exception handling.
- [x] Run Monitor executor, application, Controller, and full module tests.

### Task 8: Establish Monitor DDD, CQRS, and protocol boundaries

**Files:**

- Rename `application/MonitorQueryService.java` to `application/MonitorQueryAppService.java`.
- Move/replace application client and discovery interfaces with domain repositories or Adapter-facing abstractions according to the approved flat domain.
- Create Query records under `model/cqrs/query` for overview, broker, node lists, connections, gossip, and migrations.
- Create application DTO records under `model/cqrs/dto`.
- Create HTTP Response records under `model/io`.
- Move Broker wire records under `infrastructure/client/model`.
- Create `adapter/BrokerMonitorAdapter.java` if one adapter can own discovery plus Broker protocol conversion without becoming a second application service.
- Create MapStruct Transformers under `transformer/application`, `transformer/domain`, and `transformer/interfaces` only where a real boundary exists.
- Modify `MonitorController.java`, `HttpBrokerManagementClient.java`, Nacos discovery, Bean configuration, and all Monitor Java tests.

- [x] Add RED architecture/reflection tests proving Controller methods return `*Response`, the application entry is `MonitorQueryAppService`, and public use cases accept one Query object.
- [x] Add RED Transformer tests proving Broker wire records do not escape infrastructure and HTTP responses do not reuse domain/application models.
- [x] Move models in small vertical slices: overview, broker/gateway, connections, then diagnostics; run focused tests after each slice.
- [x] Keep one flat Monitor domain and avoid one-class secondary packages.
- [x] Delete old `monitor.model` records once the last caller migrates; do not retain deprecated aliases.
- [x] Update architecture/Harness checks only with narrow, passing fixtures after the baseline is clean.
- [x] Run all Monitor Java tests and `mvn -q -pl im-architecture test`.

### Task 9: Use epoch milliseconds and browser IANA formatting

**Files:**

- Modify Monitor HTTP Response/Transformer files created in Task 8.
- Modify `im-management/im-monitor/ui/src/api/monitor.ts`.
- Create: `im-management/im-monitor/ui/src/utils/time.ts`.
- Modify Monitor views that display timestamps, including `DiagnosticsView.vue`, `BrokersView.vue`, `GatewaysView.vue`, `ConnectionsView.vue`, and `OverviewView.vue` where applicable.
- Modify/add Vitest coverage under `im-management/im-monitor/ui/tests`.

- [x] Add RED backend tests expecting `Long` epoch milliseconds in every browser-facing absolute-time field.
- [x] Add RED frontend tests for a fixed timestamp formatted as `yyyy-MM-dd HH:mm:ss` in an explicit IANA time zone.
- [x] Change TypeScript absolute-time fields from `string` to `number`.
- [x] Add one formatter based on `Intl.DateTimeFormat`; views never render raw timestamps.
- [x] Run backend Controller/Transformer tests, `npm run test:unit`, `npm run typecheck`, and `npm run build`.

### Task 10: Close documentation and verification

**Files:**

- Update IAM and Monitor READMEs for configuration and model boundaries.
- Update `ARCHITECTURE.md`, `docs/SECURITY.md`, and `docs/RELIABILITY.md` only where current runtime facts changed.
- Update `docs/references/HARNESS_GUIDE.md` and the narrowest checker/tests for conventions proven reliable during implementation.
- Update this plan's checkboxes and move it to `docs/exec-plans/completed` only after every gate passes.

- [x] Run `git diff --check` and inspect only task-owned diffs in the mixed worktree.
- [x] Run `bash scripts/test-harness.sh`, `./scripts/check-drift.sh`, and `mvn -q -pl im-architecture test`.
- [x] Run focused IAM and Monitor module suites.
- [x] Run Monitor frontend unit tests, typecheck, and build.
- [x] Run `./scripts/verify.sh full`.
- [x] If the pre-existing `im-excel` JVM Abort 134 recurs, preserve the log, report it separately, and do not represent the full gate as passing.

Verification note (2026-08-31): focused IAM/Monitor suites, Monitor frontend gates,
Architecture, Harness, Drift, Java style, `git diff --check`, `verify quick` and
`verify full` pass. The previously observed intermittent `im-plugin/im-excel`
`ExcelTemplateTest` forked JVM Abort 134 did not recur in the final full run.

### Task 11: Move temporary login protection out of the administrator domain

**Files:**

- Create: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/support/security/LoginProtection.java`
- Create: `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/support/security/LoginProtectionTest.java`
- Modify IAM authentication, audit listener, Bean configuration and their tests.
- Modify `Administrator`, administrator status enums, MyBatis Entity/Transformer, bootstrap and related tests.
- Modify `sql/iam-ddl.sql` and `sql/iam-migration.sql`.
- Delete `IamLoginPolicy`, `LoginFailureLimit` and `LoginFailureCount` after callers are removed.

- [x] Add RED tests proving failure counts expire, the configured threshold creates a temporary restriction, success clears state, and Redis failures fail closed.
- [x] Implement atomic Redis counting and restriction TTL in `LoginProtection` without an unused interface or compatibility implementation.
- [x] Update authentication to check the administrator's long-lived status, then use `LoginProtection`; remove row locking and aggregate saves from authentication.
- [x] Update SECURITY audit detection to read technical restriction state without mapping it back to `AdministratorStatus`.
- [x] Remove `LOCKED`, failure count and lock deadline from the aggregate, persistence model, schema, migration and public status enum.
- [x] Clear technical login state after administrator enable, disable, delete and password reset operations.
- [x] Record the domain-versus-technical-state Review rule in Coding/Harness guidance and run focused IAM, Architecture, Harness, Drift and repository verification.

## 5. Verification commands

```bash
mvn -q -pl im-management/im-iam/im-iam-server -am test
mvn -q -pl im-management/im-monitor -am test
npm --prefix im-management/im-monitor/ui run test:unit
npm --prefix im-management/im-monitor/ui run typecheck
npm --prefix im-management/im-monitor/ui run build
bash scripts/test-harness.sh
./scripts/check-drift.sh
mvn -q -pl im-architecture test
./scripts/verify.sh full
git diff --check
```

## 6. Rollout and rollback

- Deploy Task 1 and Task 2 behavior fixes before structural changes.
- Assign unique IAM Snowflake node configuration before deploying Task 6 outside local development.
- Deploy Monitor backend and UI from Tasks 8-9 together because timestamp wire types change.
- Roll back by complete phase; do not add dual DTO or dual timestamp support.

## 7. Completion criteria

- All Task 1-10 checkboxes are complete.
- Revoked tokens fail both local and remote validation.
- Concurrent decrement operations preserve one active IAM super administrator.
- IAM authorization persistence no longer imports its Mapper directly.
- IAM aggregates, absolute time, browser responses, and Snowflake configuration match the accepted design.
- Monitor execution is bounded and its protocol/application/HTTP models are isolated.
- Monitor UI renders epoch milliseconds through an IANA-aware formatter.
- Focused, architecture, Harness, drift, frontend, and full gates pass, or any unrelated external blocker is reported with reproducible evidence.
- No Git commit is created without explicit user authorization.
