# Coding Guide

本规范以当前仓库已经形成的代码风格为基线，优先保证职责清晰、依赖方向稳定和行为可验证。它不是要求一次性格式化全仓的样式清单；新增和修改代码应遵循，历史代码按实际触碰范围渐进收敛。

## General Java Style

- 使用 Java 21 语法和显式类型，不使用 `var`。
- 一个类只承担一个清晰职责。类名表达角色，方法名使用动词加名词，例如 `registerGateway`、`findMigrationConnections`、`publishEvent`。
- 方法优先通过提前返回降低嵌套；条件分支应表达业务规则，而不是隐藏在复杂 Stream 链中。
- 对有状态流程优先使用普通 `for`，当循环需要删除、异常隔离或多步副作用时，不使用 `forEach` 隐藏控制流。
- Stream 适合纯转换、过滤和聚合；遇到日志、异常处理、状态修改或调试需要时使用普通循环。
- 集合返回值优先使用不可变结果；空结果使用 `List.of()`、`Set.of()` 或 `Optional.empty()`，避免返回 `null`。
- DTO、参数、结果和事件优先使用 `record`；需要可变绑定的配置、表单和持久化对象才使用普通类。
- 公共契约、配置项、复杂业务规则、生命周期和非显然失败行为补充简洁 Javadoc。自解释的实现方法、getter 和简单 Controller 不要求重复注释。
- 日志使用项目统一的 SLF4J/Lombok Logger；不得输出凭证、Token、完整消息内容或用户隐私。

## Layer Responsibilities

- `interfaces` 只做协议适配、反序列化、参数校验和结果序列化，不承载业务编排。
- `application` 编排用例、事务和跨领域协作，不直接暴露基础设施细节。
- `domain` 保存业务规则和领域状态；新增领域模型和领域服务不依赖 Spring、HTTP、Bolt、Dubbo、Nacos、Redis 或持久化实体。
- `infrastructure` 实现仓储、远程适配器、消息和缓存等外部能力。
- `lifecycle` 负责启动、定时刷新、注销和恢复等生命周期动作；具体业务决策委托给 service。
- `registry` 接口描述领域存储操作，不暴露 Redis key、Gossip entry 或缓存 builder 等实现细节。
- `support` 放技术支撑、事件发布、客户端和监控，不反向承载领域决策。
- `transformer` 负责边界模型转换。简单声明式映射使用 MapStruct 生成代码，复杂且包含校验或安全覆盖的转换保留显式实现并说明原因。

当前 Broker 的部分 `domain` 类仍兼有 Spring 装配和运行编排职责，这是历史结构，不是新增代码的参考模板。修改相关代码时优先把协议、配置和客户端依赖留在 application/support/lifecycle 边界；只有在职责清晰且测试覆盖充分时才渐进迁移现有类，不为了满足文档进行无关的包移动。

## Spring And Lombok

- 依赖默认使用构造器注入；只有存在多个构造器且需要明确 Spring 入口时才在构造器上使用 `@Autowired`。
- 不使用字段注入。时间、随机数、ID 生成器和执行器等非确定性依赖通过正式抽象注入；Spring 测试替换使用 `@TestConfiguration` 或测试 Bean，不增加仅供测试调用的生产构造器。
- `im.*` 业务配置使用 typed `@ConfigurationProperties` 并提供合理的本地默认值；Spring 或第三方框架的单个桥接值可以使用 `@Value`。同一前缀存在多个项目配置时聚合为 properties。
- 配置扫描优先使用 `@ConfigurationPropertiesScan`；不要在每个配置类重复声明同一组 properties。
- `@Bean` 方法按能力或组件边界分组，条件装配使用 `@ConditionalOnMissingBean`、`@ConditionalOnBean` 和属性条件保证可替换性。
- `@RequiredArgsConstructor` 适合无额外构造逻辑的组件；需要校验、派生字段或多个正式构造入口时使用显式构造器。
- `@Data` 只用于确实需要完整可变 bean 语义的配置、请求、实体或兼容模型；领域值对象优先使用 record 或显式方法，避免无意生成 setter、equals 和 toString。
- 不使用 `@SneakyThrows` 隐藏边界异常；异常应在适当层转换、记录或继续抛出。

## Component Usage

### Json and Mapping

- HTTP 请求响应由 Spring/Jackson 正常完成绑定；Bolt、Gossip、缓存载荷等需要显式 JSON 转换的场景统一使用 `JsonUtils`，业务代码不得自行创建 `ObjectMapper`。
- `JsonUtils.getMapper()` 返回进程级共享实例，只用于第三方组件接入；调用方不得重新配置或修改该实例。
- MapStruct 接口必须实际使用生成的映射方法，并保留 `Mappers.getMapper(...)` 或 Spring component model 的一致模式。
- MapStruct 不适合承载安全校验、用户身份覆盖、条件分支和异常转换；这些逻辑放在显式 transformer 或 domain/application 层。

### RPC And Messaging

- SDK/facade 保持传输中立，只定义跨模块契约；Bolt/Dubbo 框架类型只出现在 client、RPC adapter、configuration 或 infrastructure/support 边界。
- Nacos 类型只出现在 `im-nacos` 插件和应用配置层；业务、domain、facade 和 SDK 不直接依赖 Nacos。
- Redis/JetCache/Redisson 类型只出现在对应插件和 infrastructure；Gossip 通用类型属于 `im-gossip`，Broker 只在同步适配和状态投影边界使用。
- RPC handler 负责解析 typed params、调用门面并返回 typed result；不要在 handler 内实现迁移、路由和 Gossip 算法。
- 跨服务公共模型放在 facade/sdk；不得让调用方依赖服务端实现类。
- 远程调用必须明确超时、失败日志、幂等条件和失败策略；不得在业务层散落地址字符串和协议细节。查询可以按策略重试，写操作只有具备稳定幂等键且确认重复执行安全时才能自动重试。

### Persistence And Cache

- Repository 接口表达业务查询和保存语义，MyBatis mapper、Redis key、JetCache builder 和 Redisson 对象只属于 infrastructure。
- 缓存必须明确 key、TTL、失效策略和缓存不可用时的行为；缓存不是事实数据的唯一来源，除非设计文档明确说明。
- 事务边界由 application/infrastructure 负责，domain 不使用事务注解或数据库对象。

### Metrics And Events

- 指标使用 Micrometer 的稳定小写点分隔名称，例如 `im.broker.connections`；标签数量保持有限，不使用 userId、connectionId 等高基数值。
- Gauge 表示当前状态，Counter 表示累计事件，Timer 表示耗时；不要用 Gauge 模拟计数器。
- 业务事件只携带不可变领域值，不传播 HTTP request、RPC params 或持久化对象，也不以 Spring 事件类型作为业务载荷。ApplicationReadyEvent 等纯生命周期信号只在 lifecycle/configuration 边界使用。
- Publisher 负责发布，Listener 负责响应。领域内同步事件失败可以回滚当前事务；集成事件在事务提交后发布，失败进入重试、补偿或可靠消息流程；监控、Gossip 通知等旁路事件失败应隔离并记录，不阻塞核心业务路径。

### Web And API

- Controller 使用构造器注入，负责 HTTP 输入输出和认证上下文获取，调用 application service。
- 请求和响应模型放在接口层或服务模型包，不把数据库实体直接作为 API 返回值。
- REST 路径、参数和状态码保持同一服务内一致；错误由统一异常处理器转换，Controller 不重复拼装错误响应。
- Swagger 注解描述公开 API 的意图和约束，不以注释代替参数校验。

## Naming And Modeling

- `Params` 表示跨边界输入，`Result` 表示跨边界输出，`DTO` 表示传输模型，`Event` 表示已发生事实，`Command`/`Query` 表示应用动作意图。
- `Registry` 表示注册信息的读写抽象，`Store` 表示状态存储，`Service` 表示业务编排或决策，`Client` 表示外部调用端。
- 不为了“统一”给内部状态对象、方法或参数套用无意义后缀；命名应从调用者和所有权出发。
- 布尔方法使用 `is`、`has`、`can` 或 `should` 开头；查询方法使用 `find`、`list`、`get`，修改方法使用 `register`、`update`、`remove`、`publish` 等动作词。
- 不通过重载、兼容构造器或 wrapper 保留没有调用方的旧 API；确有迁移需要时在设计文档记录退出条件。

## Automated Rules And Exceptions

`./scripts/check-drift.sh` 只自动检查低误报规则：禁止生产代码中的 `var`、测试替身、控制台输出、手动创建 `ObjectMapper` 和字段注入。以下内容由 Review 判断，不机械门禁：方法长度、Stream 与 `for` 的选择、`@Data` 使用、`@Value` 数量、通配符 import、Mock 数量和注释数量。

确需例外时，代码附近说明原因，并在 Review 或技术债务记录中说明退出条件；不要通过关闭规则或扩大排除范围消除反馈。
