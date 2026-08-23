# Documentation And Harness Structure Implementation Plan

> **For agentic workers:** Execute this plan in the current workspace without subagents. Steps use checkbox (`- [ ]`) syntax for tracking. Do not create a Git commit unless the user explicitly requests it.

**Goal:** Reorganize repository documentation around clear ownership, capture stable coding lessons from the completed Harness-driven version, and remove current-facing documentation drift.

**Architecture:** Keep the root README as a concise entry point, `ARCHITECTURE.md` as the current architecture source of truth, and `docs/references` as stable engineering rules. Keep design and execution documents as decision/history records, with narrowly scoped final-implementation notes where intermediate choices would otherwise mislead current development.

**Tech Stack:** Markdown, Bash Harness scripts, ArchUnit/Maven verification already present in the repository.

---

## Objective and non-goals

Implement [Documentation and Harness structure design](../../design-docs/2026-08-23-documentation-harness-structure-design.md).

This work reorganizes and corrects documentation. It does not change production behavior, create new governance modules, add broad regular-expression checks, rewrite completed-plan evidence, or reformat every module README.

## Affected files and ownership

- `README.md`: concise project and documentation entry point.
- `ARCHITECTURE.md`: current topology, ownership and dependency facts.
- `docs/references/index.md`: task-oriented reference navigation.
- `docs/references/CODING_GUIDE.md`: stable coding and DDD rules.
- `docs/references/HARNESS_GUIDE.md`: Harness lifecycle, sensors and enforcement matrix.
- `docs/references/SQL_GUIDE.md`: SQL source and enforcement boundaries.
- `docs/design-docs/index.md`: design navigation and status.
- `docs/design-docs/2026-08-14-centralized-session-authentication-design.md`: final implementation note only.
- `docs/exec-plans/completed/2026-08-14-centralized-session-authentication.md`: final deviation summary only.
- `docs/exec-plans/completed/README.md`: clarify that append-only final deviation notes do not rewrite history.
- `im-service/im-account/im-account-server/README.md`, `im-broker/im-broker-sdk/README.md`, `im-plugin/im-session/README.md`: current configuration and runtime facts.
- Other module README files: consistency scan only; modify only when a concrete stale statement is found.

### Task 1: Record documentation drift and ownership

- [x] Scan current documentation for removed class names, obsolete authentication fields, temporary JWT key behavior, old Broker bootstrap configuration and duplicated rules.
- [x] Map each current-facing statement to exactly one owner: README, architecture, stable reference, module README, design decision or execution history.
- [x] Preserve unrelated dirty-worktree edits and record any overlapping user content before restructuring.

### Task 2: Simplify the project entry point

- [x] Restructure `README.md` around overview, concise domain vocabulary, core realtime paths, module map, local run/verification and document navigation.
- [x] Replace duplicated architecture, Harness and Git rule bodies with concise summaries and links to their owning documents.
- [x] Keep business behavior needed by a new developer; do not move domain vocabulary into architecture or create a new glossary document.
- [x] Run `./scripts/check-drift.sh`; expected: all local Markdown links resolve.

### Task 3: Reorganize stable engineering references

- [x] Reorder `CODING_GUIDE.md` by development decisions: Java/modeling, DDD, application/CQRS, adapters, Spring/configuration, persistence/transformers, metrics/events, naming and enforcement boundary.
- [x] Add the stable rules listed in the design, including conditional Bean closure and avoiding test-only production APIs.
- [x] Rewrite `HARNESS_GUIDE.md` so it links to coding rules and focuses on rule lifecycle, sensor ownership, fixtures, feedback and the enforcement matrix.
- [x] Verify `SQL_GUIDE.md` explicitly rejects Mapper `SELECT *`, allows `COUNT(*)`, and matches the implemented XML/annotation/DDL checker boundaries.
- [x] Rewrite `docs/references/index.md` as task-oriented navigation.
- [x] Run `bash scripts/test-java-style-harness.sh` and `bash scripts/test-sql-harness.sh`; expected: both pass without weakening fixtures.

### Task 4: Correct current architecture and module documentation

- [x] Update `ARCHITECTURE.md` with current account authentication authority, Session version revocation, message-owned chat view state, Nacos Broker bootstrap and Broker snapshot refresh.
- [x] Update Account README with JWT/Bolt enablement, Nacos override expectations and current sign-in/refresh/logout behavior.
- [x] Update Broker SDK README with DiscoveryClient bootstrap and `LIST_BROKERS` snapshot refresh behavior.
- [x] Update Session plugin README with explicit signing-key requirements and codec/model package ownership.
- [x] Scan other module README files and change only concrete contradictions with current code.

### Task 5: Annotate history without rewriting it

- [x] Add a final implementation note to the centralized session authentication design covering explicit keys, exception-based authentication failure and final domain naming.
- [x] Add an append-only final deviation summary after the completed authentication plan title; leave original tasks and evidence unchanged.
- [x] Clarify the completed-plan index policy for append-only final deviation notes.
- [x] Update `docs/design-docs/index.md` and active/completed plan indexes.

### Task 6: Verify and close

- [x] Run `git diff --check`.
- [x] Run `bash scripts/test-harness.sh`.
- [x] Run `./scripts/check-drift.sh`.
- [x] Run `./scripts/verify.sh quick`.
- [x] Run `./scripts/verify.sh full`.
- [x] Review the complete documentation diff for duplicated current facts, unresolved links, accidental history rewrites and unrelated cleanup.
- [x] Move this plan to `docs/exec-plans/completed/2026-08-23-documentation-harness-structure.md` and update plan indexes only after all checks pass.

## Rollout and rollback

This change has no runtime rollout. If a moved explanation becomes harder to discover, restore a concise link at the former entry point rather than copying the full rule back. If a Harness statement does not match an implemented sensor, correct the matrix or add a tracked follow-up; do not claim enforcement that does not exist.

## Completion criteria

- Each current architecture or engineering rule has one clear owning document.
- Root and module README files remain useful entry points without duplicating stable references.
- Authentication and Broker documentation matches the current code and configuration.
- Harness documentation clearly distinguishes automated, architecture-tested and Review-only rules.
- Existing SQL and Java Harness fixtures remain unchanged in strength and pass.
- Quick and full verification pass.
