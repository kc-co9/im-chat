# README And Message Notification Documentation Implementation Plan

> **For agentic workers:** Execute in the current workspace without subagents, worktrees or commits. Track progress with the checkboxes below.

**Goal:** Make the README hierarchy accurately explain module ownership and the complete message notification, ACK and retry flow.

**Architecture:** Keep project-wide concepts in the root README, runtime implementation in the owning server README, and public transport semantics in Facade/SDK READMEs. Correct reliability language to match task-presence acknowledgement rather than nonexistent status records.

**Tech Stack:** Markdown, Java source inspection and repository Harness scripts.

---

### Task 1: Root and reliability narrative

- [x] Add an end-to-end notification and ACK diagram to the root README.
- [x] Explain durable facts, offline delivery, duplicate notifications and retry ownership.
- [x] Correct `RELIABILITY.md` to describe persisted pending receipt tasks and current failure windows.

### Task 2: Message module documentation

- [x] Expand the message aggregate README with submodule and flow ownership.
- [x] Add detailed transaction, notifier, receipt-task, ACK, retry and observability sections to the message server README.
- [x] Clarify the Message Facade contract and keep server internals out of it.

### Task 3: Broker and Gateway documentation

- [x] Add connection-close routing to Broker operation and ownership documentation.
- [x] Correct WS Gateway SDK class and operation names.
- [x] Clarify Gateway local delivery, partial results and Session-version close filtering.

### Task 4: Plugin documentation

- [x] Document `@IgnoreException` in `im-metrics` with its best-effort boundary.
- [x] Document `LockOptions` and template overloads in `im-lock`.

### Task 5: Verify and archive

- [x] Run drift checks and `git diff --check`.
- [x] Run `./scripts/verify.sh quick`.
- [x] Review changed documentation for duplicated or stale content.
- [x] Move the plan to completed and update indexes.
