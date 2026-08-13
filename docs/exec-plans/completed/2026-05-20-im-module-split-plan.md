# IM Module Split Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the current single-module `im-chat` Spring Boot project into strict DDD Maven modules with `im-bootstrap` as the runnable application.

**Architecture:** The root `im-chat` project becomes a Maven parent/aggregator. Code moves into `im-common`, `im-domain`, `im-application`, `im-infrastructure`, `im-endpoint`, and `im-bootstrap` with compile-time dependency direction from endpoint/application toward domain/common and infrastructure as outbound adapter implementation. SQL scripts live at repository root under `sql/`.

**Tech Stack:** Java 8, Spring Boot 2.7.18, Maven, MyBatis-Plus, Redis/Redisson, MapStruct, Lombok, JUnit 5.

---

## Reference Documents

- Spec: `docs/superpowers/specs/2026-05-20-im-module-split-design.md`
- Current root Maven file: `pom.xml`
- Current source root: `src/main/java/com/co/kc/imchat`
- Current test root: `src/test/java/com/co/kc/imchat`

## Execution Rules

- Do not change business behavior.
- Do not commit unless the user explicitly asks for git commit.
- Keep package names under `com.co.kc.imchat` during the migration unless required by compilation.
- Prefer `git mv` for file moves so history remains readable.
- Run the stated verification command after each task.
- If a Maven module cycle appears, move the interface upward rather than adding reverse dependencies.
- If a test needs a higher-layer bean, move that test to the owning module or use a mock; do not make lower layers depend on higher layers.

## Target File Structure

```text
pom.xml
im-common/pom.xml
im-common/src/main/java/...
im-common/src/test/java/...
im-domain/pom.xml
im-domain/src/main/java/...
im-domain/src/test/java/...
im-application/pom.xml
im-application/src/main/java/...
im-application/src/test/java/...
im-infrastructure/pom.xml
im-infrastructure/src/main/java/...
im-infrastructure/src/main/resources/mapper/...
im-infrastructure/src/test/java/...
im-endpoint/pom.xml
im-endpoint/src/main/java/...
im-endpoint/src/test/java/...
im-bootstrap/pom.xml
im-bootstrap/src/main/java/com/co/kc/imchat/ImChatApplication.java
im-bootstrap/src/main/resources/application.yml
im-bootstrap/src/main/resources/logback-spring.xml
im-bootstrap/src/test/java/...
sql/ddl.sql
```

## Task 1: Create Multi-Module Maven Skeleton

**Files:**
- Modify: `pom.xml`
- Create: `im-common/pom.xml`
- Create: `im-domain/pom.xml`
- Create: `im-application/pom.xml`
- Create: `im-infrastructure/pom.xml`
- Create: `im-endpoint/pom.xml`
- Create: `im-bootstrap/pom.xml`

- [ ] **Step 1: Convert root `pom.xml` to aggregator**

Change root packaging to `pom`. Keep:

- `groupId`: `com.co.kc.im`
- `artifactId`: `im-chat`
- `version`: `0.0.1-SNAPSHOT`
- shared version properties

Add modules:

```xml
<modules>
    <module>im-common</module>
    <module>im-domain</module>
    <module>im-application</module>
    <module>im-infrastructure</module>
    <module>im-endpoint</module>
    <module>im-bootstrap</module>
</modules>
```

Move dependency versions into `<dependencyManagement>`. Move compiler plugin and annotation processor configuration into `<pluginManagement>`.

- [ ] **Step 2: Create child poms**

Each module uses root parent:

```xml
<parent>
    <groupId>com.co.kc.im</groupId>
    <artifactId>im-chat</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</parent>
```

Use these artifact IDs:

- `im-common`
- `im-domain`
- `im-application`
- `im-infrastructure`
- `im-endpoint`
- `im-bootstrap`

- [ ] **Step 3: Add initial dependencies**

`im-common`:

- Lombok provided
- `commons-lang3`
- `commons-collections4`
- Jackson only if `JsonUtils` requires it after migration

`im-domain`:

- `im-common`
- Lombok provided
- `commons-lang3`
- `commons-collections4`

`im-application`:

- `im-common`
- `im-domain`
- Spring context/basic Spring beans
- MapStruct and processor
- Lombok provided

`im-infrastructure`:

- `im-common`
- `im-domain`
- `im-application`
- Spring Boot starter
- Spring Boot AOP if lock aspect stays here
- MyBatis Spring Boot starter
- MyBatis-Plus starter
- MySQL runtime
- Druid
- Redis starter
- Redisson starter
- Caffeine
- JetCache
- JWT
- BCrypt
- MapStruct and processor
- Lombok provided

`im-endpoint`:

- `im-common`
- `im-domain`
- `im-application`
- Spring Boot web
- Spring Boot websocket
- Spring Boot validation
- Springfox Swagger
- MapStruct and processor
- Lombok provided

`im-bootstrap`:

- `im-endpoint`
- `im-infrastructure`
- Spring Boot starter
- Spring Boot test for tests
- Spring Boot Maven plugin

- [ ] **Step 4: Verify Maven sees modules**

Run:

```bash
mvn -q -N validate
mvn -q validate
```

Expected:

- Root validates as aggregator.
- Child modules validate.
- Compilation may still be empty or fail later only after moves begin.

## Task 2: Move Common Code

**Files:**
- Move from `src/main/java/com/co/kc/imchat/support/exception/**` to `im-common/src/main/java/com/co/kc/imchat/support/exception/**`
- Move from `src/main/java/com/co/kc/imchat/support/state/**` to `im-common/src/main/java/com/co/kc/imchat/support/state/**`
- Move from `src/main/java/com/co/kc/imchat/support/mapstruct/**` to `im-common/src/main/java/com/co/kc/imchat/support/mapstruct/**`
- Move from `src/main/java/com/co/kc/imchat/support/function/**` to `im-common/src/main/java/com/co/kc/imchat/support/function/**`
- Move selected utils to `im-common/src/main/java/com/co/kc/imchat/support/utils/`
- Move selected constants to `im-common/src/main/java/com/co/kc/imchat/support/constant/`
- Move `src/main/java/com/co/kc/imchat/model/io/Result.java` to `im-common/src/main/java/com/co/kc/imchat/model/io/Result.java`
- Move `src/main/java/com/co/kc/imchat/model/enums/BaseEnum.java` to `im-common/src/main/java/com/co/kc/imchat/model/enums/BaseEnum.java`
- Move identity support to `im-common/src/main/java/com/co/kc/imchat/support/identity/**`
- Move common tests from `src/test/java/com/co/kc/imchat/support/**` to `im-common/src/test/java/com/co/kc/imchat/support/**` when they only test common code

- [ ] **Step 1: Move exception and generic support packages**

Use `git mv` for:

```text
support/exception
support/state
support/mapstruct
support/function
support/identity
```

- [ ] **Step 2: Move common utils only**

Move:

```text
DateUtils.java
FunctionUtils.java
GeneratorUtils.java
HashUtils.java
JsonUtils.java
ReflectUtils.java
```

Keep these out of common for later tasks:

```text
HttpServletUtils.java
HttpUtils.java
NetworkUtils.java
LoggingUtils.java
```

- [ ] **Step 3: Move common constants and result model**

Move:

```text
support/constant/ErrorCode.java
support/constant/ResultCode.java
model/io/Result.java
model/enums/BaseEnum.java
```

Keep:

```text
RedisKey.java
RedisTopic.java
PushQueue.java
ParamsConstants.java
LogFormat.java
```

- [ ] **Step 4: Move common tests**

Move tests that only depend on common classes:

```text
src/test/java/com/co/kc/imchat/support/lock/LockKeysTest.java
```

Only move lock tests if lock primitives are moved to common. If lock aspect/template stays infrastructure, move those tests in Task 5.

- [ ] **Step 5: Run common tests**

Run:

```bash
mvn -pl im-common test
```

Expected:

- `im-common` compiles.
- Common tests pass.

## Task 3: Move Domain Code

**Files:**
- Move: `src/main/java/com/co/kc/imchat/domain/**` to `im-domain/src/main/java/com/co/kc/imchat/domain/**`
- Move domain-related tests to `im-domain/src/test/java/com/co/kc/imchat/domain/**`
- Move domain-related transformers only if they map DB to domain later; most `transformer/domain/**` should wait for Task 4 or Task 5 based on dependencies

- [ ] **Step 1: Move all domain packages**

Move:

```text
domain/chat
domain/friend
domain/group
domain/message
domain/session
domain/shared
domain/sticker
domain/user
```

- [ ] **Step 2: Move domain tests**

Move:

```text
FriendDomainModelTest.java
GroupDomainModelTest.java
GroupDomainServiceTest.java
ImMessageServiceTest.java
ImSystemMessageTokenFactoryTest.java
ImSystemMessageTypeTest.java
PrivateDomainModelTest.java
```

- [ ] **Step 3: Compile domain and fix missing common dependencies**

Run:

```bash
mvn -pl im-domain test
```

Expected:

- Domain compiles with only `im-common`.
- Domain tests pass.

If a domain class references an application, endpoint, or infrastructure type, replace the dependency with a domain-owned interface or value object.

## Task 4: Move Application Code and Application Models

**Files:**
- Move: `src/main/java/com/co/kc/imchat/application/**` to `im-application/src/main/java/com/co/kc/imchat/application/**`
- Move: `src/main/java/com/co/kc/imchat/model/cqrs/**` to `im-application/src/main/java/com/co/kc/imchat/model/cqrs/**`
- Move: `src/main/java/com/co/kc/imchat/transformer/application/**` to `im-application/src/main/java/com/co/kc/imchat/transformer/application/**`
- Move domain-to-application DTO transformers from `transformer/domain/**` if they do not depend on DB entities
- Move application tests to `im-application/src/test/java/com/co/kc/imchat/application/**`
- Move application transformer tests to `im-application/src/test/java/com/co/kc/imchat/transformer/application/**`

- [ ] **Step 1: Move application services**

Move:

```text
application/ChatAppService.java
application/FriendAppService.java
application/GroupAppService.java
application/GroupMessageAppService.java
application/PrivateMessageAppService.java
application/UserAppService.java
```

- [ ] **Step 2: Move CQRS models**

Move all files under:

```text
model/cqrs/command
model/cqrs/query
model/cqrs/dto
model/cqrs/event
```

- [ ] **Step 3: Move application transformers**

Move:

```text
transformer/application/FriendAppTransformer.java
transformer/application/GroupAppTransformer.java
transformer/application/ImChatAppTransformer.java
transformer/application/ImMessageAppTransformer.java
transformer/application/UserAppTransformer.java
```

Inspect `transformer/domain/**`:

- If a mapper converts domain model to application DTO, move it to `im-application`.
- If a mapper converts DB entity to domain model, leave for Task 5 infrastructure.

- [ ] **Step 4: Move application tests**

Move:

```text
FriendAppServiceTest.java
GroupAppServiceTest.java
GroupChatAppServiceTest.java
PrivateChatAppServiceTest.java
ImChatAppTransformerTest.java
```

- [ ] **Step 5: Resolve application ports**

If application services depend on technical interfaces currently in `support/**`, place the interface in application when the application orchestrates it:

```text
ImMessageNotifier.java
ImMessageNotifierFactory.java
ImMessageNotifierInvoker.java
ImMessageConfirmable.java
ImMessageConfirmableScheduler.java
PasswordService.java
TokenService.java
DomainEventPublisher.java
```

If a port is domain-owned, move it to domain instead.

- [ ] **Step 6: Run application tests**

Run:

```bash
mvn -pl im-application test
```

Expected:

- Application compiles without depending on infrastructure or endpoint.
- Application tests pass.

## Task 5: Move Infrastructure Code, Resources, and SQL

**Files:**
- Move: `src/main/java/com/co/kc/imchat/infrastructure/domain/**` to `im-infrastructure/src/main/java/com/co/kc/imchat/infrastructure/domain/**`
- Move: `src/main/java/com/co/kc/imchat/infrastructure/mybatis/**` to `im-infrastructure/src/main/java/com/co/kc/imchat/infrastructure/mybatis/**`
- Move: `src/main/java/com/co/kc/imchat/infrastructure/support/**` to `im-infrastructure/src/main/java/com/co/kc/imchat/infrastructure/support/**`
- Move infrastructure configs to `im-infrastructure/src/main/java/com/co/kc/imchat/infrastructure/config/**`
- Move: `src/main/java/com/co/kc/imchat/transformer/db/**` to `im-infrastructure/src/main/java/com/co/kc/imchat/transformer/db/**`
- Move DB-domain transformers from `transformer/domain/**` to `im-infrastructure`
- Move: `src/main/resources/mapper/**` to `im-infrastructure/src/main/resources/mapper/**`
- Move: `src/main/resources/sql/ddl.sql` to `sql/ddl.sql`
- Move infrastructure tests to `im-infrastructure/src/test/java/**`

- [ ] **Step 1: Move MyBatis and repository implementations**

Move:

```text
infrastructure/domain
infrastructure/mybatis
```

- [ ] **Step 2: Move infrastructure support implementations**

Move:

```text
infrastructure/support
support/redis
support/auth implementations if any remain there
```

Keep interfaces in application/domain/common based on Task 4 decisions.

- [ ] **Step 3: Move infrastructure config**

Move:

```text
infrastructure/config/DatasourceConfig.java
infrastructure/config/RedisConfig.java
infrastructure/config/CacheConfig.java
infrastructure/config/properties/LogProperties.java
```

Do not move Web MVC/WebSocket config here; those belong to endpoint.

- [ ] **Step 4: Move lock infrastructure**

Inspect:

```text
support/lock/**
support/lock/aspect/**
support/lock/template/**
```

Recommended split:

- lock annotations, scenes, and key builders used by application can live in `im-common` or `im-application`
- Spring AOP aspect and Redisson template implementation belong in `im-infrastructure`

Move tests accordingly:

```text
DistributeLockAspectTest.java -> im-infrastructure
DistributeLockTemplateTest.java -> im-infrastructure
LockKeysTest.java -> im-common or im-application depending on LockKeys location
```

- [ ] **Step 5: Move DB resources and SQL**

Move:

```text
src/main/resources/mapper -> im-infrastructure/src/main/resources/mapper
src/main/resources/sql/ddl.sql -> sql/ddl.sql
```

Update DDL tests to read:

```text
sql/ddl.sql
```

- [ ] **Step 6: Move infrastructure tests**

Move:

```text
DatasourceConfigTest.java
RedisConfigTest.java
MysqlImGroupChatRepositoryTest.java
MysqlImGroupInboxMessageRepositoryTest.java
BaseEntityTest.java
DdlSchemaTest.java
MapperSqlTest.java
BaseMybatisServiceTest.java
ImMessageDelaySchedulerTest.java
ImMessageDbTransformerTest.java
ImChatDomainTransformerTest.java if DB-related
```

- [ ] **Step 7: Run infrastructure tests**

Run:

```bash
mvn -pl im-infrastructure test
```

Expected:

- Infrastructure compiles.
- Infrastructure tests pass.
- No endpoint dependency is required.

## Task 6: Move Endpoint Code and API Models

**Files:**
- Move: `src/main/java/com/co/kc/imchat/endpoint/**` to `im-endpoint/src/main/java/com/co/kc/imchat/endpoint/**`
- Move HTTP/WebSocket IO models from `src/main/java/com/co/kc/imchat/model/io/**` to `im-endpoint/src/main/java/com/co/kc/imchat/model/io/**`, except `Result.java`
- Move selected enums used by API messaging to `im-endpoint` unless they are infrastructure constants
- Move: `src/main/java/com/co/kc/imchat/transformer/http/**` to `im-endpoint/src/main/java/com/co/kc/imchat/transformer/http/**`
- Move endpoint web advice/config/context utilities to `im-endpoint`
- Move endpoint tests to `im-endpoint/src/test/java/**`

- [ ] **Step 1: Move endpoint adapters**

Move:

```text
endpoint/consumer
endpoint/http
endpoint/listener
endpoint/websocket
```

- [ ] **Step 2: Move API request/response models**

Move all remaining files under:

```text
model/io/**
```

except:

```text
model/io/Result.java
```

- [ ] **Step 3: Move HTTP transformers**

Move:

```text
transformer/http/FriendHttpIoTransformer.java
transformer/http/GroupHttpIoTransformer.java
transformer/http/ImChatHttpIoTransformer.java
transformer/http/ImMessageHttpIoTransformer.java
```

- [ ] **Step 4: Move endpoint configs and web support**

Move:

```text
infrastructure/advice/ErrorAdvice.java
infrastructure/advice/ResultAdvice.java
infrastructure/config/WebMvcConfig.java
infrastructure/config/WebSocketConfig.java
support/context/**
support/utils/HttpServletUtils.java
support/model/HttpLog.java
```

- [ ] **Step 5: Place remaining API enums**

Move API-facing enums to endpoint unless used elsewhere:

```text
ImChatTypeEnum.java
ImMessageStatusEnum.java
ImMessageTypeEnum.java
SessionStatusEnum.java
ParamsConstants.java
LogFormat.java
```

Move infrastructure messaging constants to infrastructure:

```text
RedisTopic.java
PushQueue.java
RedisKey.java
```

- [ ] **Step 6: Move endpoint tests**

Move:

```text
GroupHttpIoTransformerTest.java
ImChatHttpIoTransformerTest.java
```

If controller tests are later added, they belong here.

- [ ] **Step 7: Run endpoint tests**

Run:

```bash
mvn -pl im-endpoint test
```

Expected:

- Endpoint compiles without depending on infrastructure.
- Endpoint tests pass.

## Task 7: Create Bootstrap Module

**Files:**
- Move: `src/main/java/com/co/kc/imchat/ImChatApplication.java` to `im-bootstrap/src/main/java/com/co/kc/imchat/ImChatApplication.java`
- Move: `src/main/resources/application.yml` to `im-bootstrap/src/main/resources/application.yml`
- Move: `src/main/resources/logback-spring.xml` to `im-bootstrap/src/main/resources/logback-spring.xml`
- Move: `src/test/java/com/co/kc/imchat/ImChatApplicationTests.java` to `im-bootstrap/src/test/java/com/co/kc/imchat/ImChatApplicationTests.java`

- [ ] **Step 1: Move startup class and runtime resources**

Move:

```text
ImChatApplication.java
application.yml
logback-spring.xml
ImChatApplicationTests.java
```

- [ ] **Step 2: Configure scans**

Ensure startup class includes:

```java
@SpringBootApplication(scanBasePackages = "com.co.kc.imchat")
```

If mapper scanning is not already configured elsewhere, add:

```java
@MapperScan("com.co.kc.imchat.infrastructure.mybatis.mapper")
```

- [ ] **Step 3: Configure boot packaging**

`im-bootstrap/pom.xml` should apply `spring-boot-maven-plugin`.

- [ ] **Step 4: Run bootstrap test**

Run:

```bash
mvn -pl im-bootstrap test
```

Expected:

- Bootstrap tests compile.
- Spring context test passes if currently active.

## Task 8: Remove Empty Old Source Roots and Fix Resource References

**Files:**
- Remove empty directories under old `src/main/java`, `src/test/java`, and `src/main/resources`
- Modify tests that reference old paths
- Modify config if mapper locations or SQL paths changed

- [ ] **Step 1: Find remaining old files**

Run:

```bash
find src -type f | sort
```

Expected:

- No Java or resource files remain under old root except intentionally kept files.

- [ ] **Step 2: Fix hard-coded paths**

Search:

```bash
rg "src/main/resources/sql|src/main/resources/mapper|src/main/java/com/co/kc/imchat|src/test/java/com/co/kc/imchat" .
```

Update references to new module paths or `sql/ddl.sql`.

- [ ] **Step 3: Verify no forbidden module dependencies**

Run:

```bash
mvn -q dependency:tree
```

Manually inspect for:

- `im-domain` depending on application, endpoint, or infrastructure
- `im-application` depending on endpoint or infrastructure
- `im-endpoint` depending on infrastructure

## Task 9: Full Verification

**Files:**
- All modules

- [ ] **Step 1: Run full unit test suite**

Run:

```bash
mvn test
```

Expected:

- All tests pass.
- No module compilation failures.

- [ ] **Step 2: Run package verification**

Run:

```bash
mvn package
```

Expected:

- All modules package.
- `im-bootstrap` produces the runnable Spring Boot artifact.

- [ ] **Step 3: Run bootstrap repackage directly if needed**

Run:

```bash
mvn -pl im-bootstrap spring-boot:repackage
```

Expected:

- Repackage succeeds.

- [ ] **Step 4: Review git status**

Run:

```bash
git status --short
```

Expected:

- Only intended module split changes are present.
- No generated `target/` files are tracked.
- No accidental `.DS_Store` additions.

## Task 10: Optional Git Commit, Only With Explicit User Approval

**Files:**
- All changed files

- [ ] **Step 1: Ask user before committing**

Do not commit automatically. Ask:

```text
模块拆分已经完成并通过验证。是否现在提交 git？
```

- [ ] **Step 2: If user approves, stage and commit**

Suggested commit message:

```text
拆分IM工程为DDD多模块

1. 新增common、domain、application、infrastructure、endpoint和bootstrap模块
2. 按DDD边界迁移模型、服务、资源和测试
3. 调整Maven依赖、Spring装配和SQL脚本目录
```

Run only after explicit approval:

```bash
git add -A
git commit -m "拆分IM工程为DDD多模块" -m "1. 新增common、domain、application、infrastructure、endpoint和bootstrap模块
2. 按DDD边界迁移模型、服务、资源和测试
3. 调整Maven依赖、Spring装配和SQL脚本目录"
```
