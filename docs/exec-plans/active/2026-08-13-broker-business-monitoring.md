# Broker Business Monitoring Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide isolated Broker node diagnostics and an independent `im-monitor` application that discovers, aggregates, and displays cluster business state.

**Architecture:** Add read-only HTTP diagnostics beside the Broker Bolt listener, backed by registry queries and bounded in-memory execution records. Add a separate `im-management/im-monitor` Spring Boot application that discovers Broker management endpoints through Nacos metadata, isolates node failures, exposes cluster APIs, and serves its own Vue UI.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring MVC, Nacos Discovery, Maven, JUnit 5, AssertJ, Vue 3, TypeScript, Vite, Element Plus, ECharts, Axios.

---

## Objective and non-goals

Implement [Broker business monitoring design](../../design-docs/2026-08-13-broker-business-monitoring-design.md). The first version is read-only, keeps at most 100 diagnostic records by default, does not expose all user routes, and does not introduce persistence, Prometheus/Grafana, RBAC, or `im-admin` business behavior.

## Affected modules and ownership

- `im-broker/im-broker-server`: registry read APIs, bounded recorders, sampling points, management service/DTO/controller, HTTP/configuration, and Nacos management metadata.
- `im-management`: new Maven aggregator.
- `im-management/im-monitor`: discovery/client/application aggregation/API and independently owned Vue UI.
- `im-management/im-admin`: aggregator placeholder only; no runtime/UI implementation in this plan unless Maven aggregation requires a minimal POM.
- `im-architecture`, root `pom.xml`, documentation and verification impact mapping: new module and dependency-boundary enforcement.

### Task 1: Lock public behavior and module skeleton

- [ ] Add failing architecture/module tests for root `im-management`, `im-monitor` isolation, and prohibition on `im-monitor` depending on Broker server implementations.
- [ ] Add root and management POM modules with the minimum dependencies; add nearest `AGENTS.md` and README ownership documentation before production classes.
- [ ] Add minimal `im-monitor` application context test and run `mvn -q -pl im-management/im-monitor -am test`; expected initial failure, then pass after skeleton wiring.
- [ ] Update affected-module resolution tests so management changes select the correct reactor modules.

### Task 2: Add registry read capabilities with TDD

- [ ] Extend `GatewayRegistry` with `list()` and `ConnectionRegistry` with `count()` only after adding failing concurrent behavior tests to their in-memory implementations.
- [ ] Implement immutable snapshot results without exposing internal collections.
- [ ] Run `mvn -q -pl im-broker/im-broker-server -am -Dtest='*RegistryTest' -DfailIfNoTests=false test` and architecture verification.

### Task 3: Add bounded diagnostic recorders

- [ ] Define transport-neutral Gossip and migration record models/statuses and recorder interfaces outside lifecycle/HTTP packages.
- [ ] Add failing tests for capacity eviction, newest-first limit, maximum limit, counters/timestamps, concurrent writes, error truncation, and recorder-failure isolation.
- [ ] Implement separate thread-safe bounded in-memory recorders configured by `history-capacity`.
- [ ] Run focused recorder tests; expected: pass with deterministic clocks/durations and no real waits.

### Task 4: Instrument Gossip and migration execution

- [ ] Add failing `BrokerGossipLifecycleTest` cases for success/failure records and recorder exceptions not changing synchronization behavior.
- [ ] Add failing `BrokerConnectionServiceTest` cases for per-target migration success/failure records and unchanged original exception semantics.
- [ ] Inject clocks/timers or explicit duration suppliers where needed; do not use wall-clock sleeps.
- [ ] Implement minimal sampling at the existing execution boundaries and run focused lifecycle/service tests.
- [ ] Run `./scripts/verify.sh behavior` because Gossip and connection migration behavior is touched.

### Task 5: Build the Broker diagnostic application service

- [ ] Add failing service tests for overview counts, instance identity/uptime, Broker/Gateway snapshots, one-user route lookup, empty state, record limits, and aggregation failure.
- [ ] Implement management query models and a service depending only on registry/recorder abstractions and typed Broker properties.
- [ ] Keep HTTP DTO conversion outside domain/service packages and return immutable values.
- [ ] Run focused service tests and `./scripts/verify.sh architecture`.

### Task 6: Expose the isolated Broker HTTP API

- [ ] Add typed `im.broker.management` properties and failing binding/validation tests for default loopback host, port range/conflict, and capacity bounds.
- [ ] Replace `web-application-type: none` with a lightweight web application using `spring-boot-starter-web`; keep Bolt on `12200` and management HTTP on configurable `12201` without changing Bolt semantics.
- [ ] Add MockMvc tests for every `/management` GET endpoint, validation errors, empty route results, record limit, DTO shape, and absence of write/all-route endpoints.
- [ ] Add Controller/advice/DTO code that only validates, delegates, and converts.
- [ ] Verify application startup with both listeners and ensure the default HTTP bind address is loopback, never implicit `0.0.0.0`.

### Task 7: Publish and parse Nacos management metadata

- [ ] Add focused tests proving Broker registration retains the Bolt discovery port and publishes explicit management host/port metadata.
- [ ] Extend generic Nacos registration support only if metadata cannot be configured safely in Broker application configuration; keep plugin code business-neutral.
- [ ] Add `im-monitor` discovery tests for valid metadata, missing/invalid metadata, duplicate instances, and stable broker identity.
- [ ] Implement discovery adapter returning management endpoints without deriving them from Bolt ports.

### Task 8: Implement node client and cluster aggregation

- [ ] Define a Broker management client port and add WireMock/MockWebServer-style tests using existing project test dependencies where possible; avoid adding infrastructure until needed.
- [ ] Test request timeout, malformed response, non-2xx response, error-summary truncation, and mapping of all node APIs.
- [ ] Add aggregation tests for concurrent node queries, source Broker attribution, route de-duplication, partial failure as `UNREACHABLE`, last-query time, and total failure without forged healthy data.
- [ ] Implement bounded concurrency, timeout isolation, aggregation models, and application services.

### Task 9: Expose `im-monitor` APIs

- [ ] Add MockMvc tests for all `/api` endpoints, parameter limits, partial failures, one-user-only connection lookup, and response models.
- [ ] Implement thin controllers and centralized error conversion.
- [ ] Add configuration tests for service name, request timeout, and local defaults.
- [ ] Run `mvn -q -pl im-management/im-monitor -am test`.

### Task 10: Build the independently owned monitor UI

- [ ] Create `im-management/im-monitor/ui` with Vue 3, TypeScript, Vite, Router, Element Plus, ECharts, and Axios; do not add Pinia without cross-page state requiring it.
- [ ] Add frontend tests for overview, Broker detail/list, Gateway list, user route query, Gossip/migration records, loading, empty, partial-failure, and unreachable states.
- [ ] Implement typed API clients and pages; the browser calls only `/api` and never Broker addresses.
- [ ] Configure Vite development proxy and Maven production build into `target/classes/static`; never copy build output into source resources.
- [ ] Verify `npm run test:unit`, `npm run typecheck`, production build, and final JAR static assets.

### Task 11: Documentation, complete verification, and closure

- [ ] Update `ARCHITECTURE.md`, Broker/management READMEs, security/reliability guarantees, root module map, and the design document with its active plan link.
- [ ] Run focused Broker and monitor tests, `./scripts/verify.sh behavior`, and `./scripts/verify.sh affected` while iterating.
- [ ] Run `./scripts/verify.sh quick` and `./scripts/verify.sh full` before completion.
- [ ] Review `git diff --check`, dependency boundaries, generated/untracked files, and ensure no UI build output is tracked.
- [ ] Move this plan unchanged to completed and update indexes only after all gates pass.

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
