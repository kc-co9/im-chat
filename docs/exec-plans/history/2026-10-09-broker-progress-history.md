# 项目进度

本文件是全仓当前状态索引。详细任务、验收标准和证据以链接的 active execution plan 为准；稳定项目说明继续由 README 和 Architecture 维护。

## 当前状态（Current State）

- 历史记录当时的活跃计划：[Broker Review 收口计划](../completed/2026-09-20-broker-routing-consensus.md)。
- 当前任务：T13，将 Broker Server 核心升级为完整 DDD 聚合。
- 状态：`active`

当前增量：删除 support/interceptor 的空链契约，具体链直接实现 BoltRequestHandler，
名称由 OrderedBrokerRequestInterceptorChain 收敛为 BrokerRequestInterceptorChain；测试与文档同步。

责任链后续简化已完成：删除生产 BrokerAdmissionContractValidator、BrokerAdmissionStage 及 stage()，
列表顺序直接决定执行顺序。operation 覆盖和请求 capability 匹配仅在测试侧验证；责任链、身份、
Lease 环绕及 Broker 启动聚焦 clean test 通过。

RouteBrokerExecutionInterceptor 删除 wallClock 字段与 Clock 构造参数，入口期限检查直接使用
Instant.now()；配置与测试同步，路由准入/责任链/Handler/启动聚焦 clean test 通过。

当前增量：Broker SDK 准入分类由 `BrokerAccessProfile` 改名为 `BrokerAdmissionType`，
`BrokerBoltOperation` 的访问方法同步为 `admissionType()`；Gateway 路由授权拦截器直接读取
`Instant.now()`，不再注入 `Clock`。相关聚焦 clean test 与 Broker 启动测试通过，T13 保持 active。

当前增量：按 operation 装配完整请求责任链，前置节点按配置顺序执行、业务 Handler 位于链尾。
删除 Terminal 回调与 Handler 持链；验证 Lease 节点仍环绕业务副作用，T13 保持 active。

本轮已完成：`support/interceptor` 仅保留 Bolt 责任链入口契约，`infrastructure/interceptor` 负责
调用上下文、节点递归和具体准入；`BrokerRequestConfiguration` 为每个 operation 组装“前置节点 ->
业务 Handler”完整链，并将完整链注册到 `BoltRequestProcessor`。原始 Handler 不再实现 Bolt SPI、
不持有链，也不存在 `Terminal` 回调。Handler/责任链/启动聚焦 clean test、behavior 34 项和 clean
通过；全量门禁的既有失败原因记录在 active plan。

Broker 请求链跨层契约已归 `support/interceptor`，仅保留 `BrokerRequestInterceptorChain`；
调用对象、阶段、有序执行、具体拦截器与启动校验归 `infrastructure/interceptor`，Handler 不再
构造内部调用对象。Broker 聚焦 clean test、34 项 behavior、support 契约架构规则与 clean/diff 通过。
quick 仍有顶层 model 分类、Gossip 调度配置两项既有违规；startup/e2e 分别被其他模块旧测试
构造器和缺少 bootstrap membershipEndpoint 阻断。full 非 clean Broker 测试受旧 target 产物污染；
reactor clean test 先失败于 im-cache 容量断言，单独 Broker clean test 仅余三项既有异常断言错误。
T13 未收口。

Broker Bolt 基础设施中的八个拦截器/链实现已按具体校验职责重命名，SDK profile 与准入行为不变；
配置、测试夹具和当前文档同步。Broker 责任链与启动定向 clean test 通过；routing-ha
场景证据选择器、RH-20 当前候选行为描述和聚焦 runner 引用同步，清单与脚本自测通过。
本轮 clean/diff 通过，quick 仍有顶层 model 与 Gossip 调度配置两项既有架构违规；full 已越过
RH 清单校验，停在 BrokerClusterAppServiceChangeTest 的三项既有异常断言错误，T13 未收口。

当前增量：调整 Broker Bolt 请求责任链包归属。Handler 所需契约留接口层，链执行与具体准入实现
迁基础设施层；`support/security` 身份值保持原位。聚焦 clean test、34 项 behavior、clean/diff
通过，support 白名单架构违规已消除。quick 仍有顶层 model 与 Gossip 调度配置两项既有违规；
startup 被 im-message-server 旧构造器阻断，e2e 缺 bootstrap membershipEndpoint，full
仍被 routing-ha 旧测试方法引用阻断，未进入最终 Maven verify。T13 仍 active。

Broker RPC 身份对象仍在 `support/security`：调用方身份改为 `BrokerCallerPrincipal`，SDK 类别
改为 `BrokerCallerRole`；空 Principal 接口与 Gateway 注册专用包装已删除。准入、Handler 角色拒绝和
Registry 身份绑定保持，SDK/Broker 安全聚焦 clean test 与 34 项行为测试通过。

Bolt 请求处理 SPI 收敛为唯一 `handle(context, payload)` 入口；Broker 与 Gateway 删除易混淆的
payload-only 重载，缺失身份仍由入口拒绝。插件、Broker、Gateway 跨模块定向 clean test 通过。
本轮 realtime behavior 34 项及 clean/diff 通过；quick 仍有三项既有架构违规；e2e 在 Spring
测试上下文注解解析阶段失败，未执行 Bolt 链路；full 仍被 routing-ha 清单中的旧测试方法引用阻断。

本机 Broker 注册入口兼有启动、周期心跳和关闭职责，保留 `BrokerRegistrationLifecycle` 类名并迁至
`interfaces/lifecycle`；配置、测试及当前文档同步。相关定向 clean test 与启动装配测试通过。
本轮 clean/diff 通过，quick 仍有三项既有架构违规；full 仍被 routing-ha 清单的旧测试方法引用
阻断，未进入最终 Maven verify，T13 保持 active。

Gateway 过期清理调度入口更名为 `GatewayCleanupScheduler`，与负责授权续签的
`RoutingLeaseScheduler` 保持独立；配置装配、启动任务断言和测试名称已同步，Broker 定向 clean test 通过。

本轮命名收敛：只读集群入口为 `BrokerClusterController`，本机过期 Gateway 清理用例为
`LocalGatewayCleanupAppService`，定时成员恢复入口为 `BrokerMembershipScheduler`。
配置与测试引用已同步，Broker 定向 clean test 和启动装配测试通过；行为与权限边界未调整。
本轮定向领域/应用架构规则通过，接口规则仍被 BrokerGossipScheduler 的既有依赖阻断；
quick 保留三项既有架构违规，full 再次停在 routing-ha 清单的旧测试方法引用，未进入最终 Maven verify。

Broker 接口层收敛增量：管理只读入口更名 `BrokerDiagnosticController`，Raft 成员写入口按 Review
更名 `BrokerMembershipController`；三个纯定时 `Lifecycle` 更名 `Scheduler`。删除单用途 principal
Handler 基类，operation 查询返回明确 HTTP 响应；Gateway 过期清理改由应用用例先记 Gossip 删除观察、
再删除本机注册，写入失败保留目标供下轮重试。聚焦 clean test、behavior 34 项和 clean 通过；
startup 仍被 im-message-server 旧 BrokerClusterState 测试构造器阻断，architecture/quick 仍有三项
既有包归属与依赖违规，Broker 全模块 509 项中有三项既有异常断言错误。full 在
im-routing-ha 的 FrameDeliveryAppServiceTest 既有路由数量断言处终止，未进入最终 Maven verify。
T13 继续 active。

管理写入口后续 Review：强制摘流并入 `BrokerRoutingController`，方法级 FORCE 权限覆盖类级普通写权限；
删除单方法 Emergency Controller，原 URL 保持不变。`BrokerManagementAccessTest` 与 Broker 启动测试通过。
本轮 clean 通过，quick 仍有三项既有架构违规；full 在 im-routing-ha 的旧测试方法清单引用处失败，
未进入最终 Maven verify。

当前正在 Review Broker 故障恢复的领域职责：恢复证据、降级资格与终态选择已归聚合内对象，
最新 114 项相关回归通过；Broker 全模块 468 项中仍有 3 项既有激活/quorum 异常断言错误。
quick 两次停在 Harness 自测，独立 Review 未执行，不推进 T13 收口。下列早期记录保留为历史，以最近验证为准。

本轮连接路由聚合已将 Gateway/User 身份、route revision 与完整快照绑定收敛为领域值对象，
移除快照 `-1/null` 组合哨兵；`ConnectionRouteChangeSet.applied` 改为 `accepted` 并补充语义注释。
相关聚合、Registry、Lookup 和投递聚焦测试通过，`clean` 通过。
`quick` 在已有的 Broker 管理拦截器依赖 infrastructure 配置的 Architecture Test 违规处失败；
`full` 在真实 routing-ha 阶段耗尽 900 秒预算（退出码 124），Harness 台账记录
`im-chat-routing-ha-59954`，清理状态待 Docker 恢复后核验。本轮未将 T13 标记完成。

本次连接路由接口收敛：`GatewayRouteTable` 在每次写入/回收开始时直接读取一次 `Instant.now()`，
`ConnectionRegistry` 与调用方移除了透传的 `nowEpochMillis`；实体承担 revision 新旧比较。
聚合、Registry、快照、排空和 Handler 聚焦测试及 30 个 realtime behavior 场景通过。
Broker 全模块测试仍仅有管理 Controller 缺少 `BrokerManagementProperties` 的两项错误；
`quick` 仍因管理拦截器依赖 infrastructure 配置的 Architecture Test 违规失败，与本次变更无关。
最新 `full` 的工作树指纹稳定，Harness 汇总 134 个测试且零失败/零错误，但 E2E discovery 因
`RealtimeMessagingE2ETest` 依赖的默认包 `BrokerClient` 缺失而失败，真实 routing-ha 随后再次
耗尽 900 秒预算并返回 124；当前资源台账记录 `im-chat-routing-ha-36683`。

## 最近验证

复查 LocalRouteAppProperties 后已删除：LocalRouteAppService 只依赖批次超时，
InMemoryBatchConnectionRegistry 与缓存装配直接读取基础设施源配置的容量；
测试侧使用 BrokerRoutingPropertiesFixture。接收/清理、批次 Registry 与 Broker 启动 clean test，
定向架构测试、behavior 34 项及 clean/diff 通过；此前 quick 的三项既有架构违规未由本轮改动处理。

Broker 配置职责收敛：移除未消费的 Registry brokerTtl/connectionTtl、仅在装配类拆回字段的
LocalLeaseActivationAppProperties，以及单字段 BrokerDrainAppProperties；Lease pending 缓存容量改为
可配置的 lease-pending-capacity（默认 16）。应用专用参数快照移回对应能力包，Gossip 调度快照归
interfaces/scheduler；LocalRouteAppProperties 后续复查已删除，见上条记录。
源配置字段用途与调节边界已补注释，聚焦配置/启动/用例测试通过；定向架构检查仍有既存的
BrokerRouteLookupParams 顶层 cqrs 归类违规，未改动其规则。本轮 behavior 34 项、clean/diff 与
应用/接口/领域依赖定向架构测试通过；quick 仍有 support.interceptor、顶层 cqrs 和应用外层依赖
三条既有规则失败，前序未通过不运行 full。

FileFenceWatermarkRepository 已从 infrastructure/routing 迁入 infrastructure/authority；
测试、配置装配和当前目录文档同步，仓储及 Broker 启动聚焦 clean test 通过。

当前 T13 正在将 Broker domain 的 cluster/connection 两包按 model/repository/service 归类；
仅迁移源码、对应测试和引用，不改变领域行为，connection 不创建空 service 包。72 个领域源码类型已迁移，
旧顶层类路径在 Java 源码中清零；Broker 代表性领域与启动 clean test、跨模块生产编译、behavior 34 项、
四项领域架构定向测试通过。quick 在规则更新前因旧扁平包断言和其他既有 3 项架构违规失败，
更新规则后的 quick 已消除本次包路径失败，仅剩 support.interceptor、顶层 model、application 依赖
三条既有架构违规；两包 12 组领域 clean test 通过。前序门禁失败后不运行 full，不将 T13 标记完成。

RegistryObservationAppService 已更名为 RegistryGossipAppService，装配类同步改为
RegistryGossipConfiguration；Broker/Gateway/Connection 监听器只改类型与字段名，行为不变。
监听器、Gossip 调度和 Broker 启动聚焦 clean test 通过，behavior 34 项及 clean/diff 通过。
全量 startup 的非 clean reactor 在 Broker 启动测试加载配置类时遇到
`NoClassDefFoundError: BrokerRegistry`，因此不能宣称全量启动门禁通过。

Broker interfaces/infrastructure 阅读性 Review 增量：drain、故障恢复和 Lease 续签的定时入口在同步异常后
可释放运行标志，测试先失败后通过；Gossip 快照更新改为 Set 查询和显式 tombstone 循环；
Broker/Gateway 生命周期定时入口移入 interfaces/scheduler，GossipScheduler 仅依赖应用配置值，
管理路由写入口删除未使用请求体。启动、调度、Gossip、Controller 等聚焦 clean test 通过；
接口层不依赖具体基础设施的架构断言通过，behavior 34 项及 clean/diff 通过。
startup 被 im-message-server 测试夹具的旧 BrokerClusterState 构造调用挡住；全量 quick 此前仍有
Broker 其他包规则失败。Gateway 移除后事件监听失败的可恢复重放仍未解决，已在 active plan 保留风险。

FrameDispatchAppService 对多个 Gateway 的写帧已改为专用虚拟线程并发，使用
completedFuture/thenCompose/thenApply 链式编排；BrokerClusterAdapter.dispatchFrame 已改为由 Adapter
提交单目标 Gateway IO 并返回 CompletionStage。双 Gateway 同时执行且一个失败不丢另一结果的测试，
以及 Adapter/投递/Lookup/Handler 聚焦测试通过；应用直接对 CompletionStage 做 exceptionally，
无需转换为 CompletableFuture。本轮 behavior 33 项、clean/diff 通过；quick 停在其他模块的
GroupId 缺失与既有 4 处架构断言失败，未用 full 覆盖该失败。

RouteLookup 响应位置已移除 eventDeadline，连同 SDK DTO、应用对象、映射和重复过滤同步调整；
请求端 peer IO deadline 及两端 Gateway 注册新鲜度判断保留。相关聚焦测试通过，
设计及 Broker README 的响应字段说明同步。

FrameDispatchAppService 的 Gateway 复核已接在 RouteLookup 链末尾；负缓存仍在链外，
改为按最终可投递 Gateway 结果更新。端点失配负缓存测试修复前失败、修复后与投递/Lookup/Handler
聚焦测试一起通过；Broker Architecture/README 与 Harness 规则同步。

RouteLookup 已删除无消费的 lookupId；BrokerRouteLookupParams 直接持有当前 BrokerNode、
UserId 和 RoutingEpoch，不再创建 RouteLookupTargetDTO。应用、Adapter、SDK peer 载荷与接收端
校验一致；聚焦测试通过。此前两个异步测试的间歇失败来自工作线程不继承测试线程的静态时钟 mock，
有效路由夹具已改为远期截止时间，显式过期场景仍单独覆盖。本轮行为测试 33 项、
clean/diff 通过；全量架构门禁此前在其他模块报 GroupId 缺失及分层违规，未宣称通过。

上一轮 BrokerClusterAdapter.lookupRoutes 曾收为单个 BrokerRouteLookupParams，承载目标、lookupId、
UserId 和 RoutingEpoch；本轮已进一步删除 lookupId 和目标 DTO。此前相关聚焦测试首次整组运行
有两个异步用例空结果，单独与整组复跑通过，需继续观察测试时序稳定性。

FrameDispatchAppService 按用户确认改为三段链式编排：选择候选、并发查询汇总、按 Gateway
过滤合并；同步提交失败由 Adapter 转为失败 Stage。聚焦测试通过，行为门禁复跑执行 33 项通过；
一次行为及 clean 检查因并发工作树修改导致指纹失效，需待工作区稳定后刷新门禁。

用户最终确认 D17/D18 将 RouteLookup 改为本地未命中后查询当前拓扑中的所有其他
ACTIVE/DRAINING Broker：专用虚拟线程并发，不排序、不按固定 fanout 截断；
总预算与请求期限仍约束等待，期限内已返回的结果照常使用。历史候选缓存和观察任务已移除，
已退出当前拓扑的旧节点不再由 Lookup 兜底。设计正文记录此增量独立 Review 待完成，
T13 保持 active，不将原设计 Approved 结论套用到新决策。
用户随后确认进一步收敛期限职责：SDK/入口保留整体请求期限，应用服务和
`FrameDispatchCommand` 不再传外部 deadline；Adapter 每次 peer RouteLookup IO 使用配置的
`route-lookup-timeout`（默认 300ms），并生成 peer 协议所需 deadline。聚焦测试已通过，
最新行为/架构/clean/full 尚未刷新；T13 与独立 Review 状态不变。
本轮全部当前 Broker Lookup、超时部分结果和虚拟线程聚焦测试通过；行为测试 33 项、Broker
架构边界、启动测试、clean/diff 通过。先前 `quick` 在 load-test runner 锁恢复自测失败，
`full` 在用户转入新方案讨论后取消，最新方案尚未刷新 quick/full/e2e，独立 Review 仍待完成。

FrameDispatchAppService.dispatch 删除重复的请求过期提前判断，入口拦截器负责准入；
保留 FrameDispatchCommand.deadlineEpochMillis 用于跨 Broker 查询剩余预算及 RPC 请求期限，
负缓存仍可提前结束。投递/Lookup/Handler 39 项及入口拦截器 3 项测试通过，
T13 保持 active Review，未刷新 quick/full/e2e。

ConnectionRegistry 将 applySnapshot 改为 reconcileRoutes，方法名表达按 Gateway 完整用户集合对账，
分片输入和同 revision 内容校验留在注释及聚合行为；包装实现、底层实现、测试与当前架构图同步。
未改变版本、tombstone 或路由写入语义；53 项 Registry/分片/清理/真实启动测试及
领域/模块边界检查通过，生产和测试无旧方法调用，T13 保持 active Review。

InMemoryBatchConnectionRegistry 取消按 createdAt 手工计算剩余 TTL 和逐分片 PUT；
未收齐的新批次只写入 JetCache 一次，后续分片更新同一个缓存对象，框架 TTL 从首次暂存起算。
GatewayRouteBatch 移除不再使用的 createdAt；受控时间测试验证重复分片不延长过期时间，
真实 JetCache 测试验证对象引用及后续分片更新。接收/清理/启动 29 项、
领域/模块边界与 `./scripts/verify.sh behavior` 通过；本轮未刷新 quick/full/e2e。

按用户取舍删除 LocalLeaseAppService.prepare 的满额拒绝判断及不再使用的 properties 依赖。
JetCache localLimit 达上限可淘汰旧 nonce，新 prepare 继续；旧申请安装失败走既有重试路径，
领域服务仍按准备起点与 Lease 有效期校验。满额淘汰/一次性消费及领域/启动聚焦测试通过，
Broker 全模块 496 项中 493 项通过，仍是此前已归因的 BrokerClusterAppServiceChangeTest 三项
提交/quorum 异常传播预期错误，无新增 Lease 失败；全模块状态仍失败，T13 保持 active Review。

按用户简化 Lease accept：删除应用层重复的 pendingTtl 单调过期分支，JetCache TTL 决定 pending
是否仍存在；accept 只匹配目标 nonce，先消费再交领域服务按准备起点与 Lease 有效期校验安装。
Lease/领域/启动 11 项测试通过。Broker 全模块 496 项中 493 项通过，仍是此前已归因的
BrokerClusterAppServiceChangeTest 三项提交/quorum 异常传播预期错误，无新增 Lease 失败；
全模块状态仍失败，领域/模块边界检查通过，T13 保持 active Review。

LocalLeaseAppService pending nonce 已从 LinkedHashMap 改为直接注入 JetCache Cache，
框架以 pendingTtl 过期并释放 prepare 容量，prepare 不再逐条扫描。accept 只校验目标 nonce
的单调准备期限，匹配后先删除再安装；缓存命中不等于授权资格。
6 项本机 Lease 回归及真实启动通过；Broker 全模块 496 项中 493 项通过，
仍是此前已归因的 BrokerClusterAppServiceChangeTest 三项提交/quorum 异常传播预期错误，
没有新增 Lease 缓存失败；全模块状态仍失败，领域/模块边界与 diff 检查通过，
T13 保持 active Review，未刷新 quick/full/e2e。

Broker 缓存扫描：负缓存、分片暂存、清理检查点及候选历史统一使用 im-cache/JetCache LOCAL，
删除应用手工裁剪与分片 Map/过期字节统计。分片仅首次暂存时 PUT，
有效批次计算字节上限；清理缓存为本机有界重试记忆，容量淘汰不再声明 FIFO。
LocalLeaseAppService pending nonce 保留 MonotonicTimestamp 过期/一次性消费，不能由墙上时钟 TTL 取代；
注册索引和聚合状态属事实，不做缓存淘汰。串行 clean 聚焦测试与真实启动通过；
Broker 全模块 495 项中 492 项通过，仍是此前已归因的 BrokerClusterAppServiceChangeTest 三项
提交/quorum 异常传播预期错误，无新增缓存相关失败；全模块状态仍为失败，领域/模块边界及 diff 检查通过。

候选历史直接接入已依赖的 im-cache/JetCache Cache，工厂支持 LOCAL 显式容量、TTL 与有效值快照，
不保留自定义 LocalCache 类型。应用删除 Map 淘汰循环、超期扫描和历史锁，定时观察只 put。
容量使用框架策略，不保证最早 epoch 淘汰；快照通过 JetCache getAll 读取有效值，不保留额外键索引。
im-cache 全模块测试通过，最终缓存聚焦 5 项、Broker/Handler/启动 35 项通过；
TTL/容量/快照读取与旧 epoch 候选验证、领域/模块边界检查通过，
T13 保持 active Review，未刷新 quick/full/e2e。

GatewayRegistry 注册参数改为 GatewayRegistration，其余身份参数统一 GatewayId；内存身份索引和
退役集合同步领域化，不保留基础类型重载。入口复用 RegistryTransformer，Handler/投递/Lookup/
观察/Gossip/定时清理与测试同步；注册字段进一步改为 GatewayId/GatewayHost/GatewayPort/GatewayPrincipalId，
认证主体字段为 principalId，与进程标识区分。SDK/Gossip 保留 workloadIdentity 和平铺字段，
Gossip 显式使用 GatewayEndpointDTO，不直接序列化领域对象。58 项领域/注册/授权/投递/Lookup/Gossip/
诊断/清理/映射/真实 Adapter 与启动测试通过，领域/模块边界及 diff 检查通过；
T13 保持 active Review，未刷新 quick/full/e2e。

dispatch Review 不新增方法：查询期限/负缓存改提前返回，移除 canQuery 嵌套，
每轮以 directedQuery 表达策略，预算用 lookupDeadlineMillis 明确单位，领域 epoch 用于描述过滤。
发现定向候选超过 maxFanout 后未请求节点也被排除的缺陷；新增测试先失败，
修复为 queriedBrokerIds 仅记录实际请求节点，保留每轮预算、异常隔离与投递前复核。
最终投递/Lookup/候选/Handler 34 项回归通过，领域/模块边界检查通过，T13 保持 active Review。

FrameDispatchAppService 内部 ObservedState 提取为 model/cqrs/dto/BrokerClusterObservationDTO，
字段明确为 clusterState/observedAt，符合已有 DTO 后缀规则；保持应用读模型，不引入领域 SDK 依赖。
帧投递/Lookup/候选历史 16 项聚焦测试及 diff 通过，本轮未刷新 quick/full/e2e，T13 保持 active Review。

删除 RouteLookupCommand，FrameDispatchAppService 两轮共用 lookupId，Adapter 直接接收 UserId、
RoutingEpoch、Instant 期限并在 RPC 边界固定 hopCount=1；测试 Stub/调用方同步，不新增同形请求对象。
GatewayRouteBatch/Registry 更新期限改为 updateExpiresAt，清理与已提交资格参数改为 oldEventDeadline。
SDK 字段保持原契约。聚焦帧/Registry/本机/Handler 与真实 Adapter 请求映射 74 项通过，
旧 Command 在生产/测试/脚本无残留；readiness/diff 和领域/模块边界检查通过，
未刷新 quick/full/e2e，T13 保持 active Review。

继续前轮模型升级：ConnectionRegistration 合入原 ConnectionRoute 的版本/期限/状态/tombstone 行为，
身份使用领域值并保留注册/刷新时间，手写 Builder 使用外层实例。删除 ConnectionRoute、无版本注册/注销、
sync、findRouteStates 与查询补建路径。Registry 只有 register(实体)、带版本注销和统一 find；
GatewayRouteTable 为唯一实体来源，用户索引只保存身份；观察时间诊断由生成 Transformer 展开。
Gossip 连接删除只收敛观察，不能无版本删除本机注册；Gateway 整体移除仍清理本机聚合。
新增查询同源/首次时间保留与 Gossip 不覆盖较新注册验证；全模块 492 项中 489 项通过，
仅保留此前已归因的 BrokerClusterAppServiceChangeTest 三项异常传播预期错误，无本轮新增失败。
全模块仍为失败；最终领域/存储/应用/Handler/Gossip/映射聚焦 95 项通过，
领域技术依赖、无具体 Adapter、应用分组及模块边界检查通过。未刷新 quick/full/e2e，T13 保持 active Review。

FrameDispatchAppService 按用户 Review 将定位方法链全部展开到 dispatch：定向/兜底两轮共用
请求/等待/合并步骤，保留时间预算、失败隔离、最高 revision 合并和从完成时起算负缓存。
定时 observeClusterState 直接记录/回收历史，不转调私有方法；历史锁不覆盖网络等待。
Lookup/Candidate 测试改走公开投递入口，验证实际目标、端点、水位、一跳上下文和历史保留。
16 项聚焦测试通过，Handler 与真实启动在后续全模块中通过；T13 保持 active Review。

ConnectionRegistry 参数从接口开始域化为 UserId/GatewayId/RouteRevision/Instant/Duration/List<UserId>，
两个实现直接接受领域值，无基础类型重载；注册/注销、Lookup、诊断、Gossip、定时清理及测试夹具同步。
39 项 Registry/本机路由聚焦测试通过。Broker 全模块 490 项中 3 个错误仍是此前已记录的
BrokerClusterAppServiceChangeTest 提交失败和 quorum 失败传播预期，无新增 Registry 失败；
其余 487 项通过，全模块状态仍为失败，T13 保持 active Review。
本轮应用分组、领域及模块依赖聚焦检查通过，未刷新 quick/full/e2e。
FrameDispatchAppService 保留前轮流程整理与查询修复；参数按 UserId 构造和 AssertUtils 逐项校验，
非法输入向外传播，不新增 FrameDelivery 模型，路由定位进一步拆分尚未实施。

FrameDispatchAppService 主流程整理：本地目标与远端端点复核内联，移除 localTargets、
validatedLookupTarget、isFresh、merge、toCandidate、trimNegativeCache 小封装，保留共享 queryCandidates
与 recordCurrentState。GatewayRegistration.isFreshAt 承担活跃观察规则，Broker/User/epoch 索引使用领域身份。
两项新增回归先复现负缓存过早到期与同步 peer 异常中断，再修复为从完成时计时及逐 peer 失败隔离；
15 项投递/Lookup/候选测试通过，正在验证 Handler 和真实启动，T13 保持 active Review。

LocalRouteAppService 删除单调用方 publish 私有方法，事件构造与发布放回 acceptRoutes 的 Lease
保护步骤，保留新增事件先于实际完整路由事件的顺序。LocalRouteAppServiceReceiveTest 12 项及 diff 通过；
本轮未刷新 quick/full/e2e，T13 保持 active Review。

LocalRouteAppService.cleanupRoutes 按接收方法形状整理：转换领域输入、核对完整已提交资格、
Registry 幂等清理、Transformer 转换响应。清理 Map、缓存淘汰、按水位删除及汇总移入
InMemoryBatchConnectionRegistry，沿用 RouteCleanupResult，不新增中间对象或私有包装方法。
实现清理锁与分片接收独立；应用层保留资格核对与调用顺序。26 项清理/接收/Handler/领域聚焦测试通过，
包含重试不重复删除、缓存淘汰、水位冲突和较新路由保留；应用分组、领域及模块依赖检查通过。
readiness/diff 通过，本轮未刷新 quick/full/e2e，T13 保持 active Review。

BatchConnectionRegistry 改为扩展 ConnectionRegistry 的领域接口，基础设施 InMemoryBatchConnectionRegistry
包装实际存储并管理分片缓存，删除 Capacity，接收事实独立为 GatewayRouteBatchReception。
应用服务只持有一个 Registry，配置层只对外注册一个包装 Bean；普通存取与批次应用共享底层数据。
装配和共享数据验证已补，接收/清理/包装/Handler/启动 28 项与普通 Registry/Lookup/帧/监控 35 项
测试通过。应用分组、领域依赖和模块边界聚焦检查通过；本轮未刷新 quick/full/e2e，T13 保持 active Review。

按最新 Review 将 BatchConnectionRegistry 迁入 domain/connection，去除配置、SDK 和应用回调依赖。
Capacity 为容量/期限值对象，Reception 为接收事实；应用层转换结果并串行执行 Lease 保护应用/发布。
配置层转换容量，三类应用测试装配同步。27 项领域/应用/Handler/真实启动测试及应用分组、
领域依赖、模块边界聚焦检查通过，diff 通过；本轮未刷新 quick/full，T13 保持 active Review。

BatchConnectionRegistry 位于 adapter/connection，包装实际 ConnectionRegistry，负责分片暂存、容量、
超时回收及接收互斥。LocalRouteAppService 保留资格核对、Lease 保护和发布，完整批次在接收锁内
执行应用层处理步骤；清理缓存独立保留。不新增接口，不替换其他 Registry 使用方。
26 项领域/应用/Handler 回归通过，真实启动通过；补充 Lease 不可用时不写 Registry、不发布事件的验证。
应用能力分组、领域依赖和模块边界聚焦检查通过，本轮未刷新 quick/full，T13 保持 active Review。

路由接收命令/结果统一为 AcceptGatewayRoutesCommand/AcceptGatewayRoutesResult，成功状态为
ROUTES_APPLIED，BrokerClient、Gateway 发送流程、Handler、生成 Transformer 和接收测试同步。
SDK 文档记录同版本状态契约；T13 保持 active Review。
分片序号改为 GatewayRouteChunkIndex，应用入口转换、批次索引和收集行为统一使用领域值对象。
Gateway 两处测试夹具同步此前已删除的 clusterId 参数，两侧接收/分片/Handler/身份共 24 项测试通过。
readiness/diff 通过，旧生产类型/状态名称无残留；本轮未刷新 quick/full。

LocalRouteAppService.acceptRoutes 按六步整理：转换输入、资格核对、批次一致性、容量与收集、
完整内容验证、Lease 保护下应用和发布。转换后使用领域字段；重复分片由 collect 返回零新增字节，
不新增私有方法或中间对象，保留独立锁、容量及异常传播语义。
`mvn -q -pl im-broker/im-broker-server -am -Dtest=GatewayRouteBatchTest,LocalRouteAppServiceReceiveTest,LocalRouteAppServiceCleanupTest,LocalRouteAppServiceIdempotencyTest,AbstractBrokerRpcHandlerDispatchTest -Dsurefire.failIfNoSpecifiedTests=false test`
通过（24 项）；readiness/diff 通过，本轮未刷新 quick/full，T13 保持 active Review。

Gateway 接收批次下沉 domain/connection/GatewayRouteBatch，使用手写 Builder 和领域字段。
BatchKey 并入复合身份 GatewayRouteBatchId，CleanupKey/CompletedCleanup 改为
RouteCleanupId/RouteCleanupCheckpoint，分别表达操作/Gateway 身份及已完成清理事实。
应用层仍管理容量、回收与独立锁，不新增 Repository。领域/接收/清理/Handler 与真实启动共 40 项测试通过，
应用能力分组、定时入口、领域依赖和模块边界聚焦检查及 clean/diff 通过。
本轮未刷新 quick/full，保留既有门禁失败归因。
非法摘要格式从领域化入口抛出，合法内容错配仍返回 INVALID_BATCH，T13 保持 active Review。

LocalRouteAppService 主流程整理：PendingBatch 管理元数据及重复分片一致性、添加与字节更新，
应用层只对新批次/新分片检查容量，删除重复容量分支。cleanupRoutes 在独立锁内读取一次操作并完成
校验、幂等删除和汇总，移除内部 purge、DrainCleanupCommand/Result 及无调用方转换器。
接收异常不再捕获包装，AbstractBrokerRpcHandler 的受检异常声明接通现有 handle 通道；
公开入口测试验证原异常透传、重复分片不计费、冲突批次丢弃、清理幂等和独立锁。
首轮聚焦测试通过，正在验证启动装配与模块影响，T13 保持 active Review。

LocalLeaseAppService 跟进用户调整：保留直接 MonotonicTimestamp.now 和显式领域变量，
每轮 prepare/accept 只读一次时间，用同一时刻回收记录；prepare 的申请起点与回收时刻一致。
term/sequence/nonce 等改为完整领域名称，pending 改为 pendingRequests，不新增方法或异常捕获。
测试适配两参数构造器，用测试线程静态时间替身保留确定性期限验证；Lease 与真实启动共 6 项回归、
clean/diff 通过。本轮未刷新 quick/full，T13 保持 active Review。

按 Review 调整 LocalLeaseAppService.accept：Command 字段直接在应用入口构造 RoutingLease，
删除 IllegalArgumentException 捕获，参数/领域构造异常向外传播，不转 false；构造在 nonce 读取/消费之前。
删除无调用方的 routingLeaseFrom 映射，LocalBrokerDomainTransformer 保留清理水位转换。
本机 Lease 与清理共 9 项回归、clean/diff 通过；本轮未刷新 quick/full，T13 保持 active Review。

本机应用服务规范整理：LocalLeaseAppService 将 Lease 构造交给 LocalBrokerDomainTransformer，pending
以 LeaseNonce 为键，准备记录自身判断单调过期，应用层按转换/匹配/消费/安装分段。
LocalRouteAppService 的清理身份/阶段/期限/整组水位匹配归 BrokerClusterOperation/DrainProgress，
统一使用 LocalRouteAppProperties（含清理缓存容量），分片重复判断拉平，内部纯 getter 使用 Lombok。
保留同步锁、nonce 一次性语义、毫秒期限、拒绝和异常契约，不新增协调器或用例上下文。
本机服务/领域/排空/故障恢复/确认与真实启动共 93 项聚焦回归通过；Broker 全模块 474 项中
仍有 BrokerClusterAppServiceChangeTest 的 3 项既有激活/quorum 异常断言错误，未修改相关用例或规则。
readiness、clean/diff 通过；quick 的 Harness 自测通过，实际架构检查 66 项中 3 失败、1 错误、2 跳过。
失败为当前工作树已有 support/interceptor 分包及两处 Gossip 外层依赖；错误为 Message 聚合 Builder
反射时 NoClassDefFoundError: ImChatId，未涉及本轮本机服务。未弱化规则或继续 full，T13 保持 active Review。

帧分发命名整理：FrameDeliveryAppService 改为 application/frame/FrameDispatchAppService，
三个同包测试、Frame Handler、配置 Bean 和候选观察调度引用同步；routing-ha 测试选择器同步。
原 routing 下 ConnectionRouteRevisionTest 实际测试内存 Registry，归回 infrastructure/registry/connection。
投递/Lookup/候选/Handler/Registry 与真实启动共 28 项回归通过，聚焦应用分组/定时入口/领域及模块边界检查通过；
脚本语法和 clean/diff 通过，整体 quick/full/E2E 未刷新，本轮不推进 T13 收口。

本机路由维护合并为 application/local/LocalRouteAppService，提供 acceptRoutes/cleanupRoutes，
删除 GatewayRouteSnapshotAppService 与 LocalRouteCleanupAppService；共用依赖、单一 Bean，
snapshotLock/cleanupLock 保持分片暂存和清理幂等边界独立。LocalLeaseActivationAppService 改为 LocalLeaseAppService。
三类路由测试与 Lease 测试同步，原行为及“清理等待时仍能接收分片”并发回归通过；
本机服务/集群确认/排空/恢复/投递与启动共 44 项回归通过，聚焦包边界/领域依赖/模块边界架构检查通过。
本轮 clean/diff 通过。
routing-ha 脚本测试选择器同步新测试名，脚本语法检查通过，命令/SDK 协议不改；整体 quick/full/E2E 未刷新，T13 保持 active Review。

按 Review 将本机服务放在 application/local：LocalLeaseActivationAppService 从 authority 迁入，
DrainRouteCleanupAppService 从 routing 迁入并改为 LocalRouteCleanupAppService，三类同包测试同步。
两者不合并缓存或锁，SDK/Command/Result 的 Drain 语义保持；配置、Handler、能力包规则及文档同步。
本机 Lease/清理/排空/恢复及真实启动装配共 30 项回归通过；安装最新 Broker 产物后，local 能力包、
定时入口、领域依赖和模块边界聚焦架构检查及 clean/diff 通过。旧包与清理类名引用清除，
整体 quick/full/E2E 未刷新，T13 保持 active Review。

Gateway 整轮路由确认命名改为 BrokerClusterAppService.confirmGatewayRoutes 与 ConfirmGatewayRoutesCommand，
Handler/生成映射/测试同步，SDK 协议类型保持原名。日志命令构造迁入现有 Raft Transformer，
应用层构造领域确认事实并调用 BrokerClusterAdapter.confirmGatewayRoutes 单次提交；无应用服务互调。
确认、排空与恢复共 23 项 clean 聚焦回归通过；diff 通过，整体 quick/full 未刷新，T13 保持 active Review。

应用包归属整理：LocalLeaseActivationAppService 及测试迁 authority，准备/安装 Handler 和配置同步；
GatewayRouteSnapshotAppService 只保留本机分片接收，整轮 ACK 改由 BrokerClusterAppService.
acknowledgeGatewaySnapshot 登记，Handler 直接调用，不新增应用服务或相互调用。
Lease/ACK/快照/排空/恢复和真实启动装配共 33 项回归通过；安装最新 Broker 产物后，能力包分组、
定时入口、领域依赖及模块边界聚焦架构检查和 clean/diff 通过，旧包引用清除。未刷新整体 quick/full/E2E，
此前全模块错误和 Harness 自测失败仍保留，不推进 T13 收口。

advanceDrain 领域职责整理：operation 共用 ACK 等待及期限判断，needsRouteCleanup 决定本轮清理资格；
清理证据继续归 DrainProgress，完成资格继续归 BrokerCluster，完成响应转交现有 Transformer。
应用层保留排空超时失败策略、RPC 与提交异常边界，不新增应用私有方法、领域服务或日志命令。
领域/排空/故障恢复/Transformer 共 87 项聚焦测试、readiness、clean/diff 通过；
既有全模块 3 项错误与 quick 自测失败保留，本轮未重跑 quick/full，不推进 T13 收口。

恢复领域行为下沉主体已实现：DrainProgress 生成重建证据，operation 判断降级资格，聚合在日志应用时
验证完成意图并选择终态；设计记录于 2026-10-07-broker-route-recovery-domain-design.md。
内部命令升至 v5，证据/验证意图/终态顺序和快照 v6 保持；T13 为唯一 active 任务，独立 Review 未执行。
两条回归先失败后修复：通用阶段绕过恢复验证、完成提交不可用时误退回 Gateway 等待。
额外两条并发边界回归先失败后修复：故障汇总必须匹配日志应用时的检查点，汇总后迟到 ACK 不能改写冻结事实。
测试编译阻断已通过移动测试到生产类同包修复，最终领域/恢复/编码/Transformer/真实 Raft 快照重启/
启动/拦截器共 112 项聚焦回归通过，新增两条并发回归后相关 114 项在全模块运行中通过。
Broker 全模块 468 项中 3 项既有错误仍在 BrokerClusterAppServiceChangeTest：
activationCommitFailureDoesNotAuthorizeUncommittedEpoch 一项、progressReadFailureDistinguishesUnavailableFromLostLeadership 两项，
均为未改动的激活/quorum 异常传播断言；未改断言或弱化实现。补齐并发边界后全模块已重验；
首轮 quick 在补充代码前主动停止；后两次 quick 分别在并发 stale-lock 自测（竞争成功数为 0）和
routing-ha Harness 自测（退出码 124）失败，均未进入架构检查，不将自测场景当作真实多节点验证。
clean/diff 通过，独立 Review 未执行，未继续 full。新命令拒绝旧日志，不执行数据删除或部署。

recoverRoutes 阅读性 Review：证据准备改为单处提交，正常恢复重验原证据、超时降级复用已有残缺证据；
降级登记改为正向条件，提交结果统一命名 submitted，终态明确依据持久化 degradedReason。
方法说明及八步注释同步，不新增方法或中间类型。新增正常证据等待旧事件后恢复、残缺证据不重复提交
两条回归；生产 compile、clean/diff 通过，测试仍因 BrokerAdmissionContractValidatorTest 跨包访问在
testCompile 阻断，新用例未执行，不推进全仓门禁，T13 保持 active Review。

advanceDrain 纯注释 Review：方法说明明确本轮推进、证据复用触发聚合重验、完成幂等和下一轮调度；
九处步骤注释区分 cleanup 失败、超时尝试记录 FAILED 与共识提交异常，保留原平铺结构。
去除注释及空白后的方法体比对一致，diff 通过；本次未改逻辑、未重跑测试，T13 继续 Review。
clean 两次均因验证期间工作树变动而拒绝绑定证据，未标记为通过。

按 Review 删除仅封装单个枚举比较的 rebuildingRoutes，advanceDrain 调用点改回直接比较
REBUILDING_ROUTES；canAdvanceDrain 保留三个阶段的组合判断。生产 compile、diff 通过，测试未重跑。

advanceDrain 异步链收平：移除 submissionStage 局部变量，thenApply 显式返回
CompletionStage<BrokerDrainResult>，与 exceptionally 和末尾 thenCompose 直接连接，异常边界保持原样。
生产 compile、clean/diff 通过；本轮未重跑此前受跨包访问阻断的测试，全仓门禁未刷新。

advanceDrain 异常分支独立为 exceptionally：外层只恢复 cleanup 失败，成功路径返回独立的内层提交
阶段，末尾 thenCompose 统一展开；同步抛出的证据/完成提交异常也留在内层，不误记 cleanup 超时。
删除重复异常处理和匿名 Function，不新增方法或业务类型。生产 compile 通过；排空异常测试扩展为
同步/异步参数化场景，但测试仍在 testCompile 被 BrokerAdmissionContractValidatorTest 的跨包访问阻断。
clean 首次因验证期间工作树变动失败，重跑后 clean/diff 通过；全仓门禁未刷新，T13 保持 active。

阶段谓词归领域枚举：BrokerClusterOperationPhase 增加 rebuildingRoutes/canAdvanceDrain，
advanceDrain 删除对应局部判断，直接调用阶段语义方法。生产代码 compile、clean/diff 通过；聚焦测试在 testCompile
被现有 BrokerAdmissionContractValidatorTest 跨包访问 package-private validateRequestType 阻断，
失败不属于本次谓词改动，未修改该包迁移或放宽可见性；本轮无新的测试通过证据。

advanceDrain 按已确认示意结构整理：局部 rebuildingRoutes/canAdvance 正向表达阶段资格，
ACK 等待与 cleanup 准备拉平；保留 cleanupResult 命名，提交结果命名为 submitted。
完成调用与响应分行，以 succeeded 和显式 if 表达成功条件；handle 注释说明 cleanupStage
与后续共识提交的异常边界。不新增方法或类型，BrokerDrainAppServiceTest 10 项通过，diff 通过。

advanceDrain 统一后置处理：前半段只决定是否发起 cleanup，后半段统一保存新证据或复用已有证据、
判断等待条件并提交完成；删除新 cleanup 路径的重复收尾。Optional.empty 只表示本轮不需要 cleanup，
不新增方法或结果类型。cleanup 失败仍按期限等待/记录失败，共识提交异常独立传播。
同一聚焦命令完成排空/故障恢复/聚合/进度共 56 项测试，包含新增 RPC 失败的超时前后回归；
clean/diff 通过，全仓 quick/full 未刷新，T13 保持 active Review。

advanceDrain 阅读性整理：按用户 Review 不新增私有方法，证据接受/旧事件等待/终态提交均保留在原方法，
恢复阶段变量改为 evidenceStage，步骤注释统一编号。移除本轮提取的 finishDrain，
不新增上下文或服务类型，底层完成幂等与既有超时策略保持不变。新增证据拒绝、证据提交异常、
完成提交异常回归；内联后排空/故障恢复/聚合/进度共 54 项测试通过，clean/diff 通过。
本轮 quick 自测在用户中断后停止，未形成通过证据。T13 保持 active Review。

路由恢复证据命名统一：DrainRouteSummary 改为 RouteRecoveryEvidence，DrainProgress/DrainProgressUpdate
字段改为 recoveryEvidence，同步应用编排、Adapter、Transformer 和测试。Raft 快照格式升至 v6、
日志命令格式升至 v4，明确拒绝旧格式，不添加 JSON 字段别名。
聚合/排空/故障恢复/日志/快照/ACK/cleanup 共 61 项测试通过，包含恢复证据快照后继续完成排空；
readiness、diff 通过。T13 保持 active Review。
advanceDrain 的 VERIFYING 恢复与已有证据路径统一进入主流程末尾完成提交，SUCCEEDED 重试由底层
BrokerCluster.completeDrain 返回 DUPLICATE；应用层不再单独短路成功幂等。新增验证恢复/重复完成
及旧事件到期后复用清理证据回归，同一聚焦命令共 63 项通过，重复完成不增加 revision、不重复 cleanup。
本轮 clean/diff 通过，quick 的 Harness 自测通过；架构检查仍为 BrokerGossipScheduler 与
RegistryObservationAppService 两项既有外层依赖失败（66 项中 2 失败、2 跳过），未继续 full。

四个 routing AppService 结构 Review：Handler 通过生成的 BrokerRoutingCommandTransformer 将快照、
ACK、cleanup SDK Params 转为 Server CQRS 命令；FrameDelivery 统一依赖 BrokerClusterAdapter，
移除候选查询无用参数和重复去重包装。清理阶段资格与快照水位匹配分别归 BrokerClusterOperation、
DrainProgress；删除依赖空值重复校验和简单结果包装，统一 public/private 顺序及核心步骤注释。
`mvn -q -pl im-broker/im-broker-server -am -Dtest=DrainRouteCleanupAppServiceTest,DrainRouteCleanupAppServicePurgeTest,LocalLeaseActivationAppServiceTest,GatewayRouteSnapshotAppServiceTest,GatewayRouteSnapshotAppServiceAckTest,FrameDeliveryAppServiceTest,FrameDeliveryAppServiceLookupTest,FrameDeliveryAppServiceCandidateTest,FrameProcessHandlerTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false clean test`
通过（42 项），包含 Handler JSON 入站、嵌套水位映射、实际删除和 Raft ACK 持久化；readiness、clean、diff 通过。
增量构建曾因陈旧测试字节码缺失 RouteLookupStub 失败，clean 重建后通过。T13 继续 active Review。
`./scripts/verify.sh quick` 首次在沙箱 `/dev/fd` 权限处中断，沙箱外重跑后 Harness 自测通过；
实际架构检查 66 项中 2 项失败、2 项跳过，失败仍为 BrokerGossipScheduler 引用 infrastructure 配置、
RegistryObservationAppService 引用 BrokerStateStore。未新增规则例外，不跳过失败继续 full；
本轮未刷新真实 E2E/routing-ha，也未执行独立 AI Review。

四个 routing AppService Review 修复：cleanup 核对已提交旧事件截止时间；Lease 先匹配 epoch/nonce
再消费准备记录；快照发布连接表实际用户集合，旧输入不回退 Gossip；负缓存回收使用单次迭代避免并发盲取首项。
三个行为回归先失败后通过，十一类快照/清理/Lease/投递/Lookup/Registry/Gossip 测试通过。
删除 now/currentTime/validateLookup 简单包装和无意义 command 空值防御，保留单调时间、容量、期限、
nonce 防重放与 lookup 一跳限制。不新增协调器或上下文类型；SDK 入站 CQRS 与重复共识依赖的后续结构整理见上条。

recoverRoutes 阅读性整理：已有终态提前返回，重建阶段准备摘要和验证事实，历史 VERIFYING 复用持久化意图，
三条路径统一进入终态提交链；删除三处重复的正常/降级结果判断，不新增包装方法或上下文模型。
未超时不写恢复证据、摘要拒绝不推进及降级中断恢复回归通过，排空/聚合/进度测试通过。

集群应用共识提交语义化：摘要记录、排空完成/强制结束、epoch 授权开始/完成、降级验证和恢复终态
收敛为 BrokerClusterAdapter 的具名单次提交方法。Raft 载荷转换归 BrokerClusterRaftTransformer，
应用层保留超时、证据、安装汇总和正常/降级分支，不把整个用例迁入 Adapter。
DrainProgress 统一 ACK 齐全判断与清理摘要组合；删除应用层 persistSummary/completeDrain/failDrain 包装。
排空、恢复、授权、隔离和领域聚焦测试通过，真实 Adapter/启动回归通过。

Broker application 目录收平：确认 port 已无类后删除空目录，九个 AppService 及同包测试从
application/service 上移为 application/cluster、routing、gossip、diagnostic；引用和定时入口识别同步。
分层检查改为直接能力分组并禁止旧 service/port，行为不变。15 类 clean 聚焦回归、启动/HTTP/事件/任务
测试通过；新能力包结构、定时 AppService 入口、领域无技术依赖及模块边界聚焦架构检查通过，clean/diff 通过。
整体架构此前的 Gossip 两处基础设施依赖仍保留，未将 T13 标记完成。

Broker port 收敛：删除 diagnostic/routing 单实现接口及对应转发类，诊断归 BrokerDiagnosticAppService，
RouteLookup 单次 RPC 并入 BrokerClusterAdapter，Gateway 写帧同样并入现有 Adapter 调用 SDK 并保留逐目标指标。
RouteLookup 并发线程池生命周期归配置层，原并发上限和预算仍生效。
BrokerRegistry 迁 domain/cluster，GatewayRegistry、ConnectionRegistry 迁 domain/connection；
返回 Server 自有注册观察及同步结果，SDK 转换仅在传输边界。Gossip 载荷形状和时间水位映射回归通过。
诊断/注册表/Gossip/投递/lookup/HTTP/真实 Adapter/启动聚焦测试通过；正式 architecture
此前受 im-iam-sdk JaCoCo EOFException 阻断。clean install 后实际架构测试发现应用层 SDK/基础设施依赖，
已修正本轮 Gateway 与诊断依赖（具体 Tracker 归 adapter，身份配置归应用配置）。19 类相关回归与启动通过；
最新实际架构测试 66 项中 2 项失败，剩余均为此前已有的 BrokerGossipScheduler 基础设施配置依赖和
RegistryObservationAppService 的基础设施状态存储依赖；领域无 SDK 依赖及模块边界规则通过。
不将整体架构门禁标记通过，不跳过失败继续 full。
诊断公开参数同步为 ConnectionRoutesQuery/GossipRecordsQuery，HTTP 基础参数在 Controller 转换，
诊断应用与 HTTP 回归通过；clean 静态检查与 diff 检查通过，未保留基础类型兼容重载。

清理结果领域化：DrainRouteCleanupSummary 改为 domain/cluster/RouteCleanupResult，数量字段为 RouteCount，
校验字段为 Boolean；Adapter/Transformer 负责 SDK 转换，应用层直接合并领域结果与重建数量。
删除旧 application/port 模型，排空/恢复/授权/隔离回归通过，真实 cleanup Adapter 转换验证通过。

cleanup RPC 合并：删除 BoltDrainRouteCleanupClient 及独立线程池，应用服务直接调用
BrokerClusterAdapter.cleanupRoutes(operation)。Adapter 复用有界 peerExecutor，读取领域目标，
Transformer 转换请求/摘要；超时继续取 BrokerRoutingProperties.drainCleanupRpcTimeout。
排空、故障恢复、授权和隔离聚焦回归通过，cleanup 调度/载荷/远端拒绝与真实启动验证通过。

cleanup 接口简化：删除只有一个实现的 DrainRouteCleanupClient，集群应用服务与配置直接依赖
BoltDrainRouteCleanupClient；保留原 RPC、响应判断和专用执行器生命周期，不增加替代抽象。
排空、故障恢复、应用、授权与隔离聚焦测试通过，测试改为具体客户端 mock。

集群路由操作生命周期合并：BrokerClusterAppService 保留 drain 发起排空，新增 advanceDrain、recoverRoutes
及 forceDrain，迁入原排空/故障恢复流程并删除两个独立 AppService 和配置 Bean。
定时入口直接调用对应方法，cleanup 依赖及操作超时由配置层注入；public 入口在前、private 步骤在后。
十类应用/租约/调度聚焦测试及真实启动装配通过，降级中断恢复回归保留；不推进 T13 全仓收口。

故障恢复职责 Review：两次降级阶段提交间中断的回归先失败（期望 DEGRADED，实际 SUCCEEDED）。
修复 VERIFYING 恢复时依据已持久化 error 选择正常/降级终态，领域操作拒绝残缺证据或降级意图的成功提交。
降级摘要与验证阶段串联为连续流程，删除 advanceToDegraded；排空 timedOut 简单包装展开。
恢复、排空、聚合、快照、编码和 Gateway ACK 回归通过；用户当前 scheduler 目录保留，续租测试同步归包。

注册表事件入口迁移：四个 RegistryEventListener（含公共异常隔离基类）迁入 interfaces/listener，
新增 RegistryObservationAppService 协调事件到 Gossip 状态更新，不让接口层直接依赖存储实现。
存储和 Spring 应用启停监听继续属于 infrastructure。Spring 事件分发、移除/快照及异常隔离回归通过。
五个定时入口的启动注册断言已修正为识别 Spring 包装后的任务，启动/任务/监听回归通过。
正式 architecture 重新生成 Broker 构建证据后，在 im-iam-sdk 的 JaCoCo EOFException 处失败，
未执行到架构规则检查；T13 保持 Review，不跳过失败推进 full 收口。

定时入口归属 Review：成员恢复、排空、故障恢复、续租和候选历史观察迁至 interfaces/task，
同包测试及配置引用同步；成员任务不再依赖 BrokerRaftProperties，恢复等待时长由配置层装配。
任务周期、Leader 条件和单轮执行行为保持不变，聚焦恢复/续租/排空/候选测试通过。
新增 Broker 定时 AppService 入口归属架构检查及五个任务的 Spring 定时注册断言。
直接 reactor 架构测试曾受 Gateway 两处旧 clusterId 构造器调用阻断；正式 architecture 入口
因本地 Maven 仓库写权限失败后，沙箱外重跑先遇 Broker JaCoCo 数据错误，重新生成 Broker 构建证据后
又遇 im-iam-sdk JaCoCo EOFException；未标记架构门禁通过。

操作 epoch 命名 Review：BrokerClusterOperation.targetEpoch 改为 resultEpoch，字段、Builder、JSON 创建入口
及领域/应用/响应转换调用同步迁移；CQRS 对外 targetEpoch 字段由 Transformer 保持原映射。
持久化 operation JSON 同步改名，快照格式升至 v5 并验证拒绝 v4；聚合、意图、拓扑隔离、快照、
命令编码、响应转换、授权/隔离/排空/恢复聚焦测试通过，真实 Adapter 快照及重启恢复测试通过。

授权汇总简化：authorizeCommittedEpoch 只消费是否全部安装成功，去除未使用的待重试身份列表及排序，
直接异步聚合布尔安装结果。新增一节点失败、另一节点仍等待的受控回归，验证不提前结束本轮，
且全部请求结束后保留授权阶段供重试。授权和隔离测试通过，diff 检查通过。

新 epoch 授权流程合并：`authorizeCommittedEpoch` 统一读取最新操作、准备授权阶段、并行安装 Lease、
聚合未确认节点及提交 REBUILDING_ROUTES；删除 authorizeActiveBrokers 和无调用方的 RoutingEpochActivationResult。
部分授权失败仍保留阶段供重试，已完成授权直接结束。授权/隔离/签发/安装/原子性/续租及选定历史重试、
排空和故障恢复回归通过，未刷新全模块或推进 T13 收口。

Adapter 参数 Review：操作查询、epoch 查询和领导权确认统一接收 ClusterOperationId、RoutingEpoch、
QuorumReadNonce，删除基础类型签名并同步状态机、共识接口、应用及生命周期调用和测试替身。
读取上下文保留原 prepare nonce、operationId 或独立 UUID 的内容，Raft 字节转换留在 Adapter 内部。
13 类查询、签发、安装、隔离、排空、恢复和快照 ACK 聚焦测试通过，真实 Adapter 15 项与启动测试通过。
Broker Server 全模块回归执行 435 项，3 项错误均为 BrokerClusterAppServiceChangeTest 的旧异常返回约定
与当前实现不一致（拓扑 Commit 拒绝 1 项、quorum 读取失败 2 项）；未将全模块标记通过，不跳过失败继续 full。

本机签发原子性：应用层移除 quorum 后的第二次资格检查，改调 BrokerClusterAdapter.grantLease；
底层使用状态机 apply/restore 共用的同步边界，对实际聚合判断资格后分配签发序列。
签发服务依赖迁入 Adapter，应用服务只保留流程编排；锁内不等待 RPC/quorum。
原子性回归验证 fence 无法插入资格检查与序列分配之间，fence 先应用时拒绝且不调用签发服务；
聚合、签发、安装、授权、隔离、续租及应用聚焦测试通过，真实 Adapter、配置及启动装配测试通过。
同类异步链整理：prepare 完成后直接构造请求并确认领导权；holder 授权按响应携带的节点身份聚合，
不再使用列表下标和 join；fence 恢复减少嵌套，阶段提交注释置于链式步骤前。相关聚焦回归通过。

签发资格归聚合：`BrokerCluster.checkLeaseGrant` 使用自身 epoch、节点和 routingFence 判断准入，
返回 Optional 拒绝结果；RoutingLeaseGrantService 只分配任期/序列并生成 Lease。
应用服务在 prepare 前和 quorum 后读取聚合检查资格，不再拆开读取 fence 或组装 ACTIVE 集合。
聚合拒绝类型、只读行为、历史 fence、新 epoch、签发竞态及安装/授权/续租回归通过。

租约准入前移：`activateLease` 在 prepare RPC 前统一检查 epoch、ACTIVE 身份和持久 fence，
不再单独重复检查入口 epoch；`checkAdmission` 接收目标 Broker 和请求 epoch，不依赖 prepare 生成的 nonce。
过期、非 ACTIVE、已 fenced 和缺失节点四类准入拒绝均验证不发送 prepare/quorum/install。
quorum 后资格重验保留，签发、安装、授权、隔离和续租聚焦测试通过。

`activateLease` 签发资格 Review：删除宽泛 revision 比较和重复 epoch 比较，quorum 前后领域资格检查保留；
签发与安装阶段顺序注释整理，异步失败记录目标及异常后仍返回 false。
无关 revision 更新的回归先失败（期望安装成功、实际 false），调整后通过；epoch 更新、目标失去 ACTIVE
及 fence 竞态继续拒绝安装，签发、安装、授权、隔离和续租聚焦测试通过。

租约安装 Review：删除 `requestLease`、`grantAfterRead` 和签发资格转换私有方法，
prepare、资格检查、quorum、读取后 topology/fence 重验、签发与 install 统一在 `activateLease` 展示。
签发测试通过真实公开安装入口验证结果；隔离竞态拒绝安装且不消耗序列、任期回退、重启后的 fence、
安装顺序、授权和续租聚焦测试通过，diff 检查通过。

本轮 epoch 推进 Review：`advanceEpoch` 接收已登记操作，返回 `CompletionStage<Void>`；
调用方保留登记结果并刷新操作阶段，部分授权失败保留待重试，拓扑 Commit 拒绝异常结束且不继续授权。
删除 `continueClusterChange`，四个路由用例显式编排登记、epoch 推进和响应构造；
四个用例的响应映射直接接收登记结果和最新操作，删除仅用于刷新中间共识结果的 Transformer 重载；
Transformer 及应用、隔离、授权、签发和安装聚焦测试通过。
聚合确认它们均改变 routing epoch，不保留仅适用于成员变更的 PENDING 直接 Commit 分支。
应用聚焦测试及过期请求、激活重试、隔离顺序、排空和故障恢复证据的选定回归通过。
`FenceResult` 通过 `allAcknowledged` / `quarantineCompleted` 隐藏调用处的 status 参数。
应用、Fence、授权、签发和安装聚焦测试通过；readiness、clean 与 diff 检查通过。
quick 首次因沙箱禁止访问 `/dev/fd` 中断；沙箱外重跑的 Harness 自测通过，随后在
Broker Server JaCoCo 报告生成因 `Unknown block type c0` 失败，架构门禁尚未验证通过。
T13 保持 active，不跳过失败层继续 full，不推进收口。

本轮 fence 最终校验收敛到 BrokerCluster.commitRoutingFence：补齐冻结隔离集合等于
当前 ACTIVE 集合的日志应用边界校验，删除 closeGrants 的 ReadIndex 前后 revision/epoch 比较。
新增错误集合首次提交回归先失败为期望 CONFLICT、实际 APPLIED，修复后通过；
新增无关 revision 更新不阻断 fence 提交的应用回归，领域/签发/Fence/快照测试通过。
原 operation 阶段、epoch 与任期/ReadIndex 更新约束保留，不推进收口。

最新租约职责收敛：BrokerClusterAppService 编排关闭旧 grant 和 prepare/quorum/grant/install，
BrokerClusterAdapter 提供单次 RPC 与共识能力，RoutingLeaseGrantService 持有资格和签发序列。
删除 RaftRoutingAuthorityLeaseCoordinator/BrokerLeaseAdapter，续租任务调用同一应用用例，
无应用服务互调、不拆开一次拓扑请求的连续授权行为。领域不依赖 SDK 或基础设施。
签发、安装顺序、fence 竞态、续租、隔离、授权以及真实 Adapter/启动测试通过，不推进收口。
安装公开入口接收基础类型 ActivateRoutingLeaseCommand，应用内转换标识并读取聚合，
拒绝过期 epoch；最终 17 个租约聚焦测试和 clean 通过，不增加应用服务互调。

本轮修复 Lease ReadIndex 等待期间提交 fence 后仍签发旧 epoch 的缺口：grantAfterRead
在检查状态变化后重新执行当前准入校验，命中 fence 返回 FENCED，不消耗签发序列。
新增受控 Future 回归先复现 GRANTED 错误，再验证修复；协调器、安装、续租、Fence、授权测试通过。
其他 closeGrants 校验暂保留，本轮不声称对全部并发时序完成验证。

最新按用户要求禁止 quarantine 使用公共线程池：配置层提供独立 brokerQuarantineScheduler，
应用服务直接 schedule 完成信号，拒绝时失败且不继续拓扑提交。未重新引入等待函数。
配置、Fence、授权及应用入口测试通过；旧 delayedExecutor 记录是历史方案，当前已移除。

最新移除 BrokerClusterAppService 的 quarantine 等待函数注入及其 Bean，
直接在 ACK 未全部确认分支调用 CompletableFuture.delayedExecutor。
测试从标准延迟调度边界控制等待，继续验证 ACK 缺失/旧任期/队列拒绝时不得提前提交 epoch。
Fence、授权与应用入口测试通过；未刷新 full，不推进收口。

本轮按用户确认收敛 application/port/routing/lease 六个单实现接口：
协调器只接收 BrokerClusterAdapter，删除重复 currentState 转发；安装实现迁为
adapter/cluster/lease/BrokerLeaseAdapter，签发/安装仍分离且无循环依赖。
QuarantineWaiter 改为标准 LongFunction 异步等待契约，生产 delayedExecutor 和
测试受控等待语义保留。签发、安装、续租、fence、授权和启动聚焦测试通过，不推进 T13 收口。

已按用户最终指令将 fence peer 请求合入 BrokerClusterAdapter.fenceBroker：
删除 FencePeerClient/BoltFencePeerClient，新增 RPC 载荷 Transformer 和配置层拥有的有界
peerExecutor，线程/队列上限为 8/128，可配置，饱和拒绝不回退调用线程。
异步调度、真实并发、拒绝、ACK 映射、quarantine、Spring 启动回归通过；behavior 通过
30 个场景，直接分层/模块边界测试通过。architecture 门禁提权后仍被 IAM JaCoCo EOFException
阻断，不声明全仓通过。其余 lease port 当前只审查，不改租约或隔离行为。

本轮将 authorizeEpoch 合入 BrokerClusterAppService.authorizeCommittedEpoch，删除仅被测试
直接调用的授权入口；公开 activate 回归覆盖全部 holder 授权及部分 holder 未确认时保持原阶段，
保留 Commit 未接受不继续授权的失败路径。授权、fence 与应用入口聚焦测试通过，不推进收口。

本轮整理 adapter/cluster：删除无生产接口调用方的 BrokerConsensusMembershipClient，
移除共识 Result.state 和成员 Result.membership，避免为构造失败结果读取状态；快照恢复测试
改用状态机/独立快照来源。租约协调类及测试迁入 lease 包，共识与成员结果继续分离。
同时删除仅被测试调用的 closeEpoch，fence 测试改从 activate 公开用例验证 ACK 与 quarantine。
真实 Raft、快照/租约/结果/应用/恢复测试与 LayerBoundaryTest、ModuleBoundaryTest 通过；
本轮 clean 通过，quick 提权重跑的 Harness 自测通过，但在 im-iam-sdk 的 JaCoCo report
EOFException 处失败；不修改 IAM，不用直接分层测试通过覆盖 quick 失败。
不推进 T13 收口，全模块共享成员异常契约问题仍按前述记录保留。

BrokerClusterAdapter 通用共识入口按用户确认统一命名 submitChange，不使用 Command 或
replicate 前缀；registerOperation/commitOperation 分别表达 Begin 登记和 Commit 拓扑提交。
接口、应用调用和测试替身同步，无旧名兼容转发。Cluster/Fence/Drain/FailureRecovery 聚焦测试通过。

本轮按成员/Quorum 应用服务风格调整 Cluster、Drain、FailureRecovery 三个应用服务：
预期 epoch 冲突与领域拒绝返回 Result 工厂结果，历史 epoch 输出和路由摘要命令映射归
Application Transformer；移除 requireAccepted/now/completed 包装及 CleanupOutcome。
29 个目标类与 Transformer 测试、9 个拓扑拒绝/fence/drain/Commit 失败回归通过。
修复 fence 后 Commit 未接受仍继续授权、读取空 operation 的失败路径；现在原样返回共识失败。
clean 通过；quick 的 Harness 自测通过，随后在 im-iam-sdk JaCoCo report 的 EOFException 处失败。
共享成员测试仍有两项既有失败：同步 confirmLeadership 异常直接传播，与原结果转换断言不符。
不改用户已 Review 的 Membership/Quorum 实现，不将窄范围通过视为全模块通过，不推进 T13 收口。

当前继续 T13 单集群身份简化：用户批准去掉业务层 clusterId 透传，技术持久化归属保留。
设计记录为 docs/design-docs/2026-09-30-broker-single-cluster-identity.md；领域、SDK 协议、
控制面及快照 envelope 的调用链正在迁移，不推进收口。

已按用户要求在 im-raft 新增 RaftPeerClient，集中封装 Bolt 应用 ReadIndex 证明查询；
BrokerClusterAdapter 只持有 RaftPeerClient，不再直接引用 BoltRpcClient。插件接收泛型
载荷、响应类型、服务/操作/超时，不依赖 Broker SDK；Broker 保留身份和非负索引校验及
原异步失败契约。im-raft 显式依赖 im-bolt，未新增接口、兼容构造或 Spring 装配。
45 个定向、17 个真实 Raft/启动及 31 个分层/模块边界测试通过，README/ARCHITECTURE/
Harness 已同步；该实现替代此前 Peer Client 尚未新增的记录，不推进全仓收口。

进度查询统一 queryAppliedReadIndex、GET_APPLIED_READ_INDEX/getAppliedReadIndex，SDK 请求响应
改为 AppliedReadIndexParams/Result、appliedReadIndex 字段；Handler 与 BrokerProgressAppService
同名语义，不再暗示 learner 身份或最新 applied index。无兼容别名，节点须匹配版本。
协议/本机/应用/成员聚焦 45 个测试、真实 Adapter 和启动 12 个测试及分层/模块边界 31 个测试通过。
Raft Peer Client 仍是讨论方案，未新增通用插件对 Broker 模型的依赖，不推进收口。

本机角色、已知 Leader 地址和应用 ReadIndex 查询移入 LocalBroker，经 getLocalBroker 访问，
共享 Adapter 所持的 RaftRuntime，不增加接口、缓存或生命周期；旧扁平入口删除，集群命令
仍留在 BrokerClusterAdapter。远端查询改名 queryAppliedIndex，参数 targetBroker，响应错误
说明不再绑定 learner 角色，既有 RPC 协议名称不改。角色/索引/成员/调度聚焦 47 个测试、
真实 Adapter 与启动 12 个测试及分层/模块边界 31 个测试通过，不推进收口。

按用户确认，learner 远端查询合入统一 BrokerClusterAdapter，接口与临时拆出的 Adapter 删除；
集群入口迁至 adapter/cluster，Raft 状态机/Codec/快照留在 raft 子包。应用服务只有一个
Adapter 依赖，保持领域参数、属性对象、超时与响应身份校验；无兼容构造器或转发壳。
成员/恢复聚焦 40 个测试、真实 Adapter 加启动 12 个测试及分层/模块边界 31 个测试通过。
新回归拒绝错误 clusterId、brokerId 和负索引，文档/Harness 同步统一技术入口规则。
本机门面分组是用户正在讨论的下一项方案，本轮尚未实现，不推进全仓收口。

Broker 共识契约及 Adapter 的 isLeader 改为 isLocalLeader，明确本机 Raft 节点角色；
成员检查、排空/恢复/租约调度、测试替身和真实 Raft 测试同步，不保留旧方法别名。
底层 im-raft/JRaft API 不改，ReadIndex 确认流程不变。五类聚焦 47 个测试和真实
BrokerClusterAdapterTest 10 个测试通过，README/Coding Guide/Harness 同步角色与多数派
证据的区分；不推进收口。

BrokerMembershipAppService/Controller 的同步确认入口改为 confirmLearner，恢复分支和
应用测试调用同步；领域 completeLearnerSync、Command/Result 和 HTTP 路径保持不变。
补齐快照恢复后通过真实 AppService 完成 PENDING 成员操作的回归，验证 Follower 不执行、
成为 Leader 后使用持久化端点恢复且不重复变更；真实 learner 测试新增 quorum ReadIndex
与本地 appliedReadIndex 比较。`mvn -q -pl im-broker/im-broker-server -am clean test` 通过，
`mvn -q -pl im-test/im-architecture-test -am -Dtest=LayerBoundaryTest,ModuleBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`
31 个测试通过。计划中的旧签名阻断已清除，端点绑定与 catch-up 证据已完成；
真实换届加定时恢复的全链路证据、广层 Harness 门禁仍保留，不推进收口。

Broker 六个生产类的 UUID 字符串生成统一复用 GeneratorUtils.nextUUID，覆盖成员 ReadIndex
上下文、投递标识、租约 nonce/续期操作标识、临时水位文件名及 Adapter 应用索引请求标识。
无直接 UUID 调用残留，格式为不带横杠的 32 位字符串；当前使用点不依赖标准 UUID 解析。
七类聚焦 56 个测试及真实 BrokerClusterAdapterTest 10 个测试通过，Coding Guide/Harness
同步通用生成器与协议格式边界；不推进收口。

learner 进度检查三处构造改为 BrokerClusterConsensusResult.notLeader/rejected 工厂：
固定非 Leader 原因内置，两个不同拒绝原因仍由调用方传 message，空字段由工厂集中维护。
不改变异步流程和错误原文。三类聚焦共 59 个测试通过，Coding Guide/Harness/README
同步固定原因与多原因的工厂规则；不推进收口。

同步完成编排改为 completedFuture(decision).thenCompose，根据 decision.duplicate 区分
历史重试与新操作，删除 newOperation 和 progressCheck 中间变量；Commit/终态/Transformer
保持共同链路。不新增只读准入接口或提交流程辅助方法。领域分类与历史重试不重复读取
同步证据的回归增强，三类聚焦 74 个测试通过；README/Coding Guide/Harness 同步时序规则，
不推进全仓收口。

本轮最终职责划分（替代此前将共识映射放入 Result 的记录）：六类共识到应用结果的字段映射
统一回到 BrokerClusterAppTransformer，结果类保留 conflict/rejected/failure 业务工厂，
工厂直接接收领域标识/epoch，应用服务不为映射增加成功失败分支。Coding Guide 和 Harness
记录规则，LayerBoundaryTest 新增禁止 Broker Result 转换命名的机械检查，业务语义 Review-only。
Broker、im-raft、Ops 的 Leader 地址统一为 leaderEndpoint，客户端/前端同步，不保留旧别名。
71 个 Broker 聚焦、16 个交叉回归、19 个分层测试及 Ops typecheck 通过；最初分层单跑读到
旧 Maven 产物中的五处 from 违规，改用当前源码 reactor 测试后通过。不推进 T13 或全仓收口。

AddLearnerResult.from 直接接收 ClusterOperationId，值对象拆箱移入结果工厂，应用服务
不再透传 operationId.value()；结果工厂与成员编排聚焦 48 个测试通过，保持 Review 状态。

AddLearnerResult 的共识结果映射已并入 `from(operationId, result)`，应用服务直接调用，
删除 BrokerClusterAppTransformer.addLearnerResultFrom，避免两套转换入口。结果工厂、
同步结果、HTTP 映射、应用 Transformer 和成员编排五类聚焦共 67 个测试通过，
新增六类共识状态原样映射验证；保持 Review，不推进收口。

`AddLearnerResult` 与 `CompleteLearnerSyncResult` 的 status 已改为完整覆盖结果状态的
BrokerClusterConsensusStatus 枚举，HTTP 响应仍为原状态名称字符串。固定 epoch 冲突和
同步端点拒绝 message 内置结果工厂，调用方不再传原因或空字段；同步领域失败也直接
使用 failure 工厂，保持关联操作的原接受/目标 epoch。五类聚焦共 61 个测试通过，
包含六类状态的 HTTP 映射和历史请求身份冲突的 epoch 回归；仍处于 Review，不推进收口。

`AddLearnerResult.leaderHint` 已改为 `leaderEndpoint`，HTTP Transformer 显式映射到原响应
leaderHint，保持协议不变。新增 `conflict` 与 `failure` 具名工厂，`addLearner` 本地冲突及
领域失败直接使用结果工厂，删除领域决定到 AddLearnerResult 的 Transformer 重载。
四类聚焦共 51 个测试通过（结果工厂、HTTP 映射、应用 Transformer、成员编排），
原领域失败状态与原因均保留；仍处于用户 Review，不推进收口。

`addLearner` 的领域拒绝改为直接由 Transformer 转换为 AddLearnerResult，不再构造
BrokerClusterConsensusResult 或读取 committedState。成员编排和 Transformer 聚焦共 49 个测试通过，
覆盖拒绝无共识读取及三类领域失败状态映射。后续已改为完整覆盖 NOT_LEADER 的共识状态枚举，
不向领域 Status 加入技术协议状态；仍保持 Review。

成员应用用例的 epoch/端点前置拒绝已改为直接构造应用结果，删除九处仅用于转换的
BrokerClusterConsensusResult 和额外 committedState 读取。九个前置拒绝场景验证无共识读取、
无写入副作用；成员编排、生命周期和 Transformer 三类聚焦共 47 个测试通过。
共识提交与领域判断原有转换链路不变，本轮不推进收口。

成员应用服务的五个写用例统一错误契约：epoch 不匹配返回 CONFLICT，`addLearner` 的领域
拒绝也经 Transformer 返回业务结果，不再抛参数异常。保留历史幂等重试；同步证据检查
使用本地登记之前保存的新操作标记，防止登记后 exists 判断跳过检查。三类聚焦测试
共 43 个通过（BrokerClusterAppServiceChangeTest、BrokerMembershipLifecycleTest、
BrokerClusterAppTransformerTest），规则同步 Coding Guide/Harness/README，不推进收口门禁。

用户确认保留原有请求横切组件归属：五类 `BrokerWriteAccess*` 已迁回 `support/context`，
MVC 注册配置留在 `infrastructure/config`。只恢复包位置和引用，保留权限检查及同步/异步
ThreadLocal 清理行为。Coding Guide、Broker README 和 Harness 同步该受限目录职责；
Architecture Test 增加禁止上下文依赖业务实现的约束，不增加类名特例。管理入口和上下文清理
8 个聚焦测试通过，`mvn -q -pl im-test/im-architecture-test -Dtest=LayerBoundaryTest test`
18 个分层测试通过。`./scripts/verify.sh architecture` 的测试无失败，但工作树指纹在验证期间
变化，门禁返回 1，不能作为有效收口证据；不撤销其他改动，不推进 T13 收口。

本轮继续 Review `BrokerMembershipAppService`，保留已审阅的 `addLearner` 主流程。
其余成员用例的当前端点绑定检查只约束新操作，历史重试仍由聚合核对摘要、类型与目标；
结果中的 acceptedEpoch 沿用已登记操作。同步完成先判断本地资格，再读取成员与 quorum 进度，
读取失败区分 NOT_LEADER 与 UNAVAILABLE，失败时不提交 Begin。非法终态时间转换为领域拒绝，
不修改原操作，也不做时间补偿。38 个聚焦回归通过，包含真实单 voter Raft 连续提交验证；
`mvn -q -pl im-broker/im-broker-server -am test` 全模块回归通过。
按用户要求仍处于 Review，不推进 T13 或全仓收口门禁。

本轮收尾正在修复已知架构违规：管理 HTTP 上下文及权限注解迁入 interfaces/http/management，
读取配置的 MVC 拦截器迁入 infrastructure/config；learner 已应用索引查询经 LearnerProgressAppService
编排，本机领域身份在配置装配时固定。管理协议不变；独立审查提示的初始 Servlet 异步线程
清理缺口已补 AsyncHandlerInterceptor 回调及确定性回归。聚焦与 Broker clean test 全模块通过，
clean 移除旧类字节码后 architecture 与首轮 quick 通过，两项已知违规已消除。
真实 routing-ha runner/清单的陈旧类名和方法定位已更新，新增 JDK AST 源码定位检查与
并发拒绝/恢复不自动激活回归，聚焦通过。修改后最终 quick、behavior、full 待刷新。

成员流程五处 Begin 结果判断收敛为 BrokerClusterConsensusResult.hasPendingOperation()；
接受状态、无操作、拒绝和非 PENDING 分类及成员编排/恢复聚焦回归通过，不改变阶段推进。

本轮 operation 时间精度收敛：构建与 JSON 重建统一 createdAt/completedAt 为毫秒，允许同毫秒
完成，Transformer 直接映射调用方时间，删除 +1/Math.max 时间补偿，早于创建仍拒绝。
“不要过度防御”已写入 Coding Guide 和 Harness 矩阵，保留边界/安全/不变量校验，通用规则
Review-only，时间实例有确定性回归；聚焦及 Broker 全模块重跑通过。此前架构违规未处理，
本轮未刷新 architecture/quick/full，不视为 T13 完整交付。

AdvanceClusterOperationCommand 已改为普通类和手写 Builder，字段有语义注释，时间未设置与合法零值
明确区分，JSON 重建执行相同校验；所有构造点迁移，成功命令映射归现有应用 Transformer。
完成时间拆为 earliestCompletionMillis 与 completionMillis，保留纳秒创建时间的毫秒下限。
命令、Transformer、codec、成员、排空、故障恢复及激活聚焦测试通过；本轮未刷新全模块证据。

成员结果对象新增 rejected()，应用服务不再直接比较 REJECTED 枚举；全状态分类、成员编排和
Transformer 聚焦回归通过，结果语义及失败处理顺序不变。

当前失败类型策略已归 BrokerClusterOperation.canFailOnRejection；应用服务保留明确 REJECTED 判断
和共识提交。所有 operation 类型分类及成员结果/恢复/Transformer 聚焦回归通过，判断行为不变。

当前成员结果编排收敛：finishMembershipChange 改为 handleMembershipResult，移除 singleStep 参数，
应用层按类型采用保守失败策略。失败命令、默认原因和纳秒时间边界移入现有 Transformer；
加入/移除拒绝、晋升/降级保留 PENDING、失败记录提交不可用与映射聚焦测试通过，全模块待重跑。
该调整后的 Broker 全模块重跑通过；完整门禁仍受此前架构违规阻挡，T13 保持 active。

Broker 当前规则判断命名统一为 checkAdmission、checkEndpointBinding、checkOperationConstraints、
BrokerNode.checkOperation 和 checkRepeatedIntent；只改名称，不改变返回类型、拒绝原因或副作用。

本轮节点规则归属调整：BrokerNode.assessOperation 判断自身角色/流量状态，实体转换复用该判断；
BrokerCluster.assessOperationConstraints 只组合节点资格与身份、端点、ACTIVE 数量、voter 下限。
六类聚焦测试通过，覆盖合法状态组合、拒绝不修改状态和节点允许但集群数量规则拒绝的边界。
该调整后的 Broker 全模块测试也已通过；既有架构违规仍未处理，T13 保持 active。

本轮准入整理保持逻辑在 BrokerCluster：端点绑定与节点/集群约束返回 Optional<DecisionReason>，
assessAdmission 统一区分 CONFLICT/REJECTED，Begin 和 Commit 共享无副作用规则判断，不再用异常
表达正常业务拒绝。revision 在登记前计算，防止溢出后留下部分操作或端点绑定；聚焦回归已通过。

当前确定采用 Begin 登记事实、Commit 执行实体行为，已替代以下目标拓扑方案。Proposal/operation
不再持有节点列表，CommitBrokerOperationCommand 与 commitOperation 对应明确操作应用；日志 v2、
snapshot v3。聚焦及 Broker 全模块测试通过，独立静态 Review 未发现本次回归；补充 pending
新增节点与降级/移除在快照恢复及 Leader 配置变化下继续使用冻结事实的回归均通过。
proposal.learnerJoin() 统一两处加入判断且未增加 JSON 字段。architecture 本轮仍在此前两项
边界违规失败，未继续 quick/full；T13 保持 active。
submitOperation 已按重试识别、前置校验、并行操作约束、节点业务校验、事实构造及登记分段；
生命周期阻塞规则归 BrokerClusterOperation.blocksNewOperation，路由分类归 Proposal。
构造成功后才预留历史容量，registeredRevision 明确表示 Begin 登记后的 revision。
全部阶段分类与六类聚焦回归通过，本次结构整理后的 Broker 全模块测试重跑通过。

本轮流程收敛：保留完整目标拓扑 Begin/Commit，不实施另一套意图重放协议；各具名变更只准备
独立的目标节点列表，移除用于计算目标的整个聚合副本和 findOwnedNode 包装。Broker Server README
新增 learner 全链路状态归属表和时序图，当前验证结果待刷新，T13 保持 active。

2026-09-30 T13 当前调整：九类 Broker 变更入口迁移为专属领域 Intent，重复请求匹配迁入
`BrokerClusterOperation.assessRepeatedIntent`；聚合继续持有 revision、拓扑和成员不变量。
Intent、聚合、应用服务、编解码与成员恢复聚焦测试及 Broker 全模块测试已通过，独立静态
Review 未发现具体问题，设计增量结论为 Approved；`clean` 通过。
`architecture` 在 `support.context` 包范围与 `LearnerAppliedIndexHandler` 直接依赖基础设施配置
两项边界检查失败；本次未修改这些类。遵循逐层验证顺序，未继续运行 `quick/full`。
同日后续小调整：`addLearner` 改用 `BrokerNode.canJoinAsLearner()` 明确返回准入拒绝，
不再用异常控制正常分支；目标状态生成和完整提议按顺序展开。节点、Intent、聚合及应用服务
聚焦测试通过，覆盖所有成员角色与拒绝时不修改状态；全模块证据仍属于此前 Intent 迁移版本。
保留现有工作区修改，T13 仍为 active，不代表完整发布门禁通过。
当前后续调整已将 BrokerNode 改为具名可变状态行为，聚合在目标快照内修改节点并删除
replaceBroker；构造、查询、Proposal、operation 与 Commit 边界深拷贝。实体、拓扑隔离、
聚合、共识编解码、Raft snapshot 隔离及应用服务聚焦测试、Broker 全模块测试通过；原生
序列化往返与可变节点独立性回归通过。独立 Review 未发现本次新增问题，增量 Approved；
架构门禁重跑仍在此前两项边界违规失败，未继续 quick/full。既有 Builder 在 build 后复用
同一目标的生命周期限制未在本次调整中扩大处理。

当前工作树的验证状态、耗时、测试数和 freshness 以机器生成的 `.harness/report.json` 为准。本文件不复制结果，避免更新状态摘要反过来使 revision-bound evidence 失效。

## 阻塞项

- 仓库当前只有 `deploy/local` 本地编排，没有生产候选 3/5 voter 环境、环境专用 capacity driver、真实部署端点与故障控制能力。因而无法在本机完成三次真实容量测量，也不能据此填写生产机器数值；功能和 Harness 实现不受阻塞。
- T13 最终运行证据受本机 Docker Desktop daemon 无响应阻塞：最新 `routing-ha` 耗尽 900 秒总预算并返回
  124；Harness 台账保留 `im-chat-routing-ha-36683`，须在 Docker 恢复后先核验清理并重跑。

## 下一步（Next Steps）

2026-09-29 Broker Review 修复进度：Broker 全模块 Maven 测试已通过；聚合和应用服务回归测试证明
提交后的同 operationId 重试不再重复外部成员变更，FAILED 不能冒充已提交拓扑，路由恢复在终态前
阻止新拓扑。单步 membership 明确拒绝记录 FAILED；两步成员变更的拒绝及未知结果保留 PENDING，
成功的非路由成员操作进入 SUCCEEDED。管理拦截器迁入 infrastructure/config，聚焦架构测试通过。
`behavior` 30 场景、`quick`（含 architecture）、`clean` 通过；`full` 在 Harness 资源采样节奏自测
因宿主负载导致采样间隔越界而提前失败，E2E/routing-ha 未执行。
成员 PENDING 的 Leader 换届自动恢复、brokerId 与 membershipEndpoint 的持久化绑定、
learner 日志追赶证据尚未完成，T13 保持 active，不视作可发布状态。

2026-09-29：新增 `im-plugin/im-raft`，BrokerClusterAdapter 改为通过 RaftRuntime 提交字节日志并提供
领域状态机/快照回调。插件真实集成测试 5 项通过，Broker 生产 compile 通过；Broker 聚焦测试被此前
重构尚未迁移的测试签名阻断，包括 BrokerClusterAppServiceChangeTest 已删除方法及 Lease 的 SDK/领域类型
不匹配。当前独立 Review、behavior 和 full 未完成，T13 保持 active。
本轮 `architecture` 在 JaCoCo 报告生成、`quick` 在 Harness 临时状态写入时均出现
`No space left on device`；环境检查 Data 卷使用率 100%，需要恢复磁盘空间后刷新完整门禁证据。
`mvn -q -pl im-test/im-architecture-test -Dtest=ModuleBoundaryTest test` 聚焦重跑通过（12 项），
其中新增 im-raft 契约禁止依赖 Broker 或暴露 JRaft 类型的检查；不替代完整 architecture 门禁。

T13 已完成 `BrokerCluster` 聚合本轮结构收口：operation 开始、旧 epoch fence 与 topology commit 已形成领域闭环，
operation 意图与实际拓扑 diff 由聚合校验，Raft 输入已迁至 `model/cqrs/command`，状态机只翻译具名
聚合行为。`domain.cluster` 已删除 `model/change/result/repository/service` 技术分类子目录；聚合决策与值对象、
领域服务、Repository 直接归属该聚合边界。SDK 投影使用 MapStruct 的 `BrokerClusterDomainTransformer`，其他
Transformer 也按 domain/interfaces/infrastructure 拥有方归位。此前已完成 routing
authority/connection 分包、领域服务命名与同 revision 快照摘要；重复的 application/domain
`FencingOperation`、旧 operation/phase 模型、假可选 membership 端口和 fail-open 默认实现已删除。
Raft 实现已迁入 `adapter/cluster/raft`。activate 用例已改为通过
`BrokerCluster.activate` 生成领域 operation，AppService 不再为该用例遍历 SDK Broker 或拼装
完整目标拓扑；deactivate、drain、故障摘流和 membership 管理入口也已改为调用 `BrokerCluster` 具名行为。
集群共识入口已统一为 `adapter/cluster/raft/BrokerClusterAdapter`，不再将 Raft 日志提交和成员变更
伪装为本地 Repository 操作。兼容共识结果与 Lease 激活端口仍携带 SDK 投影，尚未完成 interfaces
边界收口；旧的内部 AppService 测试也需要迁移到领域和 Repository 测试。当前 architecture 验证在 Broker 既有编译错误处阻断，首项为
`DiagnosticConfiguration` 的 public 类名与文件名不一致，随后为未生成的 Lombok getter 等错误。公共领域
类型的 Javadoc 已按聚合根、实体、值对象、领域服务、Repository 和领域决策明确角色，规范与
Harness Review 矩阵已同步；cluster/routing application service 已收敛，公开入口统一为 `*AppService`，
没有独立边界的内部编排类和过细 routing 子包已删除，公开用例统一使用 CQRS command。本轮 Broker 聚焦测试
和全模块 Maven 测试已通过；architecture 与 30 个 behavior 场景本轮通过，首次 `quick` 因验证期间工作树
变化失效，改动稳定后重跑已通过。Broker 管理操作和权限 scope 已改为保持原路径值的枚举，
共识提交状态独立为 `BrokerClusterConsensusStatus`，相关枚举逐项注明语义。领域身份、拓扑端点、revision、
epoch 和 operation 请求摘要已使用值对象；cluster 领域构造校验已统一使用 `AssertUtils.domainProp*`，普通
长参数创建已改为手写 Builder，并在 Builder 内区分未设置字段与合法基本类型值。认证后的请求 Principal 已从
错误的 `model/identity` 迁至 `support.security`，结构规范与 Broker README 已同步；Broker 领域、责任链聚焦
测试和全模块 Maven 测试通过。routing authority 已复用 `ClusterId`、`BrokerId`、`ClusterOperationId`、
`RoutingEpoch`，新增 `LeaseRequestNonce`，Lease/fence 的有效期与隔离期使用 `Duration`；执行 Guard、grant
fence 端口与领域结果不再退回裸 epoch/身份。connection 聚合的 deadline、tombstone 创建时间与 GC grace
已分别改用 `Instant`/`Duration`，epoch millis 只在 Registry adapter 边界转换。领域结果补齐字段语义和
状态组合校验。本轮 `mvn -q -pl im-broker/im-broker-server -am clean test` 及 Architecture Test 均通过。
`BrokerCluster` 已删除仅转发长参数的 `restore(...)` 工厂，聚合恢复统一使用可校验必填字段的手写 Builder；
Builder 直接创建私有空聚合并在 `build()` 内完成必填、capacity、Broker、operation 与 fence 校验，不再保留
仅供 Builder 转发参数的长构造器；
`id/revision/epoch/algorithm/brokers` 纯访问器由 Lombok fluent Getter 生成，具有只读转换语义的
`operations/currentFence` 仍保留显式方法；`ConnectionRoute` 的纯字段访问器采用同一规则，委托与派生领域
语义继续显式表达。Builder 缺失必填 capacity 与非法 `0` 均有领域测试覆盖。相关聚焦测试、Broker 全模块
测试与 Architecture Test 通过。
共识 Command 已按顶层 model 的 type-first 规则并入 `model/cqrs/command`，consensus port 只保留接口、结果与状态；
领域包已收平为 `domain/cluster`、`domain/authority`、`domain/connection` 三个一级能力，不再保留
`domain/routing` 或 `model/service/repository` 技术分类嵌套；Architecture Test 已同步禁止领域能力新增深层包。
`BrokerClusterAppService` 已删除通用 `execute(...)` 和 action switch，直接公开 activate、deactivate、
drain、force-deactivate 及五类 membership 具名用例；管理输入同步拆为九个具名 Command，共享接口只声明
operationId、brokerId、expectedEpoch 与 dryRun，force 和 membership 参数分别只存在于对应 Command。
`BrokerManagementAction` 及 action 错配分支已删除，操作类型由 Java 类型表达；每个方法独立完成 epoch、目标状态、
参数与提交流程校验。HTTP 写入口按路由状态、Raft 成员变更、强制处置拆成三个 Controller，每条固定 URL 显式
调用对应应用用例，不再通过动态 `/{action}` 入口运行时分派。
必填 Command 边界值测试、管理 HTTP 聚焦测试、Broker 全模块测试及 Architecture Test 均通过。
需要超时、过期或预算判断的组件使用 JDK `Clock` 作为可控墙上时间源，不再使用语义模糊的
`LongSupplier clock`。租约 elapsed-time 判断继续保留明确命名的单调时钟。相关时间边界聚焦测试、Broker
全模块测试与 Architecture Test 通过。
其余 Gateway 外部标识仍需按不变量、协议与序列化边界单独评估，不机械装箱。
`clean` 已通过；`full` 执行了 98 个测试且
零失败/零错误，但在 Broker 重启健康等待处失败。随后修复 routing-ha 中陈旧测试类映射，并将低于实测约
100 秒启动时间的 90 秒重启预算改为可配置的 180 秒，聚焦测试和 Harness 自测通过；真实重跑被上述
Docker daemon 阻塞，T13 最终 clean/full 尚未收口。

Docker 恢复后先清理 `im-chat-routing-ha-22968`，再运行 `./scripts/verify.sh routing-ha`、`clean` 和 `full`。
T10 的生产候选 3/5 voter 容量环境和 capacity driver 仍是独立外部阻塞。
