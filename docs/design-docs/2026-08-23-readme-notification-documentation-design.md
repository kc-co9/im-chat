# README And Message Notification Documentation Design

## Background

The repository README hierarchy already explains module ownership, but notification documentation is incomplete and some names and reliability statements have drifted from the implementation. In particular, the message README describes `PENDING/CONFIRMED` state that does not exist, the WS Gateway SDK README uses superseded contract names, and Broker documentation omits session connection-close routing.

## Decision

Documentation remains layered by ownership:

- The root README explains the end-to-end notification and acknowledgement flow, durable facts, and cross-component responsibilities.
- The message aggregate README explains submodule responsibilities.
- The message server README owns detailed transaction, notification selection, acknowledgement, retry, Redis layout, failure, and observability semantics.
- Facade and SDK READMEs describe contracts only, without copying server internals.
- Broker and WS Gateway READMEs explain their routing and local-delivery responsibilities.
- Plugin READMEs document caller-visible generic capabilities only.
- `RELIABILITY.md` records guarantees and known limitations using the same vocabulary as the implementation.

Small text diagrams will show the end-to-end flow and the receipt-task lifecycle. Detailed class lists remain tables so the diagrams stay readable.

## Current Notification Semantics

Message and inbox facts are committed to MySQL before notification delivery begins. A domain event invokes the application notification path after transaction commit. The notifier invoker first persists a delayed `ReceiptTask`, then performs the initial Broker delivery.

Redis does not store a `PENDING/CONFIRMED` status. Task presence represents pending acknowledgement: the task body and retry-attempt counter are keyed by `receiptId`, while delayed queues contain only `receiptId`. A client ACK deletes the task, queued identifier and attempt counter. A delayed identifier whose task no longer exists is skipped.

The current retry mechanism performs at most three redelivery attempts after the initial notification. It is bounded and persistent in Redis, but is not an outbox and does not guarantee redelivery across every process-failure window. Duplicate delivery is allowed and clients must handle notification identifiers idempotently.

## Scope

Update the root, message, Broker, WS Gateway, Metrics, Lock and reliability documentation. Keep accurate Account, Social, Common, Architecture and other plugin READMEs concise; do not rewrite them merely for formatting uniformity.

No production code, configuration, protocol or runtime behavior changes are included.

## Verification

- Check every documented class and operation name against source.
- Run repository drift and Markdown link checks through the normal Harness.
- Run `git diff --check` and `./scripts/verify.sh quick`.
