# Audit RPC Ingestion Design

> **Status: Withdrawn (2026-09-03).** Audit ingestion now supports only Kafka and
> authenticated asynchronous HTTP. The RPC transport duplicated an existing HTTP
> use case without an independent consumer requirement. Current behavior is defined
> by the [central management audit design](2026-08-28-central-management-audit-design.md).

## 1. Context

The central Audit boundary currently accepts audit facts through Kafka and
asynchronous HTTP. Internal Java services also need an optional synchronous
Dubbo transport. RPC is a third independently selected transport, not a fallback
for Kafka or HTTP.

The RPC boundary must preserve the existing trust rule: producer identity comes
from an authenticated transport context and never from an audit event or an RPC
business parameter.

This design extends the
[central management audit design](2026-08-28-central-management-audit-design.md).

## 2. Goals

- Add synchronous Dubbo delivery as an `im-audit-sdk` transport option.
- Keep the RPC business contract limited to the audit event.
- Authenticate the calling service with an IAM Client Credentials Token.
- Derive `sourceApp` from the verified IAM identity.
- Provide reusable Dubbo Token propagation and authentication in `im-dubbo`.
- Preserve Audit idempotency and best-effort isolation from the original
  business result.

## 3. Non-goals

- No automatic fallback between RPC, HTTP, and Kafka.
- No caller-provided `sourceApp` or Access Token business parameter.
- No trust based only on Dubbo application name, Nacos registration, or network
  location.
- No asynchronous queue, retry scheduler, or dead-letter mechanism for RPC.
- No compatibility API for an unauthenticated RPC contract.

## 4. Alternatives

### 4.1 Carry the Token in RPC Params

This is simple but exposes a security credential to business contracts,
serialization diagnostics, and accidental logging. Rejected.

### 4.2 Trust the Dubbo application name

The application name identifies routing metadata, not a cryptographically
verified caller. It is insufficient as an audit trust source. Rejected.

### 4.3 Propagate authentication through Dubbo filters

The Consumer Filter writes an IAM Token to a Dubbo Attachment. The Provider
Filter verifies it, establishes a Spring Security context for the invocation,
and always restores or clears the context afterward. The business method only
receives the audit event. Chosen.

## 5. Module Ownership

### 5.1 `im-plugin/im-dubbo`

`im-dubbo` owns transport-generic security integration:

- an RPC Access Token provider extension;
- a Token authenticator extension;
- a Consumer Filter that adds the Token to a reserved Attachment;
- a Provider Filter that establishes `SecurityContextHolder` for one invocation;
- guaranteed context restoration and Token-safe error logging.

The plugin does not depend on IAM, Audit, or another business module. It does
not interpret `appId` or Audit permissions.

### 5.2 `im-management/im-audit/im-audit-sdk`

The SDK owns:

- the public `AuditRpcService` contract;
- `RpcAuditTransport`;
- the `RPC` transport selection;
- adaptation of the existing IAM Client Credentials Token provider to the
  generic Dubbo Token provider extension.

The RPC transport uses the same immutable `AuditEvent` contract as Kafka and
HTTP. It does not add `sourceApp` to the event.

### 5.3 `im-management/im-audit/im-audit-server`

The server owns:

- `interfaces.rpc.AuditRpcServiceImpl`;
- the Audit-specific Token authenticator backed by IAM introspection;
- validation of the `audit:ingest` Scope and authenticated `appId`;
- conversion of the trusted identity and event into `AuditIngestEvent`.

## 6. Public Contract

```java
public interface AuditRpcService {

    void submit(AuditSubmitParams params);
}
```

The following contracts are forbidden because they move trusted transport facts
into business input:

```java
void submit(String sourceApp, AuditEvent event);

void submit(String accessToken, AuditEvent event);
```

## 7. Runtime Flow

```text
AuditClient
  -> RpcAuditTransport
  -> IAM Client Credentials Token provider
  -> Dubbo Consumer Filter writes reserved Token Attachment
  -> Dubbo Provider Filter authenticates Token
  -> Spring SecurityContext contains AuditClientPrincipal
  -> AuditRpcService.submit(AuditSubmitParams)
  -> AuditClientContext.get()
  -> AuditIngestEvent
  -> AuditIngestionAppService
  -> idempotent Audit MySQL append
  -> Provider Filter restores or clears SecurityContext
```

The Token is transport metadata. It must not be included in method parameters,
audit attributes, exception messages, or logs.

## 8. Security

The Audit Provider authenticator must:

1. reject a missing or blank Token;
2. introspect the Token through IAM;
3. require an active machine identity;
4. require the `audit:ingest` Scope;
5. require a non-blank `appId`;
6. create `AuditClientPrincipal` only after all checks pass.

The Provider Filter saves the previous Spring Security context before invoking
the service and restores it in `finally`. A reused Dubbo worker thread must not
observe identity from a previous invocation.

## 9. Failure Semantics

- Successful insert or confirmed duplicate `auditId`: RPC returns normally.
- Missing or invalid identity: stable authentication RPC error.
- Missing Scope: stable authorization RPC error.
- Invalid event: stable parameter RPC error.
- Persistence or server failure: stable service-unavailable RPC error.
- RPC delivery never falls back to HTTP or Kafka.
- Declarative `@Audited` delivery remains best effort: Audit failure is reported
  but does not replace the original business result.
- Explicit `AuditClient` behavior remains transport-neutral and follows its
  existing failure contract.

## 10. Configuration

`AuditTransportType` adds `RPC`. Selecting it requires:

- a usable Dubbo `AuditRpcService` reference;
- IAM Client Credentials configuration;
- the generic Dubbo authentication filters.

Missing mandatory RPC transport or authentication infrastructure fails startup.
Transport selection remains singular; enabling RPC does not activate Kafka or
HTTP delivery.

IAM Client Credentials are transport-independent identity configuration and use
`im.audit.iam.*`. HTTP-only settings remain under `im.audit.http.*`; RPC does not
reuse a property object named for HTTP. The repository is under active
development, so the existing HTTP Token fields move directly to the IAM group
without a compatibility alias.

## 11. Verification

Focused tests must prove:

- the Consumer Filter injects the Token only as an Attachment;
- the Provider Filter rejects missing and invalid Tokens;
- the Provider Filter rejects a valid Token without `audit:ingest`;
- the Provider Filter establishes and clears or restores SecurityContext;
- the RPC Adapter derives `sourceApp` from `AuditClientPrincipal`;
- duplicate `auditId` is treated as success;
- RPC selection does not fall back to Kafka or HTTP;
- missing RPC proxy or IAM configuration fails startup;
- logs and exceptions do not contain the Token.

The Coding Guide and Harness matrix own the reusable rule that trusted identity
comes from an authenticated transport context. RPC-specific tests are the
primary enforcement because a source scan cannot reliably prove runtime
authentication and context cleanup.

## 12. Consequences

The Audit SDK gains a synchronous transport suitable for internal Java callers.
It increases request latency and couples availability to the Audit Server for
the duration of the call, so Kafka remains preferable for high-throughput audit
production. The shared Dubbo security mechanism becomes reusable, but its plugin
boundary stays transport-generic and IAM-independent.
