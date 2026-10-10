# Harness 规则矩阵

## 规则矩阵

矩阵是规则登记表，只记录所属规范、当前传感器、Review 状态和升级或退出等自动化条件。Harness 运行流程由[Harness Guide](HARNESS_GUIDE.md)定义；业务、架构和编码规则的完整解释只存在于 Architecture、Coding、SQL、Unit Test、Security、Reliability 或当前批准的所属设计中。模块 README 只拥有业务统一语言、不变量和当前模块事实，不承载自动门禁规则正文。

读取状态时以“自动检查”列中的具体检查器为已执行范围；标记 `Review-only` 或“不自动化”的部分没有机器保证。“Review”列表示仍需语义审查，不等于检查器已经覆盖全部规则。遇到混合行，仅以列出的测试或脚本断言为机械约束，其余描述仍由 Review 核对。

| 规则 | 规范所有者 | 自动检查 | Review |
|---|---|---|---|
| `AGENTS.md` 等 AI 专用指令文档使用英文；面向开发者的 `README.md`、`ARCHITECTURE.md` 和 `docs/**` 使用中文解释性正文，代码标识符、命令、协议名、既定技术术语和技术图标签在更清晰时可以保留英文 | Harness/Documentation | Review-only；是否清晰取决于语境，无法根据英文 token 的出现可靠判断语言质量，机械检测易产生误报 | 是 |
| 根 Architecture 只拥有跨模块拓扑、依赖和数据所有权；复杂模块在本地 Architecture 说明内部拓扑与一致性。只有存在多个运行职责、独立一致性模型或重要跨边界流程时才新增，Facade、SDK、聚合 POM 与单一 Plugin 使用 README | Harness/Architecture | `check-drift.sh` 保证已批准的 Gateway/Broker/Message 局部入口存在；是否新增或移除局部 Architecture 仍为 Review-only，不能按目录或 POM 数量机械判断 | 是 |
| `scripts/*.sh` 文件头说明用途、输入、输出/副作用、依赖和退出码；非显然函数说明参数、算法、隔离或失败传播，简单语句不写逐行旁白 | Harness/Documentation | Review-only；注释质量不能由行数或函数前是否有注释准确判断，`bash -n` 与 Harness fixture 只验证脚本行为 | 是 |
| 根 `PROGRESS.md` 是有界全仓状态索引，所有 active plan 必须被引用；没有 active plan 时明确写 `none`，详细任务和证据仍由计划拥有 | Harness/Plans | `check-drift.sh` + PROGRESS 正反 fixture 自动检查计划链接；当前任务和验证摘要的新鲜度由 Review 确认 | 是 |
| `grill`、brainstorming 或其他需求访谈确认的决策必须先进入设计文档，记录选项的完整语义、理由和影响，不能依赖聊天历史或只保存 A/B/C 字母 | Harness/Design | active plan 的事实源链接由 `check-drift.sh` 检查；会话是否完整转录及理由质量无法从仓库机械推断，由独立设计 Review 判断 | 是 |
| 跨模块、公共协议、一致性、安全、发布或难回滚变更按 `DESIGN_DOC_GUIDE.md` 编写；正文按读者决策路径组织，备选方案、行业依据、决策记录和 Review 追溯收纳为附录；命中设计触发条件时，用户批准且独立 Review 无 Blocker/Major 后才能进入 execution plan，无需设计的工作可使用已批准 Product spec | Harness/Design | `check-drift.sh` 检查 active plan 的批准设计/Product spec 链接和稳定计划结构；设计深度、事实/假设区分、不变量闭环及 Review 质量保持 Review-only | 是 |
| 跨模块、跨会话和高风险工作从唯一执行计划模板创建；active plan 必须包含 Sprint Contract、事实源、验证分层、任务状态、详细任务、恢复状态和回滚与残余风险；状态表 ID 与详细任务 ID 一一对应，步骤使用 checkbox，`passing` 任务没有未勾选步骤 | Harness/Plans | `check-drift.sh` 检查稳定章节、设计/产品规格链接、任务 ID、checkbox 和 passing 一致性，`verify.sh clean` 复用 drift；步骤粒度、文件范围和业务语义由 Review 判断 | 是 |
| `readiness` 使用 POM/前端工具链约束验证 Java、Maven、Node 和必需命令；`clean` 只读检查可交接状态，不删除文件或误伤正常未跟踪源码；只有 blocked、零 active 的计划符合 WIP=1，计数只容忍 rg 无匹配退出码 1，不吞掉读取错误 | Harness/Environment | `test-harness.sh` 的兼容/不兼容版本、WIP、零 active blocked 计划、恢复区、临时工件与未跟踪源码 fixture | 是 |
| 核心 checker 失败包含 WHAT/WHY/FIX，并保留具体规则与文件位置 | Harness/Feedback | Drift、Java style、SQL Harness fixture | 是 |
| 模块与 DDD 依赖方向 | Architecture/Coding | ArchUnit | 是 |
| Broker RPC 调用方身份使用 `support.security.BrokerCallerPrincipal` 与 SDK `BrokerCallerRole`；Gateway 注册沿用已认证身份，准入层与 Handler 保留角色拒绝，Registry 保留 Gateway ID 身份绑定；不为单一注册操作保留空 Principal 接口或重复包装 | Broker Architecture/Security | IdentityAccessInterceptorsTest、AbstractBrokerRpcHandlerDispatchTest、BrokerRequestInterceptorChainTest、BrokerBoltHandlerTest、InMemoryGatewayRegistryTest、BrokerBoltAccessContractTest 验证身份、角色与绑定；通用 Principal 冗余类型检查为 Review-only | Broker 身份模型或注册准入改变 |
| Broker 请求链归 `infrastructure/chain`，具体实现 `BrokerRequestInterceptorChain` 直接实现 Bolt SPI，不在 support 保留无独立职责的空接口。配置列表决定节点顺序，业务 Handler 位于链尾；不声明 stage，也不使用 Terminal 回调。operation 覆盖与请求类型匹配只在测试侧验证 | Broker Architecture/Coding | LayerBoundaryTest.brokerSupportMustOnlyContainNarrowCrossLayerHelpers、brokerInterfacesMustNotDependOnConcreteInfrastructure、BrokerRequestInterceptorChainTest、BrokerAdmissionContractValidatorTest、RouteBrokerExecutionInterceptorTest 与 ImBrokerApplicationTest 验证依赖、顺序和入口注册；空接口必要性为 Review-only | Broker RPC 准入链或 Handler 依赖改变 |
| Bolt 请求处理 SPI 只有 `handle(context, payload)` 一个入口；Processor 总是传入传输上下文，Broker/Gateway 在上下文或身份缺失时保持 fail-closed，不保留可绕开身份校验的 payload-only 重载 | im-bolt README/Broker 和 Gateway 安全边界 | BoltRequestProcessorTest、AbstractBrokerRpcHandlerDispatchTest、FrameWriteHandlerTest、ConnectionCloseHandlerTest 验证单入口与缺失身份拒绝；跨模块方法签名由编译检查 | Bolt SPI 或传输身份校验改变 |
| Gateway 旧会话关闭只在本机关闭完成后记录幂等成功；中途失败释放进行中占位供同一 operation 重试，进行中的重复调用不能提前返回成功 | Gateway README/Reliability | ConnectionCloseHandlerTest 覆盖中途失败重试和进行中拒绝；ConnectionCloseDeduplicatorTest 覆盖完成、释放、容量与重复判断 | Gateway 关闭控制或去重时序改变 |
| 应用用例直接按稳定能力分组；没有实际异类职责或真实能力契约时不保留 service/port 中间目录 | Coding/Broker Architecture | LayerBoundaryTest.brokerApplicationMustGroupUseCasesByCapability 检查 cluster/frame/local/gossip/diagnostic/policy 并禁止旧 service/port；帧分发与其 Lookup 归 frame，本机 Lease 与路由维护归 local，路由接收/清理合并服务但分离状态锁，由 LocalRouteAppServiceReceiveTest 并发行为验证；恢复 ACK 归 cluster；职责归属 Review-only，待可靠识别状态所有权及副作用后扩展 | 是 |
| Broker Spring typed properties 只暴露部署差异、运行预算和容量上限；quarantine 等派生时长只计算，未消费键不保留。应用/定时入口专用参数归所属能力；本机 Registry 容量由基础设施读取源配置，应用只接收批次超时，不保留同形 model/properties 包或依赖 infrastructure 配置 | Broker Architecture/Configuration | BrokerConfigTest 验证已移除键和 Lease pending 容量绑定，BrokerRoutingPropertiesTest 验证容量与 Lease 约束，LocalRouteAppServiceReceiveTest/InMemoryBatchConnectionRegistryTest 验证批次与容量；LayerBoundaryTest.brokerApplicationMustGroupUseCasesByCapability、brokerInterfacesMustNotDependOnConcreteInfrastructure 验证包归属。哪些预算需可调为 Review-only | Broker 配置键、派生规则或参数对象归属改变 |
| Broker Registry 存取接口及注册观察归所属领域能力，内存实现归 infrastructure；领域接口不依赖 SDK DTO。诊断直接由 AppService 查询，单实现 RouteLookup/Gateway 转发接口不保留 | Broker Architecture/Coding | LayerBoundaryTest.brokerDomainMustNotDependOnTechnicalLayers、RegistryTransformerTest 传输形状/时间水位映射、Registry/FrameDelivery/Diagnostic 聚焦测试；是否需要独立技术接口仍为 Review-only | 是 |
| Broker HTTP/RPC/定时入口只承担传输与触发职责；业务用例归应用层。管理 operation 查询返回明确的 HTTP 响应类型，不直接序列化领域实体；单生产子类的入口基类是否保留按实际复用判断 | Broker Architecture/Coding | BrokerClusterControllerTest 验证响应字段边界，AbstractBrokerRpcHandlerDispatchTest 与 BrokerBoltHandlerTest 验证 principal 拒绝及正常注册，GatewayCleanupSchedulerTest 验证仅委托；职责与抽象必要性为 Review-only，需检查调用方及副作用 | 入口职责、响应或鉴权边界改变 |
| Gateway 心跳超期清理先记录 Gateway/Connection Gossip 删除观察，再移除本机路由与注册；记录失败保留目标供下轮重试，重复清理不恢复路由。本机观察非持久化事件，不能承诺崩溃重放 | Broker Architecture/Reliability | LocalGatewayCleanupAppServiceTest 覆盖失败重试、删除观察及重复执行；BrokerStateStoreTest 覆盖 Gossip 合并；进程崩溃耐久性由 Review 明确风险，不作为通过断言 | Gateway 清理顺序、Gossip 删除或重试行为改变 |
| 路由查询负缓存从 Gateway 复核完成且无可投递目标时起算；描述命中但端点失配仍视为未命中；单 peer 同步提交异常、异步失败和超时不得丢弃其他结果 | Broker Architecture/Lookup | FrameDispatchAppServiceLookupTest 验证重复未命中、端点失配负缓存、部分失败、最高水位合并 | 查询、复核或缓存策略改变 |
| RouteLookup 本地未命中后查询当前拓扑中全部其他 ACTIVE/DRAINING Broker；不排序、不截断，专用虚拟线程并发；每次 peer IO 的固定超时及 peer deadline 由 Adapter 生成，外部请求期限不传入应用命令 | Broker Architecture/Lookup | FrameDispatchAppServiceLookupTest 验证全部候选及部分失败；BrokerClusterAdapterTest 验证 IO 超时和 peer deadline；RouteLookupConfigurationTest 验证虚拟线程；整体期限与在途取消的边界仍需 Review | 候选选择、执行器或查询期限改变 |
| Broker 成员、排空、故障恢复和 Lease 续签定时入口取得运行标志后，同步准备失败必须释放标志；异步完成时释放，不能永久跳过后续轮次 | Broker Architecture/Recovery | BrokerLifecycleRecoveryTest 连续两轮同步失败与 RoutingLeaseSchedulerTest 异步重叠断言 | 调度入口或恢复时序改变 |
| Broker 可淘汰状态通过 JetCache LOCAL 管理容量与 TTL；分片绝对保留时限不被重试延长；Lease prepare 不满额拒绝，允许框架淘汰旧 nonce；accept 一次性消费目标 nonce，领域服务按单调准备起点及 Lease 自身有效期判定安装与执行 | Broker Architecture、Reliability | FrameDispatchAppServiceLookupTest 验证负缓存，LocalRouteAppServiceReceiveTest 验证分片超时/字节，InMemoryBatchConnectionRegistryTest 验证清理检查点，LocalLeaseAppServiceTest 验证满额接受新申请、旧 nonce 淘汰和重复消费，LocalRoutingLeaseServiceTest 验证缓存命中但 Lease 已过期时拒绝，JetCacheLocalValuesTest 验证框架策略；分类边界 Review-only | Broker 缓存或租约待安装记录改变 |
| 领域 Registry 的业务输入使用领域值，时间使用 Instant/Duration；入口完成转换，存储实现不保留基础类型重载再恢复领域对象 | Coding Guide/DDD、Broker Architecture | ConnectionRegistry 参数域化由编译与 Registry/Handler/Gossip/投递回归验证；通用签名检查 Review-only，待其余 Registry 输入也完成迁移并明确技术投影边界后自动化 | Registry 契约或调用入口改变 |
| Gateway 注册字段区分进程 GatewayId 与认证主体 GatewayPrincipalId；领域主机/端口在边界展开，SDK/Gossip 不直接序列化领域对象 | Broker Architecture/Registry | GatewayRegistrationTest 验证字段不变量，InMemoryGatewayRegistryTest 验证身份绑定/退役/心跳保留，RegistryTransformerTest 和 BrokerStateStoreTest 验证平铺契约及 Gossip；通用字段语义为 Review-only | 注册模型或协议映射改变 |
| 连接注册使用唯一版本化实体与读写入口，查询不补建无版本记录；无版本 Gossip 删除不能改写本机注册 | Broker Architecture/Connection | GatewayRouteTableTest、ConnectionRouteRevisionTest 保护版本/tombstone；InMemoryConnectionRegistryTest 验证同源查询和时间保留；BrokerStateStoreTest.remoteConnectionRemovalCannotEraseNewerLocalRegistration 验证观察边界；通用模型重复检查 Review-only | 连接模型、Registry 或 Gossip 删除改变 |
| Broker Lease 关闭/安装用例顺序归 Application，Adapter 提供单次 RPC、共识操作及本机原子签发入口；BrokerCluster 持有 epoch/ACTIVE/fence 资格规则，领域签发服务持有任期/序列规则；最终资格检查和签发与日志应用共用同步边界，锁内不等待网络；首次授权与续租共用签发状态，无应用服务互调，不省略 quarantine | Broker Architecture/Coding | BrokerLeaseGrantAtomicityTest、BrokerClusterTest、BrokerClusterLeaseGrantTest、BrokerClusterLeaseInstallationTest、RoutingLeaseSchedulerTest、BrokerClusterAppServiceFenceTest、BrokerClusterAdapterTest、ImBrokerApplicationTest 与模块分层测试。完整用例归属仍为 Review-only，不能仅按实现数量或类名机械判定 | 是 |
| Broker fence peer RPC 在有界线程池与有界队列上执行；饱和不回退调用线程，拒绝转失败阶段并保留 quarantine。传输和载荷映射归 Adapter/Transformer，holder 选择和 ACK 校验归应用编排 | Broker Architecture/Reliability | BrokerClusterConfigurationTest 覆盖边界与拒绝策略；BrokerClusterAdapterTest 覆盖调度、真实并发与失败阶段；BrokerClusterAppServiceFenceTest 覆盖拒绝后等待隔离窗口。其他执行器的职责与队列准入需要数据流分析，仍为 Review-only | 是 |
| Broker Adapter 的操作结果只承载被消费的状态、operation、Leader 与原因；集群/成员配置快照由独立查询取得，不为了构造结果重复读取状态。没有接口调用方的单实现成员接口不保留 | Broker Architecture/Coding | BrokerClusterConsensusResultTest、BrokerConsensusMembershipResultTest 固定结果字段契约；真实 Adapter、快照恢复与成员恢复测试覆盖行为。接口是否值得抽象仍为 Review-only，需分析生产调用关系而非仅统计实现数量 | 是 |
| Broker 路由应用写用例的新请求冲突和领域拒绝不产生共识/fence 副作用；历史操作输出保留接受 epoch，恢复阶段不被简单包装方法掩盖 | Broker README/Coding/DDD | BrokerClusterAppServiceChangeTest 的拓扑拒绝与 fence 回归、BrokerClusterAppTransformerTest 的历史 epoch 回归、BrokerDrainAppServiceTest 和 BrokerFailureRecoveryAppServiceTest；编排可读性仍为 Review-only，需能可靠分析阶段与副作用数据流后再自动化 | 是 |
| 故障恢复的证据、降级资格及终态由聚合内对象决定，日志应用时重验；通用阶段命令不能绕过，日志固定时间策略，VERIFYING 恢复保留已持久化意图 | Broker Architecture/DDD | BrokerClusterTest 验证无证据/未超时拒绝、过期证据拒绝、通用阶段绕过拒绝和终态幂等；DrainProgressTest 验证汇总后检查点冻结与重复 ACK；BrokerClusterCommandCodecTest 验证新命令及版本；BrokerClusterAdapterTest 的真实 Raft 快照重启恢复；其他流程职责归属为 Review-only，待可可靠识别资格与状态变更数据流后扩展 | 恢复规则或聚合边界改变 |
| Broker 故障恢复在 VERIFYING 持久化的降级意图必须在重启后继续提交 DEGRADED；缺失证据或降级原因不得被正常成功提交覆盖 | Broker Architecture/Reliability | BrokerFailureRecoveryAppServiceTest 的两次阶段提交间中断、快照恢复和直接成功提交拒绝回归；BrokerClusterOperation 终态证据校验 | 是 |
| 本地同机运行的 Broker Bolt、Broker 管理 HTTP、Gateway Bolt 与 Gateway WebSocket 默认监听端口互不冲突 | Reliability/Configuration | `BrokerConfigTest`、`BrokerManagementPropertiesTest`、`WsConfigTest` | 是 |
| 所有 Spring Boot Server 直接依赖 `im-metrics`，业务端口按服务族固定，management port 为业务端口加 1000，并暴露 `health,info,prometheus,metrics`、隐藏 health details；Nacos metadata 统一发布 management port；安全所有者显式放行 Actuator，`im-metrics` 不得声明 `SecurityFilterChain`；management port 必须由内网隔离；`@Observed` 自动配置须晚于 MeterRegistry 创建，且代理调用后计数器与 duration 能实际导出 | Observability/Sharding runtime design | `ObservabilityConfigurationTest`、`ImMetricsAutoConfigurationTest`、`ImMetricsManagementEndpointTest` 真实代理调用及 Prometheus 双指标断言、`verify.sh startup`；真实 Prometheus scrape 由 local infra 验证 | 是 |
| 新增业务运行模块纳入 Architecture 导入范围，避免规则因未扫描而假通过；Spring Boot 可执行模块显式加入原始 `target/classes` 测试类路径 | Architecture/Harness | ArchUnit 模块导入、测试类路径与聚焦 RED fixture | 是 |
| Facade/SDK 不依赖 server | Architecture | ArchUnit | 是 |
| 管理审计持久化只由 `im-audit-server` 拥有；Admin/IAM 依赖 SDK 而非 Server | Architecture/Coding | Maven 依赖与源码所有权扫描 + ArchUnit | 是 |
| 审计提交时序、脱敏、失败隔离、来源校验、重试和幂等 | Security/Reliability | SDK/Server 聚焦行为测试；通用源码判断 Review-only | 是 |
| Facade 公开请求使用 `facade.params/*Params`，不暴露应用层 `command/query` | Coding | Java style checker + fixtures | 是 |
| Facade 分页参数复用 `Paging`，边界包装属性显式拒绝 `null` | Coding | Facade 契约测试；全仓历史基线未收敛前 Review-only | 是 |
| Facade 纯集合结果直接返回 `List<DTO>`，只有存在分页、游标、缺失项或其他元数据时才使用结果包装 DTO | Coding | 当前 Review-only；返回包装是否承载真实元数据需要结合契约语义判断 | 是 |
| Dubbo Provider 通过 `im-dubbo` 统一转换 `RpcException`，不在业务 RPC 实现重复异常包装 | Coding/Reliability | 通用切面单测与自动配置测试；业务侧 Review-only | 是 |
| RPC Token 使用保留 Attachment 传播，Provider 在接口适配器前建立并在调用后恢复 Spring Security 上下文；业务 Params 不承载 Token 或可信来源 | Coding/Security | `RpcTokenConsumerFilterTest`、`RpcTokenProviderFilterTest`；通用载荷语义 Review-only | 是 |
| Facade 与 SDK RPC 公开输入使用独立的 `*Params`，不直接暴露 Event、领域对象或 CQRS 模型 | Coding | `check-java-style.sh` + 正反 fixture | 是 |
| 插件不依赖业务模块 | Architecture | ArchUnit | 是 |
| Snowflake 算法、机器 ID 契约、实现选择和 Bean 装配只由 `im-identity` 持有；运行模块直接声明依赖并通过必填 STATIC/REDIS 配置选择实现，不直接构造基础设施；Redisson 保持可选 | Architecture/Identity design | `RuntimeDependencyPolicyTest` 源码所有权、生产代码构造扫描、Maven 直接依赖与 optional 属性检查；`ImIdentityAutoConfigurationTest` 模式与失败语义 | 是 |
| `im-web` 不依赖 Session 或管理身份实现，管理应用与 IAM SDK 显式接入统一 Web 协议 | Architecture/Coding | ArchUnit + Maven 依赖测试 | 是 |
| 直接依赖 `im-web` 的可部署 Servlet MVC 应用显式配置 `/api/doc.html` 与 `/v3/api-docs`；SDK、Plugin 和 Facade 不声明应用路由 | Architecture/Coding | `RuntimeDependencyPolicyTest.deployableServletApplicationsConfigureOpenApiPaths` | 是 |
| 插件不硬编码业务 Controller 路径；通用 Web 请求上下文不承载安全身份或业务语义，并由 Servlet Filter 在 Spring Security 前建立 | Coding/Security | 请求上下文 Filter 注册顺序、生命周期及安全端点可见性测试；路径与语义归属 Review-only | 是 |
| 可部署 HTTP 应用统一使用 `HttpResult`，Security 失败使用相同协议，不重复声明全局错误响应 | Coding | Web/IAM SDK/Admin/Ops 聚焦行为测试；重复类型识别 Review-only | 是 |
| 管理 UI 在唯一 HTTP 边界拆解 `HttpResult`、提交同源 CSRF Token，并以契约测试覆盖 URL、Params、Body 和错误映射 | Coding/Security | 各 UI `Http.spec.ts`、API contract tests；跨工程配置一致性暂为 Review-only | 是 |
| 管理 UI 列表区分 loading/data/empty/error 并在失败时保留成功数据；抽屉保留失败输入，局部写入状态不冻结整页，危险或立即失效操作要求后果确认，状态与反馈具备可访问文本语义 | Coding/Security | IAM `InteractionComponents.spec.ts` 与各 Workspace 行为测试；其他 UI 的业务后果和复用范围 Review-only | 是 |
| 独立 Vue/TypeScript UI 提供 lint、format check、unit test、typecheck 和 build 门禁，配置由各 UI 自有 | Coding/Test | 各 UI package scripts；根 verification 继续执行测试与构建，lint/format 脚本完整性暂为 Review-only | 是 |
| 浏览器 wire model 以字符串承载 Java `Long` 业务 ID，禁止用 `Number(id)`；分页总数和 epoch millis 仅在安全整数校验后转换 | Coding/Security | 各 UI wire/date-time tests；待 TypeScript Harness 能区分业务 ID 与计数后再增加静态检查 | 是 |
| JAR 托管且无 SPA fallback 的 UI 使用 Hash Router；IAM BFF `continue` 仅恢复经过校验的同源相对路径 | Coding/Security | 各 UI Router/Navigation tests 与 IAM SDK Authorization Session tests；服务端 fallback 方案 Review-only | 是 |
| IAM BFF 公共页面、静态资源、登录与回调跳过 Session Introspection；IAM 会话端点与 API 必须认证，未知路径默认拒绝；已知前端静态资源缺失返回 NOT_FOUND，不能把所有未映射 API 当成静态资源 | Coding/Security | `IamBffRequestPolicyTest`、`IamSecurityFilterTest`、`ErrorAdviceTest`、既有未映射 API 契约测试；应用自定义安全链复用检查 Review-only | 是 |
| IAM 自身管理登录复用 Spring Form Login 与 CSRF，合法 SPA `continue` 显式恢复；缺少 `continue` 时恢复 OAuth SavedRequest，外部地址不得跳转 | Security/Coding | `IamLoginRedirectHandlerTest`、`IamSecurityBeansTest`、`LoginView.spec.ts`、`Navigation.spec.ts` | 是 |
| 常见静态资源后缀与 `/assets/**` 跳过完整 HTTP 访问日志；无后缀路径按实际 MVC 静态资源 Handler 判断，不能全局硬编码 `/`；Controller、认证入口和业务 API 仍保留日志 | Coding | `LoggingFilterTest`；其他静态资源归属 Review-only | 是 |
| 响应缓存 Filter 对 Servlet 异步请求延迟到 `AsyncListener.onComplete` 记录并提交正文，不得在初始分派返回时提交空缓存 | Coding | `LoggingFilterTest.preservesBodyWrittenAfterAsyncRequestStarts` + Broker 管理 operation 集成验证 | 是 |
| 生产代码显式类型、无字段注入 | Coding | Drift checker | 是 |
| 生产代码优先使用 import，不在方法体、注解和表达式内散落全限定类名 | Coding | 不自动化（Review-only）；同名类型冲突与字符串内容难以由正则可靠区分 | 是 |
| 生产测试替身、控制台输出、手动 `ObjectMapper` | Coding/Test | Drift checker | 是 |
| 成组简单 getter/setter | Coding | Java style checker | 是 |
| AppService 标量业务入参 | Coding | Java style checker | 是 |
| 直接承载公开用例的应用层入口使用 `*AppService`，并通过单个 Command、Query 或其他明确输入对象表达用例 | Coding | 当前 Review-only；待 `im-ops` 查询入口收敛后评估窄结构检查 | 是 |
| AppService 按聚合、用例主体和事务边界组织；同一主体的紧密命令不按方法类别拆成单一调用方的薄服务，不跨聚合或事务边界机械合并 | Coding | Review-only；方法数量、依赖数量和调用方数量都不能可靠表达业务内聚性 | 是 |
| 只服务单一 AppService 且没有独立调用方、生命周期或替换实现的步骤不提取 Engine、Coordinator、Executor 或测试专用 interface；业务子包只在存在多个稳定入口或独立一致性边界时增加 | Coding | Review-only；需结合调用图、状态所有权和失败边界判断，类名与文件数量不能可靠自动判定 | 是 |
| 拥有独立身份和生命周期且允许分别存在的聚合使用独立注册用例与事务边界；仅业务不变量要求原子共生时组合创建 | Coding | 聚焦应用服务行为测试；聚合生命周期与原子共生语义 Review-only | 是 |
| AppService 不直接依赖其他 AppService | Coding | ArchUnit | 是 |
| 跨上下文外部投影只参与派生领域资料，不作为临时字段写回本上下文聚合 | Coding/DDD | Social `FriendServiceTest` 与 `FriendAppServiceTest`；其他上下文组合语义 Review-only | 是 |
| 可部署应用不使用 `@ConditionalOnProperty` 关闭生产 Bean；条件装配只保留在插件、SDK 和自动配置 | Coding | Java style checker + 正反 fixture | 是 |
| 稳定横切策略优先使用显式注解/接口契约，避免 AppService 重复 Executor/Lambda 包装 | Coding | 不自动化（Review-only）；具体能力使用注解清单与切面行为测试 | 是 |
| 安全身份与授权由 Spring Security 管理，审计等业务注解不复制权限策略；自定义跨线程上下文只传播非安全元数据并及时清理 | Coding/Security | 权限注解清单、授权允许/拒绝测试、上下文传播与清理测试；通用静态判断 Review-only | 是 |
| 管理端业务权限枚举统一承载编码、展示名称和说明，并通过嵌套 `Code` 提供编译期常量；接入应用目录与 `@RequiresPermission` 共源，IAM Server 的标准 `@PreAuthorize` 也引用对应 `Code`，Controller 不重复权限字符串或 Spring EL | Coding/Security | `RequiresPermissionTest` 允许/拒绝测试 + 各管理模块权限元数据和接口授权契约测试；跨模块通用源码识别成熟前不使用全局正则 | 是 |
| IAM 内部管理权限与外部应用权限必须分属不同领域语义：IAM 内部权限只保护 IAM 自身管理端点，外部应用权限由各应用声明并通过权限目录同步；不得混用 Permission 聚合、权限目录或授权入口 | Coding/Security/DDD | IAM 端点授权契约、外部应用权限目录同步和跨应用隔离测试；领域语义与表复用边界由 Review 检查 | 是 |
| 声明式锁键不显式调用 `toString()`，统一由锁切面完成字符串转换 | Coding | Java style checker | 是 |
| CQRS Command/Query/Event 按意图、读取、已发生事实区分并放入对应包；边界对象不直接持有业务领域对象 | Coding | 不自动化（Observed） | 是 |
| `model/cqrs/dto` 中的应用传输结果统一使用 `*DTO` 后缀，不混用 Data/View/裸 Result | Coding | 当前 Review-only；仓库历史 DTO 目录收敛后增加窄路径命名检查和正反 fixture | 是 |
| 框架回调提交协议处理后的最终状态时使用统一保存 Command；只有回调要求应用执行不同业务流程时才拆分用例 | Coding | IAM OAuth 应用边界与适配器聚焦测试；通用语义 Review-only | 是 |
| Spring Security 查询 SPI 不修改领域状态；Authorization Code 单次消费和 Refresh Token 轮换优先使用框架默认 Provider，不增加无明确需求的历史重放状态 | Coding/Security | OAuth Credential 查询无副作用测试、默认 Provider 契约与授权持久化测试；通用语义 Review-only | 是 |
| 同一应用中的浏览器 Session 与 OAuth Client Bearer 接口按 URL 边界使用独立有序 SecurityFilterChain；Client API 无状态，Web 兜底链不混入 Bearer 认证 | Coding/Security | IAM Security Bean 命名/数量契约与权限目录认证测试；通用链配置语义 Review-only | 是 |
| 接入服务的 Opaque Client Credentials 身份和 Audience 校验复用 IAM SDK；业务服务不重复声明仅改名的 Principal/Converter | Coding/Security | IAM SDK Audience 允许/拒绝测试与 Audit 身份上下文测试；跨模块重复能力 Review-only | 是 |
| 通用哈希原语归 `HashUtils`；管理员密码、OAuth Client Secret 与 OAuth Token 保持独立凭据边界，不跨领域复用密码服务和值对象 | Coding/Security | `HashUtilsTest`、OAuth Client Secret 编码/匹配兼容性测试与 IAM Security Bean 测试；跨模块语义 Review-only | 是 |
| 不透明 UUID 字符串复用 GeneratorUtils.nextUUID，注意返回无横杠的 32 位格式；协议要求标准 UUID 或 UUID 对象时不机械替换 | Coding | Broker 成员/投递/租约/水位文件及 Raft Adapter 回归；格式约束 Review-only，自动化需能区分不透明标识与协议格式依赖 | 是 |
| `Query`/`QueryCondition` 命名、分页分离和可选过滤条件；可选文本先将空白归一化为 `null`，再执行关联校验和领域转换 | Coding | Audit 查询转换聚焦测试；通用命名与归一化顺序继续 Review-only | 是 |
| 边界输入输出使用包装类型并拒绝意外 `null` | Coding | 不自动化（Observed） | 是 |
| 边界对象与领域对象分别使用 `AssertUtils.arg*`、`domainProp*` 校验 | Coding | 不自动化（Review-only） | 是 |
| 包内能力接口、技术实现和模型明显混放 | Coding | Java style checker 的窄规则 | 是 |
| 包层级与稳定职责匹配；单领域模块保持 `domain/model|repository|service` 扁平结构，多领域模块再按能力分组 | Coding | 不自动化（Review-only） | 是 |
| 运行态诊断属于技术能力，不建模为业务 Domain；查询编排与输出归 application/model，具体技术查询归 adapter，采样资源与指标装配归 infrastructure；不为单一历史查询能力拆 port/转发类，不在 `support` 下复制小型分层 | Coding | BrokerDiagnosticAppServiceTest、GossipSyncTrackerTest 与 Broker/Ops 分层测试；模块性质、独立历史存储或真实替代实现的职责判断 Review-only | 是 |
| SDK 按稳定能力组织包；协议模型、框架适配和传输实现分离，HTTP/Kafka 等形成独立技术边界时使用二级包；不为服务端 DDD 分层在 SDK 中复制 `application/domain/infrastructure` | Coding | 不自动化（Review-only） | 是 |
| 可部署 DDD 业务模块使用统一顶层分层骨架，能力差异保留在层内子包 | Coding | 不自动化（Review-only） | 是 |
| `application` 仅承载应用服务；应用层横切组件、场景常量和应用边界异常放在模块顶层 `support` | Coding | 不自动化（Review-only） | 是 |
| 顶层 `model` 先按 CQRS、IO、枚举、结果和值等类型职责组织；认证后的请求 Principal、安全上下文与受信身份解析辅助类型进入受限的 `support.security`，不伪装成业务模型；同类模型存在多个稳定业务组且数量足够时才增加业务子目录 | Coding/Architecture | Broker `LayerBoundaryTest` 同时限制顶层 model 与 support 白名单；业务分组阈值 Review-only | 是 |
| 包迁移或重构确认文件无调用方后，同一步删除旧类、兼容壳、占位实现和过期测试；暂留兼容文件必须记录调用方、期限和退出条件 | Coding | 编译、引用扫描与 Review-only；调用方和兼容意图无法仅按文件存在性可靠判断 | 是 |
| Broker `support.context` 集中承载请求上下文和入口访问控制，允许读取 typed 管理配置，不调用应用、领域、Adapter、interfaces 或 Registry 实现；领域层仍禁止依赖 support | Coding/Architecture | `LayerBoundaryTest.brokerRequestContextMustNotDependOnBusinessImplementations`、既有 domain 隔离与 support 白名单；具体请求职责归属 Review-only。按用户确认恢复该目录，不为单个类设白名单 | 是 |
| 应用服务编排按输入转换、用例调用、输出转换分段书写，避免将边界构造、适配器调用和结果映射压缩为单行 | Coding | Review-only；需结合方法语义判断，不做脆弱格式正则 | 是 |
| Result 工厂使用 conflict/failure 等业务语义，接收领域标识和 epoch，在工厂内部转换基础值；固定原因集中维护。共识/协议到应用结果的纯字段映射归 Transformer，以目标模型 + From 命名；不在 Result 定义 from* 或 *From，也不将纯映射改名为 outcome/success。应用层不为映射增加分支，保留全部状态、Leader 端点及历史 epoch | Coding/DDD | `LayerBoundaryTest.brokerResultFactoriesMustNotContainMappingNames` 禁止 Broker Result 的转换命名；结果工厂、Transformer 与成员编排测试覆盖状态和 epoch。工厂业务语义、领域参数及伪装映射 Review-only，自动化需能解析参数类型与字段数据流后扩展，不能依赖单纯关键词 | 是 |
| 同组应用用例的预期业务冲突/拒绝统一返回结果，不混用参数异常；不吞掉非预期故障，拒绝不产生写入副作用 | Coding/DDD | BrokerClusterAppServiceChangeTest 覆盖五类成员 epoch 冲突、加入资格拒绝和并发冲突的无副作用结果；通用错误语义 Review-only，自动化需识别业务与技术故障边界 | 是 |
| 结果工厂对应单一固定原因时内置 message；同一工厂承载不同原因时显式传入 message，不能合并成笼统说明或只为隐藏文案参数拆多个工厂；状态对应的空字段集中维护 | Coding | BrokerClusterConsensusResultTest 覆盖固定非 Leader 原因与两类拒绝文案，成员编排回归保留原因；原因是否固定 Review-only，自动化需能识别所有生产调用场景 | 是 |
| Broker 应用写用例通过 getCluster 读取隔离的领域快照；SDK 查询投影转换由接口边界负责，不使用捆绑聚合和 SDK 投影的中间视图 | Coding/DDD | 当前 Review-only；自动化需能解析读取链路、快照隔离和协议映射后引入 | 是 |
| 本地登记后依领域决定识别重复请求，不重新查询已变化的 exists 判断新请求；新操作必须取得远端同步证据，历史重试沿用原准入判断 | Coding/DDD | BrokerClusterTest 覆盖 duplicate 分类，BrokerClusterAppServiceChangeTest 验证新操作读取及历史重试不重复读取；跨方法状态时序 Review-only，自动化需能解析聚合写入与后续条件的数据流 | 是 |
| 本地共识角色查询明确本机主体，不以角色标志替代多数派领导权确认 | Coding/Reliability | Broker 成员/生命周期回归覆盖本机角色分支，BrokerClusterAdapterTest 验证真实 Leader、换届和 ReadIndex；通用方法语义 Review-only，自动化需区分角色读取与共识证据 | 是 |
| applied index 与已应用的 quorum ReadIndex 不混淆；查询方法、操作枚举、协议字符串和响应字段描述实际证明的位置，而非暗示最新索引 | Coding/Reliability | AppliedReadIndexContractTest 检查协议名、BROKER_CONTROL 准入和响应字段，无旧字段别名；本机/远端进度测试验证异步应用证明。索引语义 Review-only，自动化需能追踪 Raft 读取与应用等待链路 | 是 |
| 本机能力入口共享现有运行实例，不复制领域 BrokerNode、不缓存第二份状态或独立关闭共享资源；集群命令与远端查询仍归集群入口，通用查询不以 learner 参数名暗示资格约束 | Coding/DDD | LocalBrokerTest 覆盖角色变化、地址读取、异步索引和失败传播，真实 Adapter/启动测试验证同一入口与装配；职责和命名语义 Review-only，自动化需识别状态/生命周期所有权及资格判断 | 是 |
| 管理 HTTP ThreadLocal 上下文在异步处理开始和同步请求完成时均清理；Controller 在异步编排前捕获事实，不依赖异步线程继承身份 | Security/可靠性 | `BrokerWriteAccessContextInterceptorTest` 同步和初始 Servlet 异步清理回归、管理 Controller 聚焦测试；通用分析需结合框架生命周期，Review-only | 是 |
| routing-ha 场景定位必须指向当前测试源码，runner 仍要求同轮新鲜 JUnit/拓扑证据，不能用过期类名或替代无关测试冒充场景 | Reliability/Harness | `RoutingHaScenarioManifestTest` 用 JDK AST 检查类和方法存在，runner 校验执行证据及固定 25 场景；断言语义等价 Review-only | 是 |
| 管理 HTTP ThreadLocal 上下文在异步处理开始和同步请求完成时均清理；Controller 在异步编排前捕获事实，不依赖异步线程继承身份 | Security/可靠性 | `BrokerWriteAccessContextInterceptorTest` 同步和初始 Servlet 异步清理回归、管理 Controller 聚焦测试；通用分析需结合框架生命周期，Review-only | 是 |
| 只有单一生产实现的 Raft、Bolt 或 SDK 技术能力直接作为具体 Adapter 由 AppService 编排，不创建仅用于依赖倒置或测试替身的 application port；真正表达聚合存储语义的接口才进入 domain repository | Coding/DDD | 当前 Review-only；自动化需先能识别生产实现数量、测试替身、框架 SPI 与领域 Repository 语义，避免误删真实多实现 seam | 是 |
| 同一集群技术入口可同时承载 Raft 和 Broker 间查询，不因单一查询或协议不同强制拆接口/第二个 Adapter；独立职责或生命周期才构成拆分依据，远端响应仍核对集群、节点及数据合法性 | Coding/DDD | BrokerClusterAdapterTest 覆盖合法进度和错误集群/节点/负索引，成员编排、恢复、启动及分层测试；职责与生命周期判断 Review-only，自动化需能识别生产调用关系和替代实现 | 是 |
| 通用 Raft 运行机制归 im-raft；插件只处理字节日志和业务回调，不依赖 Broker 类型，业务命令、快照内容和领域规则留在使用方 | Plugin/Architecture | RaftRuntimeTest 验证提交、恢复与换届；ModuleBoundaryTest 验证插件契约不暴露 JRaft 或业务类型；业务回调确定性仍为 Review-only，需能识别时间、随机值及外部副作用后自动化 | 是 |
| Raft Peer 应用读取证明的传输封装归 im-raft/RaftPeerClient，泛型载荷与协议名称由使用方传入，身份/索引校验归业务 Adapter；不将业务读取 RPC 误作原生 Raft 通信，不依赖 Broker SDK | Plugin/Architecture | RaftPeerClientTest 检查参数与响应转发、异常传播，BrokerClusterAdapterTest 检查身份/索引拒绝，ModuleBoundaryTest 检查插件业务依赖隔离；原生协议与业务证明的职责判断 Review-only | 是 |
| 入站框架适配器只转换框架输入输出；认证、权限解析等同一用例的领域协作由应用服务完整编排并返回应用层结果 | Coding | IAM 管理员认证应用服务与 Provider 聚焦测试；通用边界 Review-only，避免误伤 `PasswordEncoder` 等纯技术适配 | 是 |
| 确定性应用用例优先通过 `@Audited` 记录审计；动态属性由参数级 `@AuditAttribute` 与成功结果的一层属性提供，不记录字段显式使用 `include = false`，不在应用服务创建专用 Resolver 或手写成功/失败控制流 | Coding/Security | SDK Aspect 与 Audit 导出聚焦测试；字段敏感性与注解完整性 Review-only | 是 |
| 信任来源由认证上下文或固定传输通道注入，不在不可信载荷中重复声明；应用 Command/Event 仅保留一个扁平化来源字段 | Security/Coding | Audit SDK 契约结构测试与 Ingestion Transformer 聚焦测试；通用语义 Review-only | 是 |
| 普通说明使用块注释，类注释使用多行 Javadoc，字段和 record 组件的简短说明优先使用块注释 | Coding | Review-only；格式和语义需人工判断 | 是 |
| 公共领域类型的类级 Javadoc 明确聚合根、实体、值对象、领域服务、Repository、领域变更或领域结果角色，并使用领域语言说明身份、生命周期、一致性边界或不变量 | Coding/DDD | Review-only；待统一领域 stereotype 能准确区分普通领域对象后再增加机械检查 | 是 |
| record 属性较多时每个组件单独换行，并为每个组件提供简短块注释 | Coding | Review-only；不使用固定属性数量阈值 | 是 |
| MapStruct `@Mapper` 保留生成映射，不退化为全手写 `default` 方法集合 | Coding | 不自动化（Review-only） | 是 |
| MapStruct 跨层异名属性使用显式映射，关键时间与标识字段不静默丢失 | Coding | `BrokerDiagnosticAppTransformerTest` 等模块转换测试；通用异名语义判断 Review-only | 是 |
| 无运行时协作者的无状态 MapStruct Transformer 优先使用 `INSTANCE`，需要注入协作者时才使用 Spring component model，且不混用两种获取方式 | Coding | 不自动化（Review-only）；需识别 `uses`、Decorator、抽象 Mapper 及容器协作者，单纯匹配 `componentModel` 会误报 | 是 |
| Transformer 按转换目标归属；应用/CQRS 输入到领域对象使用 `*DomainTransformer`，领域对象到应用 DTO 使用 `*AppTransformer`，业务推断不放入字段 Mapper | Coding | IAM OAuth 授权边界聚焦测试；通用规则 Review-only，待历史双向 Transformer 收敛并能解析泛型返回类型后再自动化 | 是 |
| 多参数 HTTP 查询使用 `*Request + @ModelAttribute`，Request 到 CQRS 的跨层转换由接口 Transformer 完成 | Coding | 不自动化（Review-only）；参数多少及是否形成稳定请求语义需结合接口判断 | 是 |
| HTTP `@RequestBody` 使用接口层独立的 `*Request` 类型，不直接暴露 SDK、领域或 CQRS 对象 | Coding | `check-java-style.sh` + 正反 fixture | 是 |
| 绝对业务时刻在应用、领域、Java 服务契约和 MyBatis Entity 中使用 `Instant`；浏览器 HTTP 边界使用 epoch milliseconds | Coding | Audit HTTP/Transformer/持久化聚焦测试；通用语义 Review-only | 是 |
| 领域 `*QueryCondition` 中共同表达闭区间的绝对起止时刻使用共享 `TimeRange` | Coding | `TimeRange` 与 Audit Transformer/Repository 聚焦测试；通用识别 Review-only | 是 |
| 用户可见时间按显式 IANA 时区格式化为 `yyyy-MM-dd HH:mm:ss`，服务端导出接收用户时区而非使用机器默认时区 | Coding | Audit UI 与 Excel 导出聚焦测试；跨模块通用规则 Review-only | 是 |
| 租约、耗时和进程内超时使用单调时间值对象表达；领域服务不暴露 `LongSupplier` 时间源，也不使用墙上时间判断单调边界 | Coding/DDD | Broker authority 聚焦测试覆盖租约过期与准入；通用识别 Review-only，需区分应用层可控测试时钟与领域服务依赖 | 是 |
| 领域业务标识使用值对象 | Coding | 不自动化 | 是 |
| 幂等重试先核对 operation 的摘要、类型与目标，再检查新请求的状态前置条件；已提交阶段不能重复外部副作用，FAILED 不能冒充已提交 | Reliability/DDD | `BrokerClusterTest` 九类操作重试与失败提交测试、`BrokerClusterAppServiceChangeTest` 跨 epoch 和成员角色变化后重试；通用识别 Review-only | 是 |
| Broker 具名变更各自接收完整领域 Intent；重复意图匹配归 operation 实体，目标拓扑与集群不变量归聚合，不用含大量可空专属字段的通用输入容器 | Broker DDD design | `BrokerChangeIntentTest`、`BrokerClusterTest` 和 `BrokerClusterAppServiceChangeTest`；类型职责 Review-only，待能解析领域入口和输入字段适用性后自动化 | 是 |
| Begin 只登记操作事实、不修改 BrokerNode；Commit 校验并执行内部实体行为；operation 不保存可变节点，查询与持久化快照继续隔离实体 | Broker consistency | `BrokerNodeTest`、`BrokerTopologyIsolationTest`、`BrokerClusterSnapshotIsolationTest`、`BrokerClusterAppServiceChangeTest`；通用阶段及别名分析 Review-only，待可靠分析对象逃逸和状态变更路径后自动化 | 是 |
| 恢复流程依赖当前 epoch 调度时，恢复终态前不能发布后续拓扑；互斥约束随共识日志重放而非进程内标记维护 | Broker consistency | `BrokerClusterTest.routeRecoveryBlocksNextTopologyUntilTerminal` 覆盖 REBUILDING_ROUTES、VERIFYING 与终态释放 | 是 |
| 成员变更仅在可证明无外部部分提交时把明确拒绝写为 FAILED；两步晋升/降级与未知结果保持 PENDING 待恢复 | Broker consistency | `BrokerClusterAppServiceChangeTest` 覆盖单步 REJECTED、两步 REJECTED 和 UNAVAILABLE；跨 Leader 自动恢复仍为 Review-only，待持久化端点绑定后增加节点级测试 | 是 |
| 聚合根继承 `Identification`，按需实现 `Validator`；Lombok 相等性使用 `callSuper = false`，不使用 `onlyExplicitlyIncluded` 定制业务 ID 专属相等性 | Coding | Java style checker 拦截 `Identification` 子类的 `onlyExplicitlyIncluded = true`；是否需要生成相等性由 Review 判断 | 是 |
| 新增聚合封闭状态写入口，不使用类级 `@Data`/`@Setter`；统一校验不变量，以领域方法维护生命周期，并将同一阶段的成组字段组合为不可变值对象 | Coding | `OAuthGrantTest` 及 OAuth 持久化聚焦测试；通用检查暂为 Review-only，仓库仍有历史可变聚合，待其收敛且具备字段级 setter 误报样例后再启用全局门禁 | 是 |
| 承担领域校验的领域对象或聚合 Builder 内部持有私有无参构造的目标对象，直接填充其字段而不复制 Builder 属性，不使用 Lombok `@Builder`，也不保留仅供 Builder 转发参数的长参数构造器；`build()` 返回前统一校验；`Identification.pkId` 不进入领域构造器或 Builder，由 Repository 在构建后回填 | Coding | `AuditEventTest`、Broker cluster/authority 聚焦测试与 Repository/Transformer 聚焦测试；通用判定 Review-only，待能可靠识别领域 Builder、DTO Builder 和框架反序列化构造器后再自动化 | 是 |
| 领域对象必填属性不超过 4 个且顺序清晰时优先直接构造；达到 5 个及以上，或参数组合容易错位时使用手写 Builder 并在 `build()` 统一校验 | Coding | 不自动化（Review-only）；参数阈值与业务语义需结合判断 | 是 |
| 不做过度防御：校验归明确边界和领域拥有方，不重复内部已保证条件，不用兜底值或补偿修改掩盖错误事实；认证、授权、不变量、并发和失败隔离仍必须保留 | Coding/可靠性 | 通用判断 Review-only，不能按 null/if/Math.max 机械禁止；自动化条件是能解析调用契约、输入信任边界并具备必要防御/冗余防御正反 fixture。Broker 时间精度实例由 `BrokerClusterAppTransformerTest` 验证同毫秒完成、早于创建拒绝与不修正调用方时间；routing 的 cleanup/快照/Lease 聚焦测试验证截止时间、已提交水位、分块摘要与 nonce 错配不消费，cleanup/ACK 成功路径经过真实 Handler JSON 与生成的 CQRS 映射 | 是 |
| 值对象规范构造入口统一保证格式、容量、归一化和脱敏不变量 | Coding/Security | 聚焦值对象单测；通用静态判断 Review-only | 是 |
| 聚合相等性由聚合自身字段表达，排除 `Identification.pkId` 和 `rowVersion` 持久化技术状态 | Coding | `PersistenceConcurrencyArchitectureTest` 聚焦相等性测试；Java style checker + 正反 fixture 拦截业务 ID Include 定制 | 是 |
| 模型名称表达领域事实，不把截断、脱敏等内部处理方式作为类型语义 | Coding | 不自动化（Review-only） | 是 |
| 对外接口枚举与领域枚举隔离，跨层枚举转换由 Transformer 完成 | Coding | 不自动化（Review-only） | 是 |
| MyBatis Entity 的闭集状态、动作、结果和种类使用数据库层枚举；数值列枚举声明稳定值和 `@EnumValue` | Coding | Java style checker 检查低误报字段；Audit Schema 聚焦测试验证数值映射；其他列类型与开放协议值 Review-only | 是 |
| 数据库 JSON 列由 MyBatis TypeHandler 编解码，领域 Transformer 不处理 JSON 存储格式 | Coding | 不自动化（Review-only）；需区分数据库列与协议、缓存等合法显式 JSON 转换 | 是 |
| 所有 MyBatis Entity 继承 `BaseEntity`，所有表保留 `id/create_time/update_time/is_deleted/version` 标准模板字段；数据库自增技术主键与领域业务 ID 分离 | Coding/SQL | SQL checker + fixture 强制模板字段；`BaseEntityTest` 检查 `AUTO`/version；Java style checker 拦截重复声明公共字段 | 是 |
| 聚合 rowVersion 跨 Domain/Entity 双向携带，数据库 Entity 仍使用 version；Repository 使用普通 MyBatis-Plus 写入口，仅在写成功后回填 pkId/rowVersion；完整聚合 save 的冲突和关联 assign/remove/saveBatch 失败必须中断后续事件、缓存、会话及关联副作用，允许无匹配的条件 bulk/remove 按自身契约处理；集合更新原子递增 version | Coding/Reliability | `PersistenceConcurrencyArchitectureTest`、Account/Message 失败更新测试、IAM 父记录及关联写失败测试、权限/角色版本恢复测试、OAuthClient H2 连续更新/冲突测试；公共 Service 不解释业务 false | 是 |
| Message 分片写保留数据库自增技术主键和 `message_id`/`chat_id` 等业务全局 ID，UPDATE 同时包含 `user_id`、主键和 version | Coding/SQL/Reliability | `DdlSchemaTest`、`MysqlImGroupChatRepositoryTest` 与 `LocalShardingRoutingIntegrationTest` 的跨分片同 ID 隔离场景 | 是 |
| 事务失败后不继续复用已参与持久化的聚合；调用方重试前通过 Repository 重新加载，避免为普通 Java 对象实现事务回滚补偿 | Coding/Reliability | Review-only；只有出现事务边界捕获异常并复用同一聚合的生产调用方时再引入针对性检查 | 是 |
| ShardingSphere SQL 日志默认只为本地诊断开启 | Configuration/Security | `ShardingConfigurationTest` 检查五份规则使用 `${im.datasource.sharding.sql-show:true}`；生产由 Nacos 覆盖为 `false` | 是 |
| 应用不得使用 MyBatis `StdOutImpl` 绕过 ShardingSphere SQL 日志开关 | Configuration/Security | `MessageConfigTest` 检查 MyBatis 与 MyBatis-Plus 未配置 stdout logger | 是 |
| `Identification.pkId` 仅标识当前表行；跨表关系使用领域业务 ID；独立身份或生命周期的一对多接入配置拆成独立聚合，不按技术分支复制平行聚合 | Coding | IAM 聚合/Repository/Schema 聚焦测试；字段语义、基数和聚合边界 Review-only | 是 |
| Repository 通过 MyBatis Service 完成持久化，不为单一查询语义增加透传 QueryCondition 或直接编排 Mapper | Coding | Java style checker 拦截 Repository 直接导入 Mapper；查询语义层次由 Review 判断 | 是 |
| Repository 组装所属表查询时复用对应 MyBatis Service 的 `getQueryWrapper()`/`getUpdateWrapper()`，不直接创建同类型 Wrapper | Coding | Review-only；现有历史调用尚未全量收敛，待完成基线清理后再加入 Java style checker | 是 |
| Entity 到 Domain 的业务字段和 `pkId/rowVersion` 重建统一由 Domain Transformer 完成；Repository 只加载数据与关联并调用完整 Transformer，写成功后的生成值回填仍由 Repository 负责 | Coding | `PersistenceConcurrencyArchitectureTest#repositoriesDelegateDatabaseToDomainMappingToTransformers` 扫描 Repository 私有 Db 映射 helper 和 Transformer 后技术字段补写；关联加载语义由 Review 判断 | 是 |
| 承担入站入口职责的框架 SPI 只做协议适配，完整用例经过应用服务，不直接访问领域 Repository 或 MyBatis | Coding | IAM Security SPI 聚焦依赖测试；通用职责识别 Review-only | 是 |
| 完整聚合由所属 Repository 返回，关联 Repository 仅返回关系事实 | Coding | IAM `RoleRepository`、`AdministratorService` 和 Repository 聚焦测试；聚合所有权通用判断 Review-only | 是 |
| Repository 使用明确的标识值对象表达查询语义，多种外部标识在应用边界识别，不创建宽泛联合标识或退化为裸 `String` | Coding | 不自动化（Review-only）；需结合调用方是否已知标识类型以及联合标识是否具有真实领域语义判断 | 是 |
| MyBatis Mapper 扫描策略单一，逐个 `@Mapper` 时不重复声明空 `@MapperScan` 配置；公共 datasource 插件不扫描业务 Mapper | Coding | Java style checker + 正反 fixture；多数据源和跨根包扫描由 Review 判断 | 是 |
| `im-admin` 通过 Account Admin Facade 管理普通用户，不拥有本地用户持久化，不依赖 `im-datasource` 或保留失效 MyBatis 映射 | Architecture/DDD | `RuntimeDependencyPolicyTest#accountAdminFacadeHasDedicatedRuntimeBoundary` 与 Admin Context 测试 | Account 管理边界或数据所有权迁移 |
| 四个独立部署的 Management 应用直接依赖 `im-nacos`，统一获得动态配置和服务存活注册 | Architecture/Reliability | `RuntimeDependencyPolicyTest#managementModulesIncludeIamAndIndependentApplications` 检查四个运行 POM | Management 部署拓扑或配置中心方案迁移 |
| 领域服务包含真实业务决策 | Coding | 不自动化 | 是 |
| Gateway 接收实体保护批次身份、分片幂等及完整摘要；批次 Registry 使用领域契约，分片及清理的缓存策略由基础设施实现负责且锁独立；应用层先核对资格，再调用 Registry，处理 Lease 与发布 | Broker Architecture/DDD | GatewayRouteBatchTest、LocalRouteAppServiceReceiveTest/IdempotencyTest 验证分片、清理及独立锁；InMemoryBatchConnectionRegistryTest 验证共享数据、重试水位和清理缓存淘汰，ImBrokerApplicationTest 验证单一 Bean；领域依赖检查，其他临时状态归属 Review-only | 路由接收或清理身份改变 |
| Gateway 显式注销与超期清理都必须先写入 Gossip 删除事实，再删除本机 Gateway/连接注册；Gossip 写入失败时保留本机状态供下一轮重试，接口 Handler 不直接编排具体 Registry | Broker Architecture/Reliability | LocalGatewayCleanupAppServiceTest 验证 Gossip 失败可重试及删除顺序；GatewayUnregisterHandler 聚焦测试验证入口委托和连接事件；跨进程 Gossip 重放仍由运行验证覆盖 | Gateway 注销、超期清理或 Gossip 删除语义改变 |
| 跨多个领域对象的全量同步、差集计算和状态迁移集中在领域服务；应用服务只做输入转换、调用与持久化编排 | Coding | 不自动化（Review-only） | 是 |
| CQRS 输入保留边界类型；应用服务在用例入口先统一构造领域值对象和内部查询条件，后续编排不继续传播同义的原始 `String`、数值或集合 | Coding | 不自动化（Review-only）；字段业务语义和合法通用边界类型无法仅按 Java 类型可靠判断 | 是 |
| 自动过期的限流、防暴力破解计数和临时限制留在 `support`/`infrastructure`，不映射为聚合状态或业务表字段 | Coding/Security | `LoginProtectionTest` 与 IAM 认证/审计聚焦测试；通用语义 Review-only | 是 |
| 聚合删除校验与 Repository 生命周期删除语义一致 | Coding | 不自动化（Review-only） | 是 |
| Adapter 使用领域对象作为业务边界 | Coding | 不自动化 | 是 |
| 本机用例按输入转换、领域资格、状态修改分段，领域对象拥有完整证据及期限规则；配置对象直接读取，不另缓存同义标量 | Broker Architecture/Coding | LocalLeaseAppServiceTest 验证单调期限和非法授权不消费 nonce；LocalRouteAppServiceCleanupTest、DrainProgressTest 验证完整/重复/错配水位，ReceiveTest 验证独立锁；通用职责判断 Review-only，待可靠识别输入信任边界和状态所有权后自动化 | 本机状态或一致性边界改变 |
| 定时触发 AppService 的入站任务属于 interfaces，调度资源和技术组件自身生命周期仍属于 infrastructure；不按 @Scheduled 注解一律迁移 | Coding/Broker Architecture | LayerBoundaryTest.brokerScheduledApplicationEntrypointsBelongToInterfaces 检查 Broker 同时包含 Scheduled 方法并直接依赖 AppService 的入口；ImBrokerApplicationTest 验证任务注册。其他模块及间接调用入口为 Review-only，建立稳定入口识别条件后再扩展 | 是 |
| Broker 注册表业务事件监听入口属于 interfaces/listener，委托 AppService 更新 observation；存储、Spring 事件发布及技术组件启停监听仍属于 infrastructure | Coding/Broker Architecture | LayerBoundaryTest.brokerEventListenerEntrypointsBelongToInterfaces 检查接收 Broker CQRS 事件的 EventListener；RegistryEventListenersTest 验证 Spring 分发、观察更新和异常隔离。其他事件入口 Review-only，形成稳定事件角色识别后扩展 | 是 |
| 条件 Bean 装配闭合 | Coding | 聚焦启动测试 | 是 |
| Starter/SDK 接入 Bean 只由 AutoConfiguration 注册；Server 不直接或传递获得自身客户端 SDK，可选 SDK 集成依赖由消费者显式选择 | Coding/Architecture | `RuntimeDependencyPolicyTest` 禁止 IAM Server 直接依赖 IAM SDK并要求 Audit SDK 将 IAM SDK 标记为 optional；IAM 真实启动回归 | 是 |
| 本地配置提供可绑定的 localhost 与开发凭据默认值，生产通过 Nacos 整体覆盖 issuer、签名密钥、Client Secret 和 Session 加密密钥 | Coding/Security | IAM classpath RSA 加载测试 + Admin/Ops/Audit 配置绑定测试；生产覆盖完整性由部署检查和 Review 验证 | 是 |
| 可水平扩容服务的分布式 ID 生成器不共享硬编码机器标识 | Coding/Reliability | 当前 Observed + Review-only；待现有 StaticSnowflake 基线收敛后增加配置与多实例唯一性测试 | 是 |
| 固定提交后 Adapter 副作用使用注解，复合步骤由应用服务显式编排 | Coding/Reliability | 聚焦事务时序测试；通用静态识别易误判，其他场景 Review-only | 是 |
| 不增加测试专用生产 API | Coding/Test | 本轮不自动化 | 是 |
| 测试不使用真实等待 | Unit Test | Drift checker | 是 |
| 禁用测试必须说明原因 | Unit Test | Drift checker | 是 |
| DDL、危险 SQL、`${...}`、静态无条件写入 | SQL | SQL checker + fixture | 是 |
| 服务私有 DDL 位于所属 Server 模块根 `sql/`，Schema 与表不能跨服务混放 | SQL/Architecture | SQL checker 递归路径扫描 + 各服务 Schema 所有权测试；表业务归属仍由 Review 判断 | 是 |
| Server 模块 `sql/` 只使用 `ddl.sql`/`dml.sql`，交付 DDL 使用 `CREATE TABLE IF NOT EXISTS` 且禁止 `DROP TABLE`；本地 Compose init 按 `ddl/`、`dml/` 分层并由 `00-initialize.sql` 通过原生 `SOURCE` 顺序执行，六个快照必须与所属模块 SQL 字节一致 | SQL/Local runtime design | SQL checker 固定路径、幂等创建与危险语句检查，SQL Harness 正反 fixture、Local Compose Harness `cmp` 与入口顺序检查 | 是 |
| SkyWalking Agent 9.7.0 与 JDK 21 的本地 full 应用必须通过 `-XX:-UseContainerSupport` 避开容器指标初始化死锁，并在 SkyWalking 前用独立 premain Agent 预加载 `jdk.internal.misc.ThreadTracker`，避免 gRPC 后台线程与主线程形成 `JarFile`/`AppClassLoader` 锁顺序反转；同时显式限制 heap、线程栈、metaspace、direct memory、code cache 和容器总内存 | Local runtime design | Local Compose Harness 精确检查统一 JVM 参数、`mem_limit`、预加载源码/manifest 和两个 Agent 的启动顺序；真实 full 启动和 load-test smoke 检查全部应用健康及非预期重启 | 是 |
| 单机核心链路压测必须从健康的 19 服务 base 切换为 15 服务 load profile，暂停 IAM/Audit/Admin/Ops；轮次结束检查六个核心容器健康且零重启，清理后恢复 19/19 | Load-test design | Load-test runner fake fixture 检查 stop allowlist 和运行集合；真实 smoke 检查 profile、容器 restart count 与 base restore | 是 |
| 十个本地应用在 base 使用 1 GiB；load profile 中 Message 使用 2 GiB、SkyWalking OAP 使用 1536 MiB、MySQL 使用 768 MiB，其余核心应用保持 1 GiB；恢复 base 时 OAP/MySQL 回到 1 GiB/512 MiB | Load-test/Local runtime design | Local Compose Harness 检查 base 上限；Load-test Compose Harness 精确检查合并配置和 runner 显式重建 OAP/MySQL | 是 |
| base restore 对 management readiness 只允许一次 restart；restart 前健康失败可快速触发恢复，restart 后的短暂 `unhealthy` 必须在 120 秒内继续轮询，最终未恢复才失败；生产保留 90/120 秒默认值，故障 fixture 注入短等待，不能为预期失败消耗生产级超时 | Load-test design | Load-test runner 动态健康 fixture 模拟 restart 后先 unhealthy、再 healthy，要求最终进入 `BASE_READY` | 是 |
| 健康检查必须先捕获 `docker inspect` 输出并保留命令退出码，再解析 health；inspect 失败不得被 `read`、here-string 或空 health 覆盖成轮询超时 | Harness/Load-test design | `test-load-test-harness.sh` 的 inspect failure fixture，要求立即失败并保留 `RESTORING` | 是 |
| 准备阶段的群通知/ACK deadline 与完成后的重试静默窗口必须串行，全部 ACK 完成后旧 deadline 不得抢先终止静默窗口 | Load-test design | Setup WebSocket 确定性 scheduler 测试：deadline 晚于最后 ACK、早于 quiet 完成时仍成功；缺通知继续超时 | 是 |
| 准备阶段对同 receiver + 同 receipt 的 at-least-once 重投幂等重发 ACK，durable intent 只写一次，并重新开始完整 quiet horizon；同 receiver 的不同 receipt 继续失败 | Load-test design/Reliability | Setup WebSocket 测试覆盖 ACK 前重投、ACK 后 quiet 内重投、单 intent journal 和不同 receipt 反例 | 是 |
| 压测资源采样周期包含采集耗时，采集后只等待到目标周期剩余时间；目标进程在采集中结束时正常停止且不追加残缺样本。证据中位间隔保持目标周期正负 25%，单个间隔不得超出 0.5-1.5 倍 | Load-test design | Resource sampler fixture 注入受控 `docker stats` 延迟并检查时间戳间隔、JSON 完整性和 0600 权限；Node 正反例覆盖偶发采集超时、持续漂移和断档 | 是 |
| 消息压测 summary 以固定低基数桶记录 private/group 发送失败、错误帧 `body.code` 中的 10010 busy、10007 repeat、其他业务错误和响应超时，禁止将任意业务错误码放入动态指标名或标签 | Load-test design | message scenario 源码契约 + `send-timeout`/`send-busy`/`send-repeat` 生产形态错误帧的真实 k6 mock fixture 精确核对 | 是 |
| 消息压测 push 先于发送响应到达时，缓冲仅延后核对，不得延后用于传输延迟的接收时间戳 | Load-test design | `response-after-push` 真实 k6 mock fixture 人为延后发送响应 600ms，精确核对 20 次发送、68 次 ACK、P95 和分类；源码契约核对缓冲保留时间戳 | 是 |
| 消息压测必须分别记录发送到业务 response 与发送到首个 push 的 P95/P99；无响应只进入 timeout counter，不得伪造延迟样本 | Load-test design | message summary 源码契约；`response-after-push` 真实 k6 mock fixture 以 response P99 下界和 push P95 上界证明两类时间独立 | 是 |
| 压测 Node 合约按测试文件串行执行，避免 timer、HTTP/WS、fsync fixture 在单机高负载下因文件并行争用宿主调度；不得借此放宽单个测试的断言或业务超时 | Unit-test/Load-test Harness | `package.json` 固定 `node --test --test-concurrency=1`，`verify.sh load-test` 统一走 npm test | 是 |
| 真实 smoke 顶层状态验证 workflow/evidence/cleanup/checksum；connection 必须 stable，message 可 stable 或明确 service_failed。message uncertain、缺轮次、工具错误或恢复失败不得通过 | Load-test design | `test-load-test-harness.sh` 覆盖 stable、message service_failed、scope/data/cleanup 失败；报告始终展示逐轮 decision | 是 |
| 连接轮 route cleanup 只查询该轮 effective users 对应的凭据前缀，不得用整个已准备批次的用户数冒充本轮 evidence | Load-test design | runner 显式传 `--user-count effective`；route-cleanup 正反测试覆盖凭据前缀和超界拒绝；round classifier 要求 evidence userCount 等于 effective users | 是 |
| Broker 到 Gateway RPC 与 Gateway 本机 Channel write 必须使用两个固定名称的低基数 duration 指标，禁止加入 userId、messageId、connectionId 或 gatewayId 标签 | Broker/Gateway Architecture、Load-test design | `BrokerClusterAdapterTest.gatewayWritePreservesPayloadAndPerTargetSuccessFailureMetrics` 验证 SDK 写帧的 success/failure counter 与 duration，`ConnectionRegistryMetricsTest` 验证 Gateway 指标；真实短批次检查两个服务 Actuator 样本 | 是 |
| Message private/group 发送入口使用独立固定 duration 指标，覆盖锁、业务校验、事务和提交后动作；不得增加用户、会话、群、消息或 token 标签 | Message Architecture、Load-test design | AppService 注解契约测试；真实短批次检查两个 Actuator 指标的 count/sum/max | 是 |
| 消息轮 drain 后正常关闭 WebSocket；close grace 到期仍在线时记录 `forcedStops` 并强制结束，runner 只对合法 message summary 的该标记接受 k6 108 | Load-test design | 场景源码契约、round classifier forced-stop 用例、runner fake/static 契约和真实过载轮次 | 是 |
| 会更新双方/全体成员 Chat 的消息发送按事实范围互斥：私聊使用 canonical user pair，群聊使用 groupId；自动续期锁最多等待 100 ms，锁内读取待更新 Chat 并提交成员副本，释放锁后发布发送事件，messageToken 只负责幂等而不参与互斥 key | Message Architecture/Reliability | GroupChat/PrivateChat Application Service 测试验证 scoped lock key、有界 `tryLock` 参数、两类发送均先解锁再发布事件；真实消息 smoke 验证过载不再无限增加 RPC 等待线程 | 是 |
| 群消息 fanout 新记录使用 batch insert；已有 Inbox 和成员 Chat 必须通过固定字段集合的 MyBatis-Plus Lambda Update，以参与当前 Spring 事务的临时 BATCH session 更新，每条携带 `user_id` 分片键和旧版本，并在提交前要求每个 `BatchResult` 影响行数为 `1`；不得在已绑定 SIMPLE session 的同一事务中切换 Batch `SqlSessionTemplate`，成员 Chat 入口仅允许更新最新消息、已读消息、未读数、状态和活跃时间 | Message Architecture | Group Inbox/Chat Repository 与 MyBatis Service 测试检查限定字段的 Lambda Update、BATCH session、分片键和旧版本、部分乐观锁冲突、未知影响行数以及领域状态不回填；真实 smoke 覆盖 Spring/ShardingSphere 事务组合 | 是 |
| Confirmable 消息通知在首次推送前持久化 ReceiptTask，再交给独立虚拟线程；虚拟线程启动失败或首次 RuntimeException 不占用发送线程且不否定已提交事实，禁用重试时保持同步传播 | Message Architecture/Reliability | `ImMessageNotifierInvokerTest` 覆盖虚拟线程异步边界、失败隔离、禁用重试传播和正常通知 | 是 |
| Dubbo Consumer 默认禁止框架自动重试；稳定 `RpcException` 即使被运行时异常包装也应按 cause 类型透传，未知异常不得按文本还原业务码 | Reliability/im-dubbo | `DubboEnvironmentPostProcessorTest` 与 `ImDubboAutoConfigurationTest` 检查默认/覆盖及 ConsumerConfig；`FrameProcessHandlerTest` 覆盖直接、包装和未知异常 | 是 |
| 压测 `logout-round` 必须可从部分成功恢复：受控批次 Token 的 AUTH_FAIL 表示已失效并按成功继续，其他业务或网络错误保持失败 | Load-test design | Node logout-round 正反 fixture 覆盖 10001 幂等与 10000 传播；真实中断批次从 DRAINING 重跑 cleanup | 是 |
| cleanup 已恢复到 `BASE_READY` 但敏感文件尚未销毁时，只允许对六位合法 batch ID 且 manifest SHA-256、batch 绑定、七个 cleanup checkpoint 全部匹配的批次执行受限收尾；校验失败必须保留现场 | Load-test design/Reliability/Security | runner fixture 覆盖完整 checkpoint 成功、摘要不匹配拒绝和 batch 路径穿越拒绝；cleanup Harness 继续只诊断不直接删除 | 是 |
| 20 用户真实 smoke 的连接测量为 20 秒；消息为 1 msg/s、1005 秒测量和 10 秒排空，保持至少 1000 次发送且不降低正确性阈值 | Load-test design | resolved-config fixture 检查分阶段时长、目标速率与乘积；smoke evidence 绑定 config digest 和逐轮结论 | 是 |
| Social 好友提交后只发一次 Message 私聊准备 RPC；Message 按 canonical user pair 有界加锁，并在同一事务中幂等补齐双方视角，单侧历史状态只补缺失侧 | Social/Message Architecture/Reliability | `FriendAppServiceTest` 验证单次 after-commit 调用；`PrivateChatAppServiceTest` 验证双侧创建、单侧修复、重复幂等和锁参数；真实 prepare smoke 验证两侧事实收敛 | 是 |
| 消息轮的结构/发压证据无效优先判 uncertain；证据有效且存在发送、投递、延迟等已知服务失败时，增长 backlog 不得覆盖为 uncertain | Load-test design | round-result 正反 fixture 覆盖已知失败 + 增长 backlog；`j4xrkw` 真实 summary 重算为 service_failed 并保留四条阈值原因 | 是 |
| 连接人数按 groupSize 对齐，消息目标速率不得按 cohort 取整；消息连接数单独对齐 | Load-test design | fake runner 以 `messages --connections 20 --levels 5` 验证 k6 参数保留 `TARGET_RATE=5`、`EFFECTIVE_USERS=20`；连接小于完整 cohort 仍拒绝 | 是 |
| 本地 full 的 IAM 管理客户端必须使用明确的冷启动 read timeout，且不得修改 SDK 的生产默认值来吸收本机并发启动压力 | Local runtime design | Local Compose Harness 检查 Audit/Admin/Ops 的 `IM_IAM_HTTP_READ_TIMEOUT=10s`；SDK properties 测试保留默认 `2s` | 是 |
| infra/full 切换必须在移除旧服务后等待应用端口与 Nacos/Dubbo 实例释放，并在启动后校验目标 profile 的精确运行服务集合 | Local runtime design | Local Compose Harness 校验命令顺序、注册残留和集合缺失负例；真实双向切换检查 9/9 与 19/19 | 是 |
| 非测试 H2 依赖只允许由 `im-datasource` 为 ShardingSphere 5.5.2 的运行期元数据类加载提供，业务和其他插件模块不得直接引入 | Architecture/Dependency | `RuntimeDependencyPolicyTest` 精确断言 runtime H2 的唯一 POM | 是 |
| Mapper `SELECT *` | SQL | SQL checker + fixture | 是 |
| 动态 SQL、索引合理性和执行计划 | SQL | 不自动化 | 是 |
| 未上线且无历史数据兼容需求时直接更新当前 DDL，不创建迁移脚本 | SQL | 不自动化（Review-only）；发布状态和历史数据需求无法从文件名可靠推断 | 是 |

“是”表示 Review 仍需检查语义和例外，不代表重复执行同一机械扫描。
