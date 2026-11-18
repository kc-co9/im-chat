# Bolt 包职责与 Broker 快照归位实施计划

## 当前收口账本（2026-10-28）

> 2026-10-29 关闭：T1–T3 全部 passing，计划已移至 completed；冻结 full attempt8 与独立 quality 复审 Accept 见文末。以下原为恢复入口说明，保留为历史。
>
> 2026-10-29 更新：当前恢复入口已移至文末「Review 修订（W1–W8）与重新验收（2026-10-29）」。本节表格中的「仍阻塞」「待验收」行（私有 JRaft 缺件、必要跨模块验证缺失）已被后续 verify-lock、消费者验证与 attempt5 full 实际结果取代，原文保留为历史。

本节原为当前恢复入口，取代下文历史阶段的「等待用户 Review」「禁止恢复验收」指令；原命令、失败、Review 和证据记录保留，不改写为当前通过。用户已要求收口，允许按既定顺序推进验收，但当前前提尚未满足，不盲重跑 full。

| 分类 | 当前结论 | 下一步 |
|---|---|---|
| 已实施 | 包职责迁移、Gossip 快照归位、无调用者列表 RPC 删除，以及后续已批准的客户端/连接职责调整、命名和注释 | 不再扩展清理范围；以当前源码为最终验收候选 |
| 已实施 | 五角色固定前缀、独立 BoltTransportMode、Broker/Gateway 身份包装删除及调用方迁移 | 保留原安全校验；各轮窄测试及精确比较见追加节，不视为最终集成通过 |
| 已取代 | 可覆盖项目前缀、旧身份包装构造契约及早期生命周期中间方案 | 以关联设计后续决定和当前源码为准，不恢复中间实现；历史验证不删 |
| 仍阻塞 | 私有 `com.co.kc.thirdparty.sofa:jraft-core:1.4.1-imchat.1` 缺失 | 本轮只读检查 `/tmp/jraft-artifact-m2` 对应版本目录，只有 jar/pom 的 `.lastUpdated`，没有真实制品；需恢复正式来源的匹配制品，不替换依赖、不以旧 target 冒充编译 |
| 局部已解决 | TLS 轮换与 DECODE 阶段取证均已修正并获局部 Accept | 原失败及历史 DECODE 精确原因缺失仍保留，不倒推归因；允许推进不依赖 JRaft 的 Bolt 模块验证，不据此直接关闭 full |
| 仍阻塞 | 固定前缀、枚举和两个身份包装增量的必要 Broker 测试/跨模块编译缺失，独立实现 Review 均为 Revise、源码 findings none | 依赖恢复后补当前输入的必要验证，再提交独立复核；无源码 findings 不等于 Accept |
| 待验收 | 最终 clean/full、当前 quality、完整实现独立 Accept 和状态迁移 | 前置阻塞解除、文档定稿并冻结输入后执行；成功后才允许 passing/归档 |
| 未授权 | Git 提交、推送和部署 | 不执行 |

T1/T2 的 passing 仅指原包迁移子范围及其历史输入；不能据此声称后续完整变更集已验证。T3 已恢复为唯一 active，正在执行获授权的新补丁隔离双构建；旧阻塞与历史记录保留，未归档。

### 本轮实际推进

- TLS 局部修正获 Accept 后，继续执行 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltUnifiedInvokeTest -Dsurefire.failIfNoSpecifiedTests=false test`，exit0；当前 FQCN 全类 24 项零失败/错误/跳过，报告保存 `job tmp/decode-budget-diagnostics/class-baseline.xml`。此为当前基线，不是历史 DECODE 根因证明；正在核对冷 MTLS 一秒预算、解码 hook 与真实失败阶段的可区分性，不以扩大 timeout 或反复跑绿收口。

- readiness 本轮 exit0。无源码修改；未运行 Broker Maven 或扩大 full。
- 已执行 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am '-Dtest=BoltUnifiedInvokeTest#productionCallerBudgetAndPreparationFailureRetainLocalOrigin,BoltAuthenticatedTlsTest#caOverlapRestartSequenceAllowsNewAndRejectsRetiredAuthority' -Dsurefire.failIfNoSpecifiedTests=false test`，exit1：当前 FQCN 共 4 项，Unified 三参数通过，TLS 一项失败。TLS 再现原生 TLS 1.3 Finished 写入期间 `Broken pipe`，发生于 helper 的 startHandshake，不是业务 Handler 放行；失败断言仍保留，未改成接受任意 SocketException。DECODE 本轮通过不关闭其历史归因缺口。
- 实际日志及仅当前 FQCN 的两份 XML、result.json 保存于 `$CLAUDE_JOB_DIR/tmp/closeout-diagnostics/`；旧包 XML 不计入本轮测试数。
- 独立只读诊断 Reviewer `aefe0c40fa1d38c8e` 确认测试存在 TLS 1.3 时序假设：服务端可在客户端写 Finished 时拒绝其证书并关闭，不能要求 startHandshake 必然成功后才从 read 取得 alert。当前日志只证明写入失败，尚未证明服务端该次 native trust cause。不能直接接受 SocketException、删掉实际呈现证书链 DER 断言或新增生产 TLS seam 来过关。最小后续调查为仅测试探针固定 TLS 1.2 并保留严格呈现链证据，或通过已有 CONNECT 事件观察真实服务端 handshake future；后者单独不能证明被拒绝的呈现链，仍需可靠测试侧证据。未实施候选修正，未声称修复或覆盖 TLS 1.3 行为。
- 只读恢复调查未找到可用的原始私有 JAR/POM 或完整源码输入。已知原制品恢复路径只剩空目录；参考 `third-party/jraft-applied-membership/README.md`，新 recipe 的独立 Review、双 verify、reviewed lock 和外部有效模型证据仍未闭环，不能当作已批准替代恢复路径。下一步需要环境所有者提供匹配原件；原 JAR SHA-256 为 `d276c2afb1074622c007d66fa83c00944790ece795145e3c671004f4cbcc536b`，POM 为 `aa3d7d52f7289b54e559cd714ea6aa8e7b35dbcaa1f558368d66d505f8f801dc`。不自行下载、重建或替换 GAV。


## Sprint Contract

- 目标：落实已批准包边界与命名，补充关键职责注释，保持运行行为不变。
- 范围：Broker 快照及生产/测试引用；im-bolt core 全部11类及调用方、架构fixture、脚本路径；所属README/Architecture/设计索引/工程约定；PROGRESS和计划状态。
- 非目标：除下述获批前缀键改名外，不改协议、配置行为、认证、授权、预算和失败分类，不添加兼容壳，不关闭IDE，不提交或部署。
- 验收：既有失败架构用例转绿、Broker聚焦和Bolt模块测试通过，最终clean/full与独立Review/quality通过。
- 风险：Java包可见性、旧class残留、fixture遗漏、混合用户改动。共享快照保持唯一启动实例。

## 事实源

- 设计或产品规格：[Bolt 包职责与 Broker Gossip 运行参数归位设计](../../design-docs/2026-10-25-bolt-package-boundaries-design.md)。用户批准且独立设计Review Approved。
- `ARCHITECTURE.md`、`im-broker/ARCHITECTURE.md`、各模块AGENTS及README、CODING_GUIDE、UNIT_TEST_GUIDE。
- 原始RED：此前 full 的 LayerBoundaryTest.brokerInterfacesMustNotDependOnConcreteInfrastructure 对 infrastructure BrokerGossipProperties 的14条违规；不改规则。

## 验证分层

| 层级 | 命令 | 状态 |
|---|---|---|
| readiness | `./scripts/verify.sh readiness` | exit0 |
| Broker聚焦 | `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=BrokerGossipSettingsTest,BrokerGossipSchedulerTest,IdentityAccessInterceptorsTest,BrokerObservationAdmissionTest,BrokerGossipAdmissionIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test` | exit0；日志见下方 |
| Bolt模块 | `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test` | exit0；日志见下方 |
| 架构 | `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-test/im-architecture-test -am -Dtest=LayerBoundaryTest,BoltInvocationBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test` | exit0；日志见下方 |
| Harness | `bash scripts/test-harness.sh` | 最终full包含；需要时聚焦诊断 |
| 完整 | `./scripts/verify.sh clean`、`MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh full` | 用户已要求收口；当前被依赖与未归因失败阻塞，前置缺口解决并冻结后执行 |

## 任务状态

| ID | 目标 | 状态 |
|---|---|---|
| T1 | Broker快照归位 | passing |
| T2 | Bolt拆包、命名与注释 | passing |
| T3 | 完整验收、独立审查与关闭 | passing |

## 详细任务

### T1 Broker快照归位

文件：BrokerGossipProperties生产/测试改名model.properties.BrokerGossipSettings及全部实际调用方，Broker架构/README。

- [x] 沿用已实际失败架构作为RED；此前记录保留在 /tmp/iam-package-full.log，未额外保存全量迁移前源码副本。纯搬包按当前精确差异复核，不能声称存在未保存的原件。
- [x] 移动重命名快照/测试，更新Scheduler、配置、准入、HA测试引用并补启动快照与信任注释。
- [x] 执行Broker聚焦与架构命令，不放宽任何规则；失败先归因。

### T2 Bolt拆包、命名与注释

文件：im-bolt/core11类及全仓有效Java引用，ControlledProtocolWrite fixture、BoltInvocationBoundaryTest、scripts/test-harness.sh路径、README/文档。

- [x] client保留7类同包协作；server2类；security2类改名，最小开放共享访问面。
- [x] 同步测试/fixture/import/脚本路径；补充预算、连接生命周期、TLS事实及DEV验证注释，不改算法。
- [x] 运行Bolt模块与架构测试，确认旧core生产目录及旧类名引用清除。
- [x] 更新所属稳定文档及Harness规则映射，不添加class-name例外。

### T3 完整验收、独立审查与关闭

文件：最终冻结源码/文档、ignored验证产物及状态文档。

- [x] 冻结输入后顺序clean/full；原始退出码、报告、HA及cleanup均保存，任何失败先定位，不盲重跑。
- [x] 独立实现Review与当前匹配quality，必要缺口不降级。
- [x] 实际Accept后纯状态迁移，独立精确diff复核与廉价drift/clean/quality/cleanup/handoff，未授权提交。迁移后复核与廉价检查结果由独立审查与当前 Harness 拥有；失败时恢复 active 并重新打开 T3。

## 恢复状态

- 当前任务：none（2026-10-29 关闭，T1–T3 passing，计划已归档至 completed）。原恢复记录：T3（唯一 active）；用户再次要求收口。以本节及计划末尾最新检查点恢复；旧暂停与 blocked 描述保留为历史，不再作为当前调度指令。
- 最小下一步：none（2026-10-29 已关闭）。
- 历史最小下一步（2026-10-29 更新）：见文末「Review 修订（W1–W8）与重新验收」：Review 修复已落地，冻结后重新 clean/full、quality 与独立复审。以下原「最小下一步」为历史：
- 历史最小下一步：等待 `capture-pi-closeout-20261029/result.json` 和两轮报告，按真实退出码检查输入审计、payload 比较及外部模型证明；失败先保留证据并窄层定位。成功后仍须完成 reviewed lock、verify-lock、消费者验证及冻结输入的 clean/full、quality 和独立完整 Review，不跳过采用门槛。Docker engine 已重新实测可用，旧不可用结论仅为历史。
- 本轮收口仅汇总状态并检查依赖实物，没有运行 Maven、clean/full 或重新授予任何历史证据 freshness。
- Review 追加范围：用户批准删除无仓库内生产调用者的 `BrokerClient.listBrokers()` RPC 整链，D6 独立设计 Review a61ffe608a987aada Approved，无必须修改项。恢复最小清理，D6 当时不触碰前缀或其他 Bolt 客户端逻辑；clean/full 仍暂停。readiness 沿用父本轮 exit0，日志 `$CLAUDE_JOB_DIR/tmp/client-extraction-readiness.log`。
- [x] 删除 `BrokerClient.listBrokers()`、`BrokerBoltOperation.LIST_BROKERS`、`BrokerListResult`、`BrokerEndpointDTO`、`BrokerListHandler` 及 `RegistryTransformer` 专用响应映射；保留 QueryParams、String/Instant 注册映射、Registry/Gossip/HTTP 诊断。
- [x] 同步 `BrokerClientRoutingTest`、`BrokerTimeContractTest`、`RegistryTransformerTest`、`AbstractBrokerRpcHandlerTest`、Message `TestCacheConfiguration`、架构 `fixture/broker/support/health/InterfaceOwner`；保留 Gateway Instant、Gateway/Gossip 与 stale refresh committed target 断言，负例改真实同层 Handler。
- [x] 同步 SDK README、2026-10-20 统一 invoke 与 2026-09-19 路由设计的有效发现说明；不改历史归档。
- [x] 全仓引用搜索及 `git diff --check`，确认无并发 Maven 后顺序执行以下命令，失败或耗时先归因停止，不启动 full。
  1. `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=BrokerClientTest,BrokerClientRoutingTest,BrokerTimeContractTest,BrokerRefresherTest,RegistryTransformerTest,AbstractBrokerRpcHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`
  2. `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-service/im-message/im-message-server -am -Dtest=ImMessageApplicationTests,BrokerMessageNotifierTest,MessagePushServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  3. `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-test/im-architecture-test -am -Dtest=LayerBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 已完成：包迁移阶段 Broker 聚焦 exit0（/tmp/broker-gossip-settings-focused.log）；Bolt 模块 exit0，Bolt 267 tests 全零（/tmp/bolt-package-module.log）；LayerBoundaryTest 30 与 BoltInvocationBoundaryTest 4 全零（/tmp/bolt-package-architecture.log）。独立包迁移 Review Accept，无必须修订项；不替代完整验收或后续注释审查。
- 注释补充：按用户追加要求，覆盖本轮 Bolt 11 个生产类及 BrokerGossipSettings 的类、字段/record 组件、构造器和方法（含 private/override）。client 7 文件由执行者逐文件验证非注释 token 一致；其余 5 文件与修改前快照对比一致。12 文件空白检查及 `git diff --check` exit0；未改 Java 逻辑，未为此次注释补充运行 Maven/full。
- 用户 Review 追加命名调整：BoltOutboundGate 改为 client.BoltOutboundHandler，同步构造器及 3 处 pipeline 装配引用；保留 package-private、原 pipeline 名称及所有校验逻辑，不拆 channel 子包、不增加 public 面。设计 D5 已记录。静态重命名检查及 `git diff --check` exit0；此次未运行 Maven，之前模块测试不作为改名后的编译证据。
- 暂停事实：此前启动的最终验收批次已按用户要求取消，不计通过或失败；当前没有可用于关闭本计划的完整验收结果。
- D6 实施与窄验证：完整删除获批链及专用 DTO/映射，保留注册构造重载与 QueryParams、Registry/Gossip/HTTP 诊断；stale refresh 用另一次 refreshClusterState 证明 committed target 绑定不回退，Gateway Instant 与注册观察 JSON 断言保留，架构负例引用同层真实 BrokerClusterStateHandler，规则与断言未改。上述三个 focused 命令顺序 exit0；Broker 25 tests、Message 3 tests、LayerBoundary 30 tests，均零失败/错误/跳过。日志 `/tmp/d6-broker-focused.log`、`/tmp/d6-message-focused.log`、`/tmp/d6-architecture-focused.log`。全仓源码无删除符号残留，文档仅保留删除决定及历史记录；`git diff --check` exit0。构建前无本仓并发 Maven（外仓 wrasse Maven 未触碰）。未运行 clean/full、未提交；独立实现 Review 与完整验收仍未完成。
- 下一步：等待用户完成 Review；若有反馈继续处理。用户明确要求验收后，再定稿冻结并顺序执行 clean/full、当前 quality 与最终独立 Review。
- 不要修改：无关用户源码、IDE设置、既有架构规则、历史失败记录。

## T3 追加：客户端调用追踪最小拆分

事实源：[调用追踪设计](../../design-docs/2026-10-25-bolt-client-call-tracking-design.md)，用户批准，独立设计 Reviewer `a35e419d309f04bf8` Approved、必须项 none。T3 仍唯一 active；clean/full/commit 禁止，本轮只做窄验证。

- [x] 记录设计批准，更新设计索引、当前计划与 PROGRESS；readiness exit0。
- [x] 在原实现运行既有生命周期/原生完成聚焦测试建立行为基线；已有 callback 排队/拒绝/解码失败测试复用，不重复创建。
- [x] 修改 `im-plugin/im-bolt/src/main/java/com/co/kc/imchat/plugin/bolt/client/{BoltAuthenticatedClient,BoltClientCallTracker,BoltOutboundHandler,BoltAuthenticatedConnectionManager}.java`：整体提取追踪状态，保留唯一 monitor/closed、初始化交接、完整原生 future 委托及锁外 close；其余流程不改。
- [x] 同步 `im-plugin/im-bolt/README.md`；精确审查 future-before-admit、多次/迟到 finish、Completion 不回持 Client，以及全部原生委托。无新通用规则则不增加 Harness 例外。
- [x] 串行运行以下命令并记录实际退出码/报告；禁止使用旧 full 替代新实现证据。

```bash
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltConnectionLifecycleTest,BoltAuthenticatedFailureTest,BoltProductionResourcesTest,BoltUnifiedInvokeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-test/im-architecture-test -am -Dtest=LayerBoundaryTest,BoltInvocationBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test
git diff --check
```

日志统一 `$CLAUDE_JOB_DIR/tmp/call-tracking-*.log`。构建前检查无本仓并发 Maven，外仓进程未触碰；未停止既有 rg 进程。独立实现 Review、正式 quality、集中 full 仍待后续执行，不据窄验证关闭 T3。

实际证据：原实现 baseline 与提取后 focused 均 exit0，Lifecycle39、Failure4、Resources4、Unified15，共62 tests，零失败/错误/跳过；日志 `call-tracking-baseline.log`、`call-tracking-focused.log`。完整 Bolt 模块 exit0，15 XML / 267 tests 全零（`call-tracking-module.log`）；架构 focused exit0，LayerBoundary30 + BoltInvocationBoundary4 全零（`call-tracking-architecture.log`）。`git diff --check` exit0。

精确审查：保存提取前 Client 到 `$CLAUDE_JOB_DIR/tmp/call-tracking-client-before.java`；去除注释/空白并仅归一 monitor 名与固定上限表达式后，admit/drain 和完整 Completion token 完全相同。Completion 只捕获 Tracker，无 Client 引用；同步 terminal 判断未变。ended 在准入前/后均经原补偿路径释放，released 在同一 monitor 下限制单次扣减，closed 不跳过 finish。两处 Channel.close 均在 monitor 外，Handler/Manager 仅替换调用目标且未外套 Tracker monitor。既有公开原生 callback 和 lifecycle 用例复用；未新增 seam、反射、重复测试或变异实验，竞态细节以精确算法对比及独立实现 Review 补充，不声称新增场景已获得专用行为证据。

局部独立实现 Reviewer `ac1bb6179c9cf8a01` Accept，findings none，仅覆盖 Tracker 本轮差异。证据限制：quiet baseline 日志不能单独证明 exit 或冻结输入；exit0 来自工具实际返回，不具备专用冻结清单；未执行专用 mutation，不将精确 token 检查冒充行为反证。完整混合工作区 Review、正式 quality 与 full 仍未完成。Factory 依赖评估第一轮独立 Reviewer `a12ce5a7edf571b89` Revise；修订为保留现有 client.manager() 与固定 snapshot 模式读取，不实施 bindManager、nullable / volatile Manager 字段、额外 mode 参数或任何 Factory 生产改动。原 Reviewer 复核 Approved、findings none，两个必须项关闭；仅批准保留现状、零生产修改，首轮 Revise 历史保留。Manager 构造执行器但未 started 时，现 rollback 不能证明新增失败阶段完整回收；对象已构造不等于线程已启动，不宣称现有线程泄漏，也不扩生命周期修复。本轮仅文档，无 Maven/full，不改 BoltRpcClient，T3 保持唯一 active。

并发差异：前快照为 `BoltGenericOption.EXTENDED_NETTY_CHANNEL_HANDLER`，当前使用 `BoltClientOption.EXTENDED_NETTY_CHANNEL_HANDLER` 且保留另一个 GenericOption import；来源未知，未回退或归因于本任务。当前写法已经上述模块/架构验证。无 clean/full/commit。

## 回滚与残余风险

- 回滚本任务精确包迁移/引用，不覆盖混合工作区已有改动。
- 未验证：最终完整验收与当前正式quality尚未执行；聚焦通过不等同整体完成。
- 保留生产证书、MySQL条件skip、性能容量和私有JRaft后轨。

## T3 追加：Peer 身份 URI 前缀命名迁移

依据：2026-10-25-peer-identity-uri-prefix-design.md，用户批准，独立 Reviewer a0b58853328dc8e68 Approved 无必须项。T3 仍唯一 active，不重开 T1/T2。D6 删除及混合用户改动保留；不拆分 Bolt 客户端职责，不改历史归档或完整 workloadIdentity。

精确生产/文档范围见本轮刷新库存 `$CLAUDE_JOB_DIR/tmp/identity-prefix-inventory-refreshed.txt`：Broker SDK 身份策略、Server 五角色配置/Gossip 快照及装配，Gateway/Account/Message Broker 配置与装配，四份 application.yml、五份 owning README。测试范围为 BrokerRoutingPropertiesTest、GatewayDevelopmentTargetConfigurationTest 和三个 owning BoltConsumerConfigurationTest。

- [x] 九入口：非默认新键与真实 Spring env 绑定，旧键/env 单独输入只保持默认值；先 RED 保存报告。
- [x] 同步 Java 字段/参数/访问器、kebab 配置键、真实 env 引用和当前文档；默认 URI、拼接、startsWith、角色授权及 MTLS/DEV 不变。
- [x] 顺序执行下列精确命令，报告目标模块测试数量及退出码；失败先归因，不盲目 full。
- [x] 库存复查、git diff --check，更新恢复事实；不将窄验证当完整验收。

所有命令仓库根执行，先确认无本仓并发 Maven，统一 `-Dmaven.repo.local=/tmp/jraft-artifact-m2`，日志放 `$CLAUDE_JOB_DIR/tmp`。

```bash
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=BrokerRoutingPropertiesTest,GatewayDevelopmentTargetConfigurationTest,BrokerObservationRequestConfigurationTest,BrokerGossipSettingsTest,IdentityAccessInterceptorsTest,BrokerObservationIdentityTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-gateway/im-ws-gateway/im-ws-gateway-server,im-service/im-account/im-account-server,im-service/im-message/im-message-server -am -Dtest=BoltConsumerConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server,im-gateway/im-ws-gateway/im-ws-gateway-server,im-service/im-account/im-account-server,im-service/im-message/im-message-server -am -Dtest=ImBrokerApplicationTest,ImWsGatewayApplicationTest,ImAccountApplicationTest,ImMessageApplicationTests -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-sdk,im-gateway/im-ws-gateway/im-ws-gateway-sdk -am -Dtest=BrokerClientTest,BrokerClientRoutingTest,GatewayClientTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-test/im-architecture-test -am -Dtest=BoltInvocationBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test
git diff --check
```

clean/full 继续用户暂停。独立实现 Review、正式 quality 和完整验收不在本轮窄证据内。

### 前缀迁移实际证据

- RED Broker：`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=BrokerRoutingPropertiesTest,GatewayDevelopmentTargetConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`，exit1，30 tests / 24 failures / 0 errors / 0 skipped。六入口每项四种输入按预期全部失败，证明新键未绑定、旧键仍绑定。
- RED Consumer：上述 consumer 命令额外加 `-Dmaven.test.failure.ignore=true` 以收齐三个 owning module 的失败报告，进程 exit0 **不是通过**。每模块11 tests / 4 failures / 0 errors / 0 skipped，共33 / 12 failures，均为四种新旧 key/env 断言失败；GREEN 已移除此收集选项。
- 首次 Broker GREEN exit1（55 tests / 2 failures），定位到批量改名误改 Gateway 测试 OLD 输入为新键；恢复旧键负例后重新运行同一 Broker 命令 exit0，55 tests 全零。未修改生产绑定行为或弱化断言。
- 最终顺序结果：Broker 55、Consumer 33（11×3）、Startup 6、SDK 24、Architecture 4 tests，共122 tests，所有命令 exit0，零失败/错误/跳过。SDK 首个路由测试改用非默认 `spiffe://custom.example/broker/`，验证 bootstrap 及 committed target 拼接保持传入前缀。
- 日志：`$CLAUDE_JOB_DIR/tmp/prefix-red-broker.log`、`prefix-red-consumers.log`、`prefix-green-broker.log`（首次失败）、`prefix-green-broker-fixed.log`、`prefix-green-consumers.log`、`prefix-startup.log`、`prefix-sdk.log`、`prefix-architecture.log`。
- git ls-files（含未跟踪有效文件）刷新库存；当前旧名仅见负例、迁移说明及设计/状态/历史记录。无生产声明、访问器或配置旧键，仓库没有实际旧前缀环境占位符需要迁移。九入口全部通过 SystemEnvironmentPropertySource 实测 Spring 原生 env，而非用普通 property 冒充 env。
- 不改一般 identityPrefix/identityPrefixes、完整 workloadIdentity()、默认 URI、匹配/授权/TLS/DEV 算法。D6 与用户混合修改保留；未触碰职责拆分、未提交。没有引入新工程规则或通用旧键拒绝框架，Harness 无新增规则。
- 库存 rg 调用超时后台化（bu9f4epmp）；未停止该任务或先前 bw34x3rcp，后续改用有界 git 文件集逐文件扫描，不依赖后台结果。无本仓并发 Maven，所有 Maven 严格顺序执行。
- T3 仍唯一 active；实现独立 Review、正式 quality、真实 E2E/HA 与完整验收未完成；clean/full 仍按用户要求暂停，不归档计划。

## T3 追加：按职责拆包

事实源：[职责拆包设计](../../design-docs/2026-10-25-bolt-responsibility-packages-design.md)。用户明确认可，独立设计 Reviewer `ad3341b14c207845a` Approved、必须项 none，已收到恢复实施指令；T3 仍唯一 active。clean/full/commit 禁止。

准确范围：im-bolt 原 client 六生产类搬至 invocation/connection/channel，Factory/Manager 改 Controlled；Client 最小跨包 public/private calls、Tracker private closed 与受控方法；六测试移动、既有 BoltSecurityGateTestSupport 两个 server helper；ControlledProtocolWrite fixture 与 BoltInvocationBoundaryTest 正例路径；AutoConfiguration、Broker/Account/HA 等全部实际 Invoker imports/FQCN；im-bolt README、当前有效设计的取代说明。历史归档/命令及无关混合更改不动。

- [x] 保存本轮 before 文件/SHA、精确文件库存和原七测试方法/参数清单，执行旧 client 七精确 FQCN baseline；保存 UTC/命令/exit JSON 和 same-run XML。
- [x] 按 visibility 矩阵搬包与最小访问变更，保持共享 monitor、唯一 closed、原生完成委托和所有安全/分类/预算方法；不 bindManager。
- [x] 按实际清单/SHA 隔离设计 allowlist 六旧生产类、六旧测试和旧 fixture 及 inner class 至 job tmp；拒绝未知文件/符号链接/并发变化，每轮复查旧 class 不重现。
- [x] 同步测试/helper/fixture/真实引用和当前文档，保持所有断言/参数输入；保存含 untracked 的本轮 exact diff。
- [x] 严格顺序执行设计「验证策略」完整七组命令：新七 FQCN focused、Bolt module、Broker focused、consumer/startup、architecture、E2E/HA test-compile、Harness 与 diff-check，均使用私有 m2 `/tmp/jraft-artifact-m2`；准确命令原文以该设计代码块为准，每轮独立 JSON 保存完整实际 argv/UTC/exit。
- [x] same-run XML 对照 baseline/method/参数输入，无漏跑双跑；精确归一生产差异与锁/关闭位置审查，回填证据后交独立实现 Review，不关闭 T3。

本轮证据统一 `$CLAUDE_JOB_DIR/tmp/responsibility-*`。旧 class 再现或较窄层失败先停止归因，不用后续通过覆盖；不运行 clean/full、真实 HA 或 commit。

实际证据：baseline103、focused103、module304（Bolt267 + common37）、Broker50、consumer/startup37、architecture34 tests，全部零失败/错误/跳过且 exit0；E2E/HA test-compile 与 Harness exit0。本轮无 Maven 失败。完整 argv、UTC/exit、fresh XML/cases 在 `$CLAUDE_JOB_DIR/tmp/responsibility-{baseline,focused,module,broker,consumers,architecture,cross-compile,harness}/`。54 个 allowlisted 旧 class 含 inner 的清单/大小/SHA 在 responsibility-stale-artifacts.json，隔离原件在 responsibility-stale-artifacts/，各轮未重现。focused/module 七组103条 invocation 归一包名后与 baseline 相同，无漏双跑；源码方法/参数注解清单一致。七生产类仅批准项归一后相同，BoltRpcClient 字节相同，normalization.json 保存结果。Client 原 synchronized(calls) 块保留，closeAdmission 在同 monitor 重入，Tracker Channel.close 仍在锁外；无 public writable closed。含 untracked 的 exact diff 与文件清单在 responsibility-exact.diff / responsibility-changed-files.json。脚本唯一实际 ControlledClient 硬编码路径不变，checker 未改。独立实现 Reviewer `ad3341b14c207845a` 局部 Accept、findings none，已独立核对 SHA、归一差异、103 条矩阵、54 个旧产物无重现、可见性与 shared monitor；结论仅覆盖本轮职责拆包，不覆盖完整混合工作区。正式 quality/full/真实网络完整验收缺口保留，T3 仍唯一 active、不关闭不归档；clean/full/commit 未运行。本次仅同步真实局部 Review 结论，无源码修改或 build/clean/full/commit。

## T3 追加：两处可读性提取

依据：[职责拆包设计追加](../../design-docs/2026-10-25-bolt-responsibility-packages-design.md)。用户最终授权「你先调整一下看看」，独立设计 Reviewer `a38eedb4de94875cc` Approved、必须项 none。T3 仍唯一 active；clean/full/commit 禁止。范围仅 client/BoltControlledClient.java 静态 TrackedRpcClientRemoting 与 channel/BoltOutboundHandler.java Optional 校验提取，以及设计、本计划、PROGRESS。无行为变更，不新增测试 seam/断言或工程规则。

- [x] readiness exit0；核对并保存两文件 effective-before/SHA，保留用户新增 Handler @Override。
- [x] 提取 private static final TrackedRpcClientRemoting，显式原生三参数与同一个 Tracker，三个 override 原体保留；提取 private Optional<BoltInvocationException> validateInvocation，保持检查/时钟/drain/异常顺序及 write 的 admit/unsupported/super.write。
- [ ] 严格顺序运行以下三条命令，逐条保存 argv/UTC/exit 与 fresh XML；较窄失败先归因。
- [ ] 保存含未跟踪两文件的 exact diff、三个 override 与校验语义比较、git diff --check，回填证据交独立实现 Review，不关闭 T3。

```bash
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=com.co.kc.imchat.plugin.bolt.invocation.BoltUnifiedInvokeTest,com.co.kc.imchat.plugin.bolt.invocation.BoltAuthenticatedFailureTest,com.co.kc.imchat.plugin.bolt.connection.BoltConnectionLifecycleTest,com.co.kc.imchat.plugin.bolt.connection.BoltAuthenticatedTlsTest,com.co.kc.imchat.plugin.bolt.connection.BoltProductionResourcesTest,com.co.kc.imchat.plugin.bolt.connection.BoltProductionBackgroundTest,com.co.kc.imchat.plugin.bolt.client.BoltDevelopmentModeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-test/im-architecture-test -am -Dtest=LayerBoundaryTest,BoltInvocationBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

证据目录：`$CLAUDE_JOB_DIR/tmp/readability-*`。原 before 与 effective-before 分开保留；差异仅用户追加的 Handler @Override。Optional 阶段 focused103 exit0（02:45:49–02:47:20 UTC）、Bolt267 + common37 共304 tests exit0（02:47:40–02:50:49 UTC），均全零失败/错误/跳过。完整 argv/UTC/exit/fresh XML 在 readability-focused、readability-module，exact diff、语义比较与 javap 同目录前缀；三个 override 原体一致、校验只 Optional return 归一一致、write 后缀一致、无外层 Client 字段。旧架构未运行，验证 checkbox 保持未完成。

用户随后明确「可以」，批准设计中新追加的两 private void 校验方法、write 单层 RuntimeException catch、super.write 在外；校验/属性/drain 意外 RuntimeException 统一 promise 是明确行为变化，非全等价重构。独立设计 Reviewer `a38eedb4de94875cc` 复核 Approved、必须项 none，明确恢复实施。旧 Optional 结果只作历史，不继续旧版架构。

- [x] 记录新版 Approved，保存 Optional Handler 与原 UnifiedTest 快照。
- [x] 在既有 invocation/BoltUnifiedInvokeTest.java 新增直接真实 Handler.write 边界回归，先执行 RED：`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am '-Dtest=com.co.kc.imchat.plugin.bolt.invocation.BoltUnifiedInvokeTest#outbound*' -Dsurefire.failIfNoSpecifiedTests=false test`。
- [x] 最小修改 Handler 两 private void 校验和 write 单 catch，Tracked 算法不再改；同一命令 GREEN。随后按用户要求在 Handler 主流程及 Client init 交接处补中文步骤块注释，非注释逻辑不变。

本轮新版实际记录：final-red 首次 testCompile exit1，为新增 Binding fixture 漏 prefix/deadline 参数，已最小修测试并保留失败；final-red-fixed 9 tests 中恰两项意外属性/drain 异常向外传播 errors（预期 RED），final-green 同9 tests全零 exit0。含步骤注释的 final-focused 112 tests 中既有 DECODE 参数 handled expected1/actual0，exit1；新增9项通过。未扩大 module/architecture。最窄原三参数方法 final-focused-diagnostic 再现同失败，exit1。一次临时 cause 诊断 final-cause-diagnostic 3 tests exit0：DECODE handled=1、offset=1000000000，cause 为 Invoker:93 解码后 remainingMillis 的 caller budget exhausted→TimeoutException，符合本次预期，不证明此前失败根因。此前失败XML未保存原业务cause链，预算过早耗尽只是待证假设，不称已修复。诊断仅临时输出cause/handled/offset，完成后恢复测试原bytes，SHA与最终冻结manifest相同（readability-diagnostic-restoration.json）。当前暂停更宽验证，等待失败路径归因决策，不能以一次绿覆盖先前两失败。所有 result.json/log/fresh XML 在 readability-final-*，准确 argv/UTC/exit 原样保留；无clean/full/commit。
- [ ] 严格顺序执行本节代码块的七组 focused、Bolt module、两架构三命令，保存新一轮 argv/UTC/exit/fresh XML。
- [ ] 最终精确 diff/SHA、异常边界与保留规则核对，git diff --check，回填证据交 main 安排局部独立实现 Review；T3 不关闭。

### 可读性修订：内联消息类型白名单（2026-10-26）

- [x] 按用户批准，仅将 `channel/BoltOutboundHandler.java` 的 `validateMessageType()` 原判断内联至 write 的 try 内并删除该 private 方法；保留 private void validateInvocation、统一 catch 和 catch 外 super.write；同步职责拆包设计及 PROGRESS。
- [x] 精确展开比较确认与修订前仅方法内联差异，`git diff --check` exit0。原件与 SHA/比较结果在 `$CLAUDE_JOB_DIR/tmp/message-type-inline-before.java`、`message-type-inline-check.json`。

本次未运行构建或测试，不用旧 SHA/报告证明新输入通过；既有 DECODE 失败根因仍未确认，更宽验证及独立实现 Review 缺口保留，T3 仍唯一 active，clean/full/commit 继续暂停。

## T3 追加：受控客户端 API

事实源：[API 设计](../../design-docs/2026-10-25-bolt-controlled-client-api-design.md)。Reviewer a515ed306ad89329e 首轮 Revise 保留，复核 Approved，三个必须项关闭；T3 唯一 active，clean/full/commit 禁止。

- [x] 同步 Approved 与实施授权；readiness exit0。
- [x] 保存 controlled-client-before 精确清单/原件，运行七测试旧 FQCN 基线和三个 consumer 真实 invoke/PROTOCOL/直接 cause/message/空连接基线。
- [x] Client 改名/acquire 包级；七测试移 client；support 最窄 public 与 policy helper 移动，无转发；三个 consumer 保留 owning 包。
- [x] 明确 target allowlist 列表/SHA 后隔离旧 Client/七测试与 inner class；不碰源码/未知文件；每轮复查。
- [x] 严格顺序执行设计验证策略原文命令：新 FQCN focused、Bolt module、Broker focused、consumer/startup、architecture、E2E/HA test-compile、bash scripts/test-harness.sh、git diff --check。私有 m2 /tmp/jraft-artifact-m2；独立小文件记录命令/开始结束/exit，same-run XML 和方法/参数清单核对不漏跑双跑。
- [x] 回填实际证据，交独立实现 Review，不关闭 T3。

精确文件范围：im-bolt Client/Invoker/Factory/Manager/OutboundHandler/AutoConfiguration 实际类型引用，七测试及共享 support/AutoConfigurationTest；Broker 装配/Adapter/实际测试引用；Gateway/Account/Message 启动/consumer 测试；E2E/HA 类型引用；scripts/test-harness.sh；im-bolt README 与当前有效设计文档。完整路径清单保存在 $CLAUDE_JOB_DIR/tmp/controlled-client-before。历史归档不机械改；不改 Invoker 构造、RpcClient 独立策略校验、安全检查、分类或其他名字。

实际证据（均在 $CLAUDE_JOB_DIR/tmp/controlled-client-*）：baseline 七测试103、consumer-baseline37；focused-fixed103、module304（Bolt267 + common37）、broker50、consumers37、architecture34，全零失败/错误/跳过且 exit0。cross-compile exit0 不代表 E2E/HA 执行；harness exit0。各轮 result.json 独立保存完整命令、UTC 开始结束与 exit；同轮 XML/cases 已逐轮复制。首次 focused exit1 为 testCompile：Lifecycle 未限定 policy 调用及 CertificateMaterials.directory 跨包访问；最小更新调用与仅该字段 public 后同命令通过，未修改生产/断言。

52 个允许的旧 class（含 inner）清单/SHA 已核对后移到 controlled-client-stale-artifacts；最终均未重现。baseline/focused/module 迁移七组的103条 classname/name 参数化 invocation 归一包名后完全相同，无重复/旧FQCN；源码测试方法与参数注解清单一致。外包 javac probe 使用隔离旧 class 原 API exit0，新 API 明确因 acquire 非 public exit1（预期反证），日志及结果独立保存。

Client 精确对比为类名替换加 acquire 删除 public；Invoker/Factory/Manager/OutboundHandler 精确对比仅类符号替换，原构造和分类/安全逻辑未改。源码无旧类声明/import；旧名仅迁移说明、历史路径与记录。完整本轮精确 diff 为 controlled-client-exact.diff（相对本轮快照，含未跟踪文件）；git diff --check exit0。模块运行后仅 fixture Javadoc 归位和文档更动，Java 逻辑未变。独立实现 Review、正式 quality/full 仍待完成，不关闭 T3。

局部独立实现 Reviewer `a515ed306ad89329e` Accept、findings none：已核对本轮39文件SHA、生产精确归一差异、103条无漏跑/双跑、52旧产物隔离及API编译反证。结论仅覆盖本轮受控客户端API差异，不覆盖完整混合工作区；设计首轮Revise及focused首次编译失败原记录保留。正式quality、完整验收和真实E2E/HA仍有缺口，T3继续唯一active，不归档。此次只同步状态，无源码修改或build/clean/full/commit。

## T3 追加：Client 四态生命周期

依据：[四态设计](../../design-docs/2026-10-26-bolt-client-lifecycle-state-design.md)。独立设计 Reviewer `a46adc81a21ae2558` Approved、必须项 none；用户确认继续处理后实施，T3 仍唯一 active。clean/full/commit 暂停，既有 DECODE 失败未归因，不扩大 module/architecture。

- [x] 保存 Client、Handler、LifecycleTest、README 及本轮文档 before/SHA 至 `$CLAUDE_JOB_DIR/tmp/lifecycle-state-before`，同步设计批准事实。
- [ ] 在 `im-plugin/im-bolt/src/test/java/com/co/kc/imchat/plugin/bolt/connection/BoltConnectionLifecycleTest.java` 新增确定性公开边界回归，先旧实现 baseline。
- [ ] 仅修改 `im-plugin/im-bolt/src/main/java/com/co/kc/imchat/plugin/bolt/client/BoltControlledClient.java` 三布尔为四态及对应分支，同步 `im-plugin/im-bolt/README.md`；Handler 原 bytes 保留，不提取 pipeline 匿名类。
- [ ] baseline/GREEN 严格串行运行下列命令，保存 argv、UTC、exit、fresh XML；核对共享锁/唯一 closed/责任转换与精确 diff，交独立实现 Review，不关闭 T3。

```bash
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=com.co.kc.imchat.plugin.bolt.connection.BoltConnectionLifecycleTest,com.co.kc.imchat.plugin.bolt.connection.BoltProductionBackgroundTest -Dsurefire.failIfNoSpecifiedTests=false test
git diff --check
```

证据目录 `$CLAUDE_JOB_DIR/tmp/lifecycle-state-*`。无法通过现有公开协作者边界可靠注入的故障明确保留缺口，不加生产 seam 或复杂无关 fixture。

## T3 追加：ReentrantLock 生命周期串行化

依据：[串行化增量设计](../../design-docs/2026-10-26-bolt-client-serialized-lifecycle-design.md)。最终方案独立设计 Reviewer `a3b3f5a6c96a74ee1` Approved、必须项 none。T3 唯一 active，更宽验证暂停，旧 DECODE 失败保留。

- [x] 保存当前 Client/Handler/LifecycleTest/README 及设计状态原件和 SHA 至 `$CLAUDE_JOB_DIR/tmp/serialized-lifecycle-before`，同步最终 Review 事实。
- [x] 修改 `im-plugin/im-bolt/src/test/java/com/co/kc/imchat/plugin/bolt/connection/BoltConnectionLifecycleTest.java`：新契约排队、重入caught/escaping、第二init线程、5秒超时、中断和calls进展，旧生产实现先RED。
- [x] 最小修改 `im-plugin/im-bolt/src/main/java/com/co/kc/imchat/plugin/bolt/client/BoltControlledClient.java`：独立ReentrantLock/5秒tryLock、删除owner/future/await，保留当前ControlledChannelHandler及排版；同步README。
- [x] 严格顺序执行上节同一Lifecycle/ProductionBackground命令GREEN，保存argv/UTC/exit/SHA/freshXML；git diff --check、精确差异与锁责任检查后交独立实现Review，不关闭T3。

本轮证据目录 `$CLAUDE_JOB_DIR/tmp/serialized-lifecycle-*`。不运行 module/architecture/full/clean/commit。

实际证据：新契约 RED 2026-10-26 06:33:34–06:34:25 UTC，exit1，52 tests / 8 failures / 0 errors / 0 skipped；失败恰为两条重入与六条锁等待新参数，旧实现即时init拒绝/关闭准入和静默重入不符合新契约。最小实施后 GREEN 06:35:27–06:36:42 UTC，exit0，52 tests全零，生产/测试SHA运行期间未变。两轮完整argv/result.json、run.log及freshXML在 `$CLAUDE_JOB_DIR/tmp/serialized-lifecycle-{red,green}`；最终SHA、逐条case、Handler原bytes不变及diff-check exit0在 serialized-lifecycle-evidence.json，含untracked原件的本轮差异在 serialized-lifecycle-exact.diff。

新增六参数真实等待覆盖两入口排队、两入口超时及两入口中断；两个重入参数覆盖caught可成功提交与escaping回滚，短calls读取在初始化和竞争者停留时可进展。未新增生产seam。保留缺口：运行态关闭回调仍持锁期间第二shutdown的专门阻塞观察、预中断重入优先级、外部扩展跨线程循环及任意原生清理失败全矩阵未新增专项证明；既有并发shutdown/资源及后台回归随本轮运行，不用非阻塞Channel closeFuture冒称专门锁等待证明。未运行mutation、module/architecture/full/clean/commit；旧DECODE失败仍未归因，局部独立实现Review待完成，T3不关闭。

## T3 追加：最小锁边界维护

范围仅 Client 三处外层 calls 移除和 acquireLifecycleLock 原体提取，设计依据见串行化设计追加。T3仍唯一active。

- [x] 保存当前Client/Handler/Tracker/LifecycleTest及状态文档before/SHA至 `$CLAUDE_JOB_DIR/tmp/lifecycle-lock-maintenance-before`。
- [x] 仅修改 `im-plugin/im-bolt/src/main/java/com/co/kc/imchat/plugin/bolt/client/BoltControlledClient.java` 三处已审计冗余同步和两入口重复等锁；保留四处复合calls及Tracker内部锁，不提取initializeResources。
- [x] 运行本计划同一Lifecycle/ProductionBackground精确命令，保存52项freshXML/argv/UTC/exit/SHA，精确比较helper原体和保留边界，git diff --check，交局部独立实现Review。

不运行module/architecture/full/clean/commit，旧DECODE失败保留。

本轮52项窄验证06:49:39–06:50:38 UTC exit0，零失败/错误/跳过，源码/测试SHA运行中不变；完整argv/UTC/exit/freshXML在 `$CLAUDE_JOB_DIR/tmp/lifecycle-lock-maintenance-test`。helper与原等锁体去注释/空白token一致，保留四处calls，isRunning复合查询原体一致；静态比较在 lifecycle-lock-maintenance-static.json。本轮未改测试/Handler/Tracker，不提取initializeResources；精确diff/SHA及diff-check见同前缀证据。局部独立实现Review待完成，更宽验证和旧DECODE缺口不变。

## T3 追加：受控 startup 入口

- [x] 核对Bolt1.6.6 init动态startup及RuntimeException契约，保存精确16文件before/SHA至 `$CLAUDE_JOB_DIR/tmp/controlled-startup-before`，原生/raw/server入口不动。
- [ ] Client override/super及Spring Bean initMethod改startup；已确认受控Plugin、BrokerGossipAdmissionIntegrationTest、AutomaticFailureTlsHaTest调用及method reference同步，README同步；保留继承init行为回归。
- [ ] 按窄Lifecycle/Background/AutoConfiguration、其他受影响Plugin测试（Unified只选受控迁移方法，排除既有DECODE）、Broker单集成、HA仅test-compile顺序运行，逐轮保存argv/UTC/exit/frozenSHA/freshXML，先失败先归因；精确diff后交独立Review。

不运行module/architecture/full/clean/commit；HA编译不等于真实HA，旧DECODE缺口保留。

## T3 最终追加：monitor/startup统一迁移

最终协议以串行化设计末节为准，T3唯一active，取代5秒等锁退出验证要求。
- [x] 保存当前16文件原件及SHA至 `$CLAUDE_JOB_DIR/tmp/monitor-startup-before`。
- [x] 修改Lifecycle回归为BLOCKED/预中断仍等待、两startup排队及重入边界，旧实现RED。
- [x] Client使用独立monitor完整startup/shutdown，删除显式锁/等锁helper，受控调用/Spring默认Bean迁startup；保留原生/raw init及用户排版。
- [x] 窄Lifecycle/Background/AutoConfiguration、受影响Plugin且排除旧DECODE测试、跨Broker/HA编译依次运行，保存argv/UTC/exit/SHA/freshXML与exactdiff，交独立Review。

禁止module/architecture/full/clean/commit；编译不当真实启动或HA证据。

实际证据：monitor新契约RED 07:47:40–07:48:27 UTC exit1，50 tests/4 failures，恰为四个原timed-lock不满足BLOCKED/预中断等待参数。迁移GREEN含AutoConfiguration 07:49:27–07:50:51 exit0，58 tests全零；DEV/Resources/TLS及Unified两明确方法选择07:51:40–07:52:17 exit0，44 tests全零，未执行productionCallerBudgetAndPreparationFailureRetainLocalOrigin任何参数，旧DECODE缺口保持。Broker/HA 07:52:34–07:52:58 test-compile exit0，仅编译不宣称运行/真实HA。各轮完整argv/UTC/exit/frozenSHA/freshXML在 `$CLAUDE_JOB_DIR/tmp/monitor-startup-{red,green,affected,cross-compile}`；before、exact.diff、evidence.json同前缀。

Lifecycle现四个竞争参数（普通/预中断两入口）和继承init兼容回归，加caught/escaping重入及calls进展。monitor无限等待及自定义回调跨线程join风险明确保留。Handler字节未改，默认Bean initMethod startup；其余原生/raw/server init不动。未执行module/architecture/full/clean/commit。局部独立实现Review待完成，T3不关闭。一次全仓rg迁移复查超时后台化bnt602cdz，不停止既有进程，改用已冻结16文件有界库存复核，不依赖后台输出。

## T3 追加：三个初始化阶段方法

- [x] 保存当前Client/Handler/状态文档before/SHA至 `$CLAUDE_JOB_DIR/tmp/initialization-methods-before`。
- [x] 仅Client原位提取beginInitialization/commitInitialization/finishFailedInitialization，原锁块、条件、异常和调用责任位置不变。
- [x] 同当前Lifecycle/Background/AutoConfiguration精确58项命令验证，保存argv/UTC/exit/SHA/freshXML及exactdiff/原体比较，单独交局部Review，不改旧证据。

T3仍唯一active，旧DECODE及full/clean/commit/module/architecture暂停保留。

本轮08:02:12–08:03:33 UTC窄命令exit0，58 tests零失败/错误/跳过；sourceSHA运行中不变。日志/result.json/freshXML在 `$CLAUDE_JOB_DIR/tmp/initialization-methods-test`。展开三个helper后startup去注释/空白token与before完全一致，calls块数不变；比较见 initialization-methods-static.json，exactdiff/最终SHA见同前缀证据。

上一轮monitor/startup局部独立实现Reviewer `a3b3f5a6c96a74ee1` Accept、findings none，仅绑定Client e40d17188983c5b6f1d90f502c910eb12c511d73bed6793f0fdb928c919318d5与monitor-startup原证据。三个初始化方法增量随后获同Reviewer Accept，但独立SHA重算因ENOSPC未完成；保留该证据限制，不覆盖后续关闭方法提取，T3不关闭。

## T3 追加：关闭阶段命名提取

- [x] 普通df确认可用约3.7 GiB，未删除材料或变更临时路径；保存Client/Handler/状态文档before与SHA至 `$CLAUDE_JOB_DIR/tmp/shutdown-method-before`。
- [x] 仅Client提取beginShutdown及三值ShutdownAction，保留原calls复合边界、异常、NONE位于资源try之前和清理finally。
- [x] 运行同58项聚焦命令并保存argv/UTC/exit/SHA/freshXML、静态责任对照及exactdiff；本增量独立Review待完成。

09:07:22.786960–09:08:58.605285 UTC，窄命令exit0，58 tests零失败/错误/跳过；Client SHA 3713e152c050bd081ec1b37436a83a0217e2857c513f99d7c8f868f192c27d10运行中未变。完整argv/run.log/result.json及三份freshXML已复制至 `$CLAUDE_JOB_DIR/tmp/shutdown-method-test`，不依赖target保留。四处calls、record/Handler字节、NONE资源try前与LifecycleState最底检查通过；before/exactdiff/static同shutdown-method前缀。按后续盘点要求不再启动构建，未停止进程或删除材料。

T3仍唯一active，旧DECODE/module/architecture/full/clean/commit禁止保持。本增量不复用旧输入Review或测试作新输入证据。

## T3 追加：Factory / Manager 完整职责方法提取

独立设计Reviewer `a3b3f5a6c96a74ee1` 已Approved、findings none、必须项none，范围为Factory依赖设计2026-10-26追加节；未将旧依赖评估Review扩为本增量证据。

- [x] 保存Factory/Manager/Client/Handler及文档状态before/SHA至 `$CLAUDE_JOB_DIR/tmp/factory-manager-methods-approved-before`；readiness exit0，保留PATH警告。
- [x] 仅两个connection类提取classifyCreationFailure与五参数createAndPublishPool，删除acquire纯重抛catch；保留delivered/published/finally及own.get晚读取、task登记执行顺序；Client/Handler不改。
- [x] 精确归一原体及任务顺序对照，运行Factory/Manager现有窄测试再扩大相关边界测试，保存argv/UTC/exit/SHA/freshXML、scoped diff-check和exactdiff。

13:47:33.968098–13:49:01.930291 UTC，Resources/Background窄命令exit0、9 tests全零；13:49:16.600380–13:50:32.897533 UTC，TLS/Lifecycle/DEV/AutoConfiguration命令exit0、89 tests全零。未执行旧DECODE。完整argv、时间、exit、sourceSHA及freshXML/run.log复制至 `$CLAUDE_JOB_DIR/tmp/factory-manager-methods-{focused,boundaries}`；两轮源码运行中不变。Factory分类原链改return后及Manager整个lambda去注释/空白token均与before一致；task顺序、own.get晚读取、Client/Handler字节检查通过，static/exactdiff/evidence同factory-manager-methods前缀。scoped diff-check通过；全仓无关BrokerCallerIdentityInterceptor.java:23尾空白保留，不冒称全仓通过。
- [ ] 交本增量局部独立实现Review。

T3仍唯一active；等价提取不强造RED，旧DECODE/module/architecture/full/clean/commit不运行。

## T3 追加：ConnectionCreationScope 合并（2026-10-28）

用户批准合并及命名，Factory依赖设计追加节独立设计Reviewer `a5158f39a13936936` Approved、必须项 none。本次仅修改 `im-plugin/im-bolt/src/main/java/com/co/kc/imchat/plugin/bolt/connection/BoltControlledConnectionFactory.java` 及该设计、当前计划、PROGRESS；不改其他混合用户输入。以一个私有static final作用域保存final binding及可变candidate，整体保存恢复；失败时仍读取当前ThreadLocal候选，保留close抛异常时原恢复缺口。

- [x] readiness exit0；保存Factory before至 `$CLAUDE_JOB_DIR/tmp/connection-creation-scope/factory.before.java`。
- [x] 运行baseline：`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltProductionResourcesTest,BoltProductionBackgroundTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [x] 合并两个ThreadLocal，保留原状态转换及清理顺序，不为等价重构强造RED。
- [ ] 重跑同baseline命令，再运行 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltAuthenticatedTlsTest,BoltConnectionLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test`；保存日志及报告。
- [x] 独立实现Review并记录实际结果及嵌套专项测试覆盖限制。

实际结果：baseline及合并后Resources/Background均exit0，各9 tests零失败/错误/跳过；TLS/Lifecycle共63项，Lifecycle46项通过，TLS17项中1项失败（`caOverlapRestartSequenceAllowsNewAndRejectsRetiredAuthority`，原生SSLSocket握手`java.net.SocketException: Broken pipe`）。该失败未作before复现，不能归因为既有或本次修改，也不宣称边界全部通过；停止扩大验证，不改该测试。日志及四份本次XML保存在 `$CLAUDE_JOB_DIR/tmp/connection-creation-scope/`。Factory SHA `441a4b6f3f55adbfa8611ad8470dfd35b9d5b4629dfa95ff6ef19e9c7403aabf`，scoped diff-check通过；无新增真实嵌套专项回归，保留覆盖限制。独立实现Reviewer `a6a913c731e865433` 局部Accept、findings none，已独立核对精确差异及after XML；仅批准本次等价重构，不消除TLS失败或真实嵌套覆盖限制。无新增通用工程规则。

T3仍唯一active；旧DECODE/module/architecture/full/clean/commit暂停，本增量不关闭完整验收。

## T3 追加：PeerIdentityConstants（2026-10-28）

范围为前缀命名设计追加节：新增 common.constant.PeerIdentityConstants，替换 BrokerProperties、BrokerRoutingProperties、GatewayProperties、AccountBrokerProperties、Message BrokerProperties 的九个默认值；不改 YAML、配置绑定入口或其他用户输入。

- [x] 保存五类before；独立设计Reviewer `a19a522ec3aa516fc` Approved、findings none后实施常量和九处引用。
- [x] 精确展开常量后与before对比，`javac -d "$CLAUDE_JOB_DIR/tmp/peer-identity-constants/classes" im-common/src/main/java/com/co/kc/imchat/common/constant/PeerIdentityConstants.java`。
- [ ] `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=BrokerRoutingPropertiesTest,GatewayDevelopmentTargetConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [ ] 前层通过后：`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-gateway/im-ws-gateway/im-ws-gateway-server,im-service/im-account/im-account-server,im-service/im-message/im-message-server -am -Dtest=BoltConsumerConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [ ] 局部独立实现Review，记录实际结果，T3不关闭。full/clean/module/architecture及其他既有缺口保持暂停，不以本次结果覆盖历史失败。

实际：九处替换及新增常量类已实施；五个文件展开常量并移除新增import后与before逐字一致，独立javac exit0，scoped diff-check通过。Broker聚焦命令exit1，未执行测试：`im-raft` 无法解析私有依赖 `com.co.kc.thirdparty.sofa:jraft-core:jar:1.4.1-imchat.1`（nexus-knight-repository）。依赖环境阻塞已明确归因，未修改依赖、未继续消费方或更宽验证。before、files.json、sources.diff、broker.log及独立编译输出保存于 `$CLAUDE_JOB_DIR/tmp/peer-identity-constants/`。本轮尚无集成编译或测试通过证据。独立实现Reviewer `ae9d95d394bdfb2f8` Revise、findings none：源码精确等价及范围独立复核通过，无源码修改要求，但必要窄验证被私有依赖阻塞，不能Accept；保持验证与Review步骤未通过，待依赖恢复后按原顺序补齐。无新增通用工程规则。


## T3 追加：五角色身份前缀固定化（2026-10-28）

事实源：[固定前缀设计](../../design-docs/2026-10-28-fixed-peer-identity-prefixes-design.md)，用户确认，独立设计Reviewer a7ce1f2aa262d78e2 Approved、必须项none。T3仍唯一active。

精确范围：common.constant.PeerIdentityConstants；Broker/Gateway SDK model下两个IdentityConfiguration及真实构造调用；Broker Server的BrokerProperties、BrokerRoutingProperties、BrokerGossipSettings、BrokerPeerAdapter、BrokerCallerIdentityInterceptor、BrokerPeerAdmissionInterceptor、BrokerRouteCallerInterceptor、BrokerClusterConfiguration、BrokerRequestConfiguration、RegistryGossipConfiguration、ClientConfiguration；Gateway Server的GatewayProperties、GatewayBeans、GatewayBrokerAuthorizer；Account的AccountBrokerProperties/AdapterBeans；Message的BrokerProperties/RemoteAdapterConfig；四Server application.yml；对应配置绑定/授权测试与所有直接测试构造调用（含E2E/HA）。Plugin生产代码不改。

- [x] readiness与SDK baseline exit0；baseline命令 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-sdk,im-gateway/im-ws-gateway/im-ws-gateway-sdk -am -Dtest=BrokerClientRoutingTest,GatewayClientTest -Dsurefire.failIfNoSpecifiedTests=false test`，16项全零；before及日志在job tmp/fixed-peer-prefixes。
- [x] SDK固定命名空间契约先行为RED，保留独立字面量、错误角色及配对冲突；再删除项目前缀参数/字段并用固定常量GREEN。
- [x] 实施Server所有项目级装配、授权、配置与测试构造迁移，删除九配置入口及四YAML中的对应键；保留generic插件、地址ID、证书、可信配对和两个身份对象。
- [x] SDK GREEN：`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-sdk,im-gateway/im-ws-gateway/im-ws-gateway-sdk -am -Dtest=BrokerClientTest,BrokerClientRoutingTest,GatewayClientTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [ ] Broker focused：`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=BrokerRoutingPropertiesTest,GatewayDevelopmentTargetConfigurationTest,BrokerGossipSettingsTest,IdentityAccessInterceptorsTest,BrokerObservationIdentityTest,BrokerObservationAdmissionTest,BrokerPeerAdapterTest,BrokerGossipSchedulerTest,BrokerObservationRequestConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`；已知私有JRaft阻塞未恢复则不重复同因构建。
- [ ] 消费方focused：`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-gateway/im-ws-gateway/im-ws-gateway-server,im-service/im-account/im-account-server,im-service/im-message/im-message-server -am -Dtest=BoltConsumerConfigurationTest,GatewayBrokerAuthorizationTest -Dsurefire.failIfNoSpecifiedTests=false test`；E2E/HA以同私有仓库 `-pl im-test/im-e2e-test,im-test/im-routing-ha -am -DskipTests test-compile` 验证，依赖阻塞不计通过。
- [x] 根Architecture、六份SDK/Server README、Gateway/Message局部Architecture中实际相关事实、Security、Harness Guide/Matrix同步；旧设计标记取代，历史不重写。静态入口/Plugin边界检查完成，独立实现Review实际结论Revise（非Accept），缺口照实保留。

实际证据：新增 `BrokerClientRoutingTest#rejectsBootstrapInCustomBrokerNamespace` 使用旧签名先执行RED，exit1且唯一失败为应抛异常但旧实现接受自定义命名空间；删除参数并固定规则后SDK final exit0，25项零失败/错误/跳过。消费方focused exit0，Gateway/Account/Message配置各11项及Gateway授权2项，共35项全零。Broker focused实际exit1，停在im-raft私有jraft-core:1.4.1-imchat.1解析，未执行测试；该环境归因明确后仅运行不依赖im-raft的消费方层，未重复同因构建。E2E/HA构造调用已迁移，受同一依赖阻塞未test-compile，不声称编译或真实拓扑通过。

文档/规范同步及配置入口/Plugin边界静态检查已完成，scoped diff-check exit0；全仓受影响31个SDK身份对象构造调用参数数目检查无误（不替代编译）。证据、before、task.diff、source-sha.json、result.json及本轮报告见 `$CLAUDE_JOB_DIR/tmp/fixed-peer-prefixes/`；Broker代理误将证据放入/tmp，已复制own.diff/files/static至本job目录，未删除原件。独立实现Reviewer `a28177bf77d7ebedd` Revise、源码findings none：固定值、身份/成员/唯一匹配、配对规则、配置移除、Plugin通用能力及81个直接构造调用静态核对无缺陷，但Broker focused与跨模块test-compile缺口必须补齐，不记Accept；Broker/跨边界验证步骤保持未通过。旧常量提取中的“可覆盖”行为被本设计取代，但其依赖失败和Revise不重写。

full/clean/module/architecture及既有DECODE/TLS缺口暂停不变，不提交；必要验证缺失时不得Accept或关闭T3。


## T3 追加：BoltTransportMode（2026-10-28）

事实源：[模式枚举设计](../../design-docs/2026-10-28-bolt-transport-mode-design.md)，用户批准含注释，独立设计Reviewer aa6dedabbf9c56d38 Approved、问题none。

- [x] readiness exit0，25个源码before及files.json保存job tmp/bolt-transport-mode；baseline命令 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=ImBoltAutoConfigurationTest,BoltRequestProcessorTest,BoltDevelopmentModeTest -Dsurefire.failIfNoSpecifiedTests=false test` exit0。
- [x] 新增im-bolt/model/enums/BoltTransportMode.java并补类和值注释，移除Properties内Mode；迁移files.json精确25文件引用，配置值/行为不变，README说明公开枚举。
- [x] 精确规范化token对比；重跑baseline窄命令；随后 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-gateway/im-ws-gateway/im-ws-gateway-sdk,im-gateway/im-ws-gateway/im-ws-gateway-server,im-service/im-account/im-account-server,im-service/im-message/im-message-server -am -Dtest=GatewayClientTest,BoltConsumerConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [ ] 保存精确差异/报告与局部独立Review；Broker/E2E/HA受私有JRaft缺失阻塞时不重复同因构建。full/clean/module/architecture暂停，T3不关闭，不提交。

实际：baseline及提取后Bolt窄命令exit0，29项全零；SDK/消费方命令exit0，39项全零。25个迁移Java文件在精确还原类型名、移除新增import及原内嵌枚举后非注释token逐项相等；旧源码类型引用清零。source.diff/source-sha.json/result.json及七份final XML保存job tmp/bolt-transport-mode。源码/二进制兼容变化已在README披露；Broker/E2E/HA未编译，不复用旧产物或解除既有Revise。局部独立Reviewer `a4053f269f0ea9d40` Revise、源码findings none，独立核对25/25 token等价及29+39项XML；必要跨模块编译证据缺失，不记Accept。无新增规则/checker；T3保留未验收。


## T3 追加：Broker bootstrap职责内聚（2026-10-28）

事实源：[职责内聚设计](../../design-docs/2026-10-28-broker-bootstrap-inline-design.md)，用户批准，独立Reviewer a6eeb49b80726e483 Approved、findings none。

- [x] 保存含对象引用20份before于job tmp/broker-bootstrap-inline；readiness及 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-sdk -am -Dtest=BrokerClientTest,BrokerClientRoutingTest -Dsurefire.failIfNoSpecifiedTests=false test` baseline exit0。
- [x] 仅BrokerClient接收List并内聚原校验、发现交集、target映射；删除BrokerIdentityConfiguration，同步files.json所有Java调用/测试及当前README，不改Gateway身份对象。
- [ ] 重跑baseline SDK测试与三个消费者 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-gateway/im-ws-gateway/im-ws-gateway-server,im-service/im-account/im-account-server,im-service/im-message/im-message-server -am -Dtest=BoltConsumerConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`，精确校验逻辑搬迁比较并独立Review。E2E因私有JRaft阻塞未编译时照实保留。
- [x] 用户追加BoltRequestContext六字段中文注释及observationsAllowed注释；按用户意见仅删除BrokerRouteCallerInterceptor构造器brokerRaftAdapter判空，无对应null构造测试命中，不扩展其他构造器。

本轮实际：SDK baseline19项exit0，迁移后SDK final21项exit0，消费者33项exit0，均零失败/错误/跳过。原校验、筛选和节点target方法体精确匹配，源码旧包装类型引用清零，scoped diff-check通过。before/source.diff/source-sha.json/result.json及final五份XML保存在 `$CLAUDE_JOB_DIR/tmp/broker-bootstrap-inline/`。新增测试在真实构造入口证明null与反向冲突于发现前拒绝、输入列表清空不影响启动目标及重复配对归一；不是新行为修复RED证据。附带Broker判空删除未运行对应Broker测试，E2E未编译，均受既有私有JRaft依赖缺口限制。独立实现 Reviewer `abd7615ac6f041374` 已完成只读审查：Revise、源码 findings none。Reviewer 独立核对 before、当前 Java 哈希及五份 XML，确认职责搬迁和安全拒绝保留；Revise 原因是必要 Broker 测试及 E2E 编译证据缺失，不要求恢复包装类或用户删除的构造判空。上述验证步骤保持未完成，不授予完整 Accept。mutable/dedup 用例仅证明输入快照及重复输入不影响一次成功 bootstrap，不宣称独立证明所有重复候选选择行为。

full/clean/module/architecture/commit继续暂停，旧Revise与DECODE/TLS缺口保留，T3不关闭。

## T3 追加：Gateway 目标职责内聚（2026-10-28）

事实源：[职责内聚设计](../../design-docs/2026-10-28-gateway-target-inline-design.md)，用户批准，独立设计 Reviewer aa00ec4801ac133f0 Approved。

- [x] readiness 通过；保存 files.json 七份 Java before；`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-gateway/im-ws-gateway/im-ws-gateway-sdk -am -Dtest=GatewayClientTest -Dsurefire.failIfNoSpecifiedTests=false test` baseline exit0。
- [x] 将 GatewayIdentityConfiguration 构造校验/快照、selectTarget、validateIdentity、endpoint 内聚至同 SDK GatewayClient，删除包装类；同步 ClientConfiguration、FrameProcessHandlerTest、FrameDispatchAppServiceTest、GatewayDevelopmentTargetConfigurationTest、GatewayClientTest 及 SDK README，原体比较并执行同一 SDK 命令。SDK baseline/final 各 6 项，零失败、错误或跳过；原构造校验及三个方法精确保留，源码旧包装引用清零，scoped diff-check 通过。
- [ ] 独立实现 Review 与必要跨模块验证：Reviewer `a578c69c59159b4d9` 已完成只读审查，Revise、源码 findings none，独立确认七份 Java 哈希、原体搬迁和 final XML 六项零失败。必要 Broker 测试与跨模块编译仍受私有 JRaft 缺失阻塞，故此步未完成，不以窄验证代替完整验收。

证据保存于 `$CLAUDE_JOB_DIR/tmp/gateway-target-inline/`。既有 Revise、DECODE/TLS 缺口及 full/clean/module/architecture/commit 暂停不变，T3 保持唯一 active。

## T3 追加：TLS 轮换拒绝取证修正

事实源：[测试侧证据设计](../../design-docs/2026-10-28-bolt-tls-rejection-proof-design.md)。用户批准方向，独立设计 Reviewer `aefe0c40fa1d38c8e` Approved、必须修改项 none；已授权仅测试文件实施，需核对实际 JDK21、连接隔离、原样委托、严格 cause 和失败路径回收。

- [x] 保存单文件 before 于 job tmp/tls-rejection-proof；原真实 RED 和运行报告见 closeout-diagnostics，readiness exit0。
- [x] 设计独立批准后，仅修改 `im-plugin/im-bolt/src/test/java/com/co/kc/imchat/plugin/bolt/connection/BoltAuthenticatedTlsTest.java` 的拒绝探针及必要 private fixture；原样委托实际 KeyManager，按连接关联现有 CONNECT 与原生握手原因，保留成功路径 DER 断言，不改生产 TLS 或固定协议版本。
- [x] 串行执行 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am '-Dtest=BoltAuthenticatedTlsTest#caOverlapRestartSequenceAllowsNewAndRejectsRetiredAuthority' -Dsurefire.failIfNoSpecifiedTests=false test`；再执行同入口 `-Dtest=BoltAuthenticatedTlsTest`，保存实际 argv/exit/当前 FQCN XML；失败先归因，不扩大门禁。JDK21 下单用例 1 项及全类 18 项均 exit0，零失败/错误/跳过；证据 `job tmp/tls-rejection-proof/{implementation.diff,implementation-results.json,targeted.xml,full-class.xml}`。当前源码 SHA 与结果匹配，原 RED 不覆盖。
- [x] 检查选链 DER/关联连接/非信任失败负例，独立实现 Reviewer `aefe0c40fa1d38c8e` Scoped Accept、findings none；独立核对精确 before/current diff、SHA、JDK21 两份 XML、真实 KeyManager 调用顺序、严格 native cause 与资源关闭。本局部 Accept 不解除私有 JRaft 或历史 DECODE/完整验收缺口，也不证明跨 provider 或重复运行稳定性。

## T3 追加：DECODE 阶段预算证据修正

只读调查确认：旧测试 1000ms 同时承担冷 MTLS 和阶段超时刺激；assertLocal 可被解码前失败满足。历史 expected1/actual0 原因链缺失，保持未指派，不推断为确定冷握手耗时。当前全类基线 24 项通过不关闭该证据缺口。

本轮为单测试文件局部维护，不改生产预算或算法。范围 `im-plugin/im-bolt/src/test/java/com/co/kc/imchat/plugin/bolt/invocation/BoltUnifiedInvokeTest.java` 中原参数方法；现有 UNIT_TEST_GUIDE 的阶段副作用与具体 cause 规则适用。

- [x] 保存 before 和全类基线 XML 至 job tmp/decode-budget-diagnostics；独立只读调查完成，定位可混淆的观测条件。
- [x] DECODE 使用 3000ms 传输余量，真实 fromJson 完成后推进精确同量预算，断言解码一次、真实响应、Handler 一次及原始 LOCAL/TimeoutException 原因；序列化分支保持 1000ms。未额外修改旧测试作临时诊断，复用已保存原始失败/成功诊断，历史具体失败边界保持未指派，不声称已补得当时 cause。
- [x] 执行 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am '-Dtest=BoltUnifiedInvokeTest#productionCallerBudgetAndPreparationFailureRetainLocalOrigin' -Dsurefire.failIfNoSpecifiedTests=false test`；禁用 offset 推进负对照在 timeout 断言失败，恢复最终原 bytes 并处理下述旧 class 后重跑相同命令，再运行 `-Dtest=BoltUnifiedInvokeTest` 全类；已保存每轮 diff/SHA/argv/exit/当前 FQCN 报告，实际结果见下段。
- [x] 独立实现 Reviewer `aefe0c40fa1d38c8e` Scoped Accept、findings none；独立核对精确差异、最终 SHA、五份 XML 和负对照，确认阶段取证加强且序列化分支不变。成功仅修正当前测试的阶段取证，不冒称历史每次失败已找到确定边界，也不保证任意慢机器网络必在有限预算内成功。JRaft 与完整验收阻塞独立保留。

DECODE 本轮实际：修正窄测 3 项 exit0；禁用 offset 推进负对照 exit1，仅 DECODE 因 failure 为 null 在超时断言失败，解码完成及 hook 一次断言此前通过。恢复时 copy2 保留旧 mtime，首次 restored 仍执行较新负对照 class，exit1，记录保留；核对源码与 final.java bytes 相等后更新时间戳触发编译，restored-fresh 3 项 exit0，全类 24 项 exit0，均零失败/错误/跳过。未把旧产物失败改记为新源码回归，也未把它静默丢弃。日志、各轮当前 FQCN XML、精确 diff/SHA 和 implementation-results.json 保存 `job tmp/decode-budget-diagnostics/`。独立实现 Review 已获上述局部 Accept；未运行 module/architecture/full。

## T3 继续集成验证

- [x] TLS/DECODE 局部 Accept 后执行 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test`，exit0；按当前源码 FQCN 核对 15 份报告，284 项零失败/错误/跳过。证据 `job tmp/bolt-closeout-module/`。运行中保存的 Bolt 源码快照至结束无变化，但不是运行开始前完整冻结证明，不替代最终 full。
- [x] 执行 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-gateway/im-ws-gateway/im-ws-gateway-server,im-service/im-account/im-account-server,im-service/im-message/im-message-server -am -Dtest=BrokerClientTest,BrokerClientRoutingTest,GatewayClientTest,BoltConsumerConfigurationTest,GatewayBrokerAuthorizationTest -Dsurefire.failIfNoSpecifiedTests=false test`，exit0；当前 FQCN 七份 XML 共 62 项零失败/错误/跳过，证据 `job tmp/bolt-closeout-consumers/`。

私有 JRaft 缺失仍阻塞 Broker/E2E/HA 与最终完整验收。未运行 full/clean，不提前归档。

## T3 Harness 与廉价门禁

- [x] `TMPDIR="$CLAUDE_JOB_DIR/tmp" bash scripts/test-harness.sh` exit0，临时 fixture 隔离在 job tmp；这是合同自测，不是真实 Maven reactor/HA。
- [x] 自测结束后顺序执行 `./scripts/verify.sh drift` exit0、`./scripts/verify.sh clean` exit0。clean 是检查，不删除用户文件。日志和退出码保存 `job tmp/bolt-closeout-harness/`。

本轮未运行 full：私有 JRaft 原件未恢复，不重复同因解析失败。上述自测/廉价门禁及此前窄验证不是冻结输入的完整验收，状态回填也不刷新旧机器证据；T3 保持 blocked、不归档。

## T3 阻塞交接检查

- [x] `./scripts/verify.sh cleanup` exit0，仅扫描，未加 --apply；没有自动删除用户或未知资源。
- [x] `./scripts/verify.sh handoff` exit0，生成 `.harness/session-handoff.json` 和 `.harness/session-handoff.md`。这是交接产物生成成功，不是 quality Accept 或 full 通过；状态回填后需按现有 fingerprint 规则看待 freshness。
- [x] 再次检查隔离与默认 Maven 仓库，私有版本目录均只有 jar/pom 的 lastUpdated，没有原件。没有新的恢复前提，不重复构建解析失败或同一批通过检查。

剩余入口已收敛为外部依赖恢复：需环境所有者提供原始 JAR/POM 或合法可访问的原制品来源，核对顶部所列 SHA 后再恢复验证。若原件永久不可恢复，则必须单独批准并完成 third-party recipe 的来源/构建/Review 验收，不将重建或版本替换默认为本任务权限。当前没有可据以授予完整 Accept 的证据，T3 保持 blocked，计划不移入 completed。

## T3 私有 JRaft 重建授权与检查点

用户明确授权推进 `third-party/jraft-applied-membership/` 重建及验收，不采用公开二进制替代。本授权取代此前「不可自行重建」的当前调度限制，但不解除来源校验、独立 Review、完整双 verify、输入审查和消费者验收，不授权发布。

- [x] readiness exit0；`python3 -I -m unittest discover -s third-party/jraft-applied-membership -p test_build.py` exit0，43 项通过；日志 `job tmp/jraft-rebuild/fixture-baseline.log`。重复 ZIP 条目警告来自拒绝边界 fixture，不是制品构建。
- [x] 独立 Reviewer `a51c4707223cdc2aa` Scoped Accept、无 capture 前必须修改项，仅批准当前 recipe 静态实现进入 capture-input 检查点，不是制品或生产采用 Accept。
- [x] 恢复固定 commit 的 pristine 完整源码及可审计来源映射。旧 tar 和 commit 记录均不存在；新归档字节不同，已核验完整 951 文件清单、executable flags、法律文件和原补丁 apply-check，经独立 Scoped Approved 后更新 provenance，历史归档 SHA 保留。
- [ ] 来源准入后按 README 原命令执行 capture-input 双 verify，检查报告/实际输入/原始模型审计/payload 比较；外部有效模型或解析证据缺口、reviewed lock 和实际 verify-lock 仍待完成，不以 capture 通过代替采用。

当前没有运行重型 JRaft 构建、install、publish 或消费者依赖切换。验收后的持久化保存位置须与临时构建证据分开，避免唯一制品再次依赖 /tmp。

## T3 当前决定：先评估私有 JRaft 必要性

用户质疑维护私有分支的必要性并批准先行评估。当前暂停重型双构建、provenance 切换、安装和消费者依赖切换；仅只读分析补丁生产调用、所需一致性保证与官方接口替代能力。不得因临时制品丢失自动把保留私有分支视为既定决策，也不得未经行为评估直接换官方包。

源码恢复已有独立 Reviewer `a51c4707223cdc2aa` Scoped Approved：官方固定 commit 下载经根前缀去除后，951 文件清单和 executable flags 精确匹配；允许的来源映射更新尚未实施。此结论不越过当前暂停，不是制品采用批准。下一步以必要性评估结论选择保留补丁或另行设计迁移，原验收要求继续保留。

## T3 当前顺序：草案后置，先收口现实现

用户明确要求：先保存官方 JRaft 治理简化方案，继续当前实现收口，由用户自行提交后再推进方案。草案见 `docs/design-docs/2026-10-28-official-jraft-governance-simplification-design.md`，不实施其中任何生产/依赖变更。

本段取代上一节必要性评估期间的重建暂停指令：按现有私有依赖推进来源核验和双构建。恢复归档及映射已获独立 Scoped Approved，窄更新 provenance/README；固定 commit、951 文件清单、patch/legal SHA 不变。heavy capture 前重跑 recipe fixture，成功后使用恢复归档和新隔离输出执行 README 的原 build.sh 命令。缺口不自动转为接受，不授权发布/提交。

恢复来源更新后 recipe fixture 再次 exit0。首次尝试启动固定官方 commit `cb1a0c7ac16ff146ad8abb04b1bcb035270bb234` 的隔离 capture 时，被工具权限以执行外部下载源码为由拒绝，该次构建未启动；未绕过权限。未来治理草案已独立审阅可保存为 DRAFT，不实施。

### 2026-10-29 实际构建恢复

用户随后明确批准上述固定官方来源/commit、现有私有补丁的隔离双构建，不发布。已实际启动 `JRAFT_SOURCE_ARCHIVE="$CLAUDE_JOB_DIR/tmp/jraft-rebuild/download-20261028/upstream-source.tar" JRAFT_BUILD_OUTPUT="$CLAUDE_JOB_DIR/tmp/jraft-rebuild/capture-20261029" ./third-party/jraft-applied-membership/build.sh`，日志/退出码位于 job tmp 的 `jraft-rebuild/capture-20261029.log`、`capture-20261029.exit`。实际 exit1 及后续窄修见下段，不重复启动同一输入、不预填通过。readiness 再次 exit0；外部模型证明、输入锁及后续集成/完整验收缺口仍保留。

并行环境预检：`docker info` 无法连接当前 `desktop-linux` socket。已执行常规 `open -a Docker` 和官方 `docker desktop start`，但随后 `rtk proxy docker desktop status` 仍报无法取得状态，`rtk proxy docker info` 仍报 daemon 不可连接；不能把包装命令 exit0 当成 engine 可用。现有 Electron 日志显示 `ECONNREFUSED backend.sock`，未修改 Docker 设置、终止进程、重置运行时或删除容器数据。真实 HA 前必须恢复并实测 Docker engine；此环境缺口独立于 JRaft 构建，暂不重复启动同因失败。

第一轮实际报告已出现 `NodeTest` 64 项、1 failure：`testRemovingLeaderTriggerTimeoutNow` 在 removePeer 完成回调、固定 100ms 等待后，`getLeader()` 为 null，失败于 `NodeTest.java:2931`。该轮现已结束，capture exit1；99 份 fresh XML 共 473 项、1 failure、0 errors、2 skipped，唯一失败为上述方法。第二轮目录未创建，accepted-comparison 不存在，recipe 正确传播首轮失败，不把部分测试或产物当成通过。完整汇总 `job tmp/jraft-rebuild/capture-20261029-failure-result.json`。已保存当前 XML SHA 与失败摘要 `job tmp/jraft-rebuild/first-round-node-failure-20261029.json`，失败 capture 全部 4135 个文件的 SHA 冻结于 `capture-20261029-failed-evidence-sha256.json`。独立只读诊断确认 removePeer 成功闭包不构成新 Leader/FSM 就绪屏障，现有 error 级日志不足以证明 TimeoutNow 实际发送/处理及具体选举时序；补丁未直接改该生产路径，但不能由静态差异排除间接调度影响。不跳过断言、不扩大 timeout、不自动整套重跑；现有批准只允许 NodeTest#testSnapshotSync 修改，不因文件 allowlist 自动取得其他方法修改授权。

独立源码 Reviewer `a3213941f7fab1312` 返回 Block：RUNNING Client 的 Manager 在 creation.awaitTermination 中断/超时抛错后，原生 RpcClient.shutdown 后续 scanner/UserProcessor 清理被跳过，受控终态又阻止重试。已核对当前 Manager/Client 控制流，最小修复设计已由 Reviewer `a1f91e577618ebd65` Approved，原生拥有者同步作用域实现后，真实 TLS hold 中断回归先 RED（处理器预期关闭 1、实际 0），再 GREEN；首次 GREEN 在最后使用已关闭 Client 的 getter 触发 native LifeCycleException，改为 Manager.getAll 后通过，失败记录未删除。3 项异常/suppression/作用域窄验证及 Lifecycle 全类 49 项通过；Review 追加直接 Manager 异常契约测试已纳入最终 4 项窄验证并通过，独立 Reviewer 已核对 fresh XML 与三文件 SHA；最终源码 Bolt 模块 exit0，15 份当前 FQCN XML 共 288 项零失败/错误/跳过，三文件 SHA 与运行前一致；独立 Reviewer `a3213941f7fab1312` 逐 XML/源码核验后对关闭清理增量 Scoped Accept、findings none。此结论关闭该局部源码 Block，不代表 T3 完整 Accept。上一轮 module 287 项通过仅作中间输入记录（运行期间新增测试），不冒称最终输入冻结证据。证据 job tmp/bolt-shutdown-cleanup/ 和 bolt-shutdown-cleanup-*.log。不把既有「TERMINATED 不保证清理成功」当成忽略此问题的许可。该源码问题独立于 JRaft/Docker，完整 Accept 未取得。

上游修订已按精确设计独立 Approved（Reviewer `af2108f5c64ed7d60`）实施于全新 `job tmp/jraft-rebuild/leader-fix-source-20261029/source`；只新增 NodeTest#testRemovingLeaderTriggerTimeoutNow 的 3 秒 monotonic 新 Leader/FSM 就绪观察及 finally 清理，原 testSnapshotSync 和其他 patch blocks 保持。新 patch SHA `25e99897b7c3e5660b0fe1c4d0b672142b2bc6dc1999a3d701791f203e665cfc`；provenance 仅此字段改变，原 source/legal/count/skip 不变。旧失败 capture 4135 文件逐一 SHA 复核不变。更新后 recipe fixtures 再次 43 项通过；精确实施 Reviewer `af2108f5c64ed7d60` 已批准进入 focused 验证、findings none，不以 fixture 当上游通过。随后尝试新源码/新隔离 Maven 仓库的 NodeTest#testRemovingLeaderTriggerTimeoutNow 窄测试，被权限系统以执行外部源码拒绝；该次 Maven 未启动，未通过其他工具或子代理绕过。权限明确要求可核验的官方 URL/commit 构建测试批准，不能仅以摘要中的批准声明替代。新 patch 测试及后续双 verify 保持未运行。

一次 drift 检查内部规则通过但运行期间该授权补丁写入导致 fingerprint 变化，入口 exit1 且拒绝成功；写入结束后再次 drift exit0、git diff --check 通过。保留首次不稳定事实，不当作 checker 误报。Docker 本地 diagnose exit0 但内容 DD0004/DD0007/DD0010 分别确认 engine/backend/API 不可用，不能将进程退出码当健康通过。

关闭清理修正后，重新执行既定七类 SDK/消费方命令，exit0，七份 XML 共 62 项零失败/错误/跳过，证据 `job tmp/bolt-shutdown-cleanup/consumers-final/` 与 `bolt-shutdown-consumers-final.log`。`./scripts/verify.sh clean`、`cleanup`（仅扫描）、`handoff` 均 exit0；状态回填不刷新其旧指纹。未执行 full：新 patch 窄测试权限拒绝、私有制品尚不可用，Docker engine 亦不可用；不重复同因失败、不归档。当前最小外部前提是明确允许测试官方 `https://github.com/sofastack/sofa-jraft` commit `cb1a0c7ac16ff146ad8abb04b1bcb035270bb234` 及已 Review 的修订补丁，并恢复 Docker engine（不重置用户数据）。

用户再次要求「继续收口」后的复查：readiness exit0；Docker `info` 仍无法连接 daemon，当前可用磁盘约 68 GiB，不再沿用此前约 7.6 GiB 的磁盘状态。尝试既定修订方法的隔离 Maven 窄测试再次被权限系统拒绝，明确指出「继续收口」未指明外部来源，不构成该源码执行的明确批准；Maven 未启动。未换工具或代理重试，未运行更宽验证，未将本次请求记录为权限已解除。T3 仍 blocked，等待精确来源/commit 的执行授权和 Docker 恢复。

用户随后明确授权官方 `https://github.com/sofastack/sofa-jraft` commit `cb1a0c7ac16ff146ad8abb04b1bcb035270bb234` 应用当前已 Review 补丁的隔离构建测试、不发布。相同来源和既定隔离目录的 `NodeTest#testRemovingLeaderTriggerTimeoutNow` Maven 窄测试已实际启动，日志为 `job tmp/jraft-rebuild/leader-fix-focused-20261029.log`，结果待返回。权限拒绝作为历史保留，当前执行授权已具备；不预填测试通过，不改变 Docker、外部模型审核、输入锁及完整验收缺口。

授权后的首次窄命令 exit1，尚未执行测试：从 IM 仓库用 `-f` 指定上游 POM，未改变上游 `${main.user.dir}=${user.dir}`，license 插件误找 IM 根下的 `tools/codestyle/HEADER`。已核对该文件在隔离上游根实际存在，失败是本次命令工作目录错误，不归因为 Leader 断言或依赖失败。原日志保留；改为在已授权的隔离源码根执行，与 recipe 的 cwd 相同，不改 POM、不跳过插件；修正运行日志为 `job tmp/jraft-rebuild/leader-fix-focused-corrected-20261029.log`，实际 exit1：1 项、0 failures、1 error、0 skipped；setup 的 `RaftTimerFactory` ServiceConfigurationError 及 teardown 的 NodeImpl 初始化失败，尚未执行 Leader 断言。源码四个 SPI service descriptor 均存在，但 target/classes 缺少这些 descriptor；运行 Java 21.0.10/Azul 与原 capture 相同。当时仅确认资源输出缺失，未手工补资源、跳过断言或扩大测试。

只读诊断随后定位旧版资源复制时间比较：隔离源码 13 个资源 mtime=0，缺失目标的 lastModified 同为 0，maven-resources-plugin:2.6 → maven-filtering:1.1 → plexus-utils:2.0.5 在默认 overwrite=false 下以 destination.lastModified < source.lastModified 判定，导致这些资源未复制；原 capture 源资源时间非零。Copying 15 resources 是扫描数而非实际复制数。compiler createdFiles 清单仅含 class，不支持 compiler 删除 SPI 的假说。父会话复核新副本的资源时间和 SHA，确认四个 SPI 均为 epoch zero；在全新 `job tmp/jraft-rebuild/leader-fix-resource-20261029/source` 保持字节和时间，仅增加插件公开参数 `-Dmaven.resources.overwrite=true` 运行相同 focused 测试，日志 `leader-fix-resource-20261029/focused.log`，结果待返回。该命令修正不修改仓库 recipe 或补丁，不代表正式双构建通过。实际 focused exit0，fresh XML 1 项零失败/错误/跳过，四个输出 SPI descriptor 与源字节一致，结果和 XML SHA 已保存 `leader-fix-resource-20261029/focused-result.json`。随后相同隔离环境运行完整 `-Dtest=NodeTest`，日志 `leader-fix-resource-20261029/node-full.log`，结果待返回；focused 原始日志和 JSON 保留。正式 recipe 的 extract_source 用 write_bytes 写出文件，不沿用 tar mtime，故不因本次独立准备目录问题修改 recipe；正式双轮仍使用原流程。

完整 NodeTest 随后结束 exit0，fresh XML `tests=64, failures=0, errors=0, skipped=0`，suite time 735.105 秒。用户随后告知已启动 Docker；当前 docker info 实测及正式 `capture-leader-fix-20261029` 新双轮启动均因工具判定服务超时而未执行，不能据此称 daemon 仍不可用、双轮失败或授权撤销。尚无新双轮构建结果。

### Pi 会话恢复收口（2026-10-29）

用户再次要求收口。当前会话已执行 `./scripts/verify.sh readiness`，exit0；`docker info --format '{{.ServerVersion}}'` 实际返回 `27.4.0`，exit0，取代旧 daemon 不可用结论。构建前无并发 Maven/build.py；磁盘可用约 64 GiB。`python3 -I -m unittest discover -s third-party/jraft-applied-membership -p test_build.py` 实际 43 项通过，exit0；重复 ZIP 条目 warning 来自拒绝边界 fixture。

核对恢复归档 SHA 与当前 patch SHA 精确匹配 provenance 后，按现有授权启动原 `build.sh` 双轮 capture-input，不改 recipe、不 install/publish、不切换依赖。证据根为 `/Users/kc/.claude/jobs/bb58725d/tmp/jraft-rebuild/capture-pi-closeout-20261029/`，`launch.json` 保存 PID/来源/补丁身份，`run.log` 保存原输出，完成后 `result.json` 保存真实退出码与起止时间，recipe 输出在 `output/`。T3 恢复唯一 active；尚未取得本轮构建结果，不提前勾选验收步骤。外部有效模型、reviewed lock、verify-lock、消费者及最终完整验收/独立 Accept 仍待完成。

用户要求继续推进后，已安排隔离上下文 Reviewer 只读审查 recipe 与外部模型证据门槛。Reviewer handle 为 `term_125b80ef-9be2-4a80-9714-c1699e8128ae`，报告唯一输出为 `/Users/kc/.claude/jobs/bb58725d/tmp/jraft-rebuild/pi-input-review.md`；不运行并发 Maven、不修改构建现场，不预授 inputInventoryReview。其任务是明确现有门槛的最小证据闭环，不降低要求或实现通用 resolver。报告现已返回 **Revise**，不授予 inputInventoryReview 或采用 Accept。R1 是外部 descriptor 有效模型/参与边界证据仍缺失；R2 是已知保留 profile 的缺 version 坐标对象可在移除 profile 前漏检，父会话已通过无写入内存反例复现。未证实当前固定模型触发 R2，不中断现有 capture；构建期间不修改 recipe。capture 结束后先冻结报告和原输入，再按既有不放宽契约补 R2 的 RED/GREEN 及独立复核，并依 R1 的分层审计步骤关闭相关未决声明，不以物化 release 路径冒充无动态解析证明。复核摘要在 `jraft-rebuild/pi-review-reception.json`。

当前会话另执行 `./scripts/verify.sh drift` 和 `./scripts/verify.sh clean`，均 exit0；clean 提示有未跟踪文件需 scope Review，并不表示工作区无改动。保存了 capture 启动后的 `observed-worktree.json`（2761 个有效文件），仅用于后续漂移比较，不冒称运行前冻结。双轮运行结果仍待返回；任何中间 XML 或 ERROR 日志均不单独作整轮结论。

#### R2 窄修准备（独立副本，不改变运行输入）

文件范围：`third-party/jraft-applied-membership/{build.py,test_build.py,README.md}`，必要的 owning 设计/Harness 规则说明和当前计划；不改 patch/provenance/toolchain 或固定源码。当前仅在 `jraft-rebuild/profile-version-fix/` 独立副本准备，仓库 recipe 保持原字节，不将副本通过当仓库通过。

- [x] 在副本新增 `test_known_profile_coordinate_objects_require_versions`：两个已知 profile、八种既有坐标对象缺版本拒绝，固定版本和空区段正例；先以旧 build.py 执行完整 fixture，RED exit1，16 个缺版本 subtest 均为未抛 Rejected，不是加载或语法错误。
- [x] 副本仅将既有坐标对象版本检查扩展至 root 和 `profiles/profile`，执行 `python3 -I -m unittest discover -s /Users/kc/.claude/jobs/bb58725d/tmp/jraft-rebuild/profile-version-fix -p test_build.py`，GREEN exit0，44 项通过。原 43 项保留；red.log/green.log/baseline-sha.json 在该目录。
- [x] 原 Reviewer 对照副本/仓库精确 diff、SHA 和测试记录独立复核，报告 `profile-version-fix/review.md`：局部 Accept、findings none；确认既有契约 bugfix 无需新设计。Reviewer 在内存分别使用旧/新 recipe 跑 8 项有效模型测试，旧版仅新增 16 subtest 失败，新版全部通过；当前 build-1 模型经两 validator 返回相同消费 POM。仅绑定副本 SHA，R1 和仓库落地/验证仍未关闭。
- [x] capture 自然结束 exit0 后冻结全部 8462 个输出文件 SHA，仓库原 recipe 基线与 reviewed before 一致后落地窄修；两文件与获审副本字节一致。README 补既有 profile 检查和四层证据说明，Harness Guide/Matrix 登记外部上下文 Review-only 及其自动化条件，不改变审核策略。
- [x] 在仓库执行 `python3 -I -m unittest discover -s third-party/jraft-applied-membership -p test_build.py`，44 项 exit0；`./scripts/verify.sh drift` 和 `git diff --check` 均 exit0。新 validator 只读复核两轮冻结 effective POM 均通过，消费 POM 字节与原 payload 相同，结果 `profile-version-fix/repository-validation.json`。这不是新 recipe capture 重跑；verify-lock 仍须实际执行。

R1 外部模型证据继续独立未关闭；未启动并发 Maven、未安装/发布、不修改正在运行的 recipe。

R1 零 Maven 成本准备：生成 `jraft-rebuild/external-model-draft-audit.json`，保留当时物化文件 SHA 和全部 1983 条非固定原始声明（144 个 POM）。分类字符串最初误用 `fixed` 导致临时统计包含固定项，已更正为实际 `fixed-version-declaration` 并过滤重存；这只是临时诊断计数修正，不涉及生产审核逻辑。按 XML 路径暂分 descriptor-coordinate 1276、build/reporting 707，仅为句法分类，未据此认定实际参与/不参与。五条显式动态声明均为外部 POM 的 build plugin 声明；1978 条未解析属性仍需逐项模型/角色证据，不声称它们都是休眠或已固定。现场仍变化，此草稿不能当最终 inventory 或 input review。

本次轮询确认 **build-1 已完成 BUILD SUCCESS**（日志结束 `2026-10-29T03:40:38Z`），原 recipe 已生成 round result 并自然进入全新 build-2。父会话用原 `check_reports` 再核对 99 个 suite、473 tests、零失败/错误、1 项 reviewed skip（ConnectionRefreshTest#simulation）；另一个 reviewed skip 对应用例本轮实际通过，契约允许，无放宽。791 项输入、三个 payload SHA 及 build-1 全部 4230 个文件 SHA 保存于 capture 根 `round-1-frozen-evidence.json`，payload 字节摘要逐项匹配 round result。此为第一轮真实成功，不是双轮、input review 或 adopted Accept；第二轮仍运行，不修改 recipe、不启动重复构建。

随后以第一轮冻结 SHA 逐一复核 4230 个文件均未变化，从最终 `raw-model-audit.json` 生成 `jraft-rebuild/r1-frozen-model-queue.json`：6497 条原始声明，4277 条固定、2215 条未解析属性、5 条显式动态；非固定共 2220 条、158 个 owning POM。此前 1983/144 是运行中草稿观测，不替代本轮冻结队列。队列逐 POM 保存原字节 SHA，全部保留 unresolved，不以句法分类自动授予输入接受。原 Reviewer 已收到基于冻结第一轮的 R1 只读静态核验任务，优先核对五条动态声明的 owner/继承/执行角色及 error_prone_parent 的 snapshot 属性实际引用；唯一新报告为 `jraft-rebuild/r1-static-evidence.md`。不启动 Maven、不修改现场，不将该有限子集审查等同全部 R1 关闭。

R1 静态报告已返回：整体 **Revise**，指定六项疑点在冻结第一轮/标准 Maven 消费语义范围内静态关闭。五条显式动态 build 声明不沿 dependency 边导入 JRaft 或十个实际执行插件的自身模型；Reviewer 核验了项目/插件 parent 链和日志 goal，而非凭 JAR 缺失排除。error_prone_annotations 确实以 test scope 引用父属性的 JUnit SNAPSHOT，但该传递 test 边在当前 Maven 标准 collector 中先于版本解析被 scope selector 排除；不冒称外部模型没有 SNAPSHOT 声明。其余属性声明仍需有限模型/参与边界审核，未授 inputInventoryReview 或采用 Accept。当前第二轮已出现 99 份 XML/473 项零失败错误、1 skipped，正在打包；最终退出码和双轮比较未产生前不算 capture 通过。

### 双轮 capture 完成与 R2 落地

原 capture 实际于 `2026-10-29T04:00:36.461012+00:00` 结束 exit0（开始 `03:19:24.370837+00:00`）。两轮均 99 suite/473 tests、零失败/错误、同一 reviewed skip；父会话重新执行报告契约与 payload SHA 校验，两轮 791 项输入及 binary/sources/POM 摘要精确相等。`run.log` 明确 `INPUTS_CAPTURED_NOT_LOCK_VERIFIED`，`accepted-comparison.json` 不存在；没有冒称采用批准。全部 8462 个输出文件 SHA 冻结于 `capture-frozen-evidence.json`。随后 R2 已按上述步骤落入仓库并通过窄验证；原 capture 仍绑定旧 recipe，历史结果不改写。

最小下一步：在冻结 capture 的独立副本中，按 R1 报告对剩余相关 descriptor 使用 Maven 原生模型工具取证；仅 `-f` 缓存 POM 副本、空 settings、相同工具链及隔离 HOME/仓库，优先离线，不使用 `-Dartifact`、不执行额外 verify、不安装/发布。先做一个固定 descriptor 的最小探针，明确该模型与原 descriptor 上下文的等价限制；离线缺依赖或模型错误先停并归因，不自动联网或降低规则。之后再独立审核输入、生成 reviewed lock、实际 verify-lock，消费者与最终 clean/full/quality 不提前执行。

首个原生模型探针已实际执行 exit0：从第一轮冻结仓库复制到 `jraft-rebuild/external-model-probe/`，隔离 HOME/TMPDIR、空 settings、同工具链，用 `mvn -o -N ... -f <copied error_prone_annotations-2.2.0.pom> org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom -Dverbose=true`，未使用 `-Dartifact` 或 lifecycle。完整 argv/UTC/exit 在该目录 `result.json`，日志 `probe.log`，输出 `annotations-effective.xml`。实际模型确认唯一直接依赖 junit:4.13-SNAPSHOT、scope=test，与独立静态报告吻合；这不证明所有外部模型或原 capture 解析 trace，不将该 test 边误记为动态解析 Block。剩余模型仍需有限核验和独立审核。

当前按同一已验证探针串行处理冻结队列 158 个 owning POM，辅助取证脚本仅保存在 `external-model-probe/collect_models.py`，不进入仓库、不实现 resolver。复制缓存逐 POM 源 SHA 需匹配队列，环境/参数与单项探针一致，fully-qualified help goal、`-o -N -f`、每项 60 秒、首错停止；不执行 lifecycle、不联网、不改冻结 capture。PID 与上限在 `collection-launch.json`；每项 argv/UTC/exit/output SHA 在 `models/results.json`，结束或首错标记在 `models/completion.json`，原始日志/有效模型逐项保留。开始前无并发 Maven。模型生成成功仍不等于原 descriptor 上下文一致或输入接受，须逐字段角色判定及独立 Review。

该批实际已在第4项首错停止：前三模型 exit0；`jackson-bom:2.8.11.20180608` 作为项目展开时尝试加载自身 `maven-scm-provider-gitexe:1.9.1` build extension，离线缺该 JAR 和 plexus-utils:1.1，exit1。这证明 `-f` 项目展开也有与原 dependency descriptor 不同的扩展加载边界，不能以补下载该扩展消除语义问题。原命令/错误保存在 `models/004.log`、results/completion，未联网、未继续后项或盲重跑。下一步已交原 Reviewer 只读核对本机原生 ModelBuilder/processPlugins=false 及冻结仓库 ModelResolver 的最小辅助取证可行性，报告 `jraft-rebuild/r1-model-extension-boundary.md`；不直接实现通用 resolver，不将取证方法失败归因为当前 JRaft 制品错误，也不降低 R1 门槛。

Reviewer 的替代边界报告已返回 Revise：允许一次性原生 ModelBuilder 辅助模型方向，但 processPlugins=false 之外还必须无 ProjectBuilder/listener，并约束 ModelSource2 relative lookup。父会话据此仅实施 `jraft-rebuild/descriptor-probe/Probe.java` 单对象工具，不进入仓库或 production recipe：冻结 POM 精确索引与 SHA 校验、无网络版本选择、repository 声明仅记录、relative source 读取也受冻结索引限制；原生工厂负责继承/插值/BOM，不自行实现其语义。javac 与单 Jackson BOM 的 java 进程均实际 exit0，读取仅该 BOM 和两级 parent，未加载 SCM extension。证据 result.json、tool-jars-sha.json、jackson-result/evidence.tsv/effective.xml 原样保留。

该辅助 request 使用当前 JVM system properties 加三个已记录 CLI user 值，明确不是原 Maven session 完整属性；env/maven/cwd及 locationTracking 差异仍须按实际 profile/属性敏感性审核。当前 profile activation 输出仅 toString，不能当激活条件完备证据。单对象 source/输入旁路及可否扩展有限队列已交原 Reviewer，唯一报告 `descriptor-probe/review.md`；未返回前不扩大队列，不授 input Accept。

单对象后补独立边界反证 `descriptor-probe/BoundaryTest.java`，未修改 Probe 原件：临时 fixture 验证允许冻结读取、未知文件/SHA变化拒绝、目录外文件与符号链接拒绝、relative parent 旁路拒绝、缺失 relative 返回 null、动态/SNAPSHOT/属性版本拒绝及缺失固定 POM 拒绝。javac/java 均 exit0，实际 14 条断言通过；argv/exit 在 boundary-result.json，输出 boundary-1.log。未使用真实 capture 作可写 fixture。另只读核对 Jackson BOM 及两级 parent：仅 oss-parent 有无 activation 的 release profile，没有该链依赖坐标受 JDK/OS/file activation 影响的证据；完整上下文完备性仍交 Review，不据此认定所有模型等价。

Probe 独立复核 `descriptor-probe/review.md` 返回 Revise：F1 activation/context 可复核输出不足，F2 PrintWriter 吞错，F3 校验与解析流二次打开不绑定。仅修一次性 probe（不改 repo）：新增 ProfileTest 在旧版先 RED 缺结构化 context；BoundaryTest 增加流绑定/输出写失败，旧版先 RED 读到已变字节。随后 getInputStream 以同一 byte[] 校验并返回 ByteArrayInputStream、8 MiB上限；checkError 失败抛 IOException、全部写出后才 complete；activation 结构及关联属性 present/absent/来源/原session UNKNOWN明确输出，敏感键值redact，file activation保留UNRESOLVED而不伪造原存在性。`-proc:none` 编译exit0（保留deprecated API note），BoundaryTest/ProfileTest GREEN均exit0，命令在 revision-tests.json，旧失败日志保留。修订后单Jackson输出 `jackson-revised/` 实际exit0，revised-result.json记录。已交原Reviewer复核 `review-revised.md`，原 Reviewer 的 `review-revised.md` 已返回局部 **Accept**、findings none：允许以获审 SHA 生成有限辅助模型；F1/F2/F3 本次修正关闭，原 session 等价和全部 R1 仍 Revise，file/context 敏感项必须 unresolved。Reviewer 特别说明 constructor 大小读取上限并非严格对抗任意输入保证、fixture字符串断言较弱但已独立解析实际版本1→2，当前冻结无写者用途不阻断。按此边界启动 `descriptor-probe/collect.py` 的 `batch-1/`，冻结队列上限158、每项30秒、单java进程串行首错停止，不使用Maven或网络；每项argv/UTC/exit/output SHA/inputReview=unresolved保存在 results.json，file activation另标记，completion.json只表示辅助材料生成。PID与获审SHA在batch-launch.json；本轮不自动授予input Accept。

有限批次现已实际完成，158项全部 java exit0、必要输出与完成标记存在。`batch-1/coordinate-triage.json` 对辅助模型顶层 dependencies/dependencyManagement 共4458个坐标作窄扫描，仅一个非固定版本：annotations 的 junit:4.13-SNAPSHOT test边，之前独立静态报告已限定标准传递收集不参与；不是新增生产Block。43个模型出现file-context未决标记，均保留待审，不能因模型exit0/有效值固定就认定原上下文一致。已交原Reviewer逐模型或共享parent组核对profile实际影响与剩余原始声明映射，报告 `jraft-rebuild/r1-batch-review.md`；input review尚未通过，reviewed lock/verify-lock/消费者/full仍未执行。

批次 Reviewer 返回 `r1-batch-review.md`：整体Revise，但已按共享parent逐组收敛43个file模型及property/JDK/OS分支，剩余B1为完整坐标字段、B2为914条外部自建插件/扩展声明归属。父会话仅用现有材料生成 `r1-ownership-ledger.json`：每条914声明保留owner/modelPath/version/SHA与传播边排除依据，JRaft源码parent链及原effective摘要绑定；实际项目 build/plugins 的显式plugin dependencies为0。4938个dependency/相关plugin dependency对象完整字段扫描只发现Netty kqueue optional compile的未解classifier，与Reviewer先前标准传递排除结论一致，不误记固定值通过。表仍candidate，已交原Reviewer最终输入范围裁决 `r1-final-input-review.md`；不再新增工具或模型构建，若Revise须列具体相关坐标/字段/传播边。

另从两轮精确相等791输入生成 ignored证据候选 `jraft-rebuild/input-lock.candidate.json`，采用原recipe json_bytes，SHA `f65c95e04c2876621653dff82c33e1f561de313090d2767fd29eb40bbd242a7a`；未写仓库input-lock/provenance，不授accepted。最终输入Review通过后才允许将该精确锁转正式并实际verify-lock，候选生成不是接受事实。

### 固定收口批次（方案 A，2026-10-29）

本次及后续同类第三方制品收口采用固定批次，不削弱任何验收规则：

```text
实现/稳定文档冻结
  -> capture 双轮
  -> 一次性输入审核与精确锁
  -> verify-lock 双轮
  -> 消费者/单实现验证
  -> 一次 clean/full
  -> 独立整体 Review 与状态归档
```

- capture 双轮成功后冻结输入、报告、产物和审计证据；同一冻结输入不重新生成辅助模型，不重复 input review。
- input review 只接受具体冻结输入及精确锁字节，不能由 capture、verify-lock 或历史 Review 冒充；verify-lock 只验证正式锁模式，不重新承担 input review。
- full 已覆盖的 architecture/startup/behavior/E2E/HA 证据不再独立重复；full 前若实现、依赖、共享规则、稳定文档或输入发生变化，必须重新打开受影响阶段。
- 失败只回退到最窄受影响层：构建/环境失败保留现场并先归因；修复无关前提不能授权整链重跑；没有新修复或前提变化不得盲重跑。
- 各阶段通过后冻结其输入和证据。任何阶段的 `passing`、`Accept` 或历史 freshness 均不自动晋级下一阶段；本规则只减少重复，不降低门禁。

### 最终输入 Accept 与正式 verify-lock 启动

`r1-final-input-review.md` 已返回 **Accept，仅本次冻结 input scope 和精确锁内容**，必要缺口none；Reviewer独立对比791映射/序列化字节及SHA一致，关闭B1/B2/B3，不需同内容重复Review。其额外复核14个extension，完整对象统计为4952而不是4938，未发现新未解坐标；Netty optional classifier具名排除而非记为已解析。原制品/仓库Accept未授予。

按实际独立结论落地 `third-party/jraft-applied-membership/input-lock.json` 原始获审字节，provenance登记固定SHA、inputInventoryReview=accepted和精确报告范围；未安装JAR或发布。readiness exit0，仓库44项recipe fixture再次exit0、git diff --check通过。启动前仅有外仓wrasse Maven，未触碰外仓进程；本仓无并发构建。

已实际启动新隔离 `JRAFT_INPUT_MODE=verify-lock` 原build.sh双轮，证据 `jraft-rebuild/verify-lock-20261029/`，PID 83818，launch.json/recipe-sha.json记录模式/锁/recipe身份；run.log保留输出、结束后result.json记录真实UTC/exit，output为全新目录。当前正在执行，不预填通过。下一步检查真实双轮/精确锁/accepted-comparison，成功后才进入隔离消费者API/单实现、integration及冻结全仓full。不重复旧capture、不恢复辅助审计链。

等待verify-lock期间仅只读准备下一层：已核对Plugin AGENTS、im-raft实际测试为RaftRuntimeTest/RaftMembershipTest/RaftNodeSettingsTest，消费者窄命令为 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-raft -am test`；实际API使用由RaftRuntime编译覆盖。随后以同隔离仓库的 `dependency:tree`/编译classpath与最终Broker包确认私有GAV及Node/NodeImpl/FSMCallerImpl单来源。命令仍待verify-lock成功后执行，不在上游运行期间并发构建；原`/tmp/jraft-artifact-m2`缺失，需从已验收payload按既有隔离install授权恢复，不装默认m2、不发布。当前磁盘约61GiB；没有因等待新增辅助工具或扩大设计范围。

### verify-lock 完成与消费者验证

verify-lock 已实际 exit0：`2026-10-29T04:40:09.707427Z` 至 `05:19:23.094536Z`；两轮各99 suite/473 tests，零失败/错误、1 reviewed skip，791 inputs和binary/sources/POM payload完全一致，正式 `accepted-comparison.json` 已生成。证据目录 `jraft-rebuild/verify-lock-20261029/`。

已将 verify-lock build-1 的三项 payload 安装到隔离 `/tmp/jraft-artifact-m2`，未写默认 Maven 仓库、未发布。消费者命令首次运行超过120秒后被工具超时；其后同一隔离仓库的有界 offline 重试 exit1，明确缺少项目其他依赖（RocksDB、Netty 4.1.132.Final、SOFA common tools、Dropwizard metrics 等），无法访问 `nexus-knight-repository`。这不是 JRaft 制品或测试失败；没有把已有 target XML（RaftNodeSettings25、RaftRuntime24、RaftMembership2）算作本轮通过。无 Maven 进程残留。下一步是仅补齐受控隔离依赖后重跑 `im-plugin/im-raft -am test`，保留首次超时和offline缺依赖证据；不回退默认m2、不改依赖版本、不宣称消费者通过。

随后从本机默认仓库只读复制普通项目依赖到 `/tmp/jraft-artifact-m2`，并再次覆盖已验收 JRaft 三项 payload；私有 JAR SHA 与 verify-lock payload 均为 `65d4b568...697f04d`。同一命令 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-raft -am test` 实际 exit0，当前报告 3 suites/51 tests、零失败/错误/跳过：RaftNodeSettings25、RaftRuntime24、RaftMembership2。JRaft 运行日志含预期 StateAdapter failure-path 场景，但 XML 断言全部通过；未把日志警告当失败。该消费者验证只证明 im-raft 当前单模块及其聚合依赖，尚未证明全仓消费者、最终包单实现、integration/HA或full。

此前对超时的归属记录有误，现按工具原结果纠正：`im-raft -am test` 最终600秒上限调用实际正常返回，51项XML全零；后续超时的是 `MAVEN_OPTS=... ./scripts/verify.sh full` 的120秒工具窗口，输出停在Harness自测，未到完整Maven reactor。`.harness/verifications/full.json` 保留incomplete，不能计full通过；检查无本仓verify/Maven残留。此前rsync与sources文件名拼接命令失败均保留，不计构建失败或测试通过。下一次full改用不截断进程的独立runner保存真实结束码和完整日志；这是工具时间窗口修正，不是绕过Harness或复用前阶段结果。

本次启动前另修复方案A文档编辑造成的规则矩阵三行文字与传感器错位，恢复原full/重跑/时序证据三条规则对应，新增方案A独立Review-only行；明确普通跨模块任务不新增第三方capture/verify-lock。无检查器弱化。用户授权方案A的稳定规范最终仍需独立整体Review，不将之前diff-check冒称语义Review完成。

当前准备重启有完整日志/退出码的最终full，证据固定为 `jraft-rebuild/final-full-20261029/`。冻结后不更新本计划/PROGRESS/源码直至运行结束，防止Git输入变化使门禁失效；运行进度只写ignored证据。

### 最终 full attempt2 结果与归因（2026-10-29）

- `./scripts/verify.sh full`（独立 runner，证据 `jraft-rebuild/final-full-20261029-attempt2/`）实际 `06:23:47Z` 至 `06:42:54Z`，exit1。readiness/clean/Harness 自测通过；完整 Maven reactor 通过，当前窗口 2422 tests、0 failures、0 errors、2 skipped。
- 失败点是 Maven 后的 `assert_full_frozen`：起始 fingerprint `acec6e89…`，结束 `f8fc8306…`，`worktreeStable=false`；HA 未启动，`routing-ha.json` 仍为 10-24 旧记录。`full.json` 记 failed，不作验收证据。
- 前一会话曾把 HA/MySQL 记为失败原因，属误读并已更正：`mysql is not healthy`、25 场契约等输出位于 Harness 自测 fixture 段（log 137–177 行），是负向用例预期输出，不是真实 HA 运行。
- 输入变化来源：同一工作区并发的另一 Pi 会话在 full 期间（14:24 本地时间）新增 `BrokerRaftAdapterTest`、`BrokerClusterAppServiceChangeTest` 两个外部成员边界探测用例，结束后又修改 D9 草案设计及索引。它们属于后续 D9（移除私有补丁）调查，不属于 T3 变更集；保留为用户改动，不纳入 T3 提交范围。
- 用户决定先按当前补丁路径收口本计划，D9 之后推进。下一步：确认无并发写入后冻结输入，重跑一次 clean/full；冻结期间不改本计划、PROGRESS 和源码。

### 最终 full attempt3 结果（2026-10-29）

- 无并发写入下重跑 `./scripts/verify.sh full`（证据 `jraft-rebuild/final-full-20261029-attempt3/`），`07:17:15Z` 至 `07:23:24Z`，exit1；`worktreeStable=true`，起止 fingerprint 均 `e8bd07a3…`。readiness/clean 通过；在 Harness 自测阶段失败，未进入 Maven 和 HA。
- 失败断言：`scripts/test-routing-ha-process.sh:226` `material cleanup ownership/order mismatch`，即 runner fixture 某一 mode 的私有 TLS 材料目录存在性与预期不符。`$TMPDIR` 残留一个 07:22Z 创建的空 `im-chat-routing-ha-tls.*` 目录，与“非 down 模式 cleanup 失败而保留材料”一致。
- 候选机制（未证实）：`routing-ha-process.rb` 的 `finish_owned` 在 KILL 与期限之间只留约 40ms 确认组消失；超时则 `.pid` 登记残留、无 `done`，下一次 `run` 因 `unresolved historical registry` 退出 2，导致 cleanup-down 失败并保留材料。
- 复现：同一自测单独连续 5 次 exit0；30 个 CPU 占满进程压力下 4 次 exit0。未复现，不据此认定机制，也未修改 Harness/helper。失败时间段无其他 Pi 会话或本仓 Maven 活动的证据。
- 依方案 A“无新修复或前提变化不得盲重跑”，下一步由用户决定：记为偶发后再冻结重跑一次，或先对 helper 期限/fixture 做独立设计和修复。
- 用户决定：attempt3 记为未复现的偶发 fixture 失败，不改 Harness，冻结后重跑一次 full（attempt4）；若再失败则转入 helper/fixture 修复。

### 最终 full attempt4 结果（2026-10-29）

- 冻结重跑 `./scripts/verify.sh full`（证据 `jraft-rebuild/final-full-20261029-attempt4/`），`07:33:30Z` 至 `07:51:43Z`，exit1；`worktreeStable=true`（`8e56b512…`）。readiness/clean/Harness 自测通过（attempt3 的 fixture 失败未再现）；Maven reactor 在 `im-routing-ha` 失败，HA 拓扑未运行。
- 失败用例：`AutomaticFailureTlsHaTest.replacementLeaderResumesCommittedFencingWithActualMembershipAndTlsPeers`（同类其余 2 项通过，模块 9 项 1 error），`awaitInitialMembership` 45 秒超时，原因 `Applied configuration unavailable` ← `No applied configuration`，即至少一个节点 applied index 一直为 0。
- 日志证据（group `routing-ha-junit-9936744104699334058`）：51395 当选 Leader 后，到 51394 的 JRaft 明文 RPC 连接每次“connected”后立即被关闭（`ConnectionClosedException`/`Connection closed when invoke with callback`），整轮持续；51394 出站 PreVote 请求和响应正常。服务端无关闭原因日志。该段在任何业务 Bolt 路由调用之前，同一源码在 attempt2 通过。
- 候选机制（未证实）：测试 `availableEndpoints()` 用 `new ServerSocket(0)` 预约后关闭再绑定；JRaft RPC 服务端绑定通配地址，路由 Bolt 服务端绑定 `127.0.0.1`。在 macOS + SO_REUSEADDR 下，若 51394 同时被其他 `127.0.0.1` 专有监听占用（例如 TLS 服务），明文连接会被导向该监听并关闭。端口现已释放，无法事后验证；本类前两个集群端口（51039–41、51229–31）不重叠。
- 未修改测试或生产代码。连续两次冻结 full 分别在不同位置偶发失败；下一步由用户决定再次重跑，或先对测试端口分配做独立设计与修复。
- 用户决定：attempt4 记为偶发测试基础设施失败，不改代码，冻结后重跑一次 full（attempt5）。

### 最终 full attempt5 结果（2026-10-29）

- 冻结重跑 `MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh full`（证据 `jraft-rebuild/final-full-20261029-attempt5/`），`07:58:08Z` 至 `08:22:14Z`（1446 秒），exit0。
- `.harness/verifications/full.json`：`status=passed`、`worktreeStable=true`，起止指纹均为 `7ac95e9f…`，HEAD `6ecd9c9f`；Maven 2422 tests、0 failures、0 errors、2 skipped。
- `.harness/verifications/routing-ha.json`：本轮 `full-reactor` 来源，3 Broker + 2 Gateway，25 场景，`cleanupStatus=passed`，`worktreeStable=true`。
- attempt3（helper 回收窗口）与 attempt4（测试端口预约竞争）两处偶发失败本轮未出现，仍为未修复的测试基础设施残余风险，不因本轮通过视为已解决。
- 下一步：当前 quality 与独立完整实现 Review；Accept 后才做纯状态迁移。

### Review 修订（W1–W8）与重新验收（2026-10-29）

attempt5 通过后，独立整体 Reviewer 返回 **Revise**（无 Critical、无正确性/安全阻断）。用户批准按建议处理：修 W1–W6，W7/W8 记为后续；修复后重新冻结完整验收并复审。原 Review 结论保留，不改写为 Accept；attempt5 只证明修订前输入。

| 项 | 结论 | 处理 |
|---|---|---|
| W1 状态文档与证据矛盾 | 属实 | 更新 PROGRESS、active README 与本计划恢复入口 |
| W2 第三方状态与规则矩阵过期 | 属实 | `third-party/jraft-applied-membership/README.md`、`provenance.yml` 按 verify-lock/隔离安装/消费者/full 实际结果更新；剩余门槛保留；`HARNESS_RULE_MATRIX.md` BoltInvocationBoundaryTest 行改为实际 4 项 GREEN、仍无机器 RED |
| W3 仅测试使用的单参数构造器（规则 8） | 属实 | 删除 `BoltRequestProcessor(List)`；6 处测试调用显式传 `new ImBoltProperties.Server()`，默认策略不变 |
| W4 README 失败分类 | 属实 | 目标策略/caller 预算无效仍为 LOCAL；DEV 非开发地址在 acquire 拒绝现有实现归 PROTOCOL（10-25 设计已记录且禁止为测试改生产分类），README 如实描述；复审 R1 指出其与 10-20 设计表 LOCAL 不一致，已改为已知偏差并记 TD-03 |
| W5 Javadoc 过度声明 | 属实 | `BoltOutboundHandler` 改为如实说明直接写入的 ByteBuf 无法区分而放行，不改逻辑 |
| W6 Gossip 收敛遇非 2xx 不重试 | 属实 | 非 2xx 改为期限内可重试；先在 `scripts/test-routing-ha-harness.sh` 增加瞬时/持续拒绝 stub 用例，修复前 RED（未捕获 RuntimeError），修复后 GREEN |
| W7 GatewayClient 硬编码 isolated 网络（仅 DEV） | 记为后续 | 不在本计划修改 |
| W8 `scripts/test-bolt-dev-contract.sh` 未接入 Harness | 记为后续 | 不在本计划修改 |

Suggestions 均不在本次范围，保留给后续评估。attempt3（helper 回收窗口）与 attempt4（测试端口预约）偶发失败仍为未修复残余风险。

- [x] W1–W6 修复及 W6 回归用例 RED/GREEN：`bash scripts/test-routing-ha-harness.sh`（修复前 exit1，修复后 exit0）。
- [x] 窄验证：`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test`；`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-test/im-routing-ha -am -Dtest=AutomaticFailureTlsHaTest -Dsurefire.failIfNoSpecifiedTests=false test`；`./scripts/verify.sh drift`；`git diff --check`。结果：Bolt exit0、drift 通过、diff-check 通过；AutomaticFailureTlsHaTest 首轮 `stoppedFollowerAutomaticallyFencesAndRebuildsWithIndependentTlsReports` 失败（`real follower report RPCs` 为 0，断言位于状态 await 之外），随后同命令 3/3 exit0。记为与 attempt3/4 不同的新偶发残余风险，本计划不修。
- [x] 冻结后 `./scripts/verify.sh clean`、`MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh full`。
- [ ] 当前 quality 与独立复审；Accept 后才做纯状态迁移。

#### attempt6 与复审 R1–R5（2026-10-29）

- 冻结 full attempt6（证据 `jraft-rebuild/final-full-20261029-attempt6/`）`09:08:42Z` 至 `09:37:38Z` exit0：2422 tests 零失败/错误、2 skipped，routing-ha 25 场景 cleanup passed，`worktreeStable=true`（`0c4cfe55…`）。
- 新独立 Reviewer 复审 **Revise**（无正确性/安全/架构阻断）：W1/W3/W5/W6 确认修复，W2 大部分修复，W4 修复措辞不当；quality 响应同为 Revise，未写入正式路径。
- R1（Medium，W4）：README 与本计划称 DEV 地址拒绝归 PROTOCOL「按设计」，与 `2026-10-20-bolt-unified-invoke-design.md` 表格「目标/配置无效归 LOCAL」矛盾。核对：Reviewer 称无测试固定该分类不成立（三个未跟踪 `BoltConsumerConfigurationTest` 断言 PROTOCOL）；`2026-10-25-bolt-controlled-client-api-design.md` 已记录现状并禁止本计划为测试改生产分类。处理：README/本计划改为已知偏差，记 `tech-debt-tracker.md` TD-03，不改生产代码。
- R2（Low，W2）：`third-party/jraft-applied-membership/README.md` 「当前收口」节标为历史并指向现状；开头归档来源改为与 `provenance.yml` 一致的 HTTPS 固定 commit 下载及 951 文件匹配。
- R3（Low）：W7→TD-01、W8→TD-02、三类偶发失败→TD-04 写入 `docs/exec-plans/tech-debt-tracker.md`。
- R4（Low）：`docs/exec-plans/active/README.md` 恢复链接改为本节所在 Review 修订锚点。
- R5（Low）：IAM OAuth 两个测试移入 `security/oauth/`（仅 package 行变化）不属于本计划范围，本计划不改动；提交时单独判断归属，不混入本计划提交。

- [x] R1–R4 修订后窄验证：`./scripts/verify.sh drift`、`git diff --check`（均通过）。
- [x] 冻结后 `./scripts/verify.sh clean`、`MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh full`（attempt7）：`09:56:31Z` 至 `10:17:15Z` exit1，`worktreeStable=true`（`d072557e…`），HA 拓扑未执行。失败为 `AutomaticFailureTlsHaTest.stoppedLeaderRequiresFreshReportsAndReplacementCompletesDegradedWithoutGatewayAck` `:317` 等待 45 秒后某存活节点 `highestFencedEpoch` 仍为空（证据 `jraft-rebuild/final-full-20261029-attempt7/evidence/`）。诊断：同一断言内 epoch=1 与目标 INACTIVE 已成立，说明拓扑已提交；watermark 只由 `RoutingFenceHandler` → `fenceLocal` 在 fence ACK 路径写入，而 `BrokerClusterAppService` 对未确认的 fence RPC（测试超时 500ms）按设计等满 quarantine 后提交。测试断言强于设计保证，不是生产缺陷，记入 TD-04，不改代码。
- [x] 冻结后 `./scripts/verify.sh clean`、`MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh full`（attempt8，用户决定：诊断为测试问题则记入 TD-04 后重跑；证据 `jraft-rebuild/final-full-20261029-attempt8/`）：`10:24:58Z` 至 `10:47:15Z` exit0，2422 tests 零失败/错误、2 skipped，routing-ha 25 场景 cleanup passed，`worktreeStable=true`（`dd81411b…`）。
- [x] `./scripts/verify.sh quality` 与独立复审 Accept 后才做纯状态迁移：请求 `quality-6ecd9c9f…-f3ee6a4fa364`，新独立 Reviewer **Accept**（R1–R4 确认已处理；attempt7 诊断经源码核对成立，非生产缺陷），响应原样复制至 `.harness/quality/reviewer-response.json` 后 quality 生成快照。Low 发现处理：Q1 attempt8 重跑依据已补用户决定；Q2 由本次状态迁移更新 PROGRESS 与 active 索引；Q3 归 TD-04；Q4 D9 探针与 IAM OAuth 移动在提交时需单独提交或对仅含本计划改动的树补窄验证，提交前另行决定；Q5 full 后仅本计划文件的 attempt8 记录与本次状态迁移变化，原 full 时间与指纹 `dd81411b…` 照实保留、迁移后如实 stale。

#### 提交范围更新（2026-10-29）

- 用户在提交准备期间明确要求「全部提交」，取代此前提交时排除 D9 探针、D9 草案及 IAM OAuth 测试移动的安排；原排除决定保留为历史。`.gitignore` 中既有 `.pi/` 忽略规则一并纳入，Pi 会话原件不修改、不删除、不提交。
- Bolt 实现、HA 证据与文档收口为一个逻辑提交；D9 草案及两项边界探针、IAM OAuth 测试仅 package 路径移动、`.pi/` 本地会话忽略规则按独立主题分别提交。D9 仍为未批准草案，本次保存草案与探针不授权修改生产治理或切换 JRaft 依赖。
- 先前仅包含 Bolt 范围的隔离 full 因用户扩大提交范围而取消；随后启动的完整工作区额外复验按用户「不用了，就刚刚跑完，全部都没改过」指令停止，两次取消均不计通过。用户明确要求不重复 full，沿用 attempt8 实际通过及独立 Review；提交准备仅修正文档状态和链接，没有新增生产或测试逻辑。readiness、clean 与增量独立内容复核已完成；原 attempt8 时间、指纹、历史失败与状态迁移证据保持不变，不将其改写为 fresh。
