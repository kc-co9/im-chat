# Coding Guide

本规范是新增和修改代码的稳定基线，优先保证职责清晰、依赖方向稳定和行为可验证。历史代码按实际触碰范围渐进收敛，不为了统一格式进行无关重构。自动检查范围见 [Harness Guide](HARNESS_GUIDE.md)。

## Java 与可读性

- 使用 Java 21 和显式类型，不使用 `var`。
- 一个类只承担一个清晰职责；类名表达角色，公开方法名使用调用方能够理解的业务动作。
- 包按稳定职责和依赖方向组织。使用 `model`、`codec`、`spi`、`client`、`repository` 等已有语义，不把能力接口、技术实现和数据模型堆在同一包，也不按类数量机械拆包。
- 方法优先提前返回。复杂条件提取为能够表达业务判断的方法，避免嵌套分支和难以阅读的 Stream 链。
- Stream 用于纯转换、过滤和聚合；删除、异常隔离、多步副作用和状态修改使用普通循环。
- 集合返回不可变结果；无结果使用空集合或 `Optional.empty()`，不返回 `null`。只有明确允许空值的回调或框架契约才返回 `null`。
- 公共契约、配置项、复杂业务规则、生命周期和非显然失败行为提供简洁 Javadoc。自解释的私有方法、getter 和简单 Controller 不写重复注释。
- 日志使用项目统一 Logger，不输出密码、Access/Refresh Token、指纹、完整消息内容或用户隐私。

## DDD 分层

业务服务使用以下职责边界：

```text
interfaces -> application -> domain
                    |          ^
                    v          |
               adapter   infrastructure
```

- `interfaces` 只做协议适配、反序列化、边界校验和结果序列化，不承载业务编排。
- `application` 编排用例、事务、锁和跨领域协作，不包含领域规则，也不暴露基础设施细节。
- `domain` 保存业务状态和规则，不依赖 Spring、HTTP、Bolt、Dubbo、Nacos、Redis、Mapper 或持久化实体。
- `infrastructure` 实现仓储、技术型领域能力、缓存、消息和外部集成。
- `adapter` 将外部服务能力转换为当前业务可理解的语义；它不是 inbound Controller/Handler 的替代名称。
- `transformer` 负责接口、应用、领域、持久化和 SDK 模型之间的边界转换。

领域模型和领域服务的业务标识、金额和状态载体使用值对象或枚举，不直接传播 `Long`、`String`、`Boolean` 等基础类型。时间类型和枚举可以直接使用；值对象内部、接口 DTO、CQRS 模型、持久化 DTO 和事件边界允许承载基础值，并由 Transformer 转换。

领域服务必须承载跨实体规则、多个领域对象协作或不自然属于单个聚合的业务决策。纯 codec、digest、Redis、HTTP 或委托调用不是领域服务；JWT、HMAC、BCrypt 等实现放在 `infrastructure/domain/service`，领域接口只表达签发、认证、密码校验等业务语义。不引入仓库未采用的 `application/port`、`application/model` 平行体系。

聚合从持久化状态重建时使用 Builder 或明确的重建数据对象承载多字段状态。Transformer 可以直接填充 Builder；只有重建包含业务决策或多对象协作时才进入领域服务。不要为简单 `new` 或 `build()` 增加无价值的委托方法。

当前 Broker 的少量历史 `domain` 类仍兼有 Spring 装配和运行编排职责，不是新增代码模板。修改时按风险渐进迁移，不为满足文档进行无关包移动。

## 应用服务与 CQRS

- 应用服务以 `*AppService` 表达一个边界内的用例编排；同一聚合的紧密命令可以合并在一个应用服务中。
- 公开用例方法接收单个 Command、Query、Params 或其他明确输入对象，不直接接收 `String`、基本类型等标量业务参数，也不保留无调用方的标量重载。
- 查询和有业务结果的命令返回 DTO、Result 或其他结果对象；没有结果的命令返回 `void`。
- 应用服务之间原则上不形成层层委托。共享业务规则下沉领域对象或领域服务，共享技术步骤提取为合适的基础设施组件；确需用例组合时保持事务和所有权清晰。
- 事务与分布式锁属于应用或基础设施边界。优先使用项目已有注解；只有动态锁键、需要返回值或注解无法表达时使用模板。
- 不增加仅供单元测试使用的生产构造器、兼容方法、wrapper 或 no-op/fake 实现。测试通过正式 Bean、`@TestConfiguration` 或直接依赖替身控制环境。

## Adapter 与跨服务边界

- Adapter 的公开业务输入和输出使用领域对象、聚合或领域结果，外部 SDK 的 Params、Result 和 DTO 只停留在 Adapter 内部。
- 纯命令 Adapter 可以返回 `void`，不为了形式统一创建空结果对象。
- Facade/SDK 只定义跨模块公共契约，不依赖 server 实现。Bolt、Dubbo 等框架类型只出现在 client、RPC adapter、configuration 或 infrastructure/support。
- Nacos 类型只出现在 `im-nacos`、Broker SDK 的服务发现封装和应用配置边界，不进入业务、domain 或 facade 契约。
- RPC Handler 解析 typed params、调用应用或领域门面并返回 typed result，不实现路由、迁移或 Gossip 算法。
- 远程调用明确超时、失败日志、幂等条件和失败策略。写操作只有具备稳定幂等键且确认重复执行安全时才自动重试。

## Spring、配置与 Bean

- 依赖使用构造器注入，不使用字段注入。只有多个正式构造入口且需要明确 Spring 入口时使用 `@Autowired`。
- `im.*` 下多个相关配置使用 typed `@ConfigurationProperties`；单个第三方桥接值可以使用 `@Value`。配置扫描优先使用 `@ConfigurationPropertiesScan`。
- 本地配置提供可运行的非生产默认值，部署配置可以由 Nacos 覆盖。密钥、凭证和环境私有值不得以生产值提交到仓库。
- 条件 Bean 的提供者和消费者必须形成闭合装配：消费者启用时，其直接依赖必须由同一条件、前置条件或明确的自定义 Bean 保证存在。新增条件装配需要覆盖启用、关闭、覆盖 Bean 和缺失必要配置的启动测试。
- `@Bean` 方法按能力边界分组；声明 Bean 的配置类不承载密钥派生、随机生成或业务决策等实现逻辑，应委托给有明确职责的组件。
- `@ConditionalOnMissingBean` 用于允许应用覆盖默认实现，`@ConditionalOnBean` 和 `@ConditionalOnProperty` 用于表达真实前置条件，不用条件注解掩盖缺失的必需依赖。
- 时间、随机数、ID 生成器和执行器按业务需要抽象。不要只为了测试注入一个会显著降低生产代码可读性的依赖；优先在确定性边界、codec 或基础设施组件中控制它。

## 类型、Lombok 与构造

- 只存储属性且不需要可变绑定的 DTO、参数、结果、事件和值载体优先使用 `record`。
- 需要 Spring 可变绑定、ORM、协议无参构造或 setter 的类型使用普通 JavaBean，并用 Lombok 生成简单 getter、setter 和纯字段构造器。
- `@RequiredArgsConstructor` 用于没有额外构造逻辑的组件；存在校验、派生字段、防御性复制或多个正式入口时保留显式构造器。
- `@Data` 只用于确实需要完整可变 Bean 语义的配置、请求、实体或兼容模型。领域值对象使用 record 或显式行为，避免无意生成 setter 和包含敏感字段的 `toString`。
- 不使用 `@SneakyThrows` 隐藏边界异常。受检异常在合适边界转换为项目异常、记录或继续声明。

## Transformer 与 JSON

- 声明式字段映射使用 MapStruct 生成代码；不要保留一个全部由 handwritten default 方法组成的 `@Mapper` 接口。
- MapStruct 可以直接转换同名枚举和普通属性。安全校验、身份覆盖、条件分支和异常转换使用显式方法。
- HTTP 请求响应由 Spring/Jackson 绑定；Bolt、Gossip、缓存载荷等显式 JSON 转换统一使用 `JsonUtils`，生产代码不自行创建 `ObjectMapper`。
- `JsonUtils.getMapper()` 是进程共享实例，只用于第三方组件接入，调用方不得重新配置。

## 持久化与缓存

- Repository 接口表达领域查询和保存语义，不暴露锁、compare-and-set 实现、Redis key、Mapper 或缓存 builder。
- MyBatis Mapper、Redis/JetCache/Redisson 和数据库实体只属于 infrastructure。
- 锁和事务负责保护应用用例；聚合负责校验业务前置条件和执行状态变化。不要把技术锁操作伪装成 Repository 领域方法。
- 缓存明确 key、TTL、失效策略和不可用行为。除非设计明确，缓存不是事实数据的唯一来源。
- SQL 遵循独立的 [SQL Guide](SQL_GUIDE.md)。

## 指标、事件与异常

- 指标使用稳定的小写点分隔名称和有限标签，不使用 userId、sessionVersion、connectionId、Token 等高基数或敏感值。
- Gauge 表示当前状态，Counter 表示累计事件，Timer 表示耗时。重复的计数和耗时样板优先通过 `im-metrics` 的注解能力隔离。
- 指标、日志增强和 Gossip 通知等旁路能力失败时记录异常并隔离，不改变核心业务结果；安全、持久化和业务校验异常不得被静默吞掉。
- 领域事件携带不可变领域值，不传播 HTTP request、RPC params、持久化对象或 Spring 事件类型。
- 领域内同步事件可以参与当前事务；集成事件在事务提交后发布，失败进入明确的重试、补偿或可靠消息策略。

## Web 与 API

- Controller 获取认证上下文、完成 HTTP 输入输出和边界校验，然后调用应用服务。
- 请求和响应模型放在接口模型包，不直接返回数据库实体或领域聚合。
- 统一异常处理器负责把业务异常转换为错误码和响应；Controller 不重复拼装失败结果，也不用 `valid=false` 表达本应抛出的认证失败。
- Swagger 注解描述公开 API 意图和约束，不替代参数校验。

## 命名

- `Params`/`Result` 用于跨边界输入输出，`DTO` 用于传输结果，`Command`/`Query` 用于应用意图，`Event` 表示已经发生的事实。
- `Registry` 表示注册状态读写，`Store` 表示技术状态存储，`Repository` 表示聚合持久化抽象，`Client` 表示外部调用端。
- `Service` 必须表达业务决策或稳定能力，避免给纯委托类套用 Service 后缀。
- 布尔方法使用 `is`、`has`、`can`、`matches` 或 `should`；查询使用 `find`、`list`、`get`，修改使用具体动作词。
- 名称从调用者需要理解的语义出发。实现细节留在内部，不把 `afterCommit`、锁策略或存储方式拼入不需要知道这些细节的公开方法名。
- 不通过重载、兼容构造器或 wrapper 保留没有调用方的旧 API；确有迁移需要时记录调用方和退出条件。

## 自动检查与 Review

自动规则只覆盖低误报模式，具体矩阵见 [Harness Guide](HARNESS_GUIDE.md)。当前脚本检查显式类型、字段注入、生产测试替身、控制台输出、手动 `ObjectMapper`、成组简单 getter/setter、AppService 标量入参和明显职责混包；ArchUnit 检查稳定依赖方向。

领域服务尺度、值对象建模、Adapter 输入输出、构造器业务逻辑、条件 Bean 是否形成完整能力、方法可读性和注释价值由 Review 判断。不要为了自动化增加“每包最多 N 个类”、禁止所有 getter、禁止所有构造器或方法长度等高误报规则。

确需例外时在代码附近说明原因，并在 Review、Harness 反馈或技术债务中记录退出条件；不得通过关闭规则或扩大排除范围消除反馈。
