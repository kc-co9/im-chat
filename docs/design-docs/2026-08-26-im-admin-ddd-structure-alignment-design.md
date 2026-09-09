# IM Admin DDD 结构对齐设计

## 背景

`im-management/im-admin` 是独立的管理限界上下文，拥有管理员认证、RBAC、审计、Admin 数据库、Redis Session 和管理 UI；普通用户事实仍由 Account 持有，并通过 `im-account-admin-facade` 管理。运行时所有权与 `im-service` 不同，不代表内部 DDD 分层可以采用另一套工程结构。

当前 `im-admin` 已具备 `adapter/application/domain/infrastructure/interfaces/model/transformer` 主体，但配置、生命周期、Redis Repository、分页、CQRS 边界和转换职责没有完全遵循 Account Server 已收敛的结构。

## 目标

- 保持 `im-admin` 位于 `im-management`，不改变部署、数据所有权、HTTP 契约和业务行为。
- 一级分层及技术实现落点与 `im-account-server` 对齐。
- 保留 Admin 内部 administrator、authentication、role、audit、user 等合理领域细分。
- 统一使用公共 `Paging/PagingResult`、应用 DTO 和 Transformer。
- 让 CQRS 输入只承载边界值，应用服务入口再构造领域对象。
- 让 Application 不依赖 Spring `@ConfigurationProperties` 类型。
- 让 ArchUnit 真正扫描并约束 `im-management`。

## 目标结构

```text
com.co.kc.imchat.management.admin
├── adapter
├── application
│   ├── audit
│   └── lock
├── domain/<capability>/{model,repository,service}
├── infrastructure
│   ├── config/{beans,properties}
│   ├── domain/{repository,service}
│   ├── lifecycle
│   └── mybatis/{entity,enums,mapper,query,service}
├── interfaces/http/<capability>
├── model/{cqrs/{command,dto,query},io}
└── transformer/{application,db,domain,interfaces}
```

HTTP 子包按能力保留；统一的是稳定分层和依赖方向，不按目录数量机械压平。

## 结构迁移

- `adapter/account/AccountAdminAdapter` 移至 `adapter`。
- 根 `config/properties` 移至 `infrastructure/config/properties`。
- `infrastructure/config` 中 Bean 配置移至 `infrastructure/config/beans`。
- 根 `lifecycle` 移至 `infrastructure/lifecycle`。
- `infrastructure/redis/RedisAdminSessionRepository` 移至 `infrastructure/domain/repository`。
- 应用 DTO 转换放入 `transformer/application`，数据库转换放入 `transformer/db`。

## 模型与依赖

- 分页 Query 直接持有 `Paging`，删除 `PageBounds`。
- Repository 和应用服务返回 `PagingResult<T>`，删除重复的领域分页容器。
- Command/Query 不持有聚合、业务值对象、领域状态或 Repository 查询条件；审计上下文改为应用边界值对象，在应用服务中转换为领域审计请求。
- AppService 查询和有结果命令返回 Application DTO，不把聚合直接交给 Controller。
- `AdminSecurityProperties` 只在基础设施装配层出现；应用服务依赖纯 Java 的认证策略值对象。
- `domain/user` 继续作为 Account Admin Facade 的本地防腐模型，不拥有 Account 持久化和状态机。

## Harness 与验证

- ArchUnit 导入 `com.co.kc.imchat.management`，检查 Admin domain 不依赖外层、Admin application 不依赖本模块 config/infrastructure/interfaces/lifecycle、AppService 不相互依赖。
- 结构调整以现有单元、Repository、MockMvc、启动测试证明行为不变。
- 使用 focused Maven、`verify.sh affected`、`verify.sh quick` 和最终 `verify.sh full` 验证。

## 非目标

- 不新增 Admin Facade/SDK。
- 不改变数据库表、Redis Key、Cookie、权限码、审计语义或 HTTP URL。
- 不重构前端交互。
- 不引入兼容构造器、测试专用生产 API 或新的基础设施抽象。
