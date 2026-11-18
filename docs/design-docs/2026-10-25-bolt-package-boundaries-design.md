# Bolt 包职责与 Broker Gossip 运行参数归位设计

> 状态：原包迁移设计已批准，独立设计 Review Approved（ab9ff0d665930b5bf）；用户已确认按职责拆包及两个安全类型新名称。实际架构规则要求 Broker 共享运行参数使用现有 model.properties，而非先前讨论的 model.gossip；此处记录修正，不扩大允许包。Review 追加决策 D6 已获用户批准，独立设计 Review a61ffe608a987aada Approved，无必须修改项；该追加 Review 仅覆盖 D6 设计，不代表实现或验收通过。

## 摘要

移除 im-bolt 含义混杂的 core 包，按 client、server、security 分组。Broker 的不可变 Gossip 启动快照改名 BrokerGossipSettings，放入 model.properties，消除 interfaces 对 infrastructure 的反向依赖。仅调整源码组织、必要可见性与注释，不改变认证、预算、授权或同步行为。

D1–D5 的无行为及 wire 变更约束仅针对包迁移；D6 是用户明确批准的不兼容公共 SDK/RPC 删除例外。

## 背景

当前 core 混有11个类型。BrokerGossipProperties 已从 scheduler 移到 infrastructure.config.properties，但 Scheduler 仍依赖它，实际 full 在 LayerBoundaryTest.brokerInterfacesMustNotDependOnConcreteInfrastructure 失败。既有 model 包允许 model.properties，而非任意能力子包。

## 目标与非目标

- 目标：职责清晰，内部协作者保持包级封装，配置与运行时快照分离，补充非显然安全及生命周期注释。
- 非目标：不拆新运行组件、不新建接口或兼容壳、不改变配置键、wire、TLS、信任、共享快照、时间预算或失败分类，不修改架构规则来容纳违规。

## 已知事实与约束

- Plugin 不依赖业务模块；自动配置保留根包。
- client 内 factory、manager、gate、context 保持 package-private。
- TLS 会话检查为 client/server 共用，拆包必须明确最小 public API；现有 development validateTarget 也跨包调用。
- Broker model.properties 是现有允许类型职责包。领域不引用此配置快照或 BoltTarget。

## 方案概览

- client：BoltRpcClient、BoltClientInvoker、BoltControlledClient、BoltAuthenticatedConnectionFactory、BoltAuthenticatedConnectionManager、BoltInvocationContext、BoltOutboundHandler。
- server：BoltControlledServer、BoltRequestProcessor。
- security：BoltCertificateValidity 改名 BoltTlsSessionFacts；BoltDevelopmentPolicy 改名 BoltDevelopmentValidator。
- Broker：BrokerGossipProperties 改名 model.properties.BrokerGossipSettings，配置装配继续创建唯一不可变 Bean，Scheduler 与入站准入继续共享同一实例。

## 详细设计

同步全部 imports、全限定名、测试包声明/路径、脚本路径 fixture 和有效稳定文档。历史已归档计划不机械改写。客户端协作者不因拆目录变 public；共享 TLS record、客户端认证入口、服务端会话检查入口及外部调用的时间/身份访问面按实际调用最小开放。DevelopmentValidator 的跨包 validateTarget 需 public；其余内部方法保持原封装。不得复制验证逻辑。

注释说明对象职责、共享创建与单次调用预算、Channel事实发布/复用/退休、发送gate位置、证书数组边界与DEV部署前提；不逐行翻译代码。类型名变化引起的本地上下文key需同步所有使用方，不改变wire。

测试优先使用公开入口，既有同包测试需按实际内部访问调整，不通过放开生产成员取悦测试。先复用现有失败架构用例验证Broker迁移，再按新包验证Bolt原有正反fixture；不允许削弱插件边界或raw transport检查。

## 可靠性与安全

保持D10实际呈现证书数组、D11可信同JVM装配边界，不扩大为完整CertPath、在线吊销或进程内sandbox保证。record中无peerIdentity的中间有效期事实仍按原语义，不因命名美化增删验证。共享可信配对仍为启动不可变快照。

## 运维与交付

无配置键或数据迁移；Java类型全限定名变化要求源码调用方同时编译。不提供旧core兼容壳。保留VS Code，不关闭或暂停IDE。构建前核对包路径及旧target类，避免再次把旧字节码当当前代码。

## 风险与回滚

- 风险：包可见性、旧class残留、同包测试、脚本硬编码、文档路径遗漏。
- 回滚：只恢复本任务精确移动和引用，不撤销混合工作区已有修改。
- 延后：同步编排下沉、生产证书、性能容量与私有JRaft后轨不包含本次。

## 验证策略

- 聚焦：Broker共享快照/准入/Gossip测试；Bolt全模块测试。
- 架构：先运行既有失败LayerBoundaryTest与BoltInvocationBoundaryTest，包含正反fixture；再完整架构模块。
- 完整：最终冻结后 ./scripts/verify.sh clean 与 ./scripts/verify.sh full；失败保留原始原因，不盲重跑。
- 最终：独立实现Review及匹配当前scope的quality；状态关闭遵守两时点规则。

## 后续文档与执行

同步Broker Architecture、Server README、im-bolt README、CODING_GUIDE/HARNESS_GUIDE中确有归属变化的条目与引用；无新通用工程规则时不增加checker例外。独立设计Review通过后创建执行计划并登记PROGRESS，至多一个active任务。未授权Git commit。

## 附录 A：备选方案与行业依据

- 仅client/server：共享TLS和DEV检查缺归属，不采用。
- 细分connection/context/gate等：打散包级协作者，增加public面，不采用。
- client/server/security：按调用与共享边界分三组，采用。
- Broker infrastructure配置对象直接给Scheduler：现有实际架构失败，不采用；model.properties共享快照无需新框架且符合现有规则。

## 附录 B：决策记录

| ID | 问题 | 决策 | 理由 |
|---|---|---|---|
| D1 | core过杂 | client/server/security | 保持紧密协作者封装 |
| D2 | 安全类型命名 | BoltTlsSessionFacts、BoltDevelopmentValidator | 表达实际事实与校验职责 |
| D3 | Broker共享快照 | model.properties.BrokerGossipSettings | 使用现有允许包，不让入口依赖infrastructure |
| D4 | 缺注释 | 补边界与原因 | 不增无意义逐行说明 |
| D5 | 出站 ChannelHandler 命名与归属 | 用户 Review 批准改名 BoltOutboundHandler，仍放 client 包 | 遵循 Netty Handler 命名，避免拆入 channel 子包而扩大包级协作者的 public 面；不改变 pipeline 名称或发送校验 |
| D6 | 无生产调用者的 Broker 列表 RPC | 用户批准删除 `BrokerClient.listBrokers()`、`BrokerBoltOperation.LIST_BROKERS`、`BrokerListHandler`、`BrokerListResult`、专用 `BrokerEndpointDTO` 及其响应映射和测试；独立设计 Review a61ffe608a987aada Approved | 仓库全量源码搜索未发现该 SDK 方法的生产调用者；不保留无调用兼容壳。仓库外调用者未知，删除会造成外部源码/二进制及旧 RPC 调用不兼容，不能宣称外部兼容 |

### D6 Review 追加范围与边界

- 当前发现与拓扑刷新以 `BrokerClient.refreshClusterState()` → `GET_CLUSTER_STATE` 为准；`BrokerRefresher` 在应用就绪及周期刷新时调用它。`LIST_BROKERS` 不是该刷新路径，不以 Gossip 注册观察替代 Raft committed state。
- `BrokerEndpointDTO` 删除前仅用于该列表响应及专用契约测试，已随链删除。仅删除 `RegistryTransformer.brokerEndpointFrom(BrokerRegistration)` 响应映射；同名 String 参数重载仍由 `InMemoryBrokerRegistry` 创建注册对象，必须保留。`BrokerQueryParams` 仍由 `GET_CLUSTER_STATE` 使用，必须保留。
- 同步 `BrokerClientRoutingTest`、`BrokerTimeContractTest`、`RegistryTransformerTest`、`AbstractBrokerRpcHandlerTest` 和 Message `TestCacheConfiguration` 中的实际引用；只删除专用断言，保留剩余时间契约、路由和 Handler 行为断言。架构负例 `fixture.broker.support.health.InterfaceOwner` 改依赖真实同层 `BrokerClusterStateHandler`，不修改 `LayerBoundaryTest` 断言或规则。
- 备选是保留无调用 RPC 作为潜在外部兼容入口；用户选择删除，以收窄公共面和避免把运行观察误作权威发现。未知外部消费者需在发布前由维护者核查；本次不添加兼容方法、代理或新协议。
- 实施后同步有效设计中的旧发现说明和 SDK 说明；历史归档计划不机械改写。D6 独立设计 Review 通过后已随实现同步当前说明，历史基线明确标记退出及当前刷新路径。
- 验证顺序：全仓引用搜索、精确差异及 `git diff --check`，确认无本仓并发 Maven，再使用 `/tmp/jraft-artifact-m2` 顺序运行 Broker SDK/Server、Message 测试替身使用方与架构 focused Maven（`-am -Dsurefire.failIfNoSpecifiedTests=false`）。耗时或环境失败先归因，不启动 full；clean/full 继续按用户要求暂停。

## 附录 C：独立 Review

独立 Reviewer ab9ff0d665930b5bf：Approved，无必须修订项。已核对 model.properties 现有允许包、interfaces 禁止依赖 infrastructure、TLS record 最小公开面、DEV validateTarget 跨包访问及脚本/架构 fixture 迁移。设计批准不代表实现或验收通过。
