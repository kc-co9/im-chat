# IM Architecture Upgrade Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前 IM 单体式多模块应用升级为 HTTP 网关、WS 网关、IM Broker、轻量业务服务拆分的服务化架构。

**Architecture:** 第一阶段按设计文档落地 `im-common`、`im-plugin`、`im-gateway`、`im-broker`、`im-service` 结构。HTTP 通过 Spring Cloud Gateway 路由到业务服务；实时消息通过 Netty WebSocket、SOFA Bolt、Broker 定向投递；业务先轻量拆成 account/social/message。

**Tech Stack:** Java 21, Spring Boot 3.5.x, Maven, Nacos, Spring Cloud Gateway, Dubbo, SOFA Bolt, Netty, Redis, MySQL, MyBatis-Plus, Redisson, JUnit 5.

---

## Reference Documents

- Spec: `docs/superpowers/specs/2026-06-08-architecture-upgrade-design.md`
- Current root Maven: `pom.xml`
- Current modules: `im-common`, `im-domain`, `im-application`, `im-infrastructure`, `im-interfaces`, `im-bootstrap`
- Current WebSocket config: `im-interfaces/src/main/java/com/co/kc/imchat/interfaces/config/WebSocketConfig.java`
- Current Redis notification path:
  - `im-application/src/main/java/com/co/kc/imchat/application/support/notifier/**`
  - `im-infrastructure/src/main/java/com/co/kc/imchat/infrastructure/support/notifier/**`
  - `im-interfaces/src/main/java/com/co/kc/imchat/interfaces/endpoint/consumer/**`

## Execution Rules

- Keep each task compiling before moving to the next task unless the task explicitly says it is a skeleton-only step.
- Prefer `git mv` for moves so file history remains readable.
- Do not change business behavior during module moves.
- Do not let facade modules depend on server modules.
- Do not let gateway or broker modules depend on business implementation classes.
- Keep existing package prefix `com.co.kc.imchat` during the first migration unless a compile boundary requires a new package.
- Run the task verification command before committing.
- Commit after each task with the commit message listed in that task.
- Leave `.superpowers/` untracked.

## Target Structure

```text
pom.xml

im-common/

im-plugin/
  pom.xml
  im-cache/
  im-lock/
  im-datasource/
  im-session/
  im-web/
  im-dubbo/
  im-bolt/
  im-mq/

im-gateway/
  pom.xml
  im-http-gateway/
  im-ws-gateway/
    pom.xml
    im-ws-gateway-facade/
    im-ws-gateway-server/

im-broker/
  pom.xml
  im-broker-facade/
  im-broker-server/

im-service/
  pom.xml
  im-account/
    pom.xml
    im-account-facade/
    im-account-server/
  im-social/
    pom.xml
    im-social-facade/
    im-social-server/
  im-message/
    pom.xml
    im-message-facade/
    im-message-server/
```

## Dependency Direction

```text
im-common
  <- im-plugin/*
  <- facade modules
  <- server modules

im-plugin/*
  <- server modules only

*-facade
  <- callers and matching *-server

*-server
  -> matching facade
  -> im-common
  -> required im-plugin modules

im-http-gateway
  -> im-common
  -> im-plugin/im-web

im-ws-gateway-server
  -> im-ws-gateway-facade
  -> im-broker-facade
  -> im-plugin/im-bolt

im-broker-server
  -> im-broker-facade
  -> im-ws-gateway-facade
  -> im-message-facade
  -> im-plugin/im-bolt
  -> im-plugin/im-cache

im-message-server
  -> im-message-facade
  -> im-account-facade
  -> im-social-facade
  -> im-broker-facade
```

## Task 1: Create Aggregator Module Skeleton

**Files:**
- Modify: `pom.xml`
- Create: `im-plugin/pom.xml`
- Create: `im-gateway/pom.xml`
- Create: `im-broker/pom.xml`
- Create: `im-service/pom.xml`
- Create: child module `pom.xml` files listed in Target Structure

- [ ] **Step 1: Write skeleton validation expectation**

Run before edits:

```bash
mvn -q -N validate
```

Expected: PASS on the current root project.

- [ ] **Step 2: Update root modules**

Modify root `pom.xml` modules to include:

```xml
<modules>
    <module>im-common</module>
    <module>im-plugin</module>
    <module>im-gateway</module>
    <module>im-broker</module>
    <module>im-service</module>
</modules>
```

Temporarily keep old modules only if code has not moved yet. If old modules are kept during this task, add a comment in the plan checkpoint and remove them in Task 3.

- [ ] **Step 3: Add parent aggregator poms**

Create `im-plugin/pom.xml`, `im-gateway/pom.xml`, `im-broker/pom.xml`, and `im-service/pom.xml` as packaging `pom` modules with the root parent:

```xml
<parent>
    <groupId>com.co.kc.im</groupId>
    <artifactId>im-chat</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</parent>
```

- [ ] **Step 4: Add empty child module poms**

Create all facade/server child poms with packaging `jar`. Each child uses its nearest aggregator parent where practical.

For example, `im-service/im-message/im-message-facade/pom.xml`:

```xml
<parent>
    <groupId>com.co.kc.im</groupId>
    <artifactId>im-message</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</parent>
<artifactId>im-message-facade</artifactId>
```

- [ ] **Step 5: Verify Maven skeleton**

Run:

```bash
mvn -q -N validate
mvn -q validate
```

Expected: root and new empty modules validate.

- [ ] **Step 6: Commit**

```bash
git add pom.xml im-plugin im-gateway im-broker im-service
git commit -m "chore: scaffold architecture modules"
```

## Task 2: Extract Reusable Plugin Modules

**Files:**
- Modify: `pom.xml`
- Modify: `im-plugin/*/pom.xml`
- Move/create:
  - `im-plugin/im-cache/src/main/java/**`
  - `im-plugin/im-lock/src/main/java/**`
  - `im-plugin/im-datasource/src/main/java/**`
  - `im-plugin/im-session/src/main/java/**`
  - `im-plugin/im-web/src/main/java/**`
  - `im-plugin/im-dubbo/src/main/java/**`
  - `im-plugin/im-bolt/src/main/java/**`
  - `im-plugin/im-mq/src/main/java/**`

- [ ] **Step 1: Move only generic technical code**

Move generic Redis/cache, lock, datasource, session, web, Dubbo, Bolt, and MQ configuration helpers into `im-plugin`.

Do not move business repositories, business notifiers, or domain/application classes in this task.

- [ ] **Step 2: Keep plugin dependencies one-way**

Each plugin may depend on `im-common`, Spring starter libraries, and its own technology library. Plugins must not depend on `im-service`, `im-gateway`, or `im-broker`.

- [ ] **Step 3: Add minimal smoke tests**

For each plugin with non-trivial code, add a small test under that plugin. Examples:

```text
im-plugin/im-bolt/src/test/java/.../BoltPropertiesTest.java
im-plugin/im-cache/src/test/java/.../RedisKeyFormatTest.java
```

- [ ] **Step 4: Verify plugins**

Run:

```bash
mvn -q -pl im-plugin -am test
```

Expected: plugin modules compile and tests pass.

- [ ] **Step 5: Commit**

```bash
git add im-plugin pom.xml
git commit -m "refactor: extract reusable plugin modules"
```

## Task 3: Split Business Services

**Files:**
- Move from current modules into:
  - `im-service/im-account/im-account-facade`
  - `im-service/im-account/im-account-server`
  - `im-service/im-social/im-social-facade`
  - `im-service/im-social/im-social-server`
  - `im-service/im-message/im-message-facade`
  - `im-service/im-message/im-message-server`
- Remove or retire old business modules after successful migration:
  - `im-domain`
  - `im-application`
  - `im-infrastructure`
  - `im-interfaces`
  - `im-bootstrap`

- [ ] **Step 1: Define facade DTO boundaries**

Create facade request/response DTOs for cross-service calls. Do not expose domain objects directly.

Minimum facade modules:

```text
im-account-facade: user lookup, auth token validation, session/user profile responses
im-social-facade: friend relation checks, group membership checks, group member queries
im-message-facade: message send/read/revoke commands, notification ACK command
```

- [ ] **Step 2: Move account code**

Move user domain, user application service, token/auth support, password service, user repository implementation, and user HTTP controller into `im-account-server`.

Keep reusable JWT/password helpers in `im-plugin` only if they are truly generic; otherwise keep them inside account.

- [ ] **Step 3: Move social code**

Move friend and group domain/application/infrastructure/controller code into `im-social-server`.

Expose only the relation/group checks required by message sending through `im-social-facade`.

- [ ] **Step 4: Move message code**

Move chat/message domain, message application services, inbox repositories, notification ACK/retry code, and message HTTP/WS command logic into `im-message-server`.

The message service may depend on `im-account-facade`, `im-social-facade`, and `im-broker-facade`.

- [ ] **Step 5: Move tests with owning services**

Move existing tests by ownership:

```text
UserAppServiceTest -> im-account-server
FriendAppServiceTest, GroupAppServiceTest -> im-social-server
PrivateChatAppServiceTest, GroupChatAppServiceTest, NotificationAckAppServiceTest -> im-message-server
```

- [ ] **Step 6: Verify business services**

Run:

```bash
mvn -q -pl im-service -am test
```

Expected: all service modules compile and migrated tests pass.

- [ ] **Step 7: Commit**

```bash
git add im-service pom.xml
git commit -m "refactor: split business services"
```

## Task 4: Implement HTTP Gateway

**Files:**
- Create/modify: `im-gateway/im-http-gateway/pom.xml`
- Create: `im-gateway/im-http-gateway/src/main/java/com/co/kc/imchat/gateway/http/ImHttpGatewayApplication.java`
- Create: `im-gateway/im-http-gateway/src/main/resources/application.yml`
- Create tests under: `im-gateway/im-http-gateway/src/test/java/**`

- [ ] **Step 1: Add Spring Cloud Gateway dependencies**

Use Spring Cloud Gateway and Spring Cloud Alibaba Nacos dependencies managed from root `pom.xml`.

- [ ] **Step 2: Configure routes**

Add route configuration:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: im-account
          uri: lb://im-account
          predicates:
            - Path=/api/account/**
        - id: im-social
          uri: lb://im-social
          predicates:
            - Path=/api/social/**
        - id: im-message
          uri: lb://im-message
          predicates:
            - Path=/api/message/**
```

- [ ] **Step 3: Add route tests**

Add a test that loads the route definitions and asserts the three route IDs exist.

- [ ] **Step 4: Verify HTTP gateway**

Run:

```bash
mvn -q -pl im-gateway/im-http-gateway -am test
```

Expected: gateway module compiles and route test passes.

- [ ] **Step 5: Commit**

```bash
git add im-gateway/im-http-gateway pom.xml
git commit -m "feat: add http gateway"
```

## Task 5: Define IM JSON Protocol And WS Gateway Skeleton

**Files:**
- Create/modify: `im-gateway/im-ws-gateway/im-ws-gateway-facade/**`
- Create/modify: `im-gateway/im-ws-gateway/im-ws-gateway-server/**`

- [ ] **Step 1: Add protocol model tests**

Create tests for encoding and decoding the frame shape:

```text
version, type, cmd, seq, traceId, body
```

Expected request command:

```json
{"version":"1","type":"request","cmd":"message.private.send","seq":"10001","traceId":"abc","body":{}}
```

- [ ] **Step 2: Implement `ImFrame` and codec**

Create:

```text
im-ws-gateway-facade/src/main/java/.../protocol/ImFrame.java
im-ws-gateway-facade/src/main/java/.../protocol/ImFrameType.java
im-ws-gateway-server/src/main/java/.../protocol/JsonFrameCodec.java
```

- [ ] **Step 3: Add Netty WebSocket server skeleton**

Create a Netty server that accepts WebSocket connections at `/ws`, authenticates a token placeholder, decodes text frames, and routes supported commands to a command dispatcher.

- [ ] **Step 4: Add command whitelist**

Support only:

```text
message.private.send
message.private.read
message.private.revoke
message.group.send
message.group.read
message.group.revoke
notification.ack
```

Unknown commands return an error response frame.

- [ ] **Step 5: Verify WS gateway**

Run:

```bash
mvn -q -pl im-gateway/im-ws-gateway -am test
```

Expected: codec and dispatcher tests pass.

- [ ] **Step 6: Commit**

```bash
git add im-gateway/im-ws-gateway pom.xml
git commit -m "feat: add websocket gateway protocol"
```

## Task 6: Implement Broker Facade And Routing Store

**Files:**
- Create/modify: `im-broker/im-broker-facade/**`
- Create/modify: `im-broker/im-broker-server/**`
- Tests: `im-broker/im-broker-server/src/test/java/**`

- [ ] **Step 1: Write route store tests**

Test:

1. Register one connection for one user.
2. Register multiple connections for one user.
3. Unregister one connection.
4. Query returns all active connections.
5. Stale Redis route can be ignored when Gateway is unavailable.

- [ ] **Step 2: Define Broker facade**

Create facade commands:

```text
RegisterGatewayCommand
RegisterConnectionCommand
UnregisterConnectionCommand
RefreshConnectionsCommand
RouteMessageCommand
PushToUserCommand
```

- [ ] **Step 3: Implement in-memory route store**

Create a thread-safe in-memory store:

```text
userId -> List<ConnectionRoute>
gatewayId -> GatewayRoute
```

- [ ] **Step 4: Add Redis TTL fallback abstraction**

Create an interface for route fallback persistence and a Redis implementation. Keep route key schema centralized.

- [ ] **Step 5: Verify broker**

Run:

```bash
mvn -q -pl im-broker -am test
```

Expected: route store and fallback tests pass.

- [ ] **Step 6: Commit**

```bash
git add im-broker pom.xml
git commit -m "feat: add im broker routing"
```

## Task 7: Wire Gateway, Broker, And Message Realtime RPC

**Files:**
- Modify: `im-gateway/im-ws-gateway/im-ws-gateway-server/**`
- Modify: `im-broker/im-broker-server/**`
- Modify: `im-service/im-message/im-message-server/**`
- Modify: `im-plugin/im-bolt/**`

- [ ] **Step 1: Add fake RPC integration tests**

Use test doubles to verify:

```text
WS request -> Gateway dispatcher -> Broker facade -> Message command handler
Message push -> Broker route lookup -> Gateway push facade
ACK -> Gateway -> Broker -> Message ACK handler
```

- [ ] **Step 2: Implement Gateway-to-Broker client**

The Gateway calls Broker for:

```text
register connection
unregister connection
batch refresh
forward realtime command
```

- [ ] **Step 3: Implement Broker-to-Message client**

The Broker forwards supported realtime commands to `im-message-server`.

- [ ] **Step 4: Implement Message-to-Broker push client**

The message service calls Broker when a message notification needs realtime delivery.

- [ ] **Step 5: Implement Broker-to-Gateway push client**

Broker routes push requests to the target Gateway and connection list.

- [ ] **Step 6: Verify RPC wiring**

Run:

```bash
mvn -q -pl im-gateway/im-ws-gateway,im-broker,im-service/im-message -am test
```

Expected: integration-style tests with fakes pass.

- [ ] **Step 7: Commit**

```bash
git add im-gateway im-broker im-service/im-message im-plugin/im-bolt pom.xml
git commit -m "feat: wire realtime broker delivery"
```

## Task 8: Replace Redis Pub/Sub Notification Fanout

**Files:**
- Modify/remove:
  - `im-application/src/main/java/com/co/kc/imchat/application/support/redis/RedisPublisher.java`
  - `im-application/src/main/java/com/co/kc/imchat/application/support/redis/RedisSubscriber.java`
  - `im-infrastructure/src/main/java/com/co/kc/imchat/infrastructure/support/notifier/AbstractRedisImMessageNotifier.java`
  - `im-infrastructure/src/main/java/com/co/kc/imchat/infrastructure/support/notifier/*Notifier.java`
  - `im-interfaces/src/main/java/com/co/kc/imchat/interfaces/endpoint/consumer/*Consumer.java`
- New location:
  - `im-service/im-message/im-message-server/src/main/java/**`

- [ ] **Step 1: Write notifier tests**

For private sent, private revoked, group sent, and group revoked:

1. Message service creates receipt state.
2. Message service calls Broker push facade.
3. Broker route miss does not fail message persistence.
4. Retry uses Broker push facade, not Redis Pub/Sub.

- [ ] **Step 2: Replace notifier implementation**

Create Broker-backed notifier implementation inside `im-message-server`.

Remove Redis Pub/Sub publisher/subscriber from realtime notification path. Keep Redis only for receipt state and retry tasks.

- [ ] **Step 3: Remove old WebSocket consumers**

Remove or retire Redis subscriber consumers that call `SimpMessagingTemplate.convertAndSendToUser`.

- [ ] **Step 4: Verify notification behavior**

Run:

```bash
mvn -q -pl im-service/im-message -am test
```

Expected: message service tests pass and no production notifier depends on Redis Pub/Sub for push fanout.

- [ ] **Step 5: Commit**

```bash
git add im-service/im-message im-application im-infrastructure im-interfaces pom.xml
git commit -m "refactor: replace redis fanout with broker delivery"
```

## Task 9: Remove Old Bootstrap And Validate Full Build

**Files:**
- Modify: `pom.xml`
- Remove/retire old modules after code migration:
  - `im-domain`
  - `im-application`
  - `im-infrastructure`
  - `im-interfaces`
  - `im-bootstrap`
- Modify docs:
  - `README.md`
  - `docs/superpowers/specs/2026-06-08-architecture-upgrade-design.md` if implementation decisions changed

- [ ] **Step 1: Remove old modules from root aggregator**

After all code and tests have moved, remove old modules from root `pom.xml`.

- [ ] **Step 2: Update README architecture section**

Replace old Redis Pub/Sub broadcast description with the new HTTP/WS/Broker architecture.

- [ ] **Step 3: Run full verification**

Run:

```bash
mvn -q test
```

Expected: all modules compile and tests pass.

- [ ] **Step 4: Check dependency boundaries**

Run:

```bash
mvn -q dependency:tree
```

Expected:

- No facade module depends on a server module.
- No gateway module depends on business server implementation except through facade.
- No broker module depends on HTTP gateway.

- [ ] **Step 5: Commit**

```bash
git add pom.xml README.md docs im-common im-plugin im-gateway im-broker im-service
git commit -m "chore: complete architecture module migration"
```

## Task 10: Manual Runtime Smoke Test

**Files:**
- Create docs or scripts if useful:
  - `docs/superpowers/plans/2026-06-09-architecture-upgrade-runtime-check.md`
  - optional local scripts under `scripts/`

- [ ] **Step 1: Start infrastructure**

Start local dependencies:

```text
MySQL
Redis
Nacos
```

- [ ] **Step 2: Start services**

Start in order:

```text
im-account-server
im-social-server
im-message-server
im-broker-server
im-ws-gateway-server
im-http-gateway
```

- [ ] **Step 3: Verify HTTP route**

Call one account/social/message HTTP endpoint through `im-http-gateway`.

Expected: request reaches the owning service.

- [ ] **Step 4: Verify WebSocket send/push**

Open two WebSocket clients:

1. User A sends `message.private.send`.
2. Request reaches `im-message`.
3. `im-message` calls Broker.
4. Broker routes to User B's Gateway connection.
5. User B receives a `push` frame.
6. User B sends `notification.ack`.
7. `im-message` marks receipt confirmed.

- [ ] **Step 5: Commit runtime docs if added**

```bash
git add docs scripts
git commit -m "docs: add architecture runtime smoke check"
```
