# SQL Harness Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add repository-wide SQL conventions and deterministic drift checks for root DDL, MyBatis Mapper XML, and Java annotation SQL.

**Architecture:** Keep semantic guidance in `SQL_GUIDE.md` and implement low-noise checks in a focused Bash script. Exercise the checker through isolated fixtures, then call it from the existing drift and Harness test entry points.

**Tech Stack:** Bash, ripgrep, Ruby standard library where structured multiline extraction is needed, existing Harness scripts.

---

## Objective and non-goals

Implement the rules in [SQL Guide](../../references/SQL_GUIDE.md). Do not connect to MySQL, execute SQL, infer index quality, or reject dynamic SQL that cannot be reliably reconstructed. Follow-up governance now rejects `SELECT *` and `SELECT table.*` while allowing `COUNT(*)`.

## Affected files and ownership

- Create `scripts/check-sql.sh`: production SQL discovery, normalization, rule evaluation, and grouped diagnostics.
- Create `scripts/test-sql-harness.sh`: temporary positive and negative fixtures.
- Modify `scripts/check-drift.sh`: invoke the SQL checker without duplicating its rules.
- Modify `scripts/test-harness.sh`: invoke SQL Harness self-tests.
- Modify `docs/references/HARNESS_GUIDE.md`, `docs/references/SQL_GUIDE.md`, and indexes only when implementation evidence requires clarification.

### Task 1: Observe the repository baseline

- [x] Record all root `sql/*.sql`, production `src/main/resources/**/*.sql`, Mapper XML files, and MyBatis annotation usages with `rg --files`/`rg -n`.
- [x] Classify each proposed rule as currently passing or requiring a narrowly documented exception; do not weaken a rule to hide an existing violation.
- [x] Run `./scripts/check-drift.sh` and retain the baseline result in the plan progress notes.

Baseline evidence: production DDL exists only at `sql/ddl.sql`; Mapper SQL is currently XML-based and contains no `${...}` or annotation SQL; the pre-change drift gate passed on 2026-08-13. No compatibility exception is required beyond the documented `sql/ddl.sql` `DROP TABLE IF EXISTS` allowance.

### Task 2: Build failing SQL Harness fixtures

- [x] Create `scripts/test-sql-harness.sh` with a temporary minimal repository and a helper that asserts checker success or failure plus diagnostic text.
- [x] Add passing fixtures for valid DDL, Mapper XML binding, annotation single-string/text-block SQL, explicit projection fields, `COUNT(*)`, and `sql/ddl.sql` `DROP TABLE IF EXISTS`.
- [x] Add failing fixtures for module resource `.sql`, missing primary key/InnoDB/table comment, invalid snake_case/index prefixes, `TRUNCATE`, `DROP DATABASE`, `${...}`, and reliably static `UPDATE`/`DELETE` without `WHERE`.
- [x] Add separate `DROP TABLE` failures for root `sql/*.sql` other than `ddl.sql`, Mapper XML plain text/CDATA, and Java annotation single-string/text-block SQL.
- [x] Add a failing `sql/ddl.sql` fixture for bare or multiline/case-varied `DROP TABLE` without `IF EXISTS`.
- [x] Add failing location fixtures for MyBatis SQL annotations outside an `infrastructure` mapper package.
- [x] Place passing annotation fixtures under a valid `infrastructure/mapper` package. Add non-regression fixtures proving Java comments/non-SQL strings containing `${...}` do not fail, and dynamic XML plus annotation arrays/constants/concatenation are not falsely rejected for the Review-only no-`WHERE` rule; add `${...}` failures inside unsupported annotation expressions because lexical substitution remains forbidden.
- [x] Run `bash scripts/test-sql-harness.sh`; expected result before implementation: failure because `scripts/check-sql.sh` is absent or does not enforce fixtures.

### Task 3: Implement the focused checker

- [x] Create `scripts/check-sql.sh` with `set -euo pipefail`, root resolution, grouped diagnostics, and one final nonzero exit when any group fails.
- [x] Discover root DDL non-recursively from `sql/*.sql`; reject any production `*/src/main/resources/**/*.sql`; exclude `src/test`, `target`, and fixtures.
- [x] Normalize keyword matching case-insensitively across lines while preserving file/statement context in errors.
- [x] Enforce DDL structure, naming, and dangerous statement rules from `SQL_GUIDE.md`; enforce `DROP TABLE` across every root SQL file, Mapper XML statement, and supported Java annotation statement, allowing only `sql/ddl.sql` with `IF EXISTS`.
- [x] Extract MyBatis annotation values before checking `${...}` so Java comments and unrelated strings are not rejected. For arrays/constants/concatenation that cannot be reconstructed, reject `${...}` only when it occurs lexically inside the annotation expression.
- [x] Reject MyBatis SQL annotations outside Java paths/packages containing both `infrastructure` and `mapper`; cover all four `@Select`, `@Insert`, `@Update`, and `@Delete` forms.
- [x] Check no-`WHERE` only for complete Mapper XML statements and Java annotation single-string/text-block statements; leave dynamic and reconstructed forms to Review.
- [x] Run `bash scripts/test-sql-harness.sh`; expected: all positive and negative fixture assertions pass.

### Task 4: Integrate the gate

- [x] Add a single call to `scripts/check-sql.sh` from `scripts/check-drift.sh` and preserve useful failure output.
- [x] Add `scripts/test-sql-harness.sh` to `scripts/test-harness.sh`.
- [x] Run `bash scripts/test-harness.sh`; expected: `Harness script tests passed.`
- [x] Run `./scripts/check-drift.sh`; expected: `Drift checks passed.`

### Task 5: Verify and close

- [x] Run `./scripts/verify.sh quick`.
- [x] Run `./scripts/verify.sh full`.
- [x] Review `git diff --check`, the complete diff, and generated/untracked output.
- [x] Move this plan unchanged to `docs/exec-plans/completed/2026-08-13-sql-harness.md` only after both gates pass; update active/completed indexes.

## Rollout and rollback

The checker changes repository validation only and has no runtime impact. If a false positive appears, record it in `docs/feedback/HARNESS_FEEDBACK.md`, add a reproducing fixture, then narrow the parser or rule. Do not bypass the checker globally.

## Completion criteria

- All documented low-noise SQL rules have positive and negative fixture evidence.
- `SELECT *` and `SELECT table.*` are rejected; `COUNT(*)` remains accepted.
- Review-only SQL forms are not falsely rejected.
- quick/full verification passes without network or database access.
