# Audit RPC Ingestion Implementation Plan

> **Final deviation (2026-09-03):** the completed RPC implementation was removed after
> architecture review found no independent caller or behavior beyond authenticated HTTP.
> Audit now supports Kafka and asynchronous HTTP only. This file remains as execution
> history and does not describe the current runtime contract.

> **For agentic workers:** REQUIRED: Use `superpowers:executing-plans` to implement this plan. The user has explicitly prohibited subagents for this task. Track every checkbox in this file and do not create Git commits without explicit user authorization.

**Goal:** Add IAM-authenticated synchronous Dubbo delivery as the third independently selected Audit SDK transport without placing Token or `sourceApp` in the RPC business contract.

**Architecture:** `im-dubbo` supplies generic Consumer/Provider Token filters and Spring Security context lifecycle. `im-audit-sdk` owns the RPC contract, transport selection and IAM Client Credentials adapter; `im-audit-server` authenticates the Token, derives `appId`, and delegates the event to the existing ingestion application service. RPC never falls back to Kafka or HTTP.

**Tech Stack:** Java 21, Spring Boot 3.5, Apache Dubbo 3.2, Spring Security, OAuth2 Opaque Token Introspection, MapStruct, Maven, JUnit 5, Mockito, AssertJ.

---

## 1. Objective and non-goals

### Objective

- Add `RPC` to `AuditTransportType` as a synchronous transport selected by configuration.
- Authenticate every Audit RPC invocation with an IAM Client Credentials Access Token.
- Carry the Token only in a reserved Dubbo Attachment.
- Establish `AuditClientPrincipal` in `SecurityContextHolder` for exactly one Provider invocation.
- Derive `sourceApp` from the authenticated principal before constructing `AuditIngestCmd`.
- Reuse the existing idempotent Audit ingestion service and stable RPC exception boundary.

### Non-goals

- No HTTP/Kafka/RPC fallback or dual delivery.
- No `sourceApp` or Access Token field in `AuditEvent`, RPC Params, or application Command beyond the trusted flattened `AuditIngestCmd.sourceApp`.
- No trust based only on Dubbo application name, Nacos registration, or network location.
- No RPC retry scheduler, Outbox, DLQ, or compatibility contract.
- No Git commit unless the user explicitly requests it.

## 2. Design and engineering references

- [Audit RPC ingestion design](../../design-docs/2026-08-30-audit-rpc-ingestion-design.md)
- [Central management audit design](../../design-docs/2026-08-28-central-management-audit-design.md)
- [Security guarantees](../../SECURITY.md)
- [Coding guide](../../references/CODING_GUIDE.md)
- [Harness lifecycle](../../references/HARNESS_GUIDE.md)
- [Unit test guide](../../references/UNIT_TEST_GUIDE.md)

## 3. Ownership and dependency boundaries

```text
im-plugin/im-dubbo
    generic Token Attachment, authentication SPI and SecurityContext lifecycle
    must not depend on IAM or Audit

im-management/im-audit/im-audit-sdk
    AuditRpcService contract, RpcAuditTransport, RPC selection and IAM Token acquisition
    may depend optionally on im-dubbo; must not depend on im-audit-server

im-management/im-audit/im-audit-server
    Audit-specific Token authentication, Scope validation and RPC adapter
```

The implementation must keep `im-dubbo -> im-management` forbidden and must not
move Audit authentication rules into the generic plugin.

## 4. Locked contracts

```java
public interface RpcAccessTokenProvider {
    String accessToken();
}

public interface RpcTokenAuthenticator {
    Authentication authenticate(String accessToken);
}

public interface AuditRpcService {
    void submit(AuditEvent event);
}
```

The reserved Attachment key is owned by `im-dubbo` and is not configurable per
business module. Filters must never log its value.

## 5. Ordered implementation tasks

### Task 1: Add generic Dubbo Token security filters

**Files:**

- Modify: `im-plugin/im-dubbo/pom.xml`
- Create: `im-plugin/im-dubbo/src/main/java/com/co/kc/imchat/plugin/dubbo/security/RpcAccessTokenProvider.java`
- Create: `im-plugin/im-dubbo/src/main/java/com/co/kc/imchat/plugin/dubbo/security/RpcTokenAuthenticator.java`
- Create: `im-plugin/im-dubbo/src/main/java/com/co/kc/imchat/plugin/dubbo/security/RpcSecurityAttachments.java`
- Create: `im-plugin/im-dubbo/src/main/java/com/co/kc/imchat/plugin/dubbo/filter/RpcTokenConsumerFilter.java`
- Create: `im-plugin/im-dubbo/src/main/java/com/co/kc/imchat/plugin/dubbo/filter/RpcTokenProviderFilter.java`
- Create: `im-plugin/im-dubbo/src/main/resources/META-INF/dubbo/org.apache.dubbo.rpc.Filter`
- Test: `im-plugin/im-dubbo/src/test/java/com/co/kc/imchat/plugin/dubbo/filter/RpcTokenConsumerFilterTest.java`
- Test: `im-plugin/im-dubbo/src/test/java/com/co/kc/imchat/plugin/dubbo/filter/RpcTokenProviderFilterTest.java`

- [x] Write a failing Consumer Filter test proving a nonblank Token is written to `RpcContext.getClientAttachment()` under the reserved key before invocation and is absent from invocation arguments.
- [x] Run `mvn -q -pl im-plugin/im-dubbo -am -Dtest=RpcTokenConsumerFilterTest -Dsurefire.failIfNoSpecifiedTests=false test`; expect test compilation to fail because the filter and SPI do not exist.
- [x] Add `spring-security-core`, the two small SPIs, and a package-private attachment key utility. Validate provider results with `AssertUtils`; never include a Token in an exception message.
- [x] Implement `RpcTokenConsumerFilter` as a Dubbo `Filter`: obtain the Token from `RpcAccessTokenProvider`, reject blank output, set only the reserved Attachment, invoke the next `Invoker`, and remove/restore the caller's previous attachment value in `finally`.
- [x] Write Provider Filter RED tests for missing Token, successful authentication, previous SecurityContext restoration, failure cleanup, and no Token in thrown messages or captured logs.
- [x] Implement `RpcTokenProviderFilter`: read `RpcContext.getServerAttachment()`, authenticate through `RpcTokenAuthenticator`, save the previous `SecurityContext`, set a new context, invoke, and restore or clear in `finally`.
- [x] Register names `imRpcTokenConsumer` and `imRpcTokenProvider` in the Dubbo SPI resource. Use Dubbo/Spring extension injection; a missing required SPI at a filter-enabled service must fail closed.
- [x] Run `mvn -q -pl im-plugin/im-dubbo -am test`; expect all plugin tests to pass.

Provider lifecycle shape:

```java
SecurityContext previous = SecurityContextHolder.getContext();
try {
    Authentication authentication = authenticator.authenticate(accessToken);
    SecurityContext current = SecurityContextHolder.createEmptyContext();
    current.setAuthentication(authentication);
    SecurityContextHolder.setContext(current);
    return invoker.invoke(invocation);
} finally {
    SecurityContextHolder.setContext(previous);
}
```

### Task 2: Separate IAM identity properties from HTTP transport properties

**Files:**

- Create: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/properties/AuditIamProperties.java`
- Modify: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/properties/AuditHttpProperties.java`
- Modify: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/properties/AuditProperties.java`
- Modify: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/security/IamClientCredentialsTokenProvider.java`
- Create: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/security/IamRestClientFactory.java`
- Modify: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/ImAuditSdkAutoConfiguration.java`
- Modify tests under `im-management/im-audit/im-audit-sdk/src/test/java/com/co/kc/imchat/management/audit/sdk/{properties,security}`
- Modify: `im-management/im-audit/im-audit-sdk/src/test/java/com/co/kc/imchat/management/audit/sdk/ImAuditSdkAutoConfigurationTest.java`

- [x] Write RED property tests proving HTTP and RPC both require `im.audit.iam.token-uri`, `client-id`, and `client-secret`, while Kafka does not.
- [x] Add immutable `AuditIamProperties(tokenUri, clientId, clientSecret, timeout, tokenRefreshSkew)` with defaults, validation, and secret-masking `toString()`.
- [x] Remove IAM fields from `AuditHttpProperties`; retain only Audit endpoint, HTTP timeout, worker/queue and retry settings. Do not add aliases for the old paths.
- [x] Update `AuditProperties` to contain `iam`; validate `http.endpoint` only for HTTP and IAM identity for HTTP or RPC.
- [x] Make `IamClientCredentialsTokenProvider` depend on `AuditIamProperties`; use a dedicated `IamRestClientFactory` so RPC does not construct a fake HTTP transport configuration.
- [x] Update existing token-provider and HTTP transport fixtures to the new constructors and property paths.
- [x] Run `mvn -q -pl im-management/im-audit/im-audit-sdk -am -Dtest=AuditPropertiesTest,IamClientCredentialsTokenProviderTest,ImAuditSdkAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`; expect pass.

Canonical configuration after this task:

```yaml
im:
  audit:
    enabled: true
    transport: rpc
    iam:
      token-uri: https://iam.invalid/oauth2/token
      client-id: im-admin-audit
      client-secret: REQUIRED_FROM_NACOS
```

### Task 3: Add the Audit RPC contract and SDK transport

**Files:**

- Modify: `im-management/im-audit/im-audit-sdk/pom.xml`
- Create: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/rpc/AuditRpcService.java`
- Create: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/transport/RpcAuditTransport.java`
- Modify: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/transport/AuditTransportType.java`
- Create: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/AuditRpcAutoConfiguration.java`
- Modify: `im-management/im-audit/im-audit-sdk/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `im-management/im-audit/im-audit-sdk/src/test/java/com/co/kc/imchat/management/audit/sdk/transport/RpcAuditTransportTest.java`
- Test: `im-management/im-audit/im-audit-sdk/src/test/java/com/co/kc/imchat/management/audit/sdk/AuditRpcAutoConfigurationTest.java`

- [x] Write a RED transport test proving `RpcAuditTransport.send(event)` invokes `AuditRpcService.submit(event)` exactly once and propagates a synchronous RPC failure to the existing `AuditClient` boundary.
- [x] Add `AuditRpcService` and the minimal delegating `RpcAuditTransport`; do not add a Params wrapper containing one event.
- [x] Add `RPC` to `AuditTransportType` with a comment identifying synchronous IAM-authenticated Dubbo delivery.
- [x] Add optional `im-dubbo`/Dubbo compile dependencies so HTTP- and Kafka-only consumers are not forced to add Dubbo runtime infrastructure.
- [x] Add auto-configuration tests proving RPC selection creates exactly one RPC `AuditTransport` and Token provider adapter; disabled and HTTP selection create no RPC components, while Kafka remains Binder-owned.
- [x] Implement `AuditRpcAutoConfiguration`, guarded by `im.audit.enabled=true`, `im.audit.transport=rpc`, and Dubbo classes. Create the `AuditRpcService` reference with version `1.0.0`, `retries=0`, and filter `imRpcTokenConsumer`.
- [x] Adapt the existing `AccessTokenProvider` to `RpcAccessTokenProvider` through a Bean only in RPC mode. The Token stays in the filter attachment and never reaches `RpcAuditTransport` arguments.
- [x] Make RPC startup fail when Dubbo reference creation, IAM identity configuration, or the Token-provider adapter is unavailable.
- [x] Register the new auto-configuration and run `mvn -q -pl im-management/im-audit/im-audit-sdk -am test`; expect pass without a Kafka Binder.

### Task 4: Authenticate Audit RPC callers on the server

**Files:**

- Modify: `im-management/im-audit/im-audit-server/pom.xml`
- Create: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/support/security/AuditRpcTokenAuthenticator.java`
- Test: `im-management/im-audit/im-audit-server/src/test/java/com/co/kc/imchat/management/audit/support/security/AuditRpcTokenAuthenticatorTest.java`

- [x] Write RED tests using a mocked `OpaqueTokenIntrospector` for active valid identity, missing `appId`, missing `SCOPE_audit:ingest`, and introspection failure.
- [x] Add an explicit `im-dubbo` dependency to the server and implement `AuditRpcTokenAuthenticator implements RpcTokenAuthenticator`.
- [x] Introspect the Token, combine authorities without stringly typed business conversion, require `SCOPE_audit:ingest`, and create `AuditClientPrincipal(appId, attributes, authorities)` only after validation.
- [x] Return an authenticated Spring Security `Authentication` whose credentials do not retain the raw Token.
- [x] Use stable authentication/authorization exceptions and ensure their messages never contain the Token.
- [x] Run `mvn -q -pl im-management/im-audit/im-audit-server -am -Dtest=AuditRpcTokenAuthenticatorTest -Dsurefire.failIfNoSpecifiedTests=false test`; expect pass.

### Task 5: Add the RPC interface adapter

**Files:**

- Create: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/interfaces/rpc/AuditRpcServiceImpl.java`
- Test: `im-management/im-audit/im-audit-server/src/test/java/com/co/kc/imchat/management/audit/interfaces/rpc/AuditRpcServiceImplTest.java`
- Modify: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/infrastructure/config/beans/AuditServiceBeans.java`
- Modify: `im-management/im-audit/im-audit-server/src/test/java/com/co/kc/imchat/management/audit/ImAuditApplicationTest.java`

- [x] Write a RED adapter test that places `AuditClientPrincipal` in SecurityContext, calls `submit(event)`, and verifies the exact flattened `AuditIngestCmd` passed to `AuditIngestionAppService`.
- [x] Add negative tests proving missing/wrong principal is rejected and no `sourceApp` value from the event can influence the command.
- [x] Implement `AuditRpcServiceImpl` with `@DubboService(interfaceClass = AuditRpcService.class, version = "1.0.0", filter = "imRpcTokenProvider")` and the existing provider RPC exception aspect.
- [x] Keep the adapter readable: one line obtains the principal, one conversion statement creates the command, and one statement invokes the application service.
- [x] Register `AuditRpcTokenAuthenticator` as the sole `RpcTokenAuthenticator` Bean in the server configuration; do not place authentication logic in the RPC method.
- [x] Add a context test proving the Audit RPC contract starts with test replacements without contacting real IAM, Nacos, or Kafka.
- [x] Run `mvn -q -pl im-management/im-audit/im-audit-server -am -Dtest=AuditRpcServiceImplTest,ImAuditApplicationTest -Dsurefire.failIfNoSpecifiedTests=false test`; expect pass.

Adapter shape:

```java
public void submit(AuditEvent event) {
    AuditClientPrincipal principal = AuditClientContext.get();
    AuditIngestCmd command = AuditIngestionTransformer.INSTANCE.auditIngestCmdFrom(
            principal.appId(),
            event);
    auditIngestionAppService.ingest(command);
}
```

### Task 6: Lock transport selection and failure behavior

**Files:**

- Modify: `im-management/im-audit/im-audit-sdk/src/test/java/com/co/kc/imchat/management/audit/sdk/ImAuditSdkAutoConfigurationTest.java`
- Modify: `im-management/im-audit/im-audit-sdk/src/test/java/com/co/kc/imchat/management/audit/sdk/support/AuditedAspectTest.java`
- Create or modify Audit integration tests under `im-management/im-audit/im-audit-server/src/test/java`

- [x] Prove each configured transport selects only its owning transport and never a fallback: SDK context tests cover HTTP/RPC, while Kafka Binder producer contexts cover Kafka.
- [x] Prove an RPC exception is reported through `AuditFailureReporter` and does not replace a successful `@Audited` business result.
- [x] Prove an explicit Audit client invocation keeps the existing transport-neutral failure behavior.
- [x] Prove duplicate `auditId` through the RPC adapter returns normally after the repository confirms the duplicate.
- [x] Run all Audit SDK and Server tests together: `mvn -q -pl im-management/im-audit/im-audit-sdk,im-management/im-audit/im-audit-server -am test`.

### Task 7: Update architecture, Harness, configuration and documentation

**Files:**

- Modify: `im-architecture/src/test/java/com/co/kc/imchat/architecture/ModuleBoundaryTest.java`
- Modify: `im-architecture/src/test/java/com/co/kc/imchat/architecture/RuntimeDependencyPolicyTest.java`
- Modify: `docs/references/CODING_GUIDE.md`
- Modify: `docs/references/HARNESS_GUIDE.md`
- Modify: `docs/SECURITY.md`
- Modify: `docs/RELIABILITY.md`
- Modify: `im-plugin/im-dubbo/README.md`
- Modify: `im-management/im-audit/README.md`
- Modify: `im-management/im-audit/im-audit-sdk/README.md`
- Modify: `im-management/im-audit/im-audit-server/README.md`
- Modify producer `application.yml` files only where RPC is deliberately selected; leave Kafka producers unchanged.
- Modify: `docs/exec-plans/active/README.md`

- [x] Add architecture assertions that `im-dubbo` cannot depend on IAM/Audit and `im-audit-sdk` cannot depend on the server implementation.
- [x] Document the generic rule: RPC authentication metadata belongs in transport attachments; trusted caller identity is established before an RPC interface adapter and is never copied from Params.
- [x] Record runtime authentication/cleanup as focused-test enforcement in the Harness matrix; keep semantic correctness Review-only where source scanning would be noisy.
- [x] Document synchronous latency/availability, no fallback, idempotency, reserved Attachment ownership, IAM Scope, and secret-safe logging.
- [x] Update Audit configuration examples to `im.audit.iam.*` and list `rpc` beside `kafka` and `http`.
- [x] Run `./scripts/verify.sh architecture`, `./scripts/check-drift.sh`, and `bash scripts/test-harness.sh`; expect all pass.

### Task 8: Final verification and plan closure

- [x] Run `mvn -q -pl im-plugin/im-dubbo,im-management/im-audit/im-audit-sdk,im-management/im-audit/im-audit-server -am test`.
- [x] Run `./scripts/verify.sh full` and require exit code `0`.
- [x] Run `git diff --check` and inspect `git status --short`; preserve unrelated user changes.
- [x] Review the complete diff for raw Token logging, caller-supplied `sourceApp`, accidental Dubbo runtime coupling for non-RPC users, compatibility aliases, unused constructors, and test-only production APIs.
- [x] Move this completed plan to `docs/exec-plans/completed/2026-08-30-audit-rpc-ingestion.md` and update both plan indexes only after every gate passes.
- [x] Do not stage or commit unless the user explicitly requests it.

## 6. Rollout, compatibility and rollback

- The repository is under active development; old HTTP IAM property paths are removed without aliases.
- Existing Kafka producers remain unchanged unless intentionally switched to RPC.
- RPC callers must register IAM machine clients with `audit:ingest` and provide secrets through Nacos/deployment configuration.
- Rollback consists of selecting the previous single transport and removing RPC-specific runtime configuration; persisted audit facts remain valid because all transports share the same event and idempotency key.
- A failed RPC call is not retried by another transport. Callers choose RPC with its synchronous availability trade-off.

## 7. Completion criteria

- RPC is a selectable third transport and never activates HTTP/Kafka fallback.
- RPC business parameters contain neither Token nor `sourceApp`.
- IAM identity and `audit:ingest` are verified before the RPC adapter executes.
- SecurityContext is restored or cleared for success and failure paths.
- Duplicate audit delivery remains idempotent.
- Plugin, Audit, architecture, Harness, documentation and full verification gates pass.
- No unrelated files or generated output are included and no Git commit is created without authorization.
