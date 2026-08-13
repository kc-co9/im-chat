# IM Module Split Design

## Background

The project is currently a single Maven module. Packages already roughly follow DDD layers, but all code, dependencies, resources, and tests are built as one artifact. This makes the project feel bulky and does not enforce layer boundaries at compile time.

The goal is to split the project into physical Maven modules that match DDD layering, while keeping the root project name and artifact identity as `im-chat`.

## Goals

- Convert the root `im-chat` Maven project into a parent/aggregator project.
- Add modules for common, domain, application, infrastructure, endpoint, and bootstrap concerns.
- Enforce strict compile-time dependency direction between DDD layers.
- Split existing `model` and `transformer` packages by layer boundary instead of keeping separate model or transformer modules.
- Keep SQL scripts at the repository root under `sql/`.
- Preserve current behavior while moving code.

## Non-Goals

- Do not redesign business behavior.
- Do not rename all Java packages as a separate style cleanup.
- Do not introduce new runtime frameworks.
- Do not create separate deployable services.

## Module Structure

The root `pom.xml` remains `artifactId=im-chat` and changes to `packaging=pom`.

Maven modules:

```text
im-chat
  im-common
  im-domain
  im-application
  im-infrastructure
  im-endpoint
  im-bootstrap
```

Repository-level SQL directory:

```text
sql/
```

`sql/` is not a Maven module.

## Dependency Direction

```text
im-bootstrap
  -> im-endpoint -> im-application -> im-domain -> im-common
  -> im-infrastructure -----------^              -> im-common
```

Rules:

- `im-domain` depends only on `im-common`.
- `im-application` depends on `im-domain` and `im-common`.
- `im-infrastructure` depends on `im-domain`, `im-application` when implementing application ports, and `im-common`.
- `im-endpoint` depends on `im-application`, `im-domain`, and `im-common`.
- `im-bootstrap` depends on `im-endpoint` and `im-infrastructure`.
- Lower layers must not depend on endpoint or infrastructure implementation details.

## Module Responsibilities

### im-common

Contains cross-layer basic capabilities:

- Common exceptions.
- `Result`, `ResultCode`, `ErrorCode`.
- Generic utilities that do not depend on Servlet, Redis, MyBatis, or Spring Web.
- Generic state machine support.
- MapStruct marker annotations.
- Generic function interfaces.
- Generic enums such as `BaseEnum`.

`im-common` should avoid Spring Boot starters and infrastructure dependencies.

### im-domain

Contains pure domain code:

- Domain models, value objects, domain services, and domain events.
- Repository interfaces.
- Domain-specific enums and policies.

`im-domain` must not depend on Spring Web, MyBatis, Redis, application services, endpoint request models, or infrastructure implementations.

### im-application

Contains application orchestration:

- Application services.
- CQRS command, query, DTO, and application-level event models.
- Application transformers.
- Application ports used by orchestration services.

Application services should depend on domain repository interfaces and ports, not infrastructure implementations.

### im-infrastructure

Contains technical implementations:

- MyBatis entities, mappers, services, and mapper XML files.
- MySQL repository implementations.
- Redis repository implementations and Redis publisher/subscriber implementations.
- JWT, BCrypt, Spring event publisher, cache, data source, Redis, and MyBatis configuration.
- DB transformers.

`im-infrastructure` may implement domain repository interfaces and application ports.

### im-endpoint

Contains inbound adapters:

- HTTP controllers.
- WebSocket controllers.
- Consumers and listeners.
- HTTP and WebSocket request/response models.
- HTTP transformers.
- Web MVC/WebSocket config.
- Global result and error advice.
- Request user context and Servlet utilities.

Endpoint code must not directly depend on infrastructure implementations.

### im-bootstrap

Contains runtime assembly:

- `ImChatApplication`.
- Final Spring Boot packaging configuration.
- `application.yml`.
- `logback-spring.xml`.
- Component scan and MyBatis mapper scan configuration.

Bootstrap wires endpoint and infrastructure into one runnable application.

## Existing Code Mapping

### Common

Move to `im-common`:

- `support/exception/**`
- `support/state/**`
- `support/mapstruct/**`
- `support/function/**`
- `support/utils/DateUtils.java`
- `support/utils/FunctionUtils.java`
- `support/utils/GeneratorUtils.java`
- `support/utils/HashUtils.java`
- `support/utils/JsonUtils.java`
- `support/utils/ReflectUtils.java`
- `support/constant/ErrorCode.java`
- `support/constant/ResultCode.java`
- `model/io/Result.java`
- `model/enums/BaseEnum.java`

Do not move to common:

- Servlet, HTTP, and network utilities.
- Redis constants and topics.
- Request context classes.

### Domain

Move to `im-domain`:

- `domain/**`
- Repository interfaces.
- Domain events.
- Domain-specific enum models when they are not purely API models.

### Application

Move to `im-application`:

- `application/**`
- `model/cqrs/command/**`
- `model/cqrs/query/**`
- `model/cqrs/dto/**`
- `model/cqrs/event/**` when the event is application-level.
- `transformer/application/**`
- Domain-to-application DTO transformers from `transformer/domain/**`.
- Notifier and scheduling ports if application services directly orchestrate them.

### Infrastructure

Move to `im-infrastructure`:

- `infrastructure/domain/**`
- `infrastructure/mybatis/**`
- `infrastructure/support/**`
- `infrastructure/config/DatasourceConfig.java`
- `infrastructure/config/RedisConfig.java`
- `infrastructure/config/CacheConfig.java`
- `transformer/db/**`
- `support/redis/**`
- Infrastructure implementations of password, token, event publisher, notifier, and scheduler ports.
- `src/main/resources/mapper/**`

### Endpoint

Move to `im-endpoint`:

- `endpoint/**`
- HTTP and WebSocket request/response models from `model/io/**`, except common `Result`.
- `transformer/http/**`
- `infrastructure/advice/ErrorAdvice.java`
- `infrastructure/advice/ResultAdvice.java`
- `infrastructure/config/WebMvcConfig.java`
- `infrastructure/config/WebSocketConfig.java`
- `support/context/**`
- `support/utils/HttpServletUtils.java`
- `support/model/HttpLog.java`

### Bootstrap

Move to `im-bootstrap`:

- `ImChatApplication.java`
- `application.yml`
- `logback-spring.xml`

## Resource Layout

- MyBatis mapper XML files move to `im-infrastructure/src/main/resources/mapper`.
- Runtime config files move to `im-bootstrap/src/main/resources`.
- SQL scripts move to repository root:

```text
sql/ddl.sql
```

Tests that validate DDL should read `sql/ddl.sql` from the repository root.

## Maven Configuration

Root `pom.xml`:

- Uses `packaging=pom`.
- Declares modules.
- Keeps shared version properties.
- Moves shared dependency versions to `dependencyManagement`.
- Moves compiler and annotation processor configuration to plugin management.

Submodules:

- Declare only the dependencies they need.
- Configure annotation processing for MapStruct and Lombok where needed.
- Use Spring Boot packaging only in `im-bootstrap`.

Recommended dependency allocation:

- `im-common`: Lombok, commons libraries, Jackson if needed by `JsonUtils`.
- `im-domain`: `im-common`, Lombok, limited commons dependencies.
- `im-application`: `im-domain`, `im-common`, Spring context annotations, MapStruct.
- `im-infrastructure`: `im-domain`, `im-application`, `im-common`, MyBatis, MyBatis-Plus, Redis, Redisson, Druid, MySQL runtime, JWT, BCrypt, Spring event/cache/config dependencies.
- `im-endpoint`: `im-application`, `im-domain`, `im-common`, Spring Web, WebSocket, Validation, Swagger.
- `im-bootstrap`: `im-endpoint`, `im-infrastructure`, Spring Boot starter and Spring Boot Maven plugin.

## Spring Assembly

`im-bootstrap` owns application startup.

The startup class should scan the shared root package:

```java
@SpringBootApplication(scanBasePackages = "com.co.kc.imchat")
```

MyBatis mapper scan should point to infrastructure mappers:

```java
@MapperScan("com.co.kc.imchat.infrastructure.mybatis.mapper")
```

The current root package can remain `com.co.kc.imchat` during this migration to reduce diff size. Package renaming can be a later refactor.

## Test Migration

- Domain unit tests move to `im-domain/src/test`.
- Application service tests move to `im-application/src/test`.
- Infrastructure repository, MyBatis, config, and DDL tests move to `im-infrastructure/src/test`.
- Endpoint transformer and controller tests move to `im-endpoint/src/test`.
- Full Spring Boot integration tests move to `im-bootstrap/src/test`.

Tests should not force lower modules to depend on higher modules. Use mocks or test-specific configuration when a lower module needs a collaborator.

## Verification Plan

Run verification incrementally:

1. After each major migration batch, run module-specific tests:

```bash
mvn -pl <module> test
```

2. After all modules compile, run the full test suite:

```bash
mvn test
```

3. Confirm final boot artifact packaging:

```bash
mvn -pl im-bootstrap spring-boot:repackage
```

or:

```bash
mvn package
```

## Risks

### Circular Dependencies

Resolve cycles by moving interfaces upward:

- Repository interfaces stay in domain.
- Application orchestration ports stay in application.
- Technical implementations stay in infrastructure.
- Endpoint request/response models stay out of application and domain.

### MapStruct Generation

Each module that contains MapStruct mappers must have annotation processor configuration available.

### Spring Context Failures

If tests fail because beans are missing, place tests in the module that owns the real dependencies or use test mocks. Do not add reverse module dependencies to satisfy tests.

### Resource Lookup

Mapper XML lookup must include infrastructure resources. DDL tests must read `sql/ddl.sql` from the repository root.

### Large Diff

This is expected because the migration physically moves many files. Keep behavior unchanged and use tests to verify equivalence.
