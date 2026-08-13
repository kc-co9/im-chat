# IM Chat Architecture

## Purpose

This document is the concise source of truth for the repository's current architecture. Detailed decisions live in [`docs/design-docs`](docs/design-docs/index.md), while executable work is tracked in [`docs/exec-plans`](docs/PLANS.md).

## Runtime Topology

```text
client
  |-- HTTP ------> im-http-gateway ------> business services
  `-- WebSocket -> im-ws-gateway -> im-broker -> business services
                                      |
                                      `-> target im-ws-gateway -> client
```

- `im-http-gateway` is the external HTTP ingress and routing boundary.
- `im-ws-gateway` owns WebSocket connections, authentication, protocol adaptation, and local session delivery.
- `im-broker` owns user-to-gateway routing, gateway coordination, precise frame delivery, and Broker Gossip synchronization.
- `im-service` owns account, social, and message business rules and persistence.

## Module Boundaries

```text
im-gateway/*-server -> gateway SDK, broker SDK, service facade
im-broker/*-server  -> broker SDK, gateway SDK, message facade, im-gossip
im-service/*-server -> own facade, dependent facades, broker SDK
*-facade / *-sdk    -> stable contracts and im-common
im-plugin           -> framework integrations without business dependencies
im-common           -> framework-neutral shared types and utilities
```

Required dependency rules:

1. Shared modules do not depend on runtime or business modules.
2. SDK and facade modules do not depend on server implementations.
3. Plugin modules do not depend on Broker, Gateway, or service packages.
4. Domain packages do not depend on interfaces, infrastructure, configuration, lifecycle, or Spring types.
5. Runtime protocols are adapted at module boundaries; domain code does not carry Bolt, Dubbo, Nacos, Redis, or HTTP concerns.

These rules are enforced where mechanically possible by `im-architecture` and `scripts/check-drift.sh`.

## Data Ownership

- Message facts, conversation state, account data, and social relationships belong to their business services and persistent stores.
- A WebSocket gateway owns its local connection IDs and channel/session objects.
- A Broker stores only the routing information needed to locate a user's gateway; it does not own gateway-local connection IDs.
- Broker, gateway, and connection discovery state is synchronized between Brokers through Gossip and is eventually consistent.
- Nacos provides service discovery and dynamic configuration overrides. Local configuration remains sufficient to describe defaults.

## Change Rules

- Update this file when runtime topology, module ownership, dependency direction, or data ownership changes.
- Record non-trivial alternatives and decisions in `docs/design-docs` before implementation.
- Track multi-step implementation in `docs/exec-plans/active`, then move the plan to `completed` when its acceptance checks pass.
- Update [`docs/RELIABILITY.md`](docs/RELIABILITY.md) and [`docs/SECURITY.md`](docs/SECURITY.md) when their guarantees change.
