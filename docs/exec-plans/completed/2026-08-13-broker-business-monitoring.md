# Broker Business Monitoring Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide isolated Broker node diagnostics and an independent `im-monitor` application that discovers, aggregates, and displays cluster business state.

**Architecture:** Add read-only HTTP diagnostics beside the Broker Bolt listener, backed by registry queries and bounded in-memory execution records. Add a separate `im-management/im-monitor` Spring Boot application that discovers Broker management endpoints through Nacos metadata, isolates node failures, exposes cluster APIs, and serves its own Vue UI.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring MVC, Nacos Discovery, Micrometer, Maven, JUnit 5, AssertJ, Vue 3, TypeScript, Vite, Vitest, Element Plus, ECharts, Axios.

---

## Objective and non-goals

Implement [Broker business monitoring design](../../design-docs/2026-08-13-broker-business-monitoring-design.md). The first version is read-only, keeps at most 100 diagnostic records by default, does not expose all user routes, and does not introduce persistence, Prometheus/Grafana, RBAC, or `im-admin` business behavior.

## Current-state reconciliation (2026-08-23)

- Broker already exposes current-state Micrometer gauges through `BrokerMetrics`, and the repository already provides the generic `im-metrics` plugin. This plan adds bounded diagnostic history and HTTP read models; it does not replace existing metrics or add a Prometheus exporter. `ConnectionRegistry#count()` also replaces `list().size()` in the existing connection gauge.
- Broker Nacos registration already keeps `spring.cloud.nacos.discovery.port` aligned with the Bolt port. Management reachability is published only through the explicit metadata keys `management-host` and `management-port`; discovery code must not derive one port from the other.
- Operational diagnostics are not Broker domain objects. Broker diagnostic records and query services use a dedicated `diagnostic` package, while HTTP DTOs remain under `interfaces`; `im-monitor` owns its own application read models and never imports `im-broker-server` classes.
- `im-admin` remains a Maven/ownership placeholder in this plan. Do not create its Spring Boot application, Java source tree, or UI.
- The implementation follows the current repository conventions: Java records for immutable read models, typed `@ConfigurationProperties`, constructor injection, MapStruct only for declarative boundary conversion, no test-only production constructors, and no real sleeps in tests.

## Affected modules and ownership

- `im-broker/im-broker-server`: registry read APIs, bounded recorders, sampling points, management service/DTO/controller, HTTP/configuration, and Nacos management metadata.
- `im-management`: new Maven aggregator.
- `im-management/im-monitor`: discovery/client/application aggregation/API and independently owned Vue UI.
- `im-management/im-admin`: aggregator placeholder only with POM and README; no runtime, source tree, or UI implementation.
- `im-architecture`, root `pom.xml`, documentation and verification impact mapping: new module and dependency-boundary enforcement.

### Task 1: Lock public behavior and module skeleton

- [x] Add failing architecture/module tests for root `im-management`, `im-monitor` isolation, shared-module independence from management code, and prohibition on `im-monitor` depending on Broker server implementations.
- [x] Add root and management POM modules with the minimum dependencies; add nearest `AGENTS.md` and README ownership documentation before production classes.
- [x] Add minimal `im-monitor` application context test and run `mvn -q -pl im-management/im-monitor -am test`; expected initial failure, then pass after skeleton wiring.
- [x] Update affected-module resolution and fixture tests so `im-monitor`, `im-admin`, and management-parent changes select the correct reactor modules.

### Task 2: Add registry read capabilities with TDD

- [x] Extend `GatewayRegistry` with `list()` and `ConnectionRegistry` with `count()` only after adding failing concurrent behavior tests to their in-memory implementations.
- [x] Implement immutable snapshot results without exposing internal collections.
- [x] Change the existing `BrokerMetrics` connection gauge to use `count()` instead of materializing `ConnectionRegistry#list()` solely for a count; keep existing metric names stable.
- [x] Run `mvn -q -pl im-broker/im-broker-server -am -Dtest='*RegistryTest' -DfailIfNoTests=false test` and architecture verification.

### Task 3: Add bounded diagnostic recorders

- [x] Define immutable Gossip and migration diagnostic records, summaries, and recorder interfaces under Broker-owned `diagnostic` packages, outside domain, lifecycle, and HTTP packages.
- [x] Add failing tests for capacity eviction, newest-first limit, maximum limit, cumulative counters/timestamps independent of eviction, concurrent writes, and error truncation.
- [x] Implement separate thread-safe bounded in-memory recorders configured by `history-capacity`; keep their state complementary to Micrometer rather than duplicating or replacing `BrokerMetrics`.
- [x] Run focused recorder tests; expected: pass with deterministic clocks/durations and no real waits.

### Task 4: Instrument Gossip and migration execution

- [x] Add failing `BrokerGossipLifecycleTest` cases for success/failure records and recorder exceptions not changing synchronization behavior.
- [x] Add failing `BrokerConnectionServiceTest` cases for per-target migration success/failure records and unchanged original exception semantics.
- [x] Keep time measurement in the operational sampling boundary, using a controllable clock/ticker only where deterministic assertions require it; do not introduce test-only constructors or wall-clock sleeps.
- [x] Implement minimal sampling at the existing execution boundaries. Preserve current semantics exactly: Gossip logs and continues after a peer failure, and migration logs, retains local routes, and continues with other target batches.
- [x] Run `./scripts/verify.sh behavior` because Gossip and connection migration behavior is touched.

### Task 5: Build the Broker diagnostic application service

- [x] Add failing service tests for overview counts, instance identity/uptime, Broker/Gateway snapshots, one-user route lookup, empty state, record limits, and aggregation failure.
- [x] Implement immutable operational read models and a diagnostic query service under `diagnostic`; it depends only on registry/recorder abstractions and typed Broker properties and is not presented as a domain service.
- [x] Keep HTTP DTO conversion outside domain/service packages and return immutable values.
- [x] Run focused service tests and `./scripts/verify.sh architecture`.

### Task 6: Expose the isolated Broker HTTP API

- [x] Add typed `im.broker.management` properties and failing binding/validation tests for default loopback host, port range/conflict with `im.bolt.server.port`, and capacity bounds.
- [x] Replace `web-application-type: none` with a lightweight web application using `spring-boot-starter-web`; keep Bolt on `12200` and management HTTP on configurable `12201` without changing Bolt semantics.
- [x] Add MockMvc tests for every `/management` GET endpoint, validation errors, empty route results, record limit, DTO shape, and absence of write/all-route endpoints.
- [x] Add Controller, centralized advice, DTO, and declarative Transformer code under `interfaces/http/management`; these types only validate, delegate, and convert.
- [x] Verify application startup with both listeners and ensure the default HTTP bind address is loopback, never implicit `0.0.0.0`.

### Task 7: Publish and parse Nacos management metadata

- [x] Add focused tests proving Broker registration retains the Bolt discovery port and publishes `management-host`/`management-port` metadata without changing generic Nacos plugin behavior.
- [x] Extend generic Nacos registration support only if metadata cannot be configured safely in Broker application configuration; keep plugin code business-neutral. No plugin change was needed because application metadata configuration is sufficient.
- [x] Add `im-monitor` discovery tests for valid metadata, missing/invalid metadata, duplicate instances, and stable broker identity.
- [x] Implement a discovery adapter behind an `im-monitor` application-owned discovery interface, returning stable Broker identities and management endpoints without deriving them from Bolt ports.

### Task 8: Implement node client and cluster aggregation

- [x] Define an `im-monitor`-owned Broker management client interface and models; add local stub-server tests using the smallest suitable test dependency, without importing Broker server DTOs or implementations.
- [x] Test request timeout, malformed response, non-2xx response, error-summary truncation, and mapping of all node APIs.
- [x] Add aggregation tests for concurrent node queries, source Broker attribution, route de-duplication, partial failure as `UNREACHABLE`, last-query time, and total failure without forged healthy data.
- [x] Implement an explicitly owned bounded executor, request timeout isolation, immutable aggregation models, and application services; close executor resources through Spring lifecycle rather than test-only constructors.

### Task 9: Expose `im-monitor` APIs

- [x] Add MockMvc tests for all `/api` endpoints, parameter limits, partial failures, one-user-only connection lookup, and response models.
- [x] Implement thin controllers and centralized error conversion.
- [x] Add configuration tests for service name, request timeout, and local defaults.
- [x] Run `mvn -q -pl im-management/im-monitor -am test`.

### Task 10: Build the independently owned monitor UI

- [x] Create `im-management/im-monitor/ui` with Vue 3, TypeScript, Vite, Router, Vitest, Vue Test Utils, Element Plus, ECharts, and Axios; do not add Pinia without cross-page state requiring it and do not create an `im-admin` UI.
- [x] Add frontend tests for overview, Broker detail/list, Gateway list, user route query, Gossip/migration records, loading, empty, partial-failure, and unreachable states.
- [x] Implement typed API clients and pages; the browser calls only `/api` and never Broker addresses.
- [x] Configure Vite development proxy and Maven production build into `target/classes/static`; `node_modules`, coverage, and `dist` remain ignored and build output is never copied into source resources.
- [x] Verify `npm run test:unit`, `npm run typecheck`, production build, and final JAR static assets.

### Task 11: Documentation, complete verification, and closure

- [x] Update `ARCHITECTURE.md`, Broker/management READMEs, security/reliability guarantees, root module map, and the design document with its active plan link.
- [x] Run focused Broker and monitor tests, `./scripts/verify.sh behavior`, and `./scripts/verify.sh affected` while iterating.
- [x] Run `./scripts/verify.sh quick` and `./scripts/verify.sh full` before completion.
- [x] Review `git diff --check`, dependency boundaries, generated/untracked files, and ensure no UI build output is tracked.
- [x] Move this revised plan to completed and update indexes only after all gates pass.

## Rollout, compatibility, and rollback

- Defaults keep Bolt at `12200`; HTTP management uses loopback `12201`. Production must explicitly publish an operations-network address.
- Broker discovery port remains the Bolt port; management reachability comes only from Nacos metadata.
- Deploy Broker metadata/API before `im-monitor`; older Brokers without metadata appear unavailable/unsupported rather than being guessed.
- Recorder state is intentionally lost on restart. Removing `im-monitor` has no Broker routing impact; disabling the management HTTP endpoint/metadata rolls back exposure without data migration.

## Completion criteria

- Broker node endpoints and monitor aggregation match the approved design, including partial failure and no all-route query.
- Gossip/migration recording cannot change core behavior.
- Management and Bolt ports remain separated and Nacos metadata is explicit.
- UI is bundled only in `im-monitor.jar` and does not contact Brokers directly.
- Architecture, behavior, quick, and full verification pass.
