# Broker 收口前进度历史（2026-10-20）

本文保留收口前的时间顺序记录，不表示当前状态；当前恢复入口为根 PROGRESS.md 与10-11计划的关闭账本。

- 当前增量：用户批准Bolt连接层认证测试原型，设计 reviewer `0461448d-b90c-462` Accept；仅验证public hook下握手/身份前发送屏障、同址异身份池隔离、并发/重连/清理，不切换production。范围与恢复见既有A5及新原型设计；不能实现则明确blocked，不fork/反射或削弱原规则，其他WIP与全局门禁缺口保留，不提交。

- 当前增量：用户批准试用 BoltClientInvoker.invokeAuthenticated 可读性整理，设计 reviewer `b089e8c6-68d3-4a6` 中止恢复后 Accept；仅TLS身份验证/失败分类私有职责提取及命名注释，预算采样/分类/同连接调用/普通invoke保持，范围与验证见既有A5。其他WIP/全局验收缺口保留，不提交。

- 当前增量：用户批准观察请求/响应角色字段成组命名，设计 reviewer `2f8cd490-5634-4ed` Accept；receiverBrokerId/brokerIds/observerBrokerId/行brokerId与领域语义对齐，wire破坏性改名需成组部署，无旧alias。准确范围/验证见既有A5，其他WIP/发布与完整验收缺口保留，不提交。

- 当前增量：用户批准 BrokerHealthObservation领域字段 brokerId/observerBrokerId/unreachableBeginAt 与fluent Lombok Getter及注释，设计 reviewer `c21875b3-8361-4b1` Accept。SDK/Query/DTO与Raft格式不改，边界显式映射，准确范围/验证见既有A5；其他WIP/发布及最终门禁缺口保留，不提交。

- Follower 本地修复收尾：受影响 im-raft 全模块检查通过，当前代码与四份拥有者文档已对齐本机 applied stable membership、joint 失败关闭及私有制品仅隔离安装的事实。精确命令/范围/恢复见既有 A5；不启动无关服务或扩展检查。架构定向、全仓最终门禁及制品发布/复现性缺口仍保留；A5 active/WIP=1、其他 WIP 保留，不提交。

- 当前增量：用户批准evaluate四参数，报告证据资格与冷却State职责分离，设计 reviewer `a902b2d6-2902-497` Accept；删除重复冷却输入/配置/无caller elapsed API，保留应用前置冷却和末尾任期/Context重验。准确范围/行为差异及证据见既有A5，其他WIP/制品发布与全仓门禁缺口保留，不提交。

- 当前 Follower 本地修复闭环：单候选访问器 16 项、Runtime 22 项、Broker 聚焦 87 项通过；Runtime/HA fixture 独立 Review Accept。原三项 `AutomaticFailureTlsHaTest` 全部通过，证据与边界见 A5。原 Leader-only membership 阻塞在本地候选范围已解除；Gateway ACK 仍为替代边界，不是真实 Gateway E2E。私有制品仅安装在 `/tmp/jraft-artifact-m2`，发布、双构建/输入锁及最终全仓门禁未完成，A5 active/WIP=1、其他 WIP 保留，不提交、不自动扩跑。

- 当前增量：用户批准试用观察起始读取本机Leader任期，汇合后一次quorum确认，advanceEpoch证明不改；设计 reviewer `900ac3d5-7bfd-4a4` Accept，API边界/精确范围/验证见既有A5。本机任期不等同多数派证明，不保证提交原子绑定任期；其他WIP/门禁缺口保留，不提交。

- 当前增量：用户批准先删除观察整轮elapsed及耗时上限，设计 reviewer `d78ece3a-da91-47b` Accept；单RPC预算/freshness/领导权和Context/冷却保留，移除无用App timeout注入，准确范围与证据见既有A5。私有JRaft依赖解析及其他WIP/Follower Block保留，不提交。

- 当前增量：用户批准试用观察时间用途拆分，设计 reviewer `091d1def-a76a-446` Accept；取消App整轮墙钟roundAt，本机快照和最终评估各取当前时间，保留单调冷却/整轮预算，接受freshness墙钟跳变风险。精确范围与证据见既有A5，其他会话WIP/Follower Block不关闭，不提交。

- 当前执行范围收窄（用户批准）：Follower membership 本地修复只走单候选 JAR、16 项访问器测试、隔离消费者/单实现、im-raft 接入及三项 HA；不跑双构建、不启动无关服务、不设18GiB硬门槛。发布/可复现与最终门禁独立保留，未完成不能宣称生产采用。决定见既有私有 JRaft 设计后续分轨，A5 active/WIP=1，其他 WIP 保留，不提交。

- 当前增量：用户批准试用 fetchHealthReport 在Adapter任务内RPC前取now、去掉时间入参，设计 reviewer `dd5a86a1-79e3-481` Accept；独立墙钟误差取舍及准确范围见既有A5。应用整轮单调预算/本机基准不改，其他WIP/Follower Block保留，A5 active/WIP=1，不提交。

- 当前 JRaft 制品恢复：用户批准休眠 POM 声明只审计、实际有效/解析输入继续拒绝动态版本，决定已写入原私有制品设计；worker `2cbd3441` 实施测试与边界调整。实体扫描、版本分类、user.name 固定及 module POM 可复现配置分别已获 scoped Accept；新补丁 f83c0943 尚无双构建证据，不切换生产依赖。后续证据/恢复见既有 A5，Follower Block 与其他 WIP 保留，不提交。

- 当前增量：用户批准试用报告统一 roundAt，删除逐请求起点推算；设计 reviewer `68216f03-79b3-4cc` Accept，保守失效差异/精确范围/恢复见既有 A5。双基准紧邻采样，报告持续失败不延长；其他WIP/Follower Block保留，A5 active/WIP=1，不提交。

- 当前增量：probe/report 绝对请求期限成套删除，AppService.getHealthReport / PeerAdapter.fetchHealthReport 命名及领域请求包装移除已实施；设计 reviewers `296f36fe`/`dc8b6bb6` Accept，合并实现 reviewer `92a0a6c5` scoped Accept。requestStartedAt公式按用户决定暂不动；当前 architecture 因运行期间工作树变化证据无效，恢复/门禁及成组部署取舍见既有 A5 两项增量。保留调用预算/报告归一化/身份版本/freshness/整轮重验，其他 WIP/Follower Block 保留，A5 active/WIP=1，不提交。

- 当前增量：用户批准观察状态三参数 API，空 voters 由应用 invalidate 后返回，设计 reviewer `21f20619-c93e-4aa` Accept；精确施工及证据见既有 A5。恢复完整冷却窗口/同步/时源位置不改，接手命名 WIP 保留，A5 active/WIP=1、Follower Block 不关闭，不提交。

- 当前增量：继续 reconcileFailures 可读性收敛，四项精确整理已批准，设计 reviewer `442d0068-90fd-43c` Accept；范围、恢复和验证见既有 A5。报告汇合/采样/安全边界不改，缓存容量失败与 Follower Block 分别保留，A5 active/WIP=1，不提交。

- 当前增量：用户批准未完成故障操作筛选/唯一性检查归 `BrokerCluster.findUnfinishedFailureOperation`，设计 reviewer `5a5dd0a8-a475-463` Accept；应用只保留恢复编排，准确范围和证据见既有 A5。A5 active/WIP=1、其他 WIP/Follower Block 保留，不提交。

- 当前增量：用户批准 `BrokerObservationState.advance` 四参数 API，固定 cooldown 构造注入、非 Leader 只 invalidate；独立设计 reviewer `e9dfaa4f-42df-42f` Accept，精确范围与验证见现有 A5 同名增量。AppService 构造契约不变，无包装/新 Bean，A5 active/WIP=1、其他 WIP/Follower Block 保留，不提交。

- 当前增量：用户批准 `reconcileFailures` 两项重复守卫精简，独立设计 reviewer `20c9ae16-945a-491` 第二轮 Accept；先验证合法 Commit/成员 Begin 的 revision 不变量，精确范围及证据见现有 A5「重复守卫收敛」。保留跨异步安全检查，A5 active/WIP=1、其他 WIP/Follower Block 不关闭，不提交。

- 当前增量：用户批准 `BrokerObservationState` 领域归属、字段/API 与单调时间类型，独立设计 reviewer `4f4464bb-d962-45c` Accept；精确施工、证据和恢复见现有 A5 同名增量。保留冷却行为/同步边界/应用锁和 IO，A5 active/WIP=1、其他 WIP/Follower Block 不关闭，不提交。

- 当前命名维护：用户批准 `observeFailures` → `reconcileFailures` 并完善 Javadoc；仅同步调用引用，不拆流程，范围及验证见现有 A5「命名维护：reconcileFailures」。A5 active/WIP=1、Follower Block/其他 WIP 和完整验收缺口保留，不提交。

- 当前增量：用户批准 healthReport 响应回显与后置墙钟检查收敛，独立设计 reviewer `fc7eaedb-54c7-46f` Accept；精确范围、RED、fixture 归因及后续证据见既有 A5「healthReport 响应校验收敛」。保留请求及报告内容校验、应用层重验，SDK 不变；A5 active/WIP=1、其他 WIP/Follower Block 保留，不提交。

- 当前优先任务：用户要求先解除 Follower membership 能力阻塞，沿既有私有 JRaft 制品路线恢复。快照确定性测试修复设计/代码独立 Review Accept；隔离完整上游单次构建已 BUILD SUCCESS，详细证据、2 项跳过及历史 Leader 时序失败保留在 A5。用户批准继续正式 patch/provenance/recipe 收敛，后台 worker `89b65746`，独立 reviewer `99725111` 核对访问器退出条件覆盖；尚不切换生产依赖、不发布，Follower Block 不关闭。其他会话已有 healthReport WIP 保留；A5 active/WIP=1，不提交。

- 当前增量：用户批准 observeFailures 完成链及报告 Stream 收敛，独立设计 reviewer `3085c5cd` Accept；精确范围和 Error 边界差异见本机锁设计及既有 A5。冻结基线 quick exit 0/448 秒；修改后聚焦 36 项 exit 0/13 秒，独立 reviewer `1fb8396b-73f2-4b4` scoped Accept/findings none，Broker reactor exit 0/70 秒（SDK 38、Server 683 项）。本次回填后冻结工作树，顺序执行 architecture/behavior/quick/clean/full，日志及恢复见既有 A5，最终结果由后台报告返回，不以旧 quick 冒充当前树验收。A5 active/WIP=1、其他 WIP/Follower Block 保留，不提交。

- 当前采样小维护：私有 ProbeSample 等价迁为 `support.health.BrokerHealthSample`，probeFailures 引用同步，行为不变；恢复和证据见既有 A5。前轮 probe 职责收敛已按用户批准实施并获独立代码 Accept；本轮其他方法优化目标待明确。A5 active/WIP=1、其他 WIP/Follower Block 与完整门禁缺口保留，不提交。

- 当前命名维护：用户指定 `LocalLock` → `MutexLock`，仅同步 im-lock 类/测试文件名与引用、README 和设计正文；设计文件路径保留。模块 clean test、clean 通过，quick 超时，full 未运行，证据见既有 A5；保持用户 Review 待进行和既有验收缺口，不提交。

- 当前增量：按用户要求仅在 im-lock 实现 `core/LocalLock implements Lock`，AQS 二态、非线程所有权、非重入/非公平；无 Registry/Template/Broker 接入。设计 reviewer `8bcc59eb` Accept，测试先行 API RED、独立实现复审 `203e8f18` Accept；用户 Review 待进行。实施及验证缺口见既有 A5，保持 active/WIP=1，不关闭其他 WIP/Follower Block，不提交。

- 当前身份维护：删除 AppService 的 `observationLocalId` / `observationClusterId` 及构造注入，分别统一读取 `BrokerRaftAdapter.getLocalBroker().getId()` / `getClusterId()`；Adapter 保存 typed 集群 ID，配置及 22 处测试构造调用同步。上一轮磁盘 ENOSPC 导致状态回填失败，本轮已恢复可写并补录。当前 readiness、观察/Raft 聚焦、Broker reactor、HA test-compile、architecture、clean 通过；quick 180 秒超时，full 未运行。细节见既有 A5；A5 active/WIP=1、其他 WIP/Follower Block 与独立复审/完整验收缺口保留，不提交。

- 当前小维护：按用户批准将 `BrokerClusterAppService.probeFailures` 改为顺序 `stream/filter/map/reduce(thenCombine)`，删除任务集合；完整异常处理、Context 重验、单调计时和 finally 闸门释放保留，不新增 helper。聚焦 17 项、Broker reactor、architecture、clean 通过；quick 300 秒超时，full 未运行，独立复审及完整验收缺口保留。详情见既有 A5；A5 active/WIP=1、其他 WIP/Follower Block 保留，不提交。

- 当前小维护：仅将 `BrokerClusterAppService.probeFailures` 的 `requests` / `request` 改为 `probeTasks` / `probeResult`，全文件六处精确替换等价核对通过；聚焦 17 项、Broker reactor、architecture、clean 通过，quick 180 秒超时，full 未运行。类名与探测行为不变，详细命令及恢复见既有 A5；A5 active/WIP=1、其他 WIP/Follower Block 保留，不提交。

- 当前增量：用户批准健康观察 Context 改为 revision/topology/targets 结构比较，删除字符串 generation；DDD、Raft/Gossip 与健康观察按零基础读者补入README/Architecture并配图。独立设计 reviewer `05870a5a` Approved，施工见既有A5「结构化 Context 与图文入门」。采样与冷却重置条件分离、双重异步重验保留；WIP=1、Follower Block和其他WIP/历史缺口不关闭，不提交。

- 当前增量：用户批准 ConsensusMembership 使用领域 Set，RaftMembership 仍为 List<String>，同角色重复归一化、交叠构造拒绝；观察签名显式排序，防止排列差异重置采样/冷却。独立设计 reviewer `2939fb39` Approved，施工见既有 A5「ConsensusMembership 集合语义」；typed observationClusterId/入口领域值等WIP保留，WIP=1、Follower Block与历史门禁缺口不关闭，不提交。

- 当前增量：用户批准技术 RaftMembership 保留 String 列表，Broker Adapter 转领域 ConsensusMembership，一致性规则迁入 BrokerCluster.findConsistentVoters；独立设计 reviewer `e76a97c8` Approved。新决定取代前轮共享容器方向，MembershipEndpoint 仍归 common；施工见既有 A5「共识成员边界与 findConsistentVoters」。WIP=1、其他 WIP/Follower Block/历史验证缺口保留，不提交。

- 当前增量：用户批准将 MembershipEndpoint 迁至 common，共用于 RaftMembership 与 Broker；独立设计 reviewer `fd740c07` Approved，施工见既有 A5「共享成员端点」。保持不透明字符串、列表顺序/重复、v2 JSON 及依赖方向，WIP=1；磁盘空间问题、Follower Block 与其他 WIP 保留，不提交。

- 当前维护：按用户要求移除 BrokerClusterAppService 观察时源注入，并将指定观察流程 helper 展开后梳理；Gossip 重复 ConnectionKey 身份复用 ConnectionRouteId，字符串编解码/传输载荷仍归 Adapter。Coding、Test、Review 与 Harness 同步时源必要性及「先理清流程再封装」的 Review-only 原则。施工、证据与恢复见既有 A5「观察时源最小化与 Harness 反馈」，WIP=1；其他 WIP、Follower Block 和历史验收缺口保留，不提交。

- 当前两项验证阻塞修正：用户批准、独立设计 reviewer `6cc49b88` Accept；现有 A5 active/WIP=1。Peer probe 三态分类归 Adapter、Bolt 唯一构造器注入单调时源以去除两处 TLS sleep；readiness、聚焦基线及 GREEN 70 项、Broker reactor 621 项/Bolt 20 项、LayerBoundaryTest 30 项、HA test-compile、drift/diffcheck 均通过，唯一构造器 API testCompile RED 已记录；限定 diff/逐字等价与 48 组真值表见 A5，独立实现 reviewer `f7598852` scoped CODE Accept、findings none；现由单一协调者顺序执行完整 architecture/behavior/e2e/startup/quick/clean/full，结果待记录，范围和恢复见 A5 末节。保留其他 WIP/Block，不提交。

- 当前维护：健康观察 Tracker 精确 support.health 迁移及 beginCommandFrom 合并已实施；设计 reviewer `59f3550d` 第二轮 Accept、阅读性 reviewer `6952162a` Accept。冻结 App/Tracker/Begin Builder 等价证据已核对，readiness、聚焦 35/66 项、Broker clean reactor 601 项、5 项架构边界回归、HA test-compile 通过；完整 architecture 75 项/1 failure/2 skipped 仅保留既有 App→BoltException 违规。范围/日志见既有 A5 两项 checkbox，独立实现 reviewer `62f3d1db` 两项维护 Accept、findings none，阅读性 reviewer `6952162a` Accept；整体正式 Revise/Follower Block 保留；最终 quick/clean/full 均 exit 1（既有 Bolt TLS Thread.sleep drift），startup 十应用 smoke 成功但因并发仓库变化导致 Harness exit 1/证据无效，behavior/e2e 未运行；具体日志归因见 A5，A5 active/WIP=1，保留 Follower Block/其他 WIP，不改 JRaft/依赖、不提交。

- 最新结构增量：观察 State/Topology 私有内嵌 AppService，纯 voter 身份规则合并为既有领域服务 findObservationVoters；用户批准、独立设计 reviewer `9f9c5cb3-857b-488` Accept、findings none。现有 A5 active/WIP=1，施工与精确基线见 A5「观察状态内聚与领域规则合并」；不改 JRaft/依赖，整体 Follower Block 保留。本轮聚焦 GREEN、Broker reactor 597 项、im-raft 39 项与 HA test-compile exit 0；architecture 73 项/1 failure/2 skipped，唯一既有 App 对 BoltInvocationException 依赖违规已按接手快照归因，未改规则。核心 State/规则 tokens 等价证据及限定 diff 已记录，现暂停父会话独立代码 Review；最终/风险门禁未运行，整体 Follower Block 不变，不提交。 quick/clean/full 均 exit 1：既有 BoltAuthenticatedTlsTest Thread.sleep drift；startup exit 0（10 apps）。独立代码 reviewer `639572d6` narrow 36-scope Accept/findings none，formal Revise 仅保留既有 architecture/交付缺口；未宣称完成。

- 当前增量：[自动故障观察与摘流接入](../../../docs/design-docs/2026-10-16-broker-automatic-failure-observation-design.md)完整Review为Block三项P1；Broker冷却失效与monotonic round freshness已修并聚焦验证，待复核。真实三BrokerTLS HA暴露JRaft1.4.1无法在Follower经现有API读取完整已应用成员配置，能力阻塞未解决、未改im-raft或降低规格；父会话调查选项。精确194项聚焦证据、HA失败及恢复入口见[现有A5](../../../docs/exec-plans/active/README.md)。A5 active/WIP=1，无提交，其他WIP保留。

- 当前结构增量：用户批准 Transformer 分组/State 命名与精确 support.monitoring 技术观察例外；独立设计 reviewer `7cf18e69-3af8-449` Accept、findings none，施工清单见活跃 A5「Transformer 与 Monitoring 结构归属」。聚焦基线、真实架构 red、机械迁移及 green/reactor/架构验证已执行，归档逐字节等价证据见 A5；独立实现 reviewer `8103be7d-5b67-4bd` 代码 Accept、findings none，正式 Revise 保留；最终 quick/clean 已执行，full 在 routing-ha 构建的未触及 Message 测试因无效字节码失败，精确结果/日志/指纹见 A5。状态回填后刷新稳定 quick/clean，以 Harness 为准。保留 BrokerFailureObservationService 及其他 WIP，A5 active/WIP=1、不提交，历史门禁缺口不关闭。

- 最新命名增量：用户批准 RegistryGossipAppService → BrokerGossipAppService；八文件机械改名与类职责注释完成，基线/green 各 2 项、Broker reactor 523、architecture 70（2 skipped）通过，快照机械替换逐字一致。范围/日志/限定 diff 见活跃 A5「Gossip 应用服务命名收敛」；父会话最终 Review/门禁待完成，A5 active/WIP=1，保留其他 WIP/历史缺口，不提交。

- 最新 Gossip 增量：设计 reviewer `fdaeb0fa` Accept、findings none 后，GossipSyncTracker 归精确 `support.gossip`，BrokerStateStore 等价改名 BrokerGossipAdapter、保留 `adapter.gossip`；所有 caller/直接测试同步，精确包与依赖禁令、11 个依赖负例/后代包/领域反向例均逐名覆盖。readiness、聚焦基线/green 30、Broker reactor 523、architecture 70（2 skipped）、diffcheck 通过；真实架构 fixture red 与迁移编译 red 已记录。接手快照 `/tmp/gossip-move-before`、日志/命令见 A5；下一步父会话独立实现 Review 与最终门禁，A5 active/WIP=1、其他 WIP/历史验收缺口保留，不提交。

- 最新范围：用户批准 [Broker 全部自有时间契约](../../../docs/design-docs/2026-10-14-broker-time-contract-design.md)，已持久完整决定及 [A5](../../../docs/exec-plans/active/README.md) 的 SDK/Gateway、内部模型/命令/Server、配置/HTTP/Ops 独占施工清单；专门 reviewer 首轮 Revise；修订后第二轮 Accept、findings none，P1/P2/P3 三组按独占文件范围并行实施，Maven 集成验证由主会话顺序执行。A5 active/WIP=1，保留已有 WIP/历史验收缺口，不提交。新契约与 v2 源码已实施；模块测试、architecture/behavior/E2E/startup 及专门 reviewer 代码文档复审完成，routing-ha 因未触及 Message 测试无效字节码失败，最终门禁与测试先行证据缺口见 A5。本轮不冒称完整交付验收。

- changedAt 最小收敛：独立设计 Review Accept、findings none 后按 A5 实施九个内部采样入口，三个共享显式时间入口不变；真实 Runtime 完整载荷捕获/边界时间断言、ACK/cleanup/恢复共享时间捕获通过。compile red（clean 后参数不匹配）、聚焦 109 项 green、固定古旧时间反证失败且恢复 green。reactor 518 项/0 failures/3 errors，未触及 Controller 的旧 IDE class 出现 Flexible Constructor Bodies/source 25 错误；父会话接手 clean reactor 重试及后续 architecture/behavior、独立 Review、quick/clean/full。精确日志/恢复见[活跃 A5](../../../docs/exec-plans/active/README.md)；A5 active、保留其他 WIP/历史缺口、不提交。

- 活跃计划：[Broker Adapter 职责重划](../../../docs/exec-plans/active/README.md)；本地路由 HA 模拟已归档，正式生产容量按用户决定不纳入本次范围。
- 当前任务：A5 删除万能入口并收尾，WIP=1；A2/A3/A4 的能力已分离并通过 Broker 测试。
- 状态：职责拆分、consensus/LocalBroker 收敛及 A5 类型化 Raft 增量已实施；独立实现 Review 两项已修复（BrokerClient 窄边界、onApply 同批失败隔离）。本轮 RaftRuntimeTest 14 项通过，删除错误分支 return 反证 2 项均失败后已恢复；architecture 68 项、0 failures/errors、2 skipped。Broker reactor 前轮当前源码报告 509 项通过（本轮未重跑）。最终独立复审 Accept、findings none；主会话 Raft 14 项、clean、behavior 34 场景、e2e 1 场景通过。quick/full 在工具 300 秒时限超时，未取得最终结果，计划仍 A5 active；精确命令与日志见计划新增证据节。
- Builder 重试：接手已有 Lombok `@Builder`/调用迁移/README WIP，补充未设置必填输入测试；暂撤注解 red exit 1（缺 Builder 类型），恢复后 SettingsTest 24 + RuntimeTest 14 全通过；Broker reactor exit 0、当前源码 509 项，architecture exit 0、68 项/2 skipped。证据及精确命令见 A5 Builder 增量；不改 Membership、不提交 Git。独立 Review Revise（代码 findings none，基线diff/交付证据缺口）；主会话 quick（900 秒时限）和 clean 已通过，full exit 69：系统 Git 受未接受 Xcode license 阻塞，未代用户接受许可。A5 active/WIP=1。
- Builder 用户纠正历史（2026-10-13，一次性契约已被下一条取代，证据保留）：A5 改为手写 `RaftNodeSettings.Builder` + final class/私有无参构造，Builder 独占目标对象，成功后消费、重用明确 IllegalStateException，失败可修正；无重复 11 字段、长参构造或公开 setter。新增行为 red：Settings+Runtime 41 项/2 failures（旧 Lombok 成功后仍可填充）；green 41 项全通过。Broker 首轮失败（旧增量 class 导致 NoSuchMethodError、Spike membership EPERM），`mvn -q -pl im-broker/im-broker-server -am clean test` 消除旧产物后 exit 0、Broker 512 项全通过；architecture exit 0、68 项/2 skipped。精确命令与日志追加到活跃计划；前轮 Lombok/record 和门禁证据保留为历史，不冒称当前验收。当前纠正独立 Review 与 quick/clean/full 留主 Agent，A5 active/WIP=1，未提交、不改 Membership 或 POM。
- Builder 模式再次对齐（2026-10-13）：按 `BrokerClusterOperationProposal.Builder`、`RoutingLease.Builder` 删除一次性消费/置 null/target 检查和相关契约测试，改为 final `settings` 直接赋字段、`build()` 校验后返回。保留字段映射、输入校验、集合不可变拷贝、失败后可修正；README 去除一次性说明。readiness 通过，Settings+Runtime 39 项通过，Broker compile 通过；Broker reactor 首轮超时、复跑既有 im-cache 容量测试失败（预期 <=2、实际 4），未修改该测试；原命令最终复跑 exit 0，Broker 512 项、im-raft 全套 41 项通过；`git diff --check` 通过。计划记录本轮取代关系和日志，最终门禁留主 Agent，未提交、不改其他 WIP。
- 通用 membership 小维护（用户批准）：删除重复 BrokerConsensusMembership，BrokerRaftAdapter 直接返回 Runtime 的 RaftMembership，fixture 显式排序，真实成员迁移测试补稳定顺序/不可变/快照隔离断言；不改 MembershipResult、不搬包或新增 wrapper。readiness、Broker reactor（当前源码 509 项、im-raft 41 项）、architecture（68 项/2 skipped）、`git diff --check` 通过，全仓 Java 无旧类型残留；精确命令和日志见 A5 新增 checkbox。保留此前失败归因和其他 WIP，无 Git 提交；quick/clean/full 与最终 Review 留主 Agent，A5 active/WIP=1。
- 专项 Review 两项复用维护（用户批准）：删除 Broker 私有 UserId，main/test（含同包引用）全迁到 common 公共 UserId，保留 ConnectionRouteId、null 领域校验与 Long 协议边界；IAM digest 复用 HashUtils.sha256Hex，保留 matches 恒定时间/HexFormat/非法 hex 行为。路由聚焦基线、固定摘要旧实现基线及迁移后 green 已记录；readiness、Broker reactor（当前源码 510 项）、指定 Sha256TokenDigesterTest reactor（3 项）、architecture（68 项/2 skipped）、git diff --check 通过。具体差异清单/命令/日志见 A5 新增 checkbox；无新设计或规则、不提交，其他 WIP 保留。quick/clean/full 和独立复审留主 Agent；A5 active/WIP=1。
- Peer 两项行为保持简化（用户批准）：lookupRoutes 直接接收原 SDK BrokerNode/公共 UserId/领域 RoutingEpoch，删除应用参数包装；quorum 读取直接通用 BoltInvoker 六参数调用，删除仅转发 RaftPeerClient/测试及 im-raft 唯一 im-bolt 依赖，原生 JRaft 不变。RpcOwner 改实际 BoltInvoker 负例，不放宽 support.raft 规则；当前 Architecture/README/Harness 规则与设计决定已同步。聚焦基线 28 项（首轮既有 prepareLease 消息断言漂移已仅对齐测试）、green 29 项、Broker reactor 当前源码 511 项/im-raft 39 项、architecture 68 项/2 skipped、git diff --check 通过；新增断言为 green 补强不冒称 red。精确路径/命令/日志见 A5 新节，quick/clean/full/behavior/e2e、独立 Review 与最终指纹刷新留主 Agent；A5 active/WIP=1，其他 WIP 保留、未提交。
- Peer 简化收尾：lookupRoutes 直接传 SDK BrokerNode/common UserId/RoutingEpoch；quorum peer 读取直接 BoltInvoker，删除 Params/纯转发 RaftPeerClient 和唯一 im-bolt 依赖。Broker 511 项、im-raft 39 项、architecture 68 项/2 skipped 通过；独立 Review 代码 Accept、整体 Revise，文档残留已修正。主会话 behavior 34、e2e 1、quick/clean 通过；full 复跑 exit 17，Docker Hub 镜像授权令牌请求网络超时，完整门禁未通过。精确归因见 A5；不提交、不归档。
- Raft Adapter 方法命名收敛（用户批准）：类名不变，六个公开方法及全部 caller/Mockito/fixture 迁为 clusterSnapshot、submitCommand、commitRoutingFence、raftMembership、beginOperation、commitTopologyChange；Domain commitOperation 与 StateMachine getCluster 保留。已核对 Commit 全部 operation 实质拓扑变化及 Begin/Commit 各推进一次 revision、重复 Commit 不推进的语义；现测试只迁引用、断言不变、无新 API/compat 壳。readiness、聚焦 113 项、Broker reactor 511 项/im-raft 39 项、architecture 68 项/2 skipped、git diff --check 全通过，无命令失败。精确文件/调用迁移计数/命令/日志见 A5 新节，quick/clean/full、独立 Review 与最终指纹刷新留主 Agent；A5 active/WIP=1，其他 WIP 保留、未提交。
- Adapter 六方法命名收尾：代码 Review findings none，正式 Revise（基线diff/最终门禁缺口）；quick/clean 主会话通过，full exit 17，Docker Hub 镜像令牌请求网络超时，完整 Maven verify 未执行。命令与证据见 A5；类名及阶段行为不变，不提交。
- Raft Adapter 阅读性整理（用户批准）：仅 BrokerRaftAdapter 生产文件排序/排版/注释整理及两步 membership lambda 等价展开，未处理 failDrain 行为 finding；33 个公开方法/构造器 token 等价核对通过，接手原文件 `/tmp/BrokerRaftAdapter.before-readability.java`、限定 diff `/tmp/broker-readability.diff`。readiness、聚焦 57 项、Broker reactor 当前源码 511 项（首轮 Spike membership EBUSY/EPERM 失败，原命令复跑通过）、architecture 68 项/2 skipped、diffcheck 通过，精确证据见 A5 新 checkbox；quick/clean/full、独立 Review 与最终指纹刷新留主 Agent，原 WIP/历史缺口保留、未提交，A5 active/WIP=1。
- 最新增量：BrokerRaftAdapter 恢复静态嵌套 LocalBroker，经 getLocalBroker().getId()/isLeader() 明确本机查询；Begin/Commit 成对，查询加 get，submitCommand private，成员失败/完成由具名入口提交。测试 fixture 载荷一致性与真实 Adapter 接线已补强；Broker 513 项、architecture、quick/clean 通过，独立代码复审 Accept。full exit 17：Docker Hub 镜像令牌请求网络超时，正式交付仍 Revise/A5 active。既有 failDrain 行为 finding 保留未修；本轮由主 Agent 实施，仅 reviewer 使用子代理，不提交。
- 状态机构造器收敛：仅保留四参数构造器，23 处测试调用显式传原默认容量和空成员 Map；生产装配不变。聚焦测试、Broker reactor 复跑、quick（含 architecture）、clean 通过；full 第二轮 exit 17，Docker Hub SkyWalking 镜像令牌请求网络超时，未完成全套 verify。当前仓库未找到 LockGuard，未删除未知代码。A5 仍 active，不提交。
- 最新维护：核对并修正 Broker Architecture/Server README、相关设计索引与历史决策标记及两处生产注释；仅事实对齐、不改行为，验证与独立 Review 状态见活跃计划 A5「Broker 文档与注释事实对齐」。A5 保持 active，既有交付缺口不关闭。
- 机器结果：[Harness 报告](../../../.harness/report.json)、.harness/verifications/。
- 历史过程已移入 [Review 历史](../../../docs/exec-plans/history/2026-10-09-broker-review-history.md) 和 [进度历史](../../../docs/exec-plans/history/2026-10-09-broker-progress-history.md)，不作为当前执行清单。
- 下一步及验收缺口只由活跃计划维护，避免重复堆积。
- 新增决策：[Raft 类型化命令接入](../../../docs/design-docs/2026-10-12-typed-raft-command-design.md)独立设计 Review 第二轮 Accept；A5 增量实现及窄验证完成，WIP=1。Runtime 全节点解码、support.raft 精确职责包、日志/快照有意 v1 重置；独立实现 Review 已通过；red/green 与最终门禁缺口见活跃计划，quick/full 未通过前不归档。
