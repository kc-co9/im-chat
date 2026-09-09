# IM Chat Architecture

## Purpose

This document is the concise source of truth for the repository's current architecture. Detailed decisions live in [`docs/design-docs`](docs/design-docs/index.md), while executable work is tracked in [`docs/exec-plans`](docs/PLANS.md).

## Runtime Topology

```text
client
  |-- HTTP ------> im-http-gateway --auth--> im-account
  |                                  `-----> business services
  `-- WebSocket -> im-ws-gateway --auth--> im-account
                         `---------> im-broker -> business services
                                      |
                                      `-> target im-ws-gateway -> client

operations browser -> im-iam-server -> IAM MySQL
          |                    `-> OAuth2/OIDC + live introspection
          |-> im-monitor -> Broker management HTTP
          |-> im-admin -> Account Admin Facade -> im-account
          `-> im-audit-server -> Audit MySQL

im-admin / im-iam / im-monitor
          `-> source-isolated Kafka or authenticated async HTTP -> im-audit-server
```

- `im-http-gateway` is the external HTTP ingress and routing boundary.
- `im-ws-gateway` owns WebSocket connections, authentication, protocol adaptation, and local session delivery.
- `im-broker` owns user-to-gateway routing, gateway coordination, precise frame delivery, and Broker Gossip synchronization.
- `im-service` owns account, social, and message business rules and persistence.
- `im-management/im-monitor` owns read-only Broker discovery, diagnostic aggregation, and its monitoring UI; it never owns routing state or business facts.
- `im-management/im-iam/im-iam-server` exclusively owns management identity, SSO sessions, registered browser applications, machine clients and application-scoped RBAC.
- `im-management/im-iam/im-iam-sdk` owns reusable OAuth2/OIDC BFF integration and has no dependency on the IAM server implementation.
- `im-management/im-audit/im-audit-sdk` owns the immutable producer contract, safe context collection, declarative auditing and selected transport adaptation.
- `im-management/im-audit/im-audit-server` exclusively owns management BUSINESS/SECURITY audit ingestion, append-only persistence, query, UI and bounded export.
- `im-management/im-admin` owns ordinary-user administration only. Ordinary-user facts remain in Account and are managed only through the Admin Facade.
- `im-account` is the sole Token signer and online Session authentication authority. Gateways fail closed when account authentication is unavailable.
- `im-message` owns the user's current `ImChatView`; unread decisions do not depend on Account Session, Gateway, or Broker state.

Broker SDK consumers discover an initial `im-broker` endpoint through Nacos. After the first successful call, the SDK periodically obtains the Broker cluster snapshot through `LIST_BROKERS` and atomically replaces its local address list. Nacos provides bootstrap discovery; Broker registry and Gossip provide the runtime routing snapshot.

## Module Boundaries

```text
im-gateway/*-server -> gateway SDK, broker SDK, service facade
im-broker/*-server  -> broker SDK, gateway SDK, message facade, im-gossip
im-service/*-server -> own facade, dependent facades, broker SDK
im-management/im-monitor -> im-common, generic discovery integration, Broker management HTTP
im-management/im-admin   -> im-iam-sdk, im-account-admin-facade, im-web, generic plugins
im-management/im-monitor -> im-iam-sdk, im-web
im-management/im-admin and im-iam-server -> im-audit-sdk, im-mq-kafka
im-management/im-audit/im-audit-server -> im-audit-sdk, im-iam-sdk, im-mq-kafka
im-management/im-iam/im-iam-sdk -> im-common, im-web and generic Spring/Redis integration
im-management/im-iam/im-iam-server -> im-common and generic plugins
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
6. `im-web` owns generic HTTP behavior only. Session-to-Servlet adaptation belongs to `im-session`; IAM and business applications consume `im-web` without moving their identity or domain semantics into it.

These rules are enforced where mechanically possible by `im-architecture` and `scripts/check-drift.sh`.

## Data Ownership

- Account、Social、Message 分别拥有 `im_chat_account`、`im_chat_social`、`im_chat_message` MySQL Schema；Message facts、conversation state、account data 和 social relationships 不在服务间共享表或执行跨 Schema SQL。
- A WebSocket gateway owns its local connection IDs and channel/session objects.
- A Broker stores only the routing information needed to locate a user's gateway; it does not own gateway-local connection IDs.
- Account Session version is the revocation authority. Broker routes `userId + oldSessionVersion` close controls, while the target WebSocket gateway filters its local connections.
- Message owns `ImChatView(userId, chatId)` in Redis. Opening a chat replaces the current view; exiting or hiding clears it; message fan-out uses it only for unread behavior.
- Broker, gateway, and connection discovery state is synchronized between Brokers through Gossip and is eventually consistent.
- Nacos provides application discovery, Broker bootstrap discovery, and dynamic configuration overrides. It does not replace Broker routing or Gateway-local connection ownership.
- Broker management HTTP exposes bounded, read-only runtime diagnostics. `im-monitor` discovers these endpoints from explicit Nacos metadata; the browser accesses only `im-monitor` APIs.
- IAM 的 `im_chat_iam` MySQL Schema owns management administrators, browser applications, machine clients, permission catalogs, roles, grants and OAuth2 authorization metadata. IAM persists only Token digests.
- Audit 的 `im_chat_audit` MySQL Schema is the only management audit authority. It appends immutable BUSINESS/SECURITY facts idempotently by `auditId`; Admin and IAM do not own local audit tables.
- Each management application Redis owns only its encrypted BFF Token Session and bounded Introspection cache. None of these stores becomes an authority for ordinary users or audit facts.

## Change Rules

- Update this file when runtime topology, module ownership, dependency direction, or data ownership changes.
- Record non-trivial alternatives and decisions in `docs/design-docs` before implementation.
- Track multi-step implementation in `docs/exec-plans/active`, then move the plan to `completed` when its acceptance checks pass.
- Update [`docs/RELIABILITY.md`](docs/RELIABILITY.md) and [`docs/SECURITY.md`](docs/SECURITY.md) when their guarantees change.
