# Wrapper Boundary Types Implementation Plan

> **For agentic workers:** Execute sequentially in the current worktree. The user explicitly requested no worktree, no sub-agents, and no commit.

**Goal:** Prevent missing boundary values from silently becoming Java primitive defaults while preserving primitives for internal computations.

**Architecture:** Migrate the ordinary-user management `deleted` output chain from `boolean` to validated `Boolean`. Document the broader boundary rule as Review-only until historical Facade, CQRS, HTTP and domain snapshot models converge, without adding a path allowlist to the Java checker.

**Tech Stack:** Java 21 records, MapStruct, JUnit 5, repository Harness documentation.

---

### Task 1: Prove nullable-boundary rejection

- [x] Add tests requiring `Boolean` components and rejection of `null` across the user-management output chain.
- [x] Run focused tests and confirm RED against the existing primitive components.
- [x] Change the domain, CQRS, Facade, Admin domain and HTTP response records to validated `Boolean` values.
- [x] Update the MapStruct deletion conversion to return `Boolean`.
- [x] Run focused Account and Admin tests.

### Task 2: Record the Harness rule

- [x] Document when boundary types use wrappers and when internal code may use primitives.
- [x] Add the rule to the Harness matrix as Observed/Review-only and state its enforcement exit condition.
- [x] Confirm the Java checker is unchanged and no historical path whitelist is introduced.

### Task 3: Verify and archive

- [x] Run `./scripts/verify.sh quick`, `./scripts/verify.sh full`, and `git diff --check`.
- [x] Archive this plan and update plan indexes.

## Completion criteria

- Missing `deleted` values fail explicitly at every user-management output boundary.
- Internal boolean decisions remain primitive where null has no meaning.
- Documentation distinguishes wrapper-boundary semantics from internal primitive use.
- No broad checker or historical allowlist is added before the existing model baseline converges.

## Completion evidence

- Focused boundary tests first failed because `deleted` was `boolean`, then passed after the wrapper migration.
- Account and Admin module test suites passed.
- `./scripts/verify.sh quick` and `./scripts/verify.sh full` passed; the full gate completed with exit code 0.
- Final drift and whitespace checks passed after plan archival.
