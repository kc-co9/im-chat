# IM Admin Security Context Convergence Plan

**Goal:** Converge Admin authentication and authorization on Spring Security while keeping audit metadata independent from business Commands.

**Architecture:** Spring Security owns administrator identity and authorities. `@AdminAudited` owns only audit semantics. Alibaba TTL propagates only bounded non-security request metadata through explicitly wrapped executors.

## Tasks

- [x] Manage Alibaba TTL centrally and declare it only in `im-admin`.
- [x] Replace custom permission annotations and interceptors with Spring Security Authentication and `@PreAuthorize`.
- [x] Remove audit context from Commands and remove the HTTP audit request factory.
- [x] Restrict the TTL request context to client address and User-Agent, with propagation and cleanup tests.
- [x] Update audit aspect tests, endpoint inventory tests, Coding Guide, Harness matrix, module README and design document.
- [x] Run focused module tests, architecture checks, affected/quick/full verification and `git diff --check`.

## Verification evidence

- `mvn -q -pl im-management/im-admin -am test`
- `mvn -q -pl im-management/im-admin,im-architecture -am test`
- `./scripts/verify.sh affected`
- `./scripts/verify.sh quick`
- `./scripts/verify.sh full`
- `git diff --check`

## Constraints

- Do not propagate administrator identity through TTL.
- Do not duplicate Spring Security permission expressions in `@AdminAudited`.
- Do not add a generic context plugin or TTL Java Agent.
- Do not commit without explicit user authorization.
