# Central Management Audit Design

## 1. Context

`im-admin` currently owns business-operation audit records, while `im-iam-server`
owns authentication and authorization security audit records. Both modules define
their own audit models, persistence, application services, HTTP queries, and UI
entry points. This duplicates collection and query infrastructure and prevents a
single operator from tracing management activity across applications.

The repository is still under development. The new audit boundary therefore
replaces the local Admin and IAM audit implementations directly. It does not
retain dual writes, compatibility APIs, or historical-table migration.

## 2. Goals

- Add one independently deployed management audit service and one reusable SDK.
- Collect both business audits and security audits without merging their business
  meanings into one untyped log payload.
- Support Kafka and asynchronous HTTP ingestion behind an SDK-owned transport
  selection.
- Keep audit delivery failure isolated from the original business result.
- Provide IAM-protected audit search, detail, and bounded Excel export.
- Preserve project DDD, MyBatis Entity, CQRS, Transformer, and Harness conventions.
- Remove the local Admin and IAM audit stores after cutover.

## 3. Non-goals

- No local transactional Outbox in the first version.
- No automatic Kafka-to-HTTP or HTTP-to-Kafka fallback.
- No Kafka dead-letter inspection or replay UI in the audit application.
- No audit-record update, logical-delete command, or physical-delete endpoint.
- No historical import from `db_admin_audit_log` or
  `db_iam_security_audit`.
- No generic multi-provider MQ framework beyond the Kafka capability currently
  required.
- No asynchronous export job or object-storage download workflow.

## 4. Alternatives

### 4.1 Keep audit inside each application

This preserves local ownership but repeats collection, storage, query, export,
retention, and security behavior. Cross-application investigations remain split.
Rejected.

### 4.2 Move audit into IAM

IAM already records security events, but ordinary-user administration and future
management applications are not IAM domain behavior. Making IAM the audit store
would combine identity authority with unrelated business evidence. Rejected.

### 4.3 Add a central management audit boundary

An independent Audit application owns ingestion, persistence, query, export, and
its UI. Producer applications retain ownership of the meaning and timing of each
event, while the SDK standardizes transport and safe context collection. Chosen.

## 5. Module Ownership

```text
im-management
|-- im-admin
|-- im-monitor
|-- im-iam
`-- im-audit
    |-- pom.xml
    |-- im-audit-sdk
    `-- im-audit-server

im-plugin
`-- im-mq-kafka
```

`im-audit-sdk` owns the cross-process audit contract, declarative collection,
context collection, validation, redaction, and transport selection.

`im-audit-server` owns HTTP and Kafka ingestion adapters, the audit domain,
MySQL persistence, query APIs, Excel export, and the independent audit UI.

`im-mq-kafka` replaces the current unused `im-mq` SPI and in-memory bus. It owns
Spring Cloud Stream Kafka Binder integration and Kafka-specific infrastructure
configuration. It does not depend on Audit or any other business module.

Admin, IAM, Monitor, and future producers use only `im-audit-sdk` as their audit
business contract. Kafka deployments additionally add the infrastructure-only
`im-mq-kafka` runtime dependency. Producers never access the audit database or
server implementation.

## 6. Runtime Topology

```text
Admin / IAM / Monitor
        |
        v
AuditClient.submit(AuditEvent)
        |
        +-- KafkaAuditTransport --> Kafka --> AuditEventConsumer --+
        |                                                         |
        `-- HttpAuditTransport --> internal HTTP Controller -------+
                                                                  v
                                                AuditIngestionAppService
                                                                  |
                                                                  v
                                                           Audit MySQL

operations browser --> im-audit-server UI/API --> Audit MySQL
                              |
                              `--> im-iam-sdk --> im-iam-server
```

The audit UI is a standalone IAM application with `appId=imAudit`. It uses the
same standard login redirect and BFF Session model as other management
applications.

## 7. SDK Boundary

### 7.1 Public entry points

```java
public interface AuditClient {
    void submit(AuditEvent event);
}

public interface AuditContextCollector {
    AuditContext collect();
}
```

`submit` means that the SDK accepts an event for asynchronous delivery. It does
not imply direct database persistence or expose the selected transport.

`AuditContextCollector` combines the current security principal, HTTP request
metadata, trace context, and configured source application before execution
moves to an asynchronous thread. Asynchronous workers do not read request-bound
or security-bound thread state.

### 7.2 Declarative and explicit collection

Management write use cases use `@Audited` where action, target, and safe
description can be declared deterministically. Authentication, Token, Session,
and protocol listeners create and submit explicit events.

When a deterministic use case needs dynamic attributes, it marks only the
required method parameters with `@AuditAttribute`. A scalar parameter uses its
parameter name; an object parameter contributes its first-level properties.
The successful result follows the same scalar-or-object rule automatically.
Properties are not recursively expanded, null values are omitted, and a field
or record component marked `@AuditAttribute(include = false)` is excluded. The
aspect owns success/failure capture, error-code resolution, context collection,
submission timing, attribute limits, and failure isolation; the application
service does not reproduce that control flow or provide a use-case-specific
attribute resolver.

`@Audited` does not declare permissions. Spring Security remains the sole local
authorization mechanism. Unannotated arguments, exceptions, nested properties,
and output content are never collected. Fields containing credentials or other
non-audit data must explicitly declare `@AuditAttribute(include = false)`.

### 7.3 Transport selection

```yaml
im:
  audit:
    enabled: true
    transport: kafka # kafka or http
```

Transport selection belongs to `im-audit-sdk`, not `im-mq-kafka`.

- `KafkaAuditTransport` uses Spring Cloud Stream and `im-mq-kafka`.
- `HttpAuditTransport` uses a bounded asynchronous HTTP executor.
- Selecting Kafka without a usable Kafka Binder fails application startup.
- Neither implementation silently falls back to the other.

Only applications selecting Kafka need the Kafka plugin at runtime. The SDK
must not force Kafka client dependencies into HTTP-only deployments.

`im-audit-sdk` depends on Spring Cloud Stream's framework API so
`KafkaAuditTransport` can use `StreamBridge`; it does not carry a Kafka Binder or
Kafka client transitively. A Kafka deployment adds `im-mq-kafka`, which supplies
the Binder and concrete client.

## 8. Audit Contract

The SDK publishes an immutable transport event with these concepts:

```text
AuditEvent
|-- auditId
|-- auditType       BUSINESS | SECURITY
|-- action
|-- actor
|-- target
|-- outcome         SUCCESS | FAILURE
|-- errorCode       optional stable code
|-- description     safe and bounded
|-- clientAddress   optional
|-- userAgent       optional
|-- traceId         optional
|-- attributes      optional bounded string map
`-- occurredAt
```

`AuditId` is generated once by the SDK. It is used as the Kafka message key and
the server idempotency key. HTTP retries and Kafka redelivery retain the same
identifier.

`sourceApp` is deliberately absent from the producer-controlled transport
event. The HTTP adapter derives it from the authenticated IAM machine identity,
and each Kafka binding injects its fixed registered source before creating the
flat ingestion command. The domain event still owns `SourceApp` as an audited
fact, but untrusted payload data can never declare or override it.

Admin and IAM may retain source-owned action enums. The SDK wire contract carries
a stable action code and does not depend on producer-domain enum classes.

`attributes` is extensibility data, not a raw payload. It has bounded entry
count, key length, value length, and total encoded size. Callers add attributes
explicitly after redaction.

## 9. Audit Domain And Persistence

The server converts the transport event into domain values at the application
boundary. The audit aggregate is not a record-shaped bag of primitives.

```java
public class AuditEvent extends Identification implements Serializable {
    private final AuditId id;
    private final AuditType type;
    private final SourceApp sourceApp;
    private final AuditAction action;
    private final AuditActor actor;
    private final AuditTarget target;
    private final AuditOutcome outcome;
    private final AuditErrorCode errorCode;
    private final AuditDescription description;
    private AuditClientContext clientContext;
    private final TraceId traceId;
    private final AuditAttributes attributes;
    private final Instant occurredAt;
}
```

The aggregate follows the project `Identification` and Lombok equality rules.
It is immutable after construction and exposes no success transition, update,
or delete behavior because only completed facts cross the ingestion boundary.
Its Builder is handwritten and owns an instance created through the aggregate's
private no-argument constructor. Builder methods populate that instance directly
without duplicating fields or retaining a long constructor, and `build()` validates
all aggregate invariants.
The persistence-only `pkId` is not a Builder property; the Repository restores
that technical identity only after the domain object has been built.

The MyBatis Entity extends `BaseEntity` and therefore retains the standard
database primary key, creation time, update time, and logical-delete marker even
though audit business behavior never updates or deletes records.

```text
id             database auto-increment primary key
audit_id       stable audit business identity and unique idempotency key
occurred_at    producer-observed event time, stored as TIMESTAMP(3)
create_time    successful Audit database insertion time
update_time    standard Entity field; unused by the first version
is_deleted     standard Entity field; never changed by audit business behavior
```

The Repository exposes append and query operations only. Persistence uses a
MyBatis Service; the domain Repository does not inject or orchestrate a Mapper
directly.

Bounded domain queries use the shared `TimeRange` value object rather than
carrying separate start and end `Instant` fields. HTTP and application CQRS
models retain their boundary fields; the application Transformer assembles the
inclusive range before invoking the domain Repository.

Indexes support bounded searches by source/time, actor/time, target/time,
type/action/outcome/time, trace ID, and the unique audit ID.

The browser HTTP boundary represents absolute times as epoch milliseconds.
Interface Transformers convert them to and from the internal `Instant` model;
the Java SDK contract continues to use `Instant`. Browser rendering uses the
browser IANA zone and `yyyy-MM-dd HH:mm:ss`. Server-side Excel export receives
that IANA zone explicitly and never relies on the server default zone.

## 10. Transaction And Failure Semantics

- A successful audited method inside a transaction submits its success event
  only after commit.
- A rolled-back operation does not emit a success event.
- A thrown business exception emits a failure event with a stable error code and
  then propagates the original exception unchanged.
- Audit transport failure never replaces the business return value or exception.
- Methods without a transaction submit immediately after completion.
- Kafka delivery is at least once; Audit persistence is idempotent by `auditId`.
- The Kafka consumer acknowledges only after successful insertion or confirmed
  idempotent duplication.
- Repeated persistence failures follow bounded retry and then enter a Kafka
  dead-letter Topic.
- HTTP retries timeouts and server `5xx`; contract `4xx` failures are not retried.
- HTTP uses a bounded queue. Queue exhaustion or final delivery failure emits a
  finite-cardinality metric and an error log.

The first version intentionally has no local transactional Outbox. A producer
process can fail after the business transaction commits but before the audit
event reaches Kafka or HTTP. Closing that gap requires a producer-local Outbox
in the same transaction and is deferred as explicit reliability work.

Kafka dead letters remain an operational Kafka concern. The audit UI does not
consume, persist, display, or replay DLQ messages in the first version.

## 11. Security And Data Minimization

Passwords, password hashes, Cookies, Session and CSRF identifiers, Access and
Refresh Tokens, authorization codes, client secrets, signing keys, credential
digests, complete request bodies, SQL, and exception stacks are forbidden from
all audit fields and logs.

The SDK applies type validation, length bounds, and sensitive-key rejection
before transport. The server repeats contract validation and rejects malformed
events. It never trusts a transport payload merely because it came from Kafka.

The internal HTTP ingestion endpoint is not a browser API. It requires network
isolation and an IAM OAuth2 Client Credentials Access Token with only the
`audit:ingest` Scope. Each producer uses a distinct machine `clientId`; IAM itself
uses a dedicated audit-producer client and does not reuse its browser client.
The SDK caches the short-lived Access Token and renews it before expiry. The
Audit Server introspects the Token and derives `sourceApp` from the authenticated
client. The request body does not contain a source field. Authentication and
authorization `4xx` responses are not retried.

Kafka producers and consumers use distinct scoped credentials and Topic ACLs.
Each source writes only to its source-owned audit Topic. The source-specific
consumer binding injects the registered source application into the ingestion
command; the message body has no source field to spoof. Adding a source requires
an explicit Topic/ACL registration rather than granting a shared unrestricted
producer credential.

Initial Topics are `im.audit.im-admin.v1`, `im.audit.im-iam.v1`,
`im.audit.im-monitor.v1`, and `im.audit.im-audit.v1`. The Audit application uses
its own Topic for export events. Ingestion itself is not annotated for auditing,
so accepting an event cannot recursively create another event.

The audit UI defines these first-version IAM permissions:

```text
audit:read
audit:export
```

## 12. Query And UI

The standalone UI provides business-audit and security-audit views backed by one
query model, plus an audit detail view. Filters include source application,
audit type, action, actor, target, outcome, stable error code, trace ID, and a
required bounded time range where appropriate.

List growth uses `Paging` and `PagingResult`. HTTP response enums are distinct
from domain enums and Transformer mappings own cross-layer conversion.

The UI does not expose record edit, delete, Kafka administration, or dead-letter
replay controls.

## 13. Excel Export

The first version supports synchronous streaming `.xlsx` export with a distinct
`audit:export` permission.

- The request must specify a time range of no more than 31 days.
- The database is read in pages of 100 rows and rows are written incrementally.
- Internal primary key, update time, and logical-delete fields are not exported.
- Audit attributes are exported only after SDK/server redaction and size checks.
- Spreadsheet values are written as text where needed to prevent formula
  injection.
- The server does not retain generated files.
- Every export produces an `AUDIT_EXPORT` audit event containing the actor and
  annotated first-level query attributes, not the file contents.
- Export declares the common `@Audited` boundary. The query parameter opts into
  attribute collection with `@AuditAttribute`, while export orchestration remains
  unaware of audit submission.

The implementation uses Apache Fesod `fesod-sheet`, evaluated against the active
Apache Incubating release, rather than the archived EasyExcel project, through
the repository's generic `im-excel` streaming integration.

References:

- [Apache Fesod](https://github.com/apache/fesod)
- [Spring Cloud Stream Kafka Binder](https://docs.spring.io/spring-cloud-stream/reference/kafka/kafka-binder/index.html)

## 14. Direct Cutover

Implementation removes rather than adapts the local audit capabilities:

- Remove Admin audit domain, persistence, application query, HTTP API, and UI.
- Remove IAM security-audit domain, persistence, application query, HTTP API, and
  local management page.
- Remove `db_admin_audit_log` and `db_iam_security_audit` from development DDL.
- Replace source annotations/listeners with `im-audit-sdk` usage.
- Do not migrate existing development records.
- Do not dual write.

The IAM implementation gains OAuth2 Client Credentials only for scoped machine
clients such as audit HTTP producers. This intentionally supersedes the earlier
IAM non-goal that excluded Client Credentials; browser login remains
Authorization Code with PKCE and does not reuse machine clients.

The old design documents remain historical decision records. Current runtime,
security, reliability, and README documents must be updated in the same
implementation change so they no longer claim that Admin or IAM owns audit
persistence.

## 15. Verification And Harness Feedback

Focused tests must cover:

- SDK validation, sensitive-key rejection, context collection, and cleanup;
- annotation target resolution and success/failure exception preservation;
- transaction commit, rollback, and non-transactional submission timing;
- Kafka and HTTP transport selection, missing dependency startup failure, queue
  bounds, timeout, and retry classification;
- Kafka redelivery and database idempotency by `auditId`;
- transport-to-domain and domain-to-Entity Transformer mappings;
- aggregate identity, value-object invariants, and immutable behavior;
- MyBatis `BaseEntity`, database enum, Mapper/Service/Repository, DDL, and index
  conventions;
- IAM authorization for query, detail, and export;
- bounded Fesod export, paging behavior, field exclusion, and formula safety;
- architecture rules that prohibit Admin/IAM local audit persistence and prevent
  SDK-to-server or plugin-to-business dependencies.

Harness feedback for implementation:

- Update Architecture tests for `im-audit-server`, `im-audit-sdk`, and
  `im-mq-kafka` ownership and dependency direction.
- Add executable coverage proving Admin and IAM no longer declare local audit
  repositories or MyBatis audit entities. Use a narrow package/dependency rule,
  not a list of forbidden class names.
- Keep transport choice and audit failure semantics in focused behavior tests;
  they are not reliable static-regex rules.
- Keep append-only audit behavior in aggregate, Repository-contract, and
  persistence tests. Standard `BaseEntity` fields are still required and must
  not be removed to satisfy append-only semantics.
- Update `HARNESS_GUIDE.md` only when the implementation establishes the new
  executable sensor and baseline.

Completion requires focused module tests, Architecture and Harness checks,
`./scripts/verify.sh quick`, and `./scripts/verify.sh full`.
