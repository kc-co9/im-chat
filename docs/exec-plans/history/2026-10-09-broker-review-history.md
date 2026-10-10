# Broker 权威路由与共识控制面实施计划

> 本计划把已批准的 Broker 权威集群状态设计转换为可验证实现。执行时保持 WIP=1，完成步骤立即勾选；除非用户明确要求，不创建提交。

## Sprint Contract

- 目标：用成熟 Raft 库提交唯一的 `BrokerClusterState`，让 SDK 直接选择 `RouteBroker`，以 Lease/fence 保证 epoch 切换期间新权威副作用的 admission 不重叠，并用分片快照、RouteLookup 和幂等投递完成 Gateway/Broker 切换闭环。
- 范围：Broker SDK/Server、WS Gateway SDK/Server、`im-monitor` 到 `im-ops` 的一次性重命名与管理写入口、内部身份授权、Compose/seed/config、Architecture/README、确定性单测、行为测试、E2E、3 Broker + 2 Gateway `routing-ha` 和生产候选容量基线。
- 不做事项：不自研 Raft；不做旧协议、旧模块名、旧快照格式或混合版本兼容；不允许运行中修改选择算法；不实现动态权重、跨地域共识、消息 exactly-once 或以广播 Frame 兜底；不把 Gossip 观察当作权威集群状态。
- 验收标准：设计中的协议、状态不变量、错误顺序、Lease/quarantine、Gateway 分片快照、DRAINING、RouteLookup、稳定幂等标识和管理操作全部有确定性测试；`routing-ha` 25 类场景通过；同 revision E2E 通过；生产候选拓扑形成可复查容量数据和各模块推荐机器配置；最终 `clean`、`full`、独立 quality Review 通过。
- 风险链路：Raft 库与 JDK/Spring 生命周期不兼容；多数派不可用导致路由写和下行短暂停止；错误 fence/quarantine 可能造成双 admission；Gateway 快照乱序可能复活旧路由；Lookup fanout 可能放大故障；模块改名可能遗漏 seed/config/deploy；多实例门禁和容量验证耗时较长。
- 依赖输入与所需权限：JDK 21、Maven 3.8.4、Node 20.19.5、Docker/Compose、可用本机端口和持久卷；本地/CI 状态可按 allowlist 重建。首次发布只接受同 revision 全组件，不需要生产数据迁移或旧版本回滚通道。

### Broker 接口层收敛增量

- [x] 删除 support/interceptor 空接口，具体链改名 BrokerRequestInterceptorChain 并直接实现
  BoltRequestHandler；同步配置、测试夹具、启动断言及文档，support 白名单取消已无职责的目录。

- [x] 删除生产 BrokerAdmissionContractValidator、BrokerAdmissionStage 和全部 stage()；
  配置列表直接决定节点顺序，补步骤注释。测试夹具不再补空阶段节点，装配测试验证实际
  Handler 的 operation 覆盖和 capability 匹配；责任链、身份、Lease 和启动聚焦 clean test 通过。

- [x] RouteBrokerExecutionInterceptor 删除 wallClock 与外部 Clock 参数，期限比较直接使用 Instant.now()；
  同步 BrokerRequestConfiguration、BrokerRequestChainTestSupport、RouteBrokerExecutionInterceptorTest。
  路由准入、责任链、连接 Handler、Frame Handler 及 ImBrokerApplicationTest 聚焦 clean test 通过。

- [x] 将 SDK 请求准入枚举统一命名为 `BrokerAdmissionType`，同步 operation 字段/访问方法、
  服务端准入节点、配置和契约测试；各枚举项补充适用操作与校验边界注释。
- [x] `GatewayRouteAuthorizationInterceptor` 删除外部 `Clock` 注入，改用 `Instant.now()`
  判断 Gateway 注册租约；同步配置及测试构造，保持原 TTL 比较语义。

- [x] 按已批准装配调整 `support/interceptor`、`infrastructure/interceptor`、`BrokerRequestConfiguration`、
  `AbstractBrokerRpcHandler`、全部具体 Handler 及其配置和测试：删除 Terminal 与 Handler 持链，
  配置按 operation 注册完整链，业务节点位于链尾，Lease 环绕真实业务调用。
- [x] 同步 Broker Architecture/README、Harness 与 PROGRESS；运行 Handler/责任链/启动聚焦 clean test、
  `./scripts/verify.sh behavior`、`startup`、`e2e`、`quick`、`clean`、`full`，逐项记录实际失败归因。
  责任链/Handler/启动聚焦 clean test 通过，behavior 34 项通过，clean 通过，quick 的 Harness 脚本阶段通过；
  quick/full 的架构门禁仍被 `BrokerRouteLookupParams` 顶层 model、`BrokerGossipScheduler` 配置依赖
  两项既有违规阻断。startup 被 im-message-server 测试夹具的旧 `BrokerClusterState` 构造器阻断；
  e2e 被测试配置缺失 bootstrap `membershipEndpoint` 阻断；full 在 BrokerClusterAppServiceChangeTest
  的三项既有异常断言错误处终止。上述失败均未归因于本责任链改造。

- [x] `BrokerRequestInterceptorChain` 契约迁到 `support/interceptor`，只暴露 Handler 所需的请求入口与回调；
  阶段、调用对象、拦截器 SPI、执行链、具体拦截器和启动校验迁到 `infrastructure/interceptor`。
  精确范围为 Broker Server 相关生产源码、配置和测试，不改 SDK profile、认证顺序或错误语义。
- [x] 同步 Broker Architecture/README、Harness 规则及分层测试；运行 Broker 责任链/启动定向测试、
  architecture、behavior、quick、clean/full，记录现有独立门禁失败，T13 不提前收口。
  Broker 聚焦 clean test、34 项 behavior、support 契约与应用层分层规则、clean/diff 通过；
  quick 仍有顶层 model 分类及 BrokerGossipScheduler 配置依赖两项既有架构失败，startup 被
  im-message-server 旧 BrokerClusterState 测试构造器阻断，e2e 缺 bootstrap membershipEndpoint。
  full 的非 clean Broker 测试遇旧 target 编译产物；reactor clean test 先被 im-cache 容量断言阻断，
  Broker Server 单模块 clean test 消除旧产物后仅余 BrokerClusterAppServiceChangeTest 三项既有错误。
  最终 Maven verify 未通过，T13 保持 active。
- [x] 按实际职责重命名八个基础设施 Bolt 拦截器/链实现：调用方身份、Gateway 注册与授权、
  Broker peer 准入、Broker 路由调用方、服务调用方角色、RouteBroker 执行及有序责任链。
  保留 SDK access profile、拦截顺序和拒绝逻辑；配置、测试夹具与当前文档引用同步。
  Broker 责任链及启动定向 clean test 通过。routing-ha RH-08/09/10 证据选择器同步新测试名；
  RH-12 更新已移动的 FrameDispatchAppServiceTest，RH-20 按当前“单轮查询当前拓扑所有候选”
  行为更新测试与断言，不再声称历史 retention。runner 移除不存在的候选测试名；
  RoutingHaScenarioManifestTest 与 routing-ha Harness 自测通过。仓库门禁结果按本轮验证记录。
  本轮 quick 仍在顶层 model 分类和 BrokerGossipScheduler 配置依赖两项既有架构规则处失败；
  clean/diff 通过。full 已越过 RH 清单校验，继续执行后在 Broker 全模块
  BrokerClusterAppServiceChangeTest 的三项既有异常断言错误处终止，未完成最终 Maven verify。
  T13 继续 active。
- [x] 将 `support/interceptor` 中 Handler 直接依赖的链契约（阶段、调用上下文、拦截器与终点）
  迁至 `interfaces/handler/interceptor`；链执行实现及七个具体拦截器迁至
  `infrastructure/bolt/interceptor`，启动契约校验迁至 `infrastructure/bolt`。
  范围含 Broker Handler、`BrokerRequestConfiguration` 和相关测试，保持准入顺序与拒绝语义。
- [x] 运行 Broker 拦截链/身份/启动聚焦 clean test、架构定向测试、`behavior`，同步 Broker
  Architecture、Harness、README 和 PROGRESS；再执行 `quick`、`clean`、`full`，既有失败按实际归因。
  聚焦 clean test、34 项 behavior、clean/diff 通过；定向架构测试中 support 白名单和应用依赖
  规则通过，接口规则仍被 BrokerGossipScheduler 配置依赖阻断。quick 只剩顶层 model 分类与
  Gossip 调度配置两项既有违规；startup 被 im-message-server 的旧 BrokerClusterState 测试构造器阻断；
  e2e 在测试配置缺少 bootstrap membershipEndpoint 时未进入请求链；full 仍在 routing-ha
  清单引用已不存在的 FrameDeliveryAppServiceTest 方法处终止，未进入最终 Maven verify。T13 不收口。
- [x] `support/security` 保持原包归属，将 BrokerWorkloadPrincipal 命名为
  `BrokerCallerPrincipal`、SDK 枚举改为 `BrokerCallerRole`；删除无行为的
  BrokerRequestPrincipal 与仅包装注册请求的 GatewayRoutePrincipal。
  Gateway 注册准入与 Handler 继续校验角色，Registry 保留 Gateway ID 身份绑定；
  Broker SDK/Server 安全聚焦 clean test 与 34 项 realtime behavior 通过。
- [x] 统一 `BoltRequestHandler` 的单一 `handle(context, payload)` 入口，Broker/Gateway 删除
  payload-only 重载；测试显式传上下文或 null 并验证无身份拒绝。插件、Broker、Gateway
  跨模块定向 clean test 通过；后续全仓门禁按本轮结果记录。
  realtime behavior 34 项及 clean/diff 通过；quick 仍停在三项既有架构违规；e2e 在
  RealtimeMessagingE2ETest 的 Spring BootstrapWith 注解解析阶段失败，未执行请求链；full
  仍被 routing-ha 场景清单中旧 FrameDeliveryAppServiceTest 方法引用阻断，未进入最终 Maven verify。
  T13 保持 active。
- [x] 后续 Review 将同时承载启动注册、定时心跳和关闭注销的 `BrokerRegistrationLifecycle`
  从 `interfaces/scheduler` 迁至 `interfaces/lifecycle`；Spring 装配、启动任务断言、测试包及
  当前 Broker 文档同步。`BrokerRegistrationLifecycleTest`、`GatewayCleanupSchedulerTest`、
  `ImBrokerApplicationTest` 定向 clean test 通过，注册行为未调整。
  本轮 quick 仍被既有三项架构违规阻断，clean/diff 通过；full 再次停在
  RoutingHaScenarioManifestTest 的旧 FrameDeliveryAppServiceTest 方法引用，未进入最终 Maven verify。
  T13 保持 active。
- [x] `GatewayRouteLeaseCleanupScheduler` 更名为 `GatewayCleanupScheduler`，同步配置 Bean、
  启动任务断言、用例测试和当前文档；`RoutingLeaseScheduler` 保持独立，两个调度周期均未改变。
  `GatewayCleanupSchedulerTest`、`LocalGatewayCleanupAppServiceTest` 和 `ImBrokerApplicationTest`
  定向 clean test 通过。
- [x] 按本轮 Review 将只读集群入口改为 `BrokerClusterController`，本机过期 Gateway 清理用例
  改为 `LocalGatewayCleanupAppService`，纯定时成员恢复入口改为 `BrokerMembershipScheduler`；
  配置装配、调度任务断言和对应测试同步更名，不改变 URL、调度周期或清理顺序。
  Broker 定向 clean test（含启动装配）通过；全仓门禁结果见下方本轮验证记录。
  本轮 clean/diff 通过；定向三项架构测试中领域和应用规则通过，接口规则仍被
  BrokerGossipScheduler 依赖基础设施配置阻断。quick 仍为既有三项架构违规；full 在
  RoutingHaScenarioManifestTest 引用已不存在的 FrameDeliveryAppServiceTest 方法处终止，
  未进入最终 Maven verify。T13 保持 active。
- [x] 后续 Review 将单方法 `BrokerEmergencyWriteController` 合并到 `BrokerRoutingController`；
  `forceDeactivate` 方法独立标注 FORCE 权限，保留原 URL，普通路由写仍使用类级权限。
  `BrokerManagementAccessTest` 验证两种权限互不混用，Broker 启动聚焦测试通过。
  本轮 quick 在既有三项架构违规处失败，clean 通过；full 在 im-routing-ha 清单引用已不存在的
  FrameDeliveryAppServiceTest 测试方法处失败，未进入最终 Maven verify。T13 不收口。
- [x] 管理只读 Controller 改为 BrokerDiagnosticController，Raft 成员写入口按 Review 定为 BrokerMembershipController；仅调度的 Lifecycle 改名 Scheduler，路径、权限和周期保持不变。
- [x] 删除单生产子类的 AbstractPrincipalBrokerRpcHandler；GatewayRegisterHandler 直接从统一 RPC Handler 接收并检查强类型 principal，补错误身份测试。
- [x] operation 管理查询返回明确的 HTTP 响应对象，只含运营需要的身份、类型、阶段、时间、结果和恢复摘要；保留 URL，禁止直接序列化领域实体。
- [x] Gateway 过期清理由应用用例编排：Gossip 删除观察成功后才删除本机注册；失败保留目标供下一轮重试。入口只定时触发，补失败注入和重复清理测试，记录本机内存状态在进程崩溃时的剩余边界。
- [x] 同步当前 Broker 文档、计划与 Harness；依次验证 Broker 聚焦测试、startup、architecture、behavior、quick/clean/full（前序失败按规则归因），不改无关工作树状态。聚焦 clean test、behavior 34 项及 clean 已通过；startup 被 im-message-server 旧构造器调用挡住，architecture/quick 仍有三项既有违规，Broker 全模块 509 项有三项既有异常断言错误；full 在 im-routing-ha 的 FrameDeliveryAppServiceTest 既有断言失败处终止，未进入最终 Maven verify。上述门禁未通过，T13 继续 active。

### Broker 配置职责增量

- [x] 复查后删除 LocalRouteAppProperties：LocalRouteAppService 只接收 snapshotBatchTimeout，
  InMemoryBatchConnectionRegistry 和缓存装配直接读取 BrokerRoutingProperties 的批次/清理容量；
  测试使用测试侧源配置夹具，不增加生产兼容构造器。路由接收/清理、批次 Registry 与启动 clean test，
  定向架构测试、behavior 34 项、clean/diff 通过；全仓 quick 的三项既有违规仍待单独处理。
- [x] 盘点六个 Spring 配置类与七个不可变参数对象，确认部署身份、IO/Lease 时间窗口和容量上限保留可配置；派生时长只计算，不单独绑定；未使用字段与纯转发包装删除。
- [x] 将单一能力的应用参数对象归到所属 application 包、Gossip 调度参数归 interfaces/scheduler；
  原暂留的 LocalRouteAppProperties 已在复查后删除，不保留空 model/properties 包。
- [x] 移除未消费的 Registry brokerTtl/connectionTtl 与仅在配置层使用的 LocalLeaseActivationAppProperties；排空单字段包装收为明确的 Duration 构造参数，保持业务校验和配置默认行为；Lease pending 容量改为可调源配置，默认仍为 16。
- [x] 为保留的 Spring 配置字段补充用途、单位、调节/安全边界注释，Broker README 与 Harness 记录可调/派生/固定的边界；配置绑定、启动、Gossip/Registry、帧投递、Lease、排空及故障恢复聚焦测试通过。组合测试首次受 target 中 MapStruct 错误桩污染，重新 clean compile 并确认生成类字节码后通过。
- [x] `./scripts/verify.sh behavior` 通过 34 项，`./scripts/verify.sh clean` 与 `git diff --check` 通过；
  应用/接口/领域依赖与模块边界定向架构测试通过。`quick` 仍停在 support.interceptor、
  BrokerRouteLookupParams 顶层 cqrs 和应用外层依赖三条既有规则，未为本轮配置改动放宽门禁；
  前序失败后不运行 full，T13 保持 active。

### Cluster/Connection 领域包归类增量

- [x] 盘点 `im-broker-server/src/main/java/.../domain/{cluster,connection}` 与对应测试；按
  `model/repository/service` 职责分类，不创建没有领域服务的 connection/service 空包。
- [x] 将 cluster 模型、BrokerRegistry、BrokerFailureObservationService 分别迁到 model/repository/service；
  connection 模型与三个 Registry 接口分别迁到 model/repository；同步两包测试位置及 package 声明。
- [x] 机械更新全仓 Java import/限定引用，补跨子包显式 import，更新当前 Broker Architecture/README；
  `rg` 确认旧顶层类路径无 Java 引用，跨模块生产编译与 Broker 代表性领域/启动 clean test 通过。
- [x] 更新 `LayerBoundaryTest` 的 Broker 领域包显式白名单及仓储接口断言，新增
  cluster/model|repository|service 与 connection/model|repository，仅允许 repository 接口；
  四项领域架构定向测试通过，Harness 说明同步。
- [x] 依次运行 `mvn -q -pl im-broker/im-broker-server -am -DskipTests compile`、Broker 聚焦测试、
  `mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerDomainPackagesMustMatchCapabilityRoles+brokerDomainRepositoriesMustBeInterfaces+brokerDomainMustNotDependOnTechnicalLayers+brokerDomainMustNotContainConcreteAdapters' test`、
  `./scripts/verify.sh behavior`、`./scripts/verify.sh quick`、
  `./scripts/verify.sh clean`、`git diff --check`，记录既有全仓门禁失败归因；full 仅在前序门禁通过后运行。
  当前 Broker 与跨模块生产编译、两包 12 组领域 clean test、behavior 34 项、领域/模块架构定向测试通过；
  更新规则后的 quick 仅剩 support.interceptor、顶层 model、application 依赖三条既有架构失败。
  因前序门禁失败未执行 full；最终 clean/diff 单独检查通过。T13 保持 active。

### Interfaces/Infrastructure Review 增量

- [x] 本机 FileFenceWatermarkRepository 与同包测试从 `infrastructure/routing` 迁到
  `infrastructure/authority`，RoutingLeaseConfiguration import 和 Broker Server README、
  MODULE_STRUCTURE_GUIDE 当前目录说明同步；不改文件水位读写。仓储及 Broker 启动 clean test 通过。
- [x] `RegistryObservationAppService` 更名为 `RegistryGossipAppService`，对应配置类更名为
  `RegistryGossipConfiguration`；三个事件监听器字段和 Bean 方法同步命名，不改观察行为。
  `RegistryEventListenersTest`、`BrokerGossipSchedulerTest`、`ImBrokerApplicationTest` 的 clean test 通过，
  behavior 34 项及 clean/diff 通过。全量 startup 的非 clean reactor 在 Broker 启动测试加载
  RegistryGossipConfiguration 时出现 `NoClassDefFoundError: BrokerRegistry`；本轮不视为通过。
- [x] `BrokerLifecycleRecoveryTest` 先证明 drain、故障恢复和续租在同步读取已提交状态失败后不能进入下一轮；
  `BrokerDrainLifecycle`、`BrokerFailureRecoveryLifecycle`、`RoutingLeaseLifecycle` 将同步准备纳入运行标志复位边界。
  成员恢复原有复位路径通过同一测试核对。
- [x] `BrokerStateStoreTest` 固定 Gateway 快照中保留/移除连接的观察结果；
  `BrokerStateStore.updateConnectionSnapshot` 使用 Set 查询活跃用户并以显式循环标记 tombstone。
- [x] 将 `BrokerRegistrationLifecycle` 与 `GatewayRouteLeaseCleanupScheduler` 移至 `interfaces/scheduler`，
  清理入口的副作用使用显式循环；启动任务清单及测试包同步。`BrokerRoutingController`
  移除未使用的 request 入参，失败恢复类型改为枚举比较，FrameProcessHandler 删除重复空判断。
- [x] 将 BrokerGossipScheduler 的基础设施配置依赖收敛为 `BrokerGossipProperties`，由
  `RegistryObservationConfiguration` 装配三个定时入口；Gossip 网络副作用改为显式有界循环。
  `LayerBoundaryTest#brokerInterfacesMustNotDependOnConcreteInfrastructure` 定向通过。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=ImBrokerApplicationTest,BrokerLifecycleRecoveryTest,RoutingLeaseLifecycleTest,BrokerStateStoreTest,GatewayRouteLeaseCleanupSchedulerTest,BrokerRegistrationLifecycleTest,BrokerManagementWriteControllerTest,FrameProcessHandlerTest -Dsurefire.failIfNoSpecifiedTests=false clean test` 通过；先前非 clean 测试被移动类的旧 target 增量产物污染。
- [ ] Gateway 移除后的 Gossip 事件失败重试尚无可恢复事实来源：同步 listener 吞异常，Registry 已删除时下一轮不会再枚举该 Gateway。需单独设计可恢复的移除记录/重放边界，不能靠调整发布顺序解决。
- [x] `./scripts/verify.sh behavior` 通过 34 项，`./scripts/verify.sh clean` 和 `git diff --check` 通过；
  Broker 定向架构测试通过。`./scripts/verify.sh startup` 被 im-message-server 测试夹具的旧
  BrokerClusterState 构造调用挡住，Broker 自身 ImBrokerApplicationTest 已通过。
- [ ] 全量 quick 曾因 Broker 其他包的 support/model/application 规则失败；startup 上游调用方和
  Gateway 移除事件的可恢复重放仍待单独处理。按分层验证规则未将 full 视为通过，T13 保持 active。

### RouteLookup 单轮 Review 增量

- [x] 更新 `docs/design-docs/2026-09-19-broker-routing-consensus-design.md` D17/D18、设计索引和
  `PROGRESS.md`：用户确认单轮有界并发、旧 owner/DRAINING 优先、候选超 fanout 交由重试，
  总预算不变、删除定向预算；增量独立 Review 待完成，T13 保持 active。
- [x] `FrameDispatchAppServiceLookupTest`、`CandidateTest` 先覆盖本地命中零 peer、单轮并发命中/去重、
  `maxFanout` 优先级与超限行为、总预算和负缓存；执行聚焦测试确认旧两级实现不满足新行为。
- [x] `FrameDispatchAppService` 合并候选并单轮 RPC；删除 `FrameDeliveryAppProperties`、
  `BrokerRoutingProperties`、`FrameDeliveryConfiguration`、`application.yml` 的定向预算及同包测试形状；
  同步 Broker Architecture/README、根 Reliability 和 Harness 约束。
- [x] 原单轮有界版本先执行 Broker 投递/Lookup/Handler 聚焦测试、`./scripts/verify.sh behavior`、
  领域/模块边界、`./scripts/verify.sh clean` 和 `git diff --check`；随后用户明确调整为全部并发，
  最终证据以下面的新步骤为准。`quick` 在 load-test runner 锁恢复脚本失败；`full` 因用户转入新方案讨论而取消。
- [x] 用户后续明确取消历史优先和固定 fanout：`FrameDispatchAppService` 查询当前全部其他
  ACTIVE/DRAINING Broker，不排序、不截断；`RouteLookupConfiguration` 使用专用虚拟线程执行器；
  移除候选历史缓存、观察任务和失效配置，更新 Lookup/配置测试及 D17/D18、Broker 文档与 Harness。
- [x] 验证全部当前 Broker 查询、部分超时返回、虚拟线程执行：
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=FrameDispatchAppServiceLookupTest,FrameDispatchAppServiceTest,FrameProcessHandlerTest,RouteLookupConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  通过；`./scripts/verify.sh behavior` 通过 33 项；Broker 领域/模块边界测试、`ImBrokerApplicationTest`、
  `./scripts/verify.sh clean` 和 `git diff --check` 通过。最新方案未刷新 quick/full/e2e，
  独立 Review 待完成，T13 不收口。
- [x] 用户确认 IO 自主管理期限：`FrameDispatchCommand`、`FrameDispatchAppService` 和
  `BrokerClusterAdapter.lookupRoutes` 不再传递外部请求 deadline 或剩余预算；Adapter 使用
  `route-lookup-timeout` 配置每次 IO 超时并生成 peer 请求 deadline。Broker SDK/入口保留整体请求校验；
  Adapter、投递和 Handler 聚焦测试通过，文档与 Harness 同步。
- [ ] 刷新本次期限职责变更的行为、架构、clean/diff 和全量门禁实际证据；独立 Review 待完成。
- [x] `FrameDispatchAppService.dispatch` 按用户确认的三段链式流程整理：`thenApply` 选择当前候选、
  `thenCompose` 并发查询并通过 `thenCombine` 汇总、`thenApply` 过滤并按 Gateway 合并；
  Adapter 同步提交失败返回失败 Stage，测试 Stub 与其一致。投递、Lookup、Handler 聚焦测试通过。
- [ ] 刷新链式编排后的稳定工作树行为、clean/quick/full 证据；并发工作树修改导致一次行为和
  clean 门禁指纹失效，独立 Review 与 T13 仍待完成。
- [x] `BrokerClusterAdapter.lookupRoutes` 的四个入参收为 `BrokerRouteLookupParams`；
  应用构造参数，Adapter 负责转成 SDK peer 载荷。投递、Lookup、Handler 与 Adapter 聚焦测试通过；
  一次整组测试中两个异步用例短暂失败，单独和整组复跑均通过，保留时序稳定性观察。
- [x] 用户确认删除无消费的 lookupId，并直接传当前 BrokerNode：应用参数、Adapter、SDK peer
  载荷及 responder 校验同步调整，移除 RouteLookupTargetDTO。Lookup、投递、Adapter、Handler
  和数据面身份聚焦测试通过；异步有效路由夹具改用不会被真实工作线程时钟误判过期的期限。
- [x] `./scripts/verify.sh behavior` 通过 33 项，`./scripts/verify.sh clean` 与
  `git diff --check` 通过；Broker 生产和测试代码无 lookupId/RouteLookupTargetDTO 引用。
  全量架构门禁此前因其他模块的 GroupId 缺失及分层违规失败，本轮不将其标为通过；T13 保持 active。
- [x] FrameDispatchAppService 将 Gateway 注册复核放入查询链最后的 `thenAccept`，
  `join` 后依据最终 userGateways 更新负缓存；端点失配导致重复查询的回归测试先失败、
  修复后投递/Lookup/Handler 聚焦测试通过，Broker Architecture/README 与 Harness 同步。
- [x] 用户确认删除 RouteLookup 响应位置的 eventDeadline：SDK DTO、应用 RouteLocation、
  responder、Adapter 映射及过期过滤同步移除；peer 请求 deadline 与 responder/本机 Gateway
  新鲜度判断保持。投递/Lookup/Adapter/Handler 聚焦测试通过，响应文档同步。
- [x] FrameDispatchAppService 改为对所有已选 Gateway 并发调用 BrokerClusterAdapter.dispatchFrame：
  配置层管理专用虚拟线程执行器，应用按 Lookup 的 completedFuture/thenCompose/thenApply 链式格式
  汇总写入结果，单目标异常转为空结果。双 Gateway 并发和局部失败测试、投递/Lookup/Handler
  聚焦测试通过，Broker Architecture/README 同步。
- [x] 根据用户 Review，将 supplyAsync 从应用服务移入 BrokerClusterAdapter.dispatchFrame；
  Adapter 持有配置层注入的 Gateway 虚拟线程执行器并返回 CompletionStage，应用只做异常隔离与汇总。
  Adapter/投递/Lookup/Handler 聚焦测试通过，边界文档同步。
- [x] 应用汇总直接调用 CompletionStage.exceptionally，无需 toCompletableFuture；
  FrameDispatchAppServiceTest、FrameDispatchAppServiceLookupTest、FrameProcessHandlerTest、
  BrokerClusterAdapterTest、FrameDeliveryConfigurationTest 聚焦测试通过，behavior 33 项及 clean/diff 通过。
- [ ] 本轮 quick 在 im-architecture-test 因其他模块 GroupId 缺失和既有 4 处分层断言失败；
  按验证层级规则不以 full 覆盖该失败，T13 和独立 Review 保持待完成。

### Epoch 推进 Review 增量
- [x] FrameDispatchAppService 删除重复的请求过期提前判断，入口 RouteRequestExecutionInterceptor
  负责入场校验；保留 FrameDispatchCommand.deadlineEpochMillis 供在途查询计算剩余预算，
  BrokerClusterAdapter.lookupRoutes 继续透传原请求期限到 peer，不改变 SDK 字段。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=FrameDispatchAppServiceTest,FrameDispatchAppServiceLookupTest,FrameDispatchAppServiceCandidateTest,FrameProcessHandlerTest,RouteLookupHandlerTest,IdentityAccessInterceptorsTest,BrokerBoltHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过（39 项）；
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=RouteRequestExecutionInterceptorTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过（3 项），确认入场期限检查。
- [x] `./scripts/verify.sh clean` 与 `git diff --check` 通过；T13 保持 active Review。
- [x] ConnectionRegistry.applySnapshot 更名 reconcileRoutes，契约注释说明完整集合、分片汇总、
  高 revision 保留及同 revision 内容校验；包装/内存实现、注册表测试和当前架构图同步。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=GatewayRouteTableTest,InMemoryConnectionRegistryTest,ConnectionRouteRevisionTest,InMemoryBatchConnectionRegistryTest,LocalRouteAppServiceReceiveTest,LocalRouteAppServiceCleanupTest,LocalRouteAppServiceIdempotencyTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false clean test` 通过（53 项）。
  安装最新产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerDomainMustNotDependOnTechnicalLayers+brokerDomainMustNotContainConcreteAdapters,ModuleBoundaryTest' clean test` 通过；生产和测试无旧方法引用。
- [x] `./scripts/verify.sh clean` 与 `git diff --check` 通过；T13 保持 active Review。
- [x] InMemoryBatchConnectionRegistry 删除 createdAt 剩余 TTL 计算和逐分片 PUT，
  未收齐新批次只写入 JetCache 一次，后续分片更新同一本地对象；GatewayRouteBatch 移除冗余 createdAt。
  LocalRouteAppServiceReceiveTest 验证重复分片不延长 TTL，InMemoryBatchConnectionRegistryTest 以真实 JetCache
  验证对象引用与后续更新，接收/清理聚焦测试通过，Architecture/README 同步。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=InMemoryBatchConnectionRegistryTest,LocalRouteAppServiceReceiveTest,LocalRouteAppServiceCleanupTest,LocalRouteAppServiceIdempotencyTest,GatewayRouteBatchTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false clean test` 通过（29 项）。
  安装最新产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerDomainMustNotDependOnTechnicalLayers+brokerDomainMustNotContainConcreteAdapters,ModuleBoundaryTest' clean test` 和 `./scripts/verify.sh behavior` 通过。
- [x] `./scripts/verify.sh clean` 与 `git diff --check` 通过；T13 保持 active Review，未刷新 quick/full/e2e。
- [x] 删除 LocalLeaseAppService.prepare 的满额拒绝及不再使用的 properties 构造依赖，
  JetCache 容量满时可淘汰旧 nonce；测试改为新申请继续、旧申请失败、新申请安装一次成功。
  Architecture/README/Harness 同步策略和重试影响。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=LocalLeaseAppServiceTest,LocalRoutingLeaseServiceTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过（11 项）。
- [x] `mvn -q -pl im-broker/im-broker-server -am test`：496 项中 493 项通过，
  三个错误仍为既有 BrokerClusterAppServiceChangeTest 提交/quorum 异常传播预期，
  无新增 Lease 失败；全模块状态仍失败。
- [x] 安装最新产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerDomainMustNotDependOnTechnicalLayers+brokerDomainMustNotContainConcreteAdapters,ModuleBoundaryTest' clean test` 通过。
  T13 保持 active Review；未刷新 quick/full/e2e。
- [x] 按最新 Review 删除 LocalLeaseAppService.accept 对 pendingTtl 的重复单调过期判断，
  缓存 TTL 负责 pending 存在性；领域服务仍按准备起点和 Lease 自身期限校验并锁内安装。
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=LocalLeaseAppServiceTest,LocalRoutingLeaseServiceTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过（11 项）。
- [x] `mvn -q -pl im-broker/im-broker-server -am test`：496 项中 493 项通过，
  三个错误仍为既有 BrokerClusterAppServiceChangeTest 提交/quorum 异常传播预期，
  无新增 Lease 失败；全模块状态仍失败。
- [x] 安装最新产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerDomainMustNotDependOnTechnicalLayers+brokerDomainMustNotContainConcreteAdapters,ModuleBoundaryTest' clean test` 通过。
  T13 保持 active Review；未刷新 quick/full/e2e。
- [x] LocalLeaseAppService pending nonce 改为注入 JetCache LOCAL Cache，配置层提供容量与 pendingTtl；
  accept 的单调期限检查和一次性消费仍由应用服务执行，测试保持单调时间可控，文档/Harness 同步。
- [x] 按后续 Review 将缓存 TTL 改为 pendingTtl，prepare 仅查询框架有效条目容量，不再逐项回收；
  accept 仅对目标 nonce 校验单调期限并消费，保留安全兜底。新增缓存仍命中但单调已到期的拒绝测试，
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=LocalLeaseAppServiceTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过（7 项），文档/Harness 同步。
- [x] `mvn -q -pl im-broker/im-broker-server -am test`：496 项、0 assertion failures、3 errors，
  493 项通过，错误仍是既有 BrokerClusterAppServiceChangeTest 提交/quorum 异常传播预期；全模块未通过，
  无本次 Lease 缓存改造新增失败。
- [x] 安装最新产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerDomainMustNotDependOnTechnicalLayers+brokerDomainMustNotContainConcreteAdapters,ModuleBoundaryTest' clean test` 通过，diff 通过。
  未刷新 quick/full/e2e，T13 保持 active Review。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=LocalLeaseAppServiceTest,ImBrokerApplicationTest,LocalRouteAppServiceReceiveTest,InMemoryBatchConnectionRegistryTest,FrameDispatchAppServiceLookupTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过。
- [ ] 完成全部 Broker 缓存迁移后的全模块、领域/模块边界、clean/diff；T13 保持 active Review。
- [x] 扫描 Broker 本机暂存状态：候选历史、负缓存、未完成分片、清理检查点改为 JetCache LOCAL；
  分片保留首次绝对到期且容量按有效批次统计，清理检查点有界长 TTL；聚合/Registry 事实不迁移。
  LocalLeaseAppService nonce 因单调过期及一次性消费语义保留原实现，Architecture/README/Harness 同步。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=InMemoryBatchConnectionRegistryTest,LocalRouteAppServiceReceiveTest,LocalRouteAppServiceCleanupTest,LocalRouteAppServiceIdempotencyTest,FrameDispatchAppServiceTest,FrameDispatchAppServiceLookupTest,FrameDispatchAppServiceCandidateTest,FrameProcessHandlerTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过（39 项）。
- [x] 串行 `clean test` 聚焦重跑通过；此前与并行 `install` 竞争产生的 NoClassDefFoundError 已排除。
  `mvn -q -pl im-broker/im-broker-server -am test`：495 项、0 assertion failures、3 errors；
  492 项通过，三个错误仍为既有 BrokerClusterAppServiceChangeTest 提交/quorum 异常传播预期，不弱化测试。
- [x] 安装最新产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerDomainMustNotDependOnTechnicalLayers+brokerDomainMustNotContainConcreteAdapters,ModuleBoundaryTest' clean test` 通过，diff 通过。
  T13 保持 active Review，未刷新 quick/full/e2e。
- [x] 候选历史直接注入 JetCache Cache，im-cache 工厂支持 local(name, expire, capacity) 与有效值快照，
  不增加 LocalCache 类型；应用删除手写过期/淘汰/锁，observe 只 put。配置、受控测试及文档/Harness 同步。
- [x] `mvn -q -pl im-plugin/im-cache -am test` 通过，框架实际 TTL/容量/快照与工厂参数有回归。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=JetCacheFactoryTest,JetCacheLocalValuesTest,FrameDispatchAppServiceTest,FrameDispatchAppServiceLookupTest,FrameDispatchAppServiceCandidateTest,FrameProcessHandlerTest,RouteLookupHandlerTest,BrokerBoltHandlerTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false clean test` 通过（缓存 5 项、Broker 35 项）。
- [x] 安装最新产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerDomainMustNotDependOnTechnicalLayers+brokerDomainMustNotContainConcreteAdapters,ModuleBoundaryTest' clean test` 通过，diff 通过。
  T13 保持 active Review，未刷新 quick/full/e2e。
- [x] GatewayRegistry 注册接收 GatewayRegistration，其余操作接收 GatewayId，内存索引同步，
  不保留基础类型重载；生产调用方、测试和 Architecture/README 同步。
- [x] `mvn -q -pl im-broker/im-broker-server -am '-Dtest=GatewayRegistrationTest,InMemoryGatewayRegistryTest,GatewayRouteLeaseCleanupSchedulerTest,RegistryEventListenersTest,RegistryTransformerTest,FrameDispatchAppServiceTest,FrameDispatchAppServiceLookupTest,FrameDispatchAppServiceCandidateTest,FrameProcessHandlerTest,RouteLookupHandlerTest,BrokerBoltHandlerTest,BrokerStateStoreTest,IdentityAccessInterceptorsTest,ConnectionCloseHandlerTest,BrokerDiagnosticAppServiceTest,ImBrokerApplicationTest,BrokerClusterAdapterTest#gatewayWritePreservesPayloadAndPerTargetSuccessFailureMetrics' -Dsurefire.failIfNoSpecifiedTests=false clean test` 通过（58 项），diff 通过。
- [x] 安装最新产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerDomainMustNotDependOnTechnicalLayers+brokerDomainMustNotContainConcreteAdapters,ModuleBoundaryTest' clean test` 通过。
  T13 保持 active Review，未刷新 quick/full/e2e。
- [x] GatewayRegistration 字段使用领域身份/主机/端口/认证主体，GatewayPrincipalId 与 GatewayId 区分；
  SDK/Gossip 平铺字段和 workloadIdentity 通过边界展开，Gossip 改显式 DTO，生产/测试映射同步。
- [x] dispatch 在不新增方法下改查询门槛为提前返回，去除 canQuery 嵌套，使用 directedQuery 和
  lookupDeadlineMillis 明确策略与单位；保留必要预算/失败隔离/投递前复核，文档/Harness 同步。
- [x] directedCandidatesBeyondFanoutRemainEligibleForFallback 修改前失败，修复后通过；
  queriedBrokerIds 只记录实际请求节点，17 项投递/Lookup/候选回归通过。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=FrameDispatchAppServiceTest,FrameDispatchAppServiceLookupTest,FrameDispatchAppServiceCandidateTest,FrameProcessHandlerTest,RouteLookupHandlerTest,BrokerBoltHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过（34 项）。
  安装最新产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerDomainMustNotDependOnTechnicalLayers+brokerDomainMustNotContainConcreteAdapters,ModuleBoundaryTest' clean test` 通过，diff 通过。
  未刷新 quick/full/e2e，T13 保持 active Review。
- [x] ObservedState 提取为 model/cqrs/dto/BrokerClusterObservationDTO，字段 clusterState/observedAt，
  候选历史引用和 Architecture 同步；沿用 DTO 后缀规则，不引入领域层 SDK 依赖。
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=FrameDispatchAppServiceTest,FrameDispatchAppServiceLookupTest,FrameDispatchAppServiceCandidateTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过（16 项），diff 通过。本轮未刷新 quick/full/e2e。
- [x] 删除 RouteLookupCommand，Adapter 直接接受领域身份/代次/期限和 lookupId，RPC 固定一跳；
  两轮共用身份，Stub/测试同步，不新增请求对象。更新期限统一 updateExpiresAt，清理 oldEventDeadline，文档同步。
- [x] `mvn -q -pl im-broker/im-broker-server -am '-Dtest=FrameDispatchAppServiceTest,FrameDispatchAppServiceLookupTest,FrameDispatchAppServiceCandidateTest,GatewayRouteBatchTest,InMemoryConnectionRegistryTest,InMemoryBatchConnectionRegistryTest,LocalRouteAppServiceReceiveTest,LocalRouteAppServiceCleanupTest,LocalRouteAppServiceIdempotencyTest,FrameProcessHandlerTest,RouteLookupHandlerTest,BrokerBoltHandlerTest,BrokerClusterAdapterTest#routeLookupUsesConfiguredExecutorAndPreservesReadOnlyRequest' -Dsurefire.failIfNoSpecifiedTests=false clean test` 通过（74 项）。
  生产/测试/脚本无 RouteLookupCommand 引用，readiness/diff 通过。
- [x] 安装最新产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerDomainMustNotDependOnTechnicalLayers+brokerDomainMustNotContainConcreteAdapters,ModuleBoundaryTest' clean test` 通过。
  未刷新 quick/full/e2e，T13 保持 active Review。
- [x] 升级 ConnectionRegistration 为唯一版本化路由实体，删除 ConnectionRoute 与注册投影；
  Registry 合并无版本入口和查询，注册接收实体、注销接收版本证据，删除 sync/findRouteStates。
  聚合为唯一实体来源，存储只保留身份索引；注册时间/诊断映射、Handler/事件/包装/测试同步。
- [x] Gossip CONNECTION 删除不再无版本改写本机注册，Gateway 整体移除仍清理；新增同源查询/时间保留和
  remoteConnectionRemovalCannotEraseNewerLocalRegistration 回归，Architecture/README/Harness 同步。
- [x] 模型合并后 `mvn -q -pl im-broker/im-broker-server -am test`：492 项、0 assertion failures、3 errors。
  489 项通过，三个错误仍为此前已归因的 BrokerClusterAppServiceChangeTest 提交失败/quorum 失败传播预期，
  无本轮新增失败；全模块状态仍失败，不以局部通过覆盖。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=GatewayRouteTableTest,InMemoryConnectionRegistryTest,ConnectionRouteRevisionTest,InMemoryBatchConnectionRegistryTest,LocalRouteAppServiceReceiveTest,LocalRouteAppServiceCleanupTest,LocalRouteAppServiceIdempotencyTest,BrokerDiagnosticAppTransformerTest,BrokerDiagnosticAppServiceTest,BrokerStateStoreTest,RegistryEventListenersTest,ConnectionRegisterHandlerTest,BrokerBoltHandlerTest,FrameDispatchAppServiceTest,FrameDispatchAppServiceLookupTest,FrameDispatchAppServiceCandidateTest,FrameProcessHandlerTest,RouteLookupHandlerTest,RegistryTransformerTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过（95 项）。
  安装最新产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerDomainMustNotDependOnTechnicalLayers+brokerDomainMustNotContainConcreteAdapters,ModuleBoundaryTest' clean test` 通过；diff 通过。
  未刷新 quick/full/e2e，T13 保持 active Review。
- [x] FrameDispatchAppService 将 lookup、定向/兜底候选、查询/合并及历史记录内联到 dispatch，
  两轮共用网络与合并代码，保留短历史锁；定时入口直接执行，Lookup/Candidate 测试改公开入口，文档/Harness 同步。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=FrameDispatchAppServiceTest,FrameDispatchAppServiceLookupTest,FrameDispatchAppServiceCandidateTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过（16 项）。
- [x] 验证内联后的 Handler/启动：
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=FrameDispatchAppServiceTest,FrameDispatchAppServiceLookupTest,FrameDispatchAppServiceCandidateTest,FrameProcessHandlerTest,RouteLookupHandlerTest,BrokerBoltHandlerTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false clean test` 通过；后续模型合并全模块再次覆盖 Handler 和启动，领域/模块边界通过。
- [x] ConnectionRegistry 参数由接口起域化，两个实现、注册/注销/Lookup/诊断/Gossip/定时入口同步，
  测试直接使用领域参数，无旧类型重载；文档/Harness 同步。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=InMemoryConnectionRegistryTest,ConnectionRouteRevisionTest,InMemoryBatchConnectionRegistryTest,LocalRouteAppServiceReceiveTest,LocalRouteAppServiceCleanupTest,LocalRouteAppServiceIdempotencyTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过（39 项）。
- [x] `mvn -q -pl im-broker/im-broker-server -am test`：490 项，0 assertion failures、3 errors；
  错误均为此前已记录的 BrokerClusterAppServiceChangeTest：activationCommitFailureDoesNotAuthorizeUncommittedEpoch
  及 progressReadFailureDistinguishesUnavailableFromLostLeadership 两种角色，预期与异常传播策略不一致。
  其余 487 项通过，全模块仍失败，不修改无关测试或弱化校验。
- [x] 安装最新产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerDomainMustNotDependOnTechnicalLayers,ModuleBoundaryTest' clean test` 通过；diff 通过。
  全模块既有错误归因保留，未刷新 quick/full/e2e，T13 保持 active Review。
- [x] FrameDispatchAppService 展开本地定位/远端复核，内联合并与缓存裁剪，保留共享候选查询/历史记录；
  GatewayRegistration.isFreshAt 承担活跃观察规则，技术缓存以领域身份索引，文档/Harness 同步。
- [x] 新增负缓存完成时计时、同步 peer 失败隔离两项回归，修改前分别失败和报错，修复后
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=FrameDispatchAppServiceTest,FrameDispatchAppServiceLookupTest,FrameDispatchAppServiceCandidateTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过（15 项）。
- [ ] 验证 Frame Handler/Lookup/真实启动：
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=FrameDispatchAppServiceTest,FrameDispatchAppServiceLookupTest,FrameDispatchAppServiceCandidateTest,FrameProcessHandlerTest,RouteLookupHandlerTest,BrokerBoltHandlerTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false clean test`；领域/模块边界及 clean/diff。
- [x] LocalRouteAppService 将 publish 内联回 acceptRoutes 的 Lease 保护步骤，删除私有包装，事件顺序保持。
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=LocalRouteAppServiceReceiveTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过（12 项），diff 通过。本轮未刷新 quick/full/e2e。
- [x] LocalRouteAppService.cleanupRoutes 保留输入/完整资格/调用/响应转换；BatchConnectionRegistry
  新增领域清理契约，InMemoryBatchConnectionRegistry 负责缓存/淘汰/幂等删除/汇总，返回现有 RouteCleanupResult。
  BrokerDrainTransformer 转换已清理响应，SDK rejected 工厂封装固定拒绝原因，文档/Harness 同步。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=InMemoryBatchConnectionRegistryTest,LocalRouteAppServiceReceiveTest,LocalRouteAppServiceCleanupTest,LocalRouteAppServiceIdempotencyTest,RouteCleanupCheckpointTest,AbstractBrokerRpcHandlerDispatchTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过（26 项），新增淘汰与水位冲突验证。
- [x] 安装最新产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerDomainMustNotDependOnTechnicalLayers,ModuleBoundaryTest' clean test` 通过，readiness/diff 通过。
  本轮未刷新全仓 quick/full/e2e，T13 保持 active Review。
- [x] BatchConnectionRegistry 改为领域扩展接口，基础设施 InMemoryBatchConnectionRegistry 包装原存储，
  删除 Capacity，接收事实独立为 GatewayRouteBatchReception；应用只持有单一 Registry，配置与文档同步。
  InMemoryBatchConnectionRegistryTest 验证共享存储，ImBrokerApplicationTest 验证单一 Bean 与契约同实例。
- [x] 验证包装 Registry 契约调整：
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=InMemoryBatchConnectionRegistryTest,GatewayRouteBatchTest,LocalRouteAppServiceReceiveTest,LocalRouteAppServiceCleanupTest,LocalRouteAppServiceIdempotencyTest,AbstractBrokerRpcHandlerDispatchTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false clean test` 通过（28 项）。
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=InMemoryConnectionRegistryTest,ConnectionRouteRevisionTest,FrameDispatchAppServiceTest,FrameDispatchAppServiceLookupTest,FrameDispatchAppServiceCandidateTest,RouteLookupHandlerTest,ConnectionCloseHandlerTest,BrokerMetricsTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过（35 项）。
  安装最新产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerDomainMustNotDependOnTechnicalLayers,ModuleBoundaryTest' clean test` 通过。
  本轮未刷新 quick/full/e2e，T13 保持 active Review。
- [x] 按最新 Review 将 BatchConnectionRegistry 从 Adapter 迁入 domain/connection，去除配置/SDK/回调，
  使用内部 Capacity 和 Reception 领域值；应用层串行资格、收集、Lease 保护应用与发布，装配和文档同步。
- [x] 验证领域 Registry：
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=GatewayRouteBatchTest,LocalRouteAppServiceReceiveTest,LocalRouteAppServiceCleanupTest,LocalRouteAppServiceIdempotencyTest,AbstractBrokerRpcHandlerDispatchTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false clean test` 通过（27 项）。
  安装最新产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerDomainMustNotDependOnTechnicalLayers,ModuleBoundaryTest' clean test` 通过，diff 通过。本轮未刷新 quick/full。
- [x] adapter/connection/BatchConnectionRegistry 包装实际 ConnectionRegistry，迁入暂存/容量/回收/接收锁，
  LocalRouteAppService 负责输入/资格/Lease/事件，配置与三类应用测试装配同步；Architecture/README/Harness 同步。
- [x] 验证 Registry 包装调整：
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=GatewayRouteBatchTest,LocalRouteAppServiceReceiveTest,LocalRouteAppServiceCleanupTest,LocalRouteAppServiceIdempotencyTest,AbstractBrokerRpcHandlerDispatchTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false clean test` 通过；
  补充 Lease 不可用不写入/不发布验证后，同一测试选择器去掉启动测试执行 `test`，26 项通过，启动生产代码未再修改。
  安装最新产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerDomainMustNotDependOnTechnicalLayers,ModuleBoundaryTest' clean test` 通过。
  readiness/diff 通过，本轮未刷新 quick/full，T13 保持 active Review。
- [x] 路由接收命令/结果统一为 AcceptGatewayRoutesCommand/AcceptGatewayRoutesResult，成功状态为
  ROUTES_APPLIED；Broker SDK、Server Handler/应用/Transformer、Gateway Coordinator 和两侧测试同步，
  SDK README 记录同版本契约。
- [x] GatewayRouteChunkIndex 表达非负分片序号，批次索引及接受/收集行为不再传播 Integer；
  应用入口转换，领域测试覆盖非法序号和批次范围。Gateway 两处测试夹具同步移除旧 clusterId 参数。
- [x] 验证本轮接收命名调整及序号领域化：
  `mvn -q -pl im-broker/im-broker-server,im-gateway/im-ws-gateway/im-ws-gateway-server -am -Dtest=GatewayRouteBatchTest,LocalRouteAppServiceReceiveTest,AbstractBrokerRpcHandlerDispatchTest,GatewayRouteSnapshotCoordinatorTest,GatewayBrokerAuthorizationTest -Dsurefire.failIfNoSpecifiedTests=false clean test` 通过（24 项），readiness/diff 通过，旧生产名称无残留。本轮未刷新 quick/full。
- [x] LocalRouteAppService.acceptRoutes 六步整理，转换后使用领域字段，重复收集复用实体幂等行为；
  保留容量、锁及 Lease 保护语义，不新增方法或中间对象。
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=GatewayRouteBatchTest,LocalRouteAppServiceReceiveTest,LocalRouteAppServiceCleanupTest,LocalRouteAppServiceIdempotencyTest,AbstractBrokerRpcHandlerDispatchTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过（24 项），readiness/diff 通过。
- [x] domain/connection 新增 GatewayRouteBatch、GatewayRouteBatchId、RouteCleanupId、RouteCleanupCheckpoint；
  接收实体使用手写 Builder 与领域字段，提供分片匹配/幂等收集/按序组装/摘要验证，清理检查点保护重试水位。
  LocalRouteAppService 删除 PendingBatch/BatchKey/CleanupKey/CompletedCleanup，保留容量/回收/锁，不新增 Repository。
- [x] GatewayRouteBatchTest/RouteCleanupCheckpointTest 与应用接收/清理/幂等聚焦 clean test 通过，文档/Harness 同步。
- [x] 领域实体、非法摘要输入、Handler 与真实启动共 40 项测试通过：
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=GatewayRouteBatchTest,RouteCleanupCheckpointTest,LocalRouteAppServiceReceiveTest,LocalRouteAppServiceCleanupTest,LocalRouteAppServiceIdempotencyTest,LocalLeaseAppServiceTest,AbstractBrokerRpcHandlerDispatchTest,AbstractBrokerRpcHandlerTest,BrokerRequestInterceptorChainTest,BrokerBoltHandlerTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false clean test`。
  安装最新产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerScheduledApplicationEntrypointsBelongToInterfaces+brokerDomainMustNotDependOnTechnicalLayers,ModuleBoundaryTest' clean test` 通过。
  `./scripts/verify.sh clean` 与 `git diff --check` 通过；未跟踪文件包含本轮领域类型和测试，保留用户既有改动。
  T13 保持 active Review；本轮未刷新 quick/full，保留既有门禁失败归因。
- [x] LocalRouteAppService.acceptRoutes 合并重复容量分支，PendingBatch.matches/addChunk 管理一致性与内部状态；
  cleanupRoutes 内联原 purge，以领域标识缓存本轮各 Gateway 的水位和删除数量，独立清理锁内只查一次操作。
- [x] 删除无调用方 DrainCleanupCommand、DrainCleanupResult、LocalBrokerDomainTransformer；
  PurgeTest 改公开入口 IdempotencyTest，接收与清理独立锁测试同步，不保留测试专用生产入口。
- [x] acceptRoutes 删除异常包装，GatewayRouteSnapshotHandler/AbstractBrokerRpcHandler 声明受检异常并沿现有 handle 通道传播；
  ReceiveTest 补重复分片容量、冲突批次和原受检异常透传回归，聚焦本机服务/Handler 测试通过。
- [ ] 运行完整相关聚焦、启动与模块测试，更新 Harness/文档并检查 clean；不推进 T13 收口。
- [x] 跟进用户的 LocalLeaseAppService 两参数构造器与直接单调时间调用：每轮复用单个 now，
  领域变量使用完整名称，pendingRequests 表达准备记录，不新增方法、注入时间源或 catch。
  LocalLeaseAppServiceTest 用线程内静态时间替身适配构造器，5 项期限/容量/错配/重放/异常回归通过。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=LocalLeaseAppServiceTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false test`：6 项通过，clean/diff 通过；quick/full 未刷新，不推进 T13 收口。
- [x] LocalLeaseAppService.accept 在入口直接构造 RoutingLease，删除参数异常 catch；
  LocalBrokerDomainTransformer 删除无调用方 routingLeaseFrom，保留清理确认转换；README/Architecture 同步。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=LocalLeaseAppServiceTest,LocalRouteAppServiceCleanupTest -Dsurefire.failIfNoSpecifiedTests=false test`：9 项通过，clean/diff 通过；quick/full 未刷新，不推进 T13 收口。
- [x] LocalLeaseAppService 的多字段 Lease 转换迁 transformer/domain/LocalBrokerDomainTransformer，
  pending 使用 LeaseNonce 键，PreparedLeaseRequest.expired 表达单调过期；清理入口先转换确认列表，
  BrokerClusterOperation.matchesRouteCleanup 与 DrainProgress.matchesGatewayConfirmations 核对完整清理资格。
- [x] model/properties/LocalRouteAppProperties 合并快照接收配置和清理容量，服务直接读取对象，
  GatewayRouteConfiguration/测试构造同步；分片重复判断拉平，PendingBatch 纯 getter 改 Lombok，public/private 顺序保留。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=LocalLeaseAppServiceTest,LocalRouteAppServiceReceiveTest,LocalRouteAppServiceCleanupTest,LocalRouteAppServicePurgeTest,BrokerClusterTest,DrainProgressTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过，
  覆盖期限边界、非法 Lease 不消费 nonce、重复/缺失/错配水位不清理以及两类状态锁独立。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=LocalLeaseAppServiceTest,LocalRouteAppServiceReceiveTest,LocalRouteAppServiceCleanupTest,LocalRouteAppServicePurgeTest,BrokerClusterTest,DrainProgressTest,BrokerFailureRecoveryAppServiceTest,BrokerDrainAppServiceTest,BrokerClusterAppServiceGatewayRoutesTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false clean test`：93 项通过。
- [x] `mvn -q -pl im-broker/im-broker-server -am test`：474 项中 3 项既有错误仍在 BrokerClusterAppServiceChangeTest
  的激活提交失败与 quorum 读取异常断言，不将全模块标记通过；文档/Harness 已同步。
- [x] readiness、clean/diff 通过；`./scripts/verify.sh quick` Harness 自测通过，架构 66 项中
  3 失败、1 错误、2 跳过。support/interceptor 不在既有窄 support 白名单、BrokerGossipScheduler
  依赖 infrastructure 配置、RegistryObservationAppService 依赖 BrokerStateStore；另有
  PersistenceConcurrencyArchitectureTest 反射 Message 聚合 Builder 时 NoClassDefFoundError: ImChatId。
  未改这些代码或规则，不将整体门禁标记通过，不继续 full；T13 保持 active。
- [x] application/routing/FrameDeliveryAppService.java 改为 application/frame/FrameDispatchAppService.java，
  三个同包测试同步；FrameProcessHandler、RouteLookupCandidateLifecycle、两配置类及 Handler 测试引用同步。
  ConnectionRouteRevisionTest 归 infrastructure/registry/connection，脚本测试选择器和能力包规则同步 frame。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=FrameDispatchAppServiceTest,FrameDispatchAppServiceLookupTest,FrameDispatchAppServiceCandidateTest,FrameProcessHandlerTest,ConnectionRouteRevisionTest -Dsurefire.failIfNoSpecifiedTests=false clean test`：通过。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=ImBrokerApplicationTest,FrameDispatchAppServiceTest,FrameDispatchAppServiceLookupTest,FrameDispatchAppServiceCandidateTest,FrameProcessHandlerTest,ConnectionRouteRevisionTest -Dsurefire.failIfNoSpecifiedTests=false test`：28 项通过。
  安装最新产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerScheduledApplicationEntrypointsBelongToInterfaces+brokerDomainMustNotDependOnTechnicalLayers,ModuleBoundaryTest' clean test` 通过；脚本语法和 diff 通过。
- [x] 本轮 clean/diff 通过；整体 quick/full/E2E 未刷新，T13 保持 active。
- [x] application/local/LocalRouteAppService.java 合并原 GatewayRouteSnapshotAppService 与 LocalRouteCleanupAppService，
  acceptRoutes/cleanupRoutes 维护本机路由视图，共用依赖、独立 snapshotLock/cleanupLock；旧生产类删除，
  GatewayRouteConfiguration 保留单一 Bean，BrokerDrainConfiguration 删除重复装配，两个 Handler 调用同步。
- [x] LocalLeaseActivationAppService 与同包测试改名 LocalLeaseAppService；路由测试迁为 local 包下
  LocalRouteAppServiceReceiveTest/CleanupTest/PurgeTest，不保留兼容类或构造器；routing-ha 脚本选择器同步。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=LocalRouteAppServiceReceiveTest,LocalRouteAppServiceCleanupTest,LocalRouteAppServicePurgeTest,LocalLeaseAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false clean test`：通过，
  包含“清理等待时仍可接收分片”并发回归，文档和 Harness 规则同步。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=ImBrokerApplicationTest,LocalRouteAppServiceReceiveTest,LocalRouteAppServiceCleanupTest,LocalRouteAppServicePurgeTest,LocalLeaseAppServiceTest,BrokerClusterAppServiceGatewayRoutesTest,BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest,FrameDeliveryAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false clean test`：44 项通过。
  安装最新产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerScheduledApplicationEntrypointsBelongToInterfaces+brokerDomainMustNotDependOnTechnicalLayers,ModuleBoundaryTest' clean test` 通过。
  routing-ha 脚本选择器改名、bash 语法和 diff 检查通过；整体 quick/full/E2E 未刷新。
- [x] 本轮 clean/diff 通过；不推进 T13 收口。
- [x] application/authority/LocalLeaseActivationAppService.java 与 application/routing/DrainRouteCleanupAppService.java
  迁 application/local，后者改名 LocalRouteCleanupAppService；三个同包测试、Handler、配置引用同步，
  删除空 authority 目录，LayerBoundaryTest 能力包改为 local；独立状态和锁不合并。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=LocalLeaseActivationAppServiceTest,LocalRouteCleanupAppServiceTest,LocalRouteCleanupAppServicePurgeTest,BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false clean test`：通过。
- [x] 文档同步，运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=ImBrokerApplicationTest,LocalLeaseActivationAppServiceTest,LocalRouteCleanupAppServiceTest,LocalRouteCleanupAppServicePurgeTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，含此前用例共 30 项。
  安装最新 Broker 产物后，`mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerScheduledApplicationEntrypointsBelongToInterfaces+brokerDomainMustNotDependOnTechnicalLayers,ModuleBoundaryTest' clean test` 通过。
- [x] 本轮 clean/diff 通过；整体 quick/full/E2E 未刷新，不推进 T13 收口。
- [x] BrokerClusterAppService.confirmGatewayRoutes/ConfirmGatewayRoutesCommand 命名同步 Handler、
  BrokerRoutingCommandTransformer 和 BrokerClusterAppServiceGatewayRoutesTest；SDK 协议不改。
- [x] BrokerClusterAdapter.confirmGatewayRoutes 接收 operation、领域确认事实和时间，
  BrokerClusterRaftTransformer.gatewayConfirmationCommandFrom 构造日志命令；应用层不拼接日志载荷。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAppServiceGatewayRoutesTest,BrokerFailureRecoveryAppServiceTest,BrokerDrainAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false clean test`：23 项通过；文档同步、diff 通过，quick/full 未刷新。
- [x] 将 application/routing/LocalLeaseActivationAppService.java 及同包测试迁入 application/authority，
  RouteLeasePrepareHandler、RouteLeaseInstallHandler、RoutingLeaseConfiguration 引用同步。
- [x] GatewayRouteSnapshotAppService.acknowledge 迁为 BrokerClusterAppService.acknowledgeGatewaySnapshot，
  GatewayRouteSnapshotAckHandler 直接调用；测试迁 cluster/BrokerClusterAppServiceSnapshotAckTest，
  保留真实 JSON 入站、Raft ACK 持久化及过期/晚到拒绝行为，不新增应用服务或兼容转发。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=LocalLeaseActivationAppServiceTest,BrokerClusterAppServiceSnapshotAckTest,GatewayRouteSnapshotAppServiceTest,BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false clean test`：通过。
- [x] LayerBoundaryTest 同步 authority 能力包，文档/PROGRESS/Harness 同步。
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=ImBrokerApplicationTest,LocalLeaseActivationAppServiceTest,BrokerClusterAppServiceSnapshotAckTest,GatewayRouteSnapshotAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过，含此前聚焦测试共 33 项通过。
  `mvn -q -pl im-broker/im-broker-server -am -Dmaven.test.skip=true install` 安装最新产物后，
  `mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerScheduledApplicationEntrypointsBelongToInterfaces+brokerDomainMustNotDependOnTechnicalLayers,ModuleBoundaryTest' clean test` 通过。
- [x] 本轮 clean/diff 通过；整体 quick/full/E2E 未刷新，既有失败不隐藏，T13 保持 active。
- [x] 排空职责整理：domain/cluster/BrokerClusterOperation.java 共用 waitingForGatewayAcks/timedOut，
  needsRouteCleanup 表达排空类型、重建阶段、ACK 和证据资格；application/cluster/BrokerClusterAppService.java
  调用领域判断，transformer/application/BrokerClusterAppTransformer.java 转换完成响应。
  不新增应用方法、领域服务或日志命令；领域/应用测试覆盖超时与 cleanup 幂等行为。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterTest,DrainProgressTest,BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest,BrokerClusterAppTransformerTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- [x] 排空增量同步文档；87 项聚焦测试、readiness、clean/diff 通过。
  本轮未重跑 quick/full，既有全模块 3 项错误与 quick 自测失败继续可见，不推进 T13 收口。
- [x] 记录 [恢复领域行为设计](../../design-docs/2026-10-07-broker-route-recovery-domain-design.md)，按用户确认的同聚合职责推进；独立 Review 未执行。
- [x] Domain：修改 domain/cluster/DrainProgress.java、BrokerClusterOperation.java、BrokerCluster.java，
  增加恢复证据、资格及终态行为；验证 DrainProgressTest、BrokerClusterTest 的降级拒绝和完成幂等。
- [x] 日志：新增 model/cqrs/command/VerifyRouteRecoveryCommand.java、CompleteRouteRecoveryCommand.java，
  同步 BrokerClusterConsensusCommand.java、adapter/cluster/raft/BrokerClusterCommandCodec.java、RaftBrokerClusterStateMachine.java；
  Codec 版本升 v5，BrokerClusterCommandCodecTest 验证新命令和旧版本拒绝。
- [x] 编排：修改 adapter/cluster/BrokerClusterAdapter.java、transformer/infrastructure/BrokerClusterRaftTransformer.java、
  application/cluster/BrokerClusterAppService.java；更新恢复/排空/授权测试的 mock、恢复中断替身。
- [x] 前置阻断：BrokerAdmissionContractValidatorTest 跟随生产类迁 support/interceptor，同断言、同可见性；
  运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerAdmissionContractValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=DrainProgressTest,BrokerClusterTest,BrokerFailureRecoveryAppServiceTest,BrokerDrainAppServiceTest,BrokerClusterCommandCodecTest,BrokerClusterAdapterTest,BrokerClusterAppServiceActivationTest,BrokerClusterAppTransformerTest,ImBrokerApplicationTest,BrokerAdmissionContractValidatorTest -Dsurefire.failIfNoSpecifiedTests=false clean test`：112 项通过。
  原通用阶段绕过回归先失败后通过；完成不可用的等待状态回归先失败后修复。真实 Raft 验证/快照重启/完成幂等通过。
- [x] `mvn -q -pl im-broker/im-broker-server -am test`：466 项中 3 项既有错误，均为未改动的
  BrokerClusterAppServiceChangeTest 激活提交与 quorum 读取失败的异常传播断言；不标记全模块通过。
- [x] 同步 README/Architecture、Harness、设计文档、PROGRESS，命令格式 v5、快照 v6、独立 Review 缺口明确。
- [x] 运行 clean/quick 并归因失败：clean/diff 通过；补充代码后的 quick 两次分别在 load-test
  stale-lock Harness 自测（竞争成功数为 0）与 routing-ha Harness 自测（退出码 124）失败，
  尚未进入架构检查。没有修改脚本或弱化规则，不继续 full；独立子代理审查不执行，T13 保持 active。
- [x] 补齐日志应用边界的并发事实保护：BrokerClusterTest 过期故障汇总拒绝、DrainProgressTest 汇总后迟到 ACK 拒绝
  两条回归先失败后通过；相同重复 ACK 仍允许。首轮 quick 在补充代码前主动停止，后续重新运行。
- [x] 补充后重跑 `mvn -q -pl im-broker/im-broker-server -am test`：468 项中仍仅有上述 3 项既有错误，
  相关领域/恢复/日志/Adapter/Transformer/启动/拦截器 114 项零失败零错误，不将全模块标记通过。
- [x] `BrokerClusterAppService.recoverRoutes` 合并正常/降级证据准备的保存调用，正向表达降级登记，
  明确 submitted 与持久化 degradedReason 的职责；Javadoc/八步注释同步，不新增方法或中间类型。
- [x] `BrokerFailureRecoveryAppServiceTest` 增加已有正常证据在旧事件到期后恢复、已有残缺证据
  不重复提交的回归。运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerFailureRecoveryAppServiceTest,BrokerDrainAppServiceTest,BrokerClusterTest,DrainProgressTest -Dsurefire.failIfNoSpecifiedTests=false test`：
  testCompile 仍因 BrokerAdmissionContractValidatorTest 跨包访问 validateRequestType 失败，新增用例未运行。
  `mvn -q -pl im-broker/im-broker-server -am -Dmaven.test.skip=true compile`、clean/diff 通过，不推进全仓门禁。
- [x] `BrokerClusterAppService.advanceDrain` 仅完善 Javadoc 与九处步骤注释，说明一轮推进、
  已有证据重验、超时失败提交及 cleanup/共识异常边界。方法体去除注释和空白后前后一致，
  diff 通过；纯注释调整未重跑测试，不推进 T13 收口。
  clean 两次均检测验证期间工作树变化而失败，未标记通过。
- [x] 删除 BrokerClusterOperationPhase.rebuildingRoutes，应用层改回直接比较 REBUILDING_ROUTES；
  canAdvanceDrain 保留组合资格判断。生产 compile、diff 通过，本轮测试未重跑。
- [x] `BrokerClusterAppService.advanceDrain` 移除 submissionStage，将显式类型的 thenApply、
  exceptionally 和 thenCompose 直接连接，保持 cleanup 与提交异常边界；
  `mvn -q -pl im-broker/im-broker-server -am -Dmaven.test.skip=true compile`、clean/diff 通过。
  本轮未重跑此前被跨包访问阻断的测试，不推进全仓门禁。
- [x] `BrokerClusterAppService.advanceDrain` 删除 handle 内重复的异常分支，将恢复独立为 exceptionally；
  成功路径的同步/异步共识异常保留在内层提交阶段，末尾 thenCompose 展开，不新增方法或业务类型。
- [x] `BrokerDrainAppServiceTest` 的证据/完成提交失败测试扩展同步与异步参数化场景；
  `mvn -q -pl im-broker/im-broker-server -am -Dmaven.test.skip=true compile` 通过。
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerDrainAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  仍因 BrokerAdmissionContractValidatorTest 跨包访问 validateRequestType 在 testCompile 失败，新增场景未执行。
  clean 首次检测验证期间工作树变化失败，重跑后 clean/diff 通过；不将测试标记通过。
- [x] `BrokerClusterOperationPhase.java` 增加 rebuildingRoutes/canAdvanceDrain，
  `BrokerClusterAppService.advanceDrain` 直接调用枚举谓词，不再保留重复局部布尔判断。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest,BrokerClusterTest,DrainProgressTest -Dsurefire.failIfNoSpecifiedTests=false test`：
  在 testCompile 失败，BrokerAdmissionContractValidatorTest 仍位于 interfaces/handler/interceptor，
  跨包调用 support/interceptor 下 package-private validateRequestType；归因于现有包迁移，未修改或绕过。
  clean/diff 通过，不将本轮测试标记通过，不推进全仓门禁。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dmaven.test.skip=true compile` 通过，确认生产代码可编译；
  此命令不替代被阻断的测试。
- [x] `BrokerClusterAppService.advanceDrain` 应用已确认的正向阶段变量和拉平 ACK 分支，保留
  cleanupResult 命名；完成调用/响应分行，succeeded/if 表达成功条件，补充 handle 异常边界注释。
  不新增方法或类型；`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerDrainAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`：10 项通过，diff 通过。
- [x] `BrokerClusterAppService.advanceDrain` 将新 cleanup、已有证据与验证恢复移至末尾统一处理，
  删除重复的证据提交和完成判断；Optional.empty 表达本轮无需 cleanup，不新增方法或结果类型。
- [x] `BrokerDrainAppServiceTest` 增加 cleanup RPC 失败在超时前等待、超时后记录失败回归；
  运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest,BrokerClusterTest,DrainProgressTest -Dsurefire.failIfNoSpecifiedTests=false clean test`：56 项通过。
  证据拒绝、证据/完成提交异常、恢复后不重复 cleanup 和底层完成幂等回归继续通过。
  clean/diff 通过，本轮未刷新 quick/full，不推进 T13 收口。
- [x] `BrokerClusterAppService.advanceDrain` 按用户最新 Review 保留方法内编排，取消本轮新增
  finishDrain 私有步骤；evidenceStage 与步骤注释表达实际准备职责，不新增方法、上下文或服务类型。
- [x] `BrokerDrainAppServiceTest` 新增证据拒绝、证据写入异常与完成写入异常覆盖；
  运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest,BrokerClusterTest,DrainProgressTest -Dsurefire.failIfNoSpecifiedTests=false clean test`：54 项通过。
- [x] 同一聚焦命令重验 finishDrain 内联后的行为：54 项通过，clean/diff 通过；
  本轮 quick 在用户中断后停止，未形成通过证据，未刷新 full。
- [x] `domain/cluster/DrainRouteSummary.java` 重命名为 RouteRecoveryEvidence，DrainProgress、
  DrainProgressUpdate 的字段/访问器统一为 recoveryEvidence；BrokerClusterAppService、
  GatewayRouteSnapshotAppService、BrokerClusterAdapter、BrokerClusterRaftTransformer 和测试同步。
  BrokerClusterSnapshot/CommandCodec 格式分别升为 v6/v4，Architecture 文档同步，不保留旧别名。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am '-Dtest=DrainProgressTest,BrokerClusterTest,BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest,BrokerClusterCommandCodecTest,BrokerClusterSnapshotIsolationTest,GatewayRouteSnapshotAppServiceAckTest,DrainRouteCleanupAppServiceTest,BrokerClusterAdapterTest#persistsDrainEvidenceAndCompletesBrokerRemovalAtomically' -Dsurefire.failIfNoSpecifiedTests=false clean test`：61 项通过。
  日志包含汇总证据的 round-trip、旧版本拒绝，以及快照恢复后继续完成排空均通过。
- [x] `BrokerClusterAppService.advanceDrain` 将 VERIFYING 恢复与已保存证据路径汇入末尾完成提交；
  SUCCEEDED 重试由 BrokerCluster.completeDrain 保证幂等。BrokerDrainAppServiceTest 增加验证恢复、
  重复完成 revision 不变与等待旧事件后不重复 cleanup 回归；上述同一聚焦命令共 63 项通过。
- [x] 本轮 `./scripts/verify.sh clean` 与 diff 检查通过；`./scripts/verify.sh quick` Harness 自测通过，
  架构检查仍因 BrokerGossipScheduler/RegistryObservationAppService 两项既有基础设施依赖失败
  （66 项中 2 失败、2 跳过），未跳过失败继续 full，T13 保持 active。
- [x] `application/routing` 四个 AppService 整理：新增 AcceptGatewaySnapshotCommand、
  AcknowledgeGatewaySnapshotCommand、CleanupDrainRoutesCommand 和 GatewayRouteWatermark；
  三个 Handler 通过生成的 BrokerRoutingCommandTransformer 转换 SDK 输入。
  FrameDeliveryAppService/FrameDeliveryConfiguration 去除同源共识依赖与候选无用参数，
  GatewayRouteSnapshotAppService 删除简单结果包装，LocalLeaseActivationAppService 删除依赖空值校验。
  DrainRouteCleanupAppService 使用 BrokerClusterOperation.canCleanupRoutes 与
  DrainProgress.matchesGatewaySnapshot；不重复构造 SDK 水位排序比较，保留实际安全约束。
- [x] 上述服务及 FrameProcessHandlerTest、ImBrokerApplicationTest 运行
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=DrainRouteCleanupAppServiceTest,DrainRouteCleanupAppServicePurgeTest,LocalLeaseActivationAppServiceTest,GatewayRouteSnapshotAppServiceTest,GatewayRouteSnapshotAppServiceAckTest,FrameDeliveryAppServiceTest,FrameDeliveryAppServiceLookupTest,FrameDeliveryAppServiceCandidateTest,FrameProcessHandlerTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false clean test`：42 项通过。
  cleanup/ACK 成功用例经过真实 Handler JSON 入站并断言实际删除及 Raft 水位。
  readiness、clean、diff 通过；T13 保持 active，不提前收口。
- [x] `./scripts/verify.sh quick` 沙箱外重跑：Harness 自测通过，实际架构检查 66 项中 2 项失败、
  2 项跳过；仍为 BrokerGossipScheduler 的 infrastructure 配置依赖和 RegistryObservationAppService
  的 BrokerStateStore 依赖，未触碰本轮四个 routing 服务。首次沙箱 `/dev/fd` 权限失败单独归因；
  不跳过门禁继续 full，真实 E2E/routing-ha 与独立 AI Review 未刷新。
- [x] DrainRouteCleanupAppService 核对 eventDeadline，LocalLeaseActivationAppService 匹配前不消费 nonce，
  GatewayRouteSnapshotAppService 发布连接表实际集合，FrameDeliveryAppService 负缓存回收避免并发盲取首项。
- [x] 三个回归先复现错误，修复后运行
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=DrainRouteCleanupAppServiceTest,DrainRouteCleanupAppServicePurgeTest,LocalLeaseActivationAppServiceTest,GatewayRouteSnapshotAppServiceTest,GatewayRouteSnapshotAppServiceAckTest,FrameDeliveryAppServiceTest,FrameDeliveryAppServiceLookupTest,FrameDeliveryAppServiceCandidateTest,RegistryEventListenersTest,BrokerStateStoreTest,ConnectionRouteRevisionTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- [x] 清理三个 AppService 的 now/currentTime/validateLookup 简单包装、收窄非法 Lease 转换捕获；
  保留协议资源限制和校验，不新增抽象。Architecture/PROGRESS 同步，结构性 CQRS/依赖整理仍在 Review 范围。
- [x] `application/cluster/BrokerClusterAppService.java` 整理 recoverRoutes：验证事实准备与终态提交分开，
  正常、降级及历史 VERIFYING 共用完成链，保留原等待状态和已持久化降级意图，无新增私有包装。
- [x] `BrokerFailureRecoveryAppServiceTest.java` 增加未超时不写证据、摘要拒绝不推进回归；
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerFailureRecoveryAppServiceTest,BrokerDrainAppServiceTest,BrokerClusterTest,DrainProgressTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过。
- [x] `DrainProgress.java` 增加 allGatewaysConfirmed/summarizeCleanup，应用层直接表达排空流程，
  删除 persistSummary/completeDrain/failDrain 私有包装。
- [x] `BrokerClusterAdapter.java` 新增具名摘要记录、排空完成/强制结束、授权开始/完成、
  降级验证与恢复终态单次提交入口；`BrokerClusterRaftTransformer.java` 转换领域参数为日志载荷。
  `BrokerClusterAppService.java` 删除阶段命令构造，保留业务条件、异步顺序与响应判断；测试替身同步。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest,BrokerClusterAppServiceActivationTest,BrokerClusterAppServiceFenceTest,BrokerClusterTest,DrainProgressTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAdapterTest,ImBrokerApplicationTest,BrokerClusterAppServiceActivationTest,BrokerClusterAppServiceFenceTest,BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- [x] 九个 AppService 与同包测试从 application/service 上移至 application 各能力包，删除空 port/service
  目录；全仓 Java 引用、定时入口 ArchUnit 识别和能力包规则同步，旧 service/port 禁止重新出现。
  Architecture、Server README、Coding、Harness 和 PROGRESS 同步用户确认的结构。
- [x] clean 聚焦回归覆盖 15 类应用测试；启动、HTTP、事件与定时入口测试通过。
  `mvn -q -pl im-test/im-architecture-test '-Dtest=LayerBoundaryTest#brokerApplicationMustGroupUseCasesByCapability+brokerScheduledApplicationEntrypointsBelongToInterfaces+brokerDomainMustNotDependOnTechnicalLayers,ModuleBoundaryTest' clean test` 通过。
  clean/diff 通过，既有 Gossip 两处架构失败保持可见，不收口 T13。
- [x] 删除 application/port/diagnostic/BrokerDiagnostics，RegistryBrokerDiagnostics 合并为
  application/service/diagnostic/BrokerDiagnosticAppService；Controller 和诊断测试同步。
- [x] 删除 GatewayFrameDeliveryClient/GatewayFrameWriter、RouteLookupPeerClient/BoltRouteLookupPeerClient；
  FrameDeliveryAppService 调用 BrokerClusterAdapter.deliverFrame/lookupRoutes，保留 SDK 转换、
  Gateway outcome/duration 指标、Lookup 并发配置和预算；线程池装配归 RouteLookupConfiguration。
- [x] 三个 Registry 接口迁入 domain/cluster、domain/connection，注册观察和 ConnectionSyncResult 使用
  Server 领域类型，内存索引/Gossip/事件/调用方及测试同步；RegistryTransformer 映射 SDK 端点，协议形状不变。
- [x] 聚焦 RegistryTransformer、FrameDelivery（投递/lookup/candidate/指标）、Diagnostic、Registry、
  Gossip、Listener、HTTP 及真实 BrokerClusterAdapter/启动测试通过。
- [x] 正式 architecture 先在 im-iam-sdk JaCoCo EOFException 处失败，以
  `mvn -q -pl im-test/im-architecture-test -am -Dmaven.test.skip=true clean install` 重建生产依赖后，
  实际规则检查执行 66 项，剩余 2 项为 BrokerGossipScheduler/RegistryObservationAppService 的既有
  基础设施依赖，领域无 SDK 依赖及模块边界通过。未弱化规则，整体门禁未通过，不继续 full。
- [x] clean install 后实际架构测试发现 Gateway/诊断的外层依赖，修正为现有 Adapter 调用 SDK、
  BrokerDiagnosticAppProperties 承载应用配置及 adapter/diagnostic/GossipSyncTracker 具体查询能力，
  未恢复 port 或弱化分层规则。新增真实 Gateway RPC 载荷及成功/失败指标回归。
- [x] 修正后运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAdapterTest,ImBrokerApplicationTest,BrokerManagementControllerTest,RegistryTransformerTest,FrameDeliveryAppServiceTest,FrameDeliveryAppServiceLookupTest,FrameDeliveryAppServiceCandidateTest,BrokerDiagnosticAppServiceTest,InMemoryBrokerRegistryTest,InMemoryGatewayRegistryTest,InMemoryConnectionRegistryTest,BrokerStateStoreTest,RegistryEventListenersTest,FrameProcessHandlerTest,ConnectionCloseHandlerTest,GatewayRouteSnapshotAppServiceAckTest,DrainRouteCleanupAppServiceTest,BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dmaven.test.skip=true install` 安装最新验证产物后，
  `mvn -q -pl im-test/im-architecture-test clean test` 重验，剩余上述两个既有边界问题，未标记通过。
- [x] clean 检查识别诊断应用的基础类型参数，改为 ConnectionRoutesQuery/GossipRecordsQuery，
  Controller 和测试同步，诊断应用及 HTTP 聚焦回归通过，clean/diff 通过，不保留基础类型重载。
- [x] DrainRouteCleanupSummary 迁移为 `domain/cluster/RouteCleanupResult.java`，数量使用 RouteCount，
  Adapter、BrokerDrainTransformer、应用服务及排空/真实 Adapter 测试同步；RPC 契约不变。
- [x] 排空、故障恢复、授权、隔离聚焦测试及 `BrokerClusterAdapterTest#cleanupUsesPeerExecutorAndPreservesOperationPayloadAndRejection` 通过。
- [x] 删除 BoltDrainRouteCleanupClient，cleanup 单次 RPC 并入 BrokerClusterAdapter.cleanupRoutes；
  复用 peerExecutor 并使用原 typed cleanup 超时，BrokerDrainTransformer 转换载荷及摘要。
  应用服务删除 cleanup 依赖与 SDK 目标查找，构造/配置/测试同步迁移。
- [x] 排空、恢复、授权和隔离聚焦测试通过；BrokerClusterAdapterTest 新增 cleanup 调度、
  载荷/配置超时与拒绝响应覆盖。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAdapterTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，验证真实 Adapter 与删除 Client 后的启动装配。
- [x] 删除 `application/port/routing/drain/DrainRouteCleanupClient.java` 单实现接口，
  BrokerClusterAppService/BrokerClusterConfiguration 直接依赖 BoltDrainRouteCleanupClient；
  实现不再 implements 该接口，各构造和排空测试同步具体 mock。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest,BrokerClusterAppServiceTest,BrokerClusterAppServiceActivationTest,BrokerClusterAppServiceFenceTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- [x] 原 BrokerDrainAppService/BrokerFailureRecoveryAppService 流程迁入 BrokerClusterAppService，
  公开入口命名 drain、advanceDrain、recoverRoutes、forceDrain；删除独立类及 Bean，同步两定时入口。
  BrokerClusterConfiguration 装配 cleanupClient 和 BrokerDrainAppProperties，各测试构造同步迁移。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAppServiceTest,BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest,BrokerClusterAppServiceActivationTest,BrokerClusterAppServiceFenceTest,BrokerClusterLeaseGrantTest,BrokerClusterLeaseInstallationTest,BrokerLeaseGrantAtomicityTest,RoutingLeaseLifecycleTest,BrokerMembershipLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=ImBrokerApplicationTest,BrokerClusterConfigurationTest,BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。用户 scheduler 目录及新增 Gossip 任务保持，启动注册断言同步实际完整集合。
- [x] `BrokerFailureRecoveryAppServiceTest.java` 新增阶段提交间中断并重启恢复回归，先失败为
  期望 DEGRADED、实际 SUCCEEDED；`BrokerFailureRecoveryAppService.java` 统一正常/降级终态恢复，
  合并 degrade/advanceToDegraded，`BrokerClusterOperation.java` 校验故障恢复成功证据与降级意图。
- [x] `BrokerDrainAppService.java` 删除单纯时间比较包装 timedOut；两服务完善步骤和终态职责注释。
  用户已将调度入口改为 interfaces/scheduler，续租测试同步该目录，保持用户改动。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerFailureRecoveryAppServiceTest,BrokerDrainAppServiceTest,BrokerClusterTest,BrokerClusterSnapshotIsolationTest,BrokerClusterCommandCodecTest,GatewayRouteSnapshotAppServiceAckTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过；Architecture/Harness/PROGRESS 同步。
- [x] 四个 RegistryEventListener 迁入 `interfaces/listener`，事件更新逻辑归
  `application/service/gossip/RegistryObservationAppService.java`，由 RegistryObservationConfiguration 装配。
- [x] `RegistryEventListenersTest.java` 验证 Spring 分发、注册/心跳/移除/快照及异常隔离；
  BrokerStateStore、成员恢复和续租聚焦测试通过。ImBrokerApplicationTest 的五任务注册断言修正为
  识别 Spring 包装后的 Runnable，启动及入口装配测试通过。
- [x] Coding、Harness、Broker Architecture/README 与 PROGRESS 同步；架构检查区分业务事件监听与
  技术应用启停监听，不弱化接口层基础设施依赖规则。
- [x] 五个定时用例入口迁入 `interfaces/task`，恢复/续租测试同步移动；配置层引用和成员任务等待时长装配同步。
  移除成员任务的 infrastructure 配置依赖及 Component 自动注册，改由配置 Bean 注册。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerMembershipLifecycleTest,RoutingLeaseLifecycleTest,BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest,FrameDeliveryAppServiceCandidateTest,BrokerClusterConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- [x] `LayerBoundaryTest.java` 新增定时 AppService 入口归接口层的检查，`ImBrokerApplicationTest.java`
  新增五个任务在 Spring 调度器注册的断言；Coding、Harness、Broker Architecture/README 和 PROGRESS 同步。
- [x] 正式 `./scripts/verify.sh architecture` 沙箱外重跑：先在 Broker JaCoCo 数据错误处失败，
  重新生成 Broker 构建证据后在 im-iam-sdk JaCoCo EOFException 处失败，未标记架构规则通过。
  Broker 启动与五任务注册测试通过。
  直接 reactor 架构测试曾在 Gateway 两处旧 clusterId 测试构造器处阻断，不修改无关 Gateway 代码。
- [x] `BrokerClusterOperation.java` 的 targetEpoch 改为 resultEpoch，同步领域、应用、Transformer、
  CQRS 失败响应工厂、cleanup 客户端及测试中的领域访问器/Builder；对外 CQRS targetEpoch 不改名。
- [x] `BrokerClusterSnapshot.java` 快照版本升至 v5，`BrokerClusterSnapshotIsolationTest.java`
  验证 v4 拒绝、operation JSON 新字段与快照往返；13 类领域/应用/编码/转换聚焦测试通过。
- [x] 运行真实 `BrokerClusterAdapterTest` 验证快照保存和重启恢复，通过。
- [x] `BrokerClusterAppService.java` 将 authorizeActiveBrokers 合并到 authorizeCommittedEpoch，
  核心步骤和阶段恢复在同一异步链展示；删除无调用方的 `RoutingEpochActivationResult.java`。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerClusterAppServiceActivationTest,BrokerClusterAppServiceFenceTest,BrokerClusterLeaseInstallationTest,BrokerClusterLeaseGrantTest,BrokerLeaseGrantAtomicityTest,RoutingLeaseLifecycleTest,BrokerClusterAppServiceChangeTest#activationRetryWithOriginalEpochDoesNotFenceAgain+restoredFailedBrokerDoesNotBecomeActiveWithoutExplicitActivation+drainFreezesGatewayEvidenceAndEventDeadline+failureRemovalPreservesGatewayRecoveryEvidence' -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- [x] `BrokerClusterAdapter.java`、`BrokerClusterConsensus.java` 和 `RaftBrokerClusterStateMachine.java`
  的操作/epoch 查询改为领域标识，领导权确认引入 `authority/model/QuorumReadNonce.java`，
  同步受影响的应用、Controller、生命周期与查询测试替身，不保留基础类型重载。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerClusterAppServiceTest,BrokerClusterLeaseGrantTest,BrokerClusterLeaseInstallationTest,BrokerClusterAppServiceActivationTest,BrokerClusterAppServiceFenceTest,BrokerLeaseGrantAtomicityTest,BrokerClusterSnapshotIsolationTest,BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest,DrainRouteCleanupAppServiceTest,DrainRouteCleanupAppServicePurgeTest,GatewayRouteSnapshotAppServiceAckTest,BrokerMembershipLifecycleTest' -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am test` 验证完整调用范围：Broker Server 435 项中
  3 项旧异常约定不一致，位于 BrokerClusterAppServiceChangeTest 的 activationCommitFailure 和
  progressReadFailure 两个参数化分支。真实 Adapter 15 项与启动测试通过；全模块不标记通过。
- [x] `BrokerClusterAppService.java` 删除 quorum 后第二次资格检查，`BrokerClusterAdapter.java`
  提供 grantLease 并持有签发服务；`RaftBrokerClusterStateMachine.java` 在 apply/restore 同一同步边界
  原子执行实际聚合准入和签发。`BrokerClusterConfiguration.java` 与各构造调用同步迁移。
- [x] 新增 `BrokerLeaseGrantAtomicityTest.java`：受控暂停签发序列分配，验证 fence 应用等待同一锁；
  fence 先应用时拒绝且不调用签发服务。八类聚合、原子性和应用聚焦测试通过。
- [x] 运行真实 `BrokerClusterAdapterTest`、`ImBrokerApplicationTest` 和 `BrokerClusterConfigurationTest`
  验证 Adapter 依赖及启动装配，通过；Architecture、Harness 和 PROGRESS 同步。
- [x] `BrokerClusterAppService.java` 的同类异步链整理：prepare 后直接构造请求，holder 授权响应携带
  待重试节点身份并通过 reduce 聚合，去除位置索引/join；fence 恢复减少嵌套，阶段注释紧邻链式步骤。
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerLeaseGrantAtomicityTest,BrokerClusterLeaseGrantTest,BrokerClusterLeaseInstallationTest,BrokerClusterAppServiceActivationTest,BrokerClusterAppServiceFenceTest,RoutingLeaseLifecycleTest,BrokerClusterAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过。
- [x] `BrokerCluster.java` 增加只读 `checkLeaseGrant`，迁入 epoch/ACTIVE/fence 资格判断；
  `RoutingLeaseGrantService.java` 删除 checkAdmission，`BrokerClusterAppService.java` 在 prepare 前和
  quorum 后读取聚合检查资格。签发/安装测试 fixture 同步为包含 fence 的聚合快照。
- [x] `BrokerClusterTest.java` 增加拒绝类型与原因、资格判断不修改聚合、历史 fence 不拒绝新 epoch 的覆盖；
  六类聚合/签发/安装/授权/隔离/续租聚焦测试通过；Architecture、Harness、PROGRESS 同步。
- [x] `BrokerClusterAppService.java` 的签发准入前移至 prepare RPC 前；
  `RoutingLeaseGrantService.java` 的准入输入改为 BrokerId、请求 epoch 和已提交领域事实。
  `BrokerClusterLeaseInstallationTest.java` 增加四类准入失败不执行 RPC 的回归，quorum 后竞态重验保留。
  签发、安装、授权、隔离、续租五类聚焦测试通过；Architecture 和 PROGRESS 同步。
- [x] Review `BrokerClusterAppService.activateLease`，移除无关 revision 更新造成的签发拒绝和重复 epoch 判断，
  保留 quorum 前后资格检查；记录异步失败日志并完善阶段注释，不新增私有步骤方法。
- [x] `BrokerClusterLeaseGrantTest.java` 增加无关 revision 更新及 quorum 期间 epoch/ACTIVE 变化回归；
  revision 场景先失败，再重跑五类租约、授权、隔离及续租聚焦测试通过。
  `im-broker/ARCHITECTURE.md` 同步实际资格与重试语义。
- [x] `BrokerClusterAppService.java` 删除 `requestLease` 和签发辅助私有方法，完整签发编排并入
  `activateLease`；`BrokerClusterLeaseGrantTest.java` 改为通过安装入口观察实际安装 Lease，
  保留 fence 竞态、签发序列和任期回退的行为断言。
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterLeaseGrantTest,BrokerClusterLeaseInstallationTest,BrokerClusterAppServiceActivationTest,BrokerClusterAppServiceFenceTest,RoutingLeaseLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过。
- [x] `BrokerClusterAppService.java` 与 `BrokerClusterAppTransformer.java` 合并四个路由用例的
  响应映射，直接使用登记结果和最新操作，删除无调用方的中间共识结果刷新重载。
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAppTransformerTest,BrokerClusterAppServiceTest,BrokerClusterAppServiceFenceTest,BrokerClusterAppServiceActivationTest,BrokerClusterLeaseGrantTest,BrokerClusterLeaseInstallationTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过。
- [x] 在 `BrokerClusterAppService.java` 删除 `continueClusterChange`，将四个路由用例的登记失败、
  epoch 推进和响应转换放回公开入口；根据 `BrokerClusterOperationProposal.changesRouting()`
  确认这些用例不进入 PENDING，成员变更路径不在本次修改范围内。
- [x] 重跑下列五类应用聚焦测试，并运行
  `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerClusterAppServiceChangeTest#staleRoutingRequestReturnsConflictWithoutSideEffects+activationAdmissionFailureReturnsRejectedWithoutSideEffects+restoredFailedBrokerDoesNotBecomeActiveWithoutExplicitActivation+activationRetryWithOriginalEpochDoesNotFenceAgain+activationWaitsForFenceBeforePublishingNewEpoch+staleEpochDoesNotFenceCurrentHolders+drainFreezesGatewayEvidenceAndEventDeadline+failureRemovalPreservesGatewayRecoveryEvidence' -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- [x] 修改 `BrokerClusterAppService.java`：`advanceEpoch` 接收 `BrokerClusterOperation` 并返回
  `CompletionStage<Void>`；调用方保留登记结果、读取最新操作阶段，授权方法不再承担响应转换。
- [x] 修改 `FenceResult.java`：增加具名隔离完成工厂，应用层不再显式指定 status。
- [x] 更新 `BrokerClusterLeaseGrantTest.java`，在 `BrokerClusterAppServiceFenceTest.java` 增加
  拓扑 Commit 拒绝的回归，验证失败传播、epoch 未改变且不会安装新 Lease。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAppServiceTest,BrokerClusterAppServiceFenceTest,BrokerClusterAppServiceActivationTest,BrokerClusterLeaseGrantTest,BrokerClusterLeaseInstallationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- [x] 运行 `./scripts/verify.sh readiness`、`./scripts/verify.sh clean` 与 `git diff --check`：通过。
- [x] `./scripts/verify.sh quick` 首次受沙箱 `/dev/fd` 访问限制中断；沙箱外重跑 Harness 自测通过，
  architecture 阶段在 Broker Server JaCoCo 报告生成因 `Unknown block type c0` 失败。
  本轮不将架构门禁标记通过，不跳过该失败继续 full。

## 事实源

- 设计或产品规格：[Broker 权威集群状态与高可用路由切换设计](../../design-docs/2026-09-19-broker-routing-consensus-design.md)（已批准、独立 Review `Approved`）、[IM 实时行为批准场景](../../product-specs/im-realtime-approved-scenarios.md)。
- 根与局部 Architecture：`ARCHITECTURE.md`、`im-broker/ARCHITECTURE.md`、`im-gateway/ARCHITECTURE.md`。
- API、协议或数据契约：`im-broker/im-broker-sdk`、`im-gateway/im-ws-gateway/im-ws-gateway-sdk`；具体字段、状态机、错误语义和授权矩阵以批准设计为准。
- 相关代码入口：`BrokerClient`、`BrokerRefresher`、`BrokerConnectionService`、`BrokerStateStore`、Broker RPC handlers、`RegistrationLifecycle`、Gateway `ConnectionRegistry`、`FrameWriteHandler`、`BrokerManagementController`、`im-management/im-monitor`。
- 工程与验证规则：`docs/references/CODING_GUIDE.md`、`docs/references/UNIT_TEST_GUIDE.md`、`docs/references/HARNESS_GUIDE.md`、`docs/RELIABILITY.md`、`docs/SECURITY.md`。

## 验证分层

| 层级 | 命令或证据 | 必须/可选 | 当前结果 |
|---|---|---|---|
| 环境就绪 | `./scripts/verify.sh readiness` | 必须 | 2026-09-20 通过：Java 21、Maven 3.8.4、Node 20.19.5 |
| RED fixture | 各任务列出的精确测试类或 Harness fixture，先确认因缺失目标行为失败 | 按行为变更 | T6 coordinator/service/revision/auth fixture 已确认目标行为缺失；后续 chunk 背压回归先 RED 后修复 |
| 聚焦测试 | Broker/Gateway/Management 对应 Maven 或 npm 命令 | 必须 | T7 指定 Broker/Gateway/Message 测试及 Broker 全模块通过 |
| 架构或契约 | `./scripts/verify.sh architecture`、`./scripts/check-drift.sh` | 必须 | 2026-09-21：T12 `architecture` 通过，`quick` 内 drift 通过 |
| 行为 | `./scripts/verify.sh behavior` | 必须 | 2026-09-21：T12 30 个当前批准场景通过 |
| 多实例故障 | `./scripts/verify.sh routing-ha` | 必须 | 2026-09-21：3 Broker + 2 Gateway、25 类场景与 cleanup 通过。2026-09-22 T13 刷新修复陈旧测试映射和重启预算后，真实重跑因 Docker daemon 在 `compose up --wait` 无响应而以 124 结束，待环境恢复刷新 |
| 端到端 | `./scripts/verify.sh e2e` | 必须 | 2026-09-21：Lease 启动竞态改为有界等待后连续两次通过，Raft 数据使用仓库外临时目录 |
| 容量与规格 | 生产候选拓扑的专用 routing capacity 命令，三次重复并输出报告 | 最终任务必须 | 命令尚未实现 |
| 完整门禁 | `./scripts/verify.sh clean`、`./scripts/verify.sh full`、`./scripts/verify.sh quality` | 必须 | 2026-09-22：`clean` 通过；`full` 执行 98 个测试且零失败/零错误，随后因 routing-ha Broker 重启健康超时失败；quality 未运行 |

验证顺序固定为 RED -> 聚焦测试 -> architecture/drift -> behavior -> routing-ha -> e2e -> clean/full -> quality。`routing-ha` 不并入日常 `quick`，容量验证不在每个任务重复执行。

## 任务状态

同一时间至多一项任务为 `active`。状态只能使用 `not_started`、`active`、`blocked`、`passing`。

| ID | 行为目标 | 范围 | 状态 | 验证证据 | 阻塞/备注 |
|---|---|---|---|---|---|
| T1 | 通过 Spike 锁定成熟 Raft 库 | Broker Server 构建与隔离测试 | `passing` | SOFAJRaft 1.4.1 在 macOS、Linux/ARM64、Linux/AMD64 通过 5 个 Spike | 全局 Enforcer 剩余 4 组既有基线冲突 |
| T2 | 固化 cluster state 与 RouteBroker 选择契约 | Broker SDK、Broker Domain | `passing` | 14 个聚焦测试、architecture 与 quick 通过 | 依赖 T1 结论 |
| T3 | 实现 Raft 状态机、成员变更与 operation | Broker Server 控制面 | `passing` | 单/三 voter、少数派、snapshot/restart、learner、换届恢复、peer auth、startup、drift、behavior 通过 | 真实 mTLS 留 T9 |
| T4 | 实现 Lease、fence 与 epoch admission barrier | Broker Server 控制面/数据面边界 | `passing` | 28 个聚焦测试、Broker Server 全模块、startup、architecture、drift、behavior 通过 | 依赖 T3 |
| T5 | SDK 直达路由与确定错误/重试语义 | Broker SDK、调用方 | `passing` | SDK/Broker 全模块、计划聚焦命令、behavior、architecture 通过 | 依赖 T4 |
| T6 | Gateway routeRevision 与分片快照 | WS Gateway、Broker | `passing` | 计划聚焦测试、Bolt Plugin、Broker/Gateway 全模块、behavior、architecture 通过 | 依赖 T5 |
| T7 | DRAINING、RouteLookup 与幂等下行 | Broker/Gateway 实时数据面 | `passing` | 聚焦测试、Broker 全模块、behavior、E2E、architecture、drift 通过 | 真实多实例故障留 T9 |
| T8 | 完成 im-ops 改名与受控管理 | Management、IAM/Audit、部署 | `passing` | rename fixture、管理授权/Leader retry/dry-run、Ops 全模块、UI build、architecture 通过 | 依赖 T7 |
| T9 | 建立多实例 routing-ha 门禁 | im-test、scripts、Compose | `passing` | Harness fixture、routing-ha 25/25、E2E 与 cleanup 通过 | - |
| T10 | 同 revision 集成、容量与机器配置收口 | 全仓、文档、报告 | `blocked` | 已完成同 revision 集成；缺少生产候选 3/5 voter 环境与 capacity driver | - |
| T11 | 收敛 Broker Server 分层并固化模块结构规范 | Broker Server、Architecture Test、工程规范 | `passing` | clean 后 Broker Server 181 个测试、56 个 Architecture Test（2 skipped）、behavior 30 个场景与 quick 通过；peer authorization 与构造契约聚焦测试、architecture、quick 通过 | T10 的生产容量阻塞不影响本任务 |
| T12 | 用环绕责任链统一 Broker Bolt 准入并强化路由领域模型 | Broker SDK/Server、Gossip Plugin、Architecture Test、工程规范 | `passing` | access profile、责任链、身份/路由 Interceptor、连接路由聚合及聚焦测试完成；architecture、30 个 behavior 场景、quick 通过 | T10 的生产容量阻塞不影响本任务 |
| T13 | 将 Broker Server 核心升级为完整 DDD 聚合 | Broker Server domain/application/infrastructure、SDK 投影、Architecture Test、文档 | `active` | Broker 全模块、Architecture、30 个 behavior 场景、quick、clean、重命名后 routing-ha 聚焦测试与 Harness 自测通过；待真实 routing-ha/full 刷新 | Docker daemon 当前无响应；T10 的生产容量阻塞不影响本任务 |

## 详细任务

### T1 通过 Spike 锁定成熟 Raft 库

输入与边界：只验证库选型和最小运行能力，不提前实现业务状态机；SOFAJRaft 为首选，只有批准设计列出的 JDK 21、维护性、快照/成员变更或测试可控性退出条件成立时才试 Ratis，并记录可复现证据。

文件范围：根 `pom.xml`、`im-broker/im-broker-server/pom.xml`、`im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/cluster/raft/**`、必要的 `docs/design-docs/2026-09-19-broker-routing-consensus-design.md` 选型结论。

- [x] 新增 `RaftLibrarySpikeTest`，固定临时独立数据目录和随机可用端口，覆盖单节点 bootstrap、提交/read-index、snapshot/restart、三节点选举、Leader 故障，以及 learner 加入、追平、promote、demote 与移除；先运行并确认因尚未引入实现失败。
- [x] 在根 dependency management 与 Broker Server 中引入单一候选库，写最小测试适配器；不让 Raft 类型泄漏到 domain 或 SDK。
- [x] 新增 `RaftBoltCoexistenceSpikeTest` 和最小 Spring Boot lifecycle fixture，证明 Raft 与现有业务 Bolt 同进程启动、请求、停止时端口、线程、序列化、日志、异常映射和 shutdown 无冲突，并验证 Raft Metrics 可接入现有 MeterRegistry。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -DskipTests install && mvn -q -pl im-broker/im-broker-server -Dtest=RaftLibrarySpikeTest,RaftBoltCoexistenceSpikeTest test`，记录库版本、用例、耗时、线程/端口/目录清理结果。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -DskipTests dependency:tree` 和 `mvn -q -pl im-broker/im-broker-server -DskipTests enforcer:enforce`，把 Bolt、Protobuf、Netty、RocksDB JNI、JNA、Disruptor 和 Metrics 的选定版本及 convergence 结果写入 Spike 证据。JRaft 新增依赖已收敛；全模块 Enforcer 剩余四组既有基线冲突已在设计附录 C 归因。
- [x] 在本地 macOS 与 CI/生产目标 Linux 架构分别执行 RocksDB JNI load + snapshot/restart fixture；已覆盖 macOS/AArch64、Docker Linux/ARM64 与 Docker Linux/AMD64。
- [x] 若首选失败，先固化退出证据再用相同测试替换为 Ratis；本轮首选通过，回退条件未触发，生产依赖中只有 SOFAJRaft。
- [x] 把最终库、版本、限制和回退结论更新到设计的附录 C；运行 `./scripts/verify.sh architecture` 与 `./scripts/check-drift.sh`。

完成条件：一个成熟库在 JDK 21/Spring Boot 3.5、本地 macOS 和目标 Linux 下通过全部 Spike；与业务 Bolt 共存，依赖 convergence、生命周期、Metrics、日志和异常接入均有证据；测试结束没有存活线程、端口或临时目录泄漏。

### T2 固化 cluster state 与 RouteBroker 选择契约

输入与边界：实现 `BrokerClusterState`、`epoch`、`revision`、membership、`BrokerRoutingState`、endpoint 与等权拓扑预留字段；支持启动时选择 `RENDEZVOUS_HASH` 或 `MODULO_HASH`，建群后不可修改。

文件范围：SDK 新增 `cluster/BrokerClusterState.java`、`BrokerNode.java`、`BrokerMembershipRole.java`、`BrokerRoutingState.java`、`BrokerSelectionAlgorithm.java`，`routing/RouteBrokerSelector.java`、`RendezvousHashRouteBrokerSelector.java`、`ModuloHashRouteBrokerSelector.java`；Server 新增 `domain/cluster/BrokerClusterStatePolicy.java`；对应测试 `BrokerClusterStateTest`、`RouteBrokerSelectorContractTest`、`BrokerClusterStatePolicyTest`；Broker SDK/Server README。

- [x] 新增 `RouteBrokerSelectorContractTest`，固定 golden vector、输入顺序、10 万 userId 分布和增删节点集合；首次 RED 必须因 selector/算法尚不存在或结果不符固定向量失败。
- [x] 新增 `BrokerClusterStateTest` 与 `BrokerClusterStatePolicyTest`；首次 RED 必须证明空 ACTIVE 集合、重复 brokerId、未知算法、非法 membership/routing 组合未被拒绝，或 epoch/revision 没按规则递增。
- [x] 实现上述 transport-neutral model、校验器、selector 和两种算法；统一 userId 编码与无符号得分比较，不引入在线算法切换入口。
- [x] 实现 `BrokerClusterStatePolicy`：选择输入变化递增 epoch，任何内容变化递增 revision，端点变更必须新 brokerId，非法跨状态变更拒绝。
- [x] 运行 `mvn -q -pl im-broker/im-broker-sdk,im-broker/im-broker-server -am -DskipTests install && mvn -q -pl im-broker/im-broker-sdk -Dtest=BrokerClusterStateTest,RouteBrokerSelectorContractTest test && mvn -q -pl im-broker/im-broker-server -Dtest=BrokerClusterStatePolicyTest test`，再运行 `./scripts/verify.sh architecture`。
- [x] 更新 SDK/Server README 的配置、稳定身份、算法与错误输入说明。

完成条件：SDK 与 Server 对全部 golden vector 选择相同 `RouteBroker`；状态不变量由纯确定性测试覆盖。

### T3 实现 Raft 状态机、成员变更与 operation

输入与边界：Raft 只提交低频权威 `BrokerClusterState` 和 operation；Gossip 继续承载 observation，不得直接改变权威状态。自动发现只进入 `DISCOVERED`，join/promote/demote/remove/activate/drain 由显式 API 串行提交。

文件范围：新增权威集群共识端口、`application/service/cluster/BrokerClusterOperationService.java`、SOFAJRaft Adapter、`RaftBrokerClusterStateMachine.java`、`BrokerClusterSnapshotCodec.java`、`infrastructure/config/properties/BrokerRaftProperties.java`；对应 `RaftBrokerClusterConsensusTest`、`BrokerClusterOperationServiceTest`、内部身份授权测试；`application.yml`。

- [x] 新增 `RaftBrokerClusterConsensusTest`，首次 RED 必须因共识 Adapter 尚未实现而失败；覆盖单/三 voter、少数派不能提交、snapshot/restart 和 learner 追平/promote/demote/remove。
- [x] 新增 `BrokerClusterOperationServiceTest`，首次 RED 必须证明并发成员变更、危险 quorum 操作或重复 operationId 未被串行拒绝/幂等恢复；覆盖 Leader 切换后阶段只前进。
- [x] 实现 repository、Raft command/state-machine、snapshot codec、稳定 node identity/data-dir 校验和 Leader hint；Domain/Application 不依赖具体 Raft 类型。
- [x] 实现 `DISCOVERED -> LEARNER_SYNCING -> READY` 与 voter/learner 显式变更、operation 阶段和查询；自动摘流只接受满足窗口/多数规则的 observation。
- [x] 新增 `BrokerPeerAuthorizationTest`，首次 RED 必须因伪造 peer 或无效 join/observation 被接受而失败；实现应用层 brokerId/clusterId、一次性 learner join token、reporter 身份和 operation scope 校验。SOFAJRaft 原生端口由部署 mTLS 代理绑定工作负载身份，真实证书拒绝场景纳入 T9 多实例门禁。
- [x] 运行 Broker 共识、集群操作与内部身份授权聚焦测试和 `./scripts/verify.sh behavior`；确认少数派不能提交但进程和上行业务仍可运行。
- [x] 更新 Broker Architecture/README、`docs/RELIABILITY.md` 与配置说明，记录数据目录备份、多数派丢失和强制 bootstrap 边界。

完成条件：状态机在选举、重启和重复 operation 下只前进不回滚，成员变更不会静默破坏 quorum。

### T4 实现 Lease、fence 与 epoch admission barrier

输入与边界：Lease 是新权威副作用的本地准入许可，不承诺已准入远端 RPC 的完成时间不跨 epoch；跨边界完成由稳定业务标识和接收端幂等处理。

文件范围：新增并最终归入 `domain/authority/{model,service,repository}` 的 Lease、fence、local admission 和领域 seam；新增 `application/port/routing/lease/*`、`infrastructure/raft/RaftRoutingAuthorityLeaseCoordinator.java`；对应 `RaftRoutingAuthorityLeaseCoordinatorTest`、`RoutingEpochFenceTest`、`LocalRoutingLeaseServiceTest`；接入 Broker handlers/clients。

- [x] 新增 `RaftRoutingAuthorityLeaseCoordinatorTest`，首次 RED 必须因失去多数派仍可 grant/续签或回退 term/sequence 未拒绝而失败；覆盖当前 term ReadIndex、请求起点 deadline、nonce、持久 fence marker 和 holder 冻结。
- [x] 新增 `RoutingEpochFenceTest` 与 `LocalRoutingLeaseServiceTest`，首次 RED 必须因 fence 与最后一刻 side effect 可同时通过 admission 而失败；覆盖继续任职、换届、重启、STW/时钟漂移、ACK 丢失和完整 quarantine。
- [x] 实现 `RoutingEpochFence` deep module、Raft Lease coordinator 与 `LocalRoutingLeaseService.executeOrElse(epoch, sideEffect, fencedValue)` 线性化 permit；数据面不能直接申请/续签 Lease。
- [x] 实现先提交 ACTIVE 新 epoch、再授权 Lease 的 `AUTHORIZING_NEW_EPOCH` 阶段，禁止 future-epoch pregrant；fence marker 持久化后才允许 ACK/提前结束。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -DskipTests install && mvn -q -pl im-broker/im-broker-server -Dtest=RaftRoutingAuthorityLeaseCoordinatorTest,RoutingEpochFenceTest,LocalRoutingLeaseServiceTest -Dsurefire.rerunFailingTestsCount=10 test` 和 `./scripts/verify.sh behavior`。
- [x] 更新 Broker Architecture、`docs/RELIABILITY.md` 和指标/告警说明。

完成条件：所有故障路径证明 admission 不重叠；测试明确保留跨 epoch 完成的 at-least-once/幂等语义。

### T5 SDK 直达路由与确定错误/重试语义

输入与边界：Nacos 只 bootstrap；SDK 缓存 committed state 后直接选择 `RouteBroker`。`ROUTE_MOVED`、`BROKER_STATE_STALE`、`ROUTE_MISMATCH`、`ROUTING_FENCED` 必须在副作用前返回；timeout 不盲目换节点重放。

文件范围：修改 SDK `BrokerClient.java`、`BrokerRefresher.java`，新增 `cluster/BrokerClusterStateCache.java`、`routing/RouteRequestContext.java`、`RoutableBrokerRequest.java` 与显式 route result/status；Broker Server 新增 `application/policy/routing/RouteRequestValidator.java` 并修改 connection/frame handlers；对应 `BrokerClusterStateCacheTest`、`BrokerClientRoutingTest`、`RouteRequestValidatorTest`；Message `MessagePushService.java`/`BrokerMessageNotifier.java` 与 Account `SessionConnectionAdapter.java` 及其同名测试。

- [x] 新增 `BrokerClusterStateCacheTest`，首次 RED 必须因低 revision 或同 revision 不同内容被安装而失败；覆盖无缓存 bootstrap 和原子替换。
- [x] 新增 `BrokerClientRoutingTest`，首次 RED 必须因 SDK 仍先访问任意 Broker、重复重定向或 timeout 换节点重放而失败；覆盖四类拒绝、一次定向重试、持续 mismatch 告警和原 deadline。
- [x] 扩展 contract 与 `BrokerClient`，通过 cache/selector 直达；重试沿用相同 deliveryId/operationId。
- [x] 新增 `RouteRequestValidatorTest`，首次 RED 必须因错误 epoch/RouteBroker 已产生副作用或比较后 fence 仍执行而失败；实现先比较再 Guard 的 Handler 顺序。
- [x] 更新 Message、Account 和其他生产者，按各自唯一性域稳定生成业务标识；计划规定的聚焦安装与五段测试命令通过。
- [x] 运行 `./scripts/verify.sh behavior` 和 `./scripts/verify.sh architecture`；更新三个 Broker 开发者文档和调用方说明。

完成条件：正常路径无二次 Broker 转发；明确拒绝只重试一次；不确定执行结果不会被 SDK 无条件重放。

### T6 Gateway routeRevision 与分片快照

输入与边界：Gateway 每进程生成新 gatewayId；routeRevision 单调递增。完整快照按 `RouteBroker` 分片、分块并带 checksum，只有完整批次原子应用；增量、快照和 tombstone 共享 revision 水位。

文件范围：Gateway SDK 新增 `GatewayRouteSnapshotParams.java`/chunk DTO；Gateway Server 新增 `routing/GatewayRouteSnapshotCoordinator.java` 并修改 `RegistrationLifecycle.java`、`ConnectionRegistry.java`；Broker Server 新增 `application/service/routing/snapshot/GatewayRouteSnapshotService.java`、修改 connection handlers/registry；对应 `GatewayRouteSnapshotCoordinatorTest`、`GatewayRouteSnapshotServiceTest`、`ConnectionRouteRevisionTest`、`GatewayRouteAuthorizationTest`。

- [x] 新增 `GatewayRouteSnapshotCoordinatorTest`，首次 RED 必须因完整用户仍广播给所有 Broker 或无界排队而失败；覆盖分组、定向空分片、epoch 重发、jitter 和并发上限。
- [x] 新增 `GatewayRouteSnapshotServiceTest` 与 `ConnectionRouteRevisionTest`，首次 RED 必须因缺块/错误 checksum 被应用或低 revision/迟到 register 复活路由而失败；覆盖批次超时、tombstone GC 和新 gatewayId。
- [x] 扩展 Gateway/Broker contract，实现 routeRevision 与分块快照；旧完整广播路径一次性删除，不保留双写。Gateway 实现有界 coordinator，Broker 实现暂存、背压、校验和原子 apply。
- [x] 新增 `GatewayRouteAuthorizationTest`，首次 RED 必须因另一实例身份、伪造 gatewayId 或越权快照被接受而失败；实现 mTLS 身份绑定、route-write audience/scope 和 deadline 校验。
- [x] 实现 gatewayId 租约过期后的路由与 revision 水位回收；Gateway 身份必须与注册/快照中的 gatewayId 绑定。
- [x] 运行 `mvn -q -pl im-gateway/im-ws-gateway/im-ws-gateway-server,im-broker/im-broker-server -am -DskipTests install && mvn -q -pl im-gateway/im-ws-gateway/im-ws-gateway-server -Dtest=GatewayRouteSnapshotCoordinatorTest test && mvn -q -pl im-broker/im-broker-server -Dtest=GatewayRouteSnapshotServiceTest,ConnectionRouteRevisionTest,GatewayRouteAuthorizationTest test`、`./scripts/verify.sh behavior` 和 `./scripts/verify.sh architecture`。
- [x] 更新 Gateway/Broker Architecture 与 README，删除旧广播描述。

完成条件：乱序、重放、缺块和进程重启均不能复活旧路由或应用半份快照；队列和内存有明确上界。

### T7 DRAINING、RouteLookup 与幂等下行

输入与边界：当前权威 `RouteBroker` 是唯一 Frame 执行者；Lookup responder 只返回路由描述，不转发业务 Frame。先查定向候选，再对可查询集合并行查询一次；不得把广播 Frame 作为兜底。

文件范围：Broker Server 新增 `application/port/registry/connection/ConnectionRegistry.java`、`application/service/routing/drain/DrainCleanupExecutor.java`、`application/service/routing/lookup/RouteLookupService.java`、`application/service/routing/delivery/FrameDeliveryService.java`，修改 Broker peer 与 Gateway delivery adapter 和 frame/connection handlers；Gateway SDK/Server 修改 frame/close contract 并新增 `delivery/FrameDeliveryDeduplicator.java`、`ConnectionCloseDeduplicator.java`；对应 `RouteLookupServiceTest`、`DrainCleanupExecutorTest`、`FrameDeliveryServiceTest`、`BrokerDataPlaneAuthorizationTest`、`FrameDeliveryDeduplicatorTest`、`ConnectionCloseDeduplicatorTest`。

- [x] 新增 `RouteLookupServiceTest`，首次 RED 必须因正常命中仍 fanout、定向失败后广播 Frame 或 deadline 随节点数串行增长而失败；覆盖 DRAINING、retention、hopCount、负缓存、限流、部分超时和多 Gateway 聚合。
- [x] 新增 `DrainCleanupExecutorTest` 与 `FrameDeliveryServiceTest`，首次 RED 必须因缺水位仍 purge、旧 Broker 执行 Frame 或多响应重复投递而失败；实现只读 directory、drain cleanup、Lookup 描述和当前 RouteBroker 直连投递。
- [x] 新增 Gateway `FrameDeliveryDeduplicatorTest`，首次 RED 必须因相同 `deliveryId + connectionId` 重复写 Channel 而失败；实现有界去重，容量耗尽背压告警，`deliveryAttemptId` 仅观测。
- [x] 新增 `BrokerDataPlaneAuthorizationTest`，首次 RED 必须因伪造 Lookup/迁移/下行来源、错误目标或过期 deadline 被接受而失败；实现双向身份、audience/scope 与目标约束。
- [x] 新增 Gateway `ConnectionCloseDeduplicatorTest`，首次 RED 必须因延迟旧 session close 关闭新 session 而失败；实现 `operationId + userId + sessionVersion` 有界幂等，覆盖最后一刻准入、timeout、丢响应和跨 epoch 重试。
- [x] 实现 drain ACK/水位/摘要收口及 `FAILED/DEGRADED/FORCED` 结果；重复 cleanup 幂等且不能创建路由或投递 Frame。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server,im-gateway/im-ws-gateway/im-ws-gateway-server,im-service/im-message/im-message-server -am -DskipTests install && mvn -q -pl im-broker/im-broker-server -Dtest=RouteLookupServiceTest,DrainCleanupExecutorTest,FrameDeliveryServiceTest,BrokerDataPlaneAuthorizationTest test && mvn -q -pl im-gateway/im-ws-gateway/im-ws-gateway-server -Dtest=FrameDeliveryDeduplicatorTest,ConnectionCloseDeduplicatorTest test && mvn -q -pl im-service/im-message/im-message-server test`，再运行 `./scripts/verify.sh behavior`、`./scripts/verify.sh e2e`；更新 `docs/RELIABILITY.md` 与两侧 Architecture/README。

完成条件：兜底可找回 Gateway 路由但全程只由当前 RouteBroker 下行；重复和延迟完成符合明确 at-least-once/幂等契约。

### T8 完成 im-ops 改名与受控管理

输入与边界：系统未上线，执行一次性源码/模块/runtime rename，不保留 `im-monitor` 别名或迁移。Broker 验证 im-ops 服务身份的 audience/scope；管理员身份和审计由现有 im-ops/IAM/Audit 边界承担。T3/T6/T7 已在各协议首次引入时完成身份绑定，本任务只完成管理授权和全矩阵复核。

文件范围：`im-management/im-monitor` -> `im-management/im-ops`、父/根 POM；新增 `ImOpsApplication.java`、`application/BrokerClusterOperationAppService.java`、`interfaces/http/BrokerClusterOperationController.java`、管理 client/model；UI `src/api/ops.ts` 和 operation views；IAM/Audit seed、Nacos/config、Compose、scripts；Broker 只读管理入口及按路由状态、Raft 成员变更、强制处置拆分的写入口；对应 `ImOpsApplicationTest`、`BrokerClusterOperationControllerTest`、`BrokerManagementWriteControllerTest`。

- [x] 新增 `scripts/test-im-ops-rename.sh` 并先执行，RED 必须列出 runtime 源码、artifact、service、permission、config、seed 或 Compose 中残留的旧名称；历史设计路径/事实引用列入显式允许范围。
- [x] 一次性移动模块与 package，更新 artifactId、应用名、权限、Audit source、配置和部署引用；删除本地/CI allowlist 内可丢弃旧状态，不写生产迁移或兼容别名。
- [x] 新增 `BrokerClusterOperationControllerTest` 与 `BrokerManagementWriteControllerTest`；首次 RED 必须因 follower 未返回 Leader hint、非法状态仍提交或 dry-run 产生写入而失败；实现 leader-aware API、页面和后端不变量复核。
- [x] 新增/更新 `ImOpsApplicationTest` 和授权测试；首次 RED 必须因缺少 im-ops scope 或错误 audience 仍可管理写而失败；完成 IAM seed、管理授权和全服务矩阵复核。
- [x] 运行 `bash scripts/test-im-ops-rename.sh && mvn -q -pl im-management/im-ops,im-broker/im-broker-server -am -DskipTests install && mvn -q -pl im-management/im-ops -Dtest=ImOpsApplicationTest,BrokerClusterOperationControllerTest test && mvn -q -pl im-broker/im-broker-server -Dtest=BrokerManagementWriteControllerTest test && npm --prefix im-management/im-ops/ui ci && npm --prefix im-management/im-ops/ui test && npm --prefix im-management/im-ops/ui run build`，再运行 `./scripts/verify.sh architecture`。
- [x] 更新根 Architecture、Management/Broker README、`docs/SECURITY.md` 和本地启动说明。

完成条件：新名称和管理闭环可用，当前运行配置无旧名称；管理写操作可恢复、可查询、可审计且不能绕过后端不变量。

### T9 建立多实例 routing-ha 门禁

输入与边界：这是确定性功能/故障门禁，不做完整容量阶梯，也不加入普通 `quick`。拓扑至少 3 Broker + 2 Gateway，端口、volume、network 和故障注入必须由 Harness 唯一拥有并可靠清理。

文件范围：`im-test` 新增或扩展 routing HA 模块、`deploy/local` 专用 Compose、`scripts/verify.sh`、新增 `scripts/test-routing-ha-*.sh`/runner、Harness Guide、affected mapping 与证据报告。

- [x] 先新增 verify mode 与自测 fixture，确认零测试、残留进程/volume、错误退出码和超时均 fail closed；RED 覆盖 Harness 自身故障传播。
- [x] 构建 3 Broker + 2 Gateway 固定拓扑和可编程 fault controller，逐项实现设计列出的 25 类场景；异步断言用 bounded polling，不用固定 sleep 猜测收敛。
- [x] 特别验证 Gateway 优雅/异常切换、Broker drain/突然故障、Leader 选举、少数派 fence、四类拒绝、定向与全量 Lookup、READY 不自动激活、成员变更和同 revision 首次部署。
- [x] 特别验证 fence 继续任职/换届/重启，以及 Lease 最后一刻已准入 Gateway RPC 的 timeout、丢响应、延迟完成和相同 deliveryId 去重。
- [x] 特别验证延迟 close 使用相同 `operationId + userId + sessionVersion` 幂等，旧 session close 跨 epoch 到达时不能关闭已经建立的新 session。
- [x] 将 `routing-ha` 接入 `verify.sh`、affected/full/报告 freshness，执行 Harness 自测后运行 `./scripts/verify.sh routing-ha`、`./scripts/verify.sh e2e` 和 `./scripts/verify.sh cleanup`。
- [x] 更新 `docs/references/HARNESS_GUIDE.md`、`im-test` README 与故障诊断/复现命令。

完成条件：25 类场景都有可定位断言，失败会保留诊断但不遗留可变运行状态；成功 evidence 绑定当前工作树 fingerprint。

### T10 同 revision 集成、容量与机器配置收口

输入与边界：先完成功能门禁，再执行容量；不因耗时把 `load-test-smoke` 或完整容量塞进每个任务。机器配置来自三次生产候选拓扑测量，不从单机开发数据直接外推。

文件范围：全仓最终集成；`scripts/verify.sh`、新增 `scripts/routing_capacity.sh` 与 `scripts/test-routing-capacity-harness.sh`；`im-test/im-load-test/config/routing-capacity.json`、必要场景/报告 schema；`deploy/local` 资源配置；根与模块 Architecture/README；`docs/RELIABILITY.md`、`docs/SECURITY.md`、Harness Guide、本计划与 `PROGRESS.md`。

- [x] 从空开发状态重建同 revision Broker/Gateway/Message/Account/im-ops，验证 bootstrap、登录、诊断、operation dry-run 和旧 runtime 名称/协议分支为零。
- [x] 依次运行 `./scripts/verify.sh quick`、`./scripts/verify.sh architecture`、`./scripts/verify.sh behavior`、`./scripts/verify.sh routing-ha`、`./scripts/verify.sh e2e`，任何窄层失败先归因和修复。
- [x] 先为 `./scripts/verify.sh routing-capacity` 建立 fail-closed runner 和自测：零 workload、任一阶段/重复轮缺失、发压无效、采样缺失、清理失败、fingerprint/scope 漂移或报告字段缺失均失败；证据写入独立 verification JSON 并被 Harness report 读取 freshness。
- [x] 在 `routing-capacity.json` 固定连接与消息 `50/100/200/400/800/1600/3200` 七档、70% 私聊/30% 十人群聊、256B 正文、每档 1 分钟预热/5 分钟测量/1 分钟排空，以及稳态、Gateway 切换、Broker drain、Leader+RouteBroker 故障、少数派+JVM 长暂停五阶段；目标档各独立运行三次。
- [ ] 运行 `./scripts/test-routing-capacity-harness.sh` 后，在生产候选 3/5 voter 与实际 Gateway/Broker 规模执行 `./scripts/verify.sh routing-capacity`；报告连接/业务/ACK 成功率、P95/P99、重复/错投、Lookup、快照 ACK、迁移、选举/恢复、CPU/RSS/网络/磁盘/Raft fsync，并严格应用设计中的正确性、时延、70% 资源和三态判据。
- [x] 在根 README 固定 Broker、Gateway、Message、数据库、Redis、Nacos、im-ops/观测组件的规格证据字段、适用负载、扩容信号和至少 30% headroom 规则；当前所有数值因缺少生产候选证据明确标为待生产压测，不伪造规格，获得有效数据后再填写数值。
- [ ] 运行 `./scripts/verify.sh clean`、`./scripts/verify.sh full`，再按 `./scripts/verify.sh quality` 生成请求并由独立 Reviewer 审查；`Revise/Block` 必须修复并刷新受影响证据。
- [ ] 更新所有事实文档、设计实现状态、任务 checkbox 和恢复状态；全部任务 passing 后按 `docs/PLANS.md` 归档计划并执行归档后关闭审计，不再修改 tracked 文件。

完成条件：功能、故障切换、安全和同 revision 集成证据 fresh；验收范围内的角色必须有三次稳定容量证据并支撑推荐机器配置，证据不足时 T10 保持 `blocked`、不得 passing；仅明确排除在本次验收范围外的模块可标记待测。最终 full/quality 通过并具备归档条件。

### T11 收敛 Broker Server 分层并固化模块结构规范

输入与边界：采用已批准的“DDD 核心 + 技术子系统”结构。`BrokerClusterState`、membership、epoch、Lease、fence、drain 与 RouteBroker 规则保留为纯 domain；Registry、Gossip、诊断和运行态存储是技术子系统，不伪装为业务聚合。结构迁移不改变现有公开路由语义，不处理 T10 的生产容量环境。

文件范围：`docs/references/MODULE_STRUCTURE_GUIDE.md`、`docs/references/CODING_GUIDE.md`、Broker Server 的 domain/application/interfaces/infrastructure/config、Broker SDK 旧迁移契约、`LayerBoundaryTest.java`，以及对应聚焦测试和 Broker 开发者文档。

- [x] 新增模块结构规范，明确何时使用 DDD、何时使用技术分层或混合模式，给出各自目录骨架、依赖方向、命名、判定流程、渐进迁移规则和反例；从 Coding Guide 建立稳定入口。
- [x] 先扩展 `LayerBoundaryTest` 并确认 RED：Broker domain 依赖 config/support/infrastructure/Spring/plugin 时必须失败，domain 下不得放 concrete memory adapter。
- [x] 删除 `MIGRATE_CONNECTIONS`、`ConnectionMigrateHandler`、定时/事件触发的旧 modulo migration 及其 SDK DTO/参数和测试；Gossip Broker 事件只更新 observation，不再改变 RouteBroker 归属。
- [x] 将 InMemory Registry adapter 与 Gossip state store 移出 domain，更新 Bean 接线和测试；domain 只保留确有领域含义的 cluster/routing 规则与接口。
- [x] 为下行 Gateway 调用建立 application seam，把配置值在装配层转换为基础配置，把具体 Bolt client 和 Gateway SDK 参数转换留在 infrastructure adapter。
- [x] 将管理 Controller 的状态读取、校验、目标状态推导、digest 和 operation dispatch 移入 application 用例；Controller 只做 HTTP 鉴权、输入转换和输出序列化。
- [x] 拆分过宽的 `BrokerClusterOperationService`：成员变更、路由状态变更与共享提交引擎各自聚合职责，保持 operation 幂等、串行和 fence/authorize 顺序不变。
- [x] 清除 Broker `support` 兜底目录：诊断查询归 application、HTTP Response 归 interfaces、Tracker/监控归 infrastructure，内部事件拆为 application 契约与 Spring adapter；明确 `Cmd/Command` 默认属于 application，domain 只保留领域状态转换。
- [x] 运行 Broker SDK/Server 聚焦测试、`./scripts/verify.sh architecture`、`./scripts/verify.sh behavior`、`./scripts/verify.sh quick`，同步 Broker Architecture/README、计划和 `PROGRESS.md`。
- [x] 收敛 application 目录：跨层输入输出归顶层 `model`，Gossip observation 事件隐藏到 infrastructure，routing 用例按授权、Lease、快照、Lookup、投递和排空分组；新增 Architecture Test 防止 `application/**/model` 与技术事件包回流。
- [x] 将 Broker peer 授权器与 learner join token 归入 `application/policy/cluster`，access-denied 异常归入 `support.exception`，避免在 service 根包散落安全实现。
- [x] 删除 `BrokerClusterOperationService` 仅供测试的便利构造器，测试 fixture 显式提供完整生产依赖。
- [x] 将跨层可识别的 Broker peer access-denied 异常移至 `support.exception`；规范允许 `support.exception`/`support.util` 保存少量无状态、协议无关的模块辅助类型，并由 Architecture Test 禁止完整能力回流。
- [x] 将 Gateway 受信传输身份 resolver 移至 `support.security`；该包只负责安全上下文和 principal 解析，授权策略继续由 application 持有，并同步 Architecture Test 白名单。
- [x] 将 Broker 顶层 `model` 从业务优先目录调整为 CQRS、枚举和 I/O 等类型职责优先；认证后的请求
  Principal 与受信传输身份解析辅助类型归入 `support.security`，不把安全上下文伪装成业务模型；业务子目录
  只在同类模型出现多个稳定业务组且数量足够时引入，并以 Architecture Test 固化当前结构。
- [x] 固化重构即时清理规则：确认无调用方的旧类、兼容壳、占位实现和过期测试随迁移同步删除，仅允许有调用方、期限和退出条件的临时兼容文件。
- [x] 合并只有单一实现且无测试替身的 Gateway principal resolver seam，保留具体 `GatewayRoutePrincipalResolver` 并即时删除 `TlsGatewayRoutePrincipalResolver`。
- [x] 将 Broker management HTTP Response 从 `interfaces/http/management/model` 迁至类型职责优先的顶层 `model/io`，当前只有一个业务组，不增加 `io/management` 层级。
- [x] 将 Registry 已发生事实的数据模型统一迁至 `model/cqrs/event`，Spring publisher/listener 保留在 infrastructure；将误放在 `interfaces.listener` 的监听器基类迁回 `infrastructure/gossip/listener`。
- [x] 将运行态 Registry seam 从 domain 迁至 application；按 Cluster、Lease、Gateway Route、Lookup、Delivery、Drain 拆分 infrastructure 配置，将调度和进程生命周期 adapter 归入对应技术能力，并以 Architecture Test 禁止顶层 `config`/`lifecycle` 回流。
- [x] 将非安全语义的 `RoutingEpochAuthorizer` 改为 `RoutingEpochActivationService`，约束 `Authorizer` 后缀只表达访问授权；补齐遗漏的 management Request/Response 到 `model/io`。
- [x] 删除仅重复时间契约的 `BrokerEvent`，Registry 事实事件直接实现公共 `DomainEvent`，并以 Architecture Test 防止模块级空标记接口回流。
- [x] 将 Broker application 调整为类型职责优先：`service` 承载用例编排、`port` 承载外部能力 seam、`policy` 承载应用级授权与校验；测试目录同步镜像，并以 Architecture Test 禁止其他 application 根目录回流。
- [x] 删除只把语义方法映射为事件构造器的 `RegistryObservationPublisher` 浅封装；调用方直接构造 Registry 事实事件并复用公共 `DomainEventPublisher`，Spring adapter 只负责事件分发。
- [x] 删除只有单一实现和单一调用方的 `DrainOperationVerifier`/`CommittedDrainOperationVerifier`；已提交 drain operation 校验内聚到包内 `DrainCleanupExecutor`，测试通过 Repository seam 验证行为。

完成条件：旧迁移入口从契约和运行时完全消失；Broker domain 不再依赖外层或具体技术；interfaces 不承载集群状态机编排；application 不直接依赖具体 Gateway client/config；共享规范足以让后续模块用同一判据选择 DDD、非 DDD 或混合结构。

### T12 用环绕责任链统一 Broker Bolt 准入

输入与边界：采用设计文档 D43/D44 已批准的显式可插拔环绕责任链，不使用 Spring AOP 或 ThreadLocal。
每个 Bolt operation 强制声明 access profile；原始 `BoltRequestContext` 只在接口层 Interceptor 中可见，
普通 Handler 只接收请求，Gateway 注册只接收经过验证的 typed principal。身份授权先于路由执行许可，
`LocalRoutingLeaseService` 必须继续环绕 Handler 的真实副作用。Gateway 快照的分片完整性、epoch 与最终 LocalRoutingLeaseService
保留在快照服务中。本任务不处理 T10 的外部容量环境。

文件范围：Broker SDK 的 `BrokerBoltOperation` 与 request capability；Gossip Plugin 的通用 peer capability；
Broker Server `interfaces/handler` 的 dispatch/责任链/Interceptor、`support/security`、相关 infrastructure
装配、Gateway snapshot 服务和现有 Handler；删除被替代的 `RouteRequestValidator`、
`GatewayRouteAuthorizer`、`BrokerDataPlaneAuthorizer`、零生产调用的 `BrokerPeerAuthorizer` 及过期测试；同步
Broker Architecture/README、Coding/Module Structure Guide、LayerBoundaryTest 与本计划/`PROGRESS.md`。结构审查
追加连接路由领域化：新增 `domain/connection/**`，让 `GatewayRouteTable`/`ConnectionRoute` 拥有
revision、snapshot、tombstone、drain purge 与 GC 不变量；`InMemoryConnectionRegistry` 只保留存储、索引和
应用结果转换，并清理顶层 `model/value` 中职责不符的流程模型。

- [x] 先新增 access profile/capability 契约测试并确认 RED：覆盖全部 `BrokerBoltOperation`、Gossip Plugin
  依赖方向、Frame `INBOUND/OUTBOUND` 与 close 调用方角色；未分类 operation 或 capability 不匹配必须失败。
- [x] 实现 SDK/Plugin 的 access profile 与强类型 capability，不按 Handler 类名、包名或字符串识别；运行
  SDK/Plugin 聚焦测试确认 GREEN。
- [x] 先新增责任链内核测试并确认 RED：两个 `handle` 汇入同一不可覆盖 dispatch、受保护请求缺 context
  fail closed、Interceptor 顺序固定、未匹配不能默认放行、普通 Handler 看不到原始 context、principal-aware
  Handler 在 principal 缺失或类型错误时拒绝。
- [x] 实现 `BrokerRequestInvocation`、环绕 `BrokerRequestInterceptorChain`、普通/principal-aware Handler 终点与
  启动闭环校验；运行接口层聚焦测试确认 GREEN。
- [x] 先新增身份与执行 Interceptor 测试并确认 RED：Gateway bootstrap/租约绑定、Broker Peer/Control caller
  与 target/holder 区分、Gossip nodeId 绑定、Frame direction/close 角色校验、Route Guard 环绕真实副作用；
  快照只迁移传输身份授权。
- [x] 实现有序 Interceptor 与 peer identity -> operation allowlist 装配，迁移全部 Handler，删除显式
  `authorize`/`validate` 调用和被替代的 Authorizer/Validator；保留快照服务内部完整性、epoch 与 Guard。
- [x] 先新增 `GatewayRouteTableTest` 并确认 RED：覆盖旧注册不得复活新 tombstone、旧 snapshot 不得删除新
  revision、snapshot 覆盖与 deadline/grace 后才 GC、drain purge 不删除高水位路由以及 Gateway 间 revision
  隔离。
- [x] 实现 `domain/connection` 聚合、实体、key 和 change set；让 `InMemoryConnectionRegistry` 委托
  领域行为并只维护运行态索引，删除其私有 `RouteKey`/`RevisionState` 规则；运行领域和 Registry 聚焦测试。
- [x] 将 `RouteLookupContext` 收敛为单参数 `RouteLookupCommand`，`RouteLookupCandidate` 收窄为 Lookup 包内
  `RouteLookupTarget`；把 Lease pending 状态收回 `LocalLeaseActivationService`，删除职责混杂的
  `model/value` 包，并运行 Lookup/Lease/Delivery 聚焦测试。
- [x] Review application/infrastructure 中承载 Broker 稳定不变量的类型；只迁移本任务有测试证明且不依赖
  Spring/Bolt/Raft adapter 的规则，其他候选记录到设计文档，不做无依据的 domain 搬家。
- [x] 运行 Broker SDK/Server 与 Gossip Plugin 聚焦测试，再依次运行 `./scripts/verify.sh architecture`、
  `./scripts/verify.sh behavior`、`./scripts/verify.sh quick`；更新 Broker 文档、结构规范、计划与进度。

完成条件：全部 Broker Bolt operation 均有可验证的 fail-closed 准入分类；Gateway、Broker peer、数据面服务
与路由执行边界没有 Handler 手工授权旁路；Guard 仍包裹真实副作用；旧浅层授权/校验类和过期测试已删除。

### T13 将 Broker Server 核心升级为完整 DDD 聚合

2026-09-29 Broker Review 修复子步骤（用户已确认）：

- [x] 迁移 `BrokerClusterAppService{Change,Test,ActivationTest}`、`BoltRouteLeaseActivatorTest`、
  `RoutingLeaseLifecycleTest` 和 `support/StatefulClusterAdapterFixture`，恢复已删除用例及领域类型的测试签名。
- [x] 在 `domain/cluster/BrokerClusterTest` 与 `application/service/cluster/BrokerClusterAppServiceChangeTest`
  先复现提交后重试、FAILED 被误判提交、恢复期间新拓扑插入；RED 36 项中 13 failures/1 error，
  包含新测试 deadline 精度差异，已归因并修正。
- [x] 修复 `domain/cluster/BrokerCluster.java`、`application/service/cluster/Broker{Cluster,Membership}AppService.java`
  的幂等顺序和恢复中冲突；运行
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterTest,BrokerClusterAppServiceChangeTest,BrokerClusterAppServiceTest,BrokerClusterAppServiceActivationTest,BoltRouteLeaseActivatorTest,RoutingLeaseLifecycleTest,BrokerClusterAdapterTest,BrokerClusterCommandCodecTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- [ ] 在 `BrokerMembershipAppService`、`BrokerClusterAdapter`、membership lifecycle 及对应测试中处理
  明确失败、未知提交结果与 Leader 换届恢复；不能把 timeout 当成确定失败。
  单步明确 REJECTED -> FAILED、两步 REJECTED/UNAVAILABLE -> PENDING 与成功 -> SUCCEEDED
  已通过聚焦测试；
  周期恢复实现已经存在；2026-09-30 补齐快照恢复后经真实 AppService 完成成员操作的测试，
  单独的真实 Raft 换届/阶段恢复测试已通过。真实换届与定时恢复串联的全链路证据仍待补。
- [x] 在 cluster 模型、Raft snapshot/codec、`BrokerRaftProperties`、membership 用例和对应测试中
  持久化 brokerId/endpoint 绑定，补真实 learner catch-up 证据。2026-09-30 的快照恢复测试
  验证绑定保留，真实 learner 测试确认 appliedReadIndex >= Leader quorum ReadIndex，
  Broker clean test 全模块通过。
- [ ] 运行 Broker 全模块测试和 architecture、behavior、quick、clean、full；前序失败须先归因。
  Broker 全模块 `mvn -q -pl im-broker/im-broker-server -am test` 通过；完整 architecture 首跑因
  `model.cqrs.query`/`model.properties` 分类和管理拦截器反向依赖失败，修复后
  `mvn -q -pl im-test/im-architecture-test -am -Dtest=LayerBoundaryTest,ModuleBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`
  通过；完整 architecture 在重跑时因工作树变化被 Harness 判为证据无效；`quick` 随后通过并覆盖
  完整 architecture。`behavior` 30 场景、`clean` 通过；`full` 在资源采样节奏 Harness 自测因宿主负载
  偶发超出测试区间而提前失败，尚未进入 E2E/routing-ha，保持未完成。

2026-09-29 Raft 插件子步骤，事实源：[Raft 通用运行能力抽取](../../design-docs/2026-09-29-raft-plugin-design.md)。

- [x] 创建 `im-plugin/im-raft/{pom.xml,README.md,src/main/java/com/co/kc/imchat/plugin/raft/**}`，
  迁移 Node 生命周期、ReadIndex、单步 membership 和快照文件机制。
- [x] 修改 `im-plugin/pom.xml`、根 `pom.xml`、Broker Server `pom.xml` 和
  `adapter/cluster/raft/BrokerClusterAdapter.java`，Broker 通过 RaftStateHandler 提供日志/快照回调。
- [x] 运行 `mvn -q -pl im-plugin/im-raft -am -Dtest=RaftRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test`：5 项通过。
- [x] 运行 `mvn -q -pl im-plugin/im-raft,im-broker/im-broker-server -am -DskipTests compile`：通过。
- [x] 运行 `mvn -q -pl im-test/im-architecture-test -Dtest=ModuleBoundaryTest test`：12 项通过，
  包括插件契约依赖隔离；完整 architecture 流程仍保留环境阻断记录。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAdapterTest,BrokerClusterCommandCodecTest,BrokerClusterTest -Dsurefire.failIfNoSpecifiedTests=false test`：
  旧签名阻断已修复；2026-09-30 全模块 clean test 刷新覆盖上述三类 47 个测试，均通过。
- [ ] 运行 `./scripts/verify.sh architecture`、`./scripts/verify.sh behavior`、`./scripts/verify.sh clean`、
  `./scripts/verify.sh full`，独立 Review 后再判断 T13 是否可收口。
  本轮 architecture 与 quick 因 `No space left on device` 失败，分别发生在 JaCoCo 报告生成和 Harness
  临时状态写入；未继续运行 behavior/full，恢复环境并修复前序测试签名后重跑。

输入与边界：执行设计 D46。最终不保留 Server domain 对 SDK 的依赖、旧 `BrokerClusterStatePolicy`、万能
`BrokerClusterTransition` 或把 Leader/异步提交混入 Repository 的结构。领域直接按 `cluster`、`authority`
和 `connection` 三个平级能力组织；领域服务统一使用 `Service` 后缀。Raft、Bolt、Gossip、超时与并发编排仍是
application/infrastructure，不为目录形式强行领域化。本任务不执行 T10 的生产容量测试。

文件范围：新增或迁移 `domain/cluster/**`、
`domain/authority/**`、`domain/connection/**`；新增
`application/port/cluster/**` 和领域到 SDK 的 transformer；修改 Raft state machine/repository、cluster/routing
application services、配置与测试；删除被替代旧类；更新 Architecture Test、Broker Architecture/README、
模块结构规范、本计划和 `PROGRESS.md`。

- [x] 先新增 `BrokerClusterTest` 并确认 RED：覆盖 topology/traffic state 演进、revision/epoch、operation
  幂等与 phase、drain 完成/强制、fence term 恢复和有界历史；新增领域值对象非法输入测试。
- [x] 实现 `domain/cluster/BrokerCluster` 聚合及 Server 自有 Broker 节点、身份、版本、状态和值对象；
  将 `BrokerClusterStatePolicy` 与 Raft state machine 中的领域判断收回聚合。
- [x] 先用 sealed 变更族替代 nullable `BrokerClusterTransition` 以验证状态机边界；随后将可序列化输入迁至
  `model/cqrs/command` 的共识 Command，并删除 domain 变更族；
  领域结果保证状态和值组合合法，不携带 Leader hint、`CompletionStage` 或 `Throwable`。
- [x] 让 `RaftBrokerClusterStateMachine` 只委托聚合并生成/恢复 snapshot；新增 transformer 生成 SDK
  `BrokerClusterState` 投影，迁移所有 Server 调用方后禁止 domain 依赖 `broker.sdk`。
- [x] 将异步提交、Leader 判断与 membership 技术操作迁至 `application/port/cluster`；建立纯聚合生命周期
  Repository，删除 fail-open 默认实现和名为 Repository 的命令总线职责。
- [ ] 按最终分层移除单实现的 cluster 共识 Port：SOFAJRaft 实现迁入 `adapter/cluster/raft`，application
  service 直接编排具体 Adapter；逐个迁移调用方和测试后删除兼容接口。
- [ ] 对齐 `im-service` 的应用服务形状：`BrokerClusterAdapter` 最终只管理 BrokerCluster 生命周期，
  membership、authority 与 fence 技术能力拆回具体 Adapter；AppService 不再接触 SDK 拓扑、Raft Command、
  Codec 或共识结果类型。当前仅完成 activate 领域行为切片，其他管理动作和兼容接口尚待迁移。
- [x] 将路由领域能力归入 `domain/authority` 与 `domain/connection`，不保留 `routing` 和
  `model|service|repository` 技术分类目录；把
  `BrokerFailureObservationPolicy`、`LocalRoutingLeaseManager` 等领域服务改为语义明确的 `*Service`，同步调用方。
- [x] 先新增同 revision snapshot 冲突/幂等 RED 测试，再让 `GatewayRouteTable` 持有 snapshot watermark 与摘要，
  保证相同 revision 不复活 tombstone、不重置 GC 时间。
- [x] 删除旧 domain 平铺类型和兼容壳；新增 Architecture Test：domain 禁止依赖 SDK/外层，领域服务后缀、
  共识命令按 type-first 规则归 `model/cqrs/command`、领域对象在 cluster 能力域共置，并同步 Broker 文档与模块结构规范。
- [x] 收敛 `application/service/cluster`：将管理命令校验、成员变更、路由状态变更、串行提交、epoch fence
  和新 epoch Lease 激活并入 `BrokerClusterAppService`；将 drain 与故障恢复入口分别改为
  `BrokerDrainAppService`、`BrokerFailureRecoveryAppService`，删除只为内部转发存在的 Engine、Coordinator、
  单实现 seam 和旧 `*Service`。删除应用层通用 `execute(...)` 与动态 HTTP `/{action}` 入口；应用服务直接
  暴露具名管理用例，HTTP 按路由状态、Raft 成员变更和强制处置拆分入口类，每条固定 URL 显式调用对应用例，
  各用例使用只携带自身参数的具名 Command，删除重复表达操作类型的 `BrokerManagementAction`；共享 Command
  接口只声明公共边界字段。各用例完整拥有专属校验与提交流程，不拆成重复的 validate/submit switch。
- [x] 收敛 `application/service/routing`：去掉 `delivery|drain|lease|lookup|snapshot` 细包，将下行 Lookup、
  drain cleanup、holder Lease 激活和 Gateway snapshot/ACK 分别并入对应 `*AppService`；迁移共享 Lookup
  目标模型，删除仅供单一生产实现或测试替身使用的接口与执行器。
- [x] 同步所有 Handler、Lifecycle、Configuration、测试和 Broker 文档；先运行重命名后的 cluster/routing
  聚焦测试，再运行 `mvn -q -pl im-broker/im-broker-server -am test` 与
  `./scripts/verify.sh architecture`，确认公开 application 用例入口统一为 `*AppService` 且行为不变。
- [x] 先新增聚合 RED 测试：没有已提交 fence 不得改变 ACTIVE 集合，operation 类型、目标 Broker 与拓扑
  diff 必须一致；operation 创建、fence 和 topology commit 可在 Leader 换届后幂等恢复。
- [x] 将 fence 值对象归还 `domain.cluster`，增加 operation 开始共识命令，让聚合按
  `FENCING_OLD_EPOCH -> RAFT_COMMITTED` 闭环推进；同步 Raft codec、AppService 和恢复测试。
- [x] 删除 domain 下 `model/change/result/repository/service` 技术分类目录；聚合、实体、值对象、Repository
  与领域服务直接归属 `domain.cluster`。将可序列化 Raft 输入改为 `model/cqrs/command` 的封闭
  `*Command`，由 consensus port 接收、state machine 翻译为具名聚合行为。
- [x] 用 `ClusterId`、`BrokerId` 收紧故障 observation，补齐 operation、drain update 与领域决策的非法
  状态约束；将幂等摘要收敛为 `OperationRequestDigest` 值对象；用显式 phase 转移替换 ordinal，并删除确认
  无调用的兼容枚举值。
- [x] 将 `BrokerClusterDomainTransformer` 改成 MapStruct 声明式 Transformer，检查并修正整个 Broker
  transformer 包中不实 `@Mapper`、手写字段映射和归属；运行生成代码与聚焦转换测试。
- [x] 按领域构造规范复核 cluster 值对象与实体：领域属性统一使用 `AssertUtils.domainProp*`，普通长参数创建
  使用手写 Builder 并由 Builder 区分“未设置”和合法基本类型值；`BrokerCluster` Builder 直接创建私有空聚合并
  在 `build()` 完成恢复校验，不保留仅转发 Builder 参数的长构造器；将误放在 `model/identity` 的请求 Principal
  迁至 `support.security`，同步模块结构规范与开发者 README。Broker 领域、责任链聚焦测试和全模块测试通过。
- [x] 收紧 routing 子域的领域语义：authority 的 cluster/broker/operation/epoch/nonce 使用值对象，Lease、
  fence 与 quarantine 使用 `Duration`，Guard 和 grant-fence 端口不再暴露裸 epoch；connection 聚合使用
  `Instant`/`Duration` 表达 deadline、tombstone 时间与 GC grace，只在 Registry adapter 转换 epoch millis。
  领域结果补齐字段注释、状态组合校验与 Broker 身份类型；`mvn -q -pl im-broker/im-broker-server -am clean test`
  及 Architecture Test 通过。
- [x] 在 `domain/connection/{GatewayRouteTable,ConnectionRoute,ConnectionRouteId,ConnectionRouteChangeSet,GatewayId,UserId,RouteRevision,SnapshotContentDigest,GatewayRouteSnapshotCheckpoint}.java`
  用 `GatewayId/UserId/RouteRevision` 代替聚合内裸标识和修订号，用快照绑定代替 `-1/null` 组合状态；
  `infrastructure/registry/connection/InMemoryConnectionRegistry.java` 和路由投递/Lookup 出域处负责基本类型转换，
  同步领域测试、Broker README 与本设计文档。先运行
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=GatewayRouteTableTest,ConnectionRouteValueObjectTest,InMemoryConnectionRegistryTest,ConnectionRouteRevisionTest,FrameDeliveryAppServiceTest,RouteLookupHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`，结果通过。
- [x] 在 `domain/connection/{GatewayRouteTable,ConnectionRoute}.java` 让聚合写入与回收各自读取一次
  `Instant.now()`，实体统一判断旧版/同版与清理水位；在
  `application/port/registry/connection/ConnectionRegistry.java`、
  `infrastructure/registry/connection/InMemoryConnectionRegistry.java`、连接 Handler、快照/排空用例、
  Gateway 清理任务与对应测试中移除仅用于透传的 `nowEpochMillis`，保留 SDK deadline 协议。
  `GatewayRouteTableTest` 与 `ConnectionRouteRevisionTest` 将时间回收判定拆到实体固定时间测试，
  其余路由/快照/清理聚焦测试通过；Broker 全模块测试中两项既有管理 Controller 测试因缺少
  `BrokerManagementProperties` Bean 报错。
- [x] 将完整快照方法改为领域语义 `reconcileSnapshot`，删除无生产调用的 `activeRoutes()`；
  快照摘要计算保留为聚合私有行为，并把“元素数量 + big-endian long 序列”的 SHA-256 编码统一到
  `im-common/.../HashUtils.sha256HexLongs`，SDK `GatewayRouteSnapshotChecksum` 与 Broker 聚合复用同一实现。
  Common Hash、SDK checksum、Broker 聚合/Registry/快照聚焦测试通过。
- [x] 将共识 Command 从 consensus port 子目录并入 type-first 的 `model/cqrs/command`；墙上时间依赖由
  `LongSupplier clock` 统一替换为 JDK `Clock`，保留租约所需且明确命名的 monotonic clock。时间边界聚焦测试、
  Broker 全模块测试与 Architecture Test 通过。
- [x] 删除 `BrokerCluster.restore(...)` 长参数转发工厂，统一以手写 Builder 恢复聚合并校验必填字段；
  纯字段访问器使用 Lombok fluent Getter，`operations()` 与 `currentFence()` 保留显式只读转换。聚焦测试、
  Broker 全模块测试及 Architecture Test 通过。复核其他领域静态工厂后保留表达明确状态的具名工厂；
  `ConnectionRoute` 纯字段访问器同步使用 Lombok，委托和派生领域语义仍显式保留，并补齐 Builder 必填
  capacity 未设置/非法值测试。
- [ ] 运行 Broker Server 聚焦与全模块测试，再依次运行 `./scripts/verify.sh architecture`、
  `./scripts/verify.sh behavior`、`./scripts/verify.sh quick`；最终收口前按仓库要求运行 clean/full。
  本轮 Broker 聚焦/全模块、architecture、behavior 已通过；首次 `quick` 因验证期间工作树指纹变化拒绝生成
  有效证据，改动稳定后重跑已通过。先前 `clean` 已通过；`full` 在 routing-ha 重启阶段失败。
  routing-ha 的旧测试名已同步到当前 AppService，重启就绪预算已按实测约 100 秒启动时间改为可配置的
  180 秒，聚焦测试与 Harness 自测通过；真实重跑因 Docker daemon 在 `compose up --wait` 无响应而以
  124 结束，台账保留 project `im-chat-routing-ha-22968`，待 Docker 恢复后清理并刷新 routing-ha/full。

完成条件：`BrokerCluster` 是控制面唯一聚合，Raft state machine 不再拥有领域决策；ACTIVE 集合变化只能在
已提交旧 epoch fence 后发生，operation 意图与拓扑 diff 由聚合校验；Server domain 不依赖 SDK
或外层；routing 子域和领域服务命名清晰；连接快照同 revision 语义确定；旧模型全部删除且门禁通过。

## 恢复状态

### T13 具名变更意图收敛（2026-09-30）

#### 当前方案：Begin 登记事实、Commit 修改实体

- [x] 将 fence 的最终拓扑一致性检查归 BrokerCluster.commitRoutingFence，
  补齐 ACTIVE 集合匹配，删除 BrokerClusterAppService.closeGrants 的 afterRead 重复比较。
  BrokerClusterTest 错误集合首次提交回归先 RED 再通过；BrokerClusterLeaseGrantTest
  验证无关 revision 更新不阻断提交，epoch/operation/集合最终由聚合重放校验。
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterTest,BrokerClusterLeaseGrantTest,BrokerClusterAppServiceFenceTest,BrokerClusterSnapshotIsolationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  通过，不推进收口。

- [x] 按用户批准整理租约职责：删除 adapter/cluster/lease 的两套用例实现，
  将 requestLease/closeGrants/activateLease 编排归 BrokerClusterAppService，
  prepareLease/installLease RPC 归 BrokerClusterAdapter，载荷映射归 BrokerLeaseTransformer；
  RoutingLeaseGrantService 以领域事实判断资格并同步分配任期/序列，无 SDK/RPC 依赖。
  配置和续租调用同步，原一次请求持续授权的行为与 fence/quarantine 顺序保留，无应用服务互调。
- [x] 运行 BrokerClusterLeaseGrantTest、BrokerClusterLeaseInstallationTest、
  RoutingLeaseLifecycleTest、BrokerClusterAppServiceFenceTest、BrokerClusterAppServiceActivationTest、
  BrokerClusterAppServiceTest；另运行 BrokerClusterAdapterTest、ImBrokerApplicationTest，
  均通过。LayerBoundaryTest/ModuleBoundaryTest 与 clean 通过；保留原广层阻断。
  公开安装入口改用基础类型 ActivateRoutingLeaseCommand，应用服务转换领域输入并读取聚合；
  最终 17 个签发、安装、续租、Fence、授权和应用入口测试通过，未重跑全仓 full。

- [x] 修复 RaftRoutingAuthorityLeaseCoordinator.grantAfterRead：ReadIndex 返回后重验
  当前准入，拦截异步等待期间已提交、但未改变 revision/epoch 的 fence。
  RaftRoutingAuthorityLeaseCoordinatorTest.fenceCommittedWhileReadIsPendingPreventsOldEpochGrant
  首次失败为期望 FENCED、实际 GRANTED，修复后通过并验证未消耗签发序列。
  执行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=RaftRoutingAuthorityLeaseCoordinatorTest,BrokerLeaseAdapterTest,RoutingLeaseLifecycleTest,BrokerClusterAppServiceFenceTest,BrokerClusterAppServiceActivationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  通过；其他关闭校验、广层证据和并发时序仍保留原 Review 状态。

- [x] 收敛 application/port/routing/lease 六个单实现接口；协调器依赖单个具体集群 Adapter，
  删除 currentState 转发，安装实现/测试迁至 lease/BrokerLeaseAdapter。
  quarantine 使用标准 LongFunction 异步等待，仍可确定性测试且不形成循环依赖。
- [x] 执行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=RaftRoutingAuthorityLeaseCoordinatorTest,BrokerLeaseAdapterTest,RoutingLeaseLifecycleTest,BrokerClusterAppServiceFenceTest,BrokerClusterAppServiceActivationTest,BrokerClusterAppServiceTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false test`：
  签发、安装、续租、隔离、授权与启动通过。继续保留既有广层门禁失败，不推进收口。

- [x] 按用户批准的 fence 收敛方案，将 FencePeerClient/BoltFencePeerClient 合入
  BrokerClusterAdapter.fenceBroker(BrokerId, RoutingEpochFence)。BrokerClusterConfiguration
  提供有界线程数和队列、拒绝时不在调用线程执行的 peerExecutor；拒绝提交转失败阶段，
  ACK 资格与 quarantine 仍归应用编排。RPC 模型映射归 BrokerFenceTransformer；
  不修改 im-raft、对端 RoutingFenceHandler 或持久 fence 语义。
- [x] 同步应用构造/Bean/测试替身，迁移旧客户端回归并新增异步提交、并发、拒绝与配置边界测试。
  执行 BrokerClusterAdapterTest、BrokerClusterAppServiceFenceTest、BrokerClusterAppServiceActivationTest、
  BrokerClusterAppServiceTest、BrokerClusterConfigurationTest、BrokerFenceTransformerTest，
  再执行 LayerBoundaryTest/ModuleBoundaryTest 和 clean；quick/full 的既有阻断单独保留。
  聚焦、真实 Raft/并发请求及 ImBrokerApplicationTest 通过；behavior 提权重跑通过
  30 个场景，直接 LayerBoundaryTest/ModuleBoundaryTest 通过。
  architecture 门禁首次受 ~/.m2 权限阻断，提权后仍被既有 im-iam-sdk JaCoCo EOFException 阻断；
  不用窄范围通过覆盖该失败。QuarantineWaiter 与其余 lease port 本轮仅检查，未删除。

- [x] 将 BrokerClusterAppService.authorizeEpoch 合入 authorizeCommittedEpoch，保留阶段恢复、
  持久化授权步骤和结果刷新，删除旧入口；ActivationTest 改从公开 activate 用例验证。
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAppServiceActivationTest,BrokerClusterAppServiceFenceTest,BrokerClusterAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  通过；本轮不重跑已知 IAM JaCoCo report 阻断的 quick/full。

- [x] 整理 im-broker-server/adapter/cluster：删除 BrokerConsensusMembershipClient，
  移除 BrokerClusterConsensusResult.state 与 BrokerConsensusMembershipResult.membership；
  同步接口实现、Transformer、Result 工厂、调用方和测试，不添加兼容字段或构造器。
  RaftRoutingAuthorityLeaseCoordinator 及测试由 raft 迁到 lease，保留共识与成员结果分离。
- [x] 删除仅供测试调用的 BrokerClusterAppService.closeEpoch；通过公开 activate 验证
  全部 ACK、缺少 ACK、旧任期 ACK 三种场景，等待未完成时 epoch 不变。
  运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAdapterTest,RaftRoutingAuthorityLeaseCoordinatorTest,BrokerClusterSnapshotIsolationTest,BrokerClusterConsensusResultTest,BrokerConsensusMembershipResultTest,BrokerClusterAppTransformerTest,BrokerClusterAppServiceTest,BrokerClusterAppServiceFenceTest,BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest,BrokerMembershipLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  结果通过；closeEpoch 删除后的 fence 与 Result 契约测试再次通过。
  LayerBoundaryTest/ModuleBoundaryTest 通过，不替代全模块和 full 证据。
  本轮 clean 通过，quick 首轮受 /dev/fd 沙箱权限阻断；提权重跑的 Harness 自测通过，
  随后被 im-iam-sdk JaCoCo report EOFException 阻断。不修改无关 IAM 代码。

- [x] 按成员应用规范调整 `BrokerClusterAppService`、`BrokerDrainAppService`、
  `BrokerFailureRecoveryAppService` 与 Result/Application Transformer：返回业务冲突/拒绝，
  保留历史接受 epoch，删除简单包装和 cleanup 中间对象，补类与方法注释。
- [x] 执行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAppServiceTest,BrokerClusterAppServiceFenceTest,BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest,BrokerClusterAppTransformerTest -Dsurefire.failIfNoSpecifiedTests=false test`：
  29 个测试通过。另运行 BrokerClusterAppServiceChangeTest 的拓扑拒绝、fence、drain 和
  activationCommitFailureDoesNotAuthorizeUncommittedEpoch 共 9 个回归通过。
  Commit 失败不再继续授权或读取空 operation，原样保留共识失败。
  完整共享 ChangeTest 有两项原有成员读取异常契约失败，暂不改已 Review 的 Membership 实现；
  HTTP Transformer 上轮改签名的测试调用已修正，不补兼容重载。
- [ ] 刷新 quick/full 与全模块证据；clean 通过。quick 首轮因沙箱 /dev/fd 权限失败，
  提权重跑的 Harness 自测通过，随后因 im-iam-sdk JaCoCo report EOFException 失败。
  前述两项成员异常契约须单独确认，不以本轮目标类通过代替全模块通过。

- [x] 单集群简化（事实源 2026-09-30-broker-single-cluster-identity.md）：移除 Broker domain
  的 ClusterId、SDK cluster state 与 Lease/Fence/ReadIndex 请求响应、应用 command 中的业务身份；
  技术 Raft identity、snapshot envelope 和 FileFenceWatermarkRepository 归属校验保留。
  同步 transformer、控制面 handler/interceptor、启动配置及直接调用测试；不取消角色/凭证/
  目标 Broker 身份校验。范围 im-broker/** 及直接受影响的 Gateway/架构测试调用。
- [x] 补字段移除与技术身份恢复测试，执行 `mvn -q -pl im-broker/im-broker-server -am clean test`，
  再执行分层/模块边界 Maven 回归；同步 README/ARCHITECTURE/Harness/PROGRESS 并报告迁移风险。
  Broker Server 在允许本机 socket 后全量测试通过（395 个测试）；SDK 与 im-raft 测试通过。
  `-am test` 仍被工作树既有的 im-metrics Spring 上下文测试阻断，未归因于本次 Broker 改动。

- [x] 用户恢复后新增 im-raft/client/RaftPeerClient，封装 Bolt 的应用 ReadIndex 证明查询；
  im-raft/pom.xml 显式依赖 im-bolt。BrokerClusterAdapter 替换直接 BoltRpcClient 依赖，
  泛型载荷与协议由 Broker 提供，身份/索引校验和原异步失败契约保留。没有业务模型侵入插件，
  不改 JRaft 原生 peer 通信。新增 RaftPeerClientTest 参数/响应转发及异常传播测试，
  同步插件/Server README、ARCHITECTURE、Harness、PROGRESS，替代之前未实现的记录。
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=RaftPeerClientTest,LocalBrokerTest,BrokerProgressAppServiceTest,BrokerClusterAppServiceChangeTest -Dsurefire.failIfNoSpecifiedTests=false test`
  45 个测试通过；
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=RaftRuntimeTest,BrokerClusterAdapterTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  17 个测试通过；
  `mvn -q -pl im-test/im-architecture-test -am -Dtest=LayerBoundaryTest,ModuleBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`
  31 个测试通过。不推进 T13 或全仓收口。

- [x] 统一 AppliedReadIndex 语义：queryAppliedReadIndex、GET_APPLIED_READ_INDEX/getAppliedReadIndex，
  SDK AppliedReadIndexParams/Result 及 appliedReadIndex 字段、AppliedReadIndexHandler、
  BrokerProgressAppService 同步；删除旧文件，无协议别名。新增 SDK 协议名/准入/字段测试，
  更新 SDK/Server README、ARCHITECTURE、Coding Guide/Harness/PROGRESS，明确需匹配节点版本。
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=AppliedReadIndexContractTest,LocalBrokerTest,BrokerProgressAppServiceTest,BrokerClusterAppServiceChangeTest -Dsurefire.failIfNoSpecifiedTests=false test`
  45 个测试通过；`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAdapterTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  12 个测试通过；`mvn -q -pl im-test/im-architecture-test -am -Dtest=LayerBoundaryTest,ModuleBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`
  31 个测试通过。Raft Peer Client 仅讨论未实现，不推进收口。

- [x] 实现 LocalBroker 本机能力入口，将 isLeader/leaderEndpoint/appliedReadIndex 收拢，
  BrokerClusterAdapter 以 Lombok getter 暴露同一入口，移除旧扁平访问器；同步契约、
  AppService、调度、配置及测试，不新增运行实例、状态副本或接口。远端查询统一为
  queryAppliedReadIndex(BrokerNode targetBroker)，保留协议名称，去除 learner 参数假设。
  LocalBrokerTest 增加动态角色、地址、异步状态、nonce 和错误传播验证，真实 Adapter 测试
  检查稳定的本机入口。同步 README/ARCHITECTURE/Coding Guide/Harness/PROGRESS。
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=LocalBrokerTest,LearnerProgressAppServiceTest,BrokerClusterAppServiceChangeTest,BrokerMembershipLifecycleTest,RoutingLeaseLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test`
  47 个测试通过；`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAdapterTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  12 个测试通过；`mvn -q -pl im-test/im-architecture-test -am -Dtest=LayerBoundaryTest,ModuleBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`
  31 个测试通过。不推进收口。

- [x] 删除 LearnerAppliedIndexClient，先前拆出的 LearnerProgressAdapter 也按用户后续决定删除；
  查询实现合入 BrokerClusterAdapter，并迁至 adapter/cluster；保留 raft 状态机、Codec 和快照
  原位置。同步装配、AppService、测试 fixture 与构造调用，无兼容构造器。新增真实 Adapter
  远端响应 brokerId/负索引拒绝回归；更新 README/ARCHITECTURE/Coding Guide/Harness/PROGRESS。
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAppServiceChangeTest,BrokerMembershipLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false clean test`
  40 个测试通过；`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAdapterTest,ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  12 个测试通过；`mvn -q -pl im-test/im-architecture-test -am -Dtest=LayerBoundaryTest,ModuleBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`
  31 个测试通过。本机门面仅讨论未实现，保持 Review 状态。

- [x] BrokerClusterConsensus/BrokerClusterAdapter 角色查询改为 isLocalLeader，同步成员
  AppService、membership/drain/failure/lease lifecycle、测试 Stub/Fixture 和原生 Adapter 测试。
  插件/JRaft 原方法保持不变，注释说明本机角色不替代多数派 ReadIndex 证据；同步
  README/Coding Guide/Harness/PROGRESS。验证
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAppServiceChangeTest,BrokerMembershipLifecycleTest,RoutingLeaseLifecycleTest,BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  47 个测试通过；`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test`
  10 个测试通过，不推进收口。

- [x] BrokerMembershipAppService.completeLearnerSync 改为 confirmLearner，Controller Java
  入口、恢复调用和应用测试同步；不改变 HTTP 路径、领域行为和持久化操作类型。
  BrokerMembershipLifecycleTest 补真实 AppService 与恢复快照的组件回归，BrokerClusterAdapterTest
  增加 learner appliedReadIndex 与 quorum ReadIndex 比较，补前期恢复和追赶证据缺口。
  README/PROGRESS/历史 checkbox 同步当前事实；
  `mvn -q -pl im-broker/im-broker-server -am clean test` 通过，
  `mvn -q -pl im-test/im-architecture-test -am -Dtest=LayerBoundaryTest,ModuleBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`
  31 个测试通过。不把组件恢复测试当作真实换届加调度的 E2E，广层门禁继续保持未完成。

- [x] BrokerMembershipAppService、FrameDeliveryAppService、LocalLeaseActivationAppService、
  RoutingLeaseLifecycle、FileFenceWatermarkRepository、BrokerClusterAdapter 的 UUID 字符串
  统一复用 GeneratorUtils.nextUUID，移除直接 UUID import/全限定调用；不改变标识用途。
  Coding Guide/Harness/PROGRESS 记录 32 位无横杠格式与协议依赖检查要求。
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAppServiceChangeTest,FrameDeliveryAppServiceTest,FrameDeliveryAppServiceLookupTest,FrameDeliveryAppServiceCandidateTest,LocalLeaseActivationAppServiceTest,RoutingLeaseLifecycleTest,FileFenceWatermarkRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`
  56 个测试通过；`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test`
  10 个真实 Raft 适配测试通过，不推进收口。

- [x] BrokerClusterConsensusResult 增加 notLeader/rejected 工厂，替换 BrokerMembershipAppService
  进度检查三处构造；固定非 Leader 原因内置，不同拒绝原因保留显式 message，不新增文案工厂。
  BrokerClusterConsensusResultTest 增加状态、投影、端点、原因和空操作断言；同步
  Coding Guide/Harness/README/PROGRESS。验证
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterConsensusResultTest,BrokerClusterAppServiceChangeTest,BrokerClusterAppTransformerTest -Dsurefire.failIfNoSpecifiedTests=false test`
  59 个测试通过，保持 Review 状态。

- [x] BrokerMembershipAppService.completeLearnerSync 使用 completedFuture(decision).thenCompose
  衔接新操作/历史重试，由 BrokerClusterDecision.duplicate 判断，无 newOperation/progressCheck
  中间变量；不新增领域检查接口或私有提交流程。BrokerClusterTest 分类断言和成员编排历史
  重试仅读取一次证据回归增强；README、Coding Guide、Harness、PROGRESS 同步。
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterTest,BrokerClusterAppServiceChangeTest,BrokerMembershipLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test`
  74 个测试通过，不推进收口。

- [x] 最终职责划分取代本节此前将共识映射放入结果类的决定：AddLearnerResult、
  CompleteLearnerSyncResult、ActivateBrokerResult、PromoteLearnerResult、DemoteVoterResult、
  RemoveBrokerResult 的共识转换归 BrokerClusterAppTransformer；结果类保留具名业务失败工厂，
  同步 BrokerMembershipAppService/BrokerClusterAppService，不新增映射分支。
- [x] Coding Guide、Harness、Broker README/PROGRESS 同步业务工厂与 Transformer 的边界；
  LayerBoundaryTest.brokerResultFactoriesMustNotContainMappingNames 阻止 Result 的 from*/*From，
  假冒业务命名的纯映射、参数语义保留 Review-only。71 个聚焦通过：
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=AddLearnerResultTest,CompleteLearnerSyncResultTest,BrokerManagementHttpTransformerTest,BrokerClusterAppTransformerTest,BrokerClusterAppServiceChangeTest,BrokerClusterAppServiceActivationTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [x] 统一 Broker/im-raft/Ops Java 与 Ops TS 类型的 leaderEndpoint，不保留 JSON 别名；
  同步三方 README，HTTP Broker Leader 响应与 Ops 载荷反序列化回归覆盖新字段。
  `mvn -q -pl im-broker/im-broker-server,im-management/im-ops -am -Dtest=RaftRuntimeTest,BrokerManagementWriteControllerTest,BrokerClusterOperationAppServiceTest,HttpBrokerManagementClientTest -Dsurefire.failIfNoSpecifiedTests=false test`
  16 个交叉回归通过；`npm run typecheck`（im-management/im-ops/ui）通过。
- [x] `mvn -q -pl im-test/im-architecture-test -am -Dtest=LayerBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`
  19 个测试通过。首次未用 -am 的规则单跑读取旧 Maven 产物，检出五处原 from 违规，
  以当前源码 reactor 重跑已通过。保留用户 Review 状态，不推进全仓收口门禁。

- [x] AddLearnerResult.from 参数改为 ClusterOperationId，应用服务传领域身份，工厂内部
  转换结果字段；同步 AddLearnerResultTest。验证
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=AddLearnerResultTest,BrokerClusterAppServiceChangeTest -Dsurefire.failIfNoSpecifiedTests=false test`
  48 个测试通过，不推进收口。

- [x] 共识处理结果统一由 AddLearnerResult.from 创建，替换 BrokerMembershipAppService 调用，
  删除 BrokerClusterAppTransformer.addLearnerResultFrom 及无用 import；AddLearnerResultTest
  增加六类共识状态映射验证。执行上一轮五类聚焦 Maven 命令，67 个测试通过，不推进收口。

- [x] AddLearnerResult、CompleteLearnerSyncResult 的 status 改为 BrokerClusterConsensusStatus，
  HTTP 映射保持状态名称字符串不变。固定 epoch/同步端点冲突原因内置工厂，应用层直接调用
  conflict/rejected/failure，不构造中间共识结果；同步失败保留原操作 epoch。
  同步结果测试、成员编排测试、HTTP 映射及 README/PROGRESS。验证：
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=AddLearnerResultTest,CompleteLearnerSyncResultTest,BrokerManagementHttpTransformerTest,BrokerClusterAppTransformerTest,BrokerClusterAppServiceChangeTest -Dsurefire.failIfNoSpecifiedTests=false test`
  61 个测试通过，保持 Review 状态，不推进收口。

- [x] `AddLearnerResult` 字段改为 leaderEndpoint，HTTP 映射保留现有 leaderHint 协议。
  增加 conflict/failure 具名工厂，替换 addLearner 本地冲突和领域失败构造；删除对应
  Transformer 重载，领域决定直接由结果工厂构造，不丢失 CONFLICT/REJECTED/UNAVAILABLE。
  新增 AddLearnerResultTest、BrokerManagementHttpTransformerTest，同步现有 Transformer 测试。
  验证 `mvn -q -pl im-broker/im-broker-server -am -Dtest=AddLearnerResultTest,BrokerManagementHttpTransformerTest,BrokerClusterAppTransformerTest,BrokerClusterAppServiceChangeTest -Dsurefire.failIfNoSpecifiedTests=false test`
  51 个测试通过，不推进收口。
- [x] `addLearner` 的领域拒绝直接调用 Transformer 的领域决定到应用结果转换，删除中间
  共识结果和 committedState 读取；成员编排及 Transformer 聚焦 49 个测试通过，命令：
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAppServiceChangeTest,BrokerClusterAppTransformerTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  后续 status 已改为完整共识状态枚举，不向领域枚举加入技术协议状态，不推进收口。
- [x] 成员入口九处 epoch/端点前置拒绝直接创建应用结果，不创建仅供转换的共识结果，
  不重复读取 committedState。修改 BrokerMembershipAppService 和 BrokerClusterAppServiceChangeTest；
  同步 README/PROGRESS。执行上一项的三类聚焦 Maven 命令，47 个测试通过，保持 Review 状态。

- [x] `BrokerMembershipAppService` 五个成员入口 epoch 冲突统一返回 CONFLICT，
  `addLearner` 领域拒绝返回转换后的结果，不再抛参数异常；保持既有幂等与错误传播边界。
  修复同步完成登记后 exists 判断导致跳过远端证据的问题；新增五类冲突和加入资格拒绝
  无副作用回归。Coding Guide、Harness、Broker README、PROGRESS 同步。验证：
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAppServiceChangeTest,BrokerMembershipLifecycleTest,BrokerClusterAppTransformerTest -Dsurefire.failIfNoSpecifiedTests=false test`
  共 43 个测试通过；仍处于用户 Review，不推进全仓收口。

#### 架构违规收尾

- [x] 用户 Review 后确认恢复原有 `support/context` 归属：五类 BrokerWriteAccess 及拦截器测试
  已迁回，Controller/MVC 配置引用同步；之前迁出该目录的决定被本项替代。Coding Guide、
  Broker Server README、Harness 与 PROGRESS 记录目录边界，Architecture Test 保留领域隔离，
  并新增禁止上下文调用业务实现的约束。行为不变，管理入口/上下文清理 8 个聚焦回归通过；
  `mvn -q -pl im-test/im-architecture-test -Dtest=LayerBoundaryTest test` 18 个测试通过。
  architecture 脚本测试无失败，但验证期间工作树指纹变化导致退出 1，门禁证据无效，
  不推进 T13 收口。

- [x] support/context 五类按 HTTP/配置实际职责迁移并同步 import；LearnerAppliedIndexHandler
  改为只调用 LearnerProgressAppService，BrokerClusterConfiguration 注入本机领域身份。
- [x] 独立审查提示的异步 ThreadLocal 清理缺口已修复，增加 afterConcurrentHandlingStarted，
  BrokerWriteAccessContextInterceptorTest 同步/异步清理回归通过。聚焦及 Broker clean test 全模块通过；
  clean 移除已删除旧类的字节码后 architecture 通过，不修改验证规则。
- [x] 首轮 quick 通过。routing-ha runner 和场景清单更新陈旧测试定位，增加 JDK AST 类/方法
  定位回归与并发拒绝、恢复不自动激活测试，RoutingHaScenarioManifestTest 与 Broker 聚焦通过。
  固定 25 场景及 runner 的执行证据检查不变，最终 quick/behavior/full 待刷新。
- [ ] 运行 LearnerProgressAppServiceTest、BrokerManagementAccessTest、
  BrokerClusterAppServiceChangeTest、BrokerClusterAppTransformerTest、BrokerMembershipLifecycleTest 聚焦测试；
  再运行 `./scripts/verify.sh architecture`、`quick`、`behavior`、Broker 全模块和 `clean/full`。
- [ ] 同步独立 Review 和 Harness 证据，记录实际环境/外部阻塞，不提前标记 T13 完成。

- [x] BrokerClusterOperation 构建与 JSON 重建统一毫秒时间精度，Transformer 删除人为补偿；
  BrokerClusterAppTransformerTest 覆盖同毫秒完成、早于创建拒绝及 JSON 精度。Coding Guide、
  Harness、Broker README 和 PROGRESS 同步“不做过度防御”的边界规则；七类聚焦及 Broker 全模块
  重跑通过。未刷新 architecture/quick/full，此前架构违规仍待处理。

- [x] AdvanceClusterOperationCommand 改为目标对象持有型 Builder，同步全部生产/测试构造点；
  BrokerClusterAppTransformer 映射成功命令，完成时间以具名变量表达下限。
  AdvanceClusterOperationCommandTest、BrokerClusterAppTransformerTest、BrokerClusterCommandCodecTest、
  BrokerClusterAppServiceChangeTest、BrokerDrainAppServiceTest、BrokerFailureRecoveryAppServiceTest、
  BrokerClusterAppServiceActivationTest 聚焦通过，覆盖缺时间/零值/负值、JSON 重建及生命周期回归。

- [x] BrokerClusterOperation.canFailOnRejection 接管明确成员拒绝的类型策略，应用层仍判断
  REJECTED 并提交 FAILED；BrokerChangeIntentTest 全类型分类与成员编排、恢复、Transformer 聚焦通过。

- [x] 以已审阅的 `BrokerMembershipAppService.addLearner` 为基准调整其余成员流程：新操作检查
  当前端点绑定，历史重试核对原操作身份；四类成员结果沿用操作 acceptedEpoch。同步完成先进行
  本地域判断，再读取成员身份与同步证据，读失败区分 NOT_LEADER/UNAVAILABLE；
  `BrokerCluster.advanceOperation` 将非法终态时间转换为拒绝，原操作不变。补充
  `BrokerClusterAppServiceChangeTest`、`BrokerTopologyIsolationTest` 和 `BrokerClusterAdapterTest`
  回归，38 个聚焦场景通过（含真实 Raft 拒绝后继续提交）；命令为
  `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAppServiceChangeTest,BrokerTopologyIsolationTest,BrokerClusterAdapterTest#invalidCompletionTimeDoesNotPreventLaterRaftCommands -Dsurefire.failIfNoSpecifiedTests=false test`。
  `mvn -q -pl im-broker/im-broker-server -am test` 全模块回归通过；用户继续 Review，
  本项不代表 T13 或全仓门禁收口。

- [x] BrokerMembershipAppService.handleMembershipResult 移除 singleStep 参数，四处调用统一结果处理；
  BrokerClusterAppTransformer.membershipFailureCommandFrom 承担失败命令字段和时间映射。
  BrokerClusterAppTransformerTest、BrokerClusterAppServiceChangeTest、BrokerMembershipLifecycleTest 聚焦通过，
  覆盖拒绝、未知结果、可能部分成功、失败记录不可用和默认原因/纳秒精度时间边界。

- [x] Broker Server 当前规则判断方法统一 assess -> check，端点方法缩短为 checkEndpointBinding；
  同步生产调用、测试与模块 README，不修改判断行为或返回契约。

- [x] `BrokerNode.java` 新增自身资格判断，具名转换复用相同规则；`BrokerCluster.java` 保留
  跨节点身份、端点与数量约束。`BrokerNodeTest.java`、`BrokerClusterTest.java` 及六类聚焦回归通过。

- [x] BrokerCluster 的 assessAdmission 按固定顺序组织，assessMembershipEndpointBinding 与
  assessOperationConstraints 只返回领域原因；Begin/Commit 复用规则，不新增领域服务。
  六类聚焦回归通过，补充 Begin 溢出无部分登记测试，历史容量回收之前计算 revision。

- [x] `BrokerCluster.submitOperation` 按业务顺序分段，阻塞规则迁入
  `BrokerClusterOperation.blocksNewOperation`，路由分类迁入 Proposal；构造后再预留历史容量。
  `BrokerChangeIntentTest` 全阶段回归、六类聚焦测试与 Broker 全模块重跑通过，不新增纯转发私有方法。

- [x] `domain/cluster/BrokerCluster.java` 删除准备目标拓扑逻辑，Begin 直接校验并登记必要事实，
  commitOperation 校验后修改内部 BrokerNode；`BrokerClusterOperationProposal.java` 与
  `BrokerClusterOperation.java` 删除节点列表，保存端点、最低 voter 数与恢复证据。
- [x] `model/cqrs/command/CommitBrokerOperationCommand.java`、共识 sealed command、状态机和
  `BrokerClusterRaftTransformer.java` 同步命名；codec v2、snapshot v3；迁移 Server 测试与夹具。
- [x] `BrokerClusterTest`、`BrokerChangeIntentTest`、`BrokerTopologyIsolationTest`、
  `BrokerClusterCommandCodecTest`、`BrokerClusterSnapshotIsolationTest`、`BrokerClusterAppServiceChangeTest`
  聚焦测试通过；Broker 全模块通过。补充 Commit 无部分修改、pending 新增节点重放、最低 voter 数
  跨快照与新 Leader 配置恢复、proposal.learnerJoin 分类及无额外 JSON 字段回归，重跑通过。
- [x] README/Architecture、设计增量、Harness 与 PROGRESS 同步；独立静态 Review Approved。
  `./scripts/verify.sh architecture` 仍在既有 support.context 与 LearnerAppliedIndexHandler 两项违规失败。
- [ ] 修复独立架构违规后刷新 architecture、quick、clean、full；不把本轮模块通过视作完整交付。

#### 目标副本内修改 Broker 实体

- [x] 后续收敛 `BrokerCluster.java`：目标计算只复制节点列表，移除 targetCluster 重建与
  findOwnedNode 包装，不改变 Begin/Commit 或节点隔离边界；Broker Server README 补充状态归属表和时序图。
  上述六类聚焦测试通过，Broker 全模块本轮重跑中。

- [x] 修改 `domain/cluster/BrokerNode.java` 为具名可变状态行为与 copy；
  `BrokerCluster.java` 的读接口、构造和快照隔离节点，具名入口在目标副本中修改，删除 replaceBroker。
- [x] 修改 `BrokerClusterOperationProposal.java`、`BrokerClusterOperation.java` 的拓扑接收与读取为节点深拷贝；
  同步 `BrokerNodeTest.java`、`BrokerClusterTest.java` 并新增 `BrokerTopologyIsolationTest.java`、
  `adapter/cluster/raft/BrokerClusterSnapshotIsolationTest.java` 隔离回归。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerNodeTest,BrokerClusterTest,BrokerTopologyIsolationTest,BrokerClusterCommandCodecTest,BrokerClusterSnapshotIsolationTest,BrokerClusterAppServiceChangeTest -Dsurefire.failIfNoSpecifiedTests=false test` 和 `mvn -q -pl im-broker/im-broker-server -am test`，通过；原生序列化回归补充后聚焦重跑通过。
- [x] 运行 `./scripts/verify.sh architecture`，仍在 `support.context` 包范围和
  `LearnerAppliedIndexHandler` 直接依赖基础设施配置两项既有检查失败，无本次新增失败；
  不跳过失败层继续 quick/full。
- [x] 同步 Broker README、PROGRESS、Harness 及独立增量 Review 证据；独立审查未发现本次新增问题，增量 Approved，T13 保持 active。

- [x] `domain/cluster/BrokerNode.java`、`BrokerCluster.java` 中 learner 准入改为显式谓词判断，
  保留实体转换自身的前置约束；`BrokerNodeTest.java`、`BrokerClusterTest.java` 验证各成员角色、
  拒绝结果及状态不变。运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerNodeTest,BrokerChangeIntentTest,BrokerClusterTest,BrokerClusterAppServiceChangeTest -Dsurefire.failIfNoSpecifiedTests=false test`，通过。

- [x] `domain/cluster/*Intent.java` 新增九类专属输入与窄接口；
  `BrokerClusterOperation.java` 接管重复意图匹配；`BrokerCluster.java` 和
  `BrokerClusterOperationProposal.java` 迁移入口与重放契约，不保留长参数重载。
- [x] `application/service/cluster/BrokerClusterAppService.java`、
  `BrokerMembershipAppService.java` 及聚合、应用服务、成员恢复测试同步迁移。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerChangeIntentTest,BrokerClusterTest,BrokerClusterAppServiceChangeTest,BrokerClusterCommandCodecTest,BrokerMembershipLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test`，通过。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am test`，通过；独立静态 Review 未发现
  本次重构问题，设计增量 Approved。补充排空证据和最低 voter 数量缺省回归并重跑聚焦测试通过。
- [x] 运行 `./scripts/verify.sh architecture`、`./scripts/verify.sh clean` 并记录实际结果：
  clean 通过；architecture 在未由本次修改的 `support.context` 包范围及
  `LearnerAppliedIndexHandler` 依赖基础设施配置两项检查失败。
- [ ] 修复上述独立结构问题后刷新 `architecture`、`quick`、`clean`、`full`；当前不跳过失败层继续广层门禁。
- [x] 同步 Broker Server README、既有设计增量、Harness 矩阵与 PROGRESS；T13 保持 active。

- 当前任务：T13，将 Broker Server 核心升级为完整 DDD 聚合；application service 入口与内部编排已经收敛，
  最终 `clean` 已通过，等待 Docker 环境恢复后刷新 `routing-ha/full`。
- 已完成：T1-T8 的 Raft、cluster state、Lease/fence、SDK 直达、Gateway 分片快照、DRAINING/故障恢复、RouteLookup、数据面幂等与 `im-ops` 管理闭环；T9 已完成 fail-closed Harness、3 Broker + 2 Gateway 隔离拓扑、25 类场景映射、逐 Gateway/Broker 故障恢复、E2E 与 cleanup；T10 的空状态同 revision 集成及 quick、architecture、behavior、routing-ha、E2E 分层门禁已通过；T11 已完成 Broker 混合分层、application 按 service/port/policy 类型职责优先组织、旧迁移链路删除、架构门禁和通用模块结构规范。
- 阻塞项：本机 Docker Desktop daemon 当前无响应，最新 routing-ha 在 `compose up --wait` 阶段耗尽 900 秒总预算，定向 cleanup 也在 30 秒内超时；Harness 台账保留 `im-chat-routing-ha-22968`。仓库另仅提供 `deploy/local`，缺少生产候选 3/5 voter 环境、环境专用 `ROUTING_CAPACITY_DRIVER_COMMAND`、真实部署端点与故障控制能力，无法完成三次容量测量和生产机器数值。
- 下一步：Docker 恢复后先清理 `im-chat-routing-ha-22968`，再运行 T13 最终 `routing-ha`、`clean/full` 门禁并处理结果；
  T13 收口后仍需为生产候选环境提供
  `ROUTING_CAPACITY_DRIVER_COMMAND`，恢复 T10 的容量验证。
- 保留边界：继续保留三个 Broker 文档中用户已有内容，只在对应任务范围内增量更新。

## 回滚与残余风险

- 回滚：T1 候选依赖可直接移除；首次发布前各任务按完整逻辑变更 Git 回滚，本地/CI 状态只通过 allowlist 重建。`im-ops` 改名必须整体回滚。首次正式集群产生数据后优先 roll-forward，不能假设另一 Raft 库或旧格式可直接读取。
- 未验证路径：T13 变更后的 routing-ha 新鲜通过证据仍待 Docker 环境恢复；生产候选容量和机器配置尚未执行；此前同 revision 全仓集成已完成，routing-ha 已验证应用层内部身份拒绝，生产 Raft 原生端口的 mTLS sidecar/网络策略仍属于部署环境验证，不由本地 Compose 伪造通过。
- 已接受但需跟踪的风险：多数派不可用时实时路由写和下行短暂停止；已准入远端 RPC 可跨 epoch 完成，依赖接收端幂等；hash 用户数均匀不等于负载均匀；RouteLookup 只作为异常恢复路径；两 voter 不提供单故障 HA。

## 归档后关闭审计

本节不是可提前标记 passing 的实施任务。只有 T1-T10 全部 `passing`，归档前 `clean/full/quality` 已通过且无未处理 finding 时执行：

1. 将本文件原样移动到 `docs/exec-plans/completed/2026-09-20-broker-routing-consensus.md`，把 `PROGRESS.md` 切为 `none`，更新设计和索引中的 active 链接为 completed；除这些状态文件外不修改其他 tracked 文件。
2. 归档后依次运行 `./scripts/verify.sh clean`、`./scripts/verify.sh full`、`./scripts/verify.sh report`，再用 `ruby -rjson -e 'r=JSON.parse(File.read(".harness/report.json")); abort("full evidence is not fresh") unless r.dig("verifications")&.any? { |v| v["mode"]=="full" && v["status"]=="passed" && v["freshness"]=="fresh" }'` 核对最终 fingerprint。
3. 归档前的独立 quality Review 只有在 request 明确排除 execution-state 文件且其 scope fingerprint 未变化时可复用；否则在归档后重新运行 `./scripts/verify.sh quality` 生成 request，由独立 Reviewer 复审并再次运行同命令验证 response。
4. 任一归档后命令失败时，把计划移回 `active`、恢复 `PROGRESS.md` 的 T10 active 状态，将 T10 从 `passing` 重开为 `active`，记录失败命令与 fingerprint；修复后重新执行完整关闭流程，不保留半归档状态。
