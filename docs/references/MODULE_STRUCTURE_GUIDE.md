# 模块结构规范

本规范用于决定一个可部署模块或可复用模块应采用 DDD、技术分层，还是二者组合的结构。目标不是让所有模块目录相同，而是让业务规则、用例编排和技术实现各归其位，使依赖方向稳定、代码可测试、维护者能够从目录判断职责。

## 核心原则

结构由问题复杂度决定，不由项目名称、类数量或“看起来规范”决定。

- 有稳定业务语言、业务不变量、生命周期和跨对象决策时，使用 DDD 核心。
- 主要处理协议、运行时状态、缓存、注册发现、转发、编解码或观测时，使用技术分层。
- 同一进程同时包含复杂业务决策和技术运行能力时，使用“DDD 核心 + 技术子系统”，不要把整个进程强行包装成一个领域模型。
- 包名表达职责和依赖方向。只有真实存在的职责才建立目录，不为形式补齐空层。
- 一个 module 对外暴露小而完整的 interface；复杂度应隐藏在 implementation 内，不通过大量透传类型扩散给调用方。

## 选择方法

先回答以下问题：

1. 业务人员或产品是否会使用一组稳定术语描述这里的状态与行为？
2. 是否存在必须始终成立、且不能只靠数据库约束或接口校验表达的不变量？
3. 对象是否有独立身份、生命周期、状态转换或一致性边界？
4. 是否存在跨实体、跨聚合或跨资源的业务决策？
5. 主要变化来源是业务规则，还是框架、协议、存储和部署技术？

前四项多为“是”，并且第五项以业务变化为主时，采用 DDD。前四项多为“否”，第五项以技术变化为主时，采用技术分层。两类复杂度同时存在时采用混合模式。

不要仅因为以下情况使用 DDD：

- 有数据库表或 DTO；
- 类较多；
- 需要一个 `service`；
- 希望未来可能扩展；
- 技术状态可以画成状态机。

技术状态机不自动成为领域模型。连接池状态、Gossip observation、缓存水位、重试窗口和诊断记录通常仍是技术运行状态。

## DDD 模块

### 适用场景

账号、社交关系、消息业务、IAM 授权等模块具有明确业务语言、不变量和生命周期，适合 DDD。领域层表达“允许发生什么”，应用层表达“一个用例如何完成”。

### 推荐结构

```text
<module>/
  interfaces/                  # HTTP/RPC/message 入站适配
  application/                 # command/query 用例、事务和跨领域编排
  domain/
    model/                     # 聚合、实体、值对象、领域事件
    service/                   # 不自然属于单一聚合的业务决策
    repository/                # 聚合生命周期 interface
  adapter/                     # 外部业务能力到本领域语义的转换
  infrastructure/
    config/                    # ConfigurationProperties 与 Bean 装配
    domain/repository/         # Repository adapter
    domain/service/            # 技术型领域能力 adapter
    messaging/                 # 消息技术实现
  model/                       # CQRS/接口共享模型；先按类型职责、再按需按业务分组
  transformer/                 # 层间模型转换
```

存在多个相对独立的领域能力时，可改为 `domain/<capability>/model|service|repository`。只有一个领域能力时不要重复模块名增加层级。

顶层 `model` 先按类型职责组织，例如 `cqrs/command`、`cqrs/query`、`cqrs/dto`、`enums`、`io`、`result` 或 `value`。同一类型目录内只有一个业务组或类数量较少时保持扁平；出现多个稳定业务组且每组都有多种模型时，再使用 `cqrs/command/group`、`io/message` 等业务子目录。不要先建 `model/<business>`，再把 Command、DTO、Result 和枚举混在同一目录。认证后的请求 Principal、安全上下文和受信传输身份解析器属于 `support.security`，不是业务模型或 CQRS 模型。

### 依赖方向

```text
interfaces -> application -> domain
     |              |           ^
     v              v           |
 transformer      adapter -> infrastructure adapters
```

- domain 不依赖 Spring、HTTP、RPC、配置类、持久化实体、外部 SDK 或具体 adapter。
- application 可依赖 domain interface 和用例输入输出，不依赖 Controller、具体持久化或具体 RPC client。
- interfaces 只负责协议转换、边界格式校验、认证上下文提取和结果序列化。
- infrastructure 实现 domain/application 定义的 seam，并在装配层注入。

Repository 只用于聚合生命周期。远端 HTTP client、运行态目录、缓存和诊断查询不要为了“像 DDD”命名为 Repository。

## 技术模块

### 适用场景

通用插件、协议 SDK、Gateway 传输层、Gossip 引擎、诊断采集、编解码和纯运行态注册表，主要复杂度来自协议、并发、资源上界和故障恢复，不需要伪造业务聚合。

### 推荐结构

```text
<module>/
  interfaces/                  # 对外协议入口；库模块可由公开 facade 代替
  application/                 # 有完整用例编排时才存在
  <capability>/                # 按稳定技术能力垂直组织
    model/
    service/
    state/
    tracker/
  infrastructure/             # 外部系统、存储、网络 adapter
  config/                      # 库的自动配置或应用装配
```

小型库不需要机械建立所有层。例如纯 codec 可以直接使用 `codec`、`model`、`spi`；一个只有单一职责的 adapter 也不需要添加空 application 层。

Broker 专属例外：`support.raft.codec`、`support.raft.model`、`support.raft.statemachine` 三个精确职责包承载命令协议、快照载体和类型化状态回调，不允许任意后代包。它们不能依赖 application、interfaces、运行配置/生命周期、Runtime 或具体网络 client；领域不得反向依赖。Runtime/close 仍唯一归 BrokerRaftAdapter，consensus/membership 不移动。本例外不放宽其他 support 规则，间接 Transformer 编排由 Review 检查。

有界、非权威且可选的跨层诊断记录辅助类型可进入明确批准的 `support.<capability>` 精确能力包；允许有界内存记录和累计计数，不参与业务决策，不拥有网络、运行资源或生命周期，不依赖 application、interfaces、infrastructure、配置、生命周期、Adapter 或具体网络 client。不得扩展为任意子包许可或按类名豁免，领域仍不得依赖 support。Broker 当前仅批准 `support.gossip` 精确包承担此职责；运行态 Gossip store/Adapter 仍归 `adapter.gossip`，不因此移入 support。

Broker 指标观察器单独批准进入精确 `support.monitoring`，不允许后代包。允许 Spring `@Component`、Micrometer `MeterBinder` 集成，以及只读 Domain、Registry、Adapter 查询；禁止依赖 application、interfaces、infrastructure、config、lifecycle，禁止直接网络 client、RaftRuntime、执行器拥有、业务决策或写入，domain 不得依赖 support。此技术观察例外不继承纯 `support.gossip` 的禁止 Adapter/Spring 规则，也不放宽其他 support 包。精确包和稳定依赖由 Architecture Test 保护；只读调用与业务写入语义为 Review-only，待有低误报的写 API 清单或语义 checker 后自动化。

Broker 自动健康观察另行批准精确 `support.health`（不允许后代包），当前具体实现为 `BrokerHealthObservationTracker`，允许 Tracker 后缀，不建 Repository、port/实现对或兼容壳。它只维护按当前目标裁剪的有界采样、结构化上下文、freshness 与快照；观察非权威，但属于安全决策输入，不是可选诊断。成员一致性由聚合 `BrokerCluster.findConsistentVoters` 判定，最新事实、多数与稳定窗口由领域服务 `BrokerFailureObservationService.evaluate` 判定，应用层编排。允许依赖领域 model、common 和 JDK 时间/集合；禁止 application、interfaces、infrastructure、config/configuration、lifecycle、Adapter、领域 service/repository、Spring、client/network、plugin 及运行资源拥有，domain 不得反向依赖 support。装配归配置、入口归 Handler/Application；此例外不放宽 Gossip、Monitoring 或其他 support。精确包和稳定依赖由生产/fixture 共用架构规则保护；有界性、无决策及实际完成回调语义由聚焦测试和 Review 保护，待可靠资源/决策数据流分析后自动化。

不要用 `support` 接纳暂时不知道放哪里的代码。若一个目录内部已经出现 `service/model/io/tracker`，它就是一个需要命名和划定依赖方向的能力，而不是辅助代码。除上述明确批准的技术接入、健康观察与有界诊断辅助职责外，`support` 只允许极少量无状态、无领域规则且需要跨层共享的模块内部辅助类型，例如 `support.exception` 中的协议无关模块异常、`support.security` 中的安全上下文或受信传输身份解析器，以及 `support.util` 中的协议无关纯函数工具；安全辅助类型可以适配传输上下文，但不能执行授权策略、生成协议响应或依赖 application 实现，异常名称仍须表达所属模块或能力，不能成为通用 `BusinessException` 的替代品。一旦形成独立生命周期、权威业务状态、外部集成或对外模型，应立即归入 application、interfaces、infrastructure 或明确的技术 capability。

技术模块仍必须有清晰 seam：协议入口不应同时承担算法、持久状态修改和远端调用；并发、超时、背压、幂等和资源上界必须由对应技术 module 封装并测试。

## 混合模式

Broker 属于混合模式。权威集群状态和路由授权包含强不变量，适合 DDD 核心；Registry、Gossip、诊断和 Bolt 调用是技术子系统。

```text
broker-server/
  interfaces/                  # HTTP/Bolt 输入输出转换
  application/
    service/                   # 完整用例编排，再按 cluster/routing 能力分组
    port/                      # application 所需外部能力的 seam，再按能力分组
    policy/                    # 应用级授权、准入和事实校验，再按能力分组
  domain/
    cluster/                   # 一个内聚聚合边界；聚合、实体、值对象、领域服务和 Repository 共置
    authority/                 # epoch、Lease、fence、admission 及领域 seam
    connection/                # GatewayRouteTable、ConnectionRoute 与 revision/tombstone
  model/
    cqrs/command/              # 应用命令与封闭的内部状态变更命令
    cqrs/dto/                  # 查询和诊断 DTO
    cqrs/event/                # 已发生事实的事件模型
    cqrs/result/               # 命令协调结果
    enums/                     # 跨层边界枚举
    io/                        # HTTP Request/Response
  support/
    exception/                 # 少量跨层可识别的模块异常
    security/                  # 认证后的请求 Principal 与受信身份解析辅助类型
    monitoring/                # 精确包：Spring/Micrometer 只读技术观察例外
  transformer/
    application/               # 应用输出映射
    interfaces/                # 入口映射
    adapter/                   # Broker 状态、Raft、Drain、Fence、Lease 边界映射
    registry/                  # Registry 边界映射
  infrastructure/
    raft/                      # Broker 窄例外：codec/model/statemachine 精确职责包
    registry/                  # 运行态目录 adapter
    gossip/                    # observation/sync、Spring publisher/listener adapter
    diagnostic/                # Registry 聚合查询与有界 Tracker
    authority/                 # 本机 fence 水位文件持久化实现
    config/                    # 按稳定能力拆分的 Bean 与 ConfigurationProperties 装配
```

Broker 不建立顶层或通用 `lifecycle` 技术类型桶。定时调度、启动注册和关闭回收入口归
`interfaces/scheduler`；具体 Registry 存储与 fence 水位持久化分别位于 `infrastructure/registry` 和
`infrastructure/authority`。
`Authorizer` 只用于身份已经解析后的访问授权决策；状态激活、Lease 安装或阶段推进使用 `ActivationService`、
`Coordinator` 等行为名称。

混合模式的关键不是目录数量，而是权威关系：Gossip observation 可以触发一个 application command 的提议，但不能直接改变 `BrokerClusterState`；运行态 Registry 可以提供路由事实，但不能自行决定 epoch 或 RouteBroker 权威。

Broker 中“技术概念”也可能属于领域，但判定依据仍是不变量和生命周期，而不是它是否直接面向最终用户。
例如连接路由的 revision、完整快照、tombstone、drain 水位和回收条件必须由所有 adapter 一致遵守，因此由
`GatewayRouteTable` 聚合维护；内存 Registry 只保存聚合和查询索引。相反，Bolt 序列化、并发 fan-out、超时、
负缓存、Gossip 合并和快照 chunk 暂存主要受资源与协议约束，继续留在 application/infrastructure。

`application` 先按类型职责组织，再按稳定业务或技术能力分组：`service` 完整拥有用例流程，`policy` 保存不属于
domain 不变量的授权、准入和校验规则，`port` 保存 application 调用外部技术能力时依赖的 interface。
`service` 是用例模块目录，不是类名后缀过滤器：公开用例入口使用 `*AppService`，与入口强内聚且没有独立调用方的
执行引擎、Coordinator 和内部 seam 跟随所属 service，并优先使用包可见性；不要为了让目录中所有文件都以
`Service` 结尾而把内部实现伪装成独立用例或外部 port。
这里的 port 是 seam，不是 TCP/HTTP 端口；典型例子是运行态 Registry、远端 Client 和诊断查询。
由 infrastructure 提供 adapter 的 interface 才进入 port；只在多个 application service 之间协作的内部
interface 跟随所属 service，不进入 port。仓库已有公共 interface 能完整表达能力时直接复用；例如普通领域
事件发布直接依赖 `DomainEventPublisher`，不再创建与各事件构造器一一对应的模块级 Publisher。application
不放 `application/**/model`。需要被 interfaces、
application 和 infrastructure 共同引用的用例输入输出按类型职责放在顶层 `model`；已发生事实的数据模型
进入 `model/cqrs/event`，Spring publisher/listener 等分发机制仍属于 infrastructure。能力变多时按稳定行为分组，
例如在 service/port/policy 内继续按 cluster、routing、registry 以及 lease、lookup、delivery 等能力拆分。

`domain/<capability>` 已经表达一个内聚聚合边界且类型规模可控时，聚合、实体、值对象、领域服务和 Repository
可以直接共置，并由类型名与 Javadoc 表明 DDD 角色。`change`、`result` 是类型形态，不是 DDD 层，不能仅为
收纳同类后缀建立目录；只有形成可独立理解和演进的领域概念时才增加业务语义子包。类型数量增长到明显影响
导航时，可以按 `model|service|repository` 分组，但不能为单个类型建立空洞层级。

Broker Transformer 按边界能力归属：`transformer.application`、`transformer.interfaces` 保留应用输出与入口映射；`transformer.adapter` 承担集群状态与 Raft/Drain/Fence/Lease 映射，状态转换器命名 `BrokerClusterStateTransformer`，不增加 projection 目录；`transformer.registry.RegistryTransformer` 承担 Registry 映射。该能力分组不改变 MapStruct 生成映射规则，也不授权在转换器内加入业务决策。

## Interface 与命名

只在存在稳定 seam 时定义 Java interface。一个实现且没有独立测试替代需求时，不为“解耦”创建同形透传 interface。两个 adapter、外部依赖替换、故障注入或明确的依赖倒置需求出现时，interface 才真正产生价值。

名称应说明角色：

- 聚合生命周期使用 `Repository`；
- 运行态定位使用 `Directory` 或 `Registry`；
- 远端能力使用当前领域语义的 `Client` 或 `Gateway`；
- 协议转换使用 `Handler`、`Controller`、`Transformer`；
- 用例入口使用动作明确的 `*Service`，避免只有一个透传方法的薄包装；
- `Command`、`Query`、`*Cmd` 默认是 application 输入，不进入 domain；domain 使用行为方法和值对象表达规则。只有不包含调用协议、可由领域独立解释和重放的状态转换描述才属于 domain，并应命名为 `Transition`、`Change` 等，避免伪装成 Application Command；
- 纯规则使用 `Policy`、`Selector`、`Validator`；
- 应用层访问外部能力的 seam 使用 `application/port/<capability>`；不能仅因类型是 Java interface 就称为 port；
- 技术实现以技术前缀说明 adapter，例如 `Raft*`、`Bolt*`、`InMemory*`。

## 错误示例

以下结构应在 Review 中拒绝：

- domain 类 import Spring、配置属性、具体 plugin、HTTP/RPC DTO 或 support client；
- `domain/.../memory/InMemoryXxxRepository` 把具体 adapter 放在 domain；
- Controller/Handler 读取 Repository 后自行推导状态、计算 digest、执行迁移或跨多个写入；
- application 直接构造具体网络 client 或读取 `@ConfigurationProperties`；
- application 内继续建立 `model` 子目录，或把 Spring publisher/listener 等框架分发实现移入 model/interfaces；
- 把 Gossip、缓存、诊断记录建成没有业务语言和不变量的“聚合”；
- 在 `support` 下继续建立 `service/model/io/tracker`，把完整能力伪装成辅助代码；
- 为每个类建立 interface/impl 配对，但所有 interface 都只有一个同形实现；
- 仅移动 package，不改变依赖方向和调用职责。

## 渐进迁移

历史模块按触碰范围收敛，不做全仓一次性搬包：

1. 先写能够证明依赖方向的 Architecture Test，并观察它因现有违规而失败。
2. 优先消除会改变行为或破坏权威状态的旧路径。
3. 把 concrete adapter 移出 domain，保持 interface 和行为不变。
4. 把 Controller/Handler 中的完整用例编排收回 application。
5. 为 application 与外部技术建立最小稳定 seam，由 infrastructure 实现。
6. 最后按职责拆分过宽类；不在红灯状态同时进行无关命名清理。

每一步都运行聚焦测试和 Architecture Test。迁移期间不允许建立长期双写、双路由或“新旧都能走”的兼容分支；若系统尚未上线，直接删除旧路径。

## Review 清单

- 当前模块的主要复杂度是业务还是技术？选择的结构是否与之匹配？
- domain 是否只包含业务状态、规则和必要 seam？
- application 是否完整拥有一个用例，而不是让入口补齐一半流程？
- interfaces 是否可以只看转换逻辑就理解？
- concrete adapter、配置和生命周期是否位于外层？
- interface 是否隐藏了复杂度，还是仅增加一层透传？
- 测试是否从稳定 interface 证明行为，并有 Architecture Test 保护依赖方向？
- 新目录是否承载真实职责，而不是为了套模板？
