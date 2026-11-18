# Design Documents

- [Bolt 包职责与 Broker Gossip 运行参数归位](2026-10-25-bolt-package-boundaries-design.md)：原包迁移设计用户批准、独立设计 Review Approved；Review 追加 D6 删除无生产调用者的 Broker 列表 RPC 已获用户批准、独立设计 Review a61ffe608a987aada Approved，无必须修改项；实施、完整验收与独立 Review 已完成，详见[已归档计划](../exec-plans/completed/2026-10-25-bolt-package-boundaries.md)。

设计文档记录背景、备选方案、决策和后果，解释系统为什么采用当前形态；执行状态属于 `docs/exec-plans`。新设计遵循[技术设计文档规范](../references/DESIGN_DOC_GUIDE.md)并从[设计模板](TEMPLATE.md)开始。通过 `grill`、brainstorming 或其他访谈确认的选择必须写入“决策记录”，记录完整语义和影响，不能只保留 A/B/C 字母或依赖聊天历史。

命中[技术设计文档规范](../references/DESIGN_DOC_GUIDE.md)触发条件的工作，应先完成设计批准和独立 Review，再创建 execution plan 或实施代码；无需单独设计的工作可由已批准 Product spec 或 `docs/PLANS.md` 允许的其他事实源进入计划。active plan 必须链接一个实际存在的设计或产品规格；机械检查保证链接和结构存在，内容是否完整仍由 Review 判断。

## Current Designs

- [Ops 模块聚合](2026-11-18-ops-module-aggregation-design.md)：已实施，`im-ops` 聚合 `im-ops-server` 与 `im-ops-cli`；完整 full、25 个真实 HA 场景与独立 quality Accept 已通过，见[归档计划](../exec-plans/completed/2026-11-18-ops-module-aggregation.md)。原验收时间、指纹及历史失败保留，文档完成状态更新不晋级原证据 freshness。

- [群聊会话补偿与通知对象](2026-11-18-group-chat-recovery-and-notification-design.md)：独立草案。记录 Social 群变更交接失败与 Message 通知对象偏差，比较恢复和资格过滤方案。候选方案待批准与独立 Review，未实施。
- [Learner 同步确认与后台恢复职责分离](2026-11-12-learner-confirmation-explicit-recovery-design.md)：已批准。用户批准保留 scheduler/resume 自动恢复、不新增恢复 HTTP 接口，并批准 CLI 确认端点保存/传递修复；独立 Review 第二轮 Approved、findings none，首轮 Revise 保留。已在[Broker 成员应用流程计划](../exec-plans/completed/2026-11-09-broker-membership-flow-readability.md)完成实施验收，T1–T3 均为 `passing`；冻结 full、真实 HA 与独立 quality Accept 的原件及历史限制见已归档计划。

- [Broker 用户路由分片与 Gossip 连接观察收敛](2026-11-05-broker-sharded-route-observation-design.md)：草案。区分当前职责分片与连接观察全局复制，推荐取消 CONNECTION Gossip 发布、存储和转发，保留 Gateway 分片重报、单跳 Lookup 及既有 Raft/Lease/fence 安全契约；发布方式、消费者审计与验收阈值待确认，尚无全文批准、独立 Review 或实施授权，不改变现有活跃任务。

- [JaCoCo synthetic class 报告清单补充](2026-11-05-jacoco-synthetic-report-inventory-design.md)：用户已批准窄 checker／隔离 fixture 实施，独立技术 Review Approved／findings none；原件 `.harness/t11/b3-synthetic-design/review.json`，批准前受审 SHA `1795a23bd341edd3ec35314d8d70165f717b7214e098f13e81bca62b22249089`。保留完整 class inventory，按固定 JaCoCo 0.8.13 官方 class-level 规则区分报告期待集；实施与验证见 T11/B3，不新增真实运行授权。

- 自有 namespace 设计的 Raw POM oracle 补充：原 Reviewer 技术 Approved/findings none，原件 `.harness/t11/coverage-raw-pom-design-review-attempt2.json`，受审 SHA `486378fca88387426306a24b513688473cb9245b23cf55a131124199b8494312`。首次 Revise 保留；仅既有非破坏性 checker/fixture 范围，不是新用户授权或运行验收。

- [自有 namespace 覆盖插桩边界设计](2026-11-03-owned-namespace-coverage-instrumentation-design.md)：草案（Proposed），Reviewer 技术必修全部闭合、技术结论 Approved，待本次最小事实回填精确复核确认，尚无用户 include、实施或输出清理授权。离线 Help 3.5.1 的真实 52 项模型、有限 Shade/晚绑定/插件合同及后续两条来源 probe 已独立核验；每个 project 激活同一已审 external repository-only profile，实际 local repo 与 Mockito JAR 摘要已记录。原时间/SHA256 和全部旧 Revise 保留，来源前后仅 exists/SHA，mtime/size 为 post 观察，不倒推原模型采集。正式 full 必须采集当轮模型/来源，不复用旧 probe。无 coverage 实现、实际 agent/report 或 full 新证据，不关闭外部 MethodTooLarge 或整体 T11。

- [Ops 可信 Leader 提示映射补充设计](2026-11-02-ops-trusted-leader-hint-mapping-design.md)：已批准。2026-11-02 用户明确批准保守映射，独立技术设计 Review Approved、无安全／架构必修项；实际 Node PeerId 与可信管理发现 origin 唯一配对、去重前歧义拒绝、首次注册发布及冲突 fail-fast 纳入现有 T11。尚未实施或运行验收，不改写已批准接收节点切片的原范围。

- [Ops 成员写请求的业务目标与接收节点解耦](2026-11-02-ops-membership-receiver-routing-design.md)：2026-11-02 用户批准解耦首次接收节点与业务目标，独立设计 Reviewer `ace3b628dc0670655` Approved、必修 findings none；现有联合计划 T11 实施。不伪造发现身份、不新增未加入启动、不放宽鉴权或可信单跳；设计批准不是运行验收，原真实 CLI 旅程失败和 Leader 提示映射缺口保留。

- [自有运维 CLI 与 IAM 接入设计](2026-10-30-ops-cli-authentication-design.md)：已批准，2026-10-30 用户最终确认；独立复核 Accept、findings none，历史 Block 保留。public client／PKCE S256、Ops 窄 Bearer 双链、可靠撤销及有权限管理员可接续契约纳入[联合实施计划](../exec-plans/completed/2026-10-30-official-jraft-and-ops-cli.md)，原批准时点尚未实施或运行验收。

- [Broker 治理职责简化与官方 JRaft 迁移设计](2026-10-28-official-jraft-governance-simplification-design.md)：已批准，2026-10-30 用户最终确认，与 CLI 安全补充设计共同进入[联合实施计划](../exec-plans/completed/2026-10-30-official-jraft-and-ops-cli.md)。独立全文复核 Accept、findings none，历史 Block 与被否决方案保留；D10/D16 长期阻塞代价、D18 不做兼容、D19 自有 CLI 保留。上述未实施叙述属于原批准时点，当前实现／验收由已归档计划拥有。2026-10-31 T4 远程确认等待预算与共享等待桥补充已独立设计 Accept、findings none，用户随后回复「可以」确认独立 inbound 配置及 8／3000 ms 本地默认、固定只读等待桥和中断策略，已允许进入准确范围实施；原实现 QUALITY Block 未关闭，设计 Accept 不代表运行验收。

- [Bolt TLS 拒绝证据修正](2026-10-28-bolt-tls-rejection-proof-design.md)：用户批准测试侧严格取证方向，独立设计 Reviewer `aefe0c40fa1d38c8e` Approved；以连接关联的原生服务端拒绝和客户端实际选链观察替代 TLS 1.3 成功后读取的时序假设，不修改生产 TLS，实施验收见现有 T3。

- [Gateway 目标职责内聚](2026-10-28-gateway-target-inline-design.md)：用户批准删除身份包装对象，独立设计 Reviewer `aa00ec4801ac133f0` Approved；原模式、快照、目标选择及校验收回 GatewayClient，实施验收见现有 T3。

- [Broker 启动可信配对职责内聚](2026-10-28-broker-bootstrap-inline-design.md)：用户批准删除身份包装对象，独立设计 Reviewer `a6eeb49b80726e483` Approved，现有 T3 实施；校验、快照、发现交集与节点目标构造收回 BrokerClient，配置及信任规则不变。

- [Bolt 传输模式独立枚举](2026-10-28-bolt-transport-mode-design.md)：用户批准提取 `BoltTransportMode` 并补齐注释，独立设计 Reviewer `aa6dedabbf9c56d38` Approved，现有 T3 实施；仅类型迁移，不改配置值或模式行为，不合并身份对象。

- [项目角色身份 URI 前缀固定化](2026-10-28-fixed-peer-identity-prefixes-design.md)：用户确认五角色一起固定，取消部署覆盖，保留通用 Plugin 能力、bootstrap 配对及身份对象；独立设计 Reviewer `a7ce1f2aa262d78e2` Approved、必须项 none，现有 T3 实施中，完整验收及私有依赖阻塞保留。

- [Bolt 生命周期串行化增量](2026-10-26-bolt-client-serialized-lifecycle-design.md)：最终 ReentrantLock 与固定 5 秒 tryLock 方案独立设计 Reviewer `a3b3f5a6c96a74ee1` Approved、必须项 none，T3 实施中。取代初始化取消/owner/future等待，保留短 calls 锁和唯一 closed；仅 lifecycle/background 窄验证，尚未验收，旧 DECODE 缺口及更宽验证暂停保留。

- [Bolt 客户端生命周期状态收敛](2026-10-26-bolt-client-lifecycle-state-design.md)：用户已明确授权实施，独立设计 Reviewer `a46adc81a21ae2558` Approved、必须项 none；四态替换 Client 三个布尔字段，保留唯一 Tracker.closed、初始化回滚和运行期关闭责任，纳入 T3 实施及 lifecycle/background 窄验证。尚未验收，clean/full/commit 继续暂停，既有 DECODE 失败保留。

- [Bolt 按实际职责拆包](2026-10-25-bolt-responsibility-packages-design.md)：用户明确认可 invocation / connection / channel / client 目标职责与必要跨包 public；独立设计 Reviewer `ad3341b14c207845a` Approved、必须项 none，恢复 T3 实施，尚未验收。精确 visibility 矩阵保留唯一 Tracker monitor/closed、原生 future hook、Factory 回查与固定装配，不新增桥接接口或弱化 checker；clean/full/commit 继续暂停。

- [Bolt 受控客户端命名与连接获取封装](2026-10-25-bolt-controlled-client-api-design.md)：用户明确批准 Client 改名为 BoltControlledClient、acquire 收为 package-private；独立设计 Reviewer `a515ed306ad89329e` 首轮 Revise，已修订分类、共享 fixture 与旧产物隔离方案，原 Reviewer 复核 Approved，三个必须项关闭，进入 T3 实施；尚未验收。仅迁移七个直接访问测试，消费方拒绝断言改用正式 invoke；保留 RpcClient 独立校验，不调整 Invoker 构造类型，clean/full/commit 继续暂停。

- [Bolt 连接工厂依赖评估](2026-10-25-bolt-factory-dependencies-design.md)：独立 Reviewer `a12ce5a7edf571b89` 首轮 Revise 保留；原 Reviewer 复核 Approved、findings none，两个必须项关闭，仅批准保留现状、零生产修改。选择保留 client.manager() 与固定 snapshot 模式读取，不实施 bindManager、mode 参数或其他 Factory 生产改动；不扩生命周期修复或 BoltRpcClient 契约变更。

- [Bolt 客户端调用追踪职责拆分](2026-10-25-bolt-client-call-tracking-design.md)：用户已批准最小拆分范围，独立设计 Reviewer `a35e419d309f04bf8` Approved、必须项 none；单个 package-private 协作者集中调用追踪，保留生命周期与准入共享同一 monitor，实施纳入现有活跃计划 T3，验收未完成。

- [收尾流程精简实施设计](2026-10-22-closeout-process-simplification-implementation-design.md)：用户批准、独立设计 Review Approved，实施中；S1/S2 代码及聚焦验证完成，S3 政策同步与真实 full 验收未完成。当前合同为同冻结输入自测一次、reactor 一次、同轮专用报告校验及真实 HA 一次，不承诺实测耗时收益。

- [收尾流程收口与验收去重提案](2026-10-22-closeout-process-simplification-design.md)：用户已批准保留战术修复、延期阶段/归档平台；当前实施契约见上述精简实施设计，原审计与候选方案作为决策历史保留。

- [Routing HA 前检与有界清理](2026-10-21-routing-ha-bounded-runner-design.md)：已批准且战术修复保留；Engine 前检、命令边界、会话进程所有权和有界安全清理不随平台撤下。25 场景/900 秒不变，真实新流程验收尚未完成，不归因未知 Docker 内部故障。

- [Harness 归档差异审计](2026-10-21-harness-archive-audit-design.md)：已由精简实施设计取代／延期；不再提供 archive-audit、journal 或发布特权。历史设计与 Review 保留，当前仅采用受控的两时点状态内容 Review。

- [Harness 验证输入隔离与阶段恢复](2026-10-21-harness-verification-resume-design.md)：已由精简实施设计取代／延期；阶段链、恢复与实现输入指纹例外不属当前能力，完整 Git 输入冻结仍保留。历史方案与 Review 不删除，不晋级旧失败。

- [Bolt 连接层认证与统一 invoke 生产设计](2026-10-20-bolt-unified-invoke-design.md)：**D14 原生 TLS 材料加载收敛已获用户明确批准，独立设计 reviewer `c80596b3` 最终 Accept、无 remaining；首轮 Revise 已关闭，仅进入既有测试有限调整。D13 为未批准候选，已由 D14 取代，不实施、不新增私有 secret 副本。当前 T1–T3 passing、T4 唯一 active/Block、T5/T6 not_started，Loop#3 expired，不自动续期。只撤下同进程所有新 Channel 固定旧材料保证，不取消标准 TLS/严格身份/D10/D11、CA 双向原生拒绝、资源与剩余验收义务。** 以下 D11/计划批准及 T1 状态为历史范围，不覆盖 D14：D11 用户已批准，独立设计 reviewer `1fb8baed-2232-4e1` Approved/findings none：保证限于受控 invoke 和经审计公开 RpcClient typed-command（含 Connection/CONNECT），缺上下文拒绝；原生 TLS/encoder/response/control 单独信任并审计。同 JVM 可信代码前提，不承诺恶意进程内 sandbox；禁止任意预编码业务、codec/transport 替换、非受控 pipeline/unsafe 写入，以受控装配所有权、启动拒绝及架构 API 规则约束，不承诺启动检查永久隔离运行期特权修改。raw witness 保留为旧 all-origin 主张的正向 COUNTEREVIDENCE，不改为运行期拒绝；另需配置/架构负例与原生实际路径/rawbytes 审计。reviewer `31433f1e-b3e4-46d` 的旧主张 Block 保留。[唯一计划](../exec-plans/completed/2026-10-22-bolt-unified-invoke.md) 当前 T4 唯一 active/WIP=1；以下计划审查为历史范围：计划 reviewer `1a730f5e-903f-4cb` 全文及精确 diff Review Approved/findings none，完整技术退出仍 pending，不批准生产迁移。D10/B 及 reviewer `aa137e0a-4814-47d` Approved、隐藏实际路径保证损失保留；旧完整设计及计划 Review 仅覆盖历史范围，全部 SDK/Gossip 迁移及其余技术退出要求不缩减，不生产上线。

- [Bolt 连接层身份认证原型](2026-10-20-bolt-connection-authentication-spike-design.md)：用户批准、独立设计 reviewer `0461448d-b90c-462` Accept；仅测试源码验证握手/身份完成前业务发送屏障、地址+身份池隔离及重连并发，不切换生产调用链，不预设原型成功；纳入既有A5，生产方案待原型证据另行批准。

- [本机非线程所有权锁](2026-10-17-local-lock-design.md)：用户批准仅在 im-lock 实现 `LocalLock implements Lock`，非重入、跨线程释放、无 Registry/Template/Broker 接入；独立设计 reviewer `8bcc59eb` Accept，施工纳入既有 A5。

- 结构化健康采样与图文入门：见[共识成员边界设计后续决定](2026-10-16-consensus-membership-boundary-design.md)，用户批准 Context 使用 revision/topology/targets，删除字符串签名，并补 DDD/Raft/Gossip/异步观测说明；独立 reviewer `05870a5a` Approved/findings none，进入既有A5，其他Block保留。

- [共识成员模型边界与聚合一致性判断](2026-10-16-consensus-membership-boundary-design.md)：用户批准 RaftMembership 恢复 String 列表、Adapter 转 Broker 领域 ConsensusMembership、规则迁入 BrokerCluster.findConsistentVoters；独立 reviewer `e76a97c8` Approved/findings none，已实施容器分离；用户后续批准领域Set、同角色重复归一化/交叠构造拒绝和确定性观察签名，独立 reviewer `2939fb39` Approved，当前增量实施中。取代前一共享容器方向，MembershipEndpoint 仍在 common。既有 A5/WIP=1、Follower Block 保留。

- [共享成员端点值对象](2026-10-16-shared-membership-endpoint-design.md)：MembershipEndpoint 迁 common 的决定保留；typed RaftMembership 和直接列表传值已由[共识成员边界](2026-10-16-consensus-membership-boundary-design.md)取代，旧迁移证据保留在 A5，不冒充新模型验收。

- [Broker 自动故障观察与摘流接入](2026-10-16-broker-automatic-failure-observation-design.md)：用户已批准 voter 主动探测、Leader 拉取独立报告、具名自动摘流及全阶段恢复；采用重启/换届/拓扑变化重新等待完整冷却，不扩张 Raft 持久格式。独立设计 reviewer `c5f87b0c-0030-412` 首轮 Revise；已补入站唯一 URI SAN 来源保证、实际 membership 一致性闸门及恢复后冷却，第二轮 Accept后部分实施。用户规范纠正触发独立 reviewer `9de26e93` Revise八项，纠正设计第二轮Accept后仅修八项规范问题，结构/类型/映射和回归测试经短复审八项全部closed，恢复功能接入；配置/自动组合窄验证已执行，完整功能Review及HA/广门禁未完成。补齐原D22，不撤销自动摘流要求。最新结构补充已获用户批准、独立设计 reviewer `9f9c5cb3-857b-488` Accept、findings none：State/Topology 私有内嵌 AppService，删除 Voters Policy，将纯成员身份规则合并为现有领域服务的 `findObservationVoters`；外层显式读取实际 membership 并转换 typed List，保留重复检测与失败关闭。此次不改 JRaft/依赖，Follower 本机实际成员读取的能力 Block 保留。最新待审设计修正：将具体观察 Store 迁至精确 `support.health.BrokerHealthObservationTracker`（允许 Tracker 后缀），明确其观察状态是安全决策输入而非可选诊断，保留 domain 决策所有权；并单独登记 `beginCommandFrom` 转换合并的小型无行为维护。独立设计 Review 首轮 `59f3550d` Revise，仅有一项 minor：文件范围遗漏 `docs/references/CODING_GUIDE.md`；已补齐。第二轮 Accept、findings none；两项维护实施 reviewer `62f3d1db` Accept、findings none，阅读性 reviewer `6952162a` Accept。既有 A5/WIP=1、整体正式 Revise 和 Follower Block 保留，最终门禁失败归因见 A5。

- [Broker Transformer 与 Monitoring 归属](2026-10-15-broker-transformer-monitoring-boundaries-design.md)：用户已批准最终命名与精确 `support.monitoring` 技术观察例外；独立设计 reviewer `7cf18e69-3af8-449` Accept、findings none，进入实施。纳入既有 Adapter 计划 A5/WIP=1，不提交。

- [Broker 自有时间字段契约](2026-10-14-broker-time-contract-design.md)：用户已批准全部自有字段范围与无兼容决定；独立设计 Review 首轮 Revise；已补齐 Ops 文件/显式映射、runtime Duration、确定性重放反证与 UI string 迁移，专门 reviewer 第二轮 Accept、findings none，进入实施。Server/SDK 与 Gateway/Ops 直接消费者同步，内部 Instant xxxAt、数字 xxxAtMs/xxxMs、单调 nanos 保留，Raft v2 拒绝 v1；纳入既有 Adapter 计划 A5/WIP=1。

- [Raft 类型化命令接入](2026-10-12-typed-raft-command-design.md)：用户已批准，独立设计 Review 第二轮 Accept；原 v1 日志/快照决定已由 [Broker 时间契约](2026-10-14-broker-time-contract-design.md) 的 v2 决定取代；类型化接入与 Broker 支持代码按 codec/model/statemachine 归 support.raft 的职责决定保留；已纳入 Adapter 计划 A5 实施，最终门禁与交付状态见活跃计划。

- [BrokerClusterAdapter 职责重划](2026-10-11-broker-cluster-adapter-boundaries-design.md)：职责拆分与后续增量已实施，独立实现 Review 已执行；A5 仍 active，最终验收缺口见活跃计划。

- [路由容量 Driver](2026-10-10-routing-capacity-driver-design.md)：已批准；2026-10-10 用户确认容量定规格与 Broker 代码 Review 分轨。本地模拟计划已归档；真实生产容量不在本次交付范围。

- [Broker 路由恢复领域行为](2026-10-07-broker-route-recovery-domain-design.md)：用户已确认职责划分，T13 实施中；独立 Review 未执行。

- [Raft 通用运行能力抽取](2026-09-29-raft-plugin-design.md)：用户确认边界，插件及 Broker 接入已实施；独立 Review 和 Broker 全链路测试待完成。

- [Broker 权威集群状态与高可用路由切换](2026-09-19-broker-routing-consensus-design.md)：原设计通过四轮独立 Review；D17/D18 RouteLookup 和 Broker Bolt 责任链增量由 [Broker Review 收口计划](../exec-plans/completed/2026-09-20-broker-routing-consensus.md)完成代码/集成验收；本地 HA 模拟已归档。
- [IM 核心链路容量压测](2026-09-14-im-load-test-design.md)：设计已批准并通过三轮独立 Review 和用户委托主审；实施与真实压测已完成并按 completed execution plan 归档，记录宿主 k6、本机连接/消息容量探索、批次数据清理与测量安全边界。
- [可观测性与分片运行接入](2026-09-11-observability-sharding-runtime-design.md)：记录本轮 `$grill-me` 确认的数据库 `version` 字段、MyBatis-Plus 插件、ShardingSphere 配置、Prometheus、SkyWalking、端口治理和本地启动编排决策。
- [Harness 交接、启动与质量评审](2026-09-10-harness-handoff-startup-and-quality-design.md)：自动派生 session handoff、验证全部应用的受控启动路径、限界清理，并用独立 AI Reviewer 与机器证据生成模块 A/B/C/D 快照。
- [Harness 证据与运行闭环](2026-09-10-harness-evidence-and-runtime-loop-design.md)：让验证绑定当前 worktree，建立实时 E2E、行为目录、运行证据和 CI 生命周期。
- [执行工件模板融合](2026-09-10-execution-artifact-template-integration-design.md)：用唯一计划模板和 active plan 结构门禁吸收 DTPet 模板契约，不建立第二套 Harness 工作区。
- [Harness 运行门禁增强](2026-09-10-harness-operational-gates-design.md)：在现有验证入口中增加 readiness、clean、五子系统报告和 WHAT/WHY/FIX 诊断。
- [领域 README 与 Harness 优化](2026-09-09-domain-readme-and-harness-refinement-design.md)：由所属模块维护的领域语言、简明的根开发者入口、有边界的 Netty WebSocket 选型说明，以及面向生命周期的 Harness 改进。
- [Unified management console](2026-09-09-unified-management-console-design.md): shared quiet high-density Element Plus visual and interaction contract across independently deployed IAM, Admin, Audit and Monitor consoles, including local port convergence.
- [IAM console interaction](2026-09-08-iam-console-interaction-design.md): scan-first management shell, application detail context, OAuth Client list, drawer-based editing and recoverable operation feedback.
- [IAM Catalog client and OpenAPI convergence](2026-09-08-iam-catalog-client-and-openapi-convergence-design.md): dedicated permission-catalog machine credentials, Spring-side Claim type restoration and explicit MVC documentation paths.
- [Web OpenAPI and IAM Introspection](2026-09-08-web-openapi-and-iam-introspection-design.md): shared Springdoc ownership in `im-web`, authenticated IAM documentation and unambiguous OAuth Token reconstruction for default Introspection.
- [IAM management login](2026-09-07-iam-management-login-design.md): IAM-owned Vue login page, standard Spring Security form authentication, safe SPA continuation, saved OAuth request restoration, and CSRF handling.
- [IAM security hardening](2026-09-02-iam-security-hardening-design.md): resource-audience Introspection, consistent producer identity, atomic OAuth credential consumption, immediate SSO expiry, and login timing hardening.
- [Snowflake configured mode](2026-09-01-snowflake-configured-mode-design.md): explicit STATIC or REDIS selection in `im-identity`, mode-specific validation, and removal of business-owned Snowflake Bean factories.
- [Identity module ownership](2026-09-01-identity-module-ownership-design.md): complete Snowflake capability ownership in `im-identity` with optional Redis integration and direct runtime consumers.
- [Redis Snowflake machine ID](2026-09-01-redis-snowflake-machine-id-design.md): Redis-backed process leases for dynamic Snowflake machine IDs, generic plugin ownership, and IAM integration.
- [IAM and Monitor hardening](2026-08-30-iam-monitor-hardening-design.md): staged authorization correctness, IAM DDD convergence, bounded Monitor aggregation, and protocol/time boundary isolation.
- [Excel plugin boundary](2026-08-28-excel-plugin-boundary-design.md): generic Fesod streaming export lifecycle in `im-excel`, with business row models, querying, authorization and auditing retained by consumers.
- [Central management audit](2026-08-28-central-management-audit-design.md): independent Audit Server/SDK, Kafka or asynchronous HTTP ingestion, unified business and security audit storage, IAM-protected query, and bounded Excel export.
- [Web infrastructure boundary](2026-08-27-web-infrastructure-boundary-design.md): generic HTTP request metadata and `HttpResult` ownership in `im-web`, optional Session Servlet adaptation in `im-session`, and unified Admin/Monitor/IAM SDK response behavior.
- [Management IAM](2026-08-26-management-iam-design.md): centralized administrator identity, OAuth2/OIDC SSO, application-scoped RBAC, realtime introspection and bounded stale-cache fallback for management applications.
- [IM Admin DDD structure alignment](2026-08-26-im-admin-ddd-structure-alignment-design.md): canonical business-module layers, common pagination, CQRS boundary values and real Management Architecture coverage. Implementation completed.
- [Dynamic Harness feedback loop](2026-08-25-dynamic-harness-feedback-loop-design.md): mandatory feedback assessment, Review-only rule handling, and optional internal query-condition structure. Implementation completed.
- [IM Admin user management](2026-08-24-im-admin-user-management-design.md): independent administrator authentication, RBAC, audit, and Account-owned user administration. Implementation completed.
- [Documentation and Harness structure](2026-08-23-documentation-harness-structure-design.md): documentation ownership, stable engineering references, Harness enforcement boundaries, and current-fact cleanup. Implementation completed.
- [Review findings remediation](2026-08-23-review-findings-remediation-design.md): canonical Session locking, authentication lifetime alignment, refresh routing, chat unread behavior, Broker owner routing, and README context restoration. Implementation completed.
- [README and message notification documentation](2026-08-23-readme-notification-documentation-design.md): README ownership, end-to-end notification and ACK flow, receipt retry semantics, and current contract-name cleanup. Implementation completed.
- [Message chat view presence](2026-08-21-message-chat-view-presence-design.md): message-owned current chat view state and unread behavior. Implementation completed.
- [Centralized session authentication](2026-08-14-centralized-session-authentication-design.md): centralized online authentication, rotating refresh tokens, and single-device session replacement. Implementation completed.
- [Broker business monitoring](2026-08-13-broker-business-monitoring-design.md): separated Broker diagnostics, Monitor aggregation/UI, and future Admin ownership. Implementation completed.

## Historical Designs

- [Audit RPC ingestion](2026-08-30-audit-rpc-ingestion-design.md): withdrawn synchronous Dubbo audit transport; current Audit ingestion uses Kafka or authenticated asynchronous HTTP.
- [IM Admin declarative audit and security context](2026-08-26-im-admin-audit-aop-design.md): historical local audit-first design; audit ownership and failure semantics are superseded by the central management audit design.
- [Group member and chat boundary](2026-05-11-group-member-chat-boundary-design.md)
- [Open chat lifecycle](2026-05-12-open-chat-lifecycle-design.md)
- [IM module split](2026-05-20-im-module-split-design.md)
- [Architecture upgrade](2026-06-08-architecture-upgrade-design.md)

New documents use `YYYY-MM-DD-<topic>-design.md` and must link to their execution plan when implementation begins.

- [Peer 身份 URI 前缀统一命名](2026-10-25-peer-identity-uri-prefix-design.md)：用户批准，独立设计 Reviewer `a0b58853328dc8e68` Approved，无必须项；在现有 T3 实施，无旧别名，clean/full 暂停。

Settings repository-only 有限语义补充已技术 Approved，原件 `.harness/t11/coverage-settings-design-review-attempt2.json`；无新增执行授权，原 Revise 与设计 SHA 归属边界保留。

Active-profiles 有限输出补充已技术 Approved/findings none，原件 `.harness/t11/coverage-active-profiles-design-review-attempt1.json`；受审设计 SHA256 `b307f1f891a83dd080cab42c5058abf3c0ae23af6f72d556bfb40cc94ed90e39`。技术批准不当新用户授权；T11 先 inert 正文/settings 同轮关联，外壳独立后续验收，bootstrapComplete=false。
