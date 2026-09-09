# Reliability

## Reliability Model

- Business databases are the source of truth for durable account, social, conversation, and message state.
- Broker routing and discovery state is eventually consistent through Gossip; temporary disagreement must not alter durable message facts.
- WebSocket gateways own local sessions. A failed gateway may require clients to reconnect and rebuild Broker routing state.
- Client-confirmed notifications use persisted pending receipt tasks and delayed retry; handlers must remain idempotent.
- Account Session state is the authority for authentication and revocation. Session replacement and sign-out publish a best-effort WebSocket close control only after the account transaction commits.
- Nacos configuration is an override layer. Services retain local defaults and must report missing remote configuration explicitly.
- Broker SDK consumers require at least one Nacos-discovered Broker for bootstrap. Periodic Broker snapshot refresh replaces the local address list only with a non-empty result; refresh failure keeps the last usable snapshot and does not stop the consumer.
- `ImChatView` is an expiring message-domain Redis state used only for unread decisions. Missing or expired state is treated as not viewing and does not affect durable message facts.
- Broker diagnostic histories are bounded in-memory observations. They are intentionally lost on restart and never participate in routing, Gossip, migration, or durable message decisions.
- IAM MySQL is the authority for management identity, browser applications, machine clients, application-scoped roles, grants and authorization metadata. Audit MySQL is the only authority for immutable management audit facts. Ordinary-user management remains available only when the Account Admin Facade is reachable and never falls back to direct Account database access.

## Operational Requirements

- Startup failures must identify the missing dependency or invalid configuration rather than silently degrading critical paths.
- Scheduled registration, heartbeat, migration, and Gossip tasks must be idempotent and isolate peer failures.
- Timeouts, retry limits, tombstone/removed-entry retention, and Gossip fanout belong in typed configuration properties.
- Business monitoring must expose connection, delivery, retry, and synchronization health without making monitoring data part of the delivery path.
- `im-monitor` queries Broker management endpoints concurrently with a bounded executor and per-node timeout. Partial failure is returned as `UNREACHABLE`; healthy node data remains available, while total failure must not fabricate healthy totals.
- Session close control publication exposes finite `success`/`failure` counters and a duration timer without user, session, connection, or Token labels.
- Account status changes schedule Session kick-out only after their database transaction commits. The post-commit callback acquires the Session write lock, updates the Redis-backed Session state, and then immediately publishes the best-effort connection-close control; it does not open a database transaction solely to create another post-commit callback.
- A pending notification is represented by the presence of its Redis receipt task. Client ACK removes the task, delayed identifier, and retry counter; there is no separate persisted `CONFIRMED` state.
- Message notification performs at most three redelivery attempts after the initial push. Duplicate delivery is allowed, so clients and ACK handlers must be idempotent.
- Management producers submit completed audit facts without changing the original business result or exception. Transactional success is submitted only after commit; rollback does not emit success, while failure keeps and rethrows the original exception.
- Kafka delivery is at least once and Audit insertion is idempotent by `auditId`. Source consumers retry persistence failures at most three times before their dedicated DLQ. HTTP retries only timeouts and `5xx`; `4xx` is final. Kafka and HTTP are independently selected and never silently fall back to one another.
- Audit export requires a bounded time range of at most 31 days and streams database records into the workbook in pages of 100 rows.
- Admin and Monitor perform live IAM Introspection on every protected request while IAM is healthy. Only connection failures, timeouts and IAM `5xx` may use the last successful `active=true` result, for at most five minutes and never after Access Token expiry; explicit rejection never falls back.
- IAM SSO uses an 8-hour idle limit and 24-hour absolute limit. Application logout removes only that BFF Session; platform/all-device revocation invalidates the related IAM authorizations.

## Known Reliability Debt

- The repository currently has no durable scheduling or outbox mechanism suitable for retrying account session-close controls. A failed post-commit publish is logged and counted once; it is not placed in an unbounded in-memory retry queue.
- Until a durable bounded retry mechanism is introduced, an already-established old WebSocket may remain open after a control delivery failure. Old credentials still fail every subsequent account authentication because the committed Session version remains authoritative.
- Message notification retry is Redis-backed and bounded, but it is not atomically committed with the MySQL message transaction and is not an Outbox. A process failure after deleting the current retry task and before scheduling its next delay can lose the remaining redelivery attempts; durable message and inbox facts remain available for recovery.
- Audit producers do not use a local Outbox. A process failure after a business commit but before asynchronous transport acceptance can lose that event; accepted Kafka events remain safe to redeliver because persistence is idempotent.

## Verification

- Unit tests cover state transitions and idempotency.
- Integration tests cover adapters and application startup with external systems disabled or substituted deterministically.
- `./scripts/verify.sh full` is the repository completion gate.
- Broker JMX metrics expose `im.broker.instances`, `im.broker.connections`, and `im.broker.gossip.entries`.
- WebSocket Gateway JMX metrics expose `im.gateway.connections` and `im.gateway.users`.

Update this document when failure handling, state authority, retry policy, or operational guarantees change.
