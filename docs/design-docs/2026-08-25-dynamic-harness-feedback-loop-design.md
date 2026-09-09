# Dynamic Harness Feedback Loop Design

## Context

The repository already requires architecture and specification changes to update Harness documentation and sensors. However, the Agent workflow does not require an explicit Harness assessment before completion. As a result, reusable conclusions confirmed during review can remain local to one implementation instead of becoming guidance for later changes.

The recent query-condition refactor provides a concrete example: an optional internal query filter is represented by a non-null `Optional<T>`, while required filters remain direct values. This is a reusable structural convention, but the repository still contains a query condition that has not adopted it.

## Decision

Harness is maintained as a feedback loop rather than a static collection of checks:

1. Every reusable engineering convention confirmed during implementation or review must receive a Harness assessment in the same task.
2. The assessment identifies the owning specification and chooses the narrowest reliable feedback mechanism: static checker, ArchUnit, focused test, runtime signal, or Review.
3. A rule is only described as automated after it has diagnostics and positive, negative, and false-positive fixtures where applicable.
4. Rules that cannot yet be enforced accurately, or whose repository baseline has not converged, are documented as Review-only. Their automation is reconsidered when the blocking condition is removed.
5. Local naming choices and one-off implementation details do not enter Harness unless they express a reusable boundary or recurring failure mode.

The Agent workflow and Definition of Done will include this assessment explicitly so that completing production code without considering corresponding Harness changes is no longer valid.

## Query-condition convention

Use naming to distinguish an application request from an internal repository filter:

- `*Query` represents a CQRS query intent accepted by an application service.
- `*QueryCondition` represents filter conditions passed to a Repository or persistence query.
- Do not use the ambiguous `*Criteria`, bare `*Condition`, or persistence-level `*Query` names for newly created filter objects.
- Pagination is a separate concern and is passed through `Paging`; it is not embedded in `*QueryCondition`.
- Domain and infrastructure conditions may use the same `QueryCondition` suffix, while package ownership and Transformer boundaries distinguish their models.

Internal filter-only `*QueryCondition` records use non-null `Optional<T>` components for filters that may be absent. Required filters continue to use direct types. Nullable source values are normalized to `Optional` at a boundary such as a Transformer or Repository adapter, and an `Optional` component itself must never be `null`.

This convention does not apply to public request/response contracts, persistence entities, domain aggregates, or types whose serialization and framework binding require nullable properties.

The naming and optional-filter conventions remain Review-only after the persistence query conditions
were converged. No class-name exception or temporary checker whitelist is required. These rules can
move from Observed to Enforced only when a low-noise structural check and representative fixtures are
available.

## Documentation changes

- `docs/references/CODING_GUIDE.md` owns the query-condition structure rule.
- `docs/references/HARNESS_GUIDE.md` owns the mandatory assessment flow, lifecycle status, and feedback matrix.
- Root `AGENTS.md` makes Harness assessment an editing step and completion criterion.

No production code or checker behavior changes as part of this documentation update.

## Verification

- Documentation links and drift checks must pass.
- Quick and full verification remain the completion gates.
- The Harness matrix must identify the query-condition convention as Review-only and must not imply automatic enforcement.
