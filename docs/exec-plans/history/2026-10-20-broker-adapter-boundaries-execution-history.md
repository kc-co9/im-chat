# Broker Adapter 职责重划：整理前执行记录

> 本文完整保留文档整理前的计划正文，包括原始 checkbox、阶段性阻塞、Review 结论与命令日志。文中的「当前」「下一步」均属于当时的检查点，不是现行执行指令。当前状态以[计划索引](../active/README.md)为准；后续批准决定不倒改历史证据。

---

# Broker Adapter 职责重划实施计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 保持路由行为与时序不变；A5 类型化增量重置 v1，后续用户批准的时间契约增量同步改 Bolt/HTTP 时间字段与内部类型，并升持久 envelope 为 v2（拒绝 v1、无兼容），将 Broker 集群技术入口拆为领域聚合、本机 Raft Adapter、Broker 对端 Adapter 和 Gateway 投递 Adapter。

**Architecture:** `BrokerCluster` 继续作为唯一有状态的集群领域能力入口；应用服务负责 fence、quarantine、提交、授权和恢复的跨步骤协调；`BrokerRaftAdapter` 连接 `BrokerCluster` 与通用 `RaftRuntime`；Broker/Gateway 外部调用从原 `BrokerClusterAdapter` 移到各自 Adapter。第一阶段优先保持现有公开方法语义和测试夹具，确认行为后再删除旧大类。

**Tech Stack:** Java 21, Spring Boot, SOFAJRaft, Maven, existing Broker Bolt clients, existing deterministic tests.

## Sprint Contract

- 目标：按已确认设计拆分 BrokerClusterAdapter 职责，并执行已批准的类型化 Raft SPI/support.raft 接入；类型化增量历史上重置 v1；本轮时间契约改变自有 Bolt/HTTP 字段、配置键与内部 command 类型，持久 envelope 升 v2、拒绝 v1；路由/Gossip/Lease 行为不变。
- 范围：BrokerCluster 领域入口、本机 Raft Adapter、Broker 对端 Adapter、Gateway 投递 Adapter、类型化 im-raft SPI 与三个精确 support.raft 职责包，及测试/架构规则/文档；时间契约覆盖 Broker Server/SDK 所有自有字段与必要 Gateway、Ops 直接消费者。
- 自动故障增量：用户批准补齐原 D22，voter 主动探测、Leader 拉独立报告、自动发起与全阶段恢复；保守本地冷却，不改 Raft 格式；安全前置 TLS 证明通过后实施。
- 非目标：不新增 BrokerClusterDomainService，不新增业务事实；允许批准的时间字段/key/type 改变，不兼容旧 v1/v5/v6、不自动删数据，不重做 Raft 或 Gossip；consensus/membership 不移动。
- 依赖/发布：时间契约独立设计 Review 通过后实施；同版本 Broker/SDK/Gateway/Ops 成组发布，v1 数据保全但不读取，不自动删数据。现有 Docker 网络门禁缺口保留。
- 验收：聚焦测试、架构检查、behavior、routing-ha、quick、clean、full 按适用范围通过。

## 事实源

- 时间增量事实源：[Broker 自有时间字段契约](../../design-docs/2026-10-14-broker-time-contract-design.md)（用户批准，独立设计 Review 首轮 Revise，修订后待复审；以下施工步骤在 Review Approved 前冻结）。
- 设计或产品规格：[BrokerClusterAdapter 职责重划设计](../../design-docs/2026-10-11-broker-cluster-adapter-boundaries-design.md)
- 模块事实：[Broker Architecture](../../../im-broker/ARCHITECTURE.md)、[Broker Server README](../../../im-broker/im-broker-server/README.md)
- 协议事实：[im-raft README](../../../im-plugin/im-raft/README.md)、[im-gossip README](../../../im-plugin/im-gossip/README.md)

## 验证分层

| 层级 | 命令 | 状态 |
|---|---|---|
| 领域/状态机 | `mvn -q -pl im-broker/im-broker-server -am test` | 通过；上游 im-cache 单次失败复跑后通过 |
| 架构/行为 | `./scripts/verify.sh architecture`、`behavior`、`routing-ha`、`e2e` | 全部通过；routing-ha 25 场景、behavior 34 场景、e2e 1 场景 |
| 交付门禁 | `./scripts/verify.sh quick`、`clean`、`full` | 待最终运行 |

## 任务状态

| ID | 目标 | 状态 | 完成条件 |
|---|---|---|---|
| A1 | 领域与状态机基线 | passing | 聚焦基线与架构检查通过 |
| A2 | 本机 Raft Adapter | passing | BrokerRaftAdapter 持有唯一运行实例和状态机；Broker 全套测试通过 |
| A3 | Broker 对端 Adapter | passing | 全部生产调用方已迁移；旧测试委托由 A5 清除 |
| A4 | Gateway 投递 Adapter | passing | Frame 应用已使用独立 Adapter；旧测试委托由 A5 清除 |
| A5 | 删除万能入口并收尾 | active | 迁移剩余测试/调用方并通过最终门禁 |

## 当前关闭账本（2026-10-20）

用户要求先确定后续认证设计、完善Broker新手文档及复用Harness规则，再关闭本计划；未来统一invoke生产实现不纳入本计划。A1–A4能力已实施；本轮代码/文档收尾完成且对应独立Review无代码finding，A5在代理修复后恢复active（WIP=1），仅完成既有最终门禁，不授权扩展工作，以本账本作为当前恢复入口；下文历史未勾选项须按后续批准决定解释，不能批量勾绿或要求重做已取代架构。

| 类别 | 当前处置 | 关闭条件 |
|---|---|---|
| 已取代设计/步骤 | Builder一次性消费、旧LocalBroker/通用Adapter、旧State/Repository、请求期限/整轮预算/起点转换等均保留历史，按后续决定实施 | 最终集合级Review核对当前代码，不伪造历史RED或覆盖旧失败 |
| 排空failDrain旧finding | 原设计新增收尾修正，reviewer `4da4164b-ba32-4e4` Accept | 两超时路径只在提交accepted后宣称FAILED；rejected等待、异常传播，聚焦red/green与独立代码Review |
| Follower本地候选 | 本地能力已解除原Leader-only阻塞，制品仅隔离repo | 当前Runtime/Broker/TLS HA与总体风险门禁；不声称已发布/双构建复现 |
| 私有制品发布/双构建 | 用户已明确后轨，记录仍由对应私有制品设计拥有 | 不作为本地修复前置，不额外跑上游双构建；保留生产发布未获保证的残余风险 |
| 连接认证新设计 | 原型有限完成；生产统一invoke设计另文档，供用户后续推进 | 本计划只交付原型及设计，不把自然重连/轮换/生产迁移未证项标passing |
| 文档/Harness | 解释新手路径、职责与最新事实；补可复用防御/异常/副作用边界规范 | 独立事实/可读性Review，无未来能力写成当前事实，不新增类名例外 |
| 全树验证/质量 | 旧full失败与指纹历史不消失，但不冒充当前结果；本轮routing-ha Docker Hub令牌网络超时仍阻塞关闭 | 稳定树readiness→聚焦/模块→architecture→behavior→startup→e2e→routing-ha→quick/clean/full；quality独立review/cleanup/handoff；归档后关闭审计 |

- [x] AppService.advanceDrain两条failDrain拒绝分支已修：ACK拒绝等待Gateway，cleanup拒绝等待cleanup，不虚报持久FAILED；accepted/exception原语义不变。新增9个测试调用，真实状态机并发VERIFYING拒绝与恢复覆盖，行为RED23项/4failures后GREEN23项无失败错误，日志 `/tmp/broker-closeout-drain-{red,green}.{log,exit,xml}`。独立 reviewer `2ede5d35-2602-419` Accept，旧failDrain finding关闭，不改超时/协议/状态枚举。
- [x] 新统一invoke生产设计独立文件已完成，reviewer `5151be08-386f-491` Accept-as-design-baseline；O1–O4用户选择与技术可行性仍阻塞实施。原型与未来生产严格区分，索引已登记，不实施新生产代码。
- [x] Broker四份拥有者文档新增新手路线、A/B/C示例、角色/版本/协议分工，拆分Server超长健康段并修正Context实际二字段。Harness最小增量补防御前提/异步重验、cause及真实副作用取证、跨线程实际完成与生命周期同步，无类名例外。独立 reviewer `2aa6d774-dc38-4be` Accept；原历史保留，PROGRESS收敛本账本索引。
- [ ] 本轮按既有隔离repo串行运行：Broker/Bolt/Raft `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server,im-plugin/im-bolt,im-plugin/im-raft -am test` exit0 `/tmp/broker-closeout-reactor.log`；同repo经MAVEN_OPTS architecture/behavior/startup/e2e分别exit0（41行为/10应用/1实时E2E），日志 `/tmp/broker-closeout-{architecture,behavior,startup,e2e}.log`。routing-ha exit17 `/tmp/broker-closeout-routing-ha.log`：Docker构建拉取eclipse-temurin:21.0.12_8-jdk-jammy时auth.docker.io匿名token i/o timeout，容器故障场景未完成，不修改镜像/网络策略或跳过。该环境失败已归因，允许继续诊断quality/quick/clean/full，不把后续通过覆盖它。最终quick exit0/481秒、clean exit0/19秒，full exit17/548秒 `/tmp/broker-closeout-final-{quick,clean,full}.log`，Harness worktreeStable=true；full完成258项无失败/错误/跳过后在SkyWalking镜像token请求i/o timeout，未进入完整Maven verify。正式独立quality reviewer `dfeff792-a36c-408` 全12单元结论Revise，仅保留最终证据缺口，确认failDrain finding关闭；原样响应保存并经 `verify.sh quality`结构校验exit0，语义Revise未改分或降级。cleanup扫描exit0 `/tmp/broker-closeout-cleanup.log`，未apply/删除其他会话临时物。当前A5恢复active（WIP=1）进行最终验证，尚不归档；用户要求继续后重跑routing-ha仍exit17 `/tmp/broker-closeout-resume-routing-ha.log`：eclipse-temurin元数据的auth.docker.io token直连i/o timeout。只读诊断确认Docker Desktop context=desktop-linux、ProxyHTTPMode=system、daemon代理http.docker.internal:3128；macOS系统代理127.0.0.1:7890。宿主HTTPS直连同token接口12秒超时，显式经现有7890代理HTTP200（未记录token），说明可用代理路径与Docker构建实际路径不同，不归因代码。未改全机/Docker代理或重启其他任务，下一步需用户批准Docker Desktop代理调整（可能影响其他容器）后再恢复原routing-ha；禁止换镜像/跳场景。用户明确批准Docker代理调整后，备份 `/tmp/im-chat-docker-proxy-backup/`，Docker Desktop停机后仅改ProxyHTTPMode=manual/HTTP及HTTPS代理=http://127.0.0.1:7890。daemon拉原Temurin及SkyWalking镜像成功；BuildKit仍需本次命令HTTP_PROXY/HTTPS_PROXY=该代理、NO_PROXY=localhost,127.0.0.1,::1，未改镜像或仓库。此前运行redis容器friendly_golick（441fc6a49276）stop后已不存在，恢复start失败，怀疑自动删除但缺完整inspect无法确认；未凭猜测重建/覆盖数据，该环境副作用保留并须告知用户，不宣称已恢复。代理命令下routing-ha exit0/25场景且ownedresources清理通过 `/tmp/broker-closeout-proxy-env-routing-ha.log`，原网络阻塞解除。镜像构建后磁盘低，删除本仓ignored可再生成10个exec.jar（约1.35GiB）及本轮精确BuildKit source.local缓存174.6MB，未删镜像/卷/报告/源码。同代理full首次exit1 `/tmp/broker-closeout-proxy-full.log`：AutomaticFailureTlsHaTest在选举空窗的awaitAutomaticRemoval直接Optional.orElseThrow导致NoSuchElement，未等至既有预算；只改测试为leaders列表hasSize1断言后getFirst，Awaitility可重试且要求唯一Leader，其余故障/时间/阶段断言不改。聚焦真实TLS三项exit0 `/tmp/broker-closeout-ha-leader-wait.log`。下一步冻结后同代理/隔离repo运行full和其余fresh门禁，全部满足才passing并移动completed。归档后冻结tracked文档并重跑受指纹影响的quality/quick/clean/full与适用风险门禁；失败则恢复active，不用后续passing掩盖失败。

## 详细任务

### A1 固定领域状态转换和 Raft 回调行为

**Files:**
- Modify: `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/domain/cluster/model/BrokerCluster.java`
- Modify: `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/adapter/cluster/raft/RaftBrokerClusterStateMachine.java`
- Test: existing `BrokerClusterTest`, `BrokerRaftAdapterTest`, `BrokerLeaseGrantAtomicityTest`

- [x] 梳理现有领域判断：`BrokerCluster.commitOperation` 校验 operation、revision、fence 并推进 epoch；Adapter 的 membership 预判留待 A2 归位。
- [x] 现有三个聚焦测试类已覆盖这组规则与原子性，本阶段没有缺失行为需要新增测试。
- [x] 确认状态机负责命令分派、领域调用、快照恢复和同步边界，领域不依赖 Raft 类型。
- [x] 聚焦 Maven 命令及 `./scripts/verify.sh architecture` 通过。

### A2 收敛本机 Raft Adapter

**Files:**
- Create or rename within `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/adapter/cluster/`
- Modify: `BrokerClusterAdapter.java`, `LocalBroker.java`, `BrokerClusterConfiguration.java`
- Test: Raft and cluster configuration tests

- [x] `BrokerRaftAdapter` 持有唯一 `RaftRuntime`、状态回调、snapshot/restore、共识提交、membership、ReadIndex 和 close。
- [x] 日志应用、快照恢复和本机 Lease 签发仍经过同一 `RaftBrokerClusterStateMachine` 同步边界。
- [x] 未新增 `BrokerClusterDomainService`，领域未依赖 Raft/JRaft 或 SDK DTO。
- [x] Raft 日志与快照格式未改；状态机类是否内联留待 A5 删除旧大类时判断。
- [x] `mvn -q -pl im-broker/im-broker-server -am test` 通过；routing-ha 留待最终运行门禁。

### A3 拆出 Broker 对端通信 Adapter

**Files:**
- Create: `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/adapter/cluster/BrokerPeerAdapter.java`
- Modify: `BrokerClusterAdapter.java`, `BrokerClusterAppService.java`, `BrokerMembershipAppService.java`
- Test: peer identity, timeout, executor rejection, fence, lease and cleanup tests

- [x] 已移出 `fetchQuorumReadIndex`、`prepareLease`、`installLease`、`fence`、`cleanupRoutes` 和 `lookupRoutes` 到 `BrokerPeerAdapter`；保留响应身份、超时和两个独立执行器。
- [x] 已移出 `fetchQuorumReadIndex`、`prepareLease`、`installLease`、`fence` 到 `BrokerPeerAdapter`；保留目标身份、超时和有界 peer executor 语义。
- [x] 生产调用方已选定 `BrokerNode` 后调用对端 Adapter；Membership、Frame 与 Cluster 应用服务不再借旧集群 Adapter 发起对端 RPC。
- [x] 对端 Adapter 保留响应身份核对、超时、peer/lookup 独立执行器、单跳和 deadline 约束；RouteLookup 误用 peer executor 的回归已由现有测试捕获并修复。
- [x] fence 未确认后的 quarantine 和 cleanup 证据仍由 `BrokerClusterAppService` 处理；Broker 全套 Maven 测试通过。

### A4 拆出 Gateway 投递 Adapter

**Files:**
- Create: `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/adapter/gateway/GatewayDispatchAdapter.java`
- Modify: `FrameDispatchAppService.java`, `BrokerClusterConfiguration.java`, old adapter wiring
- Test: `FrameDispatchAppService*Test`, gateway write failure/metrics tests

- [x] 移出 `dispatchFrame`、Gateway endpoint 转换、结果映射、专用 executor 和指标到 `GatewayDispatchAdapter`。
- [x] Frame 应用服务已使用独立 Gateway dispatch 能力，聚焦测试通过。
- [x] 保持逐连接结果、写帧失败分类、指标和背压行为不变；`quick`、`architecture`、`drift` 通过。

### A5 删除万能入口并同步文档

#### 健康观察 Tracker 与 Begin 转换收敛（用户批准）

独立设计 reviewer `59f3550d` 第二轮 Accept、findings none（首轮唯一 minor 为补齐 CODING_GUIDE 所有者文件，已修）；阅读性 reviewer `6952162a` Accept、findings none。冻结 App SHA256 `f536e0ddefd3b7a6c9cd54cad65f309092ac16c4cd7b3f651c2a8a75ed4b2bd2` 已核对，接手全部 task 文件快照 `/tmp/health-tracker-relocation-before/current`，不得破坏已接受阅读性结构。此前阅读性聚焦 11 类 61 项、Broker reactor 601 项 exit 0，日志 `/tmp/broker-observation-readability-before/{after2,reactor}.log`。A5 active/WIP=1，不提交、不改 JRaft/依赖。

- [x] Store 机械迁移至精确 support.health.BrokerHealthObservationTracker，直接测试、全部 caller/config/HA 同步；App 仅机械引用替换。范围与基线清单见 current/scope-before.txt；规范同步 CODING_GUIDE、MODULE_STRUCTURE_GUIDE、Broker Architecture/README、HARNESS_GUIDE/矩阵；LayerBoundaryTest 共用生产/fixture 规则覆盖精确包及逐项禁止依赖。验证 readiness、Store/观察基线与 GREEN、真实架构 RED/GREEN、Broker reactor、architecture、HA test-compile；保留既有 App→BoltException 架构失败，不虚报全绿。
- [x] Begin 转换独立小维护：仅 BrokerClusterRaftTransformer、BrokerRaftAdapter 与直接测试/caller，公开 beginCommandFrom 直接构造原 proposal 和 Begin 命令，删除无独立 caller 的 proposalFrom；完整字段和序列化断言。验证聚焦基线/GREEN、Broker reactor 与架构；无兼容 wrapper、无命令模型或行为变更。

实施后提供限定 diff、机械等价与精确命令日志，暂停独立代码 Review；最终 quick/clean/full 由父会话在 Review 后执行。整体 Follower Block 和历史门禁缺口不关闭。

本轮证据（日志目录 `/tmp/health-tracker-relocation-before/current/`，每个 log 仅对应本轮命令）：

- `./scripts/verify.sh readiness` → `readiness.log` exit 0。
- `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerHealthObservationStoreTest,BrokerRaftAdapterTest,BrokerClusterSnapshotIsolationTest,BrokerLeaseGrantAtomicityTest -Dsurefire.failIfNoSpecifiedTests=false test` → `baseline.log` exit 0，23 项。Begin 十字段捕获与 JSON round-trip 补强在原 mapper 上 `-Dtest=BrokerRaftAdapterTest` baseline exit 0（`begin-strengthened-baseline.log`），是无行为维护断言，不冒称行为修复 RED。
- `mvn -q -pl im-test/im-architecture-test -am -Dtest=LayerBoundaryTest#brokerHealthSupportIsAnExactSafetyInputBoundary+healthSupportRulesAcceptSamplesAndRejectOwnershipLeaks -Dsurefire.failIfNoSpecifiedTests=false test` → `architecture-red.log` exit 1：两项断言分别确认生产精确包尚不存在、合法 fixture 未获许可；迁移并精确许可后同命令 `architecture-green.log` exit 0，2 项。没有临时放宽原规则。
- `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerHealthObservationTrackerTest,BrokerAutomaticFailureObservationTest,BrokerAutomaticFailureFlowTest,BrokerFailureProbeTest,BrokerFailureObservationStateTest,BrokerFailureObservationRoundTest,BrokerFailureObservationConfigurationTest,BrokerHealthObservationPrecisionTest -Dsurefire.failIfNoSpecifiedTests=false test` → `tracker-green.log` exit 0，35 项。
- `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerRaftAdapterTest,BrokerClusterSnapshotIsolationTest,BrokerLeaseGrantAtomicityTest,BrokerClusterAppServiceChangeTest,BrokerClusterAppServiceActivationTest -Dsurefire.failIfNoSpecifiedTests=false test` → `begin-green.log` exit 0，66 项。
- `mvn -q -pl im-broker/im-broker-server -am test` → `reactor.log` exit 0；随后 `mvn -pl im-broker/im-broker-server -am clean test` → `reactor-clean.log` exit 0，当前源码 Broker 601 项（不累计旧 surefire XML）。
- `./scripts/verify.sh architecture` → `architecture-full.log` exit 1：75 项，1 failure/0 errors/2 skipped，唯一失败是接手前已知 App.probeStatus → BoltInvocationException 三处依赖，不归因本维护、不弱化规则。保留原失败后窄验证 `mvn -q -pl im-test/im-architecture-test -am -Dtest=LayerBoundaryTest#brokerHealthSupportIsAnExactSafetyInputBoundary+healthSupportRulesAcceptSamplesAndRejectOwnershipLeaks+raftSupportRulesAcceptOnlyApprovedPackagesAndRejectOwnershipLeaks+gossipSupportRulesAcceptOnlyApprovedPackagesAndRejectOwnershipLeaks+monitoringSupportRulesAcceptIntegrationAndRejectOwnershipLeaks -Dsurefire.failIfNoSpecifiedTests=false test` → `architecture-regression-green.log` exit 0，5 项。
- `mvn -q -pl im-test/im-routing-ha -am -DskipTests test-compile` → `ha-compile.log`、`ha-compile-final.log` exit 0，仅编译，不证明 Follower 能力或真实 HA。
- `equivalence.txt`：冻结 App 全文件仅包名/类型机械替换后逐字节等价；Tracker 全文件仅包/类型替换后逐字节等价；Begin 原 proposal Builder 表达式仅外包 Begin 构造后逐字节等价。无 Java `BrokerHealthObservationStore`/`proposalFrom` 残留，`git diff --check` 通过。`task.diff` 与 `changed-files.txt` 提供相对冻结基线的准确范围（不含其他 WIP）。
- [x] 独立实现 reviewer `62f3d1db`：本两项维护 Accept、findings none；阅读性 reviewer `6952162a` Accept。仅限定维护代码通过，整体正式 Revise、Follower Block 与既有架构/交付缺口保留。随后执行最终门禁，不关闭历史缺口。

最终门禁（上述 log 同目录）：按要求顺序 `./scripts/verify.sh quick` → `clean` → `full`，`final-{quick,clean,full}.log` 与 `.exit` 均 exit 1。首个失败已先归因为本维护未触及的 `BoltAuthenticatedTlsTest.java:129/159` 两处 Thread.sleep drift；clean/full 同一组 drift 失败，full 未进入完整 Maven verify，未弱化规则或修补其他自动观察问题。clean 的 untracked note 属已有混合 WIP 与本任务新迁移文件，均已分类保留。

`./scripts/verify.sh startup` → `final-startup.log` exit 1：10 个应用实际 smoke 均成功，但 Harness 检出验证期间仓库内容变化，结果不能绑定单一 worktree，不能当当前通过证据；已通知父会话协调其他设计写入者冻结后是否复跑。behavior/e2e 本轮未运行：本维护经逐字节等价证明未改行为/协议，且前序 drift/架构门禁失败及 Follower 能力 Block 尚存，不冒称广域运行验收通过。三项用户维护（阅读性、Tracker、Begin）限定代码均获独立 Accept；整体正式 Revise、A5 active/WIP=1、Follower Block 与历史缺口继续保留，独立 JRaft artifact 设计仍属另一个增量、不纳入本 task diff。



#### 观察状态内聚与领域规则合并（用户批准）

独立设计 reviewer `9f9c5cb3-857b-488` Accept、findings none，仅授权[最新结构补充](../../design-docs/2026-10-16-broker-automatic-failure-observation-design.md#最新结构补充用户已批准独立设计-review-accept)，整体 Follower Block 保留。A5 active/WIP=1，不提交、不改 JRaft/im-raft/依赖，不触碰无关 Gossip WIP。接手快照 `/tmp/observation-domain-consolidation-before/tree`；准确清单位于根目录 `/tmp/observation-domain-consolidation-before/files.json`，不是 `tree/files.json`。下述精确 caller 清单以该 JSON 为准；新增生成集合映射所需的 BrokerClusterStateTransformer 已在编辑前补入快照。

- [x] status/PROGRESS/readiness、根/模块事实源及规范读取；补充原设计和索引并获得独立设计 Accept。
- [x] 基线：`mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerFailureObservation*Test,BrokerFailureProbeTest,BrokerAutomaticFailure*Test,BrokerFailureRecoveryAppServiceTest,BrokerObservationAdmissionTest,BrokerObservationRequestConfigurationTest,BrokerFailureObservationConfigurationTest,BrokerHealthObservationPrecisionTest,IdentityAccessInterceptorsTest,BrokerClusterStateTransformerTest' -Dsurefire.failIfNoSpecifiedTests=false test`。
- [x] Server main：`application/cluster/BrokerClusterAppService.java` 内嵌 private static FailureObservationState/Topology，删除 `application/cluster/BrokerFailureObservationState.java`；删除 `application/policy/cluster/BrokerFailureObservationVoters.java`，规则并入 `domain/cluster/service/BrokerFailureObservationService.java` 的 findObservationVoters；`transformer/adapter/BrokerClusterStateTransformer.java` 生成 endpoint List 映射；`infrastructure/chain/interceptor/BrokerPeerAdmissionInterceptor.java` 显式读实际 membership 并调用既有领域服务；`infrastructure/config/{BrokerClusterConfiguration,BrokerFailureObservationConfiguration,BrokerRequestConfiguration}.java` 删除 State/Voters Bean 与参数并替换规则依赖。
- [x] Server test：将 `application/cluster/BrokerFailureObservationStateTest.java` 四项转成 App 可观察行为；将 `BrokerFailureObservationVotersTest.java` 移至 `domain/cluster/service/BrokerFailureObservationVotersTest.java` 并以实际聚合和 typed List 调用领域服务。JSON 中其余测试仅迁真实构造 caller；`BrokerFailureObservationRoundTest` 和 `transformer/interfaces/BrokerHealthObservationPrecisionTest` 通过 probe 用例建立采样，不再构造私有 Topology；`infrastructure/interceptor/BrokerObservationAdmissionTest` 不 mock voter Set，改为真实角色/绑定/成员列表。补 generated List 保留重复项测试于 `transformer/adapter/BrokerClusterStateTransformerTest.java`。以删除公开类后的旧 caller 编译失败记录迁移 RED，再迁 caller，保留所有原行为断言与 shared gate/monotonic freshness 回归。
- [x] HA：仅迁 `im-test/im-routing-ha/src/test/java/com/co/kc/imchat/routingha/AutomaticFailureTlsHaTest.java` 构造与 voter 查询，实际配置在调用边界取得；`mvn -q -pl im-test/im-routing-ha -am -DskipTests test-compile` 仅证明编译，不声称 HA 运行通过。
- [x] 聚焦 GREEN（同基线命令）→ `mvn -q -pl im-broker/im-broker-server -am test` → `./scripts/verify.sh architecture`；上游 cache 容量随机失败先归因并最多原命令重试两次，不改 cache。核对嵌套 State core tokens 和领域规则迁移的批准变换等价证据，产出限定 diff/文件列表、`git diff --check`。
- [ ] 到此停止，父会话独立代码 Review；接受后才顺序执行适用 `behavior`、`startup`、`e2e`、`quick`、`clean`、`full`。真实 HA 仍因 Follower 能力 Block 不算通过，Message VerifyError 等未触及失败保留精确归因。

实际证据（本轮检查点，不是完整交付）：
- `/tmp/observation-domain-consolidation-before/baseline.log` exit 0；`compile-red.log` exit 1，删除公开类型后旧 test caller 缺类型，生产编译成功，不以此冒称行为修复 RED。迁移 caller 首轮 `green-first.log` exit 1 为新增测试缺 Duration/any import，已修；`green-second.log`、`focused-green.log` 均 exit 0。
- App 冷却边界反证：临时仅把 `>= cooldown.toNanos()` 改为 `>`，`cooldown-boundary-mutation-red.log` exit 1，5 tests/5 failures/0 errors；五项用例在准确阈值应请求报告却没有请求，已恢复源码，之后 focused GREEN/reactor 通过。未删原四项语义，另补 topology epoch 变化与 ±1ns。
- `mvn -q -pl im-broker/im-broker-server -am test` exit 0（`broker-reactor.log`），当前源码对应 Broker 597、im-raft 39 tests，均 0 failures/errors/skipped。本轮没有 cache 失败或重试；历史随机失败不被抹除。
- `./scripts/verify.sh architecture` exit 1（`architecture.log`），73 tests/1 failure/0 errors/2 skipped；唯一失败是既有 probeFailures 直接依赖 plugin.bolt.BoltInvocationException 的三项违规，接手快照已有相同 import/instanceof/failure/TRANSPORT_UNAVAILABLE 分类。精确行证据 `architecture-attribution.txt`；本轮不改分类、不放宽规则、不夹带修复，architecture 不能标通过。
- `mvn -q -pl im-test/im-routing-ha -am -DskipTests test-compile` exit 0（`ha-compile.log`），仅编译，未跑 HA runtime，不解除 Follower Block。`git diff --check` exit 0。
- `proof.py`/`equivalence.log` 验证 State 核心 tokens 在精确改名/可见性变换后完全相同；规则在 typed List、expected endpoint Set 和 null-list failclosed 的批准变换后 tokens 完全相同。`task-files.txt`/`task.diff` 是相对接手快照的限定差异，不包含无关 Gossip/JRaft WIP。基线增加两个原存量 Transformer 文件均在各自编辑前保存；新增 domain 测试路径在限定 diff 计入。测试使用真实领域对象与 typed List，重复 voter、重复 learner、交叉、角色错绑、未知/缺失、过渡角色、非终态成员操作均实际断言；App/Peer 共用注入的现有领域服务，无 mock voter Set。
- 停止 tracked 源码/测试编辑，交父会话独立代码 Review；behavior/startup/e2e/quick/clean/full 尚未执行，必须由父会话在 Review 后按适用风险执行。构造/Bean wiring 改动需要 startup/e2e 证据，不能以 HA compile 或 reactor 替代。

#### 自动故障观察与摘流接入（用户批准）

当前阻塞与Review修复：完整功能 reviewer `011` 结论 Block（三项P1）。Broker两项已修、待独立复核：①任何观测到的term/role/context/voter不一致均invalidateCooldown，恢复一致重新完整等待；②单轮wall anchor只采一次，后续用monotonic elapsed推进评估时刻，local/remote请求起点与提交前freshness共用同一时间轴，不允许wall回拨延长票。`round-blockers-red.log` 2tests/2failures/0errors，分别真实触发回一致无冷却拉票、9.9s年龄+200ms单调/wall回拨后错误Begin；`round-blockers-green-2.log`3项green。

补齐app级warnings矩阵：`BrokerFailureObservationRoundTest`覆盖promote第一步、demote第一步、实际membership成功而BrokerCommit未提交三个窗口不发起且一致恢复全cooldown；`BrokerFailureProbeTest`真实revision0→1 generation更换后旧响应不写入、per-target闸门仍占位直到真实完成、LOCAL UNKNOWN/transport UNHEALTHY/成功与UNKNOWN重置有内容断言。candidate/probe按brokerId排序，构造probe请求置异常保护内。Peer healthReport新增实际caller传入的Instant requestStartedAt，来自同roundanchor+elapsed，不重读远端归一墙钟；公开构造不变。

精确聚焦命令 `/tmp/broker-failure-observation-before/feature-blockers-focused-command.txt`，日志 `feature-blockers-focused-green.log` exit0：SDK4+Server190=194项/0failures/errors/skipped；`round-warnings-green.log`8项通过。限定diffcheck0，Maven已释放；未跑新广门禁。正式Block保留，不以这两项green关闭Follower问题。

**library capability BLOCKER**：真实三Broker TLS HA首轮三个场景在Follower读取成员配置时失败，`/tmp/auto-failure-ha-runtime-first.log`：`RaftRuntime.membership`→JRaft1.4.1 `NodeImpl.listPeers` 抛 Not leader。公开Node没有Follower full applied/committed config查询；本地sources.jar证明 `onConfigurationCommitted` 常规路径条件不完整且只构造peers，snapshot路径同样丢learners；不能用它缓存bootstrap/猜角色。NodeImpl公开getCurrentConf即使readlock/copy也只是latest seen，无applied index/joint事实，不能冒充批准的实际已提交配置。未修改任何im-raft文件（RaftRuntime逐字节与本任务初始快照相同），未反射/私有字段读取、未升级/vendoring。保持本地actualmembership规格，不能偷偷改为Leader转发票/跳过Follower。父会话调查具体可行选项，本任务停在能力阻塞，已有代码/WIP保留。

最新功能接入检查点：规范 reviewer `1975efff-8923-4af` 短复审 Accept、八项全部closed、findings none，仅关闭规范修正，不代表完整功能验收。恢复用户批准功能后，Properties测试已从 `/tmp/broker-failure-observation-before/pending-tests` 取回，missingclass RED `config-red.log`；typedProperties/ObservationConfiguration/两个Scheduler GREEN `config-schedulers-green.log`。固定默认1s/500ms/30s/10s/60s和4线程64队列，配置直接注入不建依赖包。

- [x] 新增 `BrokerAutomaticFailureFlowTest`：两个独立来源的实际观察驱动真实领域/状态机Begin→fence→epoch→quorum/grant/prepare/install→REBUILDING→recover SUCCEEDED，未mock eligible或调用force冒充自动。初次auto/manualfailure的advance复用共享recovery gate，pending fence并发恢复真实RED `initial-failure-gate-red.log`（1test/1failure），修正后 `auto-config-flow-green-2.log` exit0；人工force结果契约保持不变。
- [x] `BrokerFailureObservationServiceTest`新增同observedAt冲突UNKNOWN，`automatic-flow-red.log` 7tests/1failure，修正latest合并后green；未把auto正例首跑成功冒称行为RED。
- [x] 新增配置闭合装配及Scheduler异常后重试测试，取回PropertiesTest、真实目标sample gate测试保留；新增目录及初始化角色遵循已关闭八项规范。ImBrokerApplicationTest精确scheduled名单新增两个实际入口，不放宽成任意包含。
- [ ] 完整Broker reactor当前未过：`feature-broker-reactor.log`上游未触JetCacheLocalValuesTest容量随机失败（预期<=2实际4）；`feature-broker-reactor-retry.log`上游通过、Broker587项/1failure/0errors，仅startup精确任务名单缺新两个入口，已修；`feature-broker-reactor-green.log`复跑又被同cache容量失败阻塞。未改cache，不标passing，待原命令刷新。
- [ ] 真实3Broker TLS HA由独占worker运行，Maven slot已明确交还父会话/HA；此前八文件源码及Shell证据不是Java成功证据。当前Broker任务diff `/tmp/broker-failure-observation-before/feature-current-broker.diff`、81文件清单 `feature-current-broker-files.txt`（相对最初worktree.tar，排除无关Gossip WIP）；fullfeature独立只读Review由父会话安排。
- [x] Broker Architecture/四README、Security/Reliability、HarnessGuide/Matrix及原D22链接已同步当前接入事实与证据限制；`feature-style.log` exit0、限定diffcheck0。尚未执行Gateway/Message聚焦、architecture、behavior、startup、e2e及最终门禁，不以文档或窄green覆盖缺口。


事实源：[自动故障观察设计](../../design-docs/2026-10-16-broker-automatic-failure-observation-design.md)。独立设计 reviewer `c5f87b0c-0030-412` 首轮 Revise 两项已闭环，第二轮完整复读 Accept、findings none。此增量明确取代上一结构任务中「禁止触碰观察服务」的任务边界，不删除该服务或削减原 D22。A5 保持唯一 active/WIP=1，不提交，其他 WIP/历史缺口保留。

**规范纠正历史检查点（已由最新功能检查点接续）**：用户指出新增代码不符合规范，独立 reviewer `9de26e93` Revise八项；纠正设计第二轮Accept不关闭实现findings。已仅实施八项代码修正，未扩展功能/装配/HA。父会话安排 reviewer `1975efff-8923-4af` 只读复审，当前代码/测试冻结等待结论，禁止边审边修改。A5 active/WIP=1、不提交，其他WIP及历史缺口保留。

- [x] 独立复审规范纠正设计：明确具体 Store 取代技术 Repository、State 只保私有运行态、AppService 直接注入协作者、Peer domain request/context、enum/Duration内部、边界精度和Generated MapStruct映射；复审通过后才将目标精确文件/API和red/green施工清单替换旧条目。 纠正设计已Accept；仅授权八项修正，原实现findings仍待逐项复审。
- [ ] 对八项问题依次保留可反驳证据并 red/green，不提前自修：status Builder/JSON必填与三状态roundtrip；年龄ceil/失败floor的±1ns、负时长拒绝；State职责及真实在途闸门；具体Store技术窗口不伪装聚合资源库；Peer不依赖CQRS、typed上下文核对；App/SDK映射及enum/Duration；task-only FQCN收敛；Query校验、防御性复制、record和DDD注释。依赖方向与封装语义由Review，不新增类名白名单或脆弱泛化checker。


剩余两项复审增量（等待短复查，不扩功能）：reviewer `1975efff-8923-4af` 已关闭1/4/5/6/7/8，仅2精度阈值测试与3共享恢复闸门待闭环。新增真实双入口并发反例：RecoveryScheduler先进入实际pending quorum，observeFailures同时进入不得再次发起；底层异步失败结束后允许重试，反向入口同样受保护。`review-two-findings-red.log` 17项/1failure/0errors，旧代码确实两次confirmLeadership；修正 `BrokerFailureObservationState.beginRecovery/endRecovery` 和唯一公共 `recoverRoutes` guard，私有internal接续不重复加锁，实际Future结束/同步异常才释放。测试构造中的State mock统一改真实State，不能让mock false遮蔽执行。

精度补充通过真实InterfaceTransformer→SDK row→AdapterTransformer→真实policy，固定freshness 10s及stable 30s的±1ns/准确阈值各三项；现生产舍入正确，因此新增阈值测试首轮即通过，未伪称该项行为RED。`review-two-findings-green.log` exit0；原精确完整聚焦命令再次运行 `review-two-findings-focused.log` exit0，SDK+Server共180项/0failures/errors/skipped；限定diffcheck通过。当前代码冻结，待父会话短复查，原173项历史证据保留。配置/HA继续暂停。

本轮八项修正的实际范围与证据：
- [x] 显式status与JsonCreator必填、三状态真实JSON roundtrip；DTO改enum/Duration，SDK年龄ceil/失败floor、负值拒绝。真实RED `standards-correctness-red.log`：5项/2failures/2errors（缺status未拒绝、+1ns变0、HEALTHY/UNKNOWN roundtrip失败）；GREEN后保持测试。
- [x] State仅私有gates/cooldown/Topology；AppService直接构造注入正式依赖、RequiredArgsConstructor；具体 `adapter/cluster/BrokerHealthObservationStore` 取代并同一步删除新建技术Repository接口/实现及旧领域technical context。旧Store测试同步改名 `BrokerHealthObservationStoreTest`，真实caller全部迁移。
- [x] Peer接收 `BrokerHealthObservationRequest` 七字段手写Builder及report目标 `Set<BrokerId>`；新增非空身份record `BrokerClusterId`/`BrokerObservationNonce`，13scalar helper删除，无新兼容壳；三个唯一命名MapStruct接口 `BrokerHealthObservationAppTransformer`/`BrokerHealthObservationInterfaceTransformer`/`BrokerHealthObservationAdapterTransformer` 使用生成映射，内部Duration精度保留、SDK舍入仅界面Transformer。
- [x] Query compact constructor AssertUtils必填/版本/nonce/集合复制重复校验；record组件单行注释、领域DDD角色、task-scope简单类型名。仅SDK/Domain同名BrokerNode保留必要限定且注释理由。原RPC失败不被丢弃：嵌套Mockito stubbing改先捕获initial、plaintext断言改准确verified TLS URI identity，随后通过。
- [x] 最终聚焦命令逐字保存 `/tmp/broker-failure-observation-before/standards-focused-command.txt`（26类，含三状态/精度/Query/request、Store/State、RPC/Peer及原cluster/drain/recovery测试）；`standards-final-focused-2.log` exit0，SDK4 + Server169 = 173 tests/0 failures/errors/skipped。日志内quorum unavailable等为既有受控失败测试输出，不是门禁错误。首次组合 `standards-focused-green.log` 71项/1failure/1error保留，未伪称当时通过。
- [x] `./scripts/check-java-style.sh` exit0（`standards-style.log`）；当前限定 `git diff --check` exit0。未运行新的reactor/architecture/behavior/startup/e2e/HA或完整门禁，不以这些窄证据冒称功能交付。
- [ ] 独立规范实现复审 `1975efff-8923-4af`：待结论，不自动将八项标closed。复审前不扩功能、不修其他findings。

纠正检查点：前快照 `/tmp/broker-failure-observation-before/standards-code-before/broker.tar`（含ignored target，比较时排除）；限定72文件差异 `/tmp/broker-failure-observation-before/standards-correction.diff`、`standards-correction-files.txt`，计数 `standards-final-counts.txt`。只比较Broker本轮纠正，不混入plugin、Gateway/Message、HA或原结构WIP。原接手整体基线及之前RED/GREEN仍保留。

未完成项明确保留：`BrokerFailureObservationPropertiesTest.java`（API尚未实施）暂存 `/tmp/broker-failure-observation-before/pending-tests/`，已从本轮聚焦testCompile移出，不是通过/删除需求，恢复装配阶段必须取回。观察配置Bean、typedProperties、调度、完整自动组合与真实3BrokerTLS HA尚未完成；HA worker八文件仅source/shell证据，Java未编译/未运行，不得声称通过。当前App构造/Peer/Store API仅供规范复审，HA适配等复审后统一进行。

停止时证据（不是当前验收）：
- 基线 `/tmp/broker-failure-observation-before/worktree.tar`；主Agent独占44文件 `/tmp/broker-failure-observation-before/standards-review-main-owned-files.txt`；Broker+docs共69文件及差异 `standards-review-task-files.txt`、`standards-review-task.diff`（包含RPC worker，排除无关BrokerGossipAppService）。其他worker独占路径及基线由父会话归档，不用git整体diff覆盖用户WIP。
- `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerFailureObservationServiceTest,BrokerLifecycleRecoveryTest -Dsurefire.failIfNoSpecifiedTests=false test`：前两次先被共享context/Invoker迁移编译阻塞，第三次真实10项/2failures/0errors；旧稳定票覆盖与旧epoch fencing查找反例均失败。日志 `broker-behavior-red{-2,-3}.log` 位于上述基线目录；不是以编译错误冒充行为RED。
- 新API compile-red `broker-api-red.log`，首批四类green `observation-first-green.log` exit0；早期恢复新quorum证据反例 `early-recovery-red-2.log` 2项/1failure，修正后 `observation-core-green.log` exit0；peer probe新API compile-red `peer-api-red.log`；probe tick compile-red `probe-tick-red.log`。这些均为中间证据，新增properties未实施、最终结构尚待纠正，不代表当前完整编译通过。
- 最近 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerAutomaticFailureObservationTest,BrokerPeerAdapterTest,BrokerObservationContractTest,BrokerObservationIdentityTest,BrokerObservationAdmissionTest,BrokerHealthObservationHandlersTest,BrokerObservationRequestConfigurationTest,BrokerAdmissionContractValidatorTest,IdentityAccessInterceptorsTest -Dsurefire.failIfNoSpecifiedTests=false test`：`probe-core-green.log` exit1，39项/1failure/1error，RPC身份文案断言与嵌套Mockito stubbing失败待修，未用更窄green覆盖。之后仅新增尚无生产类型的Properties测试，未再跑Maven。
- Bolt初版真实TLS证明由worker `09c99e30-44c7-4a0`执行，父会话提供 `/tmp/auto-failure-bolt-{red,green}.log`、5项green证据；预算后续纠正 reviewer `e9abe20f` Accept，必须依最新源码验证，不能复用旧五项结论。单一monotonic acceptance budget覆盖serialization/acquire/TLS/RPC/decode，不承诺硬墙钟返回；LOCAL过期UNKNOWN、报告独立失效、目标/轮次/恢复闸门到实际底层结束才释放，context reset不能解除在途占位；长期DNS/连接池阻塞可安全暂停自动处置，不新建池。
- 停止时本仓无Maven运行，slot已交父会话；当前plugin worker仅验证。不得并发执行Maven，本轮仅文档diff检查。

文件根：B=`im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/`，BT=`im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/`，S=`im-broker/im-broker-sdk/src/main/java/com/co/kc/imchat/broker/sdk/`，ST 为对应 SDK test 根。

- [x] 记录第二轮设计 Accept、准确批准范围及本施工清单；接手快照 `/tmp/broker-failure-observation-before/worktree.tar`、初始/设计状态及 `readiness-design.log`（exit 0）保留仓库外。
- [ ] Bolt 安全前置证明（worker `09c99e30-44c7-4a0` 独占）：仅修改 `im-plugin/im-bolt/**`，包括 `spi/BoltInvoker.java`、`core/BoltClientInvoker.java`、`core/BoltRequestProcessor.java`、`model/BoltRequestContext.java`、必要通用失败分类及真实 TLS 测试、README。RED/GREEN：`mvn -q -pl im-plugin/im-bolt -am -Dtest=BoltClientInvokerTest,BoltRequestProcessorTest,BoltAuthenticatedInvocationTest -Dsurefire.failIfNoSpecifiedTests=false test`（按实际测试名回填）。验证真实证书握手/无URI/多URI/不受信/错误身份、同连接和总预算；mock peerId 不算证明。失败则阻塞，不改 Broker 生产实现、不弱化身份。Maven 由父会话协调串行。
- [ ] 资格回归 RED：BT `domain/cluster/service/BrokerFailureObservationServiceTest.java` 增旧稳定失败被新短暂失败覆盖；命令 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerFailureObservationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`，预期旧实现错误计入 reporter 而失败。后续补新 HEALTHY/UNKNOWN 覆盖、同时间冲突、目标/非voter、fresh/stable 边界。生产 B `domain/cluster/model/BrokerHealthObservation.java`、新增 `BrokerHealthStatus.java`，修改 `domain/cluster/service/BrokerFailureObservationService.java`；GREEN 同命令。
- [ ] 本机观察 RED/GREEN：新增 B `domain/cluster/repository/BrokerHealthObservationRepository.java`、`infrastructure/domain/cluster/InMemoryBrokerHealthObservationRepository.java`、BT 对应 `infrastructure/registry/broker/InMemoryBrokerHealthObservationRepositoryTest.java`；使用真实观察值、可控单调时钟和 Clock 验证成功/UNKNOWN重置、断档、旧generation、单目标在途、裁剪、有界状态和墙钟回拨。命令 `mvn -q -pl im-broker/im-broker-server -am -Dtest=InMemoryBrokerHealthObservationRepositoryTest,BrokerFailureObservationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [ ] 当前 voter/cooldown RED/GREEN：新增 B `domain/cluster/model/BrokerFailureObservationContext.java`、应用内部 B `application/cluster/BrokerFailureObservationState.java`（非 AppService，仅本机轮次/冷却直接协作者），BT `application/cluster/BrokerFailureObservationStateTest.java`；使用实际 `RaftMembership` 与领域快照核对 endpoint/角色及未终态成员操作。promote第一步、demote第一步、membership成功BrokerCommit待提交均禁止新建，恢复一致完整等cooldown；ACK/phase-only revision不重置冷却，实际membership/leader term变更废弃轮次。命令 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerFailureObservationStateTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [ ] SDK/RPC RED/GREEN：S `enums/BrokerBoltOperation.java`、`access/BrokerAdmissionType.java`，新增 `model/params/BrokerProbeParams.java`、`BrokerHealthReportParams.java`、`model/result/BrokerProbeResult.java`、`BrokerHealthReportResult.java`、`enums/BrokerHealthStatus.java`；B 新增 `interfaces/handler/broker/BrokerProbeHandler.java`、`BrokerHealthReportHandler.java`，修改 `infrastructure/chain/interceptor/BrokerPeerAdmissionInterceptor.java`、`BrokerCallerIdentityInterceptor.java`、对应配置 `infrastructure/config/BrokerRequestConfiguration.java`；B `transformer/interfaces/BrokerHealthObservationTransformer.java`、`transformer/adapter/BrokerHealthObservationTransformer.java`。BT `interfaces/handler/BrokerFailureObservationHandlerTest.java`、`infrastructure/interceptor/BrokerFailureObservationAdmissionTest.java`，ST `client/BrokerHealthObservationContractTest.java` 验证类型/包装必填/单位/nonce/deadline/context及真实传输来源拒绝，不用payload覆盖身份。命令 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerHealthObservationContractTest,BrokerFailureObservationHandlerTest,BrokerFailureObservationAdmissionTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [ ] peer调用及完整自动组合 RED/GREEN：修改 B `adapter/cluster/BrokerPeerAdapter.java`、`application/cluster/BrokerClusterAppService.java`；新增 B `model/cqrs/command/BrokerFailureObservationCommand.java`、`model/cqrs/query/BrokerHealthReportQuery.java`、`model/cqrs/result/BrokerFailureObservationResult.java`，BT `application/cluster/BrokerAutomaticFailureObservationTest.java`、扩展 BT `adapter/cluster/BrokerPeerAdapterTest.java`。新自动入口内部重新验资格，复用 failure Intent/Begin/fence/epoch/Lease；不调用人工force原因入口。两独立当前轮报告驱动真实领域/状态机到fencing/epoch/INACTIVE及Gateway正常/降级；拒绝/超时/本地队列拒绝分类、nonce/身份/context、旧轮结果不能覆盖、集合不可变、部分报告失败不复用。命令 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerAutomaticFailureObservationTest,BrokerPeerAdapterTest,BrokerClusterAppServiceChangeTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [ ] 早期恢复 RED/GREEN：B `application/cluster/BrokerClusterAppService.java`、`interfaces/scheduler/BrokerFailureRecoveryScheduler.java`，BT `application/cluster/BrokerFailureRecoveryAppServiceTest.java`、`interfaces/scheduler/BrokerLifecycleRecoveryTest.java`；唯一非终态FAIL查找不依赖当前=result epoch，fencing/RAFT_COMMITTED/AUTHORIZING完整接续，已有op优先；失败/拒绝释放闸门，非终态不并发。命令 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerFailureRecoveryAppServiceTest,BrokerLifecycleRecoveryTest,BrokerClusterAppServiceFenceTest,BrokerClusterLeaseInstallationTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [ ] 调度/装配 RED/GREEN：新增 B `interfaces/scheduler/BrokerFailureProbeScheduler.java`、`BrokerFailureObservationScheduler.java`、`infrastructure/config/properties/BrokerFailureObservationProperties.java`、`infrastructure/config/BrokerFailureObservationConfiguration.java`；必要调整 `BrokerClusterConfiguration.java`、`BrokerDrainConfiguration.java`、`src/main/resources/application.yml`；BT `interfaces/scheduler/BrokerFailureObservationSchedulerTest.java`、`infrastructure/config/properties/BrokerFailureObservationPropertiesTest.java`。Clock/单调时间/专用有界4线程64队列由配置拥有，无业务开关，1s/500ms/30s/10s/60s默认和校验准确；启动缺TLS为UNKNOWN不造票。命令 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerFailureObservationSchedulerTest,BrokerFailureObservationPropertiesTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [ ] 同步 B 所属 `im-broker/ARCHITECTURE.md`、`im-broker/README.md`、`im-broker/im-broker-server/README.md`、`im-broker/im-broker-sdk/README.md`，`docs/SECURITY.md`、`docs/RELIABILITY.md`、`docs/references/HARNESS_GUIDE.md`、`HARNESS_RULE_MATRIX.md`、原设计 D22 接入链接。仅登记本增量可复用规则及可靠测试，数据流来源/单调时间语义Review-only及自动化条件准确，不新增类名例外。架构仅按已批准层级接入，不放宽广域规则。
- [ ] 共享 Bolt API caller 迁移（不赋予旧测试可信TLS来源）：Broker BT `interfaces/handler/{ConnectionRegisterHandlerTest,AbstractBrokerRpcHandlerDispatchTest,BrokerBoltHandlerTest}.java`、`interfaces/handler/routing/RouteLookupHandlerTest.java` 使用 canonical五参数context，false/空URI保留旧语义；RPC worker处理 `infrastructure/interceptor/IdentityAccessInterceptorsTest.java`。手写Invoker替身显式拒绝不应发生的authenticated调用：BT `interfaces/handler/{BrokerBoltHandlerTest,FrameProcessHandlerTest}.java`、`application/frame/FrameDispatchAppServiceTest.java`；SDK worker处理 `BrokerClientTest`/`BrokerClientRoutingTest`。Gateway worker已迁移 `im-gateway/im-ws-gateway/im-ws-gateway-server/src/test/java/com/co/kc/imchat/gateway/ws/security/GatewayBrokerAuthorizationTest.java`、`handler/ConnectionCloseHandlerTest.java`、`handler/bolt/FrameWriteHandlerTest.java`（后两路径同根）；基线 `/tmp/auto-failure-gateway-context-before`，断言不变，尚无Maven证据。另同 worker 处理 `im-ws-gateway-sdk/src/test/java/com/co/kc/imchat/gateway/ws/sdk/client/GatewayClientTest.java` 和 Server test `ImWsGatewayApplicationTest.java`、`support/BrokerClientTestSupport.java` 的手写Invoker。串行命令 `mvn -q -pl im-gateway/im-ws-gateway/im-ws-gateway-server -am -Dtest=GatewayClientTest,GatewayBrokerAuthorizationTest,ConnectionCloseHandlerTest,FrameWriteHandlerTest,ImWsGatewayApplicationTest -Dsurefire.failIfNoSpecifiedTests=false test`，后续reactor/e2e刷新共享context证据。
- [ ] 父会话分配真实三Broker TLS HA测试独占范围，回填准确文件/命令后实施；临时证书仅运行时生成至build/仓库外，无私钥提交，无生产部署变更；RH-05不能只绑定恢复单测。拒绝用mock独立身份或旧容器恢复证据冒充自动触发。
- [ ] 串行扩大验证：`mvn -q -pl im-broker/im-broker-server -am test` → `./scripts/verify.sh architecture` → `./scripts/verify.sh behavior` → `./scripts/verify.sh startup` → `./scripts/verify.sh e2e` → `./scripts/verify.sh routing-ha`；每层失败先修复或明确归因，精确命令/日志/实际数回填。独立实现 Review 由父会话安排，检查task-only diff和真实TLS/自动触发证据；Review之前不宣称完成。
- [ ] 独立实现Review闭环后运行 `./scripts/verify.sh quick`、`./scripts/verify.sh clean`、`./scripts/verify.sh full`，保留Message VerifyError等未触及缺口，不扩张修复、不提交、不归档直到真实验收通过。


共享SPI七个测试替身精确路径（父会话已迁移，非本Agent额外行为修改，基线 `/tmp/auto-failure-invoker-test-before`）：
- `im-gateway/im-ws-gateway/im-ws-gateway-sdk/src/test/java/com/co/kc/imchat/gateway/ws/sdk/client/GatewayClientTest.java`
- `im-gateway/im-ws-gateway/im-ws-gateway-server/src/test/java/com/co/kc/imchat/gateway/ws/ImWsGatewayApplicationTest.java`
- `im-gateway/im-ws-gateway/im-ws-gateway-server/src/test/java/com/co/kc/imchat/gateway/ws/support/BrokerClientTestSupport.java`
- `im-service/im-message/im-message-server/src/test/java/com/co/kc/imchat/service/message/infrastructure/notification/MessagePushServiceTest.java`
- `im-service/im-message/im-message-server/src/test/java/com/co/kc/imchat/service/message/infrastructure/notification/BrokerMessageNotifierTest.java`
- `im-service/im-message/im-message-server/src/test/java/com/co/kc/imchat/service/message/support/BrokerClientTestSupport.java`
- `im-service/im-message/im-message-server/src/test/java/com/co/kc/imchat/service/message/support/TestCacheConfiguration.java`

后续串行验证 `mvn -q -pl im-service/im-message/im-message-server -am -Dtest=MessagePushServiceTest,BrokerMessageNotifierTest -Dsurefire.failIfNoSpecifiedTests=false test` 及前述Gateway聚焦命令，再刷新reactor/e2e；目前不能标记通过。HA独占worker `3570b08f`范围 `im-test/im-routing-ha/**`、`scripts/run-routing-ha-scenarios.sh`、`scripts/test-routing-ha-harness.sh`，当前同样暂停生产；真实3 Broker TLS runtime（JRaft、RpcServers/Clients、production admission、本机文件fence）不能冒称plaintext Compose故障证据，具体新测试路径由父会话归档再回填。

当前恢复：纠正设计Accept后八项代码修正及173项聚焦测试已执行，等待独立规范实现复审；当前代码/测试冻结。HA/装配/自动功能完整交付仍未完成，复审后再按父会话指示恢复，Properties待取回；不得削减功能、回滚他人WIP或提交。

#### Transformer 与 Monitoring 结构归属（用户批准）

事实源：[结构归属设计](../../design-docs/2026-10-15-broker-transformer-monitoring-boundaries-design.md)。独立设计 reviewer `7cf18e69-3af8-449` Accept、findings none；A5 保持唯一 active，不关闭历史缺口、不提交。禁止触碰 BrokerFailureObservationService、其模型或自动故障行为。

精确范围：S=`im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/`，T=`im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/`。
- S 搬迁：`transformer/domain/BrokerClusterDomainTransformer.java` → `transformer/adapter/BrokerClusterStateTransformer.java`；`transformer/infrastructure/{BrokerClusterRaftTransformer,BrokerDrainTransformer,BrokerFenceTransformer,BrokerLeaseTransformer}.java` → `transformer/adapter/`；`transformer/infrastructure/RegistryTransformer.java` → `transformer/registry/`；`infrastructure/monitoring/{BrokerMetrics,RoutingAuthorityMetrics}.java` → `support/monitoring/`。T 对应现有五个测试文件同步搬迁（State/Fence/Registry/两 Metrics），其余测试只迁引用。
- S 引用：`adapter/cluster/{BrokerRaftAdapter,BrokerPeerAdapter}.java`、`adapter/gateway/GatewayDispatchAdapter.java`、`adapter/gossip/BrokerGossipAdapter.java`、`application/local/LocalRouteAppService.java`、`infrastructure/domain/{cluster/InMemoryBrokerRegistry,connection/InMemoryGatewayRegistry}.java`、`interfaces/handler/{broker/BrokerListHandler,gateway/GatewayRegisterHandler,connection/ConnectionCloseHandler}.java`、`support/raft/statemachine/RaftBrokerClusterStateMachine.java`。
- T 引用：`adapter/{cluster/BrokerRaftAdapterTest,gossip/BrokerGossipAdapterTest}.java`；`application/cluster/{BrokerClusterAppServiceActivationTest,BrokerClusterAppServiceChangeTest,BrokerClusterAppServiceTest,BrokerClusterLeaseGrantTest,BrokerClusterLeaseInstallationTest,BrokerDrainAppServiceTest}.java`；`application/{diagnostic/BrokerDiagnosticAppServiceTest,frame/FrameDispatchAppServiceTest,local/LocalGatewayCleanupAppServiceTest}.java`；`interfaces/handler/{BrokerBoltHandlerTest,ConnectionCloseHandlerTest,ConnectionRegisterHandlerTest,FrameProcessHandlerTest,routing/RouteLookupHandlerTest}.java`；`interfaces/scheduler/BrokerMembershipSchedulerTest.java`；`infrastructure/{interceptor/IdentityAccessInterceptorsTest,registry/connection/InMemoryConnectionRegistryTest,registry/gateway/InMemoryGatewayRegistryTest}.java`；`support/{BrokerClusterConsensusStub,BrokerRaftTestBindings,StatefulClusterAdapterFixture,raft/model/BrokerClusterSnapshotIsolationTest,raft/statemachine/BrokerLeaseGrantAtomicityTest}.java`；`transformer/application/BrokerClusterAppTransformerTest.java`。
- 架构：`im-test/im-architecture-test/src/test/java/com/co/kc/imchat/architecture/LayerBoundaryTest.java`，新增 `fixture/broker/support/monitoring/{LegalObserver,ApplicationOwner,InterfaceOwner,InfrastructureOwner,ConfigurationOwner,LifecycleOwner,RpcOwner,RuntimeOwner,BrokerClientOwner,NetworkOwner,ExecutorOwner}.java`、`fixture/broker/support/monitoring/nested/NestedObserver.java`、`fixture/broker/domain/MonitoringSupportLeak.java`。既有 `fixture/broker/support/gossip/InfrastructureOwner.java` 改依赖仍在 infrastructure 的 InMemoryBrokerRegistry，以继续保护原禁令（不能随 Metrics 迁到 support）。
- 文档：本计划、`PROGRESS.md`、新设计及设计索引、`docs/references/{MODULE_STRUCTURE_GUIDE,HARNESS_GUIDE,HARNESS_RULE_MATRIX}.md`、`im-broker/ARCHITECTURE.md`、`im-broker/im-broker-server/README.md`。application/interfaces 转换器不搬、不改方法。

- [x] 接手 status/PROGRESS/readiness 与外部归档；设计持久化先于计划，独立 Review Accept 后解锁。基线 `/tmp/broker-structural-baseline/tree.tar`、status `/tmp/broker-structural-initial-status.txt`、readiness `/tmp/broker-boundary-readiness.log`。
- [x] 聚焦基线：`mvn -q -pl im-broker/im-broker-server -am '-Dtest=*TransformerTest,BrokerMetricsTest,RoutingAuthorityMetricsTest,BrokerClusterSnapshotIsolationTest,BrokerClusterCommandCodecTest' -Dsurefire.failIfNoSpecifiedTests=false test`，exit 0，日志 `/tmp/broker-structural-focused-baseline.log`。
- [x] RED：新增精确 Monitoring 正负规则与 fixtures、Transformer 分组规则及 MeterBinder 归属检查；生产与 fixture 共用参数规则，逐名断言负依赖；`./scripts/verify.sh architecture` exit 1，73 tests/2 failures/2 skipped，恰为两个旧 MeterBinder 包及 12 个旧 Transformer/生成类违规，fixture 测试通过；日志 `/tmp/broker-structural-architecture-red.log`。
- [x] 最小搬迁与全部 main/test 引用迁移；保持 MapStruct、组件和指标字节等价；`python3 /tmp/broker-structural-check.py` exit 0，所有 Broker main/test Java 经批准的 package/指定类名替换后与归档逐字节相同。旧 Broker Java 引用无残留（其他模块的 transformer.infrastructure 不在范围）。
- [x] GREEN：原聚焦命令 exit 0（`/tmp/broker-structural-focused-green.log`）；`mvn -q -pl im-broker/im-broker-server -am test` exit 0、当前源码 523 项（`/tmp/broker-structural-reactor.log`）；`./scripts/verify.sh architecture` exit 0、73 项/2 skipped（`/tmp/broker-structural-architecture-green.log`）；`git diff --check` exit 0。同步 owning specification/当前导航/Harness 规则。
- [x] 独立实现 reviewer `8103be7d-5b67-4bd`：代码 Accept、findings none；正式 Revise 仅因最终门禁待完成。非阻断建议为未来补独立 config/lifecycle 负例与错误位置 MeterBinder fixture；本轮不扩展迁移，保留 Gossip 禁令。
- [x] 依序执行 `./scripts/verify.sh quick` exit 0（454 秒）、`./scripts/verify.sh clean` exit 0（16 秒）、`./scripts/verify.sh full` exit 1（425 秒）。日志 `/tmp/broker-structural-{quick,clean,full}.log`；full 失败归因见下，不视为 A5 通过。
- [ ] 完整门禁缺口：full 在 routing-ha 测试构建阶段失败，未完成全套 verify；正式 Revise/A5 active 保留，不把通过的窄验证替代 full。状态记录后停止 tracked 编辑，执行 `./scripts/verify.sh quick`、`./scripts/verify.sh clean` 刷新最终状态指纹（日志 `/tmp/broker-structural-stable-{quick,clean}.log`）；最终结果以 Harness 为准，不再回填本 checkbox 造成自引用失效。

本轮证据与恢复：
- 基线、green、reactor、architecture 按窄到宽执行；真实 red 恰为预期旧包违规，非环境失败。首次外部比对脚本将 macOS tar 的 `._fixture` 元数据误当源码，过滤 AppleDouble 元数据后 exit 0，未改仓库代码消除错误。
- 全部 Broker main/test Java 对归档经批准替换后逐字节一致，包含未触碰的 BrokerFailureObservationService/模型；保留 application 两类和 interfaces 三类的目录及实现。原 WIP/历史验收缺口不变。
- 精确清单 `/tmp/broker-structural-files.json`（74 个 before/after 映射，含新 fixtures/文档）、隔离增量 `/tmp/broker-structural-only.diff`、外部脚本 `/tmp/broker-structural-check.py`、日志 `/tmp/broker-structural-equivalence.log`；原归档与 status 见上。既有 Gossip InfrastructureOwner 仅替换为真实 infrastructure 类型，Adapter/Spring 禁令和逐名断言保留。
- 独立实现 Review 已返回代码 Accept、findings none，正式 Revise 保留。父代理独立运行 `python3 /tmp/broker-structural-check.py` 与 `git diff --check` 均 exit 0，日志 `/tmp/broker-parent-equivalence.log`。
- 首轮最终 quick/clean/full 三份 Harness JSON 均 `worktreeStable=true`，HEAD `477758a7aedf436c906ba85a830c7bdd09f80942`，共同指纹 `7c67bc77886d71441eedc7767a6b83bbe8f386b7a85a94e016f7926d90ccf810`。full 在 routing-ha 的 Maven 测试构建阶段失败：未触及 Message 的 `ImMessageNotifierInvokerTest` 5 项中 2 failures/3 errors，根因为 `VerifyError: Control flow falls through code end`（日志 1060–1385 行），属于既有无效测试字节码/构建产物缺口，不是本次 package 迁移的代码 finding；未修改该模块或放宽门禁，未进入完整 Maven verify。
- 本状态更新会改变全树指纹；接手先看 `/tmp/broker-structural-stable-{quick,clean}.log` 与 Harness 对应 JSON 的最终结果和指纹，不将上条旧指纹冒称最终当前证据。full 的失败证据保留，最终 quick/clean 刷新不覆盖 full 缺口。A5 仍唯一 active，不提交、不归档；本增量纯机械包/名迁移，未触及运行配置、transport、wiring 或行为，不追加 behavior/e2e/startup，旧缺口继续保留。

#### Broker 注释事实审阅与节点术语

- [x] 审阅 Server/SDK 生产注释，修正 34 个 Java 文件（应用/领域 21、基础设施/协议 9、SDK 追加 4），不修改代码、运行消息、标识符或测试。精确清单/快照：`/tmp/broker-comment-app-files.txt`、`/tmp/broker-comment-infra-before.files`、`/tmp/broker-comment-sdk-extra/`；隔离 diff 与词法检查在对应 `/tmp/broker-comment-*` 工件。
- [x] 明确旧 epoch 隔离集合为 committed ACTIVE Broker 冻结集合，而非有效 Lease 持有者；目标 epoch 不代表新加入节点。修正 Begin/Commit、fence 标记持续时间、ACK 校验职责、revision、Gateway 水位来源、nonce 安装及历史容量说明；SDK/命令/配置说明同步当前职责。剩余 holder 仅为原有运行消息、上下文类型或测试局部名，不作代码迁移。
- [x] 四份 Broker 当前文档同步上述语义；修正优雅排空图中 Begin 冻结 Gateway、后续 fence 提交才冻结 Broker 的时点，历史设计不改。快照 `/tmp/broker-holder-docs-before/`。
- [x] readiness、diffcheck 及 Java 感知词法校验通过，逐文件非注释内容一致。独立 reviewer 首轮指出 Architecture 集合冻结时点，修正后复审 Accept，基础设施 9 文件确认无事实偏差；SDK 追加仅替换目标 Broker 的说明，代码词法相同。
- [ ] 最终 `./scripts/verify.sh quick`、`clean`、`full`（日志 `/tmp/broker-comments-final-{quick,clean,full}.log`）；以 Harness 实际结果为准，不以后续通过覆盖已有失败，A5 active、不提交。运行期间不修改 tracked 状态以保留稳定指纹。

#### Server README 与 Architecture 职责分离（2026-10-14）

- [x] 重写 `im-broker/im-broker-server/README.md` 为开发者使用入口：构建启动、术语、简短代码导航、分组配置、管理接口/认证、数据目录、排障和验证。详细状态机、实体行为、Raft/Gossip/Lease 流程改为对应 Architecture 链接。
- [x] `im-broker/ARCHITECTURE.md` 补齐唯一的内部事实：异步请求上下文清理、Commit 确定性、learner 确认和 duplicate 证据分支、毫秒时间约束及完整 Begin/membership/Commit 顺序；未把旧 README 全文搬入。接手快照和隔离 diff 在 `/tmp/broker-readme-purpose-before/`。
- [x] readiness、配置键覆盖/25 个链接检查及 diffcheck 通过。独立 Review 首轮 Revise：管理 API 与 Actuator/Nacos 发布端口配置并非联动，已补充对齐要求；其余职责、事实保全和阅读性检查通过。独立复审 Accept、findings none；最终门禁以机器证据为准。
- [ ] 最终 `./scripts/verify.sh quick`、`clean`、`full`（日志 `/tmp/broker-readme-purpose-{quick,clean,full}.log`）；运行期间不修改 tracked 状态，A5 保持 active、不提交。仅文档修改不以旧机器证据代替本轮验证。

#### Broker 全部自有时间契约增量（用户批准；独立设计 Review 首轮 Revise，修订后待复审）

当前 P1/P2/P3 已实施，P4 模块/行为验证及代码文档复审已完成；A5 active/WIP=1，routing-ha/最终门禁缺口见本节末，不冒称可验收新契约。

路径简写：S=`im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/`；K=`im-broker/im-broker-sdk/src/main/java/com/co/kc/imchat/broker/sdk/`；T=`im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/`；G=`im-gateway/im-ws-gateway/im-ws-gateway-server/`；O=`im-management/im-ops/`。下列文件属于本轮允许施工范围，其他 consumer 如需补充必须先用引用证据更新清单，不机械改通用 Plugin 或业务服务。

- [x] P0 设计门：专门 reviewer 首轮 Revise，修订 Ops scope、Duration 类型、重放验收与 UI wire 后复审 Accept、findings none；解锁 P1–P4。readiness 复用父会话成功结果；实施跨会话重新执行 `./scripts/verify.sh readiness`。

**P1 / worker W：SDK 数字 wire 与 Gateway 直接 caller（独占 K 和 G，不编辑 S）。**

生产文件 K：`BrokerClient.java`、`routing/RouteRequestContext.java`、`model/params/RouteLookupParams.java`、`GatewayRouteSnapshotParams.java`、`GatewayRouteSnapshotAckParams.java`、`DrainRouteCleanupParams.java`（后三项均在 model/params）、`model/result/AcceptGatewayRoutesResult.java`。G：`src/main/java/com/co/kc/imchat/gateway/ws/routing/GatewayRouteSnapshotCoordinator.java`。

- [ ] RED：修改 K 对应 `src/test/java/com/co/kc/imchat/broker/sdk/client/BrokerClientRoutingTest.java`；新增同目录 `BrokerTimeContractTest.java`，固定数字毫秒 JSON 新键/旧键缺席、request >0、同 deadline 重试、SDK 既有 Instant DTO 不变；G `src/test/java/com/co/kc/imchat/gateway/ws/routing/GatewayRouteSnapshotCoordinatorTest.java` 加正好到期停止/原窗口与 backoff 单位断言。命令 `mvn -q -pl im-broker/im-broker-sdk -am -Dtest=BrokerClientRoutingTest,BrokerTimeContractTest -Dsurefire.failIfNoSpecifiedTests=false test`；Gateway 使用 `mvn -q -pl im-gateway/im-ws-gateway/im-ws-gateway-server -am -Dtest=GatewayRouteSnapshotCoordinatorTest -Dsurefire.failIfNoSpecifiedTests=false test`。保留 compile-red/行为-red 分类，禁止把环境失败当 red。
- [x] GREEN：最小修改上述 K/G 文件：expireAtMs、updateExpireAtMs、oldEventExpireAtMs、retryAfterMs；BrokerClient 配置输入 timeoutMs、runtime Duration timeout，在 Bolt 调用处转整数毫秒；原期限/次数/单位不变，无 alias。重跑两个 RED 命令，记录实际结果；Gateway reactor 待 P2 接线后再整体运行。

**P2 / worker D：领域 + 内部 Command + Server 协议接线 + v2（独占以下 S/T）。**

S 文件：
- `application/cluster/BrokerClusterAppService.java`
- `application/local/LocalRouteAppService.java`
- `domain/cluster/model/BrokerClusterOperation.java`
- `domain/cluster/model/BrokerHealthObservation.java`
- `domain/cluster/model/DrainProgress.java`
- `domain/cluster/service/BrokerFailureObservationService.java`
- `domain/connection/model/ConnectionRegistration.java`
- `domain/connection/model/GatewayRouteBatch.java`
- `domain/connection/model/GatewayRouteTable.java`
- `domain/connection/repository/BatchConnectionRegistry.java`
- `domain/connection/repository/ConnectionRegistry.java`
- `infrastructure/domain/connection/InMemoryBatchConnectionRegistry.java`
- `infrastructure/domain/connection/InMemoryConnectionRegistry.java`
- `model/cqrs/command/AcceptGatewayRoutesCommand.java`
- `model/cqrs/command/AdvanceClusterOperationCommand.java`
- `model/cqrs/command/CleanupDrainRoutesCommand.java`
- `model/cqrs/command/CommitBrokerOperationCommand.java`
- `model/cqrs/command/CompleteBrokerDrainCommand.java`
- `model/cqrs/command/ConfirmGatewayRoutesCommand.java`
- `model/cqrs/command/ForceDrainOperationCommand.java`
- `model/cqrs/command/UpdateBrokerDrainProgressCommand.java`
- `support/raft/statemachine/RaftBrokerClusterStateMachine.java`
- `transformer/infrastructure/BrokerClusterRaftTransformer.java`
- `transformer/infrastructure/BrokerDrainTransformer.java`
- `adapter/cluster/BrokerRaftAdapter.java`
- `support/raft/codec/BrokerClusterCommandCodec.java`
- `support/raft/model/BrokerClusterSnapshot.java`
- `transformer/interfaces/BrokerRoutingCommandTransformer.java`

另 S 直接 deadline caller：`adapter/cluster/BrokerPeerAdapter.java`、`infrastructure/chain/interceptor/RouteBrokerExecutionInterceptor.java`、`interfaces/handler/connection/ConnectionRegisterHandler.java`、`ConnectionUnregisterHandler.java`（同目录）；`RouteLookupHandler.java` 由 P3 唯一拥有，W/D 只反馈所需 accessor，不并发改。BrokerRaftAdapter 的 config 读取由 D 统一接入 P3 输出。其余 `changedAt` 已为 Instant 的 command 不重复改。

T 文件：`adapter/cluster/BrokerRaftAdapterTest.java`、`BrokerPeerAdapterTest.java`（同目录）、`support/raft/codec/BrokerClusterCommandCodecTest.java`、`support/raft/model/BrokerClusterSnapshotIsolationTest.java`、`support/raft/statemachine/BrokerLeaseGrantAtomicityTest.java`、`model/cqrs/command/AdvanceClusterOperationCommandTest.java`、`domain/connection/model/ConnectionRouteValueObjectTest.java`、`GatewayRouteBatchTest.java`、`GatewayRouteTableTest.java`（同目录）、`domain/cluster/service/BrokerFailureObservationServiceTest.java`、`application/cluster/BrokerClusterAppServiceChangeTest.java`、`BrokerDrainAppServiceTest.java`（同目录）、`application/local/LocalRouteAppServiceReceiveTest.java`、`LocalRouteAppServiceCleanupTest.java`（同目录）、`transformer/application/BrokerClusterAppTransformerTest.java`、`support/BrokerClusterCommandFixture.java`、`support/BrokerRaftTestBindings.java`。迁移新增 compile caller 时限于引用上述改名 API 的现有 Broker 测试/fixture，记录精确 diff 清单；不用测试-only 生产兼容壳。

- [ ] RED：固定 `Instant.EPOCH`、负时刻、sub-ms truncation、同 ms 完成/倒序拒绝、now==expireAt、zero grace、health 阈值；codec/snapshot 明确 v2 roundtrip/v1 拒绝与真实 restart；新增 T `support/raft/statemachine/BrokerTimeReplayTest.java`（D 独占）：固定 v2 command bytes 分别应用两个独立 StateMachine，比较决定/时间/phase/revision/epoch/状态，再与 snapshot 恢复+同一 tail 重放比较；固定旧时刻与 sub-ms 输入，期望毫秒归一，current-time mutation 或保留 sub-ms 均稳定失败，恢复后 green。命令 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerTimeReplayTest,AdvanceClusterOperationCommandTest,BrokerClusterCommandCodecTest,BrokerClusterSnapshotIsolationTest,BrokerRaftAdapterTest,GatewayRouteTableTest,ConnectionRouteValueObjectTest,BrokerDrainAppServiceTest,BrokerFailureObservationServiceTest,LocalRouteAppServiceReceiveTest -Dsurefire.failIfNoSpecifiedTests=false test`；保留预期失败。
- [x] GREEN：上述内部 absolute time 改 Instant xxxAt，五数字 command 改 changedAt + 原毫秒精度/epoch 校验，旧事件与更新过期分开；interface Transformer 生成映射、显式转换；持久 v2 拒绝 v1、无 alias/删除。同步 T fixture/accessor 后原精确命令通过；已用 monotonic/leaseDuration 保持，不新增 Clock。

C/D 所有权约束：D 独占 Server `adapter/cluster/{BrokerRaftAdapter,BrokerPeerAdapter}.java`，C 只提供新配置 accessor，不并发编辑；C 独占 RouteLookupHandler/授权 TTL 与全部 diagnostic internal DTO/Transformer/HTTP/Ops；W 独占 SDK BrokerClient timeout 输入与 runtime Duration。

**P3 / worker C：自有数值配置与 diagnostic HTTP/Ops（独占下列 S 和 O）。**

S 文件：
- `infrastructure/config/properties/BrokerProperties.java`
- `infrastructure/config/properties/ClusterProperties.java`
- `infrastructure/config/properties/BrokerRaftProperties.java`
- `infrastructure/config/BrokerClusterConfiguration.java`
- `infrastructure/config/ClientConfiguration.java`
- `infrastructure/config/RegistryGossipConfiguration.java`
- `infrastructure/config/RouteLookupConfiguration.java`
- `adapter/gossip/BrokerStateStore.java`
- `interfaces/scheduler/BrokerGossipProperties.java`
- `interfaces/scheduler/BrokerGossipScheduler.java`
- `interfaces/handler/routing/RouteLookupHandler.java`
- `infrastructure/chain/interceptor/GatewayRouteAuthorizationInterceptor.java`
- `application/diagnostic/BrokerDiagnosticAppService.java`
- `adapter/diagnostic/GossipSyncTracker.java`
- `model/cqrs/dto/BrokerInstanceDTO.java`
- `model/cqrs/dto/GossipRecordDTO.java`
- `transformer/interfaces/BrokerManagementHttpTransformer.java`
- `model/io/BrokerClusterOperationResponse.java`
- `model/io/BrokerInstanceResponse.java`
- `model/io/BrokerNodeResponse.java`
- `model/io/ConnectionRouteResponse.java`
- `model/io/DiagnosticSummaryResponse.java`
- `model/io/GatewayNodeResponse.java`
- `model/io/GossipRecordResponse.java`

另 `im-broker/im-broker-server/src/main/resources/application.yml`；当前检索旧 Broker 专属配置键只命中该文件，实施前重跑 deploy/im-test/scripts/Nacos 资源引用检查并仅增补真实 caller，不改标准框架键。

O 文件：
- `src/main/java/com/co/kc/imchat/management/ops/infrastructure/client/model/BrokerInstancePayload.java`
- `src/main/java/com/co/kc/imchat/management/ops/infrastructure/client/model/BrokerNodePayload.java`
- `src/main/java/com/co/kc/imchat/management/ops/infrastructure/client/model/ConnectionRoutePayload.java`
- `src/main/java/com/co/kc/imchat/management/ops/infrastructure/client/model/DiagnosticSummaryPayload.java`
- `src/main/java/com/co/kc/imchat/management/ops/infrastructure/client/model/GatewayNodePayload.java`
- `src/main/java/com/co/kc/imchat/management/ops/infrastructure/client/model/GossipRecordPayload.java`
- `src/main/java/com/co/kc/imchat/management/ops/model/io/BrokerInstanceResponse.java`
- `src/main/java/com/co/kc/imchat/management/ops/model/io/GossipRecordResponse.java`
- `src/main/java/com/co/kc/imchat/management/ops/model/io/BrokerNodeResponse.java`
- `src/main/java/com/co/kc/imchat/management/ops/model/io/GatewayNodeResponse.java`
- `src/main/java/com/co/kc/imchat/management/ops/model/io/ConnectionRouteResponse.java`
- `src/main/java/com/co/kc/imchat/management/ops/model/io/DiagnosticSummaryResponse.java`
- `src/main/java/com/co/kc/imchat/management/ops/model/cqrs/dto/BrokerInstanceDTO.java`
- `src/main/java/com/co/kc/imchat/management/ops/model/cqrs/dto/GossipRecordDTO.java`
- `src/main/java/com/co/kc/imchat/management/ops/transformer/infrastructure/BrokerClientTransformer.java`
- `src/main/java/com/co/kc/imchat/management/ops/transformer/interfaces/OpsHttpTransformer.java`
- `ui/src/api/ops.ts`
- `ui/src/utils/time.ts`
- `ui/src/views/BrokersView.vue`
- `ui/src/views/GatewaysView.vue`
- `ui/src/views/ConnectionsView.vue`
- `ui/src/views/DiagnosticsView.vue`
- `src/test/java/com/co/kc/imchat/management/ops/infrastructure/client/HttpBrokerManagementClientTest.java`
- `src/test/java/com/co/kc/imchat/management/ops/interfaces/http/OpsBoundaryTest.java`
- `ui/tests/Http.spec.ts`
- `ui/tests/Views.spec.ts`
- `ui/tests/Time.spec.ts`

T 测试：`application/diagnostic/BrokerDiagnosticAppServiceTest.java`、`transformer/interfaces/BrokerManagementHttpTransformerTest.java`、`interfaces/http/management/BrokerClusterControllerTest.java`、`BrokerManagementAccessTest.java`（同目录）、`infrastructure/config/properties/BrokerRoutingPropertiesTest.java`（仅原 Duration/zero 回归）；新增同目录 `BrokerTimePropertiesTest.java`，以及 `transformer/application/BrokerDiagnosticAppTransformerTest.java`（只有引用字段迁移）。

- [ ] RED：Spring 新键/default/零 snapshot interval/整秒与非法值，registry broker/gateway/connection list envelope 与 overview nested instance/diagnostic summary 的 Long string/null 断言（包含空诊断时间）、内部 Instant/Duration 毫秒映射、uptime/duration 转换，Ops mock 实际新 HTTP payload + UI string/safe integer/IANA 展示。Broker 命令 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerTimePropertiesTest,BrokerManagementHttpTransformerTest,BrokerClusterControllerTest,BrokerDiagnosticAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`；Ops `mvn -q -pl im-management/im-ops -am -Dtest=HttpBrokerManagementClientTest,OpsBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`；UI `npm --prefix im-management/im-ops/ui run test:unit`。首次编译依赖 P1/P2未完成时标 pending integration，不冒称行为 red。
- [x] GREEN：runtime Duration 与数值配置/wire/第三方边界严格区分：RouteLookupHandler/GatewayRouteAuthorizationInterceptor 改 Duration gatewayTtl，BrokerGossipProperties 改 Duration timeout；S/O BrokerInstanceDTO 与 GossipRecordDTO 改 Duration uptime/duration（C 独占），采样/阈值不变。不改 Plugin/标准框架键。HTTP Long xxxAtMs/durationMs/uptimeMs；Ops payload Long→内部 Instant/Duration→HTTP Long；BrokerClientTransformer 与 OpsHttpTransformer 显式声明 xxxAtMs↔xxxAt、durationMs↔duration、uptimeMs↔uptime 映射，含 nested instance/summary；TS 当前 number wire 迁为 string，边界 safe parse 后显示；已有 time helper 复用，不新建 wrapper。重跑三条 RED 命令，UI 再 `npm --prefix im-management/im-ops/ui run typecheck`。

**P4 / 主 Agent：集成、文档与验收（worker 不并发改计划/文档）。**

- [x] 同步 `im-broker/ARCHITECTURE.md`、`im-broker/README.md`、`im-broker/im-broker-server/README.md`、`im-broker/im-broker-sdk/README.md`、`im-gateway/im-ws-gateway/im-ws-gateway-server/README.md`、`im-management/im-ops/README.md`；旧 `docs/design-docs/2026-10-12-typed-raft-command-design.md` 标明其 v1 被新设计取代，其余历史证据保留。仅有新复用规则才改 `docs/references/CODING_GUIDE.md`、`HARNESS_GUIDE.md`、`HARNESS_RULE_MATRIX.md`，不新增全仓正则豁免或检查器。
- [x] 合并三个 disjoint worker 输出后，搜旧自有字段/key（third-party/历史证据分类保留），核对实际全 diff/caller 列表；反证至少一个字段转换或边界测试确保错误实现失败，恢复后 green。`git diff --check`。
- [x] 依次 `mvn -q -pl im-broker/im-broker-server -am clean test`、`mvn -q -pl im-gateway/im-ws-gateway/im-ws-gateway-server -am test`、`mvn -q -pl im-management/im-ops -am test`、`npm --prefix im-management/im-ops/ui run test:unit` 与 `typecheck`；前层失败先归因/修复再继续。
- [ ] `./scripts/verify.sh architecture`、`behavior`、`e2e`、`routing-ha`、`startup`（自有启动配置键变更适用）、`quick`、`clean`、`full`；独立实现 Review、quality/cleanup/handoff 与 fresh fingerprint。保留网络/环境阻塞，不以后层通过覆盖前层失败；仅最终门禁完成才归档 A5，未经用户要求不提交。

本轮文档修订检查：`git diff --check` 与 `./scripts/check-drift.sh` exit 0，日志 `/tmp/broker-time-docs-review-fix-drift.log`；未执行实现测试。

本轮主会话证据与恢复：

- 三组接手快照/diff：`/tmp/broker-time-W-before`、`/tmp/broker-time-D-before`、`/tmp/broker-time-C-before`；D worker 工具被中止但已完成源码迁移，主会话接手编译与验证。初始 Java RED 只保留 source delta，未实际运行旧实现，不冒称编译/行为 red；因此上方 RED checkbox 仍未关闭。UI Time.spec 首轮 2 failures/1 pass（`/tmp/broker-time-C-ui-red.log`），green 16 项与 typecheck exit 0（`/tmp/broker-time-C-ui-{final,typecheck-final}.log`）。
- SDK 聚焦 exit 0 `/tmp/broker-time-sdk-green.log`；D 聚焦 exit 0 `/tmp/broker-time-D-green.log`；C 聚焦含新增真实 caller 测试（BrokerGossipSchedulerTest、RouteLookupHandlerTest、BrokerDiagnosticControllerTest、GossipSyncTrackerTest）exit 0 `/tmp/broker-time-C-green.log`；Gateway 聚焦 exit 0 `/tmp/broker-time-gateway-green.log`。
- Ops 首轮 testCompile 失败，遗漏实际 Payload ctor/accessor 的测试调用；扩展准确 scope 为 `OpsQueryAppServiceTest.java`，并修正 `HttpBrokerManagementClientTest` 的 client payload 与显式 Transformer 分工，生产不改；原聚焦加 OpsQueryAppServiceTest 16 项通过 `/tmp/broker-time-C-ops-focused-fix.log`。
- 真实反证：UpdateBrokerDrainProgressCommand 采样当前时间 mutation 2 failures exit 1（`/tmp/broker-time-replay-mutation.log`）；保留 sub-ms mutation 2 failures exit 1（`/tmp/broker-time-subms-mutation.log`），已恢复原 normalization；重放复跑 exit 0（`/tmp/broker-time-replay-restored.log`），clean reactor 同样包含 green。反证是后补测试有效性证据，不伪装为实现前 RED。
- 模块精确命令按 P4：Broker clean test、Gateway test、Ops test 全 exit 0（`/tmp/broker-time-{reactor,gateway-reactor,ops-reactor}.log`）。architecture 68 项/2 skipped exit 0、behavior 34 场景 exit 0（`/tmp/broker-time-{architecture,behavior}.log`）。E2E 首轮 bootstrap 注解加载异常，清理 `im-test/im-e2e-test` reactor 产物后原 e2e exit 0、1 场景（`/tmp/broker-time-e2e{,-clean,-retry}.log`）。startup 10 个应用 exit 0（`/tmp/broker-time-startup.log`）。
- routing-ha exit 1，构建测试阶段未触及的 Message `ImMessageNotifierInvokerTest` 有无效测试 bytecode VerifyError（`/tmp/broker-time-routing-ha.log`），未修改 Message 或放宽 gate；前层失败已归因但此缺口仍保留。
- 专门 reviewer 设计 Revise→Accept；实现首轮生产代码 Accept/整体 Revise（文档滞后与反证日志待完成）；文档同步及日志完成后复审代码/文档 Accept、findings none，最终交付门禁仍待执行。
- 下一步 `./scripts/verify.sh quick`、`clean`、`full` 与 quality/cleanup/handoff；最终日志 `/tmp/broker-time-final-{quick,clean,full}.log`，以 Harness 实际结果为准，停止 tracked 状态回填以保持稳定证据。不提交、不归档，A5 active，原 WIP/失败归因保留。

#### changedAt 最小收敛（用户批准；独立设计 Review Accept）

事实源：[changedAt 增量决定](../../design-docs/2026-10-11-broker-cluster-adapter-boundaries-design.md#changedat-最小收敛增量用户批准独立设计-review-accept)。A5 active/WIP=1；父会话 readiness 已通过；独立设计 Review Accept、findings none 已记录后实施。九个方法内部采样，failDrain/saveRecoveryEvidence/verifyRouteRecovery 保留显式时间；saveRecoveryEvidence 混合 caller 不新增重载。既有 failDrain finding、full 缺口和其他 WIP 不关闭。

精确实施文件（均相对于 `im-broker/im-broker-server/src/`，包前缀 `com/co/kc/imchat/broker/`）：
- main：`adapter/cluster/BrokerRaftAdapter.java`、`application/cluster/BrokerClusterAppService.java`、`application/cluster/BrokerMembershipAppService.java`。
- test：`adapter/cluster/BrokerRaftAdapterTest.java`、`support/BrokerRaftTestBindings.java`、`support/StatefulClusterAdapterFixture.java`、`application/cluster/BrokerClusterAppServiceChangeTest.java`、`application/cluster/BrokerClusterLeaseGrantTest.java`、`application/cluster/BrokerClusterAppServiceFenceTest.java`；`application/cluster/BrokerDrainAppServiceTest.java`、`application/cluster/BrokerFailureRecoveryAppServiceTest.java` 用于补强共享时间不被改写的捕获断言。
- 文档：`docs/design-docs/2026-10-11-broker-cluster-adapter-boundaries-design.md`、本计划、`PROGRESS.md`。不改当前 README/Architecture，不改 Transformer/command/Codec/状态机/领域/Plugin/架构规则；`BrokerClusterCommandFixture` 的 command 固定时间保留，既有调用经 bindings 适配，不批量改领域同名方法。

- [x] 只读核对 status、当前文档/计划、三个生产 caller 文件、Domain 时间重验与直接测试/fixture；记录九个收敛方法及三个保留方法，写入上述事实源与本清单，保留既有 WIP。
- [x] 独立设计 Review Accept、findings none（父会话已取得）；核对分类、混合 caller、completeDrain/ACK 领域重验、bindings 精确时间例外；Accept 前禁止实施。Review Revise/Block 必须保留并修订，不视为通过。
- [x] RED：先在 `adapter/cluster/BrokerRaftAdapterTest.java` 将真实 Runtime 载荷捕获测试迁为九个无时间参数入口，增加每个生成命令的毫秒时间上下界断言；三个显式入口继续固定值完整比较。执行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerRaftAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test`；旧签名预期 testCompile 失败（方法参数数量不匹配），不是行为 red。记录实际错误与日志；实施后补时间载荷反证，不能只以编译 red 证明生成时间。
- [x] 最小实现：只在上述三个 main 文件移除九个 changedAt 参数/实参，每个具名入口使用一次 Instant.now() 构造原 command；三个显式入口/调用、revision 时点、超时与结果分支不动。不新增 Clock/重载/构造器，不删除命令时间。
- [x] 迁移上述 test Mockito/fixture/bindings；生成时间例外只适用于九个收敛入口，其他请求字段仍严格校验并保留错 digest 拒绝。`StatefulClusterAdapterFixture` 按新签名模拟提交时间；应用测试仅证明编排，不能替代真实 Runtime 捕获。Drain/FailureRecovery 测试捕获并证明 failDrain 的判断/提交时间一致、saveRecoveryEvidence 与 verifyRouteRecovery 同轮时间一致。
- [x] GREEN：`mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerRaftAdapterTest,BrokerClusterAppService*Test,BrokerClusterLease*Test,BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest' -Dsurefire.failIfNoSpecifiedTests=false test`。随后临时将九入口中代表性生成时间改为固定旧值，重跑 `BrokerRaftAdapterTest`，预期生成时间断言失败，恢复后原命令 green；不得以扩大时间范围或删除字段比较消除失败。
- [x] 按窄到宽顺序验证 Reactor、architecture、behavior 与 diffcheck；精确命令及失败归因见下方主会话证据。
- [ ] 独立实现 Review 与最终 `./scripts/verify.sh quick`、`./scripts/verify.sh clean`、`./scripts/verify.sh full`；若实际触及 realtime transport/wiring，追加 `./scripts/verify.sh e2e`。刷新稳定工作树 Harness 指纹，记录精确结果，不覆盖历史缺口、不提交、不归档。

本轮证据（2026-10-14）：
- 编辑前保存所有 modified/untracked 文件及 index/worktree diff、status：`/tmp/changedAt-before/`。不恢复整个混合文件，不提交。当前 README 的并行修改归父会话，不计本增量。
- RED 原精确命令 exit 1（旧 IDE unresolved class 导致 discovery 失败，非预期 red）；加 `clean` 的 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerRaftAdapterTest -Dsurefire.failIfNoSpecifiedTests=false clean test` exit 1，明确九个新调用参数数量不匹配，日志 `/tmp/changedAt-red-clean.log`。未改生产前取得编译 red。
- GREEN 上述清单精确聚焦命令最终 exit 0，109 项全通过：`/tmp/changedAt-final-focused.log`。中途两轮测试迁移问题（checksum 格式/Instant 类型时间字段，旧固定 completion 时间应改由保留显式 failDrain 证明；共享验证捕获需受控 evidence 返回阶段）已修复，日志 `/tmp/changedAt-green{,2,3,4}.log` 保留。
- 反证只将 `commitOperation` 的时间临时替换为 `Instant.ofEpochMilli(1L)`，Adapter 原精确测试命令 exit 1，真实 Runtime 捕获断言明确 1L 不在调用前后边界；`/tmp/changedAt-mutation.log`。恢复后原聚焦命令 exit 0：`/tmp/changedAt-restored-green.log`；随后强化 cleanup failedAt 双采样断言，最终聚焦再次通过。
- bindings 完整 JSON 请求载荷断言未放宽，错 digest 拒绝保留；九个生成入口在真实 Runtime 捕获只对时间作例外并逐项完整比较其他字段，三个显式入口完整固定载荷比较。新增 ACK 超时判断/提交、cleanup failedAt 判断/提交、恢复 evidence/verify 同轮对象与固定值捕获。
- `mvn -q -pl im-broker/im-broker-server -am test` exit 1：Broker 518 项、0 failures/3 errors，未触及 Controller 的旧 IDE class 抛 `Flexible Constructor Bodies ... source level 25`，两个应用上下文错误同源；日志 `/tmp/changedAt-reactor.log`。不能记为 reactor green；父会话可先 `clean test` 清除旧构建产物后重试，architecture/behavior 必须依序等待前层修复或归因。无相关生产源码修复。

主会话证据：
- Reactor 首轮因未改动 Controller 的旧 IDE 字节码错误失败；`mvn -q -pl im-broker/im-broker-server -am clean test` exit 0（`/tmp/changedAt-reactor-clean.log`），不改生产源码规避错误。
- Architecture 首轮 exit 1，未改动 `BaseEntity.<init>` 的无效字节码 `VerifyError`；`mvn -q -pl im-plugin/im-datasource -am -Dmaven.test.skip=true clean install` exit 0 后，原 `./scripts/verify.sh architecture` 复跑 exit 0，68 项/2 skipped（`/tmp/changedAt-architecture{,-retry}.log`、`/tmp/changedAt-datasource-clean.log`）。`./scripts/verify.sh behavior` exit 0，34 场景（`/tmp/changedAt-behavior.log`）。
- 独立实现/重试文档 Review：代码与文档 Accept、findings none；正式交付 Revise（最终门禁未完成）。Review 限定 `/tmp/changedAt-only.diff` 和文档增量，既有 failDrain finding 未关闭。
- 同步此前用户提出的重试说明与排版：四份 Broker 当前 README/Architecture 普通正文按逻辑段落折行；根 README 明确 hint 不决定重试目标、deadline 过期异常、刷新重算目标及重试上限。快照 `/tmp/broker-retry-docs-before/`，代码围栏不变、独立复审通过。

恢复入口：九入口实现/聚焦/反证、clean reactor、architecture、behavior 与独立复审完成；接手最终 `./scripts/verify.sh quick`、`clean`、`full`（日志 `/tmp/changedAt-final-{quick,clean,full}.log`），以 Harness 实际结果为准，运行期间不修改 tracked 状态。无 transport/wiring 变化，本增量不新增 e2e 要求。A5 active/WIP=1、历史 full 和 failDrain finding 保留。回退仅撤本增量，禁止恢复整个混合工作树文件覆盖原 WIP。

#### 当前文档去历史叙事与阅读性整理（2026-10-14）

- [x] 仅修改 `im-broker/ARCHITECTURE.md`、`im-broker/README.md`、`im-broker/im-broker-server/README.md`、`im-broker/im-broker-sdk/README.md`：移除已删接口/类型、旧目录、命名迁移及实现清理过程；有效职责改为现状描述，保留兼容性、数据目录、旧 epoch、tombstone 与恢复失败约束。
- [x] Architecture 按应用入口、Registry、缓存、装配、RPC、提交和 Lease 拆分短小节与职责表；无超过 4 源文件行的正文段落或单项列表，未修改代码。接手快照与隔离 diff 位于 `/tmp/broker-current-docs-before/`；readiness 与 diffcheck 通过。
- [x] 独立只读 Review Accept、findings none，抽查 Adapter/状态机/Codec；职责和短段结构清楚，未丢失现行安全及兼容性约束。
- [ ] 最终门禁仍有缺口：`./scripts/verify.sh quick` 在 900 秒工具时限超时，日志末尾位于架构依赖构建的 UI npm 安装阶段，无最终通过结果；`clean` exit 0；`full` exit 1，失败包含未触及的 Message `ImMessageNotifierInvokerTest` 执行时抛出 `Unresolved compilation problems: Flexible Constructor Bodies ... source level 25`（日志第 1042 行起），不归因于本轮纯文档修改，未修复该模块或将其他通过结果冒充 full。日志 `/tmp/broker-current-docs-{quick,clean,full}.log`。A5 active，不提交；此状态回填后机器指纹需后续刷新。

#### Broker 文档与注释事实对齐（2026-10-14）

本轮仅修正文档和注释，不改运行行为、类名、API 或历史证据；保留其他 WIP，A5 仍 active。

- [x] 对照源码核对 `im-broker/ARCHITECTURE.md`、`im-broker/im-broker-server/README.md` 与设计索引及 Adapter/类型化 Raft 两份设计；定位旧目录、ReadIndex 字段、成员/ACK 用例入口及已取代决策。
- [x] 在上述五份文档修正当前事实，并在 `application/cluster/BrokerClusterAppService.java`、`application/frame/FrameDispatchAppService.java` 各修正一处职责/超时注释；历史方案只补状态标记。
- [x] 用户追加范围：扫描 Broker Server/SDK 347 个生产 Java 文件，为 20 个缺失类级 JavaDoc 的顶层类型补齐中文职责说明（SDK 4、Server 16）；嵌套 Builder/私有记录不作机械补齐。父会话复核去注释后的代码不变，快照及清单在 `/var/folders/h9/drjh32n10x170kpxrbpl_1p00000gn/T/broker-class-docs-7fowaime/`。
- [x] 独立文档 Review 首轮 Revise，指出激活 Begin/fence/Commit 顺序、Gateway TTL 范围与领域/基础设施图依赖方向；已按源码修正，保留首轮结论；独立复审 Accept、findings none，覆盖五份文档、两处更正注释及 20 处新增类级注释。
- [x] 契约检查：逐项对照当前类、方法、目录和响应字段；扫描 347 个生产文件无缺失顶层类级注释，20 个新增注释文件去注释代码一致，`git diff --check` 通过，独立复审 Accept。
- [x] 执行门禁并保留真实结果：readiness 通过；首轮 quick exit 0（`/tmp/broker-docs-quick.log`，追加类注释前）；最终 quick 首轮 exit 1，所有检查执行后因主会话并行更新计划导致 worktree fingerprint 变化（`/tmp/broker-docs-final-quick.log`），不可视作稳定验收。clean exit 0（`/tmp/broker-docs-final-clean.log`）；full exit 17（`/tmp/broker-docs-final-full.log`），Harness 自测通过后 routing-ha Docker 构建请求 auth.docker.io 镜像令牌网络超时，未进入完整 Maven verify。A5 保持 active，未提交。
- [ ] 状态记录写完后停止修改 tracked 文件，重跑 `./scripts/verify.sh quick`、`./scripts/verify.sh clean` 刷新稳定证据（`/tmp/broker-docs-stable-{quick,clean}.log`）；以 Harness 中实际结果为准，本 checkbox 保留为最终证据入口，避免为回填状态再次失效。full 网络缺口不关闭。

#### 单次转换中间变量检查

- [x] 检查 BrokerRaftAdapter、直接相关 Adapter 与 Raft Codec，beginOperation 已直接构造提交；仅将 `support/raft/codec/BrokerClusterCommandCodec.encode` 的单次 envelope 变量内联。保留承担校验、多次读取、异步结果处理与指标顺序的变量，不扩大清理范围。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterCommandCodecTest,BrokerRaftAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test` exit 0；`git diff --check` 通过，日志 `/tmp/inline-cleanup-tests.log`。本增量未重跑全量门禁；此前 full 网络阻塞仍保留，不归档、不提交。

#### 状态机构造器收敛（用户批准）

- [x] `support/raft/statemachine/RaftBrokerClusterStateMachine.java` 仅保留四参数构造器，删除两处默认参数转发；23 处测试调用显式传入容量与成员端点（10 个测试文件），原 1000/空 Map 语义不变，生产装配不变。
- [x] 聚焦 Snapshot/Lease/Adapter/Change 测试：`mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerClusterSnapshotIsolationTest,BrokerLeaseGrantAtomicityTest,BrokerRaftAdapterTest,BrokerClusterAppServiceChangeTest' -Dsurefire.failIfNoSpecifiedTests=false test` exit 0，日志 `/tmp/raft-constructor-focused.log`。
- [x] Broker reactor 首轮工具 300 秒超时，原命令 600 秒复跑 exit 0（`/tmp/raft-constructor-reactor-retry.log`）；quick 中包含 architecture，复跑 exit 0（`/tmp/raft-constructor-quick-retry.log`）；clean exit 0（`/tmp/raft-constructor-clean-retry.log`）；git diff --check 通过。前轮验证链被用户中止，无结果，不冒称通过。
- [ ] full 首轮 Harness 自测阶段 exit 1，单独 test-harness.sh 复跑 exit 0；原 full 第二轮 exit 17，routing-ha 构建拉取 SkyWalking 镜像时 auth.docker.io 授权令牌请求网络超时，未进入完整 Maven verify。日志 `/tmp/raft-constructor-full-{retry,second}.log` 与 `/tmp/raft-constructor-harness.log`。保留 A5 缺口，不提交。当前仓库文件名/全文搜索未发现 LockGuard，未修改该类型。

#### 嵌套本机视图与具名提交收敛（用户批准）

本增量取代先前删除 LocalBroker 查询视图的决定：仅恢复 Adapter 的公开静态嵌套 LocalBroker，持有同一 Runtime，实时查询角色，无独立 Bean/关闭权；不恢复顶层门面。查询动词统一为 getLocalBroker/getId/getLeaderEndpoint/getClusterSnapshot/getCommittedState/getRaftMembership，find/fetch/is 查询保持。

- [x] 修改 `adapter/cluster/BrokerRaftAdapter.java` 与全部生产/test 调用方：Begin/Commit 成对为 beginOperation/commitOperation；submitCommand private；成员拒绝/成功通过 failMembershipOperation/completeMembershipOperation 进入 Adapter，日志转换迁入 `transformer/infrastructure/BrokerClusterRaftTransformer.java`，应用层不直接提交命令。
- [x] 迁移 test-scope stub/mock 为具名 Adapter 操作绑定；真实节点测试不访问私有提交，不新增生产测试入口。保留领域与状态机的原命令回放接口，不混淆类内同名方法。`mvn -q -pl im-broker/im-broker-server -am test` 最终 exit 0，当前源码对应 Broker 513 tests/0 failures/errors/skipped（`/tmp/local-broker-final-reactor.log`）；`./scripts/verify.sh architecture` exit 0（`/tmp/local-broker-final-architecture.log`）。
- [x] 当前 Architecture/README/设计增量已更新。独立 Review 首轮 Revise：test dispatch 丢字段、具名 mock 覆盖真实接线；已加入完整载荷比较及错 digest 拒绝断言、Runtime 构造边界捕获 12 项真实具名提交、同一 LocalBroker 动态 false→true→false 断言，并删除被覆盖的 real-method stubs；复审代码 Accept、findings none。应用测试只证明编排+领域，不宣称证明真实 Adapter 接线。保留已有 failDrain 结果处理 finding，本次不改行为。
- [ ] 主会话 quick/clean exit 0；full exit 17，routing-ha 构建阶段请求 auth.docker.io 的 Eclipse Temurin 镜像授权令牌网络超时，未进入完整 Maven verify。日志 `/tmp/local-broker-final-{quick,clean,full}.log`。正式交付仍 Revise、A5 active，不提交。

迭代归因：类型/可见性迁移首轮 testCompile 失败后修复；测试发现嵌套 Mockito unfinished stubbing 与 FixedRepository record accessor 不匹配，按真实查询入口修复，未开放私有 submitCommand。Broker reactor 复跑曾在未改动 im-cache 的 JetCacheLocalValuesTest 容量断言失败（预期≤2、实际4），记录 `/tmp/local-broker-reactor2.log`，待原命令复跑，不归因于本增量。

Adapter 六方法命名主会话收尾：独立 Review 代码 findings none，正式结论 Revise（缺独立可读的改名前基线 diff 和最终门禁）。`./scripts/verify.sh quick`、`clean` exit 0，日志 `/tmp/raft-naming-final-{quick,clean}.log`；`full` exit 17，routing-ha Docker 构建请求 auth.docker.io 镜像令牌网络超时，未到完整 Maven verify（`/tmp/raft-naming-final-full.log`）。保留 Revise、不宣布 A5 完成，未提交。

主会话 Peer 简化收尾证据：独立 Review 代码 Accept、整体 Revise（当前文档残留与交付证据缺口），已修正 Broker Architecture/README 中两处旧 Params 描述；保持 Revise 可见，不冒称复审完成。`behavior` exit 0，34 场景；`e2e` exit 0，1 场景；`quick` 与 `clean` exit 0，日志 `/tmp/peer-final-{behavior,e2e,quick,clean}.log`。`full` 首轮在 Harness 自测阶段 exit 1，单独 `./scripts/test-harness.sh` 复跑 exit 0；原 full 复跑 exit 17，routing-ha 构建阶段访问 auth.docker.io 拉取 SkyWalking/Temurin 镜像授权令牌网络超时（`/tmp/peer-final-full-retry.log:1206–1220`），未进入完整 Maven verify。不修订网络/安全设置，不以窄测试替代 full；A5 保持 active，未提交 Git。

#### BrokerRaftAdapter 阅读性整理（2026-10-13，用户批准）

本轮仅整理一个生产文件，另同步本计划与 `PROGRESS.md`；A5 active/WIP=1，保留原 WIP、不提交 Git。不处理上轮 failDrain 行为 finding，不改公开方法名、分支结果、异常、null 或运行参数，不拆类/搬包/新增抽象，不新增测试。

- [x] 精确范围：`im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/adapter/cluster/BrokerRaftAdapter.java`。方法按构造、本机身份/角色/领导权证明、已提交状态查询、集群 operation 提交、Raft membership、Lease 原子签发、snapshot/close 排列；Builder 一 setter 一行，长签名/转换参数换行，promote/demote 两步 lambda 展开，删除构造编号及重复动作说明，修正 removeMember learner 分支复制注释和缩进。唯一 Runtime、落后读取、Lease 共享锁、timeout 可能已提交和两步 membership 恢复语义保留。
- [x] `./scripts/verify.sh readiness` exit 0，Java 21/Maven 3.8.4/Node 26.10.0（控制台）。`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerRaftAdapterTest,BrokerMembershipSchedulerTest,BrokerClusterAppServiceChangeTest -Dsurefire.failIfNoSpecifiedTests=false test` exit 0，3 类/57 tests/0 failures/errors/skipped；日志 `/tmp/broker-readability-focused.log`。
- [x] `mvn -q -pl im-broker/im-broker-server -am test` 首轮 exit 1，仅既有 `RaftLibrarySpikeTest.addsPromotesDemotesAndRemovesLearner` 预期 EBUSY、实际 EPERM（Leader stepped down）；未改该测试或运行参数，原命令复跑 exit 0。Broker 当前源码对应 XML 511 tests/0 failures/errors/skipped，排除历史已删/移动 XML。日志 `/tmp/broker-readability-reactor.log`、`/tmp/broker-readability-reactor-retry.log`。
- [x] `./scripts/verify.sh architecture` exit 0，68 tests/0 failures/errors/2 skipped；日志 `/tmp/broker-readability-architecture.log`。`git diff --check` exit 0。
- [x] 保存接手原文件 `/tmp/BrokerRaftAdapter.before-readability.java` 和限定增量 `/tmp/broker-readability.diff`；`python3 /tmp/check-broker-readability.py` exit 0（`/tmp/broker-readability-equivalence.log`），33 个公开方法/构造器在仅归一化两处批准的 membership lambda 展开后代码 token 完全一致，imports 集合与类/字段 token 不变。failDrain 仅排版/重复注释变化；无新行为，不声称 red。
- [ ] quick/clean/full、独立 Review 与最终 Harness 指纹刷新留主 Agent。上轮 finding/Revise 和 full 环境缺口不由本轮窄验证覆盖；文档更新后的全树指纹须最终刷新。`/tmp/broker-readability*`、原文件及核对脚本为本轮审查证据，标准 ignored target/Harness 输出保留。

#### BrokerRaftAdapter 方法命名收敛（2026-10-13，用户批准）

类名不变，行为保持；决策增量见 [职责重划设计](../../design-docs/2026-10-11-broker-cluster-adapter-boundaries-design.md#brokerraftadapter-方法命名收敛2026-10-13用户批准)。A5 仍 active/WIP=1，保留其他 WIP、不提交；不新增 API、compat 壳或镜像实现测试，现测试只迁移引用、保持断言。

- [x] 核对 `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/domain/cluster/model/BrokerCluster.java` 的 Commit 全分支与 revision：所有操作实质改变节点集合、成员角色或流量状态；Begin 登记 revision+1，成功 Commit 再+1，epoch 取冻结 resultEpoch，重复 Commit 不推进；拒绝/冲突不改拓扑。Domain commitOperation 与 StateMachine getCluster 不改。
- [x] 修改 `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/adapter/cluster/BrokerRaftAdapter.java` 六个公开方法与内部提交/成员查询：getCluster→clusterSnapshot、submitChange→submitCommand、persist(RoutingEpochFence)→commitRoutingFence、consensusMembership→raftMembership、registerOperation→beginOperation、commitOperation→commitTopologyChange。
- [x] 迁移同模块以下精确文件（路径以 `src/` 为根、包前缀 `com/co/kc/imchat/broker/`）：main 的 `application/cluster/{BrokerClusterAppService,BrokerMembershipAppService}.java`、`interfaces/scheduler/{BrokerDrainScheduler,BrokerFailureRecoveryScheduler,BrokerMembershipScheduler,RoutingLeaseScheduler}.java`；test 的 `adapter/cluster/BrokerRaftAdapterTest.java`、`application/cluster/{BrokerClusterAppServiceActivationTest,BrokerClusterAppServiceChangeTest,BrokerClusterAppServiceFenceTest,BrokerClusterAppServiceGatewayRoutesTest,BrokerClusterAppServiceTest,BrokerClusterLeaseGrantTest,BrokerClusterLeaseInstallationTest,BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest}.java`、`application/local/{LocalRouteAppServiceCleanupTest,LocalRouteAppServiceReceiveTest}.java`、`infrastructure/interceptor/{IdentityAccessInterceptorsTest,RouteBrokerExecutionInterceptorTest}.java`、`infrastructure/monitoring/RoutingAuthorityMetricsTest.java`、`interfaces/handler/routing/RouteLookupHandlerTest.java`、`interfaces/scheduler/{BrokerLifecycleRecoveryTest,BrokerMembershipSchedulerTest,RoutingLeaseSchedulerTest}.java`、`support/{BrokerClusterCommandFixture,BrokerClusterConsensusStub,BrokerRequestChainTestSupport,StatefulClusterAdapterFixture}.java`。聚焦命令：`mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerRaftAdapterTest,BrokerClusterAppService*Test,BrokerClusterLease*Test,BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest,BrokerMembershipSchedulerTest,BrokerLifecycleRecoveryTest,RoutingLeaseSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false test`。
- [x] 更新 `im-broker/im-broker-server/README.md`、`im-broker/ARCHITECTURE.md` 当前入口及 Begin/Commit 语义；上述设计追加增量、不批量重写历史；同步本计划与 `PROGRESS.md`。验证：`mvn -q -pl im-broker/im-broker-server -am test`、`./scripts/verify.sh architecture`、`git diff --check`；全仓 Java 搜索六个旧方法名并逐个核对 receiver 类型。
- [ ] 最终 quick/clean/full、独立 Review 和稳定工作树 Harness 指纹刷新留主 Agent；不把窄验证当作最终交付或覆盖此前 full 环境缺口。

本轮实际证据：
- `./scripts/verify.sh readiness` exit 0（控制台），Java 21/Maven 3.8.4/Node 26.10.0；修改前已读 status、PROGRESS 与 AGENTS。
- 上述聚焦 exit 0，13 类/113 tests/0 failures/errors/skipped，日志 `/tmp/raft-naming-focused.log`。
- Broker reactor exit 0，当前源码对应 XML 511 tests/0 failures/errors/skipped；im-raft 39 tests 全通过，排除历史已删/移动 XML。日志 `/tmp/raft-naming-broker.log`。
- architecture exit 0，68 tests/0 failures/errors/2 skipped，日志 `/tmp/raft-naming-architecture.log`；`git diff --check` exit 0。命令均无失败；日志里的故意 quorum unavailable 与关闭警告不是测试失败。
- 30 个 Java 文件共迁移 165 处标识符（含 Adapter 声明/内部调用与 test fixture 声明）：clusterSnapshot 46、submitCommand 68、commitRoutingFence 5、raftMembership 17、beginOperation 20、commitTopologyChange 9。其中 caller/Mockito/fixture 引用 129 处，fixture 声明/实现 14 处，Adapter 声明/内部调用 22 处。保留 Domain commitOperation、StateMachine getCluster、BrokerClusterCommandFixture.commitOperation 与 fences.persist 等非 Adapter 方法；全仓无旧 Adapter receiver 调用或方法引用。
- 没有新增测试或行为，不声称 red；本轮只保留 `/tmp/raft-naming-*.log` 诊断文件及标准 ignored target/Harness 输出。文档更新改变全树指纹，主 Agent 最终刷新。

#### Peer 参数与传输行为保持简化（2026-10-13，用户批准）

用户明确批准两项简化，取代旧“读取证明传输归 im-raft/RaftPeerClient”约束；决策已追加到
[职责重划设计](../../design-docs/2026-10-11-broker-cluster-adapter-boundaries-design.md#peer-调用行为保持简化决策2026-10-13)。
业务 Peer RPC 归 BrokerPeerAdapter，原生 Raft 通信归 Runtime/JRaft。A5 保持 active/WIP=1，
不修改历史记录、不提交 Git、不扩展领域迁移、不改执行器/超时/deadline/hopCount/失败策略。

- [x] 修改 `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/adapter/cluster/BrokerPeerAdapter.java`，lookupRoutes 拆出 SDK BrokerNode、公共 UserId、领域 RoutingEpoch；同文件保留领域 BrokerNode import，仅真实同名 SDK 使用 FQN。fetchQuorumReadIndex 直接 invoker.invoke 原六参数，保留身份/非负索引校验与同步失败阶段。删除 `src/main/java/com/co/kc/imchat/broker/model/cqrs/query/BrokerRouteLookupParams.java`。
- [x] 修改同模块 `src/main/java/com/co/kc/imchat/broker/application/frame/FrameDispatchAppService.java`、`src/test/java/com/co/kc/imchat/broker/support/RouteLookupStub.java`、`src/test/java/com/co/kc/imchat/broker/adapter/cluster/{BrokerPeerAdapterTest,BrokerRaftAdapterTest}.java`，迁移全部调用并去除 unused import。聚焦基线/green 命令：`mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerPeerAdapterTest,FrameDispatch*Test,RouteLookup*Test' -Dsurefire.failIfNoSpecifiedTests=false test`。
- [x] 删除 `im-plugin/im-raft/src/main/java/com/co/kc/imchat/plugin/raft/client/RaftPeerClient.java` 和同模块 `src/test/java/com/co/kc/imchat/plugin/raft/client/RaftPeerClientTest.java`；确认 im-raft 唯一 Bolt 源码调用后删除 `im-plugin/im-raft/pom.xml` 的 im-bolt 依赖。真实 JRaft 依赖与 Runtime 不变。验证：`mvn -q -pl im-broker/im-broker-server -am test`（含 im-raft 全套）。
- [x] 修改 `im-test/im-architecture-test/src/test/java/fixture/broker/support/raft/codec/RpcOwner.java`，使用实际 BoltInvoker 负例继续保护 support.raft；LayerBoundaryTest 规则不变。修改 `im-plugin/im-raft/README.md`、`im-broker/im-broker-server/README.md`、`im-broker/ARCHITECTURE.md`、`docs/references/{HARNESS_GUIDE,HARNESS_RULE_MATRIX}.md` 当前传输归属，记录职责 Review-only 的自动化条件。验证：`./scripts/verify.sh architecture`、`git diff --check`、全仓旧类型 Java/POM 搜索。
- [ ] 最终 quick/clean/full/behavior/e2e、独立 Review 与稳定工作树 Harness 指纹刷新留主 Agent，不把本轮窄验证视为交付完成。

本轮证据及失败归因：
- `./scripts/verify.sh readiness` exit 0，日志 `/tmp/peer-simplification-readiness.log`。
- 聚焦首轮 exit 1，28 tests/1 failure：接手前 BrokerPeerAdapterTest.prepareLeaseRejectsAnotherBrokerIdentity 期望 `target broker`，生产实际异常消息仍为 `brokerNode broker`。仅修正测试的原消息断言，不改生产错误文本或异常；原命令复跑基线 exit 0、28 项，日志 `/tmp/peer-simplification-baseline.log`、`/tmp/peer-simplification-baseline-retry.log`。此为既有断言漂移，不是本次迁移回归 red。
- 参数和传输迁移后聚焦 exit 0，28 项（`/tmp/peer-simplification-focused.log`）；补 requester/user/epoch 与 quorum 配置超时、同步 IO、原异常对象传播断言后 exit 0，29 tests/0 failures/errors/skipped（`/tmp/peer-simplification-focused-final.log`）。这些为行为基线与 green 补强，不冒称首次 red。
- Broker reactor exit 0，当前测试源码对应 XML 511 tests/0 failures/errors/skipped；im-raft 39 tests/0 failures/errors/skipped，排除已删 PeerClient 等历史 XML。日志 `/tmp/peer-simplification-broker.log`。
- architecture passed，68 tests/0 failures/errors/2 skipped；`.harness/verifications/architecture.json` 记录 worktreeStable=true，日志 `/tmp/peer-simplification-architecture.log`。无规则放宽，RpcOwner 继续被网络依赖规则拒绝。随后计划/PROGRESS 更新改变全树指纹，主 Agent 最终刷新。
- `git diff --check` exit 0；`rg -n 'BrokerRouteLookupParams|RaftPeerClient' --glob '*.java' --glob 'pom.xml' .` exit 1（无匹配）；im-raft src 无 Bolt 引用。历史设计/计划证据不批量替换。仅 `/tmp/peer-simplification-*.log` 属本任务诊断输出，标准 ignored target/Harness 输出保留。

#### 专项 Review：共享 UserId 与 Token 摘要复用（用户批准）

本轮为行为保持维护，不新增设计、规则或 active 任务；A5 active/WIP=1，保留其他 WIP，不提交 Git。

- [x] 删除 `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/domain/connection/model/UserId.java`；该模块 `src/main/java/com/co/kc/imchat/broker/` 与 `src/test/java/com/co/kc/imchat/broker/` 中旧 FQN 及同包引用全部迁为 `common.domain.user.model.UserId`，不搬其他模型、不新增 wrapper，保留 `ConnectionRouteId`。具体迁移文件清单如下。
- [x] 修改 `im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/domain/connection/model/ConnectionRouteValueObjectTest.java`，证明真实公共 UserId、stringValue、用户 null 与组合身份两种缺失输入仍由领域校验拒绝；现有 Registry/revision/tombstone、批次、路由/投递、诊断 Transformer 与 Gossip 测试继续验证。基线命令：`mvn -q -pl im-broker/im-broker-server -am '-Dtest=ConnectionRouteValueObjectTest,GatewayRouteTableTest,ConnectionRouteRevisionTest,InMemoryConnectionRegistryTest,BrokerDiagnosticAppTransformerTest' -Dsurefire.failIfNoSpecifiedTests=false test`；迁移后：`mvn -q -pl im-broker/im-broker-server -am test`。
- [x] 修改 `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/security/token/Sha256OAuthTokenDigester.java`，仅 digest 改为 `HashUtils.sha256Hex(token)`；保留参数校验、MessageDigest.isEqual 与 matches 使用的 HexFormat/非法 hex 分支。修改同模块 `src/test/java/com/co/kc/imchat/management/iam/infrastructure/security/Sha256TokenDigesterTest.java`，类名对齐文件与指定命令，固定摘要 fixture，并覆盖空输入和非法 hex。验证：`mvn -q -pl im-management/im-iam/im-iam-server -am -Dtest=Sha256TokenDigesterTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [x] 修改 `im-broker/im-broker-server/README.md` 说明复用身份与组合路由身份/Long 协议边界；更新本计划、`PROGRESS.md`。验证：`./scripts/verify.sh architecture`、`git diff --check`，以及全仓 Java 精确旧 FQN 搜索。
- [ ] 最终 `./scripts/verify.sh quick`、`clean`、`full` 与独立复审留主 Agent；窄验证不覆盖此前 full 环境阻塞或其他 WIP 的交付缺口。

本轮证据：
- `./scripts/verify.sh readiness` exit 0；日志 `/tmp/userid-digest-readiness.log`。
- Broker 上述聚焦基线 exit 0（`/tmp/userid-baseline.log`）；固定摘要 fixture 在旧实现上 IAM 指定命令 exit 0（`/tmp/digest-baseline.log`，1 项）。这是现有行为基线与迁移后 green，不冒称首次回归 red。
- Broker reactor exit 0，当前源码对应 XML 510 tests/0 failures/errors/skipped，排除历史已删类 XML；日志 `/tmp/userid-digest-broker.log`。
- IAM 指定 reactor 命令 exit 0，`Sha256TokenDigesterTest` 3 tests/0 failures/errors/skipped；日志 `/tmp/userid-digest-iam.log`。fixture 固定为 `opaque-token-value` 的 SHA-256 `0fdde61b3874c8ae97ab0b89a3821f7a81b9423f7686de6c98fcea9b3c16982d`，旧、新实现均通过。
- architecture exit 0，68 tests/0 failures/errors/2 skipped；日志 `/tmp/userid-digest-architecture.log`。`git diff --check` exit 0；全仓 Java `rg -n 'com\\.co\\.kc\\.imchat\\.broker\\.domain\\.connection\\.model\\.UserId' --glob '*.java' .` exit 1（无匹配），同包真实类型引用均有公共 import。
- 公共 UserId 的 Serializable/StringIdentifiable 不改变现有协议：Registry/本地负缓存使用值相等性，快照摘要仍取 Long，诊断/SDK/Gossip 边界仍展开基础字段，未新增直接领域序列化或缓存 key 转换。null 仍为 IllegalStateException，错误信息采用公共类型的“用户ID不能为空”。本轮命令未失败。

迁移文件（均相对于 `im-broker/im-broker-server/src/`，仅类型 import 迁移，除明确列出的断言外不改行为）：
- main/java/com/co/kc/imchat/broker/：`domain/connection/model/{ConnectionRouteId,ConnectionRegistration,GatewayRouteBatch,GatewayRouteTable}.java`；`domain/connection/repository/{ConnectionRegistry,BatchConnectionRegistry}.java`；`infrastructure/domain/connection/{InMemoryConnectionRegistry,InMemoryBatchConnectionRegistry}.java`；`application/diagnostic/BrokerDiagnosticAppService.java`；`application/frame/FrameDispatchAppService.java`；`application/local/LocalRouteAppService.java`；`model/cqrs/query/BrokerRouteLookupParams.java`；`interfaces/handler/connection/{ConnectionCloseHandler,ConnectionRegisterHandler,ConnectionUnregisterHandler}.java`；`interfaces/handler/routing/RouteLookupHandler.java`。
- test/java/com/co/kc/imchat/broker/：`domain/connection/model/{ConnectionRouteValueObjectTest,GatewayRouteBatchTest,GatewayRouteTableTest}.java`；`infrastructure/registry/connection/{ConnectionRouteRevisionTest,InMemoryConnectionRegistryTest,InMemoryBatchConnectionRegistryTest}.java`；`infrastructure/monitoring/BrokerMetricsTest.java`；`application/diagnostic/BrokerDiagnosticAppServiceTest.java`；`application/frame/{FrameDispatchAppServiceTest,FrameDispatchAppServiceLookupTest}.java`；`application/local/{LocalGatewayCleanupAppServiceTest,LocalRouteAppServiceReceiveTest,LocalRouteAppServiceIdempotencyTest,LocalRouteAppServiceCleanupTest}.java`；`interfaces/handler/{BrokerBoltHandlerTest,ConnectionCloseHandlerTest,ConnectionRegisterHandlerTest,FrameProcessHandlerTest}.java`；`interfaces/handler/routing/RouteLookupHandlerTest.java`；`interfaces/listener/RegistryEventListenersTest.java`；`adapter/cluster/{BrokerPeerAdapterTest,BrokerRaftAdapterTest}.java`；`adapter/gossip/BrokerStateStoreTest.java`；`support/RouteLookupStub.java`；`transformer/application/BrokerDiagnosticAppTransformerTest.java`。

#### 通用 Raft membership 复用小维护（用户批准）

- [x] 删除 `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/adapter/cluster/membership/BrokerConsensusMembership.java`；修改 `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/adapter/cluster/BrokerRaftAdapter.java`，`consensusMembership` 直接返回 Runtime 的 `RaftMembership`，四处内部变量同步迁移；不改 MembershipResult、不搬包、不加 wrapper。
- [x] 修改 `im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/support/StatefulClusterAdapterFixture.java` 使用 `RaftMembership` 并显式排序；复用 `im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/adapter/cluster/BrokerRaftAdapterTest.java` 的真实成员迁移用例，补稳定顺序、不可变和分离快照断言。验证：`mvn -q -pl im-broker/im-broker-server -am test`。
- [x] 修改 `im-broker/ARCHITECTURE.md`、`im-broker/im-broker-server/README.md` 和 `PROGRESS.md` 说明技术 membership 复用通用模型、非 Domain；验证 `./scripts/verify.sh architecture`、`git diff --check` 和 `rg -n '\bBrokerConsensusMembership\b' im-broker/im-broker-server/src` 无残留。最终 quick/full 留主 Agent，A5 active/WIP=1，保留其他 WIP、不提交。

本次小维护验证证据：
- `./scripts/verify.sh readiness` exit 0，Java 21/Maven 3.8.4/Node 26.10.0。
- `mvn -q -pl im-broker/im-broker-server -am test` exit 0；Broker 当前源码对应 XML 509 tests/0 failures/errors/skipped，im-raft 41 tests 全通过；日志 `/tmp/membership-reuse-broker.log`。已有真实三 voter + learner 场景保留 promote/demote/remove 的 accepted 与成员变化断言，新增排序、不可修改和晋升前快照不被后续配置改变的断言；本次没有新增行为，未构造源码测试或形式化 red。
- `./scripts/verify.sh architecture` exit 0，68 tests/0 failures/errors/2 skipped；日志 `/tmp/membership-reuse-architecture.log`。`git diff --check` exit 0；全仓 `rg -n '\bBrokerConsensusMembership\b' --glob '*.java' .` exit 1（无匹配），Result 保持不变。
- 本次命令未失败；此前 Builder 的容量测试失败/工具超时和 full 的 Xcode license 阻塞仍按历史证据保留，不由本轮窄验证覆盖。文档更新改变全树指纹，最终 quick/clean/full 和独立 Review 留主 Agent；A5 active/WIP=1、不提交 Git。

#### RaftNodeSettings Builder 增量

历史方案（已被下文用户纠正取代）：用户要求将 11 参数配置对象改为 Builder；前轮保持 record 与 canonical constructor 的校验、防御拷贝及路径归一化，采用已有 Lombok @Builder（非领域对象），不新增默认值。前轮命令与结果保留为历史证据，不代表当前实现。

- [x] `im-plugin/im-raft/src/main/java/com/co/kc/imchat/plugin/raft/model/RaftNodeSettings.java` 添加 Builder；先新增同模块 `src/test/java/com/co/kc/imchat/plugin/raft/model/RaftNodeSettingsTest.java` 证明字段、校验及拷贝，记录 red。迁移 `core/RaftRuntimeTest.java` 与 Broker `adapter/cluster/BrokerRaftAdapter.java` 的全部构造调用。验证：`mvn -q -pl im-plugin/im-raft -am -Dtest=RaftNodeSettingsTest,RaftRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test`、Broker reactor、architecture。
- [x] 更新 `im-plugin/im-raft/README.md`，提供全部 11 字段的 Builder 示例并说明 canonical 校验、List 拷贝和目录归一化。
- [ ] 独立实现 Review 结论 Revise（交付证据缺口，代码 findings none）：只读 Reviewer 未取得迁移前 diff，不能独立证明参数基线等价；最终 Harness 指纹待刷新。主会话 `./scripts/verify.sh quick` exit 0（900 秒时限下完成，日志 `/tmp/settings-final-quick.log`），`clean` exit 0；`full` exit 69，最终 Harness 调用 Git 报未接受 Xcode license（`/tmp/settings-final-full.log`）。不代用户接受系统许可，环境修复后复跑；保留 Revise、A5 active，不提交 Git。

Builder 重试证据（本次 worker）：
- 接手时已有 `@Builder`、SettingsTest、RuntimeTest/BrokerRaftAdapter 全部调用迁移及 README 示例；保留原 WIP，补充真实未设置必填输入测试，未修改 Membership。
- `./scripts/verify.sh readiness` exit 0，日志 `/tmp/settings-readiness.log`。
- Red：先补 SettingsTest，然后暂时精确移除 Lombok import/annotation；运行 `mvn -q -pl im-plugin/im-raft -am -Dtest=RaftNodeSettingsTest,RaftRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test` exit 1，testCompile 缺少 `RaftNodeSettingsBuilder`（非环境失败），日志 `/tmp/settings-builder-red.log`。这是重试反证，不冒称接手前已记录首次 red；随后恢复已有注解，canonical constructor 净变化为零。
- Green：同一窄命令 exit 0，SettingsTest 24 项、RuntimeTest 14 项，合计 38 tests/0 failures/errors/skipped；日志 `/tmp/settings-builder-green.log`。
- `mvn -q -pl im-broker/im-broker-server -am test` exit 0，Broker 当前源码对应报告 509 tests/0 failures/errors/skipped（排除已删除/移动类遗留 XML）；日志 `/tmp/settings-builder-broker.log`。
- `./scripts/verify.sh architecture` exit 0，68 tests/0 failures/errors/2 skipped；日志 `/tmp/settings-builder-architecture.log`。
- `rg 'new\s+RaftNodeSettings' --glob '*.java' .` 无匹配，`git diff --check` exit 0。最终 quick/full、独立 Review 和其他交付门禁留主 Agent；A5 保持 active/WIP=1，未提交 Git。文档更新后 Harness 全树指纹须在最终稳定工作树刷新。

#### RaftNodeSettings 手写 Builder 纠正（2026-10-13）

历史方案（一次性契约已被下文“Builder 模式对齐纠正”取代，证据保留）：用户明确纠正：即使本对象是通用技术启动配置，也按 `CODING_GUIDE.md` 手写 Builder 目标对象模式实现；此决定取代上述 Lombok/record 方案，不把本对象移入领域。

- 当前决定：`RaftNodeSettings` 使用 final class、私有无参构造器与 `RaftNodeSettings.Builder`。Builder 只持有目标对象，各填充方法直接写其字段，不重复 11 个属性、不保留长参数转发构造器。保留全部 component 风格只读 accessor，无公开 setter，无新增默认值或领域依赖。
- `build()` 统一执行原校验、防御拷贝和路径归一化，保留异常类型、消息及校验顺序；成功后消费 target，任意填充或再次 build 均明确抛 `IllegalStateException("Raft node settings builder already consumed")`；失败仍允许修正输入。Builder 不支持跨线程共享。
- 搜索全部 Java 调用方确认仅 Runtime、Broker Adapter 与测试使用此类型，未发现 equals/hashCode 或对象集合键语义 caller；不扩展 equals/hashCode。Runtime/Broker 已有按字段调用无需改动；Membership 和无关 POM 不在本轮范围。

- [x] 修改 `im-plugin/im-raft/src/test/java/com/co/kc/imchat/plugin/raft/model/RaftNodeSettingsTest.java`：先使用旧生成类型增加成功后消费、不同 Builder 独占对象、失败修正测试并取得行为 red；实现后迁移类型为 `RaftNodeSettings.Builder`，保留未设置必填输入与原校验测试。验证：`mvn -q -pl im-plugin/im-raft -am -Dtest=RaftNodeSettingsTest,RaftRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [x] 完全改写 `im-plugin/im-raft/src/main/java/com/co/kc/imchat/plugin/raft/model/RaftNodeSettings.java`，实现上述手写 Builder；修改 `im-plugin/im-raft/README.md` 替换 record/canonical 入口说明并明确一次性与失败修正契约。
- [x] Broker reactor 和架构验证：`mvn -q -pl im-broker/im-broker-server -am test`、消除旧编译产物后 `mvn -q -pl im-broker/im-broker-server -am clean test`、`./scripts/verify.sh architecture`；结果如下。
- [ ] 当前纠正的独立 Review 与最终 `./scripts/verify.sh quick`、`clean`、`full` 留主 Agent；既有 Revise 和 full 的 Xcode license 环境阻塞历史不覆盖为通过，A5 active/WIP=1，不提交 Git。

本轮真实验证证据：
- `./scripts/verify.sh readiness` exit 0，Java 21/Maven 3.8.4/Node 26.10.0；控制台输出，未重写前轮日志。
- Red：上述 Settings+Runtime 窄命令 exit 1，41 tests/2 failures/0 errors/skipped。新增 `successfulBuildConsumesBuilderWithoutChangingBuiltSettings` 和 `failedBuildAllowsCorrectionUntilSuccessfulBuild` 均在成功后填充未抛异常处失败（`Expecting code to raise a throwable`）；不是缺类型或环境失败。其余原用例及独占对象测试通过。日志 `/tmp/settings-handwritten-red.log`。
- Green：同一窄命令 exit 0，SettingsTest 27、RuntimeTest 14，共 41 tests/0 failures/errors/skipped。日志 `/tmp/settings-handwritten-green.log`。
- Broker 首轮 `mvn -q -pl im-broker/im-broker-server -am test` exit 1，512 tests/1 failure/8 errors：Adapter/Application 错误为旧增量 class 仍引用 `RaftNodeSettings$RaftNodeSettingsBuilder` 导致 NoSuchMethodError；另 RaftLibrarySpikeTest membership 场景出现 EPERM。未改生产调用方、测试或 POM 规避；清理 reactor 编译产物后完整复跑。日志 `/tmp/settings-handwritten-broker.log`。
- Broker `mvn -q -pl im-broker/im-broker-server -am clean test` exit 0，Broker 512 tests/0 failures/errors/skipped；im-raft 全套 43 tests/0 failures/errors/skipped（含 PeerClient 2）。清理后无旧类遗留 XML。日志 `/tmp/settings-handwritten-broker-clean.log`。
- `./scripts/verify.sh architecture` exit 0，68 tests/0 failures/errors/2 skipped。日志 `/tmp/settings-handwritten-architecture.log`。
- `rg 'RaftNodeSettings' --glob '*.java' .` 调用搜索保存在 `/tmp/settings-handwritten-callers.log`；`rg 'new\\s+RaftNodeSettings|RaftNodeSettingsBuilder' --glob '*.java' .` 仅命中 Builder 的私有目标对象创建，无旧生成类型、外部长参构造；`git diff --check` exit 0。
- 文档随后更新会改变 Harness 全树指纹，最终稳定工作树证据须由主 Agent 刷新；不把历史 quick/clean/full 当本次纠正的交付验收。

#### RaftNodeSettings Builder 模式对齐纠正（2026-10-13，本轮）

用户进一步明确要求严格对照 `BrokerClusterOperationProposal.Builder`、`RoutingLease.Builder`：Builder 只持有 `private final RaftNodeSettings settings = new RaftNodeSettings()`，setter 直接写 `settings.field` 并返回 `this`，`build()` 执行 `settings.validate()` 后返回 `settings`。本轮以该决定取代上一节“成功后消费/一次性”的契约设计；前述一次性实现与测试结果保留为历史证据，不代表当前实现。

- [x] 精确收敛 `RaftNodeSettings.Builder`：删除 `target()`、置 null、消费检查、一次性注释及多余私有 Builder 构造器；保留 accessor、原校验顺序、List.copyOf 防御拷贝/不可变返回和路径绝对化 normalize，不新增默认值或行为。
- [x] 删除仅证明一次性契约的成功后消费、不同 Builder 独占对象及失败后成功后消费断言；保留字段映射、输入校验、集合拷贝/不可变、失败后可修正等有意义测试；不增加“已构建对象可被 Builder 修改”的复用测试。
- [x] 更新 `im-plugin/im-raft/README.md`，移除一次性 Builder 说明，改为当前目标对象模式和校验失败可修正说明。
- [x] `./scripts/verify.sh readiness` exit 0。`mvn -q -pl im-plugin/im-raft -am -Dtest=RaftNodeSettingsTest,RaftRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test` exit 0，RaftNodeSettingsTest 25、RaftRuntimeTest 14，共 39 项/0 failures/errors/skipped；日志 `/tmp/settings-pattern-alignment-tests.log`。
- [x] `mvn -q -pl im-broker/im-broker-server -am -DskipTests compile` exit 0；日志 `/tmp/settings-pattern-alignment-broker-compile.log`。`mvn -q -pl im-broker/im-broker-server -am test` 首轮受工具 60 秒超时（`/tmp/settings-pattern-alignment-broker-tests.log`），后续复跑 exit 1，在既有 `im-cache` 容量测试失败（预期最多 2、实际 4；`/tmp/settings-pattern-alignment-broker-tests-retry.log`），未改动该测试。原命令最终复跑 exit 0，Broker 512 项、im-raft 全套 41 项，均 0 failures/errors/skipped；日志 `/tmp/settings-pattern-alignment-broker-tests-final.log`。
- [x] `git diff --check` exit 0。未执行最终 quick/clean/full，保留原 WIP、不提交、不做其他重构。

#### 独立实现 Review 两项修复证据

- [x] 修改 `im-test/im-architecture-test/src/test/java/com/co/kc/imchat/architecture/LayerBoundaryTest.java`；新增 `fixture/broker/support/raft/codec/BrokerClientOwner.java` 和 `fixture/broker/support/raft/model/LegalSdkState.java`。按具体网络客户端职责窄禁止 `com.co.kc.imchat.broker.sdk.BrokerClient`，不禁止整个 SDK；合法 `BrokerRoutingState` 仍通过。验证：`./scripts/verify.sh architecture`。
- [x] 修改 `im-plugin/im-raft/src/test/java/com/co/kc/imchat/plugin/raft/core/RaftRuntimeTest.java`，增加 decode/apply 两个当前 committed batch 失败场景。仅 mock JRaft 进程/网络边界，由正式 `NodeOptions.getFsm()` 获取回调，捕获两次健康 submit 的真实 Task/SubmitClosure；可控 Iterator 在 rollback 标记后仍暴露第二条，证明生产 return 而非 mock 自动停批。断言第二条不 decode、不 apply、closure 不完成（因而不 APPLIED），第一条 closure UNAVAILABLE、rollback(1, failure)、部分状态不声称回滚及两类读证明拒绝。未增加生产 API、未使用私有反射。
- Red：新增 BrokerClient 负 fixture 后运行 `./scripts/verify.sh architecture` exit 1，68 tests/1 failure/0 errors/2 skipped；LayerBoundaryTest 23 tests/1 failure。唯一失败是规则漏检 BrokerClientOwner，非环境失败；补窄客户端规则后复跑通过。日志 `/tmp/review-architecture-red.log`。
- Mutation：临时仅删除 `RaftRuntime.StateAdapter.onApply` catch 分支 return；运行 `mvn -q -pl im-plugin/im-raft -am '-Dtest=RaftRuntimeTest#decodeFailureStopsCurrentCommittedBatch+applyFailureStopsCurrentCommittedBatch' -Dsurefire.failIfNoSpecifiedTests=false test` exit 1，2 tests/2 failures/0 errors。两场景实际 decode `[9, 5]`，期望 `[9]`，证明第二条被错误继续；已精确恢复 return，生产文件本轮净变化为零。日志 `/tmp/review-raft-return-mutation.log`。
- Green：`mvn -q -pl im-plugin/im-raft -am -Dtest=RaftRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test` exit 0，14 tests/0 failures/0 errors/0 skipped。日志 `/tmp/review-raft-final.log`。
- Green：`./scripts/verify.sh architecture` exit 0，68 tests/0 failures/0 errors/2 skipped；LayerBoundaryTest 23 tests 全通过。日志 `/tmp/review-architecture-final.log`。`./scripts/verify.sh readiness` exit 0；日志 `/tmp/review-readiness.log`。
- A5 保持 active/WIP=1；本轮仅修复 Review 两项，未提交 Git，未替原 WIP 作最终 quick/clean/full/behavior/e2e 或独立复审验收。

#### 类型化 Raft 增量（用户批准、独立设计 Review Accept）

事实源：[Raft 类型化命令接入](../../design-docs/2026-10-12-typed-raft-command-design.md)。本增量取代本计划原“日志/快照版本不变”约束：均重置 v1，字段及业务语义不变；不兼容旧数据目录，不自动删除数据。A5 保持唯一 active 任务。

- [x] 修改 `im-plugin/im-raft/src/test/java/com/co/kc/imchat/plugin/raft/core/RaftRuntimeTest.java`：先记录失败，覆盖规范化解码、快照尾日志、编码失败、错误节点读证明与字节所有权。验证：`mvn -q -pl im-plugin/im-raft -am -Dtest=RaftRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [x] 新建 `im-plugin/im-raft/src/main/java/com/co/kc/imchat/plugin/raft/spi/RaftCommandCodec.java`；修改相邻 `RaftStateHandler.java` 和 `core/RaftRuntime.java`，类型化提交、全部副本解码、错误检查入口与异步回调；同命令验证。
- [x] 迁移 Broker `adapter/cluster/raft` 三个生产类型到 `support/raft/codec`、`model`、`statemachine`；修改 `adapter/cluster/BrokerRaftAdapter.java` 与引用类型的生产/测试文件，收敛 Codec 映射并重置版本。直接被测测试同步职责包，应用测试不移动。验证：`mvn -q -pl im-broker/im-broker-server -am test`。
- [x] 修改 `im-test/im-architecture-test/src/test/java/com/co/kc/imchat/architecture/LayerBoundaryTest.java` 及正反 fixture，精确允许三个职责包并禁止运行实例、网络/配置/应用/入口依赖。同步 `docs/references/CODING_GUIDE.md`、`MODULE_STRUCTURE_GUIDE.md`、`HARNESS_GUIDE.md`、Broker Architecture/README 和 im-raft README。验证：`./scripts/verify.sh architecture`。
- [x] 独立实现 Review：首轮 Revise（BrokerClient 依赖漏禁、当前批次停批证据不足），修订后独立复审 Accept、findings none；设计 Accept 不代替交付门禁。
- [x] 主会话复跑 `mvn -q -pl im-plugin/im-raft -am -Dtest=RaftRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test` exit 0，14 项；`./scripts/verify.sh clean` exit 0；`behavior` 串行复跑 exit 0，34 场景；`e2e` exit 0，1 场景。首轮 behavior 与 e2e 并行安装 Maven 元数据发生空文件读取错误，停止并行后原命令通过，非生产代码失败。
- [ ] `./scripts/verify.sh quick` 两次及 `full` 一次均在主工具 300 秒时限超时，无最终 exit/pass；Harness 自检故障注入日志中的 MySQL unhealthy、场景矩阵错误不能视为真实环境故障。日志 `/tmp/typed-final-quick.log`、`/tmp/typed-final-quick-retry.log`、`/tmp/typed-final-full.log`。须在足够执行时限下重跑，A5 不归档、不提交 Git。

#### 类型化增量实施证据（2026-10-13，本次 worker）

- `./scripts/verify.sh readiness`：exit 0，Java 21/Maven 3.8.4/Node 26.10.0。
- Red：先迁移并扩展 `RaftRuntimeTest`，执行设计中的窄 Raft 命令，exit 1；缺少 RaftCommandCodec、Runtime/Handler 泛型数量不匹配，证明旧 SPI 不满足类型化契约。日志 `/tmp/typed-raft-red.log`。
- Broker 首次聚焦命令 exit 1：插件新 SPI 已进入生产，Broker 旧泛型尚未迁移；首个错误是 StateMachine/Adapter 泛型数量不匹配，后续 Lombok 缺方法为编译级联，不归因环境。迁移后同组聚焦测试通过，日志 `/tmp/typed-broker-red.log`、`/tmp/typed-broker-focused.log`。
- Broker 版本回退反证：临时仅恢复日志 v5/快照 v6 常量，运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterCommandCodecTest,BrokerClusterSnapshotIsolationTest -Dsurefire.failIfNoSpecifiedTests=false test`，exit 1，6 tests/3 failures；合法 v1 未知 type 被错误当版本拒绝、版本断言失败。已恢复 v1，日志 `/tmp/typed-broker-version-red.log`。
- Runtime 反证：临时移除 submit clone 及两个读取回调错误检查，运行 `mvn -q -pl im-plugin/im-raft -am '-Dtest=RaftRuntimeTest#runtimeOwnsEncodedBytesAndDecodeReceivesIndependentPayload+pendingLeadershipProofRejectsNodeErrorBeforeSuccessfulCallback+pendingQuorumReadRejectsNodeErrorBeforeSuccessfulCallback' -Dsurefire.failIfNoSpecifiedTests=false test`，exit 1，3 tests/3 failures，所有权场景期望 8 实际 -98，两个在途错误证明错误成功；已恢复生产代码。第一次所有权反证仅 2 failures，暴露 latch 位点太晚（已复制到 decode），已改为第一条阻塞、第二条提交后改 buffer 再重跑。日志 `/tmp/typed-runtime-mutation-red.log`。
- Green：`mvn -q -pl im-plugin/im-raft -am -Dtest=RaftRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test` exit 0，RaftRuntimeTest 12 tests/0 failures/errors。规范化 decode 故意返回 command+1，Leader/Follower/learner 均断言规范化状态；A→snapshot→B→restart 恢复 A+B 再 C；编码异常无追加后合法提交、decode/apply 错误、在途回调错误及独占并发数组均覆盖。日志 `/tmp/typed-raft-final.log`。
- Green：`mvn -q -pl im-broker/im-broker-server -am test` 最终 exit 0；Broker 当前源码对应 XML 509 tests/0 failures/errors，排除已移动/已删除类遗留 XML，不把原 534 总和当本轮结果。Codec 3、Snapshot 4、Lease 原子性 2；日志 `/tmp/typed-broker-final.log`。
- architecture 首轮 exit 1：原 support 白名单预期拒绝四个新包类型（含 envelope）；未放宽其他包，新增三个精确职责包及依赖规则/正反 fixture 后通过。最终 `./scripts/verify.sh architecture` exit 0，68 tests/0 failures/errors/2 skipped，LayerBoundaryTest 23 项通过。日志 `/tmp/typed-architecture-red.log`、`/tmp/typed-architecture-final.log`。
- `git diff --check` exit 0。无 Git commit、无用户数据清理；最终 Review/quick/clean/full/behavior/e2e 按委派范围留给主 Agent。后续文档更新会改变 Harness 全树指纹，主 Agent 应在稳定工作树刷新最终证据。

状态枚举增量范围：`consensus/BrokerClusterConsensusResult.java` 内嵌 `Status`，删除 `consensus/BrokerClusterConsensusStatus.java`；迁移引用该枚举的 `src/main` 结果/Transformer/状态机和 `src/test` 测试，更新 Server README 与设计决策。

- [x] 已将枚举内嵌并简化命名为 `Status`，保留全部枚举值及语义；生产和测试引用同步迁移。
- [x] `mvn -q -pl im-broker/im-broker-server -am -DskipTests test-compile` 通过；`mvn -q -pl im-broker/im-broker-server -am test` 复跑通过，Broker 514 tests、0 failures/errors。此前一轮上游 `im-cache` 的 `JetCacheLocalValuesTest` 失败，未修改该模块，原命令复跑通过。
- [ ] `./scripts/verify.sh architecture`、`quick`、`clean`、`full`：architecture 首轮超时，复跑检测到验证期间工作树变化，未形成有效证据；后续门禁未执行。停止并发修改后重新运行。

2026-10-12 LocalBroker 增量范围：删除 `adapter/cluster/LocalBroker.java` 与独立 Bean；修改 `BrokerRaftAdapter`、`BrokerQuorumAppService`、`BrokerMembershipAppService`、四个 scheduler、`BrokerRequestConfiguration` 及对应测试/fixture；更新 Broker Architecture/README 和设计决策。

- [x] 将本机查询迁入 Adapter，迁移生产调用方及测试夹具；删除薄门面及重复身份入口。
- [x] `mvn -q -pl im-broker/im-broker-server -am test` 通过，Broker 报告 514 tests、0 failures/errors；首轮沙箱拒绝端口，沙箱外复跑通过。`./scripts/verify.sh architecture` 通过，首轮本地 Maven 仓库写权限失败，沙箱外复跑通过。ReadIndex 等待/失败与真实节点身份、Leader、进度断言保留。
- [x] 独立只读 Review：本次 LocalBroker 增量 findings none；核对 runtime、ReadIndex、身份、领导权证明、close 和全部调用方，无旧入口残留。
- [ ] `./scripts/verify.sh quick`、`clean`、`full` 和 `e2e` 验证最终工作树。


- [x] 状态机直接实现类型化 RaftStateHandler；删除匿名回调和 SnapshotCodec，迁移快照调用方与隔离测试；业务字段/Lease 锁不变，版本由批准的类型化增量有意重置 v1。聚焦 Snapshot/Adapter/Lease、Broker reactor、architecture 已运行；quick/clean/full 留给最终门禁。

2026-10-12 增量范围：按用户确认删除 `consensus/BrokerClusterConsensus.java`；迁移 `src/main` 全部接口调用方和 `src/test` 夹具；保留 Result/Status 和包位置。

- [x] 接口及生产引用已删除，测试夹具已迁移；`rg -n "\bBrokerClusterConsensus\b" im-broker/im-broker-server/src/main im-broker/im-broker-server/src/test` 无匹配。
- [x] `mvn -q -pl im-broker/im-broker-server -am -DskipTests test-compile` 与 `mvn -q -pl im-broker/im-broker-server -am test` 通过；首轮 im-metrics 本地端口受沙箱拒绝，沙箱外同命令复跑通过。
- [ ] `mvn -q -pl im-broker/im-broker-server -am test`、`./scripts/verify.sh architecture`、`quick`、`clean`、`full` 验证增量迁移。

**Files:**
- Delete or reduce: `BrokerClusterAdapter.java`
- Modify: `im-broker/ARCHITECTURE.md`, `im-broker/im-broker-server/README.md`
- Test: architecture, affected, behavior, routing-ha, quick, clean, full

- [x] 旧 `BrokerClusterAdapter`、peer/Gateway 委托和过渡 fixture 已删除；生产调用方分别使用三个专用 Adapter。
- [x] 已更新 Broker Architecture 与 Server README 的主要职责和调用路径。
- [x] `mvn -q -pl im-broker/im-broker-server -am test` 通过。
- [x] 独立 Review 的重复 getter、隐式 revision 读取与 README 职责描述已修正；新增命令 revision 断言通过 `mvn -q -pl im-broker/im-broker-server -Dtest=BrokerDrainAppServiceTest test`。
- [ ] `architecture`、`behavior`、`routing-ha` 已通过；`quick`、`clean`、`full` 待最终运行。
- [x] `./scripts/verify.sh e2e` 通过，执行 1 个实时 E2E 测试。

## 不变约束

- Raft command/snapshot 有意重置 v1，拒绝旧 v5 日志/v6 快照；业务字段不变。不修改 Bolt service/operation、Gossip 语义和路由选择算法，不自动删除数据目录。
- 不把 `RaftRuntime`、JRaft、网络客户端、执行器或 Spring 配置引入领域包。
- 不新增只做转发的 `BrokerClusterDomainService` 或万能 Facade。
- 任何拆分失败先恢复原调用路径，再定位行为差异；不通过放宽测试或架构规则来推进。


#### Gossip 诊断辅助归位与 Adapter 改名（实施证据）

事实源：[用户批准决定](../../design-docs/2026-10-11-broker-cluster-adapter-boundaries-design.md#gossip-诊断辅助归位与-adapter-改名用户批准独立设计-review-accept)。A5 active/WIP=1；独立设计 reviewer `fdaeb0fa` Accept、findings none 后实施。仅搬包/改名及窄架构规则，不改行为、不处理其他 finding、不提交。接手完整 WIP 快照 `/tmp/gossip-move-before`，限定增量 `/tmp/gossip-move-delta.diff`。

- [x] 持久化决定并同步目标职责文档：`docs/design-docs/2026-10-11-broker-cluster-adapter-boundaries-design.md`、`docs/references/{CODING_GUIDE,MODULE_STRUCTURE_GUIDE,HARNESS_GUIDE,HARNESS_RULE_MATRIX}.md`、`im-broker/{ARCHITECTURE,README}.md`、`im-broker/im-broker-server/README.md`、`PROGRESS.md`、本计划。当前规范/导航用 BrokerGossipAdapter 与 support/gossip；历史证据、旧执行清单及 GIT_GUIDE 的历史提交反例不批量改写。文档契约检查 `git diff --check`，独立设计 Review 已 Accept。
- [x] 取得本增量独立设计 Review：`fdaeb0fa` Accept、findings none。
- [x] 基线：`mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerStateStoreTest,GossipSyncTrackerTest,BrokerGossipSchedulerTest,BrokerDiagnosticAppServiceTest,LocalGatewayCleanupAppServiceTest,RegistryEventListenersTest,BrokerBoltHandlerTest,BrokerMetricsTest' -Dsurefire.failIfNoSpecifiedTests=false test`；保留原容量、计数、查询上限、摘要时间、合并、tombstone/远端删除隔离与异常断言，不新增生产兼容入口。

精确迁移范围（以下路径分别以 Server `src/main/java/com/co/kc/imchat/broker/`、`src/test/java/com/co/kc/imchat/broker/` 为根）：

- main：`adapter/diagnostic/GossipSyncTracker.java`、`adapter/gossip/BrokerStateStore.java`、`application/diagnostic/BrokerDiagnosticAppService.java`、`application/gossip/RegistryGossipAppService.java`、`application/local/LocalGatewayCleanupAppService.java`、`infrastructure/config/ClientConfiguration.java`、`infrastructure/config/DiagnosticConfiguration.java`、`infrastructure/config/RegistryConfiguration.java`、`infrastructure/config/RegistryGossipConfiguration.java`、`infrastructure/monitoring/BrokerMetrics.java`、`interfaces/scheduler/BrokerGossipScheduler.java`。
- test：`adapter/diagnostic/GossipSyncTrackerTest.java`、`adapter/gossip/BrokerStateStoreTest.java`、`application/diagnostic/BrokerDiagnosticAppServiceTest.java`、`application/local/LocalGatewayCleanupAppServiceTest.java`、`infrastructure/gossip/BrokerGossipSchedulerTest.java`、`infrastructure/monitoring/BrokerMetricsTest.java`、`interfaces/handler/BrokerBoltHandlerTest.java`、`interfaces/listener/RegistryEventListenersTest.java`。

- [x] RED（先测试/fixture）：直接测试 `adapter/diagnostic/GossipSyncTrackerTest.java` 搬至 `support/gossip/GossipSyncTrackerTest.java`，`adapter/gossip/BrokerStateStoreTest.java` 改名 `adapter/gossip/BrokerGossipAdapterTest.java`，迁移以上 test 引用，保留原断言。先运行下面 GREEN 聚焦命令，预期新目标类/包缺失导致编译失败，不以错误断言替代迁移 red。
- [x] 架构 RED：只改 `im-test/im-architecture-test/src/test/java/com/co/kc/imchat/architecture/LayerBoundaryTest.java`，复用参数化 root 规则，新增 `brokerGossipSupportDependencies`、`domainCannotDependOnGossipSupport`、`brokerGossipSupportCannotOwnRuntimeOrExternalOrchestration`、`gossipSupportRulesAcceptOnlyApprovedPackagesAndRejectOwnershipLeaks`；`brokerSupportPackages` 仅增加精确 `.support.gossip`，不增加任意后代或类名豁免，原 Raft/context/domain 规则不变。先写 fixture 断言，未增加许可/依赖规则前实际失败；随后增加精确许可与独立 Gossip 依赖规则。窄 architecture red 命令见下。
- [x] 架构 fixture 精确新增路径（以 `im-test/im-architecture-test/src/test/java/fixture/broker/` 为根）：`support/gossip/LegalDiagnosticHistory.java`（合法 model/common/JDK 有界内存记录），`support/gossip/nested/NestedTracker.java`（后代包拒绝），`support/gossip/ApplicationOwner.java`、`InterfaceOwner.java`、`InfrastructureOwner.java`、`ConfigurationOwner.java`、`LifecycleOwner.java`、`AdapterOwner.java`、`RpcOwner.java`、`RuntimeOwner.java`、`BrokerClientOwner.java`、`SpringOwner.java`、`NetworkOwner.java`（后十一个同在 support/gossip；分别依赖实际 AppService、入口、infrastructure、typed 配置、生命周期、BrokerGossipAdapter、BoltInvoker、RaftRuntime，全部拒绝；额外三例分别依赖 BrokerClient、Spring ApplicationContext、JDK Socket），`domain/GossipSupportLeak.java`（领域反向引用 LegalDiagnosticHistory，拒绝）。fixture 类型只承载依赖，不复制业务实现；断言逐个命中负例并明确合法例无违规，不仅检查总违规数。禁止依赖覆盖 application/interfaces/infrastructure/config/lifecycle/adapter、Spring 与已知网络客户端/JDK 网络及 Runtime；具体 BrokerClient 也禁止，沿用窄具体客户端判断，不禁全部 SDK 数据类型。运行 `./scripts/verify.sh architecture` 并保留 red/green。
- [x] GREEN：仅在以上 main 清单搬移 `adapter/diagnostic/GossipSyncTracker.java` → `support/gossip/GossipSyncTracker.java`，改名 `adapter/gossip/BrokerStateStore.java` → `adapter/gossip/BrokerGossipAdapter.java`，迁移所有 import/类型/构造器/Bean 名引用；方法、成员、容量、采样、锁、TTL、顺序和异常行为不变，无 wrapper/compat。直接测试按新包/类归位，其他测试不搬包。运行 `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerGossipAdapterTest,GossipSyncTrackerTest,BrokerGossipSchedulerTest,BrokerDiagnosticAppServiceTest,LocalGatewayCleanupAppServiceTest,RegistryEventListenersTest,BrokerBoltHandlerTest,BrokerMetricsTest' -Dsurefire.failIfNoSpecifiedTests=false test`，再 `mvn -q -pl im-broker/im-broker-server -am test` 与 `./scripts/verify.sh architecture`。
- [x] 搜索收口：`rg -n 'BrokerStateStore|adapter[./]diagnostic' im-broker/im-broker-server/src` 预期无旧类/旧包引用；`rg -n 'BrokerStateStore|adapter[./]diagnostic' im-broker/ARCHITECTURE.md im-broker/README.md im-broker/im-broker-server/README.md docs/references/CODING_GUIDE.md docs/references/MODULE_STRUCTURE_GUIDE.md docs/references/HARNESS_GUIDE.md docs/references/HARNESS_RULE_MATRIX.md` 预期无旧当前引用。根 README/ARCHITECTURE 当前无相关类型引用，若实施时新增引用则按新名称维护；历史设计/执行证据不重写。`git diff --check`；独立实现 Review 待父会话核对限定 diff 与非权威可选语义，本 checkbox 仅代表搜索及 diff 检查完成。
- [ ] 顺序执行 `./scripts/verify.sh behavior`、`quick`、`clean`、`full`，记录实际结果、阻塞与 fresh fingerprint；若实际变更超出纯搬包/改名，重新评估 e2e/startup，不能用窄测试替代 full。本增量本身不改变实时 wiring 或启动配置。本轮文档验证/最终门禁由主会话统一执行，不并发刷新 Harness。

#### 本轮实际命令与结果

- `./scripts/verify.sh readiness` exit 0。已读 `im-test/AGENTS.md`；fixture 路径及其祖先无额外 AGENTS.md，沿用根与 im-test 规则。
- 上述旧名称基线聚焦命令 exit 0，30 tests；日志 `/tmp/gossip-baseline.log`。
- 架构先行 red：`mvn -q -pl im-test/im-architecture-test -am '-Dtest=LayerBoundaryTest#gossipSupportRulesAcceptOnlyApprovedPackagesAndRejectOwnershipLeaks' -Dsurefire.failIfNoSpecifiedTests=false test` exit 1，1 test/1 failure，旧精确白名单拒绝合法 `LegalDiagnosticHistory`；不是编译或环境失败。日志 `/tmp/gossip-architecture-red.log`。
- 测试迁移 red：上述新名称 GREEN 聚焦命令 exit 1，目标 BrokerGossipAdapter/support.gossip.GossipSyncTracker 尚不存在，测试编译失败；日志 `/tmp/gossip-move-red.log`。
- 生产仅应用包/类/构造器/字段及 Bean 名等价迁移，新名称聚焦命令 exit 0，30 tests；日志 `/tmp/gossip-focused-green.log`。
- 顺序 `mvn -q -pl im-broker/im-broker-server -am test` exit 0；按仍有当前源码的报告统计 Broker 523 tests/0 failures/errors。日志 `/tmp/gossip-broker-reactor.log`。
- 顺序 `./scripts/verify.sh architecture` exit 0，70 tests/0 failures/errors/2 skipped；LayerBoundaryTest 25 项，正例无违规、11 个依赖负例、后代包与领域反向依赖均逐名断言。日志 `/tmp/gossip-architecture-green.log`。
- 旧名称/包扫描无匹配；`git diff --check` exit 0。当前 Architecture/README/规则文档已是目标事实，无需重写历史。与接手快照比对全部生产/原测试文件，经相同机械替换后逐字一致，不改断言或运行逻辑。

父会话独立实现 Review 首轮 Revise（仅 Harness Guide/矩阵仍写待实施），两处更新为已实施方法与 fixture 后复审 Accept、findings none；`./scripts/verify.sh behavior` exit 0，34 场景（`/tmp/gossip-final-behavior.log`）。

恢复：本增量 Java/架构 fixture、窄验证与独立复审完成，下一步父会话 quick/clean/full 及最终指纹刷新；不并发构建。回滚仅撤 `/tmp/gossip-move-delta.diff` 限定增量，不恢复整个混合工作树文件。有界性与非权威语义的通用静态自动化仍 Review-only，已有 Message/full/failDrain 与其他 WIP 缺口不关闭。



#### Gossip 应用服务命名收敛（实施证据）

用户已明确批准改名、行为不变；决定见原 Adapter 设计末节。A5 active/WIP=1，保留已有 WIP、历史证据与交付缺口，不提交。父会话已完成 status/readiness；快照 `/tmp/gossip-app-rename-before`。

- [x] 精确八文件：`im-broker/{README,ARCHITECTURE}.md`；Server main `application/gossip/RegistryGossipAppService.java` → `BrokerGossipAppService.java`、`infrastructure/config/RegistryGossipConfiguration.java`、`interfaces/listener/broker/BrokerRegistryEventListener.java`、`interfaces/listener/gateway/GatewayRegistryEventListener.java`、`interfaces/listener/connection/ConnectionRegistryEventListener.java`；Server test `interfaces/listener/RegistryEventListenersTest.java`。Java 根均为 `src/{main,test}/java/com/co/kc/imchat/broker/`。只迁类型/构造器、Bean 方法 `brokerGossipAppService` 和当前引用，类注释明确观察范围；配置类名、其他字段及断言不变。同步原设计、本计划和 PROGRESS。
- [x] 基线及 green：`mvn -q -pl im-broker/im-broker-server -am -Dtest=RegistryEventListenersTest -Dsurefire.failIfNoSpecifiedTests=false test` 均 exit 0，2 tests/0 failures/errors；日志 `/tmp/gossip-app-rename-baseline.log`、`/tmp/gossip-app-rename-green.log`。全仓 Java 未发现直接调用该配置方法或配置类的现有测试，无额外配置 caller 测试可运行。
- [x] 顺序 `mvn -q -pl im-broker/im-broker-server -am test` exit 0，当前源码对应 Broker 523 tests/0 failures/errors；`./scripts/verify.sh architecture` exit 0，70 tests/0 failures/errors/2 skipped。日志 `/tmp/gossip-app-rename-reactor.log`、`/tmp/gossip-app-rename-architecture.log`；无并行 Maven，各命令 timeout 900。
- [x] 八文件快照仅应用批准标识符替换及单处类注释后逐字一致，证据 `/tmp/gossip-app-rename-equality.log`；旧文件已不存在，im-broker 源码/当前文档无旧名称。`git diff --check` exit 0。限定增量 `/tmp/gossip-app-rename-delta.diff` 包含八文件和三个事实/进度文档相对接手快照的变化。专门 reviewer 独立实现复审 Accept、findings none，限定本次改名；最终 quick/clean/full 日志 `/tmp/gossip-app-final-{quick,clean,full}.log`，以 Harness 实际结果为准，运行期间不回填 tracked 状态。
- [ ] 父会话独立最终 Review、quick/clean/full 和最终 Harness 指纹刷新；窄验证不代替正式门禁，不关闭既有交付缺口。纯改名无行为 red，不冒称行为修复。

## 恢复状态

当前恢复入口为文首「当前关闭账本（2026-10-20）」。本节以下按时间保留旧恢复记录，除明确仍未关闭的finding和门禁外，结构/API/范围以最新批准决定为准，不把历史“当前”重复当成新的并行任务。当前状态：后续设计基线已审（不实施）、Broker/Harness文档完成、failDrain旧finding闭环；正式quality为Revise，仅因routing-ha/full网络阻塞与最终证据缺口，A5 blocked。下一步网络恢复后以既有隔离repo重跑routing-ha及最终门禁，取得独立quality更新后再归档和关闭审计。私有制品发布/双构建及连接层生产迁移明确后轨，不隐去其未交付状态。

- 当前自动故障增量：规范短复审八项全部closed后恢复功能接入，typed配置/schedulers及实际组合自动流程窄验证已执行；Broker reactor有未触cache随机失败和已修startup精确名单，尚待刷新。Maven交HA，父会话安排fullfeature独立Review；当前真实HA/广门禁缺口保留，精确diff/日志见A5最新检查点。A5 active/WIP=1，不提交。

- 最新 Gossip 增量：设计 Review `fdaeb0fa` Accept，搬包/改名和窄架构正反 fixture 完成；聚焦 30、Broker 523、architecture 70（2 skipped）均通过；父会话接手独立实现 Review 与最终门禁。A5 active/WIP=1，不关闭历史缺口。

- 当前新增范围：Broker 全部自有时间契约，用户已批准；独立设计 Review 首轮 Revise，文档修订后待独立复审，P1–P4 冻结。A5 active/WIP=1；下一步完成新设计 Review，然后按 W/D/C 文件独占分工实施。旧证据不代表新 v2/协议契约已验证。

- 当前任务：A5 `active`，WIP=1；类型化 Runtime、Broker support.raft、v1 重置、窄架构规则/fixture 与文档已实施并通过窄 Raft/Broker reactor/architecture。之前独立 Review 仅覆盖旧增量；本次类型化实现独立 Review 和最终门禁/质量快照仍待主 Agent。
- 已完成：A1 聚焦测试及架构检查通过；A2 首步让 `BrokerQuorumAppService` 直接依赖共享 `LocalBroker`；A4 已抽出 `GatewayDispatchAdapter` 并接入 Frame 应用；A3 对端 RPC 与应用调用方迁移后 Broker 全套 Maven 测试通过。
- 阻塞项：沙箱不允许 Raft 测试绑定端口与 Harness fixture 读取 `/dev/fd`；已授权在沙箱外重跑，不能把环境失败记为通过。
- 下一步：完成 quick、clean、full、behavior、e2e、cleanup、quality 和 handoff；独立 Review 报告见 `.harness/quality/broker-adapter-final-review.md`，只在证据新鲜且门禁通过后归档。

## 回滚与残余风险

- 每个拆分步骤保持可独立回退，先恢复旧 wiring 再处理行为差异。
- 最大风险是破坏日志应用、快照恢复和本机 Lease 原子性；任何证据失败都不得继续删除旧入口。


#### 两项既有自动观察验证阻塞修正

用户批准最小方案，独立设计 reviewer `6cc49b88` Accept；决定见既有自动观察设计末节。A5 active/WIP=1，不提交、不处理其他 WIP。快照 `/tmp/auto-verification-blockers-before`，方案 `/tmp/auto-verification-blockers-proposal.md`。

- [x] 持久化批准决定至自动观察设计、本 A5 与 PROGRESS；readiness exit 0，日志 `/tmp/auto-blocker-readiness.log`。
- [x] 聚焦基线：`mvn -q -pl im-broker/im-broker-server -am '-Dtest=BoltAuthenticatedTlsTest,BoltAuthenticatedFailureTest,BrokerPeerAdapterTest,BrokerFailureProbeTest,BrokerAutomaticFailureFlowTest,BrokerAutomaticFailureObservationTest,BrokerFailureObservationRoundTest' -Dsurefire.failIfNoSpecifiedTests=false test` exit 0，日志 `/tmp/auto-blocker-baseline.log`。
- [x] 测试先行 RED → GREEN：上述精确命令，GREEN 另加 `BrokerHealthObservationPrecisionTest`。Broker main `application/cluster/BrokerClusterAppService.java`、`adapter/cluster/BrokerPeerAdapter.java`；test `adapter/cluster/BrokerPeerAdapterTest.java`、`application/cluster/{BrokerFailureProbeTest,BrokerAutomaticFailureFlowTest,BrokerAutomaticFailureObservationTest,BrokerFailureObservationRoundTest}.java`，根为 Server `src/{main,test}/java/com/co/kc/imchat/broker/`。Bolt `src/main/java/com/co/kc/imchat/plugin/bolt/{core/BoltClientInvoker,ImBoltAutoConfiguration}.java`、`src/test/java/com/co/kc/imchat/plugin/bolt/{BoltAuthenticatedTlsTest,BoltAuthenticatedFailureTest}.java`；HA `im-test/im-routing-ha/src/test/java/com/co/kc/imchat/routingha/AutomaticFailureTlsHaTest.java` 仅迁构造器。分类仅移到 Adapter，App 保留预算与闸门；唯一构造器注入 JDK LongSupplier，所有 caller 迁移，无新包装。
- [x] 顺序 `mvn -q -pl im-broker/im-broker-server -am test`、`mvn -q -pl im-test/im-architecture-test -am -Dtest=LayerBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`、`mvn -q -pl im-test/im-routing-ha -am -DskipTests test-compile`、`./scripts/check-drift.sh` 均 exit 0；核对与快照的限定 diff 后交父会话独立代码 Review。
- [ ] 父会话独立代码 Review 之后统一最终/风险门禁；不以窄验证关闭其他 Block，不并行刷新 Harness。

本轮实际证据：

- RED `/tmp/auto-blocker-red.log` exit 1：TLS/failure 测试先迁移双参数构造器后，旧唯一构造器不匹配导致 testCompile 失败；不是环境失败。生产改造后首轮 `/tmp/auto-blocker-green1.log` exit 1，完整 testCompile 找出遗漏 mock caller `im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/transformer/interfaces/BrokerHealthObservationPrecisionTest.java:107`；仅 `true` 改领域 HEALTHY、已有 import/断言不变，纳入本增量范围。
- GREEN `/tmp/auto-blocker-green2.log` exit 0，精确命令为基线 `-Dtest` 再增加 `,BrokerHealthObservationPrecisionTest`；70 项/0 failures/errors。Adapter 明确四类 typed failure、CompletionException 解包、非 CompletionException 不解包、executor rejection、nonce 错误及验证成功；App 覆盖 10 行 elapsed 真值表、异常/同步构造失败/空状态及既有 gate/context/窗口回归；真实 TLS 验证受控 50/75ms、精确 2950ms 与晚到 typed timeout cause。
- Reactor `/tmp/auto-blocker-broker-reactor.log` exit 0，Broker 当前源码 621 项、Bolt 全套 20 项/0 failures/errors。`/tmp/auto-blocker-layer-boundary.log` exit 0，本轮仅 LayerBoundaryTest 30 项/0 failures/errors（未把其他旧报告计为本轮完整架构结果）；原 App→Bolt 异常依赖违规消失。
- HA test-compile `/tmp/auto-blocker-ha-compile.log` exit 0，不冒称 HA 运行通过；drift `/tmp/auto-blocker-drift.log` exit 0，`git diff --check` exit 0。未改规则；两处 Thread.sleep 已删除。
- `/tmp/auto-blocker-equivalence.log`：App 除 probe 两方法和移除 import 外逐字相同，Tracker/Begin/readability 保持；Peer 除 probe/import 外逐字相同，healthReport/observationCall 不变；Bolt 只逆替换时源/唯一构造器/方法 static 后逐字相同。48 组旧 boolean+typed-error 与新 enum+App 接受真值表等价；测试与静态证据互补，不冒称这替代运行验证。
- 精确文件清单 `/tmp/auto-blocker-files.txt`、限定增量 `/tmp/auto-blocker-task.diff`。下一步父会话独立代码 Review；未运行 quick/clean/full/behavior/e2e/startup，不关闭其他历史验收缺口，未提交。

### A5 增量：结构化 Context 与图文入门

用户已批准 Context 保留但字段改 revision/topology/targets，以及按零基础读者补充DDD/Raft/Gossip/健康观测图文说明；事实源为共识成员边界设计「结构化采样上下文与入门文档」。仅A5/WIP=1，不提交，其他WIP/Follower Block不关闭。

- [x] 独立设计reviewer `05870a5a` Approved；修改 Server `support/health/BrokerHealthObservationTrackerTest.java`、新增同包 `BrokerHealthObservationContextTest.java`：结构相等/hash、防御复制/null、revision/topology/targets分别变化、旧Context读写拒绝、等价Map/Set排列保留失败窗口。旧API编译RED及行为RED按实际记录，不冒称未执行。
- [x] 生产 Server `support/health/BrokerHealthObservationContext.java` 改三字段、`application/cluster/BrokerClusterAppService.java` 所有Context构造去generation字符串、`domain/cluster/model/BrokerClusterTopology.java` 删除仅用于签名的toString。直接caller `application/cluster/BrokerAutomaticFailureObservationTest.java` 与 `domain/cluster/model/BrokerTopologyIsolationTest.java` 同步；其他真实引用扫描后同一步迁移。保持成员读取/异步短路和finally，不新增工厂/包装/递增代次；旧Tracker只移除无用import，不改窗口算法。
- [ ] GREEN：`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerHealthObservationContextTest,BrokerHealthObservationTrackerTest,BrokerTopologyIsolationTest,BrokerFailureProbeTest,BrokerFailureObservationStateTest,BrokerFailureObservationRoundTest,BrokerAutomaticFailureObservationTest,BrokerHealthObservationPrecisionTest -Dsurefire.failIfNoSpecifiedTests=false test`；然后Broker reactor（既有上游失败归因后补充全Broker选择不替代），HA `mvn -q -pl im-test/im-routing-ha -am -DskipTests test-compile`。
- [x] 文档只在 `im-broker/im-broker-server/README.md` 补入门问题/术语/导航，`im-broker/ARCHITECTURE.md` 补详细DDD职责、Raft/Gossip分工、观察到权威决策全链路与双重重验时序，Mermaid配逐图文字；更新旧generation事实和Harness/Coding/ModuleStructure相关说明，不复制第二套运行手册，不把HA能力Block描述成完成。
- [ ] 独立实现/文档Review，顺序architecture/behavior/quick/clean/full；链接/图语法静态检查与实际渲染区分，保留环境和最终门禁缺口，状态回填指纹变更说明。

本结构化Context/图文文档增量实际证据：

- 生产/既有测试接手快照 `/tmp/structured-context-before`，父会话限定diff `/tmp/structured-context-parent.diff`/files.txt，另新增 `support/health/BrokerHealthObservationContextTest.java`；后续恢复 gather Context 独立membership读取、补Context组件注释和既有App空白行清理在最终源码核对。文档接手快照 `/tmp/broker-novice-docs-before`；最终diff `/tmp/README-tutorial-final.diff`、`/tmp/ARCHITECTURE-tutorial-final.diff`。README新增1图，Architecture新增3图；Mermaid fence静态成对、独立语义Review通过，未实际渲染图形，不能宣称视觉验收。
- 本增量未取得测试先行RED：worker先改生产、未按计划先运行缺API测试，明确保留证据缺口，不回滚源码伪造先行。父会话首轮聚焦失败2项（`/tmp/structured-context-parent-focused.log`）：旧Tracker next目标预期未同步、旧Topology文本断言；按结构语义修正并新增Map/Set排列相等/连续失败保留、独立revision/topology/targets失效、不可变/null断言，GREEN `/tmp/structured-context-green.log`。删除旧toString断言不删除值相等/hash/节点隔离保证。
- 完整 `mvn -q -pl im-broker/im-broker-server -am test` exit1，既有未触JetCache容量测试（`/tmp/structured-context-reactor.log`）。补充全Broker包选择首轮672项/1 failure（`/tmp/structured-context-broker-supplemental.log`）：旧改名 `isUnfinishedMembershipChange` 被Jackson当getter新增v2字段；为该计算谓词加@JsonIgnore后原固定v2 fixture通过，未改fixture/格式版本。原命令 `mvn -q -pl im-broker/im-broker-server -am '-Dtest=com.co.kc.imchat.broker.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false test` GREEN exit0（`/tmp/structured-context-broker-green.log`），不覆盖完整reactor失败。
- Review首轮 `42dd5b28` Revise：gather Context复用冷却topology少读一次membership，已恢复原位置显式读取；最终限定代码/文档Accept/findings none，并核对@JsonIgnore及DDD/Raft/Gossip边界。恢复后聚焦命令在本节原8类上追加MembershipEndpointWireCompatibilityTest，exit0（`/tmp/structured-context-final-focused.log`）；HA `mvn -q -pl im-test/im-routing-ha -am -DskipTests test-compile` exit0（`/tmp/structured-context-ha.log`）。
- `./scripts/verify.sh architecture` exit0（`/tmp/structured-context-architecture.log`），behavior exit0/41项（`/tmp/structured-context-behavior.log`）；quick/full各120秒工具超时，clean首轮因已存在的本次触及App尾随空白失败，窄修该空白后clean exit0（`/tmp/structured-context-clean-retry.log`），diffcheck通过。最终门禁仍未全过，状态回填改变指纹，A5 active/WIP=1，Follower Block/其他WIP/历史缺口保留，不提交。未内联unfinishedFailureOperations，未将跨节点Request改名。

### A5 增量：成员变更状态谓词命名

- [x] 用户指定只将 `BrokerClusterOperation.membershipChangeInProgress()` 改为 `isMembershipChangeInProgress()`，同步 `BrokerCluster` 方法引用与 `BrokerMembershipChangeTest` 两处断言；不改 type.membershipChange 或谓词行为，不留兼容转发。
- [x] readiness、`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerMembershipChangeTest,BrokerClusterConsistentVotersTest -Dsurefire.failIfNoSpecifiedTests=false test` exit0（`/tmp/membership-predicate-rename-focused.log`）；quick/full各120秒工具超时，clean exit0（`/tmp/membership-predicate-rename-{quick,clean,full}.log`）。旧Java引用扫描无残留，diffcheck通过。机械改名无新增Harness规则；既有A5 active/WIP=1、Follower Block/历史缺口保留，不提交。

### A5 增量：State Transformer 方法规范

- [x] 用户要求 BrokerClusterStateTransformer 方法格式按规范收敛：仅 Server `transformer/adapter/BrokerClusterStateTransformer.java` 的方法统一目标模型+From，格式/import/record组件注释整理，及全部真实调用方/直接测试同步；不改包/类名，不改MapStruct生成映射或异常/重建行为，不新增兼容方法。基线 `/tmp/BrokerClusterStateTransformer.before-style.java`，施工文件/限定diff由 `/tmp/state-transformer-style-before` 保存。
- [x] Coding Guide 的 Transformer 段明确目标模型+From，HARNESS_GUIDE/矩阵 Review-only说明存量按触碰范围收敛与结构化自动化条件，非机械前缀禁令。readiness exit0，不新增设计取舍。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterStateTransformerTest,MembershipEndpointWireCompatibilityTest,BrokerClusterSnapshotIsolationTest -Dsurefire.failIfNoSpecifiedTests=false test` exit0，10项/0 failures/errors，日志 `/tmp/state-transformer-style-focused.log`；`mvn -q -pl im-test/im-routing-ha -am -DskipTests test-compile` exit0（`/tmp/state-transformer-style-ha.log`）。diffcheck通过；10个caller/test相对接手快照只做方法改名的逐字等价核对通过。实际11文件清单/限定diff见 `/tmp/state-transformer-style.diff`，MapStruct生成映射保留。
- [ ] 独立reviewer `a493192a` 已完成11文件限定diff及生成映射代码审查，scoped Accept/findings none；只关闭本增量代码Review检查点，不代表全树验收。quick/full各120秒工具超时，clean exit0，日志 `/tmp/state-transformer-style-{quick,clean,full}.log`；原WIP/Block保留，A5 active/WIP=1，不提交。文档回填改变指纹，不冒称稳定全树验收。

### A5 增量：ConsensusMembership 集合语义

用户批准领域容器 Set，技术快照 List 保持；[边界设计后续决定](../../design-docs/2026-10-16-consensus-membership-boundary-design.md)独立 reviewer `2939fb39` Approved，无发现。A5 active/WIP=1，保留其他WIP/Follower Block，不提交。

- [x] RED：改 Server `domain/cluster/model/ConsensusMembershipTest.java`、`BrokerClusterConsistentVotersTest.java` 和 `transformer/adapter/BrokerClusterStateTransformerTest.java` 对应 test 路径的输入/预期；同角色重复归一化、交叠构造拒绝、不可变/null/空集合/值相等。App `application/cluster/BrokerFailureObservationStateTest.java`、`BrokerFailureProbeTest.java` 或对应既有观察测试证明不同排列不重置采样/冷却，成员变化仍失效；先执行聚焦取得旧类型/旧语义 RED。
- [x] 生产只改 Server `src/main/java/com/co/kc/imchat/broker/domain/cluster/model/{ConsensusMembership,BrokerCluster}.java`、`transformer/adapter/BrokerClusterStateTransformer.java`、`application/cluster/BrokerClusterAppService.java` Topology 的确定性成员文本。删除聚合 actual集合与重复/交叠检查，保留单出口/角色绑定/进行中规则；现有 MapStruct 转Set，不新建包装，不改变其余采样/读取。所有直接测试/HA constructor 与领域集合预期同步，准确清单/快照另行记录。
- [x] 异常边界测试：初次读取和回调重验处交叠异常不写采样/不摘流，闸门释放后可重试；观察准入异常不进入下游。不吞异常或添加猜测fallback。固定v2 fixture不变，typed observationClusterId/入口领域值/旧展开流程保持。
- [ ] GREEN：`mvn -q -pl im-broker/im-broker-server -am -Dtest=ConsensusMembershipTest,BrokerClusterConsistentVotersTest,BrokerClusterStateTransformerTest,BrokerFailureObservationStateTest,BrokerFailureProbeTest,BrokerFailureObservationRoundTest,BrokerObservationAdmissionTest -Dsurefire.failIfNoSpecifiedTests=false test`，再 `mvn -q -pl im-broker/im-broker-server -am test`；已知上游失败归因后补充全部Broker测试不冒充完整reactor；HA `mvn -q -pl im-test/im-routing-ha -am -DskipTests test-compile`。
- [x] 同步当前Architecture/README、原设计取代标记与PROGRESS，Harness正文/矩阵登记聚焦传感器和集合/签名数据流Review-only边界；Coding/ModuleStructure既有领域拥有方不变，不新增通用豁免；独立实现Review后architecture/behavior/quick/clean/full，记录真实结果及缺口，不能用旧验证代替新集合行为。

本集合增量实际证据与恢复：

- 15文件限定清单 `/tmp/consensus-set-files.txt`，接手快照 `/tmp/consensus-set-before`，限定diff `/tmp/consensus-set-scope.diff` 与父会话 `/tmp/consensus-set-review.diff`，源文件hash `/tmp/consensus-set-source.sha256`。独立 reviewer `34f1bd9a` scoped CODE Accept/findings none。领域Set/Mapper转换/聚合单出口/排序签名之外，原查询领域值、observationClusterId类型和展开流程保留；不修改Common/Plugin技术快照。
- Set API编译RED `/tmp/consensus-set-red.log`；GREEN聚焦73项（`/tmp/consensus-set-green3.log`），途中测试迁移修正保留green1/green2日志。临时移除Topology排序的真实行为RED `/tmp/consensus-set-order-red.log`：hash碰撞Aa/BB端点换序后失败持续期预期5s实际0；恢复排序GREEN，实验未留在最终源码。成员换序采样/冷却不重置，真实成员变化失效，交叠初次/异步失败后闸门释放与准入不委托均有断言。
- `mvn -q -pl im-broker/im-broker-server -am test` exit1，未修改BoltAuthenticatedFailureTest.interruptRemainsLocalAndRestoresThreadFlag预算TimeoutException取代InterruptedException（`/tmp/consensus-set-reactor.log`）；不以聚焦通过覆盖。production compile exit0（`/tmp/consensus-set-compile.log`），HA `mvn -q -pl im-test/im-routing-ha -am -DskipTests test-compile` exit0（`/tmp/consensus-set-ha.log`），无HA运行通过声明。
- 最终顺序 `./scripts/verify.sh architecture`、`behavior`、`quick`、`clean`、`full`：architecture/quick/full各120秒工具超时，无通过结论；behavior exit0/41项，clean exit0。日志 `/tmp/consensus-set-{architecture,behavior,quick,clean,full}.log`。drift/diffcheck通过；文档回填改变fingerprint，不冒称稳定全树验收。A5 active/WIP=1、Follower Block及历史缺口保留，不提交；下一步充分时限复跑广门禁并处理上游基线失败。

### A5 增量：单一返回与观察入口领域值修正

- [x] 用户明确要求实际处理：`BrokerCluster.findConsistentVoters` 以 consistent 状态、失败后 break、最终集合判定统一 return；保留进行中成员操作、重复/交叠、角色/绑定和数量匹配规则，不新增辅助封装。基线 `/tmp/BrokerCluster.before-single-return.java`。Server `application/cluster/BrokerClusterAppService.java` 的 probeBroker/healthReport 均把 cluster/caller/target/revision/epoch 转领域值，report targets 转 Set<BrokerId>，expireAt 保持 Instant，校验比较领域值。接手当前文件基础类型赋值仍存在，已按本次快照修正，不把之前记录当当前事实；App 基线 `/tmp/BrokerClusterAppService.before-domain-query-fix.java`。
- [x] readiness exit0；`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerMembershipChangeTest,BrokerClusterConsistentVotersTest,BrokerClusterTest,BrokerAutomaticFailureObservationTest,BrokerFailureProbeTest,BrokerHealthObservationPrecisionTest,BrokerFailureObservationRoundTest -Dsurefire.failIfNoSpecifiedTests=false test` exit0（`/tmp/voters-single-return-focused.log`）。既有参数化身份/版本拒绝及集合一致性测试保留，不冒称新增 RED。
- [x] 独立限定 reviewer `07471160` Accept/findings none，diffcheck通过；quick/full 在120秒工具时限超时（`/tmp/voters-single-return-{quick,full}.log`），clean exit0（`/tmp/voters-single-return-clean.log`）。既有 reactor失败/广门禁超时/Follower Block保留，A5 active/WIP=1，不提交。
- [x] 用户继续要求 observationClusterId 字段也使用领域值：App String → BrokerClusterId，移除4处重复包装，identity拼接显式.value()；配置/全部23构造caller/HA同步，共18文件。限定基线 `/tmp/observation-cluster-id-before`、diff `/tmp/observation-cluster-id.diff`。readiness、聚焦 BrokerAutomaticFailureObservationTest/BrokerFailureProbeTest/BrokerHealthObservationPrecisionTest 37项、HA test-compile、diffcheck通过（`/tmp/observation-cluster-id-{focused,ha-compile}.log`），全量未刷新。不将旧String字段描述当当前事实。

### A5 增量：成员一致性判断可读性与领域谓词

- [x] 用户批准整理 `BrokerCluster.findConsistentVoters`：同一方法内复用 actual voter/learner Set，Collections.disjoint 表达交叠，显式角色 switch，保留数量/集合校验和原结果；未新增辅助层。基线 `/tmp/BrokerCluster.before-voters-simplify.java`；`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterConsistentVotersTest -Dsurefire.failIfNoSpecifiedTests=false test` 基线通过（`/tmp/voters-simplify-baseline.log`），补充 BrokerClusterTest/BrokerFailureObservationRoundTest GREEN（`/tmp/voters-simplify-green.log`）。独立 reviewer `9988ba5b` 该等价整理 Accept，无发现；不将保留数量检查错误解释为修复所有端点绑定问题，未改变旧规则。
- [x] 用户进一步明确谓词归属：Server `domain/cluster/model/BrokerClusterOperationType.java` 新增 membershipChange；`BrokerClusterOperation.java` 新增 membershipChangeInProgress；`BrokerCluster.java` 使用方法引用。完整类型清单和 `!phase.terminal()` 保持，未倒置为 terminal。新增同包 `BrokerMembershipChangeTest.java`，覆盖全部 type 和全部 phase；先编译 RED（`/tmp/membership-change-red.log`），实现后 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerMembershipChangeTest,BrokerClusterConsistentVotersTest,BrokerClusterTest,BrokerFailureObservationRoundTest -Dsurefire.failIfNoSpecifiedTests=false test` GREEN exit0（`/tmp/membership-change-green.log`）。
- [ ] 独立复审及最终门禁：前轮 `quick` 被用户新请求中止（`/tmp/voters-simplify-quick.log`），不当通过；本增量门禁待记录，A5 active/WIP=1，既有 reactor/广层缺口、Follower Block 与其他WIP保留，不提交。规则拥有方调整为实体/枚举自身，无新通用 Harness 规则，已有「先梳理流程再按稳定职责提取」仍适用。

### A5 增量：共识成员边界与 findConsistentVoters

事实源：[共识成员边界设计](../../design-docs/2026-10-16-consensus-membership-boundary-design.md)，用户明确批准、独立 reviewer `e76a97c8` Approved/findings none。取代共享端点增量中 typed RaftMembership/领域服务所有权，common MembershipEndpoint 不搬回 Broker。A5 active/WIP=1，不提交，不改 JRaft/Follower Block/其他 WIP。

- [x] 测试先行：新增 Server `src/test/java/com/co/kc/imchat/broker/domain/cluster/model/ConsensusMembershipTest.java`；原 `domain/cluster/service/BrokerFailureObservationVotersTest.java` 迁为 `domain/cluster/model/BrokerClusterConsistentVotersTest.java`，保留完整重复/交叠/过渡/绑定规则断言，调用真实 cluster.findConsistentVoters；先跑聚焦取得缺模型/聚合方法编译 RED。null 列表在新容器构造时拒绝，不新增生产兼容入口。
- [x] Server 生产根 `src/main/java/com/co/kc/imchat/broker/`：新增 `domain/cluster/model/ConsensusMembership.java`，迁规则至 `domain/cluster/model/BrokerCluster.java`，删除 `domain/cluster/service/BrokerFailureObservationService.java` 旧方法；`adapter/cluster/BrokerRaftAdapter.java` 具名 getConsensusMembership 经 `transformer/adapter/BrokerClusterStateTransformer.java` 映射；`application/cluster/{BrokerClusterAppService,BrokerMembershipAppService}.java`、`infrastructure/chain/interceptor/BrokerPeerAdmissionInterceptor.java`、`infrastructure/config/BrokerRequestConfiguration.java` 同步 typed 容器/聚合调用及删除无用服务注入。App Topology 使用 ConsensusMembership，不减少原读取/重验。全仓 caller 同步，删除无 caller getRaftMembership。
- [x] Plugin `im-plugin/im-raft/src/{main,test}/java/com/co/kc/imchat/plugin/raft/` 的 main `model/RaftMembership.java`、`core/RaftRuntime.java` 与 test `model/RaftMembershipTest.java`、`core/RaftRuntimeTest.java` 恢复 String 快照；不改 Settings 和变更 API。所有 Broker/HA 构造/mock/fixture 和正负 contains 断言同步，Adapter raw.contains(value)，应用 typed.contains(endpoint)，不去重不规范化字符串。
- [ ] 聚焦顺序：`mvn -q -pl im-broker/im-broker-server -am -Dtest=ConsensusMembershipTest,BrokerClusterConsistentVotersTest,BrokerClusterStateTransformerTest,RaftMembershipTest,RaftRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test`；`mvn -q -pl im-broker/im-broker-server -am test`，失败归因后可补充 `'-Dtest=com.co.kc.imchat.broker.**.*Test'` 不代替完整 reactor；`mvn -q -pl im-test/im-routing-ha -am -DskipTests test-compile`，`git diff --check` 和旧 API/Plugin 泄漏扫描。测试与 caller 精确清单由接手快照及最终限定 diff 登记。
- [x] 文档同步根 Architecture、Common/Raft/Broker README 与 Broker Architecture、原自动观察/共享端点设计和索引、Coding/ModuleStructure/Harness 成员规则拥有方、PROGRESS；不放宽 support.health 依赖规则。独立实现 Review 后顺序 `architecture`、`behavior`、`quick`、`clean`、`full`；环境失败和超时照实保留。

本增量实际证据与恢复：

- 接手快照 `/tmp/consistent-voters-before/`；29文件准确清单 `changed-files.txt`，限定增量 `task.diff`，精确命令/结果 `verification-summary.txt`，规则/全 App 等价证据 `equivalence.txt`，均在该目录。Rule 完整保持，null 列表改为容器构造拒绝；App 全文按批准类型/getter/rule-call 替换等价，12处成员读取及重验位置保持。独立 reviewer `c7574e63` scoped CODE Accept/findings none，其他 WIP/历史缺口不覆盖。
- 编译 RED 为缺 ConsensusMembership/聚合方法；聚焦 GREEN 27项（focused-green.log/.exit），调用方 GREEN 132项（callers-green.log），真实 Adapter duplicate/rejected 分支断言补强；首轮聚焦遗漏 ChangeTest 的 never getRaftMembership 导致编译失败，已修并记录 focused-1.log。
- 完整 `mvn -q -pl im-broker/im-broker-server -am test` 首轮 exit1：未修改 JetCacheLocalValuesTest <=2 实际3；单次原命令复跑 exit1：未修改 BoltAuthenticatedFailureTest 中 InterruptedException 被预算耗尽 TimeoutException 替代（broker-reactor{,-retry}.log）。不弱化测试，不把补充通过等同完整 reactor。补充 `mvn -q -pl im-broker/im-broker-server -am '-Dtest=com.co.kc.imchat.broker.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false test` exit0，新鲜XML SDK38+Server638=676项（broker-supplemental.log、supplemental-counts.json）；HA `mvn -q -pl im-test/im-routing-ha -am -DskipTests test-compile` exit0（ha-compile.log/.exit），无真实 HA 通过声明。
- 最终顺序执行 `./scripts/verify.sh architecture`、`behavior`、`quick`、`clean`、`full`：architecture/quick/full 各在120秒工具时限超时，无通过结论；behavior exit0/41项，clean exit0；日志 `/tmp/consistent-voters-{architecture,behavior,quick,clean,full}.log`。drift与diffcheck通过。文档回填改变指纹，未冒称最终稳定全树验收。A5 active/WIP=1、Follower Block/其他WIP保留，不提交；下一步解决上游偶发基线并以充分时限刷新门禁。

### A5 增量：观察查询入口领域值

- [x] 用户纠正 `probeBroker` 入口仅复制基础值而未转换领域值的问题；只改 Server `application/cluster/BrokerClusterAppService.java` 与 `application/cluster/BrokerAutomaticFailureObservationTest.java`，同样修正 healthReport 五项身份/版本值。cluster/caller/target/revision/epoch 在入口构造 BrokerClusterId/BrokerId/ClusterRevision/RoutingEpoch，后续直接比较值对象，expireAt 保持 Instant。基线 `/tmp/BrokerClusterAppService.before-query-domain-values.java`；不新增封装，不扩张时源或 membership 范围。
- [x] `mvn -q -pl im-broker/im-broker-server -am -Dtest='BrokerAutomaticFailureObservationTest,BrokerFailureProbeTest,BrokerHealthObservationPrecisionTest' -Dsurefire.failIfNoSpecifiedTests=false test` exit 0（`/tmp/query-domain-values-focused.log`），新增五组身份/版本/调用方不匹配在两入口拒绝、无共识写入的断言；属于 GREEN 补强，不冒称 RED。readiness exit 0。
- [ ] 全量/最终门禁沿用既有恢复状态，尚未为本增量刷新；共享端点 reactor 缓存失败和广层超时仍保留。A5 active/WIP=1，不提交。

### A5 增量：共享成员端点

事实源：[共享成员端点设计](../../design-docs/2026-10-16-shared-membership-endpoint-design.md)，用户批准第一方案，独立 reviewer `fd740c07` Approved，无必须修正 finding。只在既有 A5/WIP=1 施工，不提交；取代旧增量的端点不搬包及观察 List 转换决定，不倒改历史证据。非目标为 Settings/String 运行操作 API、JRaft、Follower 能力 Block、已发布协议/v2 字段格式及其他 WIP。

- [x] 测试先行：新增 `im-common/src/test/java/com/co/kc/imchat/common/domain/cluster/model/MembershipEndpointTest.java` 与 `im-plugin/im-raft/src/test/java/com/co/kc/imchat/plugin/raft/model/RaftMembershipTest.java`，校验值相等/原字符串/Serializable、空白拒绝，typed List 防御复制、不可变、null 拒绝、空列表合法、顺序/重复保留；先运行聚焦命令取得缺共享类型/typed API 的编译 RED。
- [x] 迁移生产：`im-common/src/main/java/com/co/kc/imchat/common/domain/cluster/model/MembershipEndpoint.java`，删除 Broker 原 `domain/cluster/model/MembershipEndpoint.java`；`im-plugin/im-raft/src/main/java/com/co/kc/imchat/plugin/raft/{model/RaftMembership,core/RaftRuntime}.java`；Broker Server `src/main/java/com/co/kc/imchat/broker/` 下 `domain/cluster/model/{BrokerCluster,BrokerClusterOperation,BrokerClusterOperationProposal,LearnerJoinIntent}.java`、`domain/cluster/service/BrokerFailureObservationService.java`、`adapter/cluster/BrokerRaftAdapter.java`、`application/cluster/{BrokerMembershipAppService,BrokerClusterAppService}.java`、`infrastructure/chain/interceptor/BrokerPeerAdmissionInterceptor.java`、`transformer/{adapter/BrokerClusterStateTransformer,application/BrokerClusterAppTransformer}.java`。只迁同概念 imports/同包引用、typed 集合和9处 contains；真实 String 边界仍 `.value()`，保留单项 Transformer 映射，删除无 caller 集合转换。
- [x] 测试 caller 同步：Raft `core/RaftRuntimeTest.java`；Broker Server 同根 `src/test` 的 `adapter/cluster/BrokerRaftAdapterTest.java`、`application/cluster/{BrokerAutomaticFailureObservationTest,BrokerClusterAppServiceChangeTest,BrokerFailureObservationRoundTest,BrokerFailureObservationStateTest,BrokerFailureProbeTest}.java`、`domain/cluster/model/{BrokerChangeIntentTest,BrokerClusterTest,BrokerTopologyIsolationTest}.java`、`domain/cluster/service/BrokerFailureObservationVotersTest.java`、`infrastructure/interceptor/BrokerObservationAdmissionTest.java`、`interfaces/scheduler/BrokerMembershipSchedulerTest.java`、`support/{BrokerClusterCommandFixture,StatefulClusterAdapterFixture}.java`、`support/raft/model/BrokerClusterSnapshotIsolationTest.java`、`transformer/{adapter/BrokerClusterStateTransformerTest,application/BrokerClusterAppTransformerTest,interfaces/BrokerHealthObservationPrecisionTest}.java` 及实际 caller 扫描发现的同包引用；`im-test/im-routing-ha/src/test/java/com/co/kc/imchat/routingha/AutomaticFailureTlsHaTest.java`。迁移旧集合转换断言到 RaftMembershipTest，不丢弃重复/顺序保证；成员真实测试保留快照隔离和精确值命中，新增同 value 不同实例断言。
- [x] v2 固定载荷兼容：先在旧类型下取得含 endpoint 的 command 与含绑定/operation 的 snapshot JSON，保存 test resources 固定 fixture；在现有 Broker `support/raft/codec/BrokerClusterCommandCodecTest.java` 和 `support/raft/model/BrokerClusterSnapshotIsolationTest.java`（或其实际 snapshot codec 直接测试）覆盖原始载荷解码/字段、版本2、无类名，不只新编码往返。准确 fixture 路径和 test 位置写入完成证据。
- [ ] GREEN 顺序：`mvn -q -pl im-common,im-plugin/im-raft -am -Dtest=MembershipEndpointTest,RaftMembershipTest,RaftRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test`；`mvn -q -pl im-broker/im-broker-server -am test`；`mvn -q -pl im-test/im-routing-ha -am -DskipTests test-compile`；全仓 `rg` 核对旧包/无用转换/contains(String) 无残留，`git diff --check`。
- [x] 当前事实同步 `ARCHITECTURE.md`、`im-common/README.md`、`im-plugin/im-raft/README.md`、`im-broker/ARCHITECTURE.md`、`im-broker/im-broker-server/README.md`、原自动观察设计和索引、PROGRESS；共享约定只需收敛已有 common 所有权，不为类型增加白名单。
- [ ] 独立实现 Review 后 `./scripts/verify.sh architecture`、`behavior`、`quick`、`clean`、`full`，磁盘不足/环境失败先归因，不删除用户文件、不弱化规则。记录精确命令/日志/测试数；其他 WIP/Block/历史缺口继续保留。

共享成员端点实际证据与恢复：

- 限定40文件清单 `/tmp/shared-endpoint-files.txt`，接手快照 `/tmp/shared-endpoint-before`，差异 `/tmp/shared-endpoint-task.diff`；独立 reviewer `2577bfdd` scoped CODE Accept/findings none，正式交付 Revise 保留验证缺口。全部9处 contains 已 typed，旧包及集合 mapper 全仓 Java 无残留。
- 精确命令/退出码见 `/tmp/shared-endpoint-validation.txt`：共享 API 编译 RED exit 1；Common/Raft GREEN 19项通过（`/tmp/shared-endpoint-focused-green.log`）。完整 Broker reactor 两次均在未修改的 im-cache 容量断言失败（4 > 2），未到 Broker，不以补充测试覆盖该失败。补充 `mvn -q -pl im-broker/im-broker-server -am '-Dtest=com.co.kc.imchat.broker.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false test` exit 0，Server 113类630项、SDK 10类38项，已核对所有 Server Test 类都有报告；日志 `/tmp/shared-endpoint-broker-focused-green.log`。首轮补充编译发现两处 typed caller 遗漏已修，保留 `/tmp/shared-endpoint-broker-focused.log`。HA test-compile exit 0（`/tmp/shared-endpoint-ha-test-compile.log`），不冒称 HA 运行通过。
- 旧类型下真实生成 fixture：原 SnapshotIsolationTest 暂加 emitter，`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterSnapshotIsolationTest -Dsurefire.failIfNoSpecifiedTests=false test` exit 0后恢复原测试；产物 `/tmp/shared-endpoint-baseline/membership-{command,snapshot}-v2.json` 已复制到 Server `src/test/resources/raft/`，直接测试为 `support/raft/codec/MembershipEndpointWireCompatibilityTest.java`。command SHA256 `1009f2acfd961258ceaffb176843ce72b16239c14381f62aa83f0239a21c10fa`，snapshot `fe41db6ad1d91ebb8b00edbfe0c09a281c00c1dd19913c9f7d84d11d78c58032`。原 quiet 日志无输出，后附命令摘要，不能冒充原详细日志；生成器由 worker 原 edit transcript 恢复于 `/tmp/shared-endpoint-recovered-provenance/`（完整测试/patch/方法/PROVENANCE），明确为 recovered provenance，非独立保留原文件。Reviewer 已核对 fixture 解码和再编码一致性，但该恢复资料晚于其最终 Review，不冒充其已独立核验过程来源。
- `architecture` 120秒、补充 `mvn -q -pl im-test/im-architecture-test -am -Dtest=LayerBoundaryTest,ModuleBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test` 120秒、`behavior` 120秒、`quick` 120秒、`full` 120秒均工具超时，没有通过结论；对应 `/tmp/shared-endpoint-{architecture,boundaries,behavior,quick,full}.log`。`clean` exit 0（`/tmp/shared-endpoint-clean.log`），diffcheck通过；文档回填后指纹改变，不冒称稳定全树验收。待解决缓存基线/充分执行时限后复跑门禁，A5 active/WIP=1、Follower Block/其他WIP保留，不提交。

### A5 增量：观察时源最小化与 Harness 反馈

用户要求删除 `BrokerClusterAppService.observationClock` 与 `observationMonotonic` 注入，直接使用 `Instant.now()` / `MonotonicTimestamp.now()`；同时将反复出现的测试驱动生产依赖问题沉淀到 Harness。属于既有 A5 的行为保持维护，WIP=1；保留其他 WIP、Follower Block 和历史验收缺口，不提交。

- [x] 生产仅改 Server `application/cluster/BrokerClusterAppService.java` 与 `infrastructure/config/BrokerClusterConfiguration.java`；同步全仓直接构造 caller，保留墙钟/单调时间区分，不新增替代 seam、wrapper 或构造器。准确变更清单和原文件快照由 `/tmp/observation-time-before` 维护。
- [x] 原有时间边界测试改为受控测试时间，保留准确阈值、回拨、窗口、异步实际完成及代次断言。顺序执行 `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerFailureObservationStateTest,BrokerFailureProbeTest,BrokerFailureObservationRoundTest,BrokerAutomaticFailureFlowTest,BrokerAutomaticFailureObservationTest,BrokerHealthObservationPrecisionTest' -Dsurefire.failIfNoSpecifiedTests=false test`、`mvn -q -pl im-broker/im-broker-server -am test`，并编译 `mvn -q -pl im-test/im-routing-ha -am -DskipTests test-compile`。
- [x] 修正 `docs/references/CODING_GUIDE.md` 原「时间分支必须注入 Clock」与「不要只为测试抽象」的冲突；同步 `UNIT_TEST_GUIDE.md` 的确定性测试要求，`HARNESS_GUIDE.md`、`HARNESS_RULE_MATRIX.md` 为 Review-only，写明误报原因、当前存量按触碰范围收敛及自动化条件；同步原自动观察设计的装配事实。规范限定生产用途，不禁止合理框架时钟，不按类名豁免。
- [x] 按用户追加要求删除 Server `adapter/cluster/BrokerPeerAdapter.java` 的 `observationCall(Supplier<T>)` 包装，两处直接调用 `CompletableFuture.supplyAsync`；probe 同步拒绝仍为 UNKNOWN，healthReport 同步拒绝仍返回 failedFuture。接手文件 `/tmp/BrokerPeerAdapter.before-observation-call.java`，回归命令在上述聚焦选择增加 `BrokerPeerAdapterTest`。
- [ ] 检查限定增量，独立 Review；执行 `./scripts/verify.sh architecture`、`./scripts/verify.sh quick`、`./scripts/verify.sh clean`、`./scripts/verify.sh full` 并记录实际结果。readiness 已 exit 0；未执行的门禁不能用历史证据代替。

本轮证据与恢复：

- 聚焦选择为 `-Dtest='BrokerPeerAdapterTest,BrokerClusterAppService*Test,BrokerClusterLease*Test,BrokerDrainAppServiceTest,BrokerFailureRecoveryAppServiceTest,BrokerAutomaticFailure*Test,BrokerFailureObservation*Test,BrokerFailureProbeTest,BrokerHealthObservationPrecisionTest,BrokerFailureObservationConfigurationTest,BrokerObservationRequestConfigurationTest'`，配合 `-Dsurefire.failIfNoSpecifiedTests=false`；174 项通过，日志 `/tmp/observation-time-focused-peer.log`。HA `test-compile` exit 0，日志 `/tmp/observation-time-ha-compile.log`。
- 时源改造后 reactor 首轮未触及 JetCacheLocalValuesTest 容量断言失败（<=2，实际3），原命令复跑 exit 0，Broker 621、SDK 38；日志 `/tmp/observation-time-broker-reactor{,-retry}.log`。Peer 内联后 reactor 在未触及 BoltAuthenticatedFailureTest 的中断测试失败：预算已耗尽 TimeoutException 取代预期 InterruptedException，日志 `/tmp/observation-time-broker-reactor-peer.log`；未修改该测试，不把前轮通过冒充最新 reactor。
- 独立 reviewer `49f74d0d` scoped CODE Accept、无代码 finding；文档 Revise 仅为 UNIT_TEST_GUIDE 仍强制注入时间的矛盾，已修正，尚未复审。drift 和 diffcheck 已通过；architecture 在 240 秒工具时限超时，日志 `/tmp/observation-time-architecture.log`，无最终通过结论。quick/clean/full 及风险门禁尚未执行。
- 用户随后要求内联 `validateProbeEnvelope` / `validateHealthEnvelope`：已删除两方法，将各自全部字段匹配和期限检查逐字等价移回 probe/healthReport，不改身份来源、异常分类或 Future 完成时机；基线 `/tmp/BrokerPeerAdapter.before-envelope-inline.java`。`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerPeerAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test` exit 0，17 项，日志 `/tmp/observation-envelope-focused.log`；`verify.sh clean` exit 0，`quick` / `full` 分别在 120 秒工具时限超时，无通过结论，日志 `/tmp/observation-envelope-{quick,clean,full}.log`。本维护不引入新规则，其他门禁缺口继续保留。
- [x] 用户逐项要求解除 App 私有辅助封装：`probeStatus`、私有 `probeBroker`、`collectFailureReports`、`mergeFailureReports`、`observationVoters`、`sortedActiveBrokers`、`validateObservationRequest`、`observationTopology`、`roundTime`、`observationRequest`、`observationContext`。只改 Server `application/cluster/BrokerClusterAppService.java`，原文件 `/tmp/BrokerClusterAppService.before-probeStatus-inline.java`；不改公开 probeBroker 查询入口、不新增替代 helper、不改变资格、上下文重验、单调计时及实际完成闸门。聚焦命令 `mvn -q -pl im-broker/im-broker-server -am -Dtest='BrokerFailureProbeTest,BrokerFailureObservation*Test,BrokerAutomaticFailure*Test,BrokerHealthObservationPrecisionTest' -Dsurefire.failIfNoSpecifiedTests=false test`，再核对限定 diff、独立 Review 与适用门禁；新修改使前轮相关证据过期。实际聚焦 exit 0，60 项/0 failures/errors/skipped，日志 `/tmp/broker-cluster-inline-focused.log`，完整增量 `/tmp/BrokerClusterAppService.all-helper-inline.diff`；独立 reviewer `69e45d8d` scoped Accept/findings none，并确认 UNIT_TEST_GUIDE 矛盾已修正。`quick` 在 120 秒工具时限超时，日志 `/tmp/broker-cluster-inline-quick.log`，不作为通过证据。
- [x] 用户进一步明确原则：先将完整流程放在一起梳理，再决定封装，避免用方法名隐藏质量与阅读性问题。已同步 `CODING_GUIDE.md`「Java 与可读性」、`CODE_REVIEW_GUIDE.md` 的 Simplicity 检查、`HARNESS_GUIDE.md` 与规则矩阵，登记 Review-only 及升级条件；不按行数或调用方数量机械禁止私有方法，不扩大本轮代码范围。此文档增量晚于上述 reviewer，未冒充已复审。
- [x] 用户批准删除 Gossip 重复身份 `ConnectionKey`，复用 `ConnectionRouteId`：Server `adapter/gossip/BrokerGossipAdapter.java` 及直接测试 `adapter/gossip/BrokerGossipAdapterTest.java`；基线 `/tmp/BrokerGossipAdapter.before-route-id.java`。key 的 `CONNECTION:userId:gatewayId` 格式、仅首个分隔符切分与平铺 `ConnectionState` JSON 留在 Adapter；领域不新增 codec。复用领域校验后 null user/blank gateway 明确拒绝，合法输入兼容；新增多 Gateway 同用户、含冒号 Gateway、缺失/非法载荷回退及身份校验断言。合法兼容测试迁移前通过（`/tmp/broker-route-id-baseline.log`），校验断言为 GREEN 补强，不冒称 RED。
- [x] 用户继续要求内联 `evaluateFailureReports` 和 `initiateAutomaticFailure`，仅改上述 App 文件；基线 `/tmp/BrokerClusterAppService.before-final-inline.java`、限定差异 `/tmp/broker-final-inline-app.diff`。重验/冷却失效/单调 freshness、eligible 目标拒绝后直接返回、冻结 identity/reporters、Begin 与 advanceFailureEpoch 顺序保持，不新建替代封装。
- 本两项聚焦 `mvn -q -pl im-broker/im-broker-server -am -Dtest='BrokerGossipAdapterTest,BrokerFailureProbeTest,BrokerFailureObservation*Test,BrokerAutomaticFailure*Test,BrokerHealthObservationPrecisionTest' -Dsurefire.failIfNoSpecifiedTests=false test` exit 0，72 项/0 failures/errors，日志 `/tmp/broker-final-inline-focused.log`。Broker reactor 首轮未触及 JetCacheLocalValuesTest 容量断言失败（<=2，实际4），同一命令 `mvn -q -pl im-broker/im-broker-server -am test` 复跑 exit 0，日志 `/tmp/broker-final-inline-reactor{,-retry}.log`；不修改缓存测试。独立 reviewer `6637bd2f` scoped CODE Accept/findings none，正式交付 Revise 保留验证缺口；唯一文案建议已修正矩阵断句。`./scripts/verify.sh behavior` exit 0，41 项（`/tmp/broker-final-inline-behavior.log`）；`architecture` 在 180 秒工具时限超时，无最终结论（`/tmp/broker-final-inline-architecture.log`）。`quick` exit 1：test-local-compose-harness.sh:544 的临时 calls.log 报 No space left on device，`df -h . /tmp` 显示 Data 容量 100%、可用约 1.9GiB；不自动删除磁盘文件。`clean` exit 0（`/tmp/broker-final-inline-{quick,clean}.log`）；当前 full 因前序磁盘环境失败未运行，需释放足够空间后顺序复跑 quick/full。状态回填改变 fingerprint，已有机器结果不冒充最终稳定工作树验收；其他 WIP/Follower Block 保留。
- [x] 用户本轮确认仅调整 `application/cluster/BrokerClusterAppService.java#probeFailures` 局部命名：`requests` → `probeTasks`、`request`（CompletionStage）→ `probeResult`，不合并不同类型变量，不改类名、Stream、响应关联检查或单调计时。接手快照 `/tmp/BrokerClusterAppService.before-probe-names.java`；六处精确替换后的全文件等价比较通过。`./scripts/verify.sh readiness`、`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerFailureProbeTest -Dsurefire.failIfNoSpecifiedTests=false test`（17 项）、`mvn -q -pl im-broker/im-broker-server -am test`、`./scripts/verify.sh architecture` 和 `./scripts/verify.sh clean` 均 exit 0；日志 `/tmp/probe-names-{focused,reactor,architecture,clean}.log`。`./scripts/verify.sh quick` 在 180 秒工具时限超时（`/tmp/probe-names-quick.log`），无通过结论；按前序门禁失败先归因规则，本轮未继续 full，完整验收缺口保留。状态回填后指纹变化，不冒称最终全树验收；A5 active/WIP=1、其他 WIP/Follower Block 保留，不提交。
- [x] 用户批准先试流式表达：仅改 Server `application/cluster/BrokerClusterAppService.java#probeFailures`，顺序 `stream/filter/filter/map/reduce(thenCombine)` 代替循环与 `probeTasks/allOf`；保留 `probeResult` 同步异常转换、完整 handle、单调计时、Context 重验及 finally 释放，不新增 helper、不串行等待、不用 parallelStream。接手快照 `/tmp/BrokerClusterAppService.before-probe-stream.java`。readiness、`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerFailureProbeTest -Dsurefire.failIfNoSpecifiedTests=false test`（17 项）、`mvn -q -pl im-broker/im-broker-server -am test`、`./scripts/verify.sh architecture`、`./scripts/verify.sh clean` 均 exit 0；日志 `/tmp/probe-stream-{focused,reactor,architecture,clean}.log`。`./scripts/verify.sh quick` 在 300 秒工具时限超时（`/tmp/probe-stream-quick.log`），不宣称通过；前序门禁缺口归因为工具时限，full 未运行。无新增业务规则，不改类名；状态回填后指纹变化，A5 active/WIP=1、其他 WIP/Follower Block 和独立复审/完整验收缺口保留，不提交。
- [x] 用户批准删除重复身份注入：Server `application/cluster/BrokerClusterAppService.java` 移除 `observationLocalId` / `observationClusterId`，本机 ID 在四个方法局部读取 `brokerRaftAdapter.getLocalBroker().getId()`；集群 ID 从 Adapter 的 typed `getClusterId()` 获取，`adapter/cluster/BrokerRaftAdapter.java` 构造时保存 `BrokerClusterId`，不修改 Plugin 或把集群身份放入 LocalBroker。同步 `infrastructure/config/BrokerClusterConfiguration.java` 及 22 处测试构造调用（Server application/cluster 14 个测试文件、transformer/interfaces/BrokerHealthObservationPrecisionTest、routing-ha/AutomaticFailureTlsHaTest）；观察 mock 绑定真实查询入口，Raft Adapter 既有真实构造测试增加集群 ID 断言，无兼容构造器或 wrapper。上一轮本机 ID 维护的聚焦/HA 编译通过，reactor 遭未触及缓存容量/成员迁移失败，后者独立复跑通过；architecture 超时，quick/clean 因磁盘 ENOSPC exit 1，PROGRESS 回填也失败，未自动清理文件，日志 `/tmp/remove-observation-id-*.log`。本轮磁盘可用约 9.1GiB、readiness exit 0；`mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerFailureProbeTest,BrokerFailureObservation*Test,BrokerAutomaticFailure*Test,BrokerHealthObservationPrecisionTest,BrokerRaftAdapterTest,BrokerFailureObservationConfigurationTest,BrokerObservationRequestConfigurationTest' -Dsurefire.failIfNoSpecifiedTests=false test`、`mvn -q -pl im-broker/im-broker-server -am test`、`mvn -q -pl im-test/im-routing-ha -am -DskipTests test-compile`、`./scripts/verify.sh architecture`、`./scripts/verify.sh clean` 均 exit 0，日志 `/tmp/remove-cluster-id-{focused,reactor,ha-compile,architecture,clean}.log`。`./scripts/verify.sh quick` 在 180 秒工具时限超时（`/tmp/remove-cluster-id-quick.log`），full 未运行；独立复审、startup/behavior/e2e 与完整验收缺口保留，不以构造编译代替 HA 行为证明。文档回填改变指纹，A5 active/WIP=1、其他 WIP/Follower Block 保留，不提交。
- [ ] 用户批准冷却已等待时长直接传递：仅改 Server `application/cluster/BrokerClusterAppService.java`、`domain/cluster/service/BrokerFailureObservationService.java`、对应 `domain/cluster/service/BrokerFailureObservationServiceTest.java` 和 `transformer/interfaces/BrokerHealthObservationPrecisionTest.java`；`cooldownStartedAt` 改为返回非负 Duration 的 `cooldownElapsed`，evaluate 保留领域冷却阈值校验，不改门限/重置/并发闸门。决定补入原自动故障观察设计。先执行测试 API 编译 RED，再 `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerFailureObservationServiceTest,BrokerHealthObservationPrecisionTest,BrokerFailureObservation*Test,BrokerAutomaticFailure*Test,BrokerFailureProbeTest' -Dsurefire.failIfNoSpecifiedTests=false test`、Broker reactor、HA test-compile、architecture/behavior 与 quick/clean/full，记录实际结果，独立 Review；A5 active/WIP=1，不关闭其他缺口，不提交。
- [x] 用户要求先独立实现 im-lock LocalLock 后供 Review（实现和独立复审已完成，最终门禁与用户 Review 未完成）：[设计](../../design-docs/2026-10-17-local-lock-design.md)，独立设计 reviewer `8bcc59eb` Accept。精确范围 `im-plugin/im-lock/src/main/java/com/co/kc/imchat/plugin/lock/core/LocalLock.java`、`src/test/java/com/co/kc/imchat/plugin/lock/LocalLockTest.java`、模块 README 及设计/状态文档；不改 Broker、现有分布式锁、POM，不实现 Template/Registry。先测试编译 RED，再 AQS 单状态实现 GREEN，命令 `mvn -q -pl im-plugin/im-lock -am -Dtest=LocalLockTest -Dsurefire.failIfNoSpecifiedTests=false test`、`mvn -q -pl im-plugin/im-lock -am test`、`./scripts/verify.sh architecture`、quick/clean/full，独立实现 Review 后交用户复审，不提交。A5 active/WIP=1，前轮冷却增量代码 reviewer `33e1db3a` Accept、focused/reactor/HA/architecture/behavior 通过，quick 超时和最终验收缺口保留。
  - 实际证据：readiness exit 0；聚焦 RED 为 LocalLock 类型缺失（`/tmp/local-lock-red.log`），首版 GREEN exit 0。首轮 reviewer `ff87044a` Revise 两项测试问题：ExecutorService.close 失败清理可能挂起、入口 latch 不证明真实等待；已改虚拟线程/FutureTask 有界等待、Awaitility 确认等待状态、取消后队列后继及限时成功路径。最终 `mvn -q -pl im-plugin/im-lock -am -Dtest=LocalLockTest -Dsurefire.failIfNoSpecifiedTests=false test` exit 0，9 项；`mvn -q -pl im-plugin/im-lock -am test` 首版通过，修改测试后 120 秒超时，原命令复跑 exit 0，模块共 24 项；日志 `/tmp/local-lock-{green,final-focused,module,module-final,module-retry}.log`。architecture 首轮 180 秒超时，复跑 exit 0（`/tmp/local-lock-architecture{,-retry}.log`）；quick 180 秒超时，无通过结论（`/tmp/local-lock-quick.log`），先归因工具时限，full 未运行；clean exit 0（`/tmp/local-lock-clean.log`）、diffcheck通过。第二位独立 reviewer `203e8f18` scoped Accept/findings none，两项关闭；未引入新的工程规则或放宽现有规则。无 token 的误释放限制为明确接受的契约，不声称全局重复释放检测。文档回填改变指纹，用户 Review 和完整验收待续。
- [x] 用户指定最终命名 MutexLock：原 LocalLock 仅机械改名，生产路径 `im-plugin/im-lock/src/main/java/com/co/kc/imchat/plugin/lock/core/MutexLock.java`、测试 `src/test/java/com/co/kc/imchat/plugin/lock/MutexLockTest.java`，README 和原设计正文同步；设计文件名与历史证据保留。`mvn -q -pl im-plugin/im-lock -am clean test` exit 0（清除旧 LocalLock class，模块 24 项），`./scripts/verify.sh clean` exit 0，diffcheck通过；quick 120 秒工具超时、full 未运行，日志 `/tmp/mutex-lock-{test,quick,clean}.log`。不改变生产逻辑，不新增规则/依赖或 Broker caller，用户 Review 和历史完整门禁缺口保留，A5 active/WIP=1，不提交。
- [ ] 用户已 Review MutexLock 无问题，批准 Broker 最小接入，不做 Template。决定见原本机锁设计后续决定。生产仅改 Server POM 添加 im-lock、`application/cluster/BrokerClusterAppService.java` 以两个实例替换 round/recovery 布尔闸门，两个恢复入口共用 recoveryLock；保持 probing/冷却和同步异常语义。聚焦测试范围 `BrokerFailureObservationRoundTest`、`BrokerAutomaticFailureObservationTest`/既有观察恢复测试，新增跨线程完成/异常再入断言；同步 Server README。先聚焦基线，迁移后同命令 GREEN：`mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerFailureObservation*Test,BrokerAutomaticFailure*Test,BrokerFailureProbeTest,BrokerFailureRecoveryAppServiceTest' -Dsurefire.failIfNoSpecifiedTests=false test`，再 Broker reactor、architecture、behavior、startup/e2e 及 quick/clean/full，按层归因；独立 Review，不提交，A5 active/WIP=1。
- [ ] 用户批准按目标复用 MutexLock，并拆分 probe 完成链：仅 Server `application/cluster/BrokerClusterAppService.java` 及直接 `BrokerFailureProbeTest.java`、Server README。新增 ConcurrentMap<BrokerId,MutexLock> 不删除历史目标；map 捕获锁、busy 跳过；handle 分类传私有 ProbeSample，thenAccept 重验/记录，whenComplete 释放；同步 catch failedFuture 保留，State 删除 probing/beginProbe/endProbe，冷却不动。决定见原本机锁设计后续补充；先聚焦基线 `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerFailureProbeTest,BrokerFailureObservation*Test,BrokerAutomaticFailure*Test,BrokerHealthObservationPrecisionTest' -Dsurefire.failIfNoSpecifiedTests=false test`，新增跨线程独立目标释放行为测试（维护基线），迁移后同命令 GREEN，再 Broker reactor、architecture/behavior/e2e/startup、quick/clean/full 和独立 Review。历史 ID 锁表随应用实例保留的存储增长明确接受，不冒称有界；A5 active/WIP=1，其他缺口保留，不提交。
- [ ] 用户批准先处理观察 timeout 归属：Server `adapter/cluster/BrokerPeerAdapter.java` 注入 BrokerFailureObservationProperties、probe/healthReport 删除 Duration 参数并内部读取 rpcTimeout；`application/cluster/BrokerClusterAppService.java` 只迁调用，保留耗时接受性预算；`infrastructure/config/BrokerClusterConfiguration.java` 同步装配。测试精确范围为全仓 probe/healthReport caller 与 new BrokerPeerAdapter 构造（Server application/cluster/观察 Handler 测试、adapter/cluster/BrokerPeerAdapterTest、routing-ha/AutomaticFailureTlsHaTest），配置测试与 README 同步。先聚焦 `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerPeerAdapterTest,BrokerFailureProbeTest,BrokerFailureObservation*Test,BrokerAutomaticFailure*Test,BrokerHealthObservationPrecisionTest,BrokerFailureObservationConfigurationTest,BrokerObservationRequestConfigurationTest' -Dsurefire.failIfNoSpecifiedTests=false test`，再 Broker reactor、HA test-compile、architecture/startup 与 quick/clean/full。只迁配置所有权，不顺带改请求对象/nonce/期限或放宽响应校验；设计已补充原自动观察决定，不提交，A5 active/WIP=1。
- [ ] 用户批准只简化 probe 可信成功响应：Server `adapter/cluster/BrokerPeerAdapter.java#probe` 移除回显匹配与完成后墙钟检查，不扩展 healthReport/SDK/App timeout；直接 `BrokerPeerAdapterTest` 修改旧 nonce 拒绝为认证成功可信断言，并增加期限已到 probe 接受、report 仍拒绝的对照；Server README 同步。原自动观察设计已标明取代旧 probe 要求，独立设计 reviewer `70b7f033` Accept；真实 RED `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerPeerAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test` 18 项/1 failure（原 UNKNOWN，新预期 HEALTHY），日志 `/tmp/probe-trust-red.log`。GREEN 同命令，再 Broker reactor、architecture/behavior、quick/clean/full 和独立 Review，实际结果待回填；A5 active/WIP=1，不提交、不关闭其他缺口。
- [ ] 用户追加要求前述 probe 调整一起实施：Adapter probe 改 typed target/clusterId/revision/epoch 参数，使用本机 caller，在任务实际执行时生成 nonce/expireAt，App 删除请求包装与单次 elapsed 分类；保留完成采样时间、Context 重验、MutexLock 释放、healthReport/整轮预算。精确生产范围 Server `application/cluster/BrokerClusterAppService.java`、`adapter/cluster/BrokerPeerAdapter.java`、README；测试全仓 probe 直接 caller/Mockito及 PeerAdapterTest，不新增领域包装。设计见原自动观察完整职责收敛；测试 API RED 再聚焦 GREEN `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerPeerAdapterTest,BrokerFailureProbeTest,BrokerFailureObservation*Test,BrokerAutomaticFailure*Test,BrokerHealthObservationPrecisionTest' -Dsurefire.failIfNoSpecifiedTests=false test`，再 Broker reactor/HA compile、architecture/behavior/startup/e2e和 quick/clean/full、独立 Review。历史缺口保持，A5 active/WIP=1，不提交。
- [x] 用户批准本机采样结果迁移：新增 Server `support/health/BrokerHealthSample.java`，两个字段 status/completedAt 与原私有 ProbeSample 完全相同；`application/cluster/BrokerClusterAppService.java` 删除内嵌 record 并迁移引用，无校验/构造语义变化，不移动领域规则。`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerFailureProbeTest -Dsurefire.failIfNoSpecifiedTests=false test` exit 0；quick 120 秒超时，clean exit 0，日志 `/tmp/health-sample-{focused,quick,clean}.log`，full 未运行。前轮 probe 完整职责收敛 reviewer `d0b444b0` scoped Accept，focused/reactor/HA compile/architecture exit0；历史门禁缺口保留，不冒称采样迁移后的全树验收。用户所贴优化目标仍为 probeFailures，若指其他方法待明确，不擅改报告调用。A5 active/WIP=1，不提交。
- [ ] 优先解除 Follower membership 阻塞：RaftRuntime 当前使用 Leader-only listPeers/listLearners，先恢复原批准私有制品路线；用户批准上游 `NodeTest#testSnapshotSync` 确定性修复（两批1000日志、成功屏障、finally清理，不降断言），决定见已存在的 JRaft 私有制品设计后续决定。隔离源码允许 NodeTest 单方法修改，正式 recipe/生产依赖尚不切换。已获诊断 RED（492请求/1OK/491EBUSY）及未补丁同断言失败；RocksDBLogStorageTest单独8项通过。先独立设计 Review，在新临时源码修复并运行原 `mvn -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl jraft-core -am '-Dtest=NodeTest#testSnapshotSync' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true test`（固定Java21/Maven3.8.4），再全NodeTest、完整jraft-core与AppliedConfiguration suites。长校验后台串行，精确日志/补丁指纹和真实结果回填，不跳测试/断言、不复制初始配置、不提交；A5 active/WIP=1，Follower Block仍未解除。
- [ ] JRaft 制品恢复推进（用户批准继续）：快照修复设计 reviewer `467734ec` 第二轮 Accept，代码 reviewer `0d19eb88` Accept；固定隔离源码 `/tmp/follower-snapshot-deterministic.VD9QwD`。单次默认完整上游命令 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home /Users/kc/Program/maven/apache-maven-3.8.4/bin/mvn -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl jraft-core -am -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true test` exit 0/1127.5s，99 suites/468 tests/466 pass/2 skipped，log `/tmp/follower-upstream-complete.log`、`.exit`、`-run.json`、`-coverage.json`，949源文件前后一致。跳过为 ElectSelfPersistOrderTest无法转移leader及ConnectionRefreshTest.simulation；不冒称全用例运行。历史 removingLeader100ms失败未被修复/抹除；默认本次NodeTest64通过。只恢复 third-party/jraft-applied-membership/{applied-membership.patch,build.sh,provenance.yml,README.md,test_build.py,LICENSE,NOTICE}，正式patch含已Review NodeTest修复，不vendoring，不改rootPom/im-raft。先审查recipe方案/测试/补丁指纹，独立审查访问器退出条件，再双隔离构建、单实现与隔离消费者验证；生产依赖切换另设检查点。worker `89b65746` 与只读 coverage reviewer `99725111`，不并行Maven。Follower Block、Registry发布限制和其他WIP保留，不提交。
- [ ] 用户批准本地最小接入（不再前置双构建/无关服务）：单候选 `/var/folders/h9/drjh32n10x170kpxrbpl_1p00000gn/T/follower-minimal-pjpv2eup`，16访问器测试package exit0/4m19s；binary SHA d276c2afb1074622c007d66fa83c00944790ece795145e3c671004f4cbcc536b，consumer POM aa3d7d52f7289b54e559cd714ea6aa8e7b35dbcaa1f558368d66d505f8f801dc；安装仅 `/tmp/jraft-artifact-m2`，消费者compile/23JAR来源扫描exit0，Node/NodeImpl/FSMCallerImpl唯一私有来源（`/tmp/follower-minimal-consumer.log`）。原始951manifest字段误填已更正b56c1249...，43recipefixture通过，不冒称完整recipe验收。接入设计reviewer `dea713a2` Accept：root POM仅管理私有GAV，im-raft POM声明，Runtime使用getAppliedConfiguration(commandTimeout) stable-only，joint失败关闭，timeoutcause及interrupt标志保留。精确范围：根POM、im-raft/{pom,Runtime,RaftMembership,RuntimeTest,README}、Broker实际发生FSM重入的continuation及对应配置/定向测试（先RED再最小既有executor切换，禁止CallerRuns）；不改领域多数、冷却或访问器守卫。先 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-raft -am -Dtest=RaftRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test`，再实际受影响Broker定向测试、原 `AutomaticFailureTlsHaTest` 三项、依赖单实现检查。长验证后台串行，不跑Compose/无关应用；独立代码Review后记录真实HA，生产发布/复现性/最终门禁仍未完成，A5 active/WIP=1，不提交。
- [x] Follower 最小本地修复验证闭环（不关闭制品生产交付或 A5）：Runtime reviewer `a682f997` scoped Accept，Runtime 22 项、Broker聚焦87项通过。HA初轮3errors已明确归因 fixture 半停机：Bolt默认不管理存量连接，Raft停后请求仍进入Handler→PROTOCOL/UNKNOWN，非冷却/时钟停滞；诊断reviewer `14b8f8ac` Accept。精确测试范围 `im-test/im-routing-ha/src/test/java/com/co/kc/imchat/routingha/AutomaticFailureTlsHaTest.java`：启动前启用连接管理，跟踪实际acceptedchannels并验证停止后关闭/监听拒绝；清理覆盖失败与重复停止；ACK采用与领域一致的sha256HexLongs([1,2,3])及派生count，并核对冻结checkpoint。停机边界真实RED（2channels3秒未关闭），随后修复checksum/重复清理，无生产分类放宽；完整fixture reviewer `cf4028e1` scoped Accept。最终命令（Zulu21.0.10/Maven3.8.4、MAVEN_SKIP_RC=1、空settings）`mvn -B -ntp -Duser.name=kc -s "$S" -gs "$S" -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-test/im-routing-ha -am -Dtest=AutomaticFailureTlsHaTest -Dsurefire.failIfNoSpecifiedTests=false test` exit0/55.6s，3tests/0failures/errors/skipped，日志 `/tmp/follower-ha-three-final.log`、`.exit`、`.metadata.json`、`.audit.json` 和 `-reports/`。测试类SHA83d4df41de1772d9eb7f38659ca9ec0ee5b26dbfe752f643a498fbba647a3ee8；前后工作树指纹196393ed84678c7ecbd71c7bfd59dcd8aebad01c4b26ab35ae7238e734644ba2，实际core唯一私有来源SHA d276c2afb1074622c007d66fa83c00944790ece795145e3c671004f4cbcc536b。故障注入期间有预期连接/replicator ERROR，不宣称零ERROR日志；未发现原Notleader/重入信号。原Follower行为Block仅在本地候选/三HA范围解除，GatewayACK为替代边界不是GatewayE2E；私有Registry、双构建/输入锁、外部有效模型证据、完整门禁仍保留，状态回填使前述工作树指纹过期，不冒称全仓验收。用户要求不启动无关验证，本轮无额外服务/上游/recipe运行、不提交。
- [x] Follower 本地收尾文档/模块检查：仅定点同步 `im-broker/ARCHITECTURE.md`、Server README、im-raft README、原私有制品设计，保留历史证据，明确本地三HA通过、stable-only、无quorum/跨快照原子性保证、禁止FSM重入及未发布私有GAV的构建前提。受控命令 `mvn -B -ntp -Duser.name=kc -s "$S" -gs "$S" -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-raft -am test` exit0/15.665s，fresh报告 im-raft51（Runtime24/Settings25/Membership2）+common37=88，零failures/errors/skipped；日志 `/tmp/follower-closeout-raft.log`、`.exit`、`.metadata.json`，JAR候选SHA d276c2...一致。相对此前Runtime22新增计数来自当前共享工作树，不归本任务新增测试；本轮代码未变化，仅设计文档并发更新，明确 scoped证据非Harness全树验收。磁盘3.8→4.2GiB，无ENOSPC；遵用户最小执行原则未启动架构两项、Compose、startup/E2E/HA重复运行、全Broker/上游/recipe。readiness/diffcheck通过；FSM线程guard本轮只mock传播不冒称真实线程边界覆盖。架构定向/完整门禁、发布与复现性继续未完成，不关闭A5、不提交。
- 用户追加指出 Adapter 不应接收专门构造的 `BrokerHealthObservationRequest` 请求包装；当前仅提出显式参数和 Adapter 内转换 SDK Params 的理解，等待用户确认，未修改此契约。下一步确认范围，再继续既有 A5，不另开 active 任务；其他 WIP、Follower Block 和历史缺口保留。

独立实现 reviewer `f7598852` scoped CODE Accept、findings none；仅解除本两项增量代码 Review 检查点，不关闭全局 Follower/library Block。最终门禁由本协调者在冻结仓库写入后顺序执行 `./scripts/verify.sh architecture`、`behavior`、`e2e`、`startup`、`quick`、`clean`、`full`，日志 `/tmp/auto-blocker-final-<mode>.log`；门禁期间不回填文档，所有结果结束后记录实际结果及回填导致的 fingerprint 过期，避免循环复跑。

### A5 增量：Bolt连接层认证测试原型

- [x] 用户批准原型，设计 reviewer `0461448d-b90c-462` Accept，事实源 [Bolt连接层身份认证原型](../../design-docs/2026-10-20-bolt-connection-authentication-spike-design.md)。仅新增 `im-plugin/im-bolt/src/test/java/com/co/kc/imchat/plugin/bolt/BoltConnectionAuthenticationSpikeTest.java`（必要测试辅助同文件私有类），复用/独立生成真实证书；不改production/POM/deps/现有TLS规则。文档设计/index/本计划/PROGRESS，结果后README注明测试边界。A5 active/WIP=1、其他WIP保留，不提交。
- [x] 原生无gate两关键回归RED2项/2failures，暂停窗口attempted2/forwarded2/handler0，错误身份handler1；默认factory+Channel gate GREEN6项无失败错误。命令 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltConnectionAuthenticationSpikeTest -Dsurefire.failIfNoSpecifiedTests=false test`，负例加 `-Dspike.gate=false` 并限两个方法；日志 `/tmp/bolt-connection-spike-{red,green}.log`/.exit/counts，复现与边界 `/tmp/bolt-connection-spike-recovery.md`。真实TLS+公开hook、地址身份长度前缀池key、Channel gate独立attempted/forwarded计数，未改production或fork反射。
- [x] 独立 reviewer `67e6d4d5-1e52-4a6` 首轮Revise2项（认证负例异常过宽、关闭完成/部分初始化清理证据），修正为cause链SecurityException精确原因、关闭future完成成功/!open与构造清理，限制未注入/未测路径措辞；第二轮scoped Accept/findings none。复跑reviewGREEN6/nativeRED2fail/module26pass `/tmp/bolt-connection-spike-review-{red,green,module}.{log,exit,counts}`，父插件全测exit0 `/tmp/bolt-connection-spike-parent-module.log`；git diff --check通过。README新增仅测试原型定位，设计记录未完成退出边界。
- [ ] 未闭环退出条件：仅显式manager.remove后重建，不含自然失效/后台Reconnector；未覆盖证书轮换、其他ctx.write路径、完整端到端预算、初始化失败注入。原型能力结论有界，生产迁移保持未批准。MAVEN_OPTS追加既有隔离repo后architecture exit0 `/tmp/bolt-connection-spike-architecture.log`。首轮quick因新增System.out统计触发drift，删除输出保留断言、不加例外；后续模块出现旧channel池清理尚未完成的测试竞争，真实旧版反证exit1 `/tmp/bolt-connection-spike-closure-counterfactual-2.log`。加旧channel事件循环有界barrier与旧pool移除断言后重建，未加timeout/弱化close断言；focused两轮6pass/module两轮26pass `/tmp/bolt-connection-spike-closure-{focused,module}-{1,2}.*`，reviewer `67e6d4d5` scoped Accept，仅测试同步修正非生产池race修复。最终quick exit0/445秒、clean exit0/16秒；full exit17/510秒，日志 `/tmp/bolt-connection-spike-final-<mode>.log`，Harness worktreeStable=true。full已执行249项无失败/错误/跳过后，routing-ha Docker构建拉取apache/skywalking-java-agent:9.7.0-java21的匿名token网络i/o timeout，未进入完整Maven verify；未改镜像/跳过HA或关闭缺口。本状态回填使全树证据指纹需刷新，不宣称当前完整交付。低空间/并发/全局既有失败分开归因；A5 active/WIP=1，不以原型测试通过宣称生产接入或全部退出条件完成。

### A5 增量：认证调用可读性整理

- [x] 用户批准试用，设计 reviewer `b089e8c6-68d3-4a6` Accept，设计首节为事实源。生产仅 `im-plugin/im-bolt/.../core/BoltClientInvoker.java` 两私有helper/局部命名/步骤注释；普通invoke及所有public签名不改，不新增类型/兼容壳。冻结 `/tmp/BoltClientInvoker.before-auth-readability.java`；其他WIP保留，A5 active/WIP=1，不提交。
- [x] 基线 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am '-Dtest=BoltAuthenticatedFailureTest,BoltAuthenticatedTlsTest' -Dsurefire.failIfNoSpecifiedTests=false test` exit0，改后同命令GREEN exit0 `/tmp/bolt-auth-readability-{baseline,green}.log`；纯迁移不伪造新增行为RED。普通invoke逐字等价程序核对通过，限定diff `/tmp/bolt-auth-readability.diff`，独立 reviewer `46eb3be4-ddf5-4c2` scoped Accept/findings none。准确采样数为起点一次+六次remaining（caller五次/helper握手一次），先前设计review称总五次不准确，仅文字纠正，不改实际顺序。顶层Timeout优先、SSL链、传输类型、中断/消息/cause、同连接均已复核。
- [x] `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test` exit0/20项无失败错误 `/tmp/bolt-auth-readability-plugin.log`；同隔离repo MAVEN_OPTS前缀architecture exit0 `/tmp/bolt-auth-readability-architecture.log`，git diff --check通过。真实TLS未mock替代。
- [ ] 最终quick exit1 `/tmp/bolt-auth-readability-final-quick.log`：验证期间工作树变化，Harness不能绑定单一指纹，证据无效；clean exit0 `/tmp/bolt-auth-readability-final-clean.log`。按前序失败停止full，待独占稳定窗口恢复quick/clean/full，不覆盖其他会话WIP或重试掩盖变化。其他WIP/发布与全局验收缺口保留，无新Harness例外，A5 active/WIP=1、不提交。

### A5 增量：观察请求与响应角色命名

- [x] 用户批准，设计 reviewer `2f8cd490-5634-4ed` Accept，首节设计为事实源。生产范围SDK observation接口/Probe-Report Params/ReportResult-Row、Server Probe-ReportQuery/Report-ObservationDTO、三组Transformer及Adapter/App/准入实际caller；同步直接测试/HA。probe响应字段不改，RPC/Raft不改。caller清单 `/tmp/observation-wire-names-callers.txt`，冻结 `/tmp/observation-wire-names-before/`；文档SDK/Server README/Architecture/设计/计划/PROGRESS，同版本成组部署无alias，A5 active/WIP=1、不提交。
- [x] SDK与内部Query/DTO新API均compileRED exit1（缺新getter）；最小迁移及重复聚焦reactorGREEN153项/0failures/errors/skipped exit0 `/tmp/observation-wire-names-{red,green}.log`及counts。23Java文件manifest/diff `/tmp/observation-wire-names-files.txt`、`/tmp/observation-wire-names.diff`；JSON新字段/旧字段不输出或不alias，原身份/集合/时长规则保持。worker readiness工具链通过但因父文档并发指纹变动exit1，父先前readiness exit0。未改POM/install或协议operation。
- [x] 用户后续批准纯命名维护：AppTransformer probeFrom参数localId→brokerId，reportFrom→healthReportFrom/localId→observerBrokerId，rowFrom→healthObservationFrom/now→snapshotAt，同步生成映射表达式/App caller/PrecisionTest；无时间采样或算法改动。父 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am '-Dtest=BrokerHealthObservationPrecisionTest,BrokerHealthObservationHandlersTest,BrokerAutomaticFailureObservationTest,BrokerObservationQueryTest' -Dsurefire.failIfNoSpecifiedTests=false test` exit0 `/tmp/health-transformer-names-focused.log`，git diff --check通过。
- [x] 用户批准小维护：ForceDeactivateBrokerCommand.digest内校验force reason，App删除同一校验；共享摘要工具/合法canonical/hash不变。新ForceDeactivateBrokerCommandTest对null/空白4项真实行为RED（5项/4failures）`/tmp/force-digest-red.log`；GREEN命令 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am '-Dtest=ForceDeactivateBrokerCommandTest,BrokerFailureRecoveryAppServiceTest,BrokerClusterAppServiceChangeTest' -Dsurefire.failIfNoSpecifiedTests=false test` exit0 `/tmp/force-digest-green.log`，diffcheck通过。构造命令本身仍不校验，不新增兼容壳或异常类型。
- [ ] 合并独立限定diff复审及隔离repo reactor/HA编译/architecture/behavior/quick/clean/full待执行。既有full/Harness超时及发布缺口保留，不弱化规则，A5 active/WIP=1，不提交。

### A5 增量：健康观察字段语义

- [x] 用户批准，设计 reviewer `c21875b3-8361-4b1` Accept。精确范围 domain BrokerHealthObservation字段/builder/JSON/getter/注释，domain ObservationService、support Tracker、Adapter/App Transformer及真实领域caller测试（含precision/HA）；SDK及Query/DTO字段保持。29caller扫描 `/tmp/health-observation-model-callers.txt`。文档设计/Architecture/README/计划/PROGRESS，冻结 `/tmp/health-observation-model-before/`。无Raft/wire格式变更，不动其他target/reporter业务字段，A5 active/WIP=1，不提交。
- [x] worker新领域API compileRED32条缺符号诊断exit1/0tests；GREEN164项exit0（worker用了jacoco.skip，非最终门禁），HA test-compile exit0。父会话不跳覆盖率重跑同聚焦命令exit0 `/tmp/health-observation-model-parent-green.log`。13Java文件manifest/diff `/tmp/health-observation-model-files.txt`、`/tmp/health-observation-model.diff`，JSON新字段/旧名不alias、三态wire映射及原校验覆盖。独立 reviewer `a9e936a6-6d50-41e` scoped Accept/findings none，含父会话evaluate五步骤注释与observer局部命名增量。
- [x] 用户后续批准纯命名维护：RequiredObservationReports→ObservationQuorum（无alias），Service局部quorum/latestObservations/observerIds；Decision字段requiredReports及JSON component名保持，只有类型变化。`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am '-Dtest=BrokerFailureObservationServiceTest,BrokerClusterTest,BrokerHealthObservationTest,BrokerHealthObservationPrecisionTest' -Dsurefire.failIfNoSpecifiedTests=false test` exit0 `/tmp/observation-quorum-focused.log`，旧Java类型/局部名扫描无残留，git diff --check exit0。不改变多数计算、异常消息或wire，未宣称新增行为RED。
- [x] 用户另批准Decision字段 requiredReports→quorum、reportingVoters→observerIds；同步App/领域测试，排序/不可变拷贝/资格与异常消息保持。新增BrokerFailureObservationDecisionTest覆盖新访问器、排序、输入隔离、不可变、新JSON往返及旧字段缺席。修正测试工具API名称后取得仅缺新访问器的compileRED exit1 `/tmp/observation-decision-names-red.log`；`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am '-Dtest=BrokerFailureObservationDecisionTest,BrokerFailureObservationServiceTest,BrokerFailureObservationRoundTest,BrokerAutomaticFailureFlowTest' -Dsurefire.failIfNoSpecifiedTests=false test` GREEN exit0 `/tmp/observation-decision-names-green.log`，git diff --check通过。SDK/Raft格式不改，领域JSON组件改名，无旧别名。
- [x] 用户确认保留for循环，仅整理最终quorum回调命名：proof/latest/latestMembership→quorumRead/currentCluster/currentMembership，target/decision/reporters/identity/removalDecision/registered→brokerNode/observationDecision/observerIds/operationKey/clusterDecision/registrationResult；补背景变化作废冷却注释，不提取Context而改变短路求值、不改日志key/行为。`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am '-Dtest=BrokerFailureObservationRoundTest,BrokerFailureObservationStateTest,BrokerAutomaticFailureObservationTest,BrokerAutomaticFailureFlowTest' -Dsurefire.failIfNoSpecifiedTests=false test` exit0 `/tmp/reconcile-current-names-focused.log`；git diff --check通过。
- [x] 用户批准快照局部变量阶段命名 initialCluster/observedCluster/confirmedCluster/confirmedMembership；精准重命名等价核对通过，四观察测试聚焦exit0 `/tmp/cluster-stage-names-focused.log`，尾行空白清理后diffcheck通过。随后批准自动故障ID规则归已有值对象 `ClusterOperationId.forAutomaticFailure(clusterId, brokerId, epoch)`，App删operationKey并以operationId.value生成原摘要/日志，原字符串格式不变，无新生成器/兼容壳。新增ClusterOperationIdTest的API compileRED `/tmp/automatic-operation-id-red.log`，GREEN `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am '-Dtest=ClusterOperationIdTest,BrokerFailureObservationRoundTest,BrokerAutomaticFailureFlowTest' -Dsurefire.failIfNoSpecifiedTests=false test` exit0 `/tmp/automatic-operation-id-green.log`，稳定重试/cluster-broker-epoch变化/原格式断言通过，git diff --check通过。
- [ ] 最终隔离repo模块/reactor/architecture/behavior/quick/clean/full仍未重跑，既有full失败及Harness自测超时缺口保留，低磁盘/并发变化不隐去，A5 active/WIP=1、不提交。

### A5 增量：报告资格与冷却职责分离

- [x] 用户批准四参数evaluate，设计 reviewer `a902b2d6-2902-497` Accept；设计首节为事实源。生产仅领域ObservationService删冷却/两参构造、State删无caller elapsed API/App调用删最后参数/配置注入同步、领域决策Javadoc；所有直接测试构造/precision/HA caller同步。Properties cooldown保留，仅State持有，不加wrapper/接口/Service。基线 `/tmp/evaluate-four-before/`，其他WIP保留，A5 active/WIP=1，不提交。
- [x] readiness exit0；新API compileRED25条独立诊断/0项测试exit1，GREEN21suites/158项无失败错误跳过exit0，日志 `/tmp/evaluate-four-{red,green}.log`及.exit/counts。准确GREEN命令 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am '-Dtest=*Observation*Test,*FailureFlow*Test,*FailureProbe*Test,*PeerAdapter*Test' -Dsurefire.failIfNoSpecifiedTests=false test`。15文件manifest `/tmp/evaluate-four-files.txt`、限定diff `/tmp/evaluate-four.diff`；State advance阈值/回退与应用冷却前/换届冷却零evaluate断言保留，precision/HA直接caller同步。
- [x] 独立 reviewer `0c7bb9b7-2938-42e` 中止恢复后 scoped Accept/findings none。主会话同隔离repo `-pl im-broker/im-broker-server -am test` exit0 `/tmp/evaluate-four-reactor.log`，`-pl im-test/im-routing-ha -am test-compile -DskipTests` exit0 `/tmp/evaluate-four-ha-compile.log`；MAVEN_OPTS追加 `-Dmaven.repo.local=/tmp/jraft-artifact-m2` 后 architecture exit0 `/tmp/evaluate-four-architecture.log`、behavior exit0/41项 `/tmp/evaluate-four-behavior.log`。Review只读与reactor时间重叠，无并发Maven，不称全部在Review返回后才执行；未改私有制品/POM/其他WIP。
- [ ] 最终同repo前缀quick exit0/509秒、clean exit0/18秒，full exit1/82秒，日志 `/tmp/evaluate-four-final-<mode>.log`，Harness worktreeStable=true；full未取得测试计数（0），原日志仅保留警告/报告路径，无足够失败定位证据，不伪称HA或业务测试失败。单独诊断 `MAVEN_OPTS追加隔离repo bash -x ./scripts/test-harness.sh` 在300秒工具时限超时 `/tmp/evaluate-four-harness-diagnosis.log`，未完成/未确定full根因；后续从Harness自测及full runner定位，不改规则或语义分数。此记录回填使全树fingerprint需刷新，必要门禁未全过。移除逐目标mono采样不是全行为等价；未新增共享规则/Harness例外，其他WIP/私有制品发布与全局验收缺口保留，A5 active/WIP=1，不提交。

### A5 增量：观察起始本机任期读取

- [x] 用户批准试用，独立设计 reviewer `900ac3d5-7bfd-4a4` Accept；自动观察设计首节为事实源。精确生产范围 im-raft/core/RaftRuntime localLeaderTerm、本机LocalBroker.getLeaderTerm、App.reconcileFailures首次quorum替换；直接Runtime/Adapter/App观察测试与fixture同步。无额外DTO/依赖变更，advanceEpoch/Lease/submit不改，文档 im-raft README/Broker Architecture/Server README/RELIABILITY/设计/计划/PROGRESS。快照 `/tmp/local-leader-term-before/`，其他WIP保留，A5 active/WIP=1，不提交。
- [x] readiness exit0；新Runtime API compile RED（缺localLeaderTerm）、旧App冷却期行为RED1项/1failure（原先发2次quorum）。最终Runtime聚焦24项、Broker聚焦56项exit0，日志 `/tmp/local-leader-term-{red,red-behavior,green-runtime,green-broker,green}.log`及.exit。Runtime callback起止/两种FSM错误/无ReadIndex、Adapter模拟与真实接线、冷却/阻塞/不一致零quorum、空term失效/revision变更/跨任期丢弃/跨线程最终quorum失败释放/恢复不读term均覆盖。初次Broker learner测试1项Applied configuration unavailable错误，原命令重跑通过，未改membership算法，不隐去稳定性缺口。
- [x] 独立 reviewer `1a99b622-4037-481` 中止恢复后 scoped代码Accept/findings none，Architecture旧“确认领导权”用语修为本机任期可读。九Java文件manifest `/tmp/local-leader-term-files.txt`、快照 `/tmp/local-leader-term-before/`、限定diff `/tmp/local-leader-term.diff`，advanceEpoch/Lease/submit/membership原样。`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am test` exit0 `/tmp/local-leader-term-reactor.log`；同repo `-pl im-test/im-routing-ha -am test-compile -DskipTests` exit0 `/tmp/local-leader-term-ha-compile.log`。使用既有隔离制品路线，不安装默认仓库或改POM；Review只读与reactor时间重叠，无并发Maven。
- [x] `MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh architecture` exit0 `/tmp/local-leader-term-architecture.log`，同前缀behavior exit0/41项 `/tmp/local-leader-term-behavior.log`。
- [ ] 最终同隔离repo通过MAVEN_OPTS运行quick exit0/452秒、clean exit0/16秒、full exit1/386秒，日志 `/tmp/local-leader-term-final-<mode>.log`，Harness记录worktreeStable=true。full当前routing-ha 6项/3failures，AutomaticFailureTlsHaTest三场景均在Node.stop/stopRpcAndAssertClosed断言 `managed accepted channels before stopping ...` 为空失败，XML `im-test/im-routing-ha/target/surefire-reports/TEST-com.co.kc.imchat.routingha.AutomaticFailureTlsHaTest.xml`；不是旧Not leader错误，不能直接归为已知无关失败，需独立调查连接预热/追踪及停机边界与本机任期改动的关系。此增量未改HA测试或跳过断言，不宣称完整HA/制品验收。记录回填会使全树fingerprint需刷新；其他WIP保留，A5 active/WIP=1，不提交。

### A5 增量：移除整轮耗时上限

- [x] 用户批准，设计 reviewer `d78ece3a-da91-47b` Accept；范围 AppService删elapsed及整轮预算/无用timeout注入，配置及所有App构造caller同步、Round测试慢轮次仍合格与过旧拒绝；Peer单次RPC配置不改，冷却observedAt保留。文档Architecture/ServerREADME/RELIABILITY/设计/计划/PROGRESS。不改POM/私有制品或其他WIP，A5 active/WIP=1，不提交。
- [x] worker聚焦模块（无-am）可运行，真实RED20项/2预期failures，旧预算阻止慢轮次evaluate；GREEN167项/0failures/errors/skipped exit0，日志 `/tmp/no-round-budget-{red,green}.log`/.exit。构造caller23处全迁10参，18文件清单 `/tmp/no-round-budget-files.txt`、基线 `/tmp/no-round-budget-before/`、限定diff `/tmp/no-round-budget.diff`。Round fixture原Set顺序敏感时间残留已窄修：最终墙钟设置前移且每次report重置额外ns，不弱化生产规则；保留并发完成链缩进WIP。
- [x] 独立 reviewer `10cdd5cb-73ed-463` 首轮代码Accept/文档Revise（Architecture遗留本轮耗时语句），修正唯一句后第二轮 scoped Accept/findings none。git diff --check、`./scripts/verify.sh clean` exit0 `/tmp/no-round-budget-clean.log`。
- [ ] Broker reactor `mvn -q -pl im-broker/im-broker-server -am test` exit1 `/tmp/no-round-budget-reactor.log`：未触及im-cache JetCacheLocalValuesTest容量预期<=2实际4，按前序失败停止architecture/behavior/quick/full。worker单独HA test-compile exit1 `/tmp/no-round-budget-ha-compile.log`：安装Broker旧jar11参与源码10参不一致，未install/改POM；需在依赖拥有者解决私有JRaft制品可用性后重跑reactor和HA reactor编译，不新增兼容构造。其他WIP/Follower与历史依赖缺口保留，A5 active/WIP=1，不提交。

### A5 增量：观察时间用途拆分

- [x] 用户批准试用、设计 reviewer `091d1def-a76a-446` Accept，设计首节为事实源。Java范围 AppService.reconcileFailures 时间点、直接 Round/State/Flow 测试；新snapshot双时源邻近采样，evaluatedAt一次，取消roundAt及其加elapsed推算。Adapter/领域不改，不新增时源抽象。快照 `/tmp/observation-time-split-before/`；文档仅Broker Architecture/Server README/设计/计划/PROGRESS。A5 active/WIP=1、其他WIP/Follower Block保留，不提交。
- [x] worker对旧实现真实行为RED：`mvn -q -pl im-broker/im-broker-server -Dtest=BrokerFailureObservationRoundTest,BrokerFailureObservationStateTest,BrokerAutomaticFailureFlowTest test` exit1、28项6failures/0errors；最小实现GREEN exit0、28项；扩展 `mvn -q -pl im-broker/im-broker-server '-Dtest=*Observation*Test,BrokerAutomaticFailureFlowTest,BrokerFailureProbeTest,BrokerPeerAdapterTest' test` exit0、146项。日志 `/tmp/observation-time-split-{red,green,focused-green}.log`/.exit。覆盖同一evaluatedAt、freshness±1ns、前跳/回拨、起点后本机样本与UNKNOWN断档、整轮负elapsed/超预算。早期两次GREEN测试static Instant stub干扰已用fixture额外ns计数纠正，最终重跑旧实现取得上述行为RED，不改生产规则。worker readiness工具通过但因共享树变化Harness exit1；父会话先前readiness exit0已执行。
- [x] 独立 reviewer `d18a31d4-62fd-423` 中止恢复后 scoped Accept/findings none，确认 elapsed预算未删除（用户仅询问未授权删除）、Adapter与协议不改；限定diff `/tmp/observation-time-split.diff`、三文件manifest `/tmp/observation-time-split-files.txt`，快照 `/tmp/observation-time-split-before/`。quiet日志历史精确计数由worker汇总，reviewer独立确认退出码与当前Round20/State7 XML，不冒称独立重建全部历史计数。
- [ ] 父会话 `mvn -q -pl im-broker/im-broker-server -am test` exit1 `/tmp/observation-time-split-reactor.log`：im-raft依赖 com.co.kc.thirdparty.sofa:jraft-core:1.4.1-imchat.1 无法从当前Maven仓库解析，该依赖变更属于其他私有制品任务，本增量未触及POM或安装私有制品，不降级/替换依赖。停止后续architecture/behavior/quick/full，待该拥有者完成依赖可用性后恢复；clean单独执行 `/tmp/observation-time-split-clean.log`。git diff --check通过。其他WIP/并发指纹/缓存容量/Follower缺口分别保留，无新Harness例外，A5 active/WIP=1，不提交。

### A5 增量：报告 Adapter 请求前采样

- [x] 用户批准试用，独立设计 reviewer `dd5a86a1-79e3-481` Accept；设计首节明确取代统一roundAt并接受独立墙钟采样误差。Java范围 Adapter.fetchHealthReport五参/请求前now、App调用、AdapterTransformer命名，以及所有直接测试fetch签名/原round基准断言；不改其他算法或协议。文档 Broker Architecture/Server README/设计/本计划/PROGRESS。快照 `/tmp/report-local-now-before/`；其他WIP保留，A5 active/WIP=1，不提交。
- [x] 新API compile RED exit1、20条五参API错误，日志 `/tmp/report-local-now-red.log`；观察聚焦 `mvn -q -pl im-broker/im-broker-server '-Dtest=BrokerPeerAdapterTest,BrokerFailureObservation*Test,BrokerAutomaticFailure*Test,BrokerHealthObservation*Test,BrokerObservation*Test,BrokerFailureProbeTest' -Dsurefire.failIfNoSpecifiedTests=false test` exit0、137项无失败错误跳过，日志 `/tmp/report-local-now-green.log`，.exit/.counts文件保留。首次GREEN静态Instant mock初始化额外交互已修正，只验证now调用并在初始化后清除mock交互，不改生产规则。新用例证明入队不采样/RPC前一次采样/多行同起点/RPC耗时计入年龄且失败窗口不延长，原统一基准用例改为新职责，校时误差显式测试。七文件清单/限定diff `/tmp/report-local-now-files.txt`、`/tmp/report-local-now.diff`。
- [x] 独立 reviewer `628cf859-835d-477` scoped Accept/findings none；Broker reactor `mvn -q -pl im-broker/im-broker-server -am test` exit0 `/tmp/report-local-now-reactor.log`，architecture exit0 `/tmp/report-local-now-architecture.log`，behavior exit0/41项 `/tmp/report-local-now-behavior.log`。只读Review与reactor时间重叠，未并发Maven、不冒称全部在Review返回后才运行。
- [ ] 本记录后冻结代码/文档，运行 quick/clean/full `/tmp/report-local-now-final-<mode>.log`；最终结果以工具/Harness及回复为准，文档回填会使全树指纹需刷新。指纹变化/缓存容量/既有Follower Block分别归因，不弱化门禁，不新增Harness例外，A5 active/WIP=1/其他WIP保留，不提交。

### A5 增量：报告整轮时间基准

- [x] 用户批准试用，设计 reviewer `68216f03-79b3-4cc` Accept。范围 AppService.reconcileFailures 双基准紧邻采样/单次传 roundAt，PeerAdapter/AdapterTransformer 参数名；测试 RoundTest 新增延迟请求与freshness阈值，沿用Tracker快照/回拨测试。文档 Broker Architecture/Server README/设计/计划/PROGRESS。不改协议或domain算法，已接受后发报告更保守失效。快照 `/tmp/report-round-before/`，A5 active/WIP=1，不提交。
- [x] `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerFailureObservationRoundTest,BrokerHealthObservationTrackerTest' -Dsurefire.failIfNoSpecifiedTests=false test` 行为RED exit1、17项3failures（旧实现传100.100/100.150而非100），GREEN exit0/17项；扩展聚焦 Round/Tracker/Peer/Precision/AutomaticFlow/State/Probe/Service 98项exit0，准确命令与exit文件 `/tmp/report-round-{red,focused-green,green}.{log,exit}`。新增freshness等于阈值/超1ns及失败持续不足窗口3组、轮次起点后本机样本UNKNOWN回归；快照/限定diff `/tmp/report-round-before/`、`/tmp/report-round.diff`。
- [x] 独立 reviewer `dfd457fb-e334-41d` scoped Accept/findings none，文档六参数旧名requestStartedAt已同步roundAt。`mvn -q -pl im-broker/im-broker-server -am test` exit0 `/tmp/report-round-reactor.log`；Review首次中止恢复后完成，期间未并发Maven或改算法，不冒称全部验证在Review后。
- [ ] `./scripts/verify.sh architecture` exit1 `/tmp/report-round-architecture.log`：验证期间工作树变化，Harness不接受绑定证据，停止后续behavior/quick/full，待独占稳定窗口恢复。clean单独执行 `/tmp/report-round-clean.log`，git diff --check检查；其他会话WIP/指纹不稳及Follower Block分别保留，不弱化门禁，无新增通用规则/Harness例外。A5 active/WIP=1、不提交。

### A5 增量：报告请求职责收敛

- [x] 用户批准“先处理其他，requestStartedAt 再考虑”，设计 reviewer `dc8b6bb6-c757-45e` 第二轮 Accept；首节为事实源。基于期限删除增量，范围仅 AppService 请求构造/局部负耗时断言、PeerAdapter.fetchHealthReport 显式参数/SDK 构造、Adapter Transformer 删除两项请求映射、删除 domain BrokerHealthObservationRequest/直接测试，以及相关 Adapter/应用 caller tests；Interface 映射不删。requestElapsed/requestStartedAt 公式/采样位置与最终elapsed检查保持。无新包装，A5 active/WIP=1，不提交。
- [x] 六参数 fetch API compile RED exit 1 `/tmp/health-request-red.log`；最小实现后观察聚焦 GREEN exit 0，113项（SDK6/Server107）无失败/错误，日志 `/tmp/health-request-green.log`，准确命令/恢复 `/tmp/health-request-worker-recovery.md`。本增量9个Java路径含2处删除，manifest `/tmp/health-request-files.txt`，快照 `/tmp/health-request-before/`，限定diff `/tmp/health-request.diff`。真实SDK Params捕获身份/版本/nonce/目标集合，队列测试证明任务执行时生成一次nonce，年龄归一化精确断言保留。旧 Request 与请求Transformer映射引用清零，用户WIP保留。与期限删除增量合并独立复审 reviewer `92a0a6c5-31ab-43f` scoped CODE Accept/findings none，正式交付 Revise仅保留证据缺口。

### A5 增量：观察期限移除与报告方法命名

- [x] 用户批准、设计 reviewer `296f36fe-e82b-4c1` Accept，自动观察设计首节为契约事实。准确 caller 清单 `/tmp/health-contract-callers.txt`；生产范围 SDK observation request/Probe/Report Params/Result、Server 观察领域请求/Query/DTO/三组 Transformer、PeerAdapter/AppService 两个报告方法、PeerAdmission、两个配置装配及 Handler；对应直接测试和 routing-ha AutomaticFailureTlsHaTest/fixture 同步。不得改通用 Bolt、Raft、Gateway/Lease 期限或依赖；RPC operation 名保持。文档同步 Broker Architecture/Server README/SDK README/RELIABILITY/SECURITY 及本计划/PROGRESS。其他 WIP 保留，A5 active/WIP=1、不提交。
- [x] worker 冻结 caller 快照 `/tmp/health-contract-before/`，33个Java文件清单 `/tmp/health-contract-files.txt`、限定 diff `/tmp/health-contract.diff`；旧API对新SDK构造参数 compile RED `/tmp/health-contract-red.log`。最终聚焦命令 `mvn -q -pl im-broker/im-broker-sdk,im-broker/im-broker-server -am '-Dtest=BrokerObservationContractTest,BrokerHealthObservationRequestTest,BrokerObservationQueryTest,BrokerPeerAdapterTest,BrokerAutomaticFailureObservationTest,BrokerObservationAdmissionTest,BrokerHealthObservationHandlersTest,BrokerHealthObservationPrecisionTest,BrokerAutomaticFailureFlowTest,BrokerFailureObservationRoundTest,BrokerFailureObservationStateTest,BrokerFailureProbeTest,BrokerObservationRequestConfigurationTest,BrokerAdmissionContractValidatorTest,IdentityAccessInterceptorsTest' -Dsurefire.failIfNoSpecifiedTests=false test` exit 0，114项（SDK6/Server108），日志 `/tmp/health-contract-green.log`；首轮新增测试Mockito静态嵌套stub错误已通过预先构造Instant纠正，无生产规则弱化。覆盖JSON/record无期限、晚到只读请求接受、身份/版本/目标/归一化/预算，未变RPC operation、时源计算或用户WIP。随后用户批准报告请求包装删除，后续接其增量统一验证。
- [x] 合并实现独立 reviewer `92a0a6c5-31ab-43f` scoped Accept/findings none；主会话 `mvn -q -pl im-broker/im-broker-server -am test` exit 0 `/tmp/health-contract-reactor.log`，`mvn -q -pl im-test/im-routing-ha -am test-compile -DskipTests` exit 0 `/tmp/health-contract-ha-compile.log`。只读Review与reactor时间重叠，未并发Maven，不写成Review后才执行。未改RPC operation、Bolt/Raft或依赖，git diff --check通过。
- [ ] `./scripts/verify.sh architecture` exit 1 `/tmp/health-contract-architecture.log`：Harness检测验证期间工作树变化，不能绑定单一指纹，结果无效。停止后续 behavior/e2e/quick/full，待取得稳定独占窗口从 architecture 恢复；clean单独检查记录日志 `/tmp/health-contract-clean.log`，实际结果以工具为准。已有缓存容量历史失败/Follower Block保留，不弱化规则或覆盖其他会话改动。无新通用工程规则/Harness例外；过期只读查询/成组部署/可信对端假设为已接受风险，A5 active/WIP=1，不提交。

### A5 增量：观察状态三参数 API

- [x] 用户批准，设计 reviewer `21f20619-c93e-4aa` Accept，设计首节为事实源。精确范围 State advance、AppService 空 voters 分支及调用、领域 BrokerObservationStateTest；文档仅 Broker Architecture/Server README/设计/本计划/PROGRESS。保留接手 beginCluster 等命名 WIP；快照 `/tmp/observation-three-before/`。A5 active/WIP=1，不提交。
- [x] readiness exit 0；迁领域测试为三参数及 invalidate 恢复窗口，`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerObservationStateTest -Dsurefire.failIfNoSpecifiedTests=false test` compile RED exit 1（旧签名）。实现后 `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerObservationStateTest,BrokerFailureObservation*Test,BrokerAutomaticFailure*Test' -Dsurefire.failIfNoSpecifiedTests=false test` GREEN exit 0，日志 `/tmp/observation-three-{red,green}.log`。保留应用成员不一致/恢复及±1ns断言；独立 reviewer `dc4ad500-5c31-4a0` scoped Accept/findings none，限定 diff `/tmp/observation-three-{state,app}.diff`。
- [x] `mvn -q -pl im-broker/im-broker-server -am test` exit 0 `/tmp/observation-three-reactor.log`；此轮未复现此前缓存容量失败，不撤销历史归因。
- [ ] `./scripts/verify.sh architecture` exit 1 `/tmp/observation-three-architecture.log`：Harness 检测验证期间工作树变化，无法绑定单一指纹，结果无效；停止后续 behavior/quick/full，先取得独占稳定窗口再重跑，不覆盖其他会话改动。缓存容量失败和 Follower Block 分开保留，不扩大修复或弱化规则；A5 active/WIP=1，独立代码 Accept 不冒充完整验收。

### A5 增量：协调流程可读性收敛

- [x] 用户批准、设计 reviewer `442d0068-90fd-43c` Accept，首节设计为事实源。精确范围 AppService#reconcileFailures 四项整理及 Comparator import、Cluster 新增 hasBlockingOperation 并在 checkAdmission 复用；测试仅新增 `domain/cluster/model/BrokerClusterReconciliationQueryTest.java`。不改时间/异常/报告汇合/锁；基线 `/tmp/reconcile-readability-before/`。A5 active/WIP=1，不提交。
- [x] 新增 activeBrokers 保序及阻塞查询测试，`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterReconciliationQueryTest -Dsurefire.failIfNoSpecifiedTests=false test` compile RED exit 1（缺 hasBlockingOperation）。最小实现后 `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerClusterReconciliationQueryTest,BrokerClusterTest,BrokerClusterFailureOperationQueryTest,BrokerFailureObservation*Test,BrokerAutomaticFailure*Test' -Dsurefire.failIfNoSpecifiedTests=false test` GREEN exit 0，日志 `/tmp/reconcile-readability-{red,green}.log`。独立 reviewer `0b5c765c-4f99-4ce` scoped Accept/findings none，限定 diff `/tmp/reconcile-readability-{app,cluster}.diff`；未改报告/采样/锁/异常，git diff --check exit 0。
- [ ] Broker reactor `mvn -q -pl im-broker/im-broker-server -am test` 两次 exit 1，日志 `/tmp/reconcile-readability-reactor.log`、`/tmp/reconcile-readability-reactor-retry.log`：均为未触及 im-cache 容量测试上限2，首轮实际3、次轮4；不改该测试/生产规则，按前序失败停止 architecture/behavior/quick/full。`./scripts/verify.sh clean` exit 0 `/tmp/reconcile-readability-clean.log`，既有 untracked 范围保留。下一步应先处理独立缓存容量失败再恢复更广验证；Follower HA Not leader Block 与其他会话 WIP 保留。无新工程规则/Harness 例外，A5 active/WIP=1，不冒称完整验收。

### A5 增量：未完成故障操作查询归属

- [x] 用户批准、设计 reviewer `5a5dd0a8-a475-463` Accept；自动观察设计首节为事实源。生产范围 `domain/cluster/model/BrokerCluster.java` 新增 findUnfinishedFailureOperation，`application/cluster/BrokerClusterAppService.java#reconcileFailures` 改 Optional 调用；测试新增 `domain/cluster/model/BrokerClusterFailureOperationQueryTest.java`，不改已有应用断言；文档仅设计/本计划/PROGRESS。接手生产 `/tmp/unfinished-failure-before/`。A5 active/WIP=1、不提交。
- [x] readiness exit 0；新增测试命令 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterFailureOperationQueryTest -Dsurefire.failIfNoSpecifiedTests=false test` compile RED exit 1，同时含缺 API 与错误 DrainProgress Map fixture，日志 `/tmp/unfinished-failure-red.log`。fixture 改 List 后临时缺方法反证 compile RED exit 1 `/tmp/unfinished-failure-red-corrected.log`，不是行为 RED；初次 GREEN 又暴露 fixture 缺 Broker，补合法 Broker，未改生产不变量。最终 `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerClusterFailureOperationQueryTest,BrokerFailureObservation*Test,BrokerAutomaticFailure*Test' -Dsurefire.failIfNoSpecifiedTests=false test` exit 0 `/tmp/unfinished-failure-green.log`。新增领域13项覆盖无、唯一、全 phase、其他类型/终态、多项冲突和查询无副作用。
- [x] 独立实现 reviewer `12e121ee-fd9d-498` scoped Accept/findings none；限定 diff `/tmp/unfinished-failure-{cluster,app}.diff`。git diff --check exit 0，`./scripts/verify.sh clean` exit 0 `/tmp/unfinished-failure-clean.log`，既有 untracked 范围保留。
- [ ] Broker reactor `mvn -q -pl im-broker/im-broker-server -am test` 两轮 exit 1 `/tmp/unfinished-failure-reactor.log`、`/tmp/unfinished-failure-reactor-retry.log`，均为未触及 im-cache JetCacheLocalValuesTest.snapshotUsesJetCacheExpirationAndFrameworkCapacity 上限2，实际首轮6/次轮5；不弱化该测试、不扩大本次代码范围。按前序失败停止 architecture/behavior/quick/full，后续先解决或在独占构建窗口复核该容量失败后恢复门禁。当前 Follower membership full Block 和其他会话 WIP 保留，不修改依赖；A5 active/WIP=1，不宣称完整验收。

### A5 增量：BrokerObservationState 四参数 API

- [x] 用户批准、独立设计 reviewer `e9dfaa4f-42df-42f` Accept；设计首节为事实源。精确生产范围 `domain/cluster/model/BrokerObservationState.java`、`application/cluster/BrokerClusterAppService.java`（显式原签名构造、两处调用）；测试 `domain/cluster/model/BrokerObservationStateTest.java`、`application/cluster/BrokerFailureObservationStateTest.java`；文档 Broker Architecture/Server README、设计、本计划/PROGRESS。接手快照 `/tmp/observation-four-before/`，A5 active/WIP=1，不提交。
- [x] readiness exit 0；迁直接测试到构造 cooldown/四参数后，`mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerObservationStateTest,BrokerFailureObservationStateTest' -Dsurefire.failIfNoSpecifiedTests=false test` compile RED exit 1（旧 API 参数不匹配）。实现后 `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerObservationStateTest,BrokerFailureObservation*Test,BrokerAutomaticFailure*Test' -Dsurefire.failIfNoSpecifiedTests=false test` GREEN exit 0，日志 `/tmp/observation-four-{red,green}.log`。成员/任期/拓扑/时间回退边界保留，角色往返应用测试新增非 Leader 不读 membership 断言。生产限定 diff `/tmp/observation-four-{state,app}.diff`，git diff --check exit 0。
- [x] 独立 reviewer `96a55fd6-4b0e-442` scoped Accept/findings none，逐项核对显式构造器11参数类型/顺序及依赖赋值。`mvn -q -pl im-broker/im-broker-server -am test` exit 0、`./scripts/verify.sh architecture` exit 0、`./scripts/verify.sh behavior` exit 0（41 项），日志 `/tmp/observation-four-{reactor,architecture,behavior}.log`。只读 Review 与 reactor 时间重叠，未并发 Maven，不冒称 Review 后才运行。
- [ ] 本次记录后冻结源码/文档，执行 quick、clean、full，日志 `/tmp/observation-four-final-<mode>.log`；前序失败先归因，已知 full 的 Follower membership Not leader Block 不改依赖/弱化 HA。最终实际结果由回复及 Harness 给出，不用旧证据关闭缺口。无新增共享工程规则/Harness 例外，A5 active/WIP=1、其他会话 WIP 保留。

### A5 增量：reconcileFailures 重复守卫收敛

- [x] 用户批准、独立设计 reviewer `20c9ae16-945a-491` 第二轮 Accept；准确前提见自动观察设计首节（阶段推进不保证修改 revision）。生产仅 AppService#reconcileFailures 删除 epoch 比较及最终 voter 重算、同步注释；不改领域逻辑。测试仅 `domain/cluster/model/BrokerClusterTest.java`、`BrokerClusterConsistentVotersTest.java` 加合法提交/Begin 不变量断言；现有应用回归不改。接手生产 `/tmp/reconcile-guards-app-before.java`，A5 active/WIP=1、不提交。
- [x] 新增断言后基线命令 `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerClusterTest,BrokerClusterConsistentVotersTest,BrokerFailureObservation*Test,BrokerAutomaticFailure*Test' -Dsurefire.failIfNoSpecifiedTests=false test` exit 0，日志 `/tmp/reconcile-guards-baseline.log`；此为已有不变量验证，不伪造行为 RED。最小删除后同命令 GREEN exit 0 `/tmp/reconcile-guards-green.log`，限定 diff `/tmp/reconcile-guards-app.diff`。独立实现 reviewer `0f703978-722d-4dc` scoped Accept/findings none；完整交付 Revise，未关闭门禁缺口。
- [ ] Broker reactor 首轮 exit 1 `/tmp/reconcile-guards-reactor.log`：未触及 im-cache 容量测试预期 <=2、实际4；原命令 `mvn -q -pl im-broker/im-broker-server -am test` 重跑 exit 0 `/tmp/reconcile-guards-reactor-retry.log`，未改测试。architecture exit 1 `/tmp/reconcile-guards-architecture.log`：im-raft JaCoCo report EOFException（前轮 im-nacos Unknown block type 78 亦保留历史）；clean exit 0 `/tmp/reconcile-guards-clean.log`。磁盘 ENOSPC 中断状态回填，用户批准清理后恢复；损坏 exec 留样 `/tmp/reconcile-guards-jacoco-evidence/{im-raft,im-nacos}.exec`。无本仓 Maven 活动后 `mvn -q -pl im-plugin/im-raft,im-plugin/im-nacos -am clean test` exit 0 `/tmp/reconcile-guards-rebuild-coverage.log`；未改配置，说明此前失败与可重建产物有关，不把磁盘满推断成唯一已证实成因。`./scripts/verify.sh architecture` 重跑 exit 0 `/tmp/reconcile-guards-architecture-resume.log`，`./scripts/verify.sh behavior` exit 0、41 项批准场景 `/tmp/reconcile-guards-behavior.log`。独立 reviewer 关于 voter 文案建议已核对：步骤注释已改为完整上下文，Javadoc 的 voter 校验由完整 Context 间接保证，不额外变更。最终 `./scripts/verify.sh quick` exit 0、`clean` exit 0，`full` exit 1（410 秒），日志 `/tmp/reconcile-guards-final-<mode>.log`。full 的 AutomaticFailureTlsHaTest 6 项/3 errors，均为 NodeImpl.listPeers → RaftRuntime.membership → BrokerRaftAdapter.getConsensusMembership 的 `IllegalStateException: Not leader`，精确 XML `im-test/im-routing-ha/target/surefire-reports/TEST-com.co.kc.imchat.routingha.AutomaticFailureTlsHaTest.xml`；这是既有 Follower 实际 membership 能力阻塞的当前运行证据，未改依赖或放宽 HA。门禁冻结 fingerprint `25985aa64742037551031cb60b7fc76936de5c03f9c24a3c6ad7c3a7718a31f9`、worktreeStable=true；本状态回填使全树证据不再 fresh，不冒称当前完整交付。代码 narrow Accept 保留，下一步由既有私有制品任务解决 Follower Block 后再恢复 full，不在本增量扩大实现。其他会话 WIP、Follower Block、历史验收缺口保留。

### A5 增量：BrokerObservationState 领域归属

- [x] 用户批准、设计 reviewer `4f4464bb-d962-45c` Accept；决定及澄清见自动观察设计首节。生产精确范围：新增 `domain/cluster/model/BrokerObservationState.java`，修改 `application/cluster/BrokerClusterAppService.java` 的 import/字段/六处调用并删除私有 State；测试新增 `domain/cluster/model/BrokerObservationStateTest.java`，保留现有应用行为测试。文档同步 Broker Architecture、Server README、设计和本计划/PROGRESS。不改锁、阈值规则、采样位置、IO 或快照，A5 active/WIP=1，不提交。
- [x] readiness exit 0 后冻结 `/tmp/observation-state-app-before.java`；新增领域测试先执行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerObservationStateTest -Dsurefire.failIfNoSpecifiedTests=false test` compile RED exit 1（缺类型，非行为 RED）。最小迁移后 GREEN `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerObservationStateTest,BrokerFailureObservation*Test,BrokerAutomaticFailure*Test' -Dsurefire.failIfNoSpecifiedTests=false test` exit 0，41 项/0 failures/errors/skipped，日志 `/tmp/observation-state-{red,green}.log`；新增领域测试 5 项，保留应用原断言。App 限定 diff `/tmp/observation-state-app.diff`。
- [x] 独立 reviewer `642bc1f5-b8f6-4d4` scoped Accept/findings none，核对条件次序、typed term 值比较、双重 quorum、采样位置与 synchronized 保持；复审与主会话 reactor 顺序存在重叠，只读 reviewer 未运行 Maven，不冒称 reactor 在 Review 返回后才启动。`mvn -q -pl im-broker/im-broker-server -am test` exit 0，`mvn -q -pl im-test/im-routing-ha -am test-compile -DskipTests` exit 0，日志 `/tmp/observation-state-reactor.log`、`/tmp/observation-state-ha-compile.log`。
- [ ] `./scripts/verify.sh architecture` exit 1，日志 `/tmp/observation-state-architecture.log`：未触及 im-nacos JaCoCo report `Unknown block type 78`，未取得架构结果；按前序失败停止更广 behavior/quick/full，未弱化覆盖率规则。`./scripts/verify.sh clean` exit 0，日志 `/tmp/observation-state-clean.log`；`git diff --check` exit 0。下一步先在独占构建窗口检查并重建损坏覆盖率产物，再恢复 architecture/behavior/quick/clean/full；Follower Block/其他会话 WIP 和历史验收缺口保留。无新共享工程规则或 Harness 例外；领域依赖边界沿用已有架构检查，冷却行为由新领域测试与既有应用回归保护。

### A5 命名维护：reconcileFailures

- [x] 用户批准 `observeFailures()` → `reconcileFailures()`，仅方法名和 Javadoc；同步 Server Scheduler、SchedulerTest、Observation Round/State、Automatic Failure Flow/Observation 直接测试及 `im-test/im-routing-ha/.../AutomaticFailureTlsHaTest.java` 调用，不改逻辑或 Scheduler 类/配置。决定见自动观察设计「后续命名维护」。全仓 Java 旧方法扫描无残留，`git diff --check` 通过。
- [x] `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerFailureObservation*Test,BrokerAutomaticFailure*Test' -Dsurefire.failIfNoSpecifiedTests=false test` exit 0，日志 `/tmp/reconcile-failures-focused.log`；仅迁测试引用，不新增行为，不冒称新增 RED。
- [x] `mvn -q -pl im-test/im-routing-ha -am test-compile -DskipTests` exit 0（`/tmp/reconcile-failures-ha-compile.log`）；`mvn -q -pl im-broker/im-broker-server -am test` exit 0（`/tmp/reconcile-failures-reactor.log`）。
- [ ] `./scripts/verify.sh quick` exit 1（`/tmp/reconcile-failures-quick.log`），仍在 architecture 上游 im-nacos JaCoCo report 因 `Unknown block type 78` 失败；本轮未触及该模块/覆盖率配置，不跳过规则。`./scripts/verify.sh clean` exit 0（`/tmp/reconcile-failures-clean.log`），提示既有 untracked 范围需审查；`full` 因已归因的前序失败未执行。A5 active/WIP=1、Follower Block/其他 WIP 与完整门禁缺口保留，不提交；本次只迁方法名/注释，不新增工程规则或 Harness 例外。

### A5 增量：healthReport 响应校验收敛

- [x] 用户批准删除响应回显及后置墙钟检查；设计记录为 `docs/design-docs/2026-10-16-broker-automatic-failure-observation-design.md` 首节，独立设计 reviewer `fc7eaedb-54c7-46f` Accept。生产范围仅 `adapter/cluster/BrokerPeerAdapter.java#healthReport`，直接测试仅 `adapter/cluster/BrokerPeerAdapterTest.java`，模块事实同步 Server README；SDK、Application、Bolt、Raft 不改。接手快照 `/tmp/health-report-peer-before.java`、`/tmp/health-report-test-before.java`。A5 active/WIP=1，其他 WIP/Follower Block 保留，不提交。
- [x] readiness exit 0；先改两项接受预期，`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerPeerAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test` RED exit 1，21 项中两项因旧 context mismatch 拒绝而 error，日志 `/tmp/health-report-red.log`。删除唯一回显检查块；增加报告目标越界/数量和传输失败回归。首轮 GREEN 105 项中两项 error：新增 fixture 构造重复目标被 SDK 先行拒绝，导致 Mockito unfinished stubbing；已移除无效 fixture，不削弱生产校验，重复目标继续由 SDK 契约测试保护。
- [x] 聚焦命令 `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerPeerAdapterTest,BrokerFailureObservation*Test,BrokerAutomaticFailure*Test,BrokerHealthObservation*Test,BrokerObservation*Test' -Dsurefire.failIfNoSpecifiedTests=false test` exit 0，105 项无失败/错误，日志 `/tmp/health-report-green.log`。独立实现 reviewer `f3d52e2e-5570-4a0` scoped Accept/findings none，交付 Revise；其摘要将重复目标称为本次新增 Adapter 回归不准确，实际新增仅数量/越界拒绝，重复目标由已有 SDK 构造契约保护。Broker reactor `mvn -q -pl im-broker/im-broker-server -am test` 首轮 180 秒超时 `/tmp/health-report-reactor.log`；无残留本仓 Maven 后以 600 秒时限复跑 exit 0，日志 `/tmp/health-report-reactor-retry.log`。生产限定 diff `/tmp/health-report-peer.diff` 只有删除检查块。
- [ ] `./scripts/verify.sh architecture` exit 1，日志 `/tmp/health-report-architecture.log`：未触及 im-nacos 的 JaCoCo report 失败 `Unknown block type 78`，架构测试未取得结果；本轮未修改 im-nacos 或 JaCoCo 配置，未为放行跳过覆盖率。按前序失败停止广门禁，`behavior`、`e2e`、`quick`、`clean`、`full` 未运行；下一步检查并重建该模块损坏的覆盖率产物后从 architecture 恢复，不触及其他会话的隔离 JRaft 验证。`git diff --check` exit 0。已接受风险为不再从回显发现已认证服务端返回错误上下文的实现缺陷。无新通用规则，不扩大 Harness 例外；A5 active/WIP=1，其他 WIP/Follower Block 和完整验收缺口保留。

### A5 增量：observeFailures 完成链收敛

- [x] 用户批准、设计 reviewer `3085c5cd` Accept；决定先补入 `docs/design-docs/2026-10-17-local-lock-design.md`。接手生产快照 `/tmp/BrokerClusterAppService.before-observe-opt.java`。生产范围仅 Server `application/cluster/BrokerClusterAppService.java#observeFailures`，单一释放出口与顺序报告 Stream，不新增 helper/Template，明确 Error 边界改变。A5 active/WIP=1，其他 WIP/Follower Block 保留，不提交。
- [ ] 实施上述精确方法范围；`mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerFailureObservation*Test,BrokerAutomaticFailure*Test' -Dsurefire.failIfNoSpecifiedTests=false test`，日志 `/tmp/observe-opt-focused.log`；独立限定 diff Review，再 `mvn -q -pl im-broker/im-broker-server -am test`，日志 `/tmp/observe-opt-reactor.log`。失败先归因，父会话 Review 前不继续 full gates。
- 本增量前冻结验证 `./scripts/verify.sh quick > /tmp/observe-inline-background-quick.log 2>&1` exit 0，448 秒；Harness HEAD `477758a7aedf436c906ba85a830c7bdd09f80942`，fingerprint `b3328a76be40b3ceccd488679132a576b5c9746495026cf4dfaeef8db89a996d`，worktreeStable=true。用户新请求取代待运行 clean/full；后续源码/文档变更使该证据不再代表当前树。readiness 本轮 exit 0，日志 `/tmp/observe-opt-readiness.log`。

本增量实际证据与最终门禁冻结：

- [x] 完成精确方法修改，限定 diff `/tmp/observe-opt.diff`；方法之外生产正文保持接手快照。聚焦 `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerFailureObservation*Test,BrokerAutomaticFailure*Test' -Dsurefire.failIfNoSpecifiedTests=false test` exit 0，13 秒，36 项/0 failures/errors/skipped，日志 `/tmp/observe-opt-focused.log`。
- [x] 独立 reviewer `1fb8396b-73f2-4b4` scoped Accept，findings none。`mvn -q -pl im-broker/im-broker-server -am test` exit 0，70 秒，SDK 38 项、Server 683 项，均无失败/错误/跳过，日志 `/tmp/observe-opt-reactor.log`；此 reactor 在父会话 Review 返回前已经启动并完成，没有并发 Maven 或源码变更，不将执行顺序误写成 Review 后。`git diff --check` exit 0。
- [ ] 状态回填后冻结文档和源码，顺序运行 `./scripts/verify.sh architecture`、`behavior`、`quick`、`clean`、`full`，每项工具时限 1800 秒，日志 `/tmp/observe-opt-background-<mode>.log`；检查 full 前磁盘，不自动清理。前序失败先归因并停止广门禁；完成后只在报告中返回实际结果和 Harness 指纹，不回填引起证据过期。A5 active/WIP=1、其他 WIP/Follower Block 和历史交付缺口保留，不提交。
