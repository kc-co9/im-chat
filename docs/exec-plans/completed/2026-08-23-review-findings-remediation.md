# Review Findings Remediation Implementation Plan

> **For agentic workers:** Execute in the current workspace without subagents, worktrees or commits. Follow TDD and update checkboxes as each step completes.

**Goal:** Fix the five confirmed cross-component behavior defects and restore useful DDD, Harness and architecture context in the root README.

**Architecture:** Keep locking in the application boundary with a canonical `userId` key, align Session storage lifetime with Refresh Token lifetime, and reuse existing domain and Broker owner-routing patterns. Keep README explanations useful but subordinate to the stable architecture and reference documents.

**Tech Stack:** Java 21, Spring Boot, JetCache, Redisson lock plugin, Dubbo/Bolt, JUnit 5, Mockito, AssertJ, Maven and repository Harness scripts.

---

### Task 1: Canonical Session write lock

- [x] Add failing `SessionAppServiceTest` assertions proving sign-in, refresh and sign-out execute with `SESSION_WRITE:userId`.
- [x] Run the focused account test and confirm RED because `DistributedLockTemplate` is not used.
- [x] Inject `DistributedLockTemplate`, remove incompatible method annotations and execute all Session mutations under the canonical key.
- [x] Update Account Bean wiring and re-run focused tests to GREEN.

### Task 2: Align Session and Refresh lifetime

- [x] Add a failing Cache configuration test proving Session TTL follows `JwtProperties.refreshTokenTtl`.
- [x] Run the focused test and confirm the current one-day TTL fails.
- [x] Make `CacheConfig.userSessionCache` use typed JWT configuration and re-run to GREEN.

### Task 3: Make Refresh endpoint public end to end

- [x] Add a failing `UserContextInterceptorTest` for `/user/refreshToken` without trusted headers.
- [x] Run the focused plugin test and confirm RED with HTTP 401.
- [x] Add the path to the public endpoint set and re-run to GREEN.

### Task 4: Clear private-chat unread state on open

- [x] Add a failing application/domain assertion for opening a private chat with unread messages.
- [x] Run the focused message test and confirm unread remains non-zero.
- [x] Add `ImPrivateChat.readToLatest()` and invoke it from `openPrivateChat`.
- [x] Re-run the focused test to GREEN.

### Task 5: Forward close controls to owner Broker

- [x] Add a failing remote-owner `ConnectionCloseHandlerTest`.
- [x] Run the focused Broker test and confirm the request is not forwarded.
- [x] Add `BrokerPeerClient.closeConnections` and owner forwarding in the Handler.
- [x] Update local-owner fixtures and re-run Broker tests to GREEN.

### Task 6: Restore root README context

- [x] Add concise DDD dependency/layer responsibilities, Harness enforcement levels and current data-ownership explanations.
- [x] Keep detailed rules linked to their source documents and avoid duplicating full reference text.

### Task 7: Verify and close

- [x] Run focused affected Maven suites.
- [x] Run `./scripts/verify.sh behavior`.
- [x] Run `./scripts/verify.sh quick`.
- [x] Run `./scripts/verify.sh full`.
- [x] Run `git diff --check` and review the complete diff.
- [x] Move this plan to `docs/exec-plans/completed` and update plan indexes.
