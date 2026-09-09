# IAM Security Hardening Implementation Plan

> **For agentic workers:** REQUIRED: Use `superpowers:executing-plans` to implement this plan. The user explicitly prohibited subagents, worktrees, and Git commits for this task.

**Goal:** Correct IAM audience and Introspection semantics, make one-time OAuth credentials atomic, and close SSO and administrator-login security gaps.

**Architecture:** OAuth clients keep separate owner and target application identities. IAM emits the owner as `appKey` and the target as standard `aud`; Introspection authorizes the resource server by `aud`. Authorization Code consumption and Refresh Token rotation use digest-based conditional persistence updates. SSO expiry and unknown-account authentication fail closed in the current request without introducing compatibility paths.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Authorization Server, Spring Security, MyBatis-Plus/MySQL, MapStruct, JUnit 5, Mockito.

---

## Design source

- [IAM security hardening design](../../design-docs/2026-09-02-iam-security-hardening-design.md)
- [IAM runtime and OAuth guide](../../../im-management/im-iam/README.md)
- [Security guarantees](../../SECURITY.md)
- [Harness lifecycle](../../references/HARNESS_GUIDE.md)

## Task 1: Model OAuth Token audience

**Files:** OAuth client domain/CQRS/HTTP/persistence models, IAM schema and migration, matching transformers and tests.

- [x] Add `audienceAppId` as a required OAuth client relationship, distinct from owner `appId`.
- [x] Require the target application to exist and be active during client registration and RegisteredClient loading.
- [x] Persist `audience_app_id` as an indexed business-ID reference and update all development fixtures.
- [x] Write failing domain/application/persistence tests before production changes.

## Task 2: Correct claims and Introspection authorization

**Files:** IAM token claim customizer, Introspection success handler/configuration, Audit HTTP/RPC authentication, focused tests.

- [x] Emit producer identity as `appKey` and the target application's stable key as standard `aud`.
- [x] Resolve both caller and Token ownership from IAM repositories; do not trust request claims for identity.
- [x] Return `active=false` when the Introspection caller's owning application does not match `aud`.
- [x] Make Audit HTTP and RPC consumers use `appKey` consistently, without an `appId` alias.
- [x] Prove cross-client same-audience success and unrelated-audience rejection.

## Task 3: Make Authorization Code and Refresh Token consumption atomic

**Files:** OAuth authorization adapter, MyBatis authorization service/entity tests.

- [x] Carry the matched credential digest as internal restored-authorization state.
- [x] Consume an Authorization Code only when its digest still matches, it is unused, and the authorization is active.
- [x] Rotate a Refresh Token only when its previous digest still matches and the authorization is active.
- [x] On replay/CAS failure, revoke the authorization and raise OAuth `invalid_grant`.
- [x] Prove that concurrent or repeated consumption has one winner.

## Task 4: Enforce SSO absolute expiry in the current request

**Files:** SSO filter, Spring Security filter-chain configuration and tests.

- [x] Run the absolute-lifetime filter before session SecurityContext loading.
- [x] Invalidate the expired session and clear the current `SecurityContext`.
- [x] Prove downstream code cannot observe the expired authentication.

## Task 5: Equalize unknown-account login work

**Files:** password service implementation/contract, administrator authentication application service and tests.

- [x] Execute one BCrypt verification for both known and unknown administrator emails.
- [x] Keep one external authentication-failure response and existing known-account restriction behavior.
- [x] Prove unknown-email authentication invokes the dummy verification path exactly once.

## Task 6: Documentation, Harness and verification

- [x] Update `docs/SECURITY.md`, the IAM main plan, and Harness feedback matrix with reusable security boundaries.
- [x] Add only deterministic low-noise checks; keep semantic OAuth rules in focused behavior tests.
- [x] Run focused module tests while iterating.
- [x] Run `./scripts/verify.sh quick`.
- [x] Run `./scripts/verify.sh full` before completion.
- [x] Move this plan to `completed` only after every required verification passes.

No commit step is included because the user explicitly requested that this work remain uncommitted.
