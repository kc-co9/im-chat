# IM Chat Coding Agent Guide

## Audience And Source Priority

This file contains instructions for Coding Agents working in this repository. Human-facing project introduction, runtime setup, and module usage belong in `README.md` files.

Use documentation in this order:

1. Follow the nearest `AGENTS.md` for execution rules and local constraints.
2. Use `ARCHITECTURE.md` and `docs/**` as shared engineering sources of truth.
3. Read root and module `README.md` files for business context, runtime behavior, configuration, and developer workflows.

README content provides context but does not override an applicable `AGENTS.md` rule. Do not move Agent-only workflow instructions into README files.

## Repository Map

- `im-common`: stable shared contracts and framework-neutral utilities.
- `im-plugin`: reusable infrastructure integrations and Spring Boot auto-configuration.
- `im-gateway`: HTTP and WebSocket ingress.
- `im-broker`: real-time routing, gateway coordination, and Gossip state synchronization.
- `im-service`: account, social, and message business services.
- `im-architecture`: executable dependency and package-boundary rules.
- `scripts`: deterministic verification and drift checks.

Read the nearest `AGENTS.md` before changing a module. Use module `README.md` files for runtime design and configuration details.

Repository documentation:

- `ARCHITECTURE.md`: current runtime topology, module boundaries, and data ownership.
- `docs/design-docs`: accepted and historical design decisions.
- `docs/exec-plans/active`: approved multi-step work currently in progress.
- `docs/PLANS.md`: execution-plan lifecycle and required content.
- `docs/RELIABILITY.md` and `docs/SECURITY.md`: cross-cutting operational guarantees.
- `docs/product-specs/im-realtime-approved-scenarios.md`: reviewed realtime behavior expectations.
- `docs/references/CODE_REVIEW_GUIDE.md`: findings-first code review procedure using a context independent from implementation.
- `docs/references/HARNESS_GUIDE.md`: approved topologies, rule lifecycle, ownership, implementation examples, feedback tracking, and removal criteria.
- `docs/references/UNIT_TEST_GUIDE.md`: test boundaries, assertions, determinism, and review criteria.
- `docs/references/CODING_GUIDE.md`: Java style, layer responsibilities, and project component usage.

## Required Rules

1. Preserve module ownership. Shared modules must not depend on business or runtime modules.
2. Domain packages must not depend on interfaces, infrastructure, configuration, lifecycle, or Spring framework types.
3. Facade and SDK modules contain public contracts only; they must not depend on their server implementations.
4. Plugin modules provide generic capabilities and must not depend on Broker, Gateway, or business-service packages.
5. Controllers and RPC handlers delegate to application or domain services; they do not reach into concrete persistence implementations.
6. Transformer interfaces annotated with MapStruct must use generated mapping for declarative mappings. Do not keep a `@Mapper` annotation on an interface whose methods are all handwritten defaults.
7. Prefer explicit Java types over `var`. Keep public method names action-oriented and consistent with the owning abstraction.
8. Do not add unused compatibility constructors, no-op production implementations used only by tests, or wrapper methods without a caller.
9. Configuration belongs in typed `@ConfigurationProperties` when multiple related values exist. Local defaults remain usable and Nacos may override them.
10. Never weaken a verification rule merely to make a change pass. Fix the violation or document and narrowly scope an intentional exception.
11. Tests must prove observable behavior with meaningful assertions and deterministic inputs. Follow `docs/references/UNIT_TEST_GUIDE.md`.
12. New production code follows `docs/references/CODING_GUIDE.md`; prefer existing project components and boundaries over new infrastructure.

## Workflow

Before editing:

1. Inspect `git status --short`; preserve unrelated user changes.
2. Read `ARCHITECTURE.md`, the owning module's `AGENTS.md`, and its `README.md`.
3. Check `docs/exec-plans/active` and the relevant design or product specification.
4. Search for existing patterns before introducing a new abstraction.

After editing:

```bash
./scripts/verify.sh quick
```

Before completion or commit:

```bash
./scripts/verify.sh full
```

Use `./scripts/verify.sh affected` while iterating on a mixed worktree and `./scripts/verify.sh behavior` when changing routing, Gossip, delivery, or acknowledgement behavior.

Use a focused Maven command while iterating, but do not replace the full verification gate with a narrower test run.

Only create a Git commit when the user explicitly requests it. Before staging or committing, read and follow `docs/references/GIT_GUIDE.md`; review the staged diff and do not include unrelated user changes.

## Definition Of Done

- Code compiles and relevant tests pass.
- Architecture tests and drift checks pass.
- New behavior has focused, deterministic tests at the appropriate boundary; regression tests fail before the corresponding fix.
- Public contracts, configuration, and module responsibilities are documented when changed.
- Active execution plans are updated during implementation and moved to `completed` only after verification passes.
- No generated output, IDE metadata, logs, credentials, or local environment values are committed.
- The final change contains no unrelated cleanup.

## Commit Messages

Follow `docs/references/GIT_GUIDE.md`. Use a concise Chinese verb-led title followed by numbered paragraphs describing complete change groups and their intent.
