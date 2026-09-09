# Central Management Audit Implementation Plan

> **For agentic workers:** REQUIRED: Use `superpowers:executing-plans` to implement this plan. The user has explicitly prohibited subagents and worktrees for this task. Track every checkbox in this file and do not create Git commits without explicit user authorization.

**Goal:** Replace the Admin and IAM local audit implementations with one independently deployed Audit application and one reusable SDK, using Kafka or asynchronous IAM-authenticated HTTP ingestion, append-only persistence, centralized query/detail views, and bounded Excel export.

**Architecture:** `im-management/im-audit/im-audit-sdk` owns the immutable cross-process event contract, safe context collection, declarative auditing, transaction timing, and transport selection. `im-management/im-audit/im-audit-server` owns ingestion, validation, idempotent append, query, export, and its standalone IAM-protected UI. `im-plugin/im-mq-kafka` supplies the Spring Cloud Stream Kafka Binder runtime without defining a business MQ SPI. IAM gains a separate machine-client aggregate for OAuth2 Client Credentials; browser applications remain Authorization Code + PKCE clients.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Cloud Stream Kafka Binder, Spring Authorization Server, Spring Security OAuth2 Client/Resource Server, MyBatis-Plus/MySQL, MapStruct, Lombok, Micrometer, Apache Fesod Sheet, Vue 3, TypeScript, Vitest.

---

## 1. Objective and non-goals

### Objective

- Add `im-audit-server` and `im-audit-sdk` under a new `im-management/im-audit` aggregate.
- Replace the unused generic `im-mq` SPI with the concrete `im-mq-kafka` infrastructure plugin.
- Accept BUSINESS and SECURITY audit events through source-isolated Kafka Topics or asynchronous HTTP.
- Authenticate HTTP producers with dedicated IAM machine clients and the `audit:ingest` Scope.
- Persist completed audit facts idempotently by `auditId`; provide append and read operations only.
- Protect the standalone Audit UI and APIs through IAM using `appId=imAudit`, `audit:read`, and `audit:export`.
- Export at most 50,000 records over at most 31 days as a streaming `.xlsx` response.
- Migrate Admin and IAM producers, then remove both local audit stores, APIs, and pages directly.

### Non-goals

- No producer-local Outbox, transport fallback, generic MQ provider SPI, DLQ management UI, replay API, stored export file, or asynchronous export job.
- No compatibility API, dual write, historical audit-table migration, or preservation of development audit records.
- No audit update, logical-delete command, or physical-delete endpoint. Standard `BaseEntity` fields remain present but unused by audit behavior.
- No automatic Git commit.

## 2. Design references

- [Central management audit design](../../design-docs/2026-08-28-central-management-audit-design.md)
- [Management IAM design](../../design-docs/2026-08-26-management-iam-design.md)
- [Admin audit AOP design](../../design-docs/2026-08-26-im-admin-audit-aop-design.md)
- [Architecture](../../../ARCHITECTURE.md)
- [Security guarantees](../../SECURITY.md)
- [Reliability guarantees](../../RELIABILITY.md)
- [Coding guide](../../references/CODING_GUIDE.md)
- [Harness lifecycle](../../references/HARNESS_GUIDE.md)
- [Unit test guide](../../references/UNIT_TEST_GUIDE.md)

## 3. Ownership and dependency boundaries

```text
im-management/im-audit/im-audit-sdk
    public audit contract, context collection, AOP and transport selection
    may use framework APIs but never depends on im-audit-server

im-plugin/im-mq-kafka
    Spring Cloud Stream Kafka Binder/runtime configuration only
    never depends on audit or another business module

im-management/im-audit/im-audit-server
    audit domain, ingestion, persistence, query, export and UI
    depends on im-audit-sdk for the wire contract and im-iam-sdk for UI/API security

im-management/im-admin and im-management/im-iam/im-iam-server
    own the meaning and timing of source audit events
    depend on im-audit-sdk, never on im-audit-server
```

Kafka producers use one source Topic and credential each:

```text
im.audit.im-admin.v1
im.audit.im-iam.v1
im.audit.im-monitor.v1
im.audit.im-audit.v1
```

HTTP producers use one IAM machine `clientId` each. Audit derives `sourceApp` from the authenticated machine client; Kafka ingestion derives it from the Topic-specific binding. The transport payload does not contain `sourceApp`, so producer-controlled data cannot override the authenticated transport identity.

## 4. Locked public contracts and runtime rules

```java
public interface AuditClient {
    void submit(AuditEvent event);
}

public interface AuditContextCollector {
    AuditContext collect();
}
```

- `AuditEvent` is immutable and contains `auditId`, type, source, action, actor, target, outcome, optional stable error code, safe description, bounded client/trace/attribute context, and occurrence time.
- `AuditClient.submit` means accepted for asynchronous delivery; it does not promise database persistence.
- `@Audited` never carries permission metadata and never serializes arguments, return values, request bodies, exceptions, credentials, Tokens, Cookies, SQL, or stacks.
- Successful transactional operations submit after commit; rolled-back operations do not emit success. Failure events preserve and rethrow the original business exception.
- Audit collection and transport failures are logged and metered but never replace the business result or exception.
- Kafka is at least once and server insertion is idempotent by `auditId`. HTTP retries timeouts and `5xx` only; `4xx` is final. There is no automatic cross-transport fallback.
- Ingestion endpoints themselves are not audited, preventing recursive event creation.

## 5. Ordered implementation tasks

### Task 1: Lock module topology and remove the generic MQ abstraction

**Files:**

- Modify: `pom.xml`
- Modify: `im-plugin/pom.xml`
- Rename: `im-plugin/im-mq` to `im-plugin/im-mq-kafka`
- Replace: `im-plugin/im-mq-kafka/pom.xml`
- Replace: `im-plugin/im-mq-kafka/README.md`
- Delete production types under: `im-plugin/im-mq-kafka/src/main/java/com/co/kc/imchat/plugin/mq`
- Replace tests under: `im-plugin/im-mq-kafka/src/test`
- Modify: `im-architecture/src/test/java/com/co/kc/imchat/architecture/RuntimeDependencyPolicyTest.java`
- Modify: `im-plugin/README.md`

- [x] Write a failing architecture/dependency test proving the reactor manages `im-mq-kafka`, the module supplies the Kafka Binder runtime, and it exposes no `MessagePublisher`, `MessageSubscriber`, `MqMessage`, or in-memory production bus.
- [x] Run `./scripts/verify.sh architecture` and confirm RED because the renamed module/capability does not exist.
- [x] Rename the artifact to `im-mq-kafka`, add `spring-cloud-stream-binder-kafka`, and keep configuration limited to generic Kafka/Binder infrastructure.
- [x] Remove the old SPI, in-memory bus, and old auto-configuration rather than retaining compatibility adapters or adding a no-op replacement; the Binder owns its own Spring auto-configuration.
- [x] Run `mvn -q -pl im-plugin/im-mq-kafka -am test` and confirm GREEN.

### Task 2: Add Audit aggregate modules and executable architecture sensors

**Files:**

- Modify: `pom.xml`
- Modify: `im-management/pom.xml`
- Create: `im-management/im-audit/pom.xml`
- Create: `im-management/im-audit/im-audit-sdk/pom.xml`
- Create: `im-management/im-audit/im-audit-server/pom.xml`
- Create: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/ImAuditApplication.java`
- Create: `im-management/im-audit/im-audit-server/src/main/resources/application.yml`
- Create: `im-management/im-audit/im-audit-server/src/test/java/com/co/kc/imchat/management/audit/ImAuditApplicationTest.java`
- Modify: `im-architecture/src/test/java/com/co/kc/imchat/architecture/RuntimeDependencyPolicyTest.java`
- Modify: `im-architecture/src/test/java/com/co/kc/imchat/architecture/ModuleBoundaryTest.java`
- Modify: `im-architecture/src/test/java/com/co/kc/imchat/architecture/LayerBoundaryTest.java`
- Modify: `im-architecture/pom.xml`
- Modify: `scripts/affected-modules.sh`

- [x] Add failing architecture tests for the Audit aggregate, SDK/server direction, Kafka plugin ownership, and Admin/IAM prohibition from depending on Audit Server.
- [x] Run `./scripts/verify.sh architecture` and confirm RED on the missing topology.
- [x] Register `im-audit`, its two children, managed dependencies, application entry point, port `18093`, and affected-module routing.
- [x] Configure the server as an IAM-protected Servlet application with datasource, MyBatis, web, metrics, IAM SDK, and Audit SDK dependencies; do not add producer transport behavior yet.
- [x] Run `mvn -q -pl im-management/im-audit/im-audit-server,im-management/im-audit/im-audit-sdk -am test` and `./scripts/verify.sh architecture`.

### Task 3: Define and validate the Audit SDK contract

**Files:**

- Create model types under: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/model`
- Create: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/client/AuditClient.java`
- Create: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/context/AuditContext.java`
- Create: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/context/AuditContextCollector.java`
- Create: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/properties/AuditProperties.java`
- Create: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/support/AuditEventFactory.java`
- Create tests under matching SDK packages.

- [x] Write failing tests for required fields, wrapper boundary types, immutable collections, maximum lengths/counts/encoded size, clock injection, and one-time `auditId` generation.
- [x] Write failing redaction tests for password, secret, Cookie, Session, CSRF, Access/Refresh Token, authorization code, signing key, digest, SQL, request body, and stack-like attribute keys.
- [x] Run `mvn -q -pl im-management/im-audit/im-audit-sdk -am -Dtest='*AuditEvent*,*AuditProperties*,*AuditEventFactory*' -Dsurefire.failIfNoSpecifiedTests=false test` and confirm RED.
- [x] Implement immutable records/enums for the transport boundary, typed configuration, contract validation, bounded attributes, and safe event construction.
- [x] Keep producer action values as stable strings; do not import Admin/IAM domain enums into the SDK.
- [x] Rerun the focused SDK tests and confirm GREEN.

### Task 4: Implement safe context collection and declarative auditing

**Files:**

- Create: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/annotation/Audited.java`
- Create: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/support/AuditedAspect.java`
- Create context collectors under: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/context`
- Create: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/ImAuditSdkAutoConfiguration.java`
- Create AutoConfiguration imports and tests under matching SDK packages.

- [x] Write failing tests for principal/request/trace capture on the caller thread, context cleanup, target-expression resolution, success/failure outcomes, original-exception preservation, and audit-failure isolation with an error log and finite-cardinality metric.
- [x] Write failing transaction tests for after-commit success, rollback suppression, immediate non-transactional success, and failure emission without swallowing the business exception.
- [x] Run the focused `*AuditedAspectTest,*AuditContextCollectorTest` tests and confirm RED.
- [x] Implement `@Audited`, context capture, event construction, transaction synchronization, and non-recursive infrastructure exclusions.
- [x] Register beans through auto-configuration; fail startup on invalid enabled configuration and use `@ConditionalOnMissingBean` only for replaceable plugin abstractions.
- [x] Rerun focused SDK tests and confirm GREEN.

### Task 5: Implement Kafka and asynchronous HTTP transports

**Files:**

- Create transport types under: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/transport`
- Create IAM token support under: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/security`
- Modify: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/ImAuditSdkAutoConfiguration.java`
- Create transport tests under matching SDK packages.

- [x] Write failing selection tests for `kafka` and `http`, missing Kafka Binder startup failure, invalid transport failure, and the absence of silent fallback.
- [x] Write failing Kafka tests for source Topic selection, `auditId` message key, serialization failure isolation, and send-failure metrics/logs.
- [x] Write failing HTTP tests for bounded queue rejection, IAM Client Credentials token acquisition/caching/early renewal, request timeout, retry of timeout/`5xx`, no retry of `4xx`, and stable `auditId` across retries.
- [x] Run focused `*TransportTest,*TokenProviderTest` tests and confirm RED.
- [x] Implement `AuditTransport`, `KafkaAuditTransport` with `StreamBridge`, and `HttpAuditTransport` with a bounded executor and explicit retry classification.
- [x] Ensure HTTP-only deployments do not require a Kafka Binder/client dependency; ensure producer threads never read request/security ThreadLocals.
- [x] Rerun focused SDK tests and both transport auto-configuration contexts.

### Task 6: Separate IAM browser applications from machine clients

**Files:**

- Create machine-client domain types under: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/domain/client/model`
- Create: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/domain/client/repository/ServiceClientRepository.java`
- Create: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/application/ServiceClientAppService.java`
- Create Service Client CQRS/HTTP types under matching IAM `model` and `interfaces/http/management` packages.
- Create: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/mybatis/entity/DbIamServiceClient.java`
- Create Service Client enum/mapper/service/repository/transformer types under matching IAM infrastructure packages.
- Modify: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/security/IamRegisteredClientRepository.java`
- Modify: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/security/IamAuthorizationService.java`
- Modify: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/security/IamTokenClaimsCustomizer.java`
- Modify: `sql/iam-ddl.sql`
- Modify IAM configuration Bean registries and tests.

- [x] Write failing domain/repository tests for separate machine identity, active status, exact Scope set, unique `clientId`, secret hashing, registration, secret rotation, and disabling.
- [x] Write failing OAuth tests proving browser clients receive only Authorization Code + Refresh Token with PKCE, while machine clients receive only Client Credentials, no redirects, no consent, no Refresh/ID Token, and a 15-minute opaque Access Token.
- [x] Write failing authorization-persistence tests for Client Credentials rows with no administrator or browser authorization request and for collision-safe Spring IDs such as `application:<id>` and `service:<id>`.
- [x] Run focused `*ServiceClient*,*RegisteredClientRepository*,*AuthorizationServer*` tests and confirm RED.
- [x] Add `db_iam_service_client`, its standard `BaseEntity` fields, database status enum, indexes, MyBatis Service, Repository, and MapStruct conversion.
- [x] Add IAM management registration/rotation/disable operations protected by `iam:client:write`; accept a deployment-owned raw secret, persist only its hash, and never return it after registration.
- [x] Update Spring Authorization Server resolution, authorization persistence, Introspection claims, and `appId` derivation for distinct client kinds.
- [x] Register `imAdmin`, `imIam`, `imMonitor`, and `imAudit` audit-producer clients with only `audit:ingest`; operational secrets live in Nacos/runtime configuration, not repository YAML.
- [x] Run `bash scripts/test-sql-harness.sh` and focused IAM tests.

### Task 7: Build the append-only Audit domain and persistence model

**Files:**

- Create aggregate/value objects under: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/domain/audit/model`
- Create: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/domain/audit/repository/AuditEventRepository.java`
- Create: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/infrastructure/mybatis/entity/DbAuditEvent.java`
- Create database enums under: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/infrastructure/mybatis/enums`
- Create Mapper/Service/Repository under matching Audit infrastructure packages.
- Create: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/transformer/domain/AuditDomainTransformer.java`
- Create: `sql/audit-ddl.sql`
- Create domain, mapping, repository, and schema tests under matching packages.

- [x] Write failing tests for aggregate identity, value-object invariants, immutable state, completed outcome, and absence of update/delete transitions.
- [x] Write failing schema tests for `BaseEntity`, auto-increment database ID, unique `audit_id`, database enums, InnoDB/comments, and source/actor/target/type/action/outcome/trace/time indexes.
- [x] Write failing repository tests for idempotent append, duplicate `auditId`, detail lookup, bounded page filters, and append-only contract.
- [x] Run focused Audit domain/persistence tests and `bash scripts/test-sql-harness.sh`; confirm RED before implementation.
- [x] Implement the aggregate, typed query condition, Entity/enums, Mapper, MyBatis Service, Repository, and MapStruct mappings. The Repository uses the Service rather than a Mapper directly.
- [x] Keep `create_time`, `update_time`, and `is_deleted`; never expose business update/remove methods.
- [x] Rerun focused tests and SQL Harness.

### Task 8: Implement authenticated HTTP and source-isolated Kafka ingestion

**Files:**

- Create: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/application/AuditIngestionAppService.java`
- Create ingestion CQRS types under: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/model/cqrs`
- Create: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/interfaces/http/AuditIngestionController.java`
- Create: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/interfaces/mq/AuditEventConsumer.java`
- Create application/interface transformers under matching Audit packages.
- Create security and Kafka binding configuration under Audit infrastructure config.
- Create focused ingestion tests.

- [x] Write failing application tests for transport-to-domain conversion, repeated `auditId` success, contract validation, and malformed/sensitive event rejection.
- [x] Write failing HTTP tests for missing/invalid token, missing `audit:ingest`, source derived from the authenticated machine client, body-source mismatch, and idempotent retry.
- [x] Write failing Kafka tests for Topic-derived source, body-source mismatch, acknowledge-after-insert, duplicate acknowledgement, bounded retry, and DLQ routing after persistence failure.
- [x] Run focused `*Ingestion*,*AuditEventConsumer*` tests and confirm RED.
- [x] Implement the application orchestration and thin adapters. Do not annotate ingestion itself with `@Audited`.
- [x] Configure source-specific bindings and Topic ACL documentation; keep Kafka retry/DLQ operational rather than exposing business APIs.
- [x] Rerun focused tests and verify that HTTP and Kafka produce the same persisted domain fact.

### Task 9: Add Audit query/detail APIs and IAM catalog integration

**Files:**

- Create: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/application/AuditQueryAppService.java`
- Create query/DTO/response/enum types under Audit `model/cqrs` and `model/io` packages.
- Create: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/interfaces/http/AuditQueryController.java`
- Create: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/transformer/application/AuditAppTransformer.java`
- Create: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/transformer/interfaces/AuditHttpTransformer.java`
- Add Audit IAM application/catalog configuration and tests.

- [x] Write failing application tests for BUSINESS/SECURITY lists, detail by business `auditId`, all approved filters, wrapper response fields, Paging/PagingResult, stable ordering, and required bounded time ranges.
- [x] Write failing HTTP authorization tests for anonymous, authenticated-without-scope, and `audit:read` access.
- [x] Write failing Transformer tests proving HTTP enums are distinct from domain enums and all cross-layer enum conversion is generated by MapStruct.
- [x] Run focused query/controller/transformer tests and confirm RED.
- [x] Implement CQRS objects with primitive/wrapper wire values, construct domain values in the application boundary, and keep Controller/RPC lines separated into input conversion, invocation, and output conversion.
- [x] Register `imAudit` and synchronize `audit:read`/`audit:export` into IAM without granting them implicitly to unrelated application roles.
- [x] Rerun focused tests.

### Task 10: Add bounded Fesod Excel export

**Files:**

- Modify: `pom.xml`
- Modify: `im-management/im-audit/im-audit-server/pom.xml`
- Create: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/application/AuditExportAppService.java`
- Create export query/row types under Audit `model` packages.
- Create: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/interfaces/http/AuditExportController.java`
- Create export support and tests under matching Audit packages.

- [x] Manage and add `org.apache.fesod:fesod-sheet` only to Audit Server.
- [x] Write failing validation tests for mandatory range, maximum 31 days, maximum 50,000 rows, and invalid range ordering.
- [x] Write failing export tests for paged database reads, streaming workbook output, headers, omitted internal fields, text-safe formula-like values, no retained file, and empty results.
- [x] Write failing authorization/audit tests proving `audit:export` is required and a successful/failed export submits one `AUDIT_EXPORT` event through Audit SDK with filter summary and row count, without file contents.
- [x] Run focused `*AuditExport*` tests and confirm RED.
- [x] Implement synchronous `.xlsx` streaming, batch reads, row cap enforcement, safe cell values, and response headers.
- [x] Rerun focused tests and inspect a generated test workbook through Fesod parsing assertions rather than manual file inspection.

### Task 11: Build the standalone Audit UI

**Files:**

- Create frontend files under: `im-management/im-audit/im-audit-server/ui`
- Create static-resource build integration analogous to Admin/IAM in `im-audit-server/pom.xml`.
- Create frontend tests under the Audit UI test convention.

- [x] Write failing frontend tests for IAM login redirect/session restoration, permission-gated navigation, business/security tabs, filters, pagination, detail view, and export permission.
- [x] Implement a quiet management UI that uses the same `HttpResult` envelope and IAM BFF behavior as Admin/Monitor.
- [x] Keep edit/delete/DLQ/replay controls absent; show explicit loading, empty, error, forbidden, and export-limit states.
- [x] Run `npm run typecheck` and `npm run test:unit` from `im-management/im-audit/im-audit-server/ui`.
- [x] Run the Audit Server resource-packaging test to prove the UI is included in the application artifact.

### Task 12: Migrate Admin audit production and remove local ownership

**Files:**

- Modify: `im-management/im-admin/pom.xml`
- Replace usages under: `im-management/im-admin/src/main/java/com/co/kc/imchat/management/admin/support/audit`
- Modify annotated Admin application services, especially `ManagedUserAppService` and administrator-management services.
- Delete Admin local audit application/domain/persistence/HTTP/transformer files under their current audit-specific packages.
- Remove Admin audit Bean registrations from its infrastructure configuration.
- Delete: `im-management/im-admin/src/main/java/com/co/kc/imchat/management/admin/interfaces/http/AdminAuditController.java`
- Delete Admin local audit CQRS/IO/enums and matching tests.
- Delete Admin audit UI API/router/navigation/view entries.
- Modify: `sql/admin-ddl.sql`
- Add/modify Admin producer tests.

- [x] Write failing producer tests for each privileged Admin write action, safe target resolution, success/failure, after-commit timing, and no sensitive password/profile payload.
- [x] Add `im-audit-sdk` and the selected deployment transport dependency/configuration; replace local annotations/aspect with SDK behavior.
- [x] Remove local `AdminAudit`, Repository, MyBatis Entity/enums/Mapper/Service, query API, page, permissions, and `db_admin_audit_log` DDL.
- [x] Keep Admin business action codes stable but map them into SDK strings; do not move Admin domain enums into the SDK.
- [x] Run Admin focused tests, UI tests, and `bash scripts/test-sql-harness.sh`.

### Task 13: Migrate IAM security audit production and remove local ownership

**Files:**

- Modify: `im-management/im-iam/im-iam-server/pom.xml`
- Replace IAM audit listeners/aspects under: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/support/audit`
- Delete IAM local security-audit application/domain/persistence/HTTP/transformer files.
- Remove IAM audit Bean registrations and management permission/page entries.
- Delete IAM local audit CQRS/response types and matching tests.
- Delete IAM audit UI API/router/navigation/view entries.
- Modify: `sql/iam-ddl.sql`
- Add/modify IAM producer tests.

- [x] Write failing explicit-event tests for login success/failure, Token issuance/revocation, protocol failures, machine-client changes, privileged writes, safe context, and source `imIam`.
- [x] Replace the local audit persistence path with `AuditClient.submit`; use explicit submission for authentication/Token/protocol events and `@Audited` only where deterministic.
- [x] Remove `SecurityAudit`, local Repository/MyBatis storage, management endpoint/page, local audit permission, and `db_iam_security_audit` DDL.
- [x] Prove IAM can audit its own HTTP producer activity without reusing its browser client and without recursive ingestion events.
- [x] Run focused IAM security/audit tests, IAM UI tests, and `bash scripts/test-sql-harness.sh`.

### Task 14: Close Architecture, Harness, documentation, and verification

**Files:**

- Modify: `im-architecture/src/test/java/com/co/kc/imchat/architecture/RuntimeDependencyPolicyTest.java`
- Modify: `im-architecture/src/test/java/com/co/kc/imchat/architecture/ModuleBoundaryTest.java`
- Modify: `im-architecture/src/test/java/com/co/kc/imchat/architecture/LayerBoundaryTest.java`
- Modify: `docs/references/HARNESS_GUIDE.md`
- Modify the narrowest relevant Harness script/test only if the new rule is reliably mechanical.
- Modify: `ARCHITECTURE.md`
- Modify: `docs/SECURITY.md`
- Modify: `docs/RELIABILITY.md`
- Modify: `README.md`
- Modify: `im-management/README.md`
- Modify: `im-management/im-admin/README.md`
- Modify IAM and new Audit README files.
- Modify: `docs/design-docs/index.md`
- Modify: `docs/exec-plans/active/README.md`

- [x] Add package/dependency architecture tests proving only Audit Server owns management audit Repository/MyBatis packages and that producer modules depend only on SDK.
- [x] Add a topology test for `im-mq-kafka` and a narrow source scan proving Admin/IAM do not redeclare local audit persistence. Do not use one-off forbidden class-name lists.
- [x] Keep transport semantics, append-only behavior, redaction, and transaction timing in focused behavior/contract tests; document them as Review-only where static detection would be noisy.
- [x] Update runtime, security, reliability, configuration, Topic/ACL, IAM machine-client, export, and operator documentation to describe the implemented state.
- [x] Update this plan after every implementation task; record deviations before changing the approved design.
- [x] Run all verification commands in Section 6. Only after every gate passes, move this file unchanged to `docs/exec-plans/completed` and update both plan indexes.

## 6. Test and verification strategy

Use focused tests during each task, then run these gates from the repository root:

```bash
bash scripts/test-sql-harness.sh
bash scripts/test-harness.sh
./scripts/check-drift.sh
./scripts/verify.sh architecture
./scripts/verify.sh affected
./scripts/verify.sh quick
./scripts/verify.sh full
git diff --check
```

Run module suites explicitly before the full gate:

```bash
mvn -q -pl im-plugin/im-mq-kafka -am test
mvn -q -pl im-management/im-audit/im-audit-sdk -am test
mvn -q -pl im-management/im-audit/im-audit-server -am test
mvn -q -pl im-management/im-iam/im-iam-server -am test
mvn -q -pl im-management/im-admin -am test
```

Run each affected management frontend suite from its `ui` directory:

```bash
npm run typecheck
npm run test:unit
```

The full gate is not replaced by focused Maven or frontend runs. If a failure comes from unrelated pre-existing worktree changes, record the exact command and evidence; do not weaken the gate or revert user work.

## 7. Rollout, compatibility, and rollback

### Direct cutover order

1. Provision Kafka Topics/ACLs and IAM machine clients/scopes.
2. Deploy IAM machine-client support.
3. Deploy Audit Server and verify HTTP/Kafka ingestion, idempotency, query, and export.
4. Deploy Admin and IAM producers with exactly one selected transport each.
5. Remove local Admin/IAM audit tables and pages from development DDL/code in the same implementation line.

There is no compatibility or historical-data migration. Rollback means redeploying the immediately preceding development build; it does not merge central events back into deleted local tables. Kafka events already accepted remain safe to redeliver because insertion is idempotent by `auditId`.

Configuration secrets and Kafka credentials are supplied by Nacos/deployment configuration. Repository YAML contains only non-secret local structure and must not contain production-like client secrets.

## 8. Completion criteria

- Audit SDK validates and safely delivers immutable events over Kafka or asynchronous HTTP without affecting business results.
- IAM browser and machine clients are separate, and HTTP ingestion accepts only `audit:ingest` Client Credentials tokens.
- Kafka source isolation, body-source validation, retries, DLQ routing, and database idempotency are covered by deterministic tests.
- Audit domain/persistence follows `Identification`, value-object, `BaseEntity`, database-enum, MyBatis Service, Repository, Transformer, and paging conventions.
- Query, detail, standalone IAM-protected UI, and bounded Fesod export work with distinct HTTP/domain enums.
- Admin and IAM no longer own local audit Repository/MyBatis/API/UI code or tables.
- Architecture and Harness contain the narrow reusable rules established by implementation.
- Focused suites, frontend suites, SQL/Harness gates, Architecture, affected, quick, and full verification all pass.
- Documentation describes the actual implemented runtime and the active plan is moved to completed only after verification.
