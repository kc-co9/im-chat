# IM Test Coding Agent Guide

Apply the root `AGENTS.md` first. This subtree contains repository-level test modules only and must not own production behavior.

## Boundaries

- Keep unit and integration tests beside their owning production modules.
- Put deterministic dependency and package rules in `im-architecture-test`.
- Put cross-module runtime journeys using real process or network boundaries in `im-e2e-test`.
- State every replaced boundary explicitly. Do not describe a test as proving MySQL, Redis, Nacos, or a remote service when that dependency is substituted.
- Use bounded synchronization or Awaitility for asynchronous runtime state. Never use real sleeps.
- A new repository-level test capability requires a focused `verify.sh` mode, documentation, and impact mapping.
- Load-test fixtures remain deterministic and dependency-explicit. Only `verify.sh load-test-smoke` and the explicit load-test runner may create real users, connections, messages, or runtime evidence.
- Load-test completion evidence must bind the full worktree observed during execution and the dedicated load-test scope. Execution-state documents may be excluded from scope freshness; production code, test code, configuration, scripts, and stable documentation may not.

## Verification

Run the focused test mode while iterating, then follow the root completion gates.
