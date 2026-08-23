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

## Operational Requirements

- Startup failures must identify the missing dependency or invalid configuration rather than silently degrading critical paths.
- Scheduled registration, heartbeat, migration, and Gossip tasks must be idempotent and isolate peer failures.
- Timeouts, retry limits, tombstone/removed-entry retention, and Gossip fanout belong in typed configuration properties.
- Business monitoring must expose connection, delivery, retry, and synchronization health without making monitoring data part of the delivery path.
- Session close control publication exposes finite `success`/`failure` counters and a duration timer without user, session, connection, or Token labels.
- A pending notification is represented by the presence of its Redis receipt task. Client ACK removes the task, delayed identifier, and retry counter; there is no separate persisted `CONFIRMED` state.
- Message notification performs at most three redelivery attempts after the initial push. Duplicate delivery is allowed, so clients and ACK handlers must be idempotent.

## Known Reliability Debt

- The repository currently has no durable scheduling or outbox mechanism suitable for retrying account session-close controls. A failed post-commit publish is logged and counted once; it is not placed in an unbounded in-memory retry queue.
- Until a durable bounded retry mechanism is introduced, an already-established old WebSocket may remain open after a control delivery failure. Old credentials still fail every subsequent account authentication because the committed Session version remains authoritative.
- Message notification retry is Redis-backed and bounded, but it is not atomically committed with the MySQL message transaction and is not an Outbox. A process failure after deleting the current retry task and before scheduling its next delay can lose the remaining redelivery attempts; durable message and inbox facts remain available for recovery.

## Verification

- Unit tests cover state transitions and idempotency.
- Integration tests cover adapters and application startup with external systems disabled or substituted deterministically.
- `./scripts/verify.sh full` is the repository completion gate.
- Broker JMX metrics expose `im.broker.instances`, `im.broker.connections`, and `im.broker.gossip.entries`.
- WebSocket Gateway JMX metrics expose `im.gateway.connections` and `im.gateway.users`.

Update this document when failure handling, state authority, retry policy, or operational guarantees change.
