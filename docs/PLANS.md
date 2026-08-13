# Execution Plan Policy

Execution plans turn an approved design into verifiable repository changes.

## Lifecycle

1. Create a plan in `docs/exec-plans/active` using `YYYY-MM-DD-<topic>.md`.
2. Link the design document or product specification that defines the intended behavior.
3. Describe affected modules, ordered implementation tasks, migration or compatibility concerns, and exact verification commands.
4. Update task status as work progresses. Record deviations that change the intended design.
5. When all completion criteria pass, move the plan unchanged to `docs/exec-plans/completed` and update relevant indexes.

## Required Sections

- Objective and non-goals
- Design/specification references
- Affected modules and ownership boundaries
- Ordered implementation tasks
- Test and verification strategy
- Rollout, compatibility, and rollback considerations when runtime behavior changes
- Completion criteria

Small, single-file maintenance changes do not require an execution plan. Cross-module work, migrations, new runtime components, and changes to public contracts do.
