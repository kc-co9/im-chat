# Centralized Session Authentication Implementation Plan

> **最终偏差摘要（2026-08-23）**
>
> 本计划保留实施时的任务与验证证据，不按最终代码重写。最终实现取消 local profile 临时 JWT 密钥，启用 JWT 时必须提供显式强密钥；认证成功 DTO 不包含 `valid`，失败抛出统一认证异常；中间的 `SessionCredentialService`、`Fingerprint` 等命名最终收敛为 `SessionService`、`SessionTokenCodec`、`RefreshFingerprint` 和 `SessionEstablishment`。当前契约和配置以设计文档的最终实现说明、模块 README 和源码为准。

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace local permanent JWT authentication with account-owned online validation, two-hour Access Tokens, rotating 30-day Refresh Tokens, immediate logout/replacement revocation, and single-device WebSocket convergence.

**Architecture:** Keep generic JWT encoding in `im-session`, but make `im-account-server` the only signer and authentication authority. HTTP and WS Gateways call the account Facade asynchronously and fail closed; account Session state owns `sessionVersion` and the current Refresh Token digest. Session replacement events travel through Broker routing only to accelerate closure of already-established WS connections.

**Tech Stack:** Java 21, Spring Boot/WebFlux, Dubbo, Netty, Redis/JetCache/Redisson, JJWT, AssertJ/JUnit 5, existing Bolt/Broker SDK.

---

## Objective and non-goals

Implement [Centralized Session Authentication Design](../../design-docs/2026-08-14-centralized-session-authentication-design.md).

This plan does not add multi-device sessions, Gateway-side Session storage, authentication-result caching, silent token renewal on ordinary responses, or compatibility wrappers without a verified caller. HTTP request authentication and WS handshake authentication must remain fail-closed when account validation is unavailable.

## Affected modules and ownership

- `im-plugin/im-session`: transport-neutral JWT claims, codec, typed configuration, trusted session-version context.
- `im-service/im-account/im-account-facade`: stable validation contracts only.
- `im-service/im-account/im-account-server`: signing, Session state, refresh rotation, login/logout/refresh behavior and control-event publishing.
- `im-gateway/im-http-gateway`: asynchronous online validation and trusted header rebuilding.
- `im-gateway/im-ws-gateway/im-ws-gateway-server`: asynchronous handshake validation and local session-version connection metadata.
- `im-broker/im-broker-sdk` and `im-broker/im-broker-server`: session-close control routing without owning Token or connection IDs.
- Root docs: authentication guarantees, public contract and rollout notes.

Do not move account persistence into Gateway or Broker. Broker changes must remain compatible with the active Broker monitoring plan and must not add message content, Token values, or high-cardinality authentication metrics.

## Ordered tasks

### Task 1: Establish deterministic JWT primitives

**Files:**

- Create `im-plugin/im-session/src/main/java/com/co/kc/imchat/plugin/session/token/model/TokenType.java`
- Create `im-plugin/im-session/src/main/java/com/co/kc/imchat/plugin/session/token/model/TokenClaims.java`
- Create `im-plugin/im-session/src/main/java/com/co/kc/imchat/plugin/session/token/model/DecodedToken.java`
- Create `im-plugin/im-session/src/main/java/com/co/kc/imchat/plugin/session/token/codec/JwtTokenCodec.java`
- Create `im-plugin/im-session/src/main/java/com/co/kc/imchat/plugin/session/properties/JwtProperties.java`
- Modify `im-plugin/im-session/src/main/java/com/co/kc/imchat/plugin/session/ImSessionAutoConfiguration.java`
- Retain temporarily, then remove in Task 8: `token/codec/JwtTokenService.java`, `token/spi/TokenService.java`, and `token/model/TokenDTO.java`
- Rewrite `im-plugin/im-session/src/test/java/com/co/kc/imchat/plugin/session/token/codec/JwtTokenCodecTest.java` as codec behavior tests
- Modify `im-plugin/im-session/src/test/java/com/co/kc/imchat/plugin/session/ImSessionAutoConfigurationTest.java`
- Modify `im-plugin/im-session/README.md`

- [x] Add failing tests using a fixed `Clock` and explicit test key for Access/Refresh claims, issuer, `iat`, `exp`, `jti`, wrong signature, wrong issuer, expired token and wrong expected token type.
- [x] Run `mvn -q -pl im-plugin/im-session -am -Dtest=JwtTokenCodecTest,ImSessionAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected: RED because the codec and properties do not exist.
- [x] Implement immutable token records and a codec whose API accepts explicit claims and returns a typed decoded result; do not embed account Session lookup in the plugin.
- [x] Bind `im.session.jwt` properties with `Duration` values (`2h`, `30d`, `15m`) and validate issuer, TTL ordering and minimum external secret strength.
- [x] Support an ephemeral strong key only under an explicit local profile when `secret` is absent; all non-local profiles fail startup without a secret. Never log or expose key bytes.
- [x] Add the codec and properties alongside the existing `TokenService` so downstream modules continue compiling between tasks. Remove the old service only after account and both Gateways migrate in Task 8.
- [x] Separate token responsibilities into `token/codec`, `token/model`, and `token/spi`; keep the token root package free of production classes.
- [x] Re-run the focused command; expected: GREEN.

### Task 2: Define account authentication contracts

**Files:**

- Modify `im-service/im-account/im-account-facade/src/main/java/com/co/kc/imchat/service/account/facade/AccountService.java`
- Retain temporarily, then remove in Task 8: `TokenValidateParams.java` and `TokenValidateDTO.java`
- Create `im-service/im-account/im-account-facade/src/main/java/com/co/kc/imchat/service/account/facade/params/AccessTokenParams.java`
- Create `im-service/im-account/im-account-facade/src/main/java/com/co/kc/imchat/service/account/facade/dto/SessionAuthDTO.java`
- Modify `im-service/im-account/im-account-facade/README.md`
- Modify `im-plugin/im-session/src/main/java/com/co/kc/imchat/plugin/session/context/UserContext.java`
- Modify `im-plugin/im-session/src/main/java/com/co/kc/imchat/plugin/session/context/UserContextHeaders.java`
- Modify `im-plugin/im-web/src/main/java/com/co/kc/imchat/plugin/web/session/UserContextInterceptor.java`
- Add serialization/contract tests under `im-service/im-account/im-account-facade/src/test/java`

- [x] Add failing contract tests proving Facade params/results are serializable, do not expose Refresh Token digests, and represent only successful authentication; invalid Token or Session state raises `AUTH_FAIL(10001)` without transport-specific exception types.
- [x] Run `mvn -q -pl im-service/im-account/im-account-facade -am test`; expected: RED until the new contracts exist.
- [x] Define the `authenticate` Facade parameter/result types; add the `AccountService` method together with its provider implementation in Task 4 so every intermediate revision remains buildable. Keep token-type parsing and Session decisions behind the server implementation.
- [x] Include only `valid`, `userId`, `sessionVersion`, and `accessTokenExpiresAt` in the result.
- [x] Extend trusted request context with `sessionVersion` and a dedicated internal header so account logout can consume it before the Gateway migration task. Keep old validation contracts temporarily; remove them after all call sites migrate in Task 8.
- [x] Re-run the facade tests; expected: GREEN.

### Task 3: Add single-session state and atomic refresh rotation

**Files:**

- Modify `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/domain/session/model/Session.java`
- Modify `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/domain/session/repository/SessionRepository.java`
- Modify `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/model/cqrs/dto/SessionDTO.java`
- Modify `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/transformer/domain/UserDomainTransformer.java`
- Modify `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/infrastructure/domain/repository/RedisSessionRepository.java`
- Modify `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/infrastructure/config/beans/RepositoryBeans.java`
- Create focused domain/repository tests under `im-service/im-account/im-account-server/src/test/java/com/co/kc/imchat/service/account/domain/session` and `.../infrastructure/domain/repository`

- [x] Add failing domain tests for sign-in creating a new session version, sign-out clearing Refresh state, online-version matching, expired Refresh state, and replacement returning the previous version.
- [x] Add a failing repository concurrency test in which two rotations use the same expected Refresh digest and exactly one succeeds.
- [x] Run `mvn -q -pl im-service/im-account/im-account-server -am -Dtest='*Session*Test' -Dsurefire.failIfNoSpecifiedTests=false test`; expected: RED.
- [x] Replace mutable `@Data` Session state with explicit behavior and read-only accessors for `sessionVersion`, `refreshFingerprint`, and `refreshTokenExpiresAt`.
- [x] Extend `SessionRepository` with a compare-and-rotate operation expressed in domain terms. Implement it with a Redis/Redisson atomic primitive or narrowly scoped distributed critical section; do not implement a non-atomic get-then-put across instances.
- [x] Keep the existing Session cache key and serialization compatibility where possible; document and test behavior when an old cached Session lacks new fields (treat it as requiring a new login, not as authenticated).
- [x] Pass time and generated session identity into domain behavior explicitly; no `LocalDateTime.now()` or random generation inside domain behavior. Task 4 injects the application `Clock` and generator when it migrates the login flow.
- [x] Re-run focused tests; expected: GREEN.

### Task 4: Implement login, centralized validation, refresh and logout

**Files:**

- Modify `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/application/AccountAppService.java`
- Create `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/domain/session/model/AccessCredential.java`
- Create `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/domain/session/model/RefreshCredential.java`
- Create `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/domain/session/model/CredentialPair.java`
- Create `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/domain/session/model/Fingerprint.java`
- Create `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/domain/session/service/SessionCredentialService.java`
- Create `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/infrastructure/domain/service/JwtSessionCredentialService.java`
- Remove misplaced intermediate implementations `application/TokenIssuer.java` and `application/RefreshTokenDigest.java`
- Modify `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/domain/user/service/UserService.java`
- Modify `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/infrastructure/config/beans/AppServiceBeans.java`
- Modify `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/interfaces/rpc/AccountRpcService.java`
- Modify `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/interfaces/http/UserController.java`
- Modify `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/model/cqrs/dto/SignInDTO.java`
- Replace `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/model/io/UserSignInResponse.java`
- Create `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/model/io/TokenRefreshRequest.java`
- Create `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/model/io/TokenPairResponse.java`
- Add application and HTTP/RPC tests under the corresponding account test packages

- [x] Add failing tests proving: sign-in returns both tokens and expiry instants; missing user and wrong password return the same public authentication result; a second sign-in invalidates the first version; validation checks Access type, expiry, online status and version.
- [x] Add an ArchUnit RED test proving account `application` classes do not depend directly on `JwtTokenCodec`, plugin Token claim/type models, or `javax.crypto`; add focused domain-service contract and infrastructure implementation tests following the existing `PasswordService -> BcryptPasswordService` topology.
- [x] Add failing refresh tests for successful rotation, old Refresh rejection, Access-token rejection at refresh, expired Refresh, version mismatch, and two concurrent refreshes with one success.
- [x] Add failing logout tests proving the trusted authenticated session version is required and both old token types fail afterward.
- [x] Run `mvn -q -pl im-service/im-account/im-account-server -am -Dtest='*Account*Test,*Token*Test,*UserController*Test' -DfailIfNoTests=false test`; expected: RED.
- [x] Define the business-level `SessionCredentialService` in `domain/session/service`, keep credential/fingerprint values in `domain/session/model`, and encapsulate JWT/HMAC/JTI under `infrastructure/domain/service`. Do not expose `digest`, `HMAC` or codec operations through the domain interface.
- [x] Make `authenticate` perform online Session lookup and fail closed. Do not accept Refresh Tokens as authenticated identities.
- [x] Add `/user/refreshToken`; accept only a Refresh Token and return a rotated pair. Keep `/user/signIn` and refresh responses structurally identical.
- [x] Change sign-out to use trusted user/session context rather than a request user ID.
- [x] Normalize nonexistent-user and wrong-password errors at the public boundary while retaining non-sensitive internal diagnostics.
- [x] Re-run focused tests; expected: GREEN.

### Task 5: Centralize HTTP Gateway authentication without blocking WebFlux

**Files:**

- Modify `im-gateway/im-http-gateway/pom.xml`
- Modify `im-gateway/im-http-gateway/src/main/java/com/co/kc/imchat/gateway/http/security/authentication/AuthenticationManager.java`
- Modify `im-gateway/im-http-gateway/src/main/java/com/co/kc/imchat/gateway/http/security/authentication/AuthenticationToken.java`
- Modify `im-gateway/im-http-gateway/src/main/java/com/co/kc/imchat/gateway/http/security/authentication/AuthenticationSuccessHandler.java`
- Modify `im-gateway/im-http-gateway/src/main/java/com/co/kc/imchat/gateway/http/security/filter/UserContextHeaderSanitizingFilter.java`
- Modify `im-gateway/im-http-gateway/src/main/java/com/co/kc/imchat/gateway/http/security/config/SecurityConfig.java`
- Create `im-gateway/im-http-gateway/src/main/java/com/co/kc/imchat/gateway/http/security/config/AccountAuthenticationConfig.java`
- Rewrite `im-gateway/im-http-gateway/src/test/java/com/co/kc/imchat/gateway/http/security/authentication/SecurityComponentTest.java`
- Modify filter/security integration tests and `im-gateway/im-http-gateway/README.md`

- [x] Add failing tests proving external user/session headers are removed, successful account validation rebuilds both trusted headers, Refresh Tokens fail normal authentication, and invalid/timeout/error responses fail closed.
- [x] Add a test that records the executing thread and proves a blocking account test double is not invoked on the WebFlux event-loop thread; prefer a real async Dubbo contract if the project integration supports it, otherwise use a named bounded authentication scheduler with bounded queue and rejection behavior.
- [x] Run `mvn -q -pl im-gateway/im-http-gateway -am test`; expected: RED.
- [x] Replace local `TokenService` parsing with `AccountService.authenticate` and configure a strict timeout with no write-style retry.
- [x] Permit `/account/user/refreshToken` while leaving all other business paths authenticated.
- [x] Carry `sessionVersion` in `AuthenticationToken`, sanitize the external header, rebuild it after validation and expose it through `UserContext` downstream.
- [x] Re-run HTTP Gateway and affected plugin tests; expected: GREEN.

### Task 6: Centralize WS handshake authentication and store session metadata locally

**Files:**

- Modify `im-gateway/im-ws-gateway/im-ws-gateway-server/pom.xml`
- Modify `im-gateway/im-ws-gateway/im-ws-gateway-server/src/main/java/com/co/kc/imchat/gateway/ws/security/authentication/WsAuthenticationManager.java`
- Modify `im-gateway/im-ws-gateway/im-ws-gateway-server/src/main/java/com/co/kc/imchat/gateway/ws/security/identity/WsPrincipal.java`
- Modify `im-gateway/im-ws-gateway/im-ws-gateway-server/src/main/java/com/co/kc/imchat/gateway/ws/server/context/ContextAttributes.java`
- Modify `im-gateway/im-ws-gateway/im-ws-gateway-server/src/main/java/com/co/kc/imchat/gateway/ws/server/handler/HandshakeHandler.java`
- Modify `im-gateway/im-ws-gateway/im-ws-gateway-server/src/main/java/com/co/kc/imchat/gateway/ws/registry/ConnectionRegistry.java`
- Modify `im-gateway/im-ws-gateway/im-ws-gateway-server/src/main/java/com/co/kc/imchat/gateway/ws/config/GatewayBeans.java`
- Create `im-gateway/im-ws-gateway/im-ws-gateway-server/src/main/java/com/co/kc/imchat/gateway/ws/config/AccountAuthenticationConfig.java`
- Rewrite `im-gateway/im-ws-gateway/im-ws-gateway-server/src/test/java/com/co/kc/imchat/gateway/ws/handler/netty/HandshakeHandlerTest.java`
- Modify `.../registry/ConnectionRegistryTest.java` and module README

- [x] Add failing handshake tests for valid Access, Refresh rejection, expired/invalid Session, account timeout and account failure.
- [x] Add a deterministic asynchronous handshake test proving Netty event-loop remains responsive while validation is pending and the request reference is released exactly once on every rejection path.
- [x] Add registry tests that register `userId + sessionVersion + connectionId`, list active state without exposing channels, and close only connections matching an old session version.
- [x] Run `mvn -q -pl im-gateway/im-ws-gateway/im-ws-gateway-server -am test`; expected: RED.
- [x] Replace local Token parsing with async account Facade validation. Continue the handshake on the channel event loop only after validation succeeds.
- [x] Save session version only in Gateway-local connection metadata; Broker registration remains `userId -> gatewayId` and receives no connection ID/session ownership.
- [x] Re-run WS Gateway tests; expected: GREEN.

### Task 7: Route session-close controls through Broker

**Files:**

- Modify `im-broker/im-broker-sdk/src/main/java/com/co/kc/imchat/broker/sdk/enums/BrokerBoltOperation.java`
- Create `im-broker/im-broker-sdk/src/main/java/com/co/kc/imchat/broker/sdk/model/params/ConnectionCloseParams.java`
- Modify `im-broker/im-broker-sdk/src/main/java/com/co/kc/imchat/broker/sdk/BrokerClient.java`
- Add Broker SDK tests
- Create `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/interfaces/handler/connection/ConnectionCloseHandler.java`
- Add Broker handler/service tests
- Create `im-gateway/im-ws-gateway/im-ws-gateway-sdk/src/main/java/com/co/kc/imchat/gateway/ws/sdk/model/params/ConnectionCloseParams.java`
- Modify `im-gateway/im-ws-gateway/im-ws-gateway-sdk/src/main/java/com/co/kc/imchat/gateway/ws/sdk/enums/GatewayBoltOperation.java`
- Modify `im-gateway/im-ws-gateway/im-ws-gateway-sdk/src/main/java/com/co/kc/imchat/gateway/ws/sdk/GatewayClient.java`
- Create `im-gateway/im-ws-gateway/im-ws-gateway-server/src/main/java/com/co/kc/imchat/gateway/ws/handler/ConnectionCloseHandler.java`
- Modify `im-gateway/im-ws-gateway/im-ws-gateway-server/src/main/java/com/co/kc/imchat/gateway/ws/config/GatewayBeans.java`
- Modify `im-service/im-account/im-account-server/pom.xml`
- Create account after-commit session replacement publisher and tests

- [x] Add failing Broker tests proving a close control resolves only the current user Gateway route, forwards only `userId + version`, and treats offline/missing routes as successful no-ops.
- [x] Add failing Gateway handler tests proving only old-version connections close and new-session connections survive an out-of-order control request.
- [x] Add failing account tests proving login/logout commits Session state before publishing; publish failure does not roll back authentication state and is observable without Token/message data.
- [x] Run focused Broker, Gateway SDK/server and account tests; expected: RED.
- [x] Add dedicated control operations instead of overloading frame delivery. Broker must not store session version or Gateway-local connection IDs.
- [x] Implement bounded retry outside the login transaction using an existing reliable scheduling mechanism; if no suitable mechanism exists, record an explicit follow-up debt and keep Session validation as the authority rather than adding an in-memory unbounded retry queue.
- [x] Add finite counters/timers without userId, sessionVersion, connectionId or Token tags.
- [x] Run `./scripts/verify.sh behavior`; expected: PASS.

### Task 8: Remove obsolete token paths, document rollout and verify

**Files:**

- Modify `docs/SECURITY.md`
- Modify `docs/RELIABILITY.md`
- Modify `ARCHITECTURE.md` if the finalized authentication call/control path changes the concise topology
- Modify module READMEs and `docs/design-docs/2026-08-14-centralized-session-authentication-design.md` only for implementation-confirmed clarifications
- Remove obsolete local Gateway TokenService wiring and tests
- Update this plan and execution-plan indexes

- [x] Search for `new JwtTokenService`, local Gateway `TokenService`, old single `token` response fields, old validation params and hard-coded secrets; expected final production count: zero except deliberate codec construction/configuration in account infrastructure.
- [x] Add a migration test/fixture proving cached Sessions without `sessionVersion` cannot authenticate and can sign in again cleanly.
- [x] Verify no logs contain password, Access/Refresh Token, digest or complete authentication body; add focused regression assertions around changed authentication logs.
- [x] Run affected module suites:
  - `mvn -q -pl im-plugin/im-session,im-plugin/im-web -am test`
  - `mvn -q -pl im-service/im-account/im-account-facade,im-service/im-account/im-account-server -am test`
  - `mvn -q -pl im-gateway/im-http-gateway,im-gateway/im-ws-gateway/im-ws-gateway-server -am test`
  - `mvn -q -pl im-broker/im-broker-sdk,im-broker/im-broker-server -am test`
- [x] Run `./scripts/verify.sh behavior`, `./scripts/verify.sh quick`, and `./scripts/verify.sh full`; expected: PASS.
- [x] Run `git diff --check`, inspect the complete diff and confirm no generated output, credentials or unrelated cleanup is present.
- [x] Move this plan unchanged to `docs/exec-plans/completed/2026-08-14-centralized-session-authentication.md` and update active/completed indexes only after all acceptance checks pass.

## Test strategy

- Token tests use fixed clocks, explicit keys and deterministic IDs.
- Account application tests use real Session behavior and only fake repositories/remote publishers at direct boundaries.
- HTTP tests prove WebFlux non-blocking behavior and trusted-header sanitization.
- WS tests use `EmbeddedChannel`/controllable completion and assert reference-count safety.
- Refresh concurrency tests verify observable single-winner behavior, not internal lock calls.
- Broker control tests cover missing route, owner route, remote route, duplicate/out-of-order delivery and publish failure.
- Full behavior verification remains required because the work changes authentication, routing and connection lifecycle.

## Rollout, compatibility and rollback

This changes the public login response and requires a client that stores both Tokens and refreshes before expiry or on application startup. Deploy account Facade/provider support first, then Gateway consumers and client, then remove the old local validation path. If atomic deployment is unavailable, add only a time-bounded migration adapter with an explicit removal task; never retain the hard-coded secret or fail-open local validation.

Existing Redis Sessions without new fields are invalidated and require login. Rollback after issuing new Token types requires reverting account and Gateway together; old local JWT validation cannot safely validate the new session-bound contract. Record operational readiness for account RPC latency/error rate before enabling centralized validation for all traffic.

## Completion criteria

- Source contains no committed usable JWT secret.
- Access Tokens expire after two hours; Refresh Tokens rotate and expire after 30 days.
- Logout and second login immediately invalidate old HTTP authentication and future WS handshakes.
- Existing WS connections for an old session version are closed without closing the replacement session.
- HTTP and Netty event loops do not block on account validation.
- External user/session headers are sanitized and rebuilt from the account result.
- Login failures do not disclose account existence.
- Focused, behavior, quick and full verification all pass.
- No commit is created unless the user explicitly requests one.
