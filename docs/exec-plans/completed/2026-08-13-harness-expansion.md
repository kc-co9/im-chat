# Harness Expansion Implementation Plan

> **For agentic workers:** Execute tasks in order and update checkboxes as each verification gate passes.

**Goal:** Extend the repository Harness from architecture guidance into behavior, CI, maintainability, runtime feedback, continuous governance, and reusable topology guidance.

**Architecture:** Keep deterministic feedback in repository scripts, Maven tests, and CI. Add runtime metrics at existing registry boundaries, model reviewed business scenarios as executable tests, and keep inferential review as an explicit independent checklist rather than pretending it is deterministic.

**Tech Stack:** Bash, Maven, JUnit 5, ArchUnit, Maven Enforcer, JaCoCo, Micrometer, Spring Boot Actuator, GitHub Actions.

---

### Task 1: Change-aware verification and CI

- [x] Add module impact resolution and `verify.sh affected`.
- [x] Add PR, main, and scheduled GitHub Actions workflows.
- [x] Test documentation-only, leaf-module, plugin, and shared-module impact resolution.

### Task 2: Behavior harness

- [x] Document approved IM scenarios and acceptance boundaries.
- [x] Map Broker routing, migration, Gossip convergence, and frame delivery scenarios to executable evidence.
- [x] Add a dedicated behavior verification mode.

### Task 3: Maintainability and compatibility sensors

- [x] Enforce supported Java and Maven versions at the reactor boundary.
- [x] Produce JaCoCo reports without introducing an arbitrary repository-wide coverage threshold.
- [x] Add an API baseline command for facade and SDK modules with an explicit release-baseline limitation.

### Task 4: Inferential review harness

- [x] Add an independent review guide covering intent, overengineering, architecture, behavior, reliability, and security.
- [x] Define a stable findings-first review output suitable for humans and agents.

### Task 5: Runtime feedback

- [x] Add Actuator and Micrometer support to Broker and WebSocket Gateway.
- [x] Export Broker, Gossip, gateway, user, and connection capacity signals at existing registry boundaries.
- [x] Add focused metric registration tests.

### Task 6: Continuous governance and harness quality

- [x] Add scheduled garbage-collection checks for stale plans, unresolved debt, dead documentation links, and untracked drift.
- [x] Record verification duration and rule outcomes as a machine-readable Harness report.
- [x] Add a quality score based on objective sensor availability and latest outcomes, not subjective code scoring.

### Task 7: Harness templates and documentation

- [x] Add topology guides for business services, plugins, RPC handlers, and registries.
- [x] Link templates from module AGENTS files without duplicating their rules.
- [x] Update README with the expanded model, commands, CI lifecycle, metrics, and future extension points.

### Task 8: Verification and closure

- [x] Run script-level tests and all new focused tests.
- [x] Run `./scripts/verify.sh quick` and `./scripts/verify.sh full`.
- [x] Move this plan to `completed` only after all checks pass.
