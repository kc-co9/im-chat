# IM Chat Coding Agent Guide

## Audience And Source Priority

This file contains instructions for Coding Agents working in this repository. Human-facing project introduction, runtime setup, and module usage belong in `README.md` files.

Use documentation in this order:

1. Follow the nearest `AGENTS.md` for execution rules and local constraints.
2. Use root `ARCHITECTURE.md` for cross-module topology, boundaries, and data ownership, and the affected module's local `ARCHITECTURE.md` when present for internal topology and consistency boundaries. Use document indexes and status markers to identify the current approved design, product specification, and shared engineering rules; historical designs are context, not automatically authoritative.
3. When changing a business model or behavior, read each affected owning module `README.md` for local ubiquitous language, invariants, and current module facts. Use root and technical-module README files for project context, runtime behavior, configuration, and developer workflows.

README content provides context but does not override an applicable `AGENTS.md` rule. Do not move Agent-only workflow instructions into README files.

## Repository Map

- `im-common`: stable shared contracts and framework-neutral utilities.
- `im-plugin`: reusable infrastructure integrations and Spring Boot auto-configuration.
- `im-gateway`: HTTP and WebSocket ingress.
- `im-broker`: real-time routing, gateway coordination, and Gossip state synchronization.
- `im-service`: account, social, and message business services.
- `im-test`: repository-level Architecture Test and realtime E2E modules.
- `scripts`: deterministic verification and drift checks.

Read the nearest `AGENTS.md` before changing a module. Use module `README.md` files for runtime design and configuration details.

Repository documentation:

- `ARCHITECTURE.md`: current runtime topology, module boundaries, and data ownership.
- `im-gateway/ARCHITECTURE.md`, `im-broker/ARCHITECTURE.md`, `im-service/im-message/ARCHITECTURE.md`: local topology and consistency boundaries for complex runtime modules. Create a local architecture document only for non-trivial internal topology, multiple runtime responsibilities, an independent consistency model, or an important cross-boundary flow; use README for simple aggregators, Facades, SDKs, and single-purpose plugins.
- `docs/design-docs/index.md`: design status and navigation; historical decisions provide context unless marked current or approved for the task.
- `docs/exec-plans/active`: approved multi-step work currently in progress.
- `docs/PLANS.md`: execution-plan lifecycle and required content.
- `docs/RELIABILITY.md` and `docs/SECURITY.md`: cross-cutting operational guarantees.
- `docs/product-specs/index.md`: current product-specification status and navigation.
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
13. Keep AI-only instruction documents such as `AGENTS.md` in English. Write explanatory prose in developer-readable `README.md`, `ARCHITECTURE.md`, and `docs/**` in Chinese; code identifiers, commands, protocol names, established technical terms, and technical diagram labels may remain in English when clearer.
14. Run verification from the narrowest applicable layer toward broader gates. If an earlier layer fails, fix or explicitly attribute that failure before proceeding; a later passing gate does not override an earlier failure.
15. Keep each `scripts/*.sh` file contract accurate when behavior changes. Document non-obvious function parameters, output, isolation, algorithms, and failure propagation in Chinese; do not add line-by-line narration for self-explanatory shell statements.
16. Keep each commit as one complete logical change with a consistent verified repository state. Do not mix unrelated cleanup into the same commit.
17. An active execution plan may have at most one `active` task. Keep `PROGRESS.md` synchronized with the active plan and current task.

## Workflow

Before editing:

1. Inspect `git status --short`; preserve unrelated user changes.
2. Read root `PROGRESS.md`, then identify the requested scope and non-goals, affected owners, authoritative fact sources, and required environment or tool access.
3. Run `./scripts/verify.sh readiness` before substantial work; fix or attribute environment failures before implementation.
4. Read root `ARCHITECTURE.md`, each affected owning module's local `ARCHITECTURE.md` when present, nearest `AGENTS.md`, and the current design or product specification identified by its index or status marker. For business model or behavior changes, also read each affected owning module README.
5. When an active execution plan applies, resume its current task and recovery state before editing. Keep `PROGRESS.md` synchronized when the active plan or task changes. Small single-file maintenance remains exempt under `docs/PLANS.md`.
6. Search for existing patterns before introducing a new abstraction.

After editing:

1. Assess whether implementation or Review established a reusable engineering convention. If so, update the owning specification and `docs/references/HARNESS_GUIDE.md` in the same task, then add the narrowest reliable checker/test or explicitly mark the rule Review-only with its automation condition.
2. Do not create one-off class-name exceptions or weaken an existing rule to avoid updating the affected code.

```bash
./scripts/verify.sh quick
```

Before completion or commit:

```bash
./scripts/verify.sh clean
./scripts/verify.sh full
```

Use `./scripts/verify.sh affected` while iterating on a mixed worktree and `./scripts/verify.sh behavior` when changing routing, Gossip, delivery, or acknowledgement behavior.

Use `./scripts/verify.sh startup` when changing an application entry point or startup configuration. Use `./scripts/verify.sh cleanup` to scan Harness-owned temporary state; add `--apply` only when the allowlisted cleanup targets have been reviewed. Use `./scripts/verify.sh handoff` to produce the next-session report. Use `./scripts/verify.sh quality` to prepare or validate an independent AI Reviewer response; do not hand-edit semantic scores or treat `review_required` as passing.
Use `./scripts/verify.sh e2e` when changing WebSocket handshake, Gateway/Broker runtime wiring, Bolt routing, realtime frame transport, connection cleanup, or the E2E boundary itself. Treat verification evidence as current only when its HEAD/worktree fingerprint is fresh in the Harness report.

Use a focused Maven command while iterating, but do not replace the full verification gate with a narrower test run.

Only create a Git commit when the user explicitly requests it. Before staging or committing, read and follow `docs/references/GIT_GUIDE.md`; review the staged diff and do not include unrelated user changes.

## Definition Of Done

- Code compiles and relevant tests pass.
- Architecture tests and drift checks pass.
- New behavior has focused, deterministic tests at the appropriate boundary; regression tests fail before the corresponding fix.
- Public contracts, configuration, and module responsibilities are documented when changed.
- Reusable engineering conventions discovered during implementation or Review are reflected in the owning specification and Harness matrix; Review-only decisions state why they are not mechanically enforced yet.
- Quality snapshots combine current machine evidence with an independent AI Reviewer. A `Revise` or `Block` verdict remains visible in the snapshot and must not be silently downgraded.
- Active execution plans and affected documentation reflect the actual task and evidence state; plans move to `completed` only after verification passes.
- Prescribed and risk-applicable verification is reported with exact commands and actual results; when one cannot run, report its specific reason and evidence gap.
- Remove or classify task-created ephemeral and untracked debug or temporary artifacts. Standard ignored verification/build output such as `target/` may remain.
- Preserve unrelated user changes and exclude them from staging, commits, and the task-attributed change list.
- Keep the final response proportional: list applicable evidence, affected plan/document state, and classified residual risks; `none` is valid, and small maintenance may be concise.

Use `PROGRESS.md` for the current repository status, `docs/references/HARNESS_GUIDE.md` for failure attribution and rule lifecycle, and `docs/PLANS.md` for Sprint Contract, detailed task state, recovery, and plan lifecycle.

## Commit Messages

Follow `docs/references/GIT_GUIDE.md`. Use a concise Chinese verb-led title followed by numbered paragraphs describing complete change groups and their intent.
