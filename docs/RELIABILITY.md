# Reliability

## Reliability Model

- Business databases are the source of truth for durable account, social, conversation, and message state.
- Broker routing and discovery state is eventually consistent through Gossip; temporary disagreement must not alter durable message facts.
- WebSocket gateways own local sessions. A failed gateway may require clients to reconnect and rebuild Broker routing state.
- Client-confirmed notifications use persisted confirmation state and delayed retry; handlers must remain idempotent.
- Nacos configuration is an override layer. Services retain local defaults and must report missing remote configuration explicitly.

## Operational Requirements

- Startup failures must identify the missing dependency or invalid configuration rather than silently degrading critical paths.
- Scheduled registration, heartbeat, migration, and Gossip tasks must be idempotent and isolate peer failures.
- Timeouts, retry limits, tombstone/removed-entry retention, and Gossip fanout belong in typed configuration properties.
- Business monitoring must expose connection, delivery, retry, and synchronization health without making monitoring data part of the delivery path.

## Verification

- Unit tests cover state transitions and idempotency.
- Integration tests cover adapters and application startup with external systems disabled or substituted deterministically.
- `./scripts/verify.sh full` is the repository completion gate.
- Broker JMX metrics expose `im.broker.instances`, `im.broker.connections`, and `im.broker.gossip.entries`.
- WebSocket Gateway JMX metrics expose `im.gateway.connections` and `im.gateway.users`.

Update this document when failure handling, state authority, retry policy, or operational guarantees change.
