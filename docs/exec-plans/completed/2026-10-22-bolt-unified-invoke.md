# Bolt 连接层认证与统一 invoke 实施计划
当前恢复入口：Bolt 实现验收及状态关闭已完成，计划已迁移。活跃计划/当前实施任务：none。full8 与最终59cb实现 Accept 绑定原冻结输入；实际迁移及机械 href 例外已获40c50a94精确 Review Accept，五项廉价检查已实际完成。Loop4 PAUSED，原时间、指纹、stale、Block 历史与残余限制保留。


## 当前执行入口

当前以文末“T6 第八轮标准完整验收冻结入口（当前）”为最新恢复状态；此前各轮及有限组窗口保留历史归属。

- 状态：**T1–T5 scoped `passing`；T6 唯一 `active`，WIP=1**。T5 完整 scoped exit 独立 Reviewer `b5a56382` 最终 Accept/no findings，全文 `/tmp/bolt-t5-exit-final.md`；只批准进入 T6，不是 full、生产安全或部署 Accept。
- T5 正常 focused 实际编译成功；最小 all-rules-missing control 为 4 tests / 2 assertion failures / 0 errors / 0 skips，finally 恢复原 bytes 后四方法 GREEN。正常规则逐项检查六个 bypass 方法，不称六项独立 mutation RED，不承诺同 JVM sandbox。
- 公共同 GAV 原缓存恢复后，Loop 写入曾使 architecture 143.755s/exit1；该失败及此前 Central TLS 12.513s/exit1 保留在历史附录。Loop#4 现为 **PAUSED**，父暂停写入后形成两个独立成功窗口：architecture 104.554s/exit0、quick 505.203s/exit0，均 changed=[]、非 timeout；各有 8 份 fresh XML，79 tests / 0 failures / 0 errors / 2 skips。两项 skip 来自既有 `LocalShardingRoutingIntegrationTest` 的 `im.test.local.mysql` property absent，不代表真实 MySQL 已验证；新 Bolt 四方法均零 skip。quick 本轮日志第 826 行实际出现 `Harness script tests passed.`，不是复用历史 517.385s 父自测。证据 `/tmp/bolt-t5-wide-gates/`。
- 私有 JRaft 原 JAR/POM 已按批准 SHA 恢复至 `/tmp/jraft-artifact-m2`；commons-compress:1.26.1 仅恢复普通同 GAV 原缓存 JAR/POM，两者均未改 POM、依赖版本或 mirror，不构建外仓。恢复及字节证据见 `/tmp/bolt-t5-artifact-restore/`、`/tmp/bolt-t5-plugin-recovery/`；正式私有制品发布/双构建可复现性后轨仍未解决。
- T6 首次 full 实际 463.426s/exit1、2744 文件始末输入相同：父 Harness 的 runner build fixture 期望23，fake engine-info 在0.6s总预算（预留0.15s终止）内实际0.494616s/exit124，未到 Maven/真实 HA；不是生产期限算法故障。原证据 `/tmp/bolt-t6-acceptance/RESULT.md`，旧 full-maven.log 非本轮 fresh。有限修复已获独立 spec `56df`、quality `beff9611` 最终 Accept/no findings；生产 helper 未改，target/control 与子自测通过，父自测实际 608.045s/exit0，但运行中未知 `.gitignore` 变化不构成冻结证明。现按用户「继续」保留现有 `.gitignore`/`.pi/` 为用户既有输入，仅三状态文件批次后重新冻结第二次 full；tracked `.gitignore` 纳入全输入，不改变 quality 排除，不续接/覆盖首轮失败。Loop4保持 PAUSED。
- 第二轮 full 已实际失败：707.160s/exit1、2743输入稳定，父自测通过；上游660全零，Broker124 fresh XML/778 tests/2 failures/38 errors，本轮总239 fresh XML/1438 tests，并非完整 reactor通过，真实HA未到。原件 `/tmp/bolt-t6-acceptance-attempt2/RESULT.md` 保留；最早 `ImBrokerApplicationTest` 的旧fixture禁用client/server并mock Invoker，使正式Peer构造parameter8缺RpcClient。观察mock默认false、null peer及Handler无认证事实另属有限fixture前提迁移，不放宽生产MTLS/DEV权限。当前重开T6输入，先执行下述有限组；原full失败不改写成功。
- full 成功仍保持 T6 active；必须取得正式独立 quality 实现 Accept，之后另作两时点状态关闭和精确 diff Review。D14 已批准、D13 从未批准实施，不创建私钥副本；未提交、未生产部署，不删除未知 `.pi/` 或用户变更。

现行执行只读本入口、下列契约/任务与恢复状态。末尾「历史执行与失败记录（非当前指令）」保留旧候选、旧状态及命令原文，不能作为当前暂停/实施指令。

## Sprint Contract

- 目标：全部 Bolt 业务调用采用不可变地址/完整预期 URI 目标、启动固定策略、认证后连接交付和编码前发送屏障，删除普通地址调用及 `invokeAuthenticated`。
- 架构：目标选择归 SDK/接入层；证书验证、身份隔离池和候选清理归 im-bolt；caller 接受预算与共享创建预算分离。入站认证不替代角色、成员、scope/audience、Gateway 租约、epoch、Lease/fence 和幂等。
- 技术栈：Java 21、SOFA Bolt 1.6.6、Netty/JSSE、Spring Boot typed properties、JUnit/AssertJ/Awaitility、现有 Maven/Harness。
- 范围：im-bolt、im-gossip、Broker Peer/SDK/Server、Gateway SDK/Server、Account/Message SDK 装配、全部直接构造者及测试 fixture、HA/E2E/架构规则、配置与运维文档。
- 非目标：不改 JRaft transport/依赖、Dubbo、外部 WebSocket 协议；不做 SPIFFE/SPIRE、热加载、吊销服务、性能容量保证、兼容桥或生产发布。不恢复旧 A5 或历史计划。
- 验收标准：T1 四项技术闸门及独立证据复核先通过，才允许 T2 起修改生产代码；全路线有确定性负例，D11 支持的受控 typed 新业务在编码前 forwarded=0 且远端 handler=0；原生 TLS/encoder/response/control 单独信任并实际审计，不承诺同 JVM sandbox；完整冻结输入实际 full 和独立实现 Review Accept 后才关闭。
- 风险：公开 hook 可能无法可靠取得 D10 会话数组事实、覆盖 D11 受控写起点、完成原生来源审计或收口后台任务；任何一项不成立即 blocked，回设计 Review，不以 fork、反射、复制 initializer、叶证书替代、无限线程或明文降级绕开。
- 环境/权限：本机 JDK/keytool、Maven 依赖和私有 JRaft 制品、Docker Engine/Compose、隔离测试网络及可清理临时目录；无需生产凭据或发布权限。私有制品不可解析时记录实际命令/cause，先归因再推进，不改依赖掩盖。
- 发布/回滚：仓库未上线，所有消费方同步迁移重建；不得发布半迁移版本。测试专用 CA/私钥临时生成不入库，不充当生产材料。生产签发、secret 分发、轮换和部署审查另行落实。

### 已批准选择与初始配置

| 决策 | 固定语义 | 当前实施状态 |
|---|---|---|
| O1 | 部署显式地址/完整身份配对；Nacos 候选与配对取交集；Gossip seed 同样配对，观察 ID 不冒充稳定 brokerId | 已批准；T3 passing，退出 spec 40ac3088、quality 02665be5 Accept |
| O2（历史，已由 D10 替换） | 实际验证路径叶证书及中间 CA 截止，不含信任锚 | 旧退出条件未通过，历史 witness/反证不改写为成功 |
| D10/B | 原生握手成功后，本机实际发送及对端呈现的非空 X.509 数组全部成员（含出现的根/锚）定义 `latestNotBefore <= now < earliestNotAfter`；严格 URI 仅检查对端叶；到期不准入新业务，在途保持原预算 | 用户批准含隐藏路径保证损失；设计 reviewer `aa137e0a-4814-47d` Approved/findings none；T1 技术退出已取得独立 Accept，T2 生产能力已 passing；最终冻结 full 尚未运行 |
| D11 | 受控 invoke 与经审计公开 RpcClient typed-command（含 Connection/CONNECT）编码前检查；原生 TLS/encoder/response/control 可信且单独审计；禁止任意预编码业务、codec/transport 替换、非受控 pipeline/unsafe 写入 | 用户批准，设计 reviewer `1fb8baed-2232-4e1` Approved/findings none；计划 reviewer `1a730f5e-903f-4cb` 全文及精确 diff Review Approved/findings none；T1–T5 scoped passing，当前 T6 唯一 active；raw COUNTEREVIDENCE 与历史 Block 保留 |
| O3 | 独立明文开发配置，仅回环/隔离容器网络、合成数据；允许普通链路，不发有效观察票、不自动摘流；生产禁止、TLS 失败不回退 | 已批准；T4 通用、owner 准入、profiles/部署合同及真实 DEV E2E 已取得局部证据；三 consumer startup 已实际运行，整体退出 a21eb322 scoped Accept；最终同输入验收仍归 T6 |
| O4 | 无旧版兼容；全消费方同步重建；本次隔离验收不生产上线；后台能力安全才启用，否则证明全部禁用 | 已批准；T1 后台技术退出及 T2 生产退出已取得 Accept，不等于全仓最终验收 |

以下数值在编写 T1 测试前固定为**初始配置**，不是容量、TPS、宿主规格或延迟承诺。调整先更新本表、测试及配置说明，再重跑受影响证据：

- `im.bolt.client.mode` 默认 `MTLS`；允许的唯一其他值 `DEVELOPMENT_PLAINTEXT`，仅显式 `bolt-dev` profile 可用；生产 profile 拒绝开发模式。client/server 仍可默认不启用，启用 MTLS 而材料/前缀缺失即启动失败。
- `im.bolt.client.allowed-peer-uri-prefixes` 无宽松默认值；必须非空、带节点分隔边界且互不重叠。TLS 沿用 `im.bolt.{client,server}.tls` keystore 配置，密码只取 secret，不新增假想独立 trust-store。
- `im.bolt.client.connection-creation-timeout=3s`；`creation-threads=2`、`creation-queue-capacity=16`、`max-target-pools=64`、`max-connections-per-target=1`、`max-pending-callers=64`（创建 waiter）；补充 `max-inflight-rpcs=64` 为单个受控 client 的 RPC 总额，不是每 Channel 64。每 Channel 另记 drain 计数，不增加第二套 RPC 额度；超额 LOCAL，不驱逐有在途连接来腾位。单 caller 仍取原 timeout，拒绝非正值。
- `im.bolt.client.background-reconnect-enabled=false`、`connection-warmup-enabled=false` 为首版选择；不能只相信新增属性，T1 必须证明 Bolt 默认、Url、manager/reconnector、所有入口确实禁用；无法证明禁用或安全启用均 blocked。
- 候选/队列/池/在途收口必须有可读计数和关闭完成证据；测试异步等待上限 5s、外部 keytool 单进程上限 20s 仅为测试防挂界限，不是生产硬期限。不可中断 DNS/同步创建迟到只能关闭，不承诺 caller 硬返回期限。
- 开发网络固定：宿主仅 `127.0.0.1`/`::1`；Compose 使用独立 internal 网络 `bolt-dev`、`172.30.88.0/24`，无外部网络附着，Bolt 不向宿主非回环发布，显式合成数据标识。启动配置、实际 bind/目标限制和 Compose 契约联合验证；单独声明任意私网 CIDR 不构成隔离证明。安全 HA 使用另一真实 mTLS 拓扑。

## 事实源

- 设计或产品规格：[2026-10-20-bolt-unified-invoke-design.md](../../design-docs/2026-10-20-bolt-unified-invoke-design.md)，独立 reviewer `83f8f2a3-df78-41b` 全文/源码/精确增量 Approved，findings none；不等于技术 GREEN。D10/B 增量独立 reviewer `aa137e0a-4814-47d` Approved/findings none，已核对用户批准范围包含隐藏实际路径保证损失，无需再次选择。本计划原版已由独立 reviewer `0264f12b-306a-477` 全文及调用清单复核，结论 Approved/findings none，允许从最小原生 TLS witness 开始；不批准跳过 T1 或迁移生产源码。本次 D10 计划修订由独立 reviewer `7822499f-8cf4-430` 精确增量及全文 Review Approved/findings none；仅批准先执行 test-only 数组有效区间切片，不批准 T1 退出或生产迁移。
- D11 设计增量：用户已批准，独立 reviewer `1fb8baed-2232-4e1` Approved/findings none；本次对应计划 reviewer `1a730f5e-903f-4cb` 全文及精确 diff Review Approved/findings none，允许恢复 T1 test-only，不沿用历史计划批准。设计批准不等于测试 GREEN，raw 旁路证据及旧 all-origin Block 保留。
- 执行规则：根及 `im-plugin`、`im-broker`、`im-gateway`、`im-service`、`im-test` 的 AGENTS；[PLANS](../../PLANS.md)、[UNIT_TEST_GUIDE](../../references/UNIT_TEST_GUIDE.md)、[CODING_GUIDE](../../references/CODING_GUIDE.md)、[HARNESS_GUIDE](../../references/HARNESS_GUIDE.md)、[CODE_REVIEW_GUIDE](../../references/CODE_REVIEW_GUIDE.md)。
- Architecture：根 `ARCHITECTURE.md`、`im-broker/ARCHITECTURE.md`、`im-gateway/ARCHITECTURE.md`、`im-service/im-message/ARCHITECTURE.md`；运行行为以拥有模块 README 和[实时批准场景](../../product-specs/im-realtime-approved-scenarios.md)为准。
- 代码事实：`BoltConnectionAuthenticationSpikeTest` 只有有限原型证明；不将 peerCertificates 数组当验证路径，不将显式 remove 后重建当后台证明。设计调用路线表是最低清单，实施前重检增量。
- 原生 TLS 边界：沿用 Bolt TLS 装配和标准 JSSE 信任验证，不自建/替换 SSL manager、TrustManager 或 provider。历史审计未找到实际 CertPath/anchor 的受支持来源；D10 已替换该退出条件，不再要求路径证明，也不把数组称作实际路径。仅对呈现范围附加约束，不监控隐藏路径证书。

### 精确路径缩写

本计划的 `基址 + 后缀` 表示拼接后的准确路径，不表示可任意修改整个目录。`J`=`src/main/java/com/co/kc/imchat/`，`U`=`src/test/java/com/co/kc/imchat/`，`R`=`src/main/resources/`。

| 缩写 | 基址 |
|---|---|
| B | `im-plugin/im-bolt/` |
| G | `im-plugin/im-gossip/` |
| S | `im-broker/im-broker-sdk/` |
| P | `im-broker/im-broker-server/` |
| W | `im-gateway/im-ws-gateway/im-ws-gateway-sdk/` |
| V | `im-gateway/im-ws-gateway/im-ws-gateway-server/` |
| A | `im-service/im-account/im-account-server/` |
| M | `im-service/im-message/im-message-server/` |
| H | `im-test/im-routing-ha/` |
| E | `im-test/im-e2e-test/` |
| X | `im-test/im-architecture-test/` |

## 验证分层

聚焦测试允许上游 `-Dsurefire.failIfNoSpecifiedTests=false`，但必须核对 owning XML 确有指定测试、零跳过、零失败。编译 RED、依赖失败、timeout、无测试均不算行为 RED；selected/compile/分窗口证据不拼 full。

| 层级 | 准确命令/证据入口 | 当前归属与剩余义务 |
|---|---|---|
| 环境 | `./scripts/verify.sh readiness` | 本轮readiness实际exit0；工具版本通过不替代依赖/真实拓扑验收 |
| T1–T4阶段退出 | 下列完成任务及历史原报告/Review | 仅各自输入窗口 scoped passing；T6刷新最终集成证据 |
| T5架构focused | 隔离仓库准确focused命令见 T5 | 历史缺件失败保留；正常focused与最小missing-rule RED→恢复后GREEN及T5 scoped exit完成，见当前入口 |
| T5稳定合同与shell | `/tmp/bolt-t5-stable-contracts/`、`/tmp/bolt-t5-reconciled-gates/` | 稳定组双Accept；shell映射RED/GREEN不是Java架构RED/GREEN |
| T5后续宽层 | `./scripts/verify.sh architecture`、`./scripts/verify.sh quick` | TLS及Loop输入变化失败保留；暂停Loop后architecture/quick各自exit0，T5 scoped exit Accept |
| 风险覆盖 | `./scripts/verify.sh behavior`、`./scripts/verify.sh startup`、`./scripts/verify.sh e2e` | 独立入口用于诊断；最终由T6 full同轮实际architecture/startup/behavior/E2E报告满足，不削减覆盖 |
| T6实现验收 | `./scripts/verify.sh clean`、`./scripts/verify.sh full`、`./scripts/verify.sh quality` | T6唯一active；待冻结输入clean/full、完整reactor owner测试、同轮报告/制品证明、一次真实HA及独立Accept |
| 状态关闭 | `./scripts/verify.sh drift`、`./scripts/verify.sh clean`、`./scripts/verify.sh quality` scope校验、`./scripts/verify.sh cleanup`、`./scripts/verify.sh handoff` | 仅完整验收后两时点关闭；不为纯状态迁移重复Java/HA |

历史廉价门禁仅归 `/tmp/bolt-t5-reconciled-gates/` 的冻结输入：drift 10:24:03.753466–10:24:41.723013Z，37.969s/exit0；clean 10:24:43.964381–10:25:16.534160Z，32.569s/exit0；`bash scripts/test-harness.sh` 10:25:39.417640–10:34:16.805706Z，517.385s/exit0，1800s预算，原日志815行 `Harness script tests passed.`。各 inputs changed=[]；`run.sha` 与回填后 `final.sha` 不同。证据独立406 Accept不改变这些时间/输入。原exit1、240s工具timeout、114.64s诊断exit1、286.006s/exit124、488.167s/exit1仍在历史附录；父shell成功不等于真实HA或Maven startup。

先窄后宽；失败先修复或归因，同因无新前提停止。同一成功full后不重复独立Java/HA。不把handler=0单独当作无提前编码证明；时序使用latch/future、显式输入和有界等待，不用sleep。

## 任务状态

| ID | 行为目标 | 范围 | 状态 | 验证证据 | 阻塞/备注 |
|---|---|---|---|---|---|
| T1 | D10 数组有效区间、D11 受控发送及其余技术退出闸门 | 仅 im-bolt 测试源码及本计划证据 | `passing` | 完整退出5495396a Accept、质量06107abb Accept，模块174 tests；raw COUNTEREVIDENCE 保留 | 仅技术退出，非生产验收 |
| T2 | 安全连接能力及统一目标契约 | im-bolt | `passing` | 完整退出 f4ef3381、quality 3729b260 Accept | 未发布中间态；不等于最终full |
| T3 | 全路线与所有 fixture 同步迁移 | Gossip、SDK、Peer、三消费方、HA/E2E | `passing` | spec 40ac3088、quality 02665be5 复审Accept；旧入口已删除 | 不发布中间态；运行验收继续归T4/T6 |
| T4 | 入站与受限开发模式 | Bolt/Broker/Gateway/消费方配置 | `passing` | 整体exit a21eb322 scoped Accept；CA7c/2f Accept、startup996/859 Accept；模块299（im-bolt262）、startup4各原轮全零 | 原037 Block已关闭，旧RED/失败保留；不是full/生产/T6 Accept |
| T5 | 无旁路规则与稳定文档 | 架构测试、Harness、README/Architecture | `passing` | 架构及稳定组原Accept；完整scoped exit b5a56382 Accept/no findings | focused/control及architecture104.554s、quick505.203s各exit0；非T6验收 |
| T6 | 完整隔离验收及独立 Review 关闭 | 全变更集、当前计划/索引 | `passing` | full8 实际完整验收、59cb 最终实现 Accept、40c50a94 状态候选 Accept | 仅 implementation passing；同一 T6 第二时点关闭义务仍见下方 pending 清单，不表示整体关闭 |

## 详细任务

### T1 测试专用技术退出闸门

文件范围（**仅测试**）：新增 B+U+`plugin/bolt/BoltPresentedChainGateTest.java`，保留同目录 `BoltValidatedPathGateTest.java` 为历史 witness 及 support 调用者；后续新增同目录 `BoltOutboundOriginsGateTest.java`、`BoltSharedCreationGateTest.java`、`BoltBackgroundLifecycleGateTest.java`；复用/修改同目录现有 `BoltSecurityGateTestSupport.java`；只读现有 `BoltConnectionAuthenticationSpikeTest.java` 和 Bolt 1.6.6 公开 API/字节码。支持代码只放上述测试 support，不改 POM、生产装配、配置或任何消费者。测试安全实现与故意不安全 RED 对照同属测试源码；不因 API 不可用而编写假 GREEN。

- [x] D10数组/区间、D11受控typed发送与原生来源、共享创建/RPC预算及后台禁用技术闸门，按历史逐项control取得有效RED/GREEN并完成完整退出；不是路径/anchor保证、同JVM sandbox或生产验收。
- [x] 最终有限两项补证及模块回归已完成：`mvn -q -pl im-plugin/im-bolt -am test`，模块174全零；`/tmp/bolt-t1-acceptance-final/` 的 red-budget/red-classification（3/1 failures）、green-selected（4）、final-green-class（18）、final-module及coordinator-module.exit各归原输入。退出 `5495396a` Accept、quality `06107abb` Accept。原O2 witness、raw COUNTEREVIDENCE及Block/Revise保留历史附录，不升级为新闸门GREEN。

### T2 实现受控连接、预算与统一 invoke

文件范围：B+J+`plugin/bolt/` 现有 `spi/BoltInvoker.java`、`core/BoltClientInvoker.java`、`core/BoltRpcClient.java`、`ImBoltAutoConfiguration.java`、`properties/ImBoltProperties.java`、`exception/BoltInvocationException.java`；新增 `model/BoltTarget.java`、`core/BoltAuthenticatedConnectionFactory.java`、`core/BoltAuthenticatedConnectionManager.java`、`core/BoltOutboundGate.java`、`core/BoltAuthenticatedClient.java`、`core/BoltInvocationContext.java`、`core/BoltCertificateValidity.java`。名称按当前职责固定；不引入泛化能力注册框架。新增 B+U+`plugin/bolt/BoltUnifiedInvokeTest.java`、`BoltConnectionLifecycleTest.java`；修改同目录 `BoltAuthenticatedFailureTest.java`、`BoltAuthenticatedTlsTest.java`、`ImBoltAutoConfigurationTest.java`。

- [x] 完成受控连接/统一目标、D10/D11、双预算及初始资源上限、分类/原cause、外部Bean正反、后台禁用与关闭收口；原testCompile RED与11tests/4failures行为RED分开记录。
- [x] 实际分组focused→模块回归及最终两项补证已由完整退出接受：`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test`，最终237全零、409s；最终selected为4 cases不是4 classes。命令/exit/SHA/报告见 `/tmp/bolt-t2-exit-final/`；退出 `f4ef3381` / quality `3729b260` 双Accept。各前序组183/194/205/224/228/233保留各自原窗口，不拼full。

原三类合并selector指定RED→GREEN及后续原顺序未执行；controlled实际含同三类集合不等于重演原序列。`c224c8c0`仅接受原精确分组等价合同，未抹除原命令事实。完整原命令及失败/Revise/Block见历史附录；最终fresh集成仍归T6。

### T3 全路线、消费方和测试 fixture 成组迁移

生产文件范围：

- G+J+`plugin/gossip/`：`client/GossipPeerClient.java`、`sync/GossipSynchronizer.java`。
- S+J+`broker/sdk/`：`BrokerClient.java`、`BrokerSdkAutoConfiguration.java`、`lifecycle/BrokerRefresher.java`；新增 `model/BrokerIdentityConfiguration.java`（轻量契约，无 Spring Server 依赖）。
- W+J+`gateway/ws/sdk/GatewayClient.java`。
- P+J+`broker/`：`adapter/cluster/BrokerPeerAdapter.java`、`interfaces/scheduler/BrokerGossipScheduler.java`、`interfaces/scheduler/BrokerGossipProperties.java`、`infrastructure/config/RegistryGossipConfiguration.java`、`infrastructure/config/ClientConfiguration.java`、`infrastructure/config/properties/BrokerProperties.java`。
- V+J+`gateway/ws/`：`config/GatewayBeans.java`、`config/properties/GatewayProperties.java`；M+J+`service/message/`：`infrastructure/config/RemoteAdapterConfig.java`、`infrastructure/config/properties/BrokerProperties.java`；A+J+`service/account/`：`infrastructure/config/beans/AdapterBeans.java`、`infrastructure/config/properties/AccountBrokerProperties.java`。
- P/V/M/A+R+`application.yml`；B 的 T2 入口文件最终删除普通地址方法及 `invokeAuthenticated`。

测试文件范围（修改已有）：G+U+`plugin/gossip/sync/GossipSynchronizerTest.java`；S+U+`broker/sdk/client/`：`BrokerClientTest.java`、`BrokerClientRoutingTest.java`；W+U+`gateway/ws/sdk/client/GatewayClientTest.java`；P+U+`broker/`：`adapter/cluster/BrokerPeerAdapterTest.java`、`infrastructure/gossip/BrokerGossipSchedulerTest.java`、`application/frame/FrameDispatchAppServiceTest.java`、`cluster/raft/RaftBoltCoexistenceSpikeTest.java`、`interfaces/handler/BrokerBoltHandlerTest.java`、`interfaces/handler/FrameProcessHandlerTest.java`；V+U+`gateway/ws/`：`support/BrokerClientTestSupport.java`、`ImWsGatewayApplicationTest.java`；M+U+`service/message/`：`support/BrokerClientTestSupport.java`、`support/TestCacheConfiguration.java`、`infrastructure/notification/BrokerMessageNotifierTest.java`、`infrastructure/notification/MessagePushServiceTest.java`；H+U+`routingha/AutomaticFailureTlsHaTest.java`、`routingha/RoutingHaCertificates.java`；E+U+`e2e/RealtimeMessagingE2ETest.java`。新增 G+U+`plugin/gossip/client/GossipPeerClientTest.java`。

- [x] 全路线/fixture typed迁移、O1配对及committed信任、AUTH不吞、Gateway身份绑定、Gossip通用目标与旧入口删除完成，新增精确路径与直接super调用者见历史附录。
- [x] 实际运行分组回归并完成退出：`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-sdk -am test`（SDK46+Bolt237，`/tmp/bolt-t3-sdk/final-module.command`）；transport selected60与affected test-compile、unified-final selected116/SDK18/HAfocused1/architecture最终75（2既有skip）各属原窗口。最终 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltAuthenticatedTlsTest,BoltAuthenticatedFailureTest -Dsurefire.failIfNoSpecifiedTests=false test` 为TLS12+Failure4全零。`/tmp/bolt-t3-unified-final/` 保存首次architecture失败及修正、回滚RED1failure、最终修订；完整spec `40ac3088` / quality `02665be5` Accept。

原五模块逐一顺序RED/GREEN未执行；selected60/116与compile不是五owner full tests。`c224c8c0`接受的分组方法替代不取消T6完整owner回归，也不将compile RED当信任/重试行为RED。完整原selector及所有补充scope保留历史附录。

### T4 入站认证、开发限制和真实运行边界

文件范围：B+J+`plugin/bolt/core/BoltRequestProcessor.java`、`model/BoltRequestContext.java`、T2 的配置/有效期类；P+J+`broker/infrastructure/chain/interceptor/BrokerCallerIdentityInterceptor.java`、`infrastructure/config/RegistryConfiguration.java`、`infrastructure/config/properties/BrokerRoutingProperties.java`；V+J+`gateway/ws/handler/FrameWriteHandler.java`、`handler/ConnectionCloseHandler.java`、`config/GatewayBeans.java`；P/V/A/M+R+`application.yml`，新增四模块 R+`application-bolt-dev.yml`；`deploy/local/compose.yml`、`deploy/local/compose.routing-ha.yml`。

测试：修改 B+U+`plugin/bolt/BoltRequestProcessorTest.java`；P+U+`broker/infrastructure/interceptor/IdentityAccessInterceptorsTest.java`、`BrokerObservationIdentityTest.java`；V+U+`gateway/ws/handler/bolt/FrameWriteHandlerTest.java`、`gateway/ws/handler/ConnectionCloseHandlerTest.java`；新增 B+U+`plugin/bolt/BoltDevelopmentModeTest.java`、V+U+`gateway/ws/config/BoltConsumerConfigurationTest.java`、A+U+`service/account/infrastructure/config/BoltConsumerConfigurationTest.java`、M+U+`service/message/infrastructure/config/BoltConsumerConfigurationTest.java`；修改 H/E 的 T3 测试，`scripts/test-local-compose-harness.sh`、`scripts/harness-startup.sh`、`scripts/test-startup-harness.sh`（仅适配此安全配置/合成网络，不扩张运行框架）。

- [x] 入站D10、严格叶URI/namespace与owner授权、受限DEV允许/拒绝、immutable实际mode及profiles/部署合同完成；通用spec4ded/quality937、observer fb176e22、owner91cedb4c/59f9d63b、profiles88f36f28/f7e70ae7各有限Accept。Docker仅create/inspect/check-only，不是业务startup。
- [x] D12真实Gateway E2E及actual-delete清理修复完成：`MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh e2e`，2026-10-23T04:47:25Z、exit0、1 case；`/tmp/bolt-t4-d12-e2e/` 原命令/报告及spec443f8df4/qualityd0e434b7 Accept。随后三文件等价收尾未fresh编译，旧E2E只属改前SHA；SDK6、Broker17、WS22各独立窗口，不拼full。
- [x] D14/CA三方法完成：`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am '-Dtest=BoltAuthenticatedTlsTest#atomicStoreReplacementPreservesExistingSessionsAndNativeNewChannelsReadReplacement+restartedEndpointsPresentReplacementMaterial+caOverlapRestartSequenceAllowsNewAndRejectsRetiredAuthority' -Dsurefire.failIfNoSpecifiedTests=false test`；08:15:00.930787–08:15:28.576155Z、27.65s/exit0、3tests全零、changed=[]。`/tmp/bolt-t4-ca-retirement/`，spec7c6fa2da/quality2f8888cd Accept。完整交付后旧session保持、新双端物理ID改变及actual local/peer全数组、restart、CA完整顺序/双向native拒绝均有证据；JSSE negative probe边界及旧3/2 RED、3/1 failure、同DN首轮失败不改写。
- [x] CA后完整模块 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test`，258.86s/exit0、27freshXML，-am299（im-bolt262）全零、changed=[]；`/tmp/bolt-t4-post-ca-regression/`。
- [x] 三consumer最终startup：`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-gateway/im-ws-gateway/im-ws-gateway-server,im-service/im-account/im-account-server,im-service/im-message/im-message-server -am -Dtest=ImWsGatewayApplicationTest,ImAccountApplicationTest,ImMessageApplicationTests -Dsurefire.failIfNoSpecifiedTests=false test`，08:36:35.940875–08:37:12.704359Z、36.76s/exit0、4tests全零、3freshXML/changed=[]；`/tmp/bolt-t4-consumer-startup/runtime/`，spec996/quality859 Accept。Message仅外部DB/Redis边界fixture修订，保真实业务/SDK/controlled生命周期，原三个earliest Bean失败分别保留。
- [x] 独立完整exit `a21eb322` 最终scoped Accept/noCritical，`/tmp/bolt-t4-exit-final.md`。历史0377477c Block由批准D14与真实CA/startup证据关闭，不删除原失败。startup shell另由父原日志明确成功，不等于Maven startup/父完整通过。

原五模块完整组合RED/GREEN、独立behavior/startup入口及startup→E2E原顺序未执行（最终startup晚于E2E）；`c224c8c0`接受分窗口阶段退出，不取消T6五owner完整test及同轮behavior/startup/E2E/真实HA。Broker注销WARN属外部Broker不可用，不写成全资源无warning证明。

### T5 无旁路规则、运维文档和 Harness 登记

文件范围：新增 X+U+`architecture/BoltInvocationBoundaryTest.java`、`fixture/bolt/AddressInvocationBypass.java`、`fixture/bolt/DirectBusinessRpcBypass.java`、`fixture/bolt/ControlledProtocolWrite.java`；修改 `scripts/affected-modules.sh`（认证/调用/配置变化映射已有测试入口，不另建 mode）、`scripts/test-harness.sh`。稳定文档：根 `ARCHITECTURE.md`、`im-broker/ARCHITECTURE.md`、`im-gateway/ARCHITECTURE.md`、`im-service/im-message/ARCHITECTURE.md`；B/G/S/W/P/V/A/M+`README.md`；`im-test/im-routing-ha/README.md`、`im-test/im-e2e-test/README.md`、`deploy/local/README.md`；`docs/SECURITY.md`、`docs/RELIABILITY.md`、`docs/references/CODING_GUIDE.md`、`docs/references/HARNESS_GUIDE.md`、批准设计及 `docs/design-docs/index.md`。不存在新行为的 Architecture 段落只改直接调用边界，不做无关清理。


范围修订已source Review接受：architecture test `src/test/java/` 下，规则为 `com/co/kc/imchat/architecture/BoltInvocationBoundaryTest.java`；fixture最终为 `com/co/kc/imchat/plugin/bolt/spi/fixture/AddressInvocationBypass.java`、`com/co/kc/imchat/gateway/ws/handler/fixture/DirectBusinessRpcBypass.java`、`com/co/kc/imchat/plugin/bolt/core/fixture/ControlledProtocolWrite.java`，并新增 `com/co/kc/imchat/gateway/ws/server/handler/fixture/WebSocketProtocolWrite.java`。真实package统一production predicate，无fixture强制flag/类名豁免。稳定组矩阵另按既有owner登记 `docs/references/HARNESS_RULE_MATRIX.md`，不新增框架；sampling/health只限既有 `scripts/test-load-test-harness.sh` fixture及必要指南，生产采样/健康逻辑未改。

- [x] 架构源码候选完成有限source修订，spec `af6d9e74` 原三项Block关闭、quality `109` Accept；地址契约先于owner豁免、真实codec constructor/NETTY_EPOLL_SWITCH负例、业务raw与合法WS正例同predicate。证据 `/tmp/bolt-t5-invocation-boundary/`；后续真实编译及有限control RED/GREEN已完成，见当前入口。
- [x] 稳定24文件契约/影响映射与Review-only理由/自动化条件完成，spec `994cd31e` / quality `03cdba73` 最终Accept。纯shell影响映射原exit1→修后exit0，语法/路径链接/bolt-dev合同通过；不是架构Java RED/GREEN。证据 `/tmp/bolt-t5-stable-contracts/`。原HA README Revise已按native RpcServer实际边界关闭，不称Spring controlled server契约已验证。
- [x] sampling及health有限fixture修复、各自最窄正反/独立子自测完成并双Accept；合同重述 `c224c8c0` Accept、廉价门禁原冻结窗口三个exit0及证据Review406 Accept见验证分层。原失败/timeout/Revise保留。稳定组/脚本双Accept不等于T5 exit。
- [x] 由环境owner恢复合法原私有制品（见当前入口）后，冻结输入并执行原focused隔离命令 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-test/im-architecture-test -am -Dtest=BoltInvocationBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`，900s/no并发；核对实际编译及指定XML。依赖/编译失败单独归因，不能算规则RED。
- [x] 在上述精确规则/fixture范围取得有限all-rules-missing真实断言RED→原字节恢复GREEN（时间/XML/命名限制见当前入口）：地址/直接业务RpcClient/raw业务、codec/transport、pipeline/unsafe负例须被检出；受控owner与合法WS正例接受。保存实际命令/exit/SHA/XML及清理，不用静态source Accept代替运行，不把静态规则当raw运行期sandbox。
- [x] focused完成后依次 `./scripts/verify.sh architecture`、`./scripts/verify.sh quick`，先窄后宽；失败先归因，无新前提不重复。既有 `bash scripts/test-harness.sh` 原冻结窗口完整成功已保存，不为本次纯整理重复；后续输入改变按实际影响复核。稳定文档/设计/非状态索引已定稿；普通同GAV原缓存恢复后，暂停Loop新前提下architecture104.554s/exit0、quick505.203s/exit0，各fresh79tests/0failure/0error/2既有localMySQLskip，Bolt4全零；quick父自测成功见本轮826行，不复用旧517s。原12.513s与143.755s失败均保留。
- [x] 独立T5退出Review核对精确源码/稳定合同、真实正反证据及宽层结果；b5a56382最终scoped Accept/no findings，全文 `/tmp/bolt-t5-exit-final.md`。全部T5义务完成，T5 passing、T6唯一active；不预批准本状态diff或T6验收。

### T6 冻结验收、独立实现 Review 与两时点关闭

文件范围：本计划、`PROGRESS.md`、`docs/exec-plans/active/README.md`；首次 full 失败后的有限必要修复另含 `scripts/test-routing-ha-process.sh` 与 `docs/references/HARNESS_GUIDE.md` 当前事实/fixture约定，不改生产helper算法。最终迁移目标 `docs/exec-plans/completed/2026-10-22-bolt-unified-invoke.md` 与 `docs/exec-plans/completed/README.md`；实际报告只由 Harness 生成，不手写评分或成功证据。

- [x] 汇总 T1–T5 已完成/取代/延期/阻塞关闭账本，确认无漏迁移；收拢源代码、依赖、测试、稳定文档、共享 checker、设计，审阅 `git diff`，保留无关用户变更；协调所有写入者后冻结完整 Git 输入。
- [x] 在冻结输入运行 `./scripts/verify.sh clean`、`./scripts/verify.sh full`。full 必须有同轮父自测、完整 reactor、实际 architecture/startup/behavior/E2E 报告及制品证明、真实 HA；其中 reactor 必须实际覆盖 T3 原五组 owner（im-gossip、Broker SDK、Gateway SDK、Broker Server及Gateway/Message/Account Server）的完整测试，以及 T4 五 owner（im-bolt、Broker Server、Gateway/Account/Message Server）的完整测试报告。selected60/116、compile、im-bolt262/startup4/E2E1的历史分窗口证据不能替代这些 owner full tests；完整 Gossip/投递/ACK行为、三consumer startup、真实Gateway E2E和多Broker TLS/HA均须同轮实际证据，不要求补跑历史五条顺序或重复独立behavior/startup入口；不复用旧原型/旧 full，不在运行中回填计划。失败保留 provenance，先窄层诊断；任何 Git-visible 改动使该次 full 失效。
- [x] 执行 `./scripts/verify.sh quality`，独立 Reviewer 依据当前精确 diff、批准设计、T1 退出证据及同轮机器记录给出实现 verdict，重点审计 D10 会话数组事实与区间、全部写源、预算/上限/清理、后台禁用、全路线和入站/开发限制。Revise/Block 保持可见，`review_required` 不算 Accept；修复后重开受影响任务并按变化重新验收。

前三项属于已验收的实现任务；原后两项原文移至「T6 状态关闭清单」，仍是同一 T6 的第二时点关闭义务，未完成、不因 implementation passing 而豁免。

#### T6 当前有限组：Raft/Bolt coexistence fixture

前七文件组 spec `824`、quality `87ff` 最终 Accept/no findings；最终 related 实际89 tests全零，仅该组通过，原full2未修复共存fixture。当前仅改 `im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/cluster/raft/RaftBoltCoexistenceSpikeTest.java` 与本计划、PROGRESS、active README 三状态文件。Loop4 PAUSED，用户.gitignore保持且纳入输入；不改生产、安全、POM、JRaft transport/metrics/Gossip。

- [x] 读取full2原fresh XML及准确testcase/source，保留首次ping false导致后续apply/metrics/cleanup assertions未执行的baseline；readiness exit0，保存全Git输入before bytes（含未跟踪计划）。
- [x] 最小迁移显式actual bolt-dev/synthetic loopback受控双端、同源正式Invoker/typed target与真实Processor；保ping/失败响应、Raft apply/metrics/端口和线程cleanup全部原断言，不伪造TLS身份。
父已批准公开契约迁移：旧 raw failed response 的 `isSuccess=false`、`code=BOLT_HANDLER_ERROR`、`message=expected spike failure` 三断言，替换为正式 typed invoke 抛出的 `BoltInvocationException.failure()==PROTOCOL`、实际保留的 `IllegalArgumentException` cause 与其准确 generic message，以及真实 fail Handler 执行一次（ping 后仍零次）。客户端不再能观察或声称收到原 code/message；handler 仍抛原异常，由真实 Processor 转换失败响应，未返回嵌套 response。其余成功 pong、原生 Raft apply/metrics 和最后端口/线程 cleanup 断言均保留。

- [x] 单次 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=RaftBoltCoexistenceSpikeTest -Dsurefire.failIfNoSpecifiedTests=false test`（900s），失败先归因；成功后单次 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am test`（1800s）。核对fresh XML/count和全部输入窗口，宽层不覆盖窄层失败。
- [x] 冻结 `/tmp/bolt-t6-coexistence/` before/files/source.diff/sha/commands/exits/windows/reports/RESULT 后交父spec再quality；不运行full3、不标T6passing。状态回填与run SHA分开。

共存组实际 focused 13.627s/exit0，1 fresh XML/1 test全零；随后Broker完整module -am 245.120s/exit0，196 fresh XML/1286 tests全零，其中Broker779 tests全零，均零skip。两窗口2743输入changed=[]，非工具timeout，原full2失败仍保留。正式受控server绑定127.0.0.1:0后读取actual port，真实profile validators/同源invoker启动授权通过，普通DEV context不伪TLS/身份；成功typed pong、失败PROTOCOL/cause/一次真实handler、原Raft apply及metrics、退出后端口可绑定与新基础设施非daemon线程清理断言全部实际执行。原生JRaft snapshot初次目录无数据WARN保留，不称无WARN。无手工stop/shutdown或double Processor lifecycle；Spring各owner管理关闭。本组仅测试与三状态文件，状态回填为运行后另批次SHA，不声称回填后fresh；父spec/quality尚待，full3/clean/full/HA未运行，T6仍唯一active。

#### T6 当前有限修复组：Broker startup、观察与 caller fixture

精确测试范围：P+U+`broker/ImBrokerApplicationTest.java`、`application/cluster/BrokerFailureObservationStateTest.java`、`application/cluster/BrokerFailureObservationRoundTest.java`、`application/cluster/BrokerAutomaticFailureFlowTest.java`、`application/cluster/BrokerAutomaticFailureObservationTest.java`、`interfaces/handler/BrokerBoltHandlerTest.java`、`adapter/cluster/BrokerPeerAdapterTest.java`；状态仅本计划、PROGRESS和active README。before原始bytes（包含未跟踪计划）、files/source.diff/SHA/命令/exit/窗口/XML归 `/tmp/bolt-t6-broker-fixtures/`。不改生产、POM/依赖或规则；Loop4保持PAUSED。coexistence根因已归因，但本组不修改 `cluster/raft/RaftBoltCoexistenceSpikeTest.java`，独立父Review检查点后处理，不宣称全部Broker已绿。

- [x] 读取full2 fresh失败及完整cause，确认startup是禁用transport/mock Invoker与正式装配不相容；full2的相应fixture failure为真实基线RED，编译失败不算行为RED；`./scripts/verify.sh readiness` exit0。
- [x] startup使用正式bolt-dev/合成回环/受控client与server，保真实Peer/业务和既有chain断言；先运行 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=ImBrokerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false test`（900s，运行前查本仓Maven），核对指定fresh XML及冻结输入；失败先归因再推进。
- [x] 观察mock只显式建模MTLS前提、null peer补真实typed adapter与测试Invoker、Handler补严格叶URI上下文；实际MTLS正例及既有DEV拒绝保持。然后单命令 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=ImBrokerApplicationTest,BrokerFailureObservationStateTest,BrokerFailureObservationRoundTest,BrokerAutomaticFailureFlowTest,BrokerAutomaticFailureObservationTest,BrokerBoltHandlerTest,BrokerPeerAdapterTest,BrokerDevelopmentObservationPermissionTest,BrokerObservationIdentityTest,IdentityAccessInterceptorsTest -Dsurefire.failIfNoSpecifiedTests=false test`（900s），核对fresh XML/次数、原assert、负例和运行输入稳定；不是owner full/HA。
- [x] 保存精确增量与运行provenance后停止writer交父独立spec/quality（Review尚未执行）；本组不运行full3，不标T6passing。coexistence独立迁移及其运行、完整验收与正式实现Review仍待完成。

本有限组实际结果：startup最窄 `2026-10-23T12:57:34.198957Z–12:57:48.949772Z`、14.751s/exit0、1 fresh XML/2 tests全零；related首次 `12:58:46.997811Z–12:59:03.030320Z`、16.033s/exit1、10 XML/89 tests/5 failures/0 errors/0 skips。新增可见根因为Round五处旧 `verifyNoInteractions(peer)` 把正式只读权限检查也算副作用；明确验证 `observationsAllowed` 并保留 `verifyNoMoreInteractions`（其他全部调用仍禁止），不是放宽远端调用/Begin/Quorum断言。related最终 `12:59:30.437826Z–12:59:44.791413Z`、14.354s/exit0、10 fresh XML/89 tests全零。三窗口各2743输入/changed=[]，900s工具上限内，均非超时；原full2及related首次失败保留。startup真实DEV仅普通装配，观察仍被实际mode拒绝并有WARN，非生产MTLS证明；MTLS新正例只构造实际策略快照，不启动网络、不声称真实TLS。其余既有DEV拒绝与严格identity负例同轮执行。本组共七测试文件，无生产/规则/POM/依赖变更，无新公共helper/兼容构造；状态回填是运行后另批次，不冒称回填后SHA是运行fresh。coexistence未改，原Raft apply/metrics/cleanup断言未在本组运行；停止writer交父先spec再quality，之后独立coexistence组及重新冻结完整验收。没有执行clean/full3/quality/真实HA，不标T6passing、不归档/提交/部署。

完成条件：所有必需门禁和独立 Review 真实成立，状态审计通过，临时材料删除或分类，未提交、未生产发布；不得提前将未执行步骤打勾。


## T6 第三轮完整验收冻结入口

Broker七测试文件组已取得独立 spec `824`、quality `87ff` 最终 Accept/no findings，related最终89 tests全零；共存fixture独立 spec `8373`、quality `4f8ba` 最终 Accept/no findings，focused1 test及Broker完整module -am196 fresh XML/1286 tests全零。两组原始运行窗口、输入SHA及回填状态SHA分别保留在 `/tmp/bolt-t6-broker-fixtures/`、`/tmp/bolt-t6-coexistence/`；有限组双Accept不等于最终实现Review或完整验收。

当前取代上述“待父Review/未修复共存”的恢复指令：两组均已闭环，源码、稳定文档、设计、依赖及共享checker冻结，不新增源码修改。唯一writer仅本次集中回填PROGRESS、本计划及active README三状态文件，保存全部Git-visible before原bytes（含未跟踪计划全文）、精确状态diff/SHA、after snapshot与ignore规则及ignored Loop关键SHA于 `/tmp/bolt-t6-acceptance-attempt3/`。实际文件数由snapshot枚举，不硬编码。用户tracked `.gitignore`与有效忽略规则纳入完整输入，`.pi/`保持用户owned且ignored，不扩大quality exclusions。Loop4保持PAUSED，不启动/续期；不干预外仓Maven或既有k6 PID49694。

下一步先确认本仓无并发Maven，运行 `./scripts/verify.sh clean`，仅exit0且输入稳定后单次 `MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh full`（10800s工具预算）；完整执行父自测、一次reactor、同轮risk报告/制品proof及真实HA，不skip、不跨轮partial reuse。clean失败先归因停止；full失败保存最早owner/root cause、fresh XML/count与输入漂移边界交父，不盲重跑。原full1 463.426s/exit1 fixture engine预算、full2 707.160s/exit1 Broker778 tests/2 failures/38 errors历史原件不覆盖。

T1–T5仍scoped passing，T6唯一active/WIP=1。本轮开始后及结束后只写/tmp与ignored验证输出，不再Git-visible回填。full成功即时交父安排正式quality及独立最终实现Review，不手写semantic分数，不把review_required当Accept；不重复同输入独立architecture/startup/behavior/E2E/HA，不提前T6passing或归档。最终实际full+独立Accept后才另阶段两时点纯状态关闭；不提交、不部署、不删除未知.pi。

## 恢复状态

- 当前唯一任务：T6 active；T1–T5 scoped passing，WIP=1。T5完整退出 b5a56382 Accept/no findings；机器证据均只归原窗口，不将本状态批次冒称原运行fresh。
- 历史第二轮full前批次（已由当前有限修复组取代）：有限修复 spec `56df`、quality `beff9611` 最终 Accept/no findings，证据 `/tmp/bolt-t6-preacceptance-fix/`。target/control 与子自测通过；父自测 608.045s/exit0，但 `.gitignore` 在运行中变化，不冒称冻结证明。用户现明确保留未知 `.gitignore`/`.pi/` 为既有输入继续，不要求再次确认归属；不修改、撤销、删除、staging 或归本任务，不扩大 quality 排除。`.pi/` 被用户现有规则忽略属于现输入，不是本任务新增排除；tracked `.gitignore`、全部 not-ignored untracked（含本计划原 bytes）及 ignore 规则均保存并纳入冻结证据。
- [x] 有限修复已完成并取得双 Accept：`scripts/test-routing-ha-process.sh` 分离正常 engine 成功见证与 hang 期限负例，保留精确124/23/29、owned group及资源断言；`docs/references/HARNESS_GUIDE.md` 修正T5已编译事实并沉淀fixture约定。生产 helper 未改，原父运行非冻结事实保留；这不是 T6 full 或最终实现 Accept。
- [ ] 历史第二轮验收批次（实际full707.160s/exit1、HA未到，原未勾验收义务不冒称成功）：唯一 writer 仅更新本计划、`PROGRESS.md`、active README 当前状态；保存 `/tmp/bolt-t6-acceptance-attempt2/` before-state、exact-state.diff、files、sha、snapshot、clean/full 原始证据。三状态文件批次后冻结全部源码/依赖/设计/稳定文档，先 `./scripts/verify.sh clean` 实际 exit0 且输入稳定，再单命令 `MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh full`（10800s预算）。无本仓并发 Maven，不干预外仓或既有 k6；运行中及结束后不再 Git-visible 写入，只写 /tmp/ignored machine reports。失败先保存原 exit/时间/同轮 raw reports、Maven/真实 HA 到达边界与缺口并停止；成功仍 T6 active，由父另作正式 quality 与独立最终实现 Accept，不重复独立架构/startup/behavior/E2E/HA。
- 关闭账本：T1技术/T2生产/T3全路线/T4入站开发与D14轮换/T5规则稳定合同各已scoped完成；O2路径主张由D10数组契约取代，all-origin sandbox由D11受控typed信任边界取代，only-restart保证由D14取代，旧反证/失败保留；D13未批准、未实施；生产签发/secret分发/上线、容量、私有制品正式发布/双构建可复现性及历史Raft/Redis问题均为后轨，不由本轮消除。T6真实full/HA与独立quality Accept仍待验收，首次full暴露fixture成功路径预算问题，有限修复已双 Accept、第二轮full已失败，当前有限Broker fixture修复及coexistence独立组待完成，不代表已验收。
- 批准坐标：`com.co.kc.thirdparty.sofa:jraft-core:jar:1.4.1-imchat.1`；原JAR SHA-256 `d276c2afb1074622c007d66fa83c00944790ece795145e3c671004f4cbcc536b`，POM SHA-256 `aa3d7d52f7289b54e559cd714ea6aa8e7b35dbcaa1f558368d66d505f8f801dc`，均已合法原字节恢复；公共commons-compress仅同GAV原缓存恢复。禁止自建artifact、换GAV/版本/mirror或借外仓build规避。正常Maven解析依赖不构成依赖版本变更。
- Loop#4 **PAUSED**；前三轮expired历史保留。用户监督由worker通知继续，不自动resume、不让scheduler在gate中写Git-visible JSON。父及其他writer同样冻结；不触碰未知 `.pi/`、exclusions、用户原改动或外仓。
- full失败先保存原exit、manifest/reports/provenance与进程清理事实，按earliest layer归因交父；无新前提不盲重跑。full成功也不标T6 passing/归档，先由父安排正式独立quality实现Review；review_required/Revise/Block不是Accept。实际full与独立Accept之后另作纯状态关闭Review和廉价门禁。无提交、部署或生产材料操作。

## 回滚与残余风险

- 施工回滚仅撤销本计划拥有变更，保全用户原改动、运行证据和清理记录。发布须停流并整体恢复匹配版本/配置/证书；不得认证失败回退普通invoke。
- D10仅实际session呈现的local/peer非空X.509全数组区间，非隐藏验证路径/anchor保证；D11可信同JVM受控typed边界，raw COUNTEREVIDENCE不变，不承诺sandbox。owner角色/成员/scope/audience/租约/epoch/Lease/fence/幂等责任不变。
- D14只撤下「同进程所有新Channel固定旧材料」保证；已有Channel保持session，新Channel仍标准TLS/严格身份/D10；不承诺自动热更新或任意inplace write一致。完整原子交付+滚动重启是运维建议，不是only-restart门禁。
- caller是接受预算，不保证不可中断DNS硬返回；墙钟回拨、无在线吊销、无TPS/容量保证，生产签发/secret分发/权限/同UID或root/崩溃残留/轮换与上线仍属外部责任，不以D13未实施宣称解决。
- 历史Raft首次复制偶发根因、私有制品发布/双构建可复现性、原Redis容器丢失事实不由本计划消除；Audit隔离Spring装配不是外部IAM/DB/Kafka全栈证明。最终full/真实HA/独立实现Accept未完成。

## 历史执行与失败记录（非当前指令）

### T6激活前入口与恢复原文（历史）

> ## 当前执行入口
>
> - 状态：**T1–T4 scoped `passing`；T5 唯一 `active`，WIP=1；T6 `not_started`**。阶段退出不是最终 full、生产安全或部署验收。
> - 本轮原件恢复/正常focused已获2242a511有限Accept；批准的最小control RED→原bytes恢复GREEN已完成，architecture实际被新Central TLS插件依赖解析失败阻断，quick未运行，现冻结交父Review，不full。此前三文档整理99d8b493 Accept及廉价门禁仍只归原精确输入，不刷新其freshness。
> - 原件恢复：来源 Reviewer `836c6257` 最终Accept/no findings，独立 `440c` 原字节双SHA实算exit0；按用户批准只恢复原件，不新build/deploy。2026-10-23T11:37:51.178115–11:38:01.128657Z固定 `org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file` 实际exit0、9.951s，Git输入changed=[]。恢复前exact GAV仅两个lastUpdated，无未知jar/POM/lock/part；落盘JAR SHA为 `d276c2afb1074622c007d66fa83c00944790ece795145e3c671004f4cbcc536b`，POM SHA为 `aa3d7d52f7289b54e559cd714ea6aa8e7b35dbcaa1f558368d66d505f8f801dc`；两个lastUpdated原SHA/mtime保持。标准metadata由Maven写入，未手工修改。证据 `/tmp/bolt-t5-artifact-restore/`。
> - 历史29.70s依赖失败及0freshXML保留；当前不再缺原JAR/POM。source spec `af6d9e74` / quality `109` Accept候选未作源码修订即真实编译/运行成功：
>
> ```sh
> mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-test/im-architecture-test -am -Dtest=BoltInvocationBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test
> ```
>
> - focused窗口2026-10-23T11:38:22.552377–11:39:02.755360Z，900s预算，实际40.203s/exit0/非超时、Git输入changed=[]。fresh owning XML四方法零failure/error/skip：productionConsumersUseControlledBoltBoundary、rejectsAddressOnlyInvocationContracts、rejectsBusinessRawCodecPipelineAndUnsafeBypasses、acceptsControlledOwnerProtocolWrites。生产扫描及逐方法负例断言/合法owner与WS正例通过；这是正常focused GREEN，不是missing-rule RED已经完成。证据 `/tmp/bolt-t5-boundary-runtime/attempt1/` 保存before/diff/SHA/命令/exit/窗口及原XML副本/mtime/SHA。
> - 本组control已按2242a511最终建议及用户批准完成：仅临时替换 `BoltInvocationBoundaryTest.java#violations` 整个方法体返回空ArrayList，所有断言/四fixture/生产源码不变，无永久property或开关。原SHA/原bytes、mutant diff/SHA及finally恢复证据 `/tmp/bolt-t5-boundary-control/`。2026-10-23T11:42:39.137297–11:42:57.382054Z，18.245s/exit1，fresh owning XML 4tests/2failures/0errors/0skips，address断言与business首invoke断言失败，production及合法正例通过；是预期规则缺失真实RED，不是compile失败。finally按原bytes恢复、五路径SHA与原候选完全一致；11:42:58.386101–11:43:15.059565Z，恢复后同focused命令16.673s/exit0，fresh四方法全零。两个窗口changed=[]。有限证据只证明两个negative方法在全规则缺失时报警，正常GREEN逐项证明六个bypass方法检出；不称六项独立RED或逐谓词mutation coverage。
> - 随后按先窄后宽实际执行一次 `MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh architecture`：11:43:20.573922–11:43:33.087372Z，1200s预算、12.513s/exit1/非超时、changed=[]。earliest为第一条依赖install在im-common的maven-jar-plugin:3.4.2解析commons-compress:1.26.1时Central TLS握手关闭，未到独立architecture clean test；不是私有JRaft再次缺失、源码或规则失败。原focused GREEN不覆盖本宽gate失败。无新网络前提不重跑/改mirror/GAV/版本/skip；quick未运行，等待父归因及下一环境处置。T5仍active、T6not_started，不full/HA/提交部署；本组立即冻结交独立Review。
> - 稳定文档/影响映射 `994cd31e` / `03cdba73`、sampling `b56ec434` / `f8c03710`、health `eb0` / `efe76e56` 已各有限双 Accept。合同精确 diff `c224c8c0` Accept，后续证据 Review `406` Accept；不是未来 diff 或 T5 passing 预批准。
> - 廉价门禁原冻结窗口 drift/clean/父 Harness 实际均 exit0（2026-10-23 10:24–10:34Z）；末次状态 SHA 与运行 SHA 分开，本次整理更不刷新其 freshness。无需为纯整理重跑昂贵父自测。
> - 当前监督 Loop#4 已由用户明确续期并创建：每5分钟检查、最多50轮；前三轮 expired 历史保留，不自动续期。本次状态批次仅本计划与根 `PROGRESS.md`；实现/稳定文档继续冻结，原整理及门禁报告归原输入，不刷新 freshness。有限只读调查已恢复 exact-SHA 原消费 POM 候选，来源/字节证据见 `/tmp/bolt-t5-artifact-recovery/pom-investigation/RESULT.md`；来源Review及原件隔离恢复已完成，focused真实结果见上方；不改 T5/T6 状态。「整理后开始」不批准 D13 私钥副本。无提交、无生产部署。
>
> 现行执行只读本入口、下列契约/任务与恢复状态。末尾「历史执行与失败记录（非当前指令）」保留旧候选、旧状态及命令原文，不能作为当前暂停/实施指令。
>
> ## 恢复状态
>
> - 本轮停点：docs-only整理已由99d8b493精确Review Accept/no findings；本轮仅一次回填后冻结并顺序drift/clean，结果记于 `/tmp/bolt-plan-consolidation/gates/`，结束后不再Git可见状态写入。Review基于全文/before/精确diff，不是allowed paths或既有c224/406自动放行。架构仍缺合法私有制品源，writer不自动开始实现/构建。
> - 当前唯一任务：T5 active；T1–T4 scoped passing、T6 not_started。架构source Accept但实际依赖失败、0 XML，未编译/neither RED nor GREEN；稳定组及shell门禁已接受，不再写成static-only/T4 blocked/startup未跑。
> - 批准坐标：`com.co.kc.thirdparty.sofa:jraft-core:jar:1.4.1-imchat.1`；原批准jar SHA-256 `d276c2afb1074622c007d66fa83c00944790ece795145e3c671004f4cbcc536b`，POM SHA-256 `aa3d7d52f7289b54e559cd714ea6aa8e7b35dbcaa1f558368d66d505f8f801dc`；隔离仓库 `/tmp/jraft-artifact-m2`。须由环境owner提供合法可访问原源并核对制品；未找到可用backup，root目录mtime不能证明外部删除。
> - 禁止自建artifact、换GAV、改POM/metadata/mirror或借外仓build规避；恢复合法源不等于授权私钥副本。不实施从未批准的D13；D14/c80596b3已批准沿原生加载，旧only-restart保证被取代而原RED仍有效。
> - 合法恢复后的首条命令和顺序见顶部/T5：actual focused compile/正反RED→GREEN，再architecture/quick，独立T5 exit；无新环境前提不执行Maven。后续T6完整冻结、clean/full/真实HA与qualityAccept、精确状态迁移Review、廉价cleanup/handoff仍全部未验收。
> - 不触碰无关用户变更、未知 `.pi/`、外仓、historical/completed计划或历史原报告；Loop#4每5分钟、最多50轮，前三轮expired历史保留，无提交部署。
>


以下为整理前剩余记录的引用留样，按原相对次序保留；Sprint Contract、事实源、各任务准确原scope与T6义务已移至上方当前正文。引用内旧「当前」「待审」「active」「未执行」及checkbox只描述当时窗口，不参与当前任务状态或执行指令；其后Accept取代暂停状态，不改写失败。引用中的重复旧恢复段不再是入口。

定位：T1原生写起点/raw反例与完整退出5495396a；T2 e7/044/0cb Block、a220 Revise及237最终；T3首次architecture失败/75与source收尾；T4 ed172外部Bean Block、两900s timeout、259/1failure、三轮profiles Block、D12 E2E、轮换037 Block/旧3/2 RED/CA gap及startup各Bean错误；T5 af6三Block、994 Revise、sampling/health失败与合同重述。保留完整命令和证据目录，不用 `/tmp` 原件备份替代仓库历史。

> # Bolt 连接层认证与统一 invoke 实施计划
>
> > Coding Agent 使用 `superpowers:subagent-driven-development`（有子代理时）或 `superpowers:executing-plans` 执行。步骤使用 checkbox 跟踪；未经用户明确要求不提交。本计划只登记施工要求，不表示测试已运行或能力已证明。
>
> ## 当前恢复：T4 scoped exit Accept，T5唯一active
>
> 独立整体exit `a21eb322` 最终Accept/noCritical，全文 `/tmp/bolt-t4-exit-final.md`；仅允许进入T5，不是full/生产安全/T6验收。T1–T4 passing，T5唯一active/WIP=1，T6 not_started。startup spec996/quality859 Accept；CA7c6fa2da/2f8888cd Accept；完整模块299（其中im-bolt262）与startup4各独立窗口，不拼为full。历史0377477c Block通过D14已批准选择与真实CA/startup证据关闭，旧RED及失败保留。Broker注销WARN属外部Broker不可用，不写成无warning或全资源独立关闭证明。私有JRaft仅后续Broker/HA/full风险，不再泛阻startup。T5 架构源码组已 source spec af6d9e74/quality109 Accept，私有 JRaft 解析阻断新测试编译，非 RED/GREEN；稳定文档/影响映射有限组 spec994cd31e、quality03cdba73 均最终 Accept/no findings；sampling b56ec434/f8c03710、health eb0/efe76e56 均最终 Accept/no findings；当前仅合同归属候选待原 Reviewer 精确 diff 审查，父 Harness488.167s/exit1保留；不归档、不提交部署、Loop#3不续期。
>
>
> ### 合同归属重述（精确 diff 已独立 Accept）
>
> 依据独立 `a1c20572` 完整矩阵起草；其 Accept 仅批准有限方法替代原则，不预批准本 diff。下述 `[x]` 只登记已有实际证据及原独立 Review 接受的阶段行为，不表示紧邻历史命令已执行，也不自行宣布现行合同已获批准。精确前件、diff、SHA 与命令退出记录冻结于 `/tmp/bolt-t5-contract-reconciliation/`；独立 c224c8c0 已在 `2026-10-23T10:20:30.232Z–10:21:22.595Z` 核对本精确三文档 diff，最终 Accept/no findings；原 a1 session 已释放，新 Reviewer 已读完整 matrix 与 exactdiff。完整 final 副本 `/tmp/bolt-t5-reconciled-gates/reports/contract-review-final.md`。批准仅限 `/tmp/bolt-t5-contract-reconciliation/source.diff`，不预批准未来 diff 或 T5 passing。本轮冻结输入实际 `./scripts/verify.sh drift`（10:24:03.753466–10:24:41.723013Z，37.969s）、`./scripts/verify.sh clean`（10:24:43.964381–10:25:16.534160Z，32.569s）及单次 `bash scripts/test-harness.sh`（10:25:39.417640–10:34:16.805706Z，517.385s，1800s预算）各exit0、各inputs changed=[]；父原日志815行 `Harness script tests passed.`，不是据child推测。完整命令/log/exit/窗口与冻结全Git输入在 `/tmp/bolt-t5-reconciled-gates/`；本末次状态回填SHA与run input分开，不宣称回填后工作树fresh。原drift/clean exit1、父488.167s/exit1及更早timeout保留。T5仍唯一active：新架构测试因私有 `com.co.kc.thirdparty.sofa:jraft-core:1.4.1-imchat.1` 实际解析缺件未编译，neither RED nor GREEN。下一步先有限独立证据Review，请用户提供原批准jar/POM的SHA与合法可访问源或恢复原批准制品；不改GAV/metadata/依赖、不外仓构建、不手动伪造m2。恢复后下一窄命令仍为 `mvn -q -pl im-test/im-architecture-test -am -Dtest=BoltInvocationBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`，缺件无新前提不执行。T6 not_started，不运行Maven/quick/full/真实HA。T1–T4 scoped passing、T5 唯一 active、T6 not_started 不变。未证明的 owner 全套回归及完整 architecture/startup/behavior/E2E/HA 明确由既有 T6 同轮 full 步骤承接，仍未勾选；不得由 selected、compile 或阶段 Review 注销。
>
> 历史有界恢复（原窗口保留，非当前startup状态）：`2026-10-23T07:18:33Z–07:27:05Z` 单次 online 窄 Maven 实际 exit1、512.55s（900s 上限，非超时）；当前 im-bolt 已编译，三个轮换方法实际执行，3 tests / 2 failures / 0 errors / 0 skipped。`liveStoreReplacementKeepsOldMaterialOnExistingAndNewChannels` 在 line280 新 Channel 客户端 local encoded 证书比较得到真实 RED：预期 old CA，实际 new CA；此前旧连接及双端 Channel ID 更换断言已通过，但首个证书断言失败，不能宣称已比较新 Channel 服务端全部数组。`restartedEndpointsPresentReplacementMaterial` 实际通过；CA overlap 在最后撤旧 CA 的 line335→350 缺少原生 SSLHandshakeException，实际 cause 为 SSLException: Bolt session certificate array missing，单独归为取证 API gap，不计已证明 TLS 拒绝。普通 im-bolt 依赖已足以运行（本轮隔离仓库有 Maven 写入，-q 未给出逐项下载进度，不能据此逐项证明远端下载）；私有 JRaft jar/POM 仍缺，仅为后续 owning 模块风险，不阻挡本次 im-bolt。精确命令、原始 XML/mtime/SHA、源码指纹及比较见 `/tmp/bolt-t4-recovery-attempt/`。无生产改动、无 secret copy 授权，不重跑同因失败；T4 仍 active/Block，T5/T6 not_started，三 startup 尚未运行。
>
> 以下“尚未编译/运行、earliest enforcer 阻塞”描述均为运行前历史；当前轮换运行以本段及主验证表为准，不改写旧证据。
>
> 现行批准语义：仅撤下「同进程所有新 Channel 固定旧材料、替换后必须重启才生效」保证；沿 Bolt 原生材料加载，不承诺自动热更新或任意同路径 inplace write 一致。已有 Channel 保持 session，新 Channel 仍完成标准 TLS/严格身份/D10 gate；统一 invoke、owner 授权、mode、namespace、mTLS、资源与 D10/D11 不变，不新增生产明文。完整原子交付+滚动重启是运维建议，不是 runtime only-restart 门禁。
>
> 历史独立退出预审 `0377477c` **Block T4** 保留（全文 `/tmp/bolt-t4-exit-preaudit.md`）。旧无热加载固定材料完成条件已由 D14 用户选择取代，但原 RED 是旧契约的有效反例，不能改写成 GREEN；本轮 server 新 Channel 后续完整数组尚未比较。CA 旧 → 双信任 → 新叶 → 撤旧 CA 的双向原生拒绝、重启、三 startup 与资源退出仍未完成。D13 从未获实施授权，现为被取代候选；不创建私钥副本，不复制 initializer/manager/TrustManager、不反射或改依赖。
>
> 后续有限测试提案唯一代码路径为 `im-plugin/im-bolt/src/test/java/com/co/kc/imchat/plugin/bolt/BoltAuthenticatedTlsTest.java`；必要文档见主设计 D14 的精确清单，不修改其他代码/脚本。保留现有三个轮换方法的原运行证据；获独立设计 Accept 后才有限调整第一方法及本文件 helper，验证 native 支持语义而非仅删除失败断言：完整交付前后旧 Channel 双端 local/peer encoded 全数组/session 保持；关闭并强制新 Channel 后双端 ID 改变、client/server 实际 session 全数组分别比较；重启后新材料仍验证。CA 方法先定位 `SSLException: Bolt session certificate array missing` 的原生握手 cause 取证 API gap，再取得双向原生 TLS 允许/撤旧拒绝证据；不得 AUTH/timeout 假绿，无法证明则 Block。
>
> - [x] 将本轮精确四文档 diff/SHA 冻结到 `/tmp/bolt-t4-native-material-decision/`，由父立即独立设计 Review；Accept 前不改测试或生产。历史 source/spec Accept 及 cfe0699d 证据 Accept 不代替 D14 设计 Review；现 D14 独立设计 c80596b3 最终 Accept，首轮 Revise 已关闭。
> - [x] D14 有限测试调整及精确 selector 已冻结并审查；原 only-restart 保证由 D14/c80596b3 取代，不是将旧 RED 跑绿。最终 `/tmp/bolt-t4-ca-retirement/commands.log` 实际选择 `BoltAuthenticatedTlsTest#atomicStoreReplacementPreservesExistingSessionsAndNativeNewChannelsReadReplacement+restartedEndpointsPresentReplacementMaterial+caOverlapRestartSequenceAllowsNewAndRejectsRetiredAuthority`（`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltAuthenticatedTlsTest#atomicStoreReplacementPreservesExistingSessionsAndNativeNewChannelsReadReplacement+restartedEndpointsPresentReplacementMaterial+caOverlapRestartSequenceAllowsNewAndRejectsRetiredAuthority -Dsurefire.failIfNoSpecifiedTests=false test`），27.65s、exit0、3 tests 全零；spec7c6fa2da/quality2f8888cd Accept。旧名称、3/2 RED、3/1 failure 与中间取证失败各保留原窗口。
>
> 原步骤历史（不补造执行；组合、顺序及当时状态不代表当前义务）：独立设计 Accept 后，冻结该唯一测试文件的有限调整、精确方法名/窄命令及必要文档提案并 Review；不得仅删旧失败断言。历史命令 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am '-Dtest=BoltAuthenticatedTlsTest#liveStoreReplacementKeepsOldMaterialOnExistingAndNewChannels+restartedEndpointsPresentReplacementMaterial+caOverlapRestartSequenceAllowsNewAndRejectsRetiredAuthority' -Dsurefire.failIfNoSpecifiedTests=false test` 本轮已运行 exit1，不重复把旧契约跑绿。若重命名须先登记替代 selector，保证三个语义场景确实执行/不跳过；单条 900s/no 并发，保存源码 SHA、命令/exit、XML/mtime，原窗口和报告不覆盖。
> - [x] D14 三场景与 CA 双向 native 拒绝及 fixture 清理已由上述三方法、`/tmp/bolt-t4-post-ca-regression/commands.log` 的 im-bolt `-am test`（299，其中 im-bolt262 全零）及 a21eb322 完整映射接受；已有 session 保持，新双端物理 ID 改变、四组 actual local/peer 全数组与 restart 均有实际证据。随后三 startup 最终4 tests 全零、spec996/quality859 Accept；完整退出 `/tmp/bolt-t4-exit-final.md` Accept。不同窗口不合并为 full，最终复验仍归 T6。
>
> 原步骤历史（不补造执行；组合、顺序及当时状态不代表当前义务）：取得 client/server session 与 restart、CA 双向原生拒绝及 fixture 资源清理真实证据；合法恢复后执行下文三 startup focused，再作 T4 整体退出 Review。所有余项验收前 T4 active/Block，不提前 passing；T5/T6 not_started，full/HA 仍待 T6。
>
> 历史运行前 earliest enforcer 环境阻塞及后续 `/tmp/jraft-artifact-m2` 私有 JRaft jar/POM 缺件未解除；无新前提，不执行 Maven、不改 POM/依赖/设计、不提交或部署。readiness 不证明依赖恢复。
>
>
> 历史轮换 source/spec `b183` **Block** 及当时修订（已由最终 source Accept 关闭）：fixture 原 `client.addConnectionEventProcessor` 注册到 RpcClient 自有 listener，而受控 manager 使用另一份 listener，`connectedSsl` 会等待超时，不能作为 TLS 拒绝证据。当前仅修本测试 RotationEndpoint：client.init 后、首连接前，将实际 client.getConnectionManager() 返回的 owned manager 转为公开 DefaultConnectionManager，经公开 getConnectionEventListener().addConnectionEventProcessor 注册；未新建 manager/listener，native SslHandler handshakeFuture 断言不变。ConnectionManager 接口没有此 getter，DefaultConnectionManager 的 public getter 及 listener 的 public 注册 API 已只读核对原生字节码。精确 `/tmp/bolt-t4-rotation/spec-revise.diff`、`spec-revise.sha`；当时候选待原 b183 复审再 quality，环境未变无 Maven/keytool/网络，仍未编译/未运行，neither RED nor GREEN。无生产修复授权，T4 active，T5/T6 not_started。
>
> 历史运行前 source Review：spec `b183` 最后 SPEC delta 无 must、最终 source/spec **Accept**；quality `efa22cba` 最终源码 **Accept**。原 listener 归属及异步 CONNECT teardown race 两次 Block 保留为历史；最终策略已删除 CONNECT observer/promise，仅从实际失败 cause 链要求原生 SSLHandshakeException。源码保留 native handshakeFuture cause 链不保证每个方向 alert/close 都呈现此类型；runtime 若未呈现必须记录取证 API gap，不退回 AUTH/timeout。三个轮换候选尚未编译、尚未运行，neither RED nor GREEN。三 startup 未运行、earliest Enforcer 依赖环境阻塞及后续批准 m2 私有制品缺件不变；T4 整体预审 0377477c Block 未解除。生产材料修复未授权，等待真实 RED 和设计 Review，不批准 secret copy。T4 唯一 active，T5/T6 not_started；writer 停止，由父继续有界环境恢复监督，无环境新前提不重复构建。
>
>
> 历史 Quality `efa22cba` **Block** 唯一异步 CONNECT observer teardown race 及当时修订（已由最终 source Accept 关闭）。本次代码前精确范围仍仅 `BoltAuthenticatedTlsTest.java#rejectsAuthority` / `RotationEndpoint`：当前正式 client/server 都 final 且固定 option，受控 factory final/package-private，无已有 CandidateObserver 或可注入的公开 pipeline hook；既有 support 的 EXTENDED_NETTY_CHANNEL_HANDLER 只适用于其独立原生 RpcClient/RpcServer，不能穿过正式受控 option 冻结。不新增 seam/反射/复制 initializer。选择更窄公开失败 cause 取证：删除异步 pipeline observer，CA 拒绝要求调用失败 cause 链包含原生 SSLHandshakeException，不能仅 AUTH 分类或 wrapper SSLException。BoltCertificateValidity.authenticate 在 handshakeFuture 失败后以 SSLException(cause=handshakeFuture.cause()) 包装，factory/manager 保留 cause 链。若运行时对端拒绝只留下 channel-close 且丢失原生原因，本断言必须失败并记录 API/证据缺口，不退回 timeout/分类假绿。原 b183 listener 归属 finding 已关闭的历史保留；最终 source/spec 与 quality 均 Accept，仍未运行、neither RED nor GREEN。
>
> ## 验证分层
>
> 历史轮换运行：历史有界恢复（原窗口保留，非当前startup状态）：`2026-10-23T07:18:33Z–07:27:05Z` 单次 online 窄 Maven 实际 exit1、512.55s（900s 上限，非超时）；当前 im-bolt 已编译，三个轮换方法实际执行，3 tests / 2 failures / 0 errors / 0 skipped。`liveStoreReplacementKeepsOldMaterialOnExistingAndNewChannels` 在 line280 新 Channel 客户端 local encoded 证书比较得到真实 RED：预期 old CA，实际 new CA；此前旧连接及双端 Channel ID 更换断言已通过，但首个证书断言失败，不能宣称已比较新 Channel 服务端全部数组。`restartedEndpointsPresentReplacementMaterial` 实际通过；CA overlap 在最后撤旧 CA 的 line335→350 缺少原生 SSLHandshakeException，实际 cause 为 SSLException: Bolt session certificate array missing，单独归为取证 API gap，不计已证明 TLS 拒绝。普通 im-bolt 依赖已足以运行（本轮隔离仓库有 Maven 写入，-q 未给出逐项下载进度，不能据此逐项证明远端下载）；私有 JRaft jar/POM 仍缺，仅为后续 owning 模块风险，不阻挡本次 im-bolt。精确命令、原始 XML/mtime/SHA、源码指纹及比较见 `/tmp/bolt-t4-recovery-attempt/`。无生产改动、无 secret copy 授权，不重跑同因失败；T4 仍 active/Block，T5/T6 not_started，三 startup 尚未运行。
>
> 本节汇总当前验证归属，详细运行窗口、SHA、失败及 Review 历史见后文记录。T1–T3 已 passing；T4 已取得局部生产与真实 DEV E2E 证据，三 consumer startup现已实际4tests全零，独立spec/quality及整体退出未完成。旧成功报告只证明对应运行时的输入，不代表当前工作树 fresh；最终 full 尚未运行。聚焦测试使用 `-Dsurefire.failIfNoSpecifiedTests=false` 允许上游无指定类，但必须核对所属模块报告确有每个指定测试、零跳过、零失败，不能以退出码掩盖未执行。
>
> | 层级 | 命令或证据 | 必须/可选 | 当前结果 |
> |---|---|---|---|
> | 环境 | `./scripts/verify.sh readiness` | 必须 | 本次成功；不证明私有制品/运行拓扑可用 |
> | T1 技术原型 | T1 的逐项 RED/GREEN 命令和真实 TLS/pipeline/生命周期证据 | 必须先行 | 完整退出 5495396a、质量 06107abb Accept；最终模块174 tests exit0，保留旧170及历史失败记录 |
> | 轮换当前实际运行 | 本计划顶部三方法 online 窄命令，900s/no并发 | 本次已执行 | exit1 非超时；3/2 failure/0 error/0 skip；live 新 Channel 材料真实 RED、restart pass、CA native cause 证据 API gap；详见 `/tmp/bolt-t4-recovery-attempt/` |
> | 归属模块 | T2–T4 指定 Maven 命令；`./scripts/verify.sh affected` | 必须；affected 用于混合 worktree 迭代 | T2/T3及T4各有限组已运行并审查；SDK6、独立Broker17、cleanup fresh WS22属于质量收尾前输入；当前三startup4全零待本组Review；私有 JRaft 仅后续Broker/HA/full缺件风险，im-bolt 普通依赖本次已足以运行 |
> | 架构/契约 | `./scripts/verify.sh architecture`；T5 的静态检查正反 fixture | 聚焦时使用 | 历史默认仓库解析失败保留；父使用 `/tmp/jraft-artifact-m2` 已实际成功，见本轮记录；T5 未运行 |
> | 行为/启动/实时 | `./scripts/verify.sh behavior`、`./scripts/verify.sh startup`、`./scripts/verify.sh e2e` | 风险必须覆盖，最终可由 full 同轮实际报告满足 | 正式DEV E2E于2026-10-23T04:47:25Z完成，exit0、1 case、同轮记录stable；后续等价源码收尾尚未fresh编译。三startup及最终同轮覆盖待完成 |
> | 完整验收 | `./scripts/verify.sh clean`、`./scripts/verify.sh full`、`./scripts/verify.sh quality` | 必须 | 未运行 |
> | 状态关闭 | `./scripts/verify.sh drift`、`./scripts/verify.sh clean`、`./scripts/verify.sh quality`、`./scripts/verify.sh cleanup`、`./scripts/verify.sh handoff` | 实现验收后 | 未运行 |
>
> 先窄后宽，失败先修复或归因；同一成功 full 后不重复独立 Java/HA 门禁。不把 handler=0 单独当作无提前编码证明。所有时序用 latch/future、显式时间输入及有界等待，不用 sleep。保留 RED 对照的真实失败断言和退出码，不将预期失败算 GREEN。
>
> ## 任务状态
>
> 当前 **T1–T4 passing，T5唯一active/WIP=1，T6 not_started**。D12及owner/profile/CA有限组已有独立Accept；CA spec7c6fa2da/quality2f8888cd Accept，完整im-bolt模块262tests全零（-am299），三startup最终4tests全零。Message仅补外部基础设施fixture，当前新spec/quality与完整T4退出Review pending，writer停止；不自动T4passing。私有JRaft仅后续Broker/HA/full风险，不能再泛称阻三startup。历史RED/错误/旧报告归属保留，最新准确证据见runtime/RESULT.md与post-ca-regression/RESULT.md。
>
> - 历史任务 T1：
>   - ID：T1
>   - 行为目标：D10 数组有效区间、D11 受控发送及其余技术退出闸门
>   - 范围：仅 im-bolt 测试源码及本计划证据
>   - 状态：`passing`
>   - 验证证据：完整退出5495396a Accept、质量06107abb Accept，模块174 tests；raw COUNTEREVIDENCE 保留
>   - 阻塞/备注：仅技术退出，非生产验收
> - 历史任务 T2：
>   - ID：T2
>   - 行为目标：安全连接能力及统一目标契约
>   - 范围：im-bolt
>   - 状态：`passing`
>   - 验证证据：完整退出 f4ef3381、quality 3729b260 Accept
>   - 阻塞/备注：未发布中间态；不等于最终full
> - 历史任务 T3：
>   - ID：T3
>   - 行为目标：全路线与所有 fixture 同步迁移
>   - 范围：Gossip、SDK、Peer、三消费方、HA/E2E
>   - 状态：`passing`
>   - 验证证据：spec 40ac3088、quality 02665be5 复审Accept；旧入口已删除
>   - 阻塞/备注：不发布中间态；运行验收继续归T4/T6
> - 历史任务 T4：
>   - ID：T4
>   - 行为目标：入站与受限开发模式
>   - 范围：Bolt/Broker/Gateway/消费方配置
>   - 状态：`passing`
>   - 验证证据：整体exit a21eb322 scoped Accept；CA7c/2f Accept、startup996/859 Accept；模块299（im-bolt262）、startup4各原轮全零
>   - 阻塞/备注：原037 Block已关闭，旧RED/失败保留；不是full/生产/T6 Accept
> - 历史任务 T5：
>   - ID：T5
>   - 行为目标：无旁路规则与稳定文档
>   - 范围：架构测试、Harness、README/Architecture
>   - 状态：`active`
>   - 验证证据：先既定四文件所有权规则有限组
>   - 阻塞/备注：稳定文档/影响映射后续，最终冻结前完成
> - 历史任务 T6：
>   - ID：T6
>   - 行为目标：完整隔离验收及独立 Review 关闭
>   - 范围：全变更集、当前计划/索引
>   - 状态：`not_started`
>   - 验证证据：未运行
>   - 阻塞/备注：两时点关闭
>
> ## 详细任务
>
>
>
> 命令约定：令 `C` 为下面表中准确类名。每项先执行 `mvn -q -pl im-plugin/im-bolt -am -Dtest=$C -Dsurefire.failIfNoSpecifiedTests=false -Dbolt.gate.control=$K test`，预期所属安全断言失败；再移除 `-Dbolt.gate.control=$K` 执行相同命令，预期全部通过。测试中实现此测试专用开关，默认安全，且断言 control 名称合法，避免拼写错误跳过反证。
>
> | C / K | RED 对照及精确用例目标 | 必须取得的 GREEN/退出证据 |
> |---|---|---|
> | `BoltPresentedChainGateTest` / `leaf-only` | `allPresentedCertificatesConstrainWindow`：仅检查叶时，较早中间 CA 或其他已呈现成员的边界断言失败 | 真实原生握手成功后才建立事实；两端非空、全 X.509，所有成员含出现的根/锚参与区间，不声称实际路径保证 |
> | 同类 / `omit-presented-root`、`omit-unused-presented` | `presentedRootAndUnusedCertificateConstrainWindow`：剔除已呈现根或较早未使用的已呈现证书时失败；两个 control 分别运行 | 实际 session 数组先证明 fixture 确已呈现成员；配置但未发送证书不参与（`unsentConfiguredCertificateIsExcluded`）。省略根不保证运行期检查该根；原生拒绝则记录握手拒绝，不伪造成功 |
> | 同类 / `inclusive-not-after`、`exclusive-not-before` | `windowBoundsUseExplicitInstant`：把 notAfter 等号放行或 notBefore 等号拒绝时分别失败 | 显式 `Instant` 参数精确断言最晚 notBefore 含等号、最早 notAfter 不含等号及空区间拒绝；不新增可注入生产 Clock。合成数组只测纯计算/缺失类型负例，不建立认证事实 |
> | 同类 / `skip-leaf-uri` | `nativeTrustAndStrictLeafUriRejectBeforeForwarding`：跳过叶 URI 时，无/重复/多匹配 URI 安全断言失败 | 原生标准信任下正确身份正例；错 URI/其他命名空间、no-URI、错误 CA、明文、两端过期/未生效负例均 forwarded=encoded=handled=0，保留原因；区分原生握手拒绝与握手后额外拒绝，中间 CA/根无 URI 不误拒绝 |
> | 同类 / `reuse-channel-facts` | `eachTlsChannelReacquiresSessionFacts`：新 Channel 复制旧事实时失败 | TLS 1.2/1.3 各有完整/恢复握手证据；每个 Channel 握手成功后重新读取非空 X.509 两端数组并校验，读取失败/缺失/类型错误 fail closed。未证明恢复实际发生不能标为支持；无支持证据则证明恢复失败关闭，不承诺可用 |
> | `BoltOutboundOriginsGateTest` / `tail-only` | `everyBusinessOriginIsRejectedBeforeEncoding`：故意只装尾部 gate，至少一个较前 ctx.write 穿过编码计数器，安全断言失败 | 逐项审计 D11 允许装配下 RpcClient String/Url/Connection 的 sync/future/callback/oneway、实际 CONNECT listener 和全部可达 typed ctx.write；保留原生 context 的 synthetic typed 探针并明确其性质，不能代替实际原生来源。记录 attempted、forwarded、encoded、远端 handled，非法 typed 新业务后三者为零且 promise 保留原因；原生 TLS/encoder/response/control/rawbytes 另有实际路径与正向可用证据 |
> | 同类 / `missing-command-context` | `directConnectionWithoutControlledContextCannotSend`：缺命令上下文被默认放行时失败 | 每个业务 RpcRequestCommand 带本机目标/独立单调预算，不写 wire、不写共享 Channel deadline；PENDING/错绑定/过期/超预算拒绝；TLS/control/正常与错误响应实际可用；不得死锁握手或因到期切断已放行请求的在途响应；目标及 deadline 不得存入共享 Channel/Url |
> | `BoltSharedCreationGateTest` / `cancel-shared` | `shortCallerCannotCancelLongCallerCreation`：短 caller cancel 共享 future 导致长 caller 失败 | 两waiter共享原生task/真实TLS、短LOCAL长成功已局部验证；中断恢复与独立创建截止/停止后候选关闭已验证。序列化/实际排队/gate/解码各阶段及全部上限仍待验证 |
> | 同类 / `unbounded-admission` | `creationQueuePoolsAndPendingCallersStayWithinInitialLimits`：绕过上限或 caller 放弃即释放仍运行任务额度，超额断言失败 | 初始 2/16/64/1/64 上限饱和及拒绝 LOCAL；创建失败、超时、中断、重入和 shutdown 后任务/队列/候选/池清理；恢复 ThreadLocal 上层值并 finally 清理；同址 B/C 与 key 碰撞负例、不同策略隔离；在途数仅底层完成/原预算结束释放 |
> | `BoltBackgroundLifecycleGateTest` / `native-background-enabled` | `disabledBackgroundCannotCreateFromAnyNativeEntry`：故意保留一个 Bolt 默认重连/heal/预热入口，创建计数断言失败 | 对 ReconnectManager、createConnectionAndHealIfNeed、HealConnectionCall 实际集成触发；自然远端关闭（不调用 manager.remove）、重复失败、同址换错证书、shutdown 与初始化中途失败。首版选择禁用：证明默认、Url、选项和入口全部关闭，裸地址创建拒绝，无重排残留；普通按需新建仍重新认证 |
>
> D11 剩余发送切片（均为未来要求，仅修改 `BoltOutboundOriginsGateTest.java` / `BoltSecurityGateTestSupport.java`，不新增框架）：
>
> - [x] `pendingConnectListenerRejectsBeforeEncoding` / `pending-connect`：实际 CONNECT listener 直接 Connection 调用；构造流程等待事件尝试结束才发布会话认证事实，证明认证 PENDING 而非暂停TLS握手。安全路径编码/放行/处理0且promise保留同一拒绝原因；不安全control在编码计数0/1断言失败。认证后同Channel正例可用。证据见下方本轮记录。
> - [x] `expiryPreservesInflightResponse` / `close-inflight-on-expiry`：真实请求已接受后由latch暂停正常响应，显式时间推进到会话区间earliestNotAfter拒绝新发送；旧响应仍正常完成，原Channel保持打开。control提前关闭后，指定响应成功断言因ConnectionClosedException失败，不靠sleep。错误响应分支留在下方独立native response方法，未宣称已完成。
> - [x] `concurrentCommandsKeepTargetAndDeadlineIndependent` / `shared-command-context`：同Channel先扣留合法命令，再使错目标/纳秒截止等号命令进入gate，最后释放合法命令；分别核对逐命令Admission集合、attempted=2、forwarded/encoded/handled=1及明确拒绝原因，两请求结束后才reset。control共享Admission使扣留命令成功断言失败，不写共享Channel/Url目标/deadline。
> - [x] `nativeIdleHeartbeatAndAckRemainUsable` / `block-native-control`：真实IdleStateEvent触发原生heartbeat/ACK，核对两端promise、ACK ID、原生future消费/heartbeat count及TLS前后字节；业务attempted/forwarded/handled=0，两端encoder输出各1。control准确失败于ACK写入成功断言。旧synthetic witness保留并区分。
> - [x] `nativeNormalAndErrorResponsesRemainUsable` / `block-native-response`：真实RPC产生SUCCESS与SERVER_EXCEPTION，错误响应在已接受后以latch暂停，证书截止后释放仍完成，要求InvokeServerException及具体错误文本而非timeout。两端response promise/编码及业务计数均核对；control阻断响应，在收到正常结果断言失败。
> - [x] `controlledAssemblyRejectsUnsupportedExtensions` / `allow-unsupported-extension`：真实关闭构造入口，external/front/back、真实codec经factory/manager注入、native NETTY_EPOLL_SWITCH选择共6负例在init/connect前拒绝；unsafe control实际安装/通信后6个拒绝断言失败，0error。safe-default原生TLS+ownedguards正例可用，7项GREEN；模块95 tests通过。证据 `/tmp/bolt-t1-assembly/` 含source.diff、SHA、commands/exits及reports；协调者重跑exit0。spec `ec6b8b77-e5b7-420`、质量 `5a631c90-e08e-4c5` 均scoped Accept，不是生产Spring/运行期mutation containment证明。manager移交前初始化失败资源仍归后续生命周期验证，不提前勾绿。
> - [x] `nativeTaskSubmissionRejectionResponseRemainsUsable` / `block-native-response`：公开UserProcessor.getExecutor在execute提交阶段确定性拒绝，原生RpcCommandHandler独立写出SERVER_THREADPOOL_BUSY；核对请求/响应ID、InvokeServerBusyException、handler0、rejection1、响应promise成功及两端编码输出各1。control阻断后，busy cause断言明确拒绝实际timeout，1 failure/0error；GREEN1 test通过，模块96 tests通过，协调者独立重跑exit0。证据 `/tmp/bolt-t1-native-busy/` 含before/source.diff/SHA/command/exit/reports。spec `5d853afa-04cf-43a`、质量 `7e23bee8-cde5-412` 均scoped Accept，补齐 `04c2da8b-7836-4e9` 指出的具体来源缺口，保留原历史Block，不据此自动通过完整T1。
>
> CONNECT/inflight 本轮证据：`/tmp/bolt-t1-connect-inflight/` 保存before/source.diff、精确command/exit/reports及历史失败。方法级control RED各1 test/1 failure/0 error，分别击中编码计数和在途响应成功断言；无control两方法2 tests通过。`mvn -q -pl im-plugin/im-bolt -am test` 85 tests、0 failure/error/skip，协调者独立重跑exit0。spec reviewer `9aa88956-b5b3-493` 和质量 reviewer `9b2c7a87-e1b9-4ee` 均 scoped Accept；support仅复用现有authenticate。保留早期oneway未同步抛错和expiry-control未包装测试error的失败归因，不晋级为有效RED；report目录残留历史2026-10-19 dumpstream不作为本轮新失败或本轮执行证据。
>
> 原生control/响应/隔离本轮证据：`/tmp/bolt-t1-native-control/` 保存before/source.diff、SHA、commands/exits及各轮报告。三方法control分别1 test/1 failure/0 error，命中ACK写成功、正常结果收到及扣留命令成功断言；无control三方法3 tests通过。模块命令88 tests、0 failure/error/skip，协调者独立重跑exit0（`coordinator-module.exit`）。spec reviewer `7ff88333-a478-435`、质量 reviewer `cdb6fc14-919c-40d` 均scoped Accept，仅限本轮；现有support未修改。响应control中timeout只作为阻断后的预期失败原因，不将timeout当GREEN的服务器错误证明。未额外声称独立exceptionRemotingContext hook或完整原生来源已收口。
>
> 受控原生写起点账本（结构性审计，不代表完整T1退出）：BaseRemoting的sync/future/callback/oneway四处Channel写入已由12API覆盖；实际CONNECT/PENDING已覆盖；RpcHeartbeatTrigger的ctx写入及RpcHeartBeatProcessor ACK已由真实idle/ACK覆盖；RpcRequestProcessor.sendResponseIfNecessary经RemotingContext的正常/业务异常响应已覆盖；RpcCommandHandler.processExceptionForSingleCommand的独立提交拒绝响应现由busy切片覆盖。原生encoder/TLS输出沿上述真实路径有正向计数。8个ctx typed探针只证明安装位置；raw预编码witness仍是D11排除机制的COUNTEREVIDENCE，不是运行期拒绝。生产装配启动/所有权架构规则仍归T2/T5；完整退出复核不得仅凭这份账本勾绿。
>
> 共享创建首切片证据：
>
> - [x] 新增 `BoltSharedCreationGateTest.java`，同一原生connTasks/RunStateRecordedFutureTask/ConnectionPool，公开hook薄调度及2线程/16队列，无影子pool或共享future registry。`shortCallerCannotCancelLongCallerCreation` 验证短超时LOCAL不cancel、长caller取得真实认证连接；中断LOCAL并恢复标志。
> - [x] `independentCreationBudgetRejectsLateAuthenticatedCandidate` 两分支分别用创建截止等号、截止前stopAdmission，候选均LOCAL拒绝/close完成/空池/业务写0，创建固定3s从提交起算但实际排队到期尚未测试。初轮Review `ec8a43b2-1168-463` Revise指出stop被deadline遮蔽，修正shutdown输入为CREATION_NANOS-1后复审scoped Accept，不擦除旧Revise。
> - [x] `cancel-shared` 方法级RED1 test/1 failure/0error，命中长caller成功断言，另有原生shutdown CancellationException suppressed，不能描述为清理完全成功；`publish-late-candidate`方法级RED2 tests/2 failures/0error。无control类级3 tests通过，最终模块99 tests、0 failure/error/skip，协调者重跑exit0。命令形式沿C/K约定，精确command/exit/reports/before/diff/SHA在 `/tmp/bolt-t1-shared/`；修正后证据为revised-*、`coordinator-revised-module.exit`，旧cancel记录不标新运行。质量 reviewer `157ca94e-64f5-46d` scoped Accept。
> - [x] 资源饱和切片：`creationQueuePoolsAndPendingCallersStayWithinInitialLimits` 参数化EXECUTOR/POOLS/CALLERS分别实际饱和2+16、64个真实TLS目标池（每池1连接）、64创建waiter；超额LOCAL/RejectedExecutionException。同RpcClient及原生connTasks，无影子pool。`queuedCreationExpiresBeforeFactoryAtExactSubmissionDeadline` 在提交截止等号、进入factory前拒绝排队任务，随后同目标新预算可用。waiter超时不释放额度，底层callable实际完成才释放，尚不是RPC业务在途额度。
> - [x] `unbounded-admission` 3 tests/3 failures；`queued-expiry` 1 test/1 failure（factory2变3）；`release-on-wait-timeout` 3 tests/1 failure（pending64变63）；均0error、有效断言反证。新类7 tests通过、模块103 tests通过，协调者重跑exit0。证据 `/tmp/bolt-t1-resources/` 含before/delta.diff、SHA、各command/exit/reports；最终SHA `49493f2637343b10a55004afb2ed6beb9ffef7644e96cf18c8cdb8afd8d76745`，较早RED不标最终SHA重跑，baseline-red.exit=0不计有效RED。spec `866aab30-34e0-477`、质量 `5cdd169a-1887-489` scoped Accept。
> - [x] 同文件新增 `exceptionalBackendCompletionReleasesWaitingHandles`（FAIL/CANCEL）、`lateFailedTaskPreservesReplacementInNativeMap`、`sameAddressIdentityAndPolicyRemainIsolated`、`delimiterBearingTargetsHaveDistinctKeys` 共5项：真实backend失败完成释放64创建waiter；取消的是caller-result而非共享创建；公开原生map受控替换在旧任务迟到失败后保留；实际TLS预期C不复用B、AUTHENTICATION保留SecurityException，policy独立task/channel，长度前缀key防分隔符碰撞。
> - [x] controls `release-on-backend-cancel` 2 tests/1 failure（64变63），另有清理suppressed pending=-1，不能称RED清理成功；`remove-replacement-on-failure` 1 failure；`collapse-target-key` 2 failures，均0error。新类12 tests及模块108 tests通过，协调者重跑exit0。证据 `/tmp/bolt-t1-shared-edges/` 含before/source.diff/revised-source.diff、各command/exit/reports/SHA；较早失败留样不晋级。spec `ead873a8-f73c-439` 初轮Revise发现CANCEL未等待被取消caller的permit-owning completion竞争；补有界等待其dependent completion和实际backend原因后复审Accept。质量 `902bb829-2a60-497` scoped Accept。最终SHA `488fd45161fc14d618c2d11fb9c94e35fe4462eab09e8d4729ca7af8e6461eb5`。
> - [x] `queuedCreationCancellationCompletesAccounting` / `skip-queued-cancel-completion` 与 `reentrantFactoryRestoresTargetScope` / `drop-parent-target-scope`：排队任务启动前取消完成实际 accounting，释放64创建 waiter、移除队列/旧 task，同目标新任务可创建；真实工厂同线程嵌套成功/错身份失败均恢复外层目标，最外层 finally 后裸创建拒绝。两个方法级 control 各1 test/1 failure/0 error，分别命中取消完成与外层创建成功断言；取消 RED 另有 pending=64 的 suppressed 清理失败，历史 cancel-shared RED 也有 CancellationException suppressed，不描述为 RED 清理成功。无 control 类级14 tests、模块110 tests通过；证据 `/tmp/bolt-t1-cancel-scope/` 的 frozen-* command/exit/reports，最终源码 SHA-256 `60ee543c91bf0a295b6f199a8a01ce977a991c9648307b4152430b671a98ce7e`。spec `778ba256`、质量 `8d5c6298` 均 scoped Accept，不代表完整 T1。
> - [x] 所列 RPC 在途、callback executor/解码及后台生命周期局部组已实际验证并获双 Review，完整跨组退出仍待独立审核；不将创建 waiter 额度或局部切片当完整预算资源闸门/T1退出。保留早期重复shutdown清理失败，修正独立assertAll及单一manager关闭后新GREEN通过。
>
> RPC 原生完成首切片历史为 **Review Block，修复后已获 scoped Accept**：spec reviewer `aa310b30` scoped Block 指出 `CompletionFuture.admit` 在 client/Channel 计数前发布 admitted，native completion 可观察半登记并 NPE/重复核算；此前131 tests通过不能覆盖该 finding。修复候选已用同一 synchronized 协议串行化完整计数登记/发布与终态 release-once，新增 `registerTerminalRaceKeepsAccountingBalanced` / `publish-admission-before-counts`。实际原生 Timer 在 gate 尚未 forward/encode、登记中途到期，与注册交错；不是手工 putResponse 或不可能的远端提前响应。候选 RED1 failure、GREEN race1/selected22/class58/module132均0 failure/error/skip；原 hook/caller controls 仍分别10/6 failures、0 error。精确 `/tmp/bolt-t1-rpc-completion/revised-*`、before/diff/SHA及 RESULT 保留旧 Block 和初次失败；修复后 spec `aa310b30` scoped Accept、质量 `50bcaf7e` scoped Accept；父模块重跑132 tests exit0（`/tmp/bolt-t1-rpc-completion/coordinator-revised-module.exit`），源码 SHA-256 `2ed80bb4397cc02a52d65af093c78333ccbec831e100acf755fa228e71b65c32`。矩阵首两方法及竞争回归已通过本组复核，但 callback executor 排队/拒绝、解码失败、backend interruption及send cause完整保留仍 pending，不勾完整 T1。
>
> RPC 容量与到期排空切片已获 spec `91697835`、质量 `d87c37d0` scoped Accept/findings none：`clientRpcLimitIsSharedAcrossChannels`、`expiredChannelDrainsAfterNativeCompletion`、`nativeTimeoutReleasesRpcPermitOnce` 的 RED 分别1/6/3 failures、0 error；GREEN selected10/class68/module142，父模块重跑exit0 `/tmp/bolt-t1-rpc-capacity-drain/coordinator-module.exit`。源码 SHA-256 `2303714b28a1903b3dbe3e6014a7ece988c6ddec4e7dc3731aa4cbfa349198ce`，before/diff/commands/exits/reports见同目录。容量为同client跨两Channel顺序真实准入，不是并发stress；不据此补勾完整RPC/T1。下一步仅矩阵最后两方法：真实写失败/close及callback executor/解码边界；backend interruption仍待验证。生产提取前审查关闭与released通知移出rpcAccounting锁的可重入影响，不将此建议冒称已完成。
>
> RPC 原生终态/callback 边界当前为 **spec `6efcdc32` scoped Revise，修复候选待复审**。首次151 tests通过不覆盖 callbackSubmitted 在入队/拒绝计数前发布的竞争；父同输入模块重跑实际失败（151 tests/1 failure，`/tmp/bolt-t1-rpc-terminal-edges/coordinator-module.log`、`.exit`），不得称该输入 fresh GREEN。失败为 close 分支等待 responseWrites 时，关闭 Channel 已移除 observer；此前 server receipt 已由 requestAccepted 证明，不是请求未到达。候选将信号移至真实入队/拒绝计数之后；close 分支仅在确证 server handler 返回、server Channel closed、eventloop barrier及 observer 已移除时，不再要求不可观察的 response promise。该证据不声称关闭后 native 写尝试已完成或远端收到响应；开放 Channel 的 timeout 迟到响应 promise 断言保留。候选 revised RED 两组仍有效，GREEN selected9/class77/module151，精确 before/diff/SHA/command/exit/reports 在 `/tmp/bolt-t1-rpc-terminal-edges/revised-*`；源码 SHA-256 `ea68fdc3b5aba94413c0affe09e052db25b615704dc391168e1684ace0f25810`。历史 Revise 与父失败保留，不提前写 Accept，等待原 reviewer 复审及独立质量 Review。
>
> 原生终态边界最新审查状态：spec `6efcdc32` 已复审 scoped Accept；质量 `295f59b3` scoped Revise 指出 nativeFuture.putResponse 先唤醒 SYNC，而 nativeTerminal 后发布，SYNC wrapper 可能先完成 released，读取 nativeTerminal 存在 NPE 窗口。最小修复候选仅在 sendFailureAndCloseReleaseRpcPermitOnce 的保证 putResponse 分支，读取前有界等待 terminalAttemptDone（其完成在 nativeTerminal 发布后）；未给不经过 putResponse 的 sync 原生 TIMEOUT 增加等待。second-revised GREEN selected9/class77/module151，精确 before/diff/SHA及command/exit/reports见 `/tmp/bolt-t1-rpc-terminal-edges/second-revised-*`。当前待原质量 reviewer 复审；此前父151tests成功不覆盖新 finding，历史 Revise/失败均保留，不提前登记双 Accept。
>
> 原生终态边界修复后退出：spec `6efcdc32` scoped Accept、质量 `295f59b3` 第二轮复审 scoped Accept/findings none，最终源码 SHA-256 `565d85486e25b691e4da297149993c7f76e92517669ce585cc0ee5a1e6933261`，selected9/class77/module151通过。上述历史两轮 Revise、父模块失败及修复经过继续保留，不能用新 Accept 抹除原失败。下一组为既有 T1 后台生命周期闸门，不据局部 Accept 勾选完整 T1；RPC backend interruption/并发压力建议由退出独立 Review 评估，不默认通过。
>
> 后台主要首组已获 spec `52ebf0ff`、质量 `5b3a88b8` scoped Accept：10入口control RED10 failures、0error/skip，GREEN class13/module164，父重跑 `/tmp/bolt-t1-background/coordinator-module.exit` exit0。源码SHA `4b6545098483b0b1dbc15140d76ef6d2dc59b6c40ec7f98a1ee2988de97740f6`，证据同目录。显式启动false、自然关闭后按需重建已局部证明；private有界warmup executor对象保留，不承诺全JVM/static worker归零。下一组验证同址错身份/重复失败、创建中停止及分阶段初始化失败反向收口；未完成前不勾完整后台/T1。
>
> 后台失败收口当前 spec `ec3ae65b` scoped Revise，修复候选待复审：原初始化失败断言未检查 suppressed，cleanupOrder/termination不能替代关闭成功。新增三阶段 `hasNoSuppressedExceptions()` 后，窄 GREEN 实际揭示 factory/manager 阶段尚未 startup 的 client 被 shutdown，产生 LifeCycleException；改为仅对实际已 started client 执行 shutdown，仍独立关闭未移交manager。原初始化 cause及失败日志保留。候选 init RED3 failures有效；修正后 init3/class18/module169全通过，证据 `/tmp/bolt-t1-background-failures/revised-*`，Background SHA `c3484ea04c07f710d91e0bbce6a91e1143b29cc8920af02222af0790617542d2`、support SHA `37d49fa206be9e18a96f2cc1fd51c7e81b35a875005c7c44e67e3ba253b2e79b`。父旧169tests成功不覆盖该 finding；当前待原spec复审及质量Review，不勾完整后台/T1。
>
> 后台失败收口历史质量修订检查点（已由下段复审结论关闭）：spec `ec3ae65b` 已复审 scoped Accept；质量 `428992c0` scoped Revise 指出 ownerExecutor.submit/get 在 try 外，初始化中断/等待失败可泄漏非daemon线程。候选将分配后提交/等待纳入同一try/catch清理；新增 `interruptedOwnerInitializationTerminatesExecutorAndRestoresFlag` / `skip-owner-init-cleanup`，真实阻塞owner任务与当前线程中断使Future.get抛InterruptedException，清理释放latch、真实await executor termination后恢复caller中断标志，原异常无suppressed。RED1 failure/0error，GREEN narrow4/class19/module170。证据 `/tmp/bolt-t1-background-failures/second-revised-*`，Background SHA `33a7ea1821df0ec4ccebe5cb2cee4b278a4b68dff3d207d92c7a8d4437f6dd83`，support SHA仍 `37d49fa206be9e18a96f2cc1fd51c7e81b35a875005c7c44e67e3ba253b2e79b`。当前待原质量复审，不改写历史spec Revise/隐藏清理失败或提前双Accept；通过后进行完整T1退出差距审核，不自动追加无依据旁支或标passing。
>
> 后台失败组修复后的最终局部结论：spec `ec3ae65b` scoped Accept、质量 `428992c0` 复审 scoped Accept/findings none，覆盖该组；上述两轮 Revise、隐藏 cleanup failure 和父旧输入证据仍保留。最终 Background SHA `33a7ea1821df0ec4ccebe5cb2cee4b278a4b68dff3d207d92c7a8d4437f6dd83`，support SHA `37d49fa206be9e18a96f2cc1fd51c7e81b35a875005c7c44e67e3ba253b2e79b`；初始化中断 RED skip-owner-init-cleanup 1 failure/0error，GREEN narrow4/class19/module170。后台首组13项，加同址错身份1、停止1、阶段初始化3、中断1，共19项，不改写首组13/164历史。
>
> - [x] 后台禁用入口矩阵、自然关闭按需重新认证、同址错身份三轮失败收敛、创建中停止迟到候选关闭、分阶段初始化失败及中断收口，已完成实际测试/有效反证与局部双 Review；private warmup executor 的可观察性、static共享worker边界及其他建议保留给完整退出审核，不声称全JVM线程归零。
> - [x] T1完整退出已完成（最终Accept见当前结论，以下保留原审核输入）：reviewer `5495396a` 只读核对批准设计、完整计划与六个测试文件，待有限 must-gaps/verdict；此前所有 scoped Accept 不替代本项。T2–T6 保持 not_started，不启动生产迁移。
>
> RPC 在途下一切片（独立计划 reviewer `2e7583c3-b272-40c` Approved/findings none；允许 T1 test-only，尚未实施/运行）：
>
> 仅修改 B+U+`plugin/bolt/BoltOutboundOriginsGateTest.java` 与同目录 `BoltSecurityGateTestSupport.java`；复用现有 Endpoint/gate/原生 remoting，不复制完整 remoting、future、timer、resolver 或 callback 实现。初始 `max-inflight-rpcs=64` 是 client 总额，与64创建 waiter 独立；每 Channel drain 计数只在编码前真实准入登记。以 `(Channel, command id, 原生 InvokeFuture 身份)` 关联并 release-once，不 remove/re-add 或替换竞争中的原生 map 项，不写共享 Channel deadline/target。caller 接受结果与 backend completion 分离：caller 等待超时/结果取消不释放；原生 response（含错误）、native timeout、send failure、Connection close 才释放。过期 Channel 拒绝新准入，既有 RPC 真正结束且 drain=0 后关闭；不是远端业务已停止的证明。
>
> | 精确方法 / control | 未来安全断言与原生证据 |
> |---|---|
> | `nativeCompletionHooksCoverControlledInvokeModes` / `callback-only-completion` | 参数 SYNC/FUTURE/CALLBACK 均实际调用原生 API；正常/错误 response、native timeout、send failure、close 分别核对原生 future/map、准入登记、唯一释放及原因。control 仅依赖业务 callback，使 sync/future 释放断言失败。另记录 hook acquisition/terminal 次数，不能只凭 callback 结果宣称全模式终态。 |
> | `callerAbandonmentKeepsNativeRpcPermit` / `release-on-caller-abandonment` | 参数 SYNC_WAIT_TIMEOUT/FUTURE_WAIT_TIMEOUT/CALLER_RESULT_CANCEL：真实响应暂停后 caller 放弃，native future 尚未结束时额度仍占用；释放原生响应或实际 native timeout 后恰好释放一次。sync wait timeout 若已进入原生终态则按真实终态释放，不能把原生结束伪称 caller-only 放弃；用较短 caller 外层接受预算单独证明 caller-only 情形。 |
> | `clientRpcLimitIsSharedAcrossChannels` / `per-channel-rpc-limit` | 同一 client 至少两真实认证 Channel 合计64个已放行双向 RPC，暂停响应；第65个 LOCAL，forwarded/encoded/handled 不增加。caller 放弃不腾额度，真实完成一个后新 RPC 可用。control 错按 Channel 各64而放行第65个。 |
> | `nativeTimeoutReleasesRpcPermitOnce` / `omit-native-timeout-release` | SYNC/FUTURE/CALLBACK 各实际原生 timeout=3000ms；有界等待具体 timeout 及原生登记移除，释放一次，迟到响应/close 不负数、不二次释放。显式推进 commandNanos 不能替代 timer。 |
> | `expiredChannelDrainsAfterNativeCompletion` / `close-expired-inflight` | 三模式真实请求已接受后推进证书截止，新请求拒绝；旧 Channel 保持 open，原生 response 或 timeout 完成后 closeFuture 成功且 !open、drain=0。control 在仍有在途时关连接，命中旧响应成功断言。 |
> | `sendFailureAndCloseReleaseRpcPermitOnce` / `duplicate-native-release` | 三模式在准入之后实际写失败及独立 Connection close 分支保留具体 cause；再触发迟到响应/timeout/close 的竞争，原生登记清空、额度不负数/不重放、候选及 Channel 关闭完成。control 对重复终止重复释放。 |
> | `nativeCompletionDoesNotDependOnUserCallbackExecutor` / `release-in-user-callback` | CALLBACK 的 queued executor、拒绝 executor、响应解码失败各真实触发；清理不依赖业务 callback 执行。原生终态/map 已结束即释放，未执行 callback 不能悬挂额度；control 将释放放业务 callback，命中额度断言。 |
>
> 完成 hook 探索矩阵：SYNC/FUTURE/CALLBACK 的 response/error、timeout、send failure、close 均须填写实际 hook/终态/释放证据。可探索 protected `createInvokeFuture` 与 `DefaultInvokeFuture.putResponse` 的薄扩展，但 sync wait timeout 存在 remove 后返回合成 timeout、未调用该 future.putResponse 的路径，须单独真实覆盖；future timeout 不执行 callback。不得只监听 write success、轮询 scanner 或依赖业务 callback executor；不通过手工 remove future 伪造完成。先做限定 inline callback witness 可以，但不能勾全模式或降低 T1 退出；公开 hook 无法可靠实现则 T1 blocked，先 Review，不复制原生实现掩盖缺口。
>
> - [x] 独立计划 reviewer `2e7583c3-b272-40c` 审阅本切片精确 diff `/tmp/bolt-rpc-plan.diff` 与全文，Approved/findings none，批准上述上限作用域、hook 矩阵及反证目标，允许修改测试；本次仅为设计允许的初始资源限额计划补充，不改变 D10/D11 信任边界或上线授权。
> - [x] 对表中每个 `C=BoltOutboundOriginsGateTest#<精确方法>`、`K=<control>` 顺序执行 `mvn -q -pl im-plugin/im-bolt -am -Dtest=$C -Dsurefire.failIfNoSpecifiedTests=false -Dbolt.gate.control=$K test`，核对指定断言 RED；移除 control 同命令 GREEN。参数各项确已执行，control 名称纳入合法校验；保留初次失败/清理 suppressed，不把超时挂死、编译或依赖错误当有效 RED。
> - [x] 顺序运行 `mvn -q -pl im-plugin/im-bolt -am -Dtest=BoltOutboundOriginsGateTest -Dsurefire.failIfNoSpecifiedTests=false test`、`mvn -q -pl im-plugin/im-bolt -am test`；实际分组证据为 `/tmp/bolt-t1-rpc-completion/`、`/tmp/bolt-t1-rpc-capacity-drain/`、`/tmp/bolt-t1-rpc-terminal-edges/` 的 before/diff/SHA、command/exit/reports，核对登记/额度/队列归零、closeFuture及executor termination。取得本组独立规格/质量 scoped Accept 后更新本计划，仍不自动通过完整 T1。
>
> 环境补证：父实际执行 `MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh architecture` exit0，日志 `/tmp/bolt-architecture-isolated.log`、退出码 `/tmp/bolt-architecture-isolated.exit`。依据批准私有制品设计的既有隔离安装，不改版本/依赖、不重新安装或发布；历史默认仓库解析失败仍保留，不改写为成功。此为当时输入下的架构证据，不是本次文档修改后的 fresh full；完整 full/实现质量验收仍未运行。
>
> 上述方法沿用本节 C/K 命令约定，`C=BoltOutboundOriginsGateTest#<准确方法>`、`K=<对应control>`；每项先有效 RED 再无 control GREEN，新增名称须实现合法 control 校验。原生来源 typed 探针继续保留，清单须区分实际原生行为、synthetic typed 与 raw COUNTEREVIDENCE；不得静默宣称全部原生来源已审计。
>
> - [x] D11 计划 reviewer `1a730f5e-903f-4cb` 全文及精确 diff Review Approved/findings none；输入为全文及 `/tmp/bolt-d11-plan-amendment.diff`，仅批准恢复 T1 test-only，不代表完整技术退出。
> - [x] 本次计划修订独立 reviewer `7822499f-8cf4-430` 已读取精确增量 `/tmp/bolt-d10-doc-amendment.diff`、全文及历史快照，Approved/findings none；允许首个 test-only 数组区间切片，不继承旧 Review 为技术退出。
> - [x] 先新增 `BoltPresentedChainGateTest.java` 并复用现有 support，按 D10 证明握手后数组事实、严格叶 URI、全成员区间、配置未发送排除及 TLS 1.2/1.3 完整/恢复行为。保留 `BoltValidatedPathGateTest.java` 历史 witness 及其 support 调用；不得将其旧 witness 通过或 peer-array 预期失败晋级为新闸门 GREEN。
> - [x] 核对实际 pipeline 顺序和全部业务写来源，记录到本任务证据；用 `keytool` 临时生成根/中间 CA/叶、交叉链和非选中附带链，用真实 Bolt 客户端/服务端握手。私钥/密码不写日志、不提交，失败也清理临时目录。
> - [x] 按表逐项新增安全断言、实现 test-only 对照，运行每个 RED 命令，核对失败是指定不变量而非编译、依赖、超时挂死或无测试；记录命令、失败方法和报告路径。
> - [x] 仅在测试源码中以公开工厂/manager/extended handler hook 写最小安全原型，运行每个无 control 的 GREEN 命令。会话事实或写屏障公开接口不可实现立即 blocked，不进入 T2。
> - [x] 执行 `mvn -q -pl im-plugin/im-bolt -am test`；检查所有新增类真实执行，并保留候选 closeFuture success、!open、executor termination、队列/池/在途归零及临时证书目录删除的断言证据。
> - [x] 独立 Reviewer 复核四组完整证据、写起点审计完整性、D10 数组来源/区间/恢复证据、D11 受控来源/原生路径和装配约束原型及禁用证明；在本任务记录 verdict/ID/精确输入。四组全部 GREEN 且无 Blocker/Major 才勾完并将 T1 passing、T2 active；否则 T1 blocked，PROGRESS 同步最小下一步。
>
> 历史 O2 部分证据（原 witness/反证及 27 tests 原样保留；不代表 D10 GREEN，不勾选上述完整退出步骤）：
>
> - [x] 新增 `BoltValidatedPathGateTest.java` 与 `BoltSecurityGateTestSupport.java`，只观察原生 mTLS session 的实际发送链及额外 certificate-only 信任材料；不声称多私钥 alias 选择或实际验证路径。root/intermediate 均是 keystore 信任项，实际所选 anchor 未知。
> - [x] `mvn -q -pl im-plugin/im-bolt -am -Dtest=BoltValidatedPathGateTest -Dsurefire.failIfNoSpecifiedTests=false -Dbolt.gate.control=peer-array test`：预期反证失败，1 failure、0 error/skip；完整 peer 数组包含 root，不能直接充当 O2 的非锚路径。日志 `/tmp/bolt-path-gate-red.log`，报告 `/tmp/bolt-path-gate-red-reports/`。这不证明公开 hook 不存在。
> - [x] 去掉 control 的同一 witness 命令通过，1 test、0 failure/error/skip；日志 `/tmp/bolt-path-gate-witness.log`，报告 `/tmp/bolt-path-gate-witness-reports/`。witness 通过不是路径闸门 GREEN。
> - [x] `mvn -q -pl im-plugin/im-bolt -am test` 通过，协调者独立重跑成功，所属模块报告合计 27 tests、0 failure/error/skip；日志 `/tmp/bolt-path-coordinator-tests.log`。资源断言覆盖客户端/服务端关闭、跟踪 Channel closeFuture 成功及 !open、keytool 有界结束、临时证书目录删除。
> - [x] `./scripts/verify.sh architecture` 已尝试，私有 JRaft 制品解析失败，日志 `/tmp/bolt-path-architecture.log`；先归因依赖前提，不继续更宽 full。`readiness`、`drift`、`clean` 及 `cleanup` 窄治理检查已执行通过，不等于架构或实现验收。
>
> D10 本轮部分证据（不是完整数组闸门，不勾选完整退出步骤）：
>
> - [x] 新增 `BoltPresentedChainGateTest.java`，复用 `BoltSecurityGateTestSupport.java`；真实原生 mTLS 握手后读取两端非空 X.509 数组。中间 CA/呈现根参与有效区间，配置但未发送的证书排除。纯计算以显式 `Instant` 验证两端等号、缺失/类型错误及空/反向区间；不建立合成认证事实。
> - [x] 四个有效反证：`leaf-only` 选择 `allPresentedCertificatesConstrainWindow`、`omit-presented-root` 选择 `presentedRootConstrainsWindow`、`inclusive-not-after`/`exclusive-not-before` 选择 `windowBoundsUseExplicitInstant`。每项执行 `mvn -q -pl im-plugin/im-bolt -am -Dtest=BoltPresentedChainGateTest#<上述方法> -Dsurefire.failIfNoSpecifiedTests=false -Dbolt.gate.control=<对应control> test`，分别在指定安全断言失败，退出1；非法 control 也在合法名称断言失败。精确命令、退出码、日志及报告保存在 `/tmp/bolt-d10-slice/final-red/`，不是编译或依赖失败。
> - [x] `mvn -q -pl im-plugin/im-bolt -am -Dtest=BoltPresentedChainGateTest -Dsurefire.failIfNoSpecifiedTests=false test`：18 tests、0 failure/error/skip。历史 witness 单独回归通过；`mvn -q -pl im-plugin/im-bolt -am test`：45 tests、0 failure/error/skip，协调者独立重跑成功（`/tmp/bolt-d10-coordinator-module.log`）。原始证据含 command/exit/reports 在 `/tmp/bolt-d10-slice/final-green.*`、`final-module.*` 及其对应 reports 目录。
> - [x] spec reviewer `4e45587b-b698-4f3` Accept/findings none，仅限上述 partial slice；质量 reviewer `361e3c7f-7a3a-4ca` Accept/findings none，同样仅限 partial slice。临时材料/进程及 Channel 清理断言通过，fixture 目录扫描无剩余。本轮没有生产/POM 变更。
> - [x] 严格叶 URI 切片：`nativeTrustAndStrictLeafUriRejectBeforeForwarding` 的 10 个真实场景覆盖正确 URI 与其他命名空间共存、错/缺/多匹配 URI、错误 CA、明文及本机/对端过期和未生效。URI 负例实际 `invoke(Connection)` 探测，forwarded/encoded/handled 均为0，正例均为1。本机无效证书明确为 native client 握手后 D10 空区间拒绝，对端无效证书要求具体 Certificate cause；不混同来源。
> - [x] 命令 `mvn -q -pl im-plugin/im-bolt -am '-Dtest=BoltPresentedChainGateTest#nativeTrustAndStrictLeafUriRejectBeforeForwarding' -Dsurefire.failIfNoSpecifiedTests=false -Dbolt.gate.control=skip-leaf-uri test` 退出1，10 tests 中恰有3个URI断言失败；去掉 control 的类级命令28 tests通过，模块命令55 tests通过。命令/exit/日志/报告在 `/tmp/bolt-t1-uri/`；协调者独立重跑模块退出0（`coordinator-module.exit`）。spec reviewer `b946bd24-b2eb-467`、质量 reviewer `d8671f18-297e-417` 均 Accept，仅限局部切片。
> - [x] 未使用但实际呈现成员：仅测试材料经公开 KeyStore API 转为 JKS，原生握手成功后两端 session 数组均包含无关短寿命 CA；其截止参与 D10。`omit-unused-presented` RED 退出1，准确失败于截止断言；无 control GREEN 1 test通过，不把配置或合成数组当认证事实。
> - [x] TLS 1.2/1.3 各两个完整握手 Channel 重新获取会话事实，事实/window 对象不复用且 acquisition=2；`reuse-channel-facts` RED 退出1，两协议均准确失败于旧事实复用断言。resume-only（握手前 `setEnableSessionCreation(false)`）两协议均以原生 `SSLHandshakeException` 及明确不可恢复原因失败关闭，候选无事实/未交付、业务计数0、Channel关闭完成；不承诺恢复可用性，不以sessionID判断恢复。
> - [x] 恢复/呈现成员精确命令、退出码、报告、before快照及diff在 `/tmp/bolt-t1-recovery/`。窄验证运行 `mvn -q -pl im-plugin/im-bolt -am '-Dtest=BoltPresentedChainGateTest#eachTlsChannelReacquiresSessionFacts+resumeOnlyChannelFailsClosedOrReacquiresFacts' -Dsurefire.failIfNoSpecifiedTests=false test` 和 `-Dtest=BoltPresentedChainGateTest#unusedPresentedCertificateConstrainsWindow`；RED分别追加 `reuse-channel-facts`（仅each方法）及 `omit-unused-presented` control。最终 `no-console-protocol` 4 tests通过、`no-console-module` 模块60 tests通过，均0 failure/error/skip。spec reviewer `11d80cd4-352c-44b`、质量 reviewer `c9b48cf3-e91a-450` scoped Accept，仅限本切片。
> - [x] 保留首次standalone入口绕过原型工厂的失败及 `final-module` 关闭后pipeline移除SslHandler导致的两项NPE；改为经过认证factory、保存同Channel原生handler引用后，先窄验证再模块通过。曾有控制台诊断输出，已按现有规范删除并以具体异常断言替代；历史日志不删除。初次readiness被协调者并发文档更新使指纹失效，暂停写入后 `readiness-frozen.log` 通过，不将该失败记作环境故障。
> - [x] D11 受控发送、共享创建/RPC预算资源及后台所列局部切片已执行并获 scoped 双 Review；完整退出独立复核仍 pending；raw 旁路继续作为旧 all-origin COUNTEREVIDENCE，历史完整 T1 Block 不改写。D11 设计及计划已批准，仅恢复 test-only，不能以局部 Accept 进入 T2。
>
> 写起点本轮证据（局部witness通过不等于安全GREEN）：
>
> - [x] 新增 `BoltOutboundOriginsGateTest.java`，复用support实际调用的 `NativeMaterial/outboundMaterial()`。真实native TLS下覆盖 String/Url/Connection × sync/future/callback/oneway 12条公开API，逐命令 `InvokeContext` 不可变目标/单调预算，不随wire发送；缺本机上下文拒绝。8个实际context业务探针明确是synthetic，不假称原生handler发送业务；Heartbeat正例不证明原生idle触发/ACK，CONNECT仍未覆盖。
> - [x] `mvn -q -pl im-plugin/im-bolt -am '-Dtest=BoltOutboundOriginsGateTest#everyBusinessOriginIsRejectedBeforeEncoding' -Dsurefire.failIfNoSpecifiedTests=false -Dbolt.gate.control=tail-only test`：exit1，8个case中3个在编码观察计数0/1断言失败；同方式选择 `directConnectionWithoutControlledContextCannotSend` + `missing-command-context`：exit1，指定编码计数断言失败。均不是TLS/依赖/编译失败。
> - [x] `mvn -q -pl im-plugin/im-bolt -am -Dtest=BoltOutboundOriginsGateTest -Dsurefire.failIfNoSpecifiedTests=false test`：23 tests通过；模块命令83 tests通过，协调者独立重跑exit0。证据 `/tmp/bolt-t1-origins/` 含RESULT、精确命令/exit/reports、before快照和support增量；保留早期编译/计数修订失败，不改写为有效RED。
> - [x] `explicitPreencodedContextWriteBypassesTypedGateWitness` 通过表示反例成立：原生命令编码器预编码后从encoder context写入，promise成功、远端handled=1、typed attempted/forwarded=0、编码输出观察=1、TLS前明文和HEAD密文字节数均>0。不是原生handler自动生成非法业务的证明，也不是所有公开hook方案不可能的证明；当前 typed 原型不能满足当时批准的 all-origin 要求；D11 改变支持边界，不把该反例变为运行期拒绝或 GREEN。
> - [x] 独立设计边界审查 `31433f1e-b3e4-46d`：Block将双typed guard视为all-origin通过，建议受控协议来源/禁用任意预编码扩展边界，必须用户批准并独立设计Review。spec reviewer `59cfe70f-c7ac-4de`：witness scoped Accept、完整T1 Block。质量 reviewer `020c8646-f181-453`：witness scoped Accept、完整T1 Block。`encoded` 是穿过encoder输出边界的ByteBuf写入计数，不是encoder调用数；避免误读。不删除反例或把原始字节路径静默排除。
>
> 完成条件：不是旧六项原型通过，也不是四个类存在；必须是以上每个反证有效、D10 数组闸门、D11 受控发送及实际原生来源审计、其余安全闸门 GREEN、资源关闭可观测及独立退出复核。后台若改为启用方案，须先修改首版配置选择并复核：每次保留目标 Url/key、重新认证、同址错证书拒绝、重复失败队列有界及停止无残留均有真实测试；不能只删除禁用断言。
>
>
>
> - [x] 新增目标/前缀/无歧义 key、同址异身份、认证前不交付、池注入/stale 连接拒绝、预算优先级和四分类行为测试。原目标组因缺 BoltTarget 得到 testCompile RED（不是安全行为 RED），命令为 `mvn -q -pl im-plugin/im-bolt -am -Dtest=BoltUnifiedInvokeTest -Dsurefire.failIfNoSpecifiedTests=false test`（`/tmp/bolt-t2-target-policy/red.command`）；修订 GREEN 实际为 `mvn -q -pl im-plugin/im-bolt -am -Dtest=BoltUnifiedInvokeTest,ImBoltAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`（同目录 `revised-green-selected.command`）。真正修订行为 RED 为11 tests/4 failures，命中 LOCAL 分类与密码泄漏（同目录 RESULT.md 与 revised-red 日志）；不扩大为全部安全行为反证。后续 Lifecycle/受控连接/预算/资源行为由各组独立证据支持，不表示原三类指定 RED→GREEN 序列已执行。
> - [x] 分组反证及同三类集合 GREEN 已接受：`/tmp/bolt-t2-controlled/drain-revised-final-focused.command` 实际为 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltConnectionLifecycleTest,BoltUnifiedInvokeTest,ImBoltAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`，focused22；随后 `drain-revised-final-module.command` 模块194，均 exit0，e906/fb9 双 Accept。原 selector 字面量及指定历史 RED→GREEN 序列未执行，不追补历史；不是三个类从未一起运行。
>
> 原步骤历史（不补造执行；组合、顺序及当时状态不代表当前义务）：原三类合并聚焦命令义务尚未有对应运行证据：`mvn -q -pl im-plugin/im-bolt -am -Dtest=BoltUnifiedInvokeTest,BoltConnectionLifecycleTest,ImBoltAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`，原要求先 RED（新契约/能力缺失）再 GREEN。既有分组 RED/GREEN 与 scoped exit 仅证明相应行为及阶段退出，不自动免除此命令义务；是否接受分组证据替代，须另经独立合同 Review 明确批准，本组不自行批准等价。
> - [x] 从 T1 经验证 hook 提取最小生产实现；每次独立内部 Url 绑定地址+身份+固定策略，不修改 Bolt 缓存 Url。factory 拥有未交付候选，manager 拥有池及过期排空，Invoker 不关闭共享连接；按初始限额实现有界创建/等待与实际完成额度，不以首 caller 预算替代共享预算。
> - [x] gate 使用逐命令本机上下文，检查身份、墙钟链有效区间及剩余单调预算；以真实放行为在途登记点，到期不再准入但不提前关闭在途，结果解码后超预算拒绝。中断恢复标志；保留 SSL/Certificate cause AUTHENTICATION、预算 LOCAL 优先级、受限 transport 集合和 PROTOCOL，不自动重放。
> - [x] 受控 `BoltAuthenticatedClient` 提供可检验装配能力；普通外部 RpcClient/custom invoker 启动拒绝，受支持外部 Bean 正例可启动；初始化中途失败反向关闭自有资源、不双关外部资源。在 `ImBoltAutoConfigurationTest.java` 中增加 `rejectsUnsupportedClientOrExtensionConfiguration`（覆盖外部客户端、扩展、codec/transport 替换）、`startsWithSafeDefaultControlledAssembly` 和受支持外部 Bean 正例，负例须启动失败并保留原因，安全默认须启动成功。能力最早在 T2 生产引入时同步完成这些正负测试，不延至 T4/T5，也不只测 TLS enabled；不引入通用能力框架，不声称启动检查永久隔离有权限的运行期修改。
> - [x] 原目标组修订 selected 两类 GREEN 后，实际运行 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test`（`/tmp/bolt-t2-target-policy/revised-final-module.command`，selected11/module183）；后续 Lifecycle 与完整 T2 分组回归、最终 selected4/module237 及 f4ef3381/3729b260 双 Accept 见下文原证据。暂存旧生产入口仅供 T3 同一施工批次迁移，不发布、不视为完整安全交付；T3 删除后再作最终门禁。
> - [x] 已接受实际分组 focused→模块回归与 T2 最终补证：controlled 的 drain-revised-final-focused/module；`/tmp/bolt-t2-production-budget/revised-final-focused.command`（Unified+Lifecycle）及 revised-final-module.command；`/tmp/bolt-t2-production-rpc/revised-focused.command`（Lifecycle+Unified）及 revised-module.command；`/tmp/bolt-t2-production-resources/revised-selected.command`（ProductionResources）及 revised-module.command；`/tmp/bolt-t2-production-background/final-selected.command`（ProductionBackground）及 final-module.command 均保留实际命令/exit/log/reports。最终 `/tmp/bolt-t2-exit-final/final-selected.command` 两方法展开4 cases（非4 classes）及 final-module.command 模块237全零，f4ef3381/3729b260 接受完整 T2。原完整顺序未执行，不宣称重现；T1 不退化由实际模块回归支持，最终 fresh 集成归 T6。
>
> 原步骤历史（不补造执行；组合、顺序及当时状态不代表当前义务）：原「重跑前述三类合并聚焦命令至 GREEN，再运行 `mvn -q -pl im-plugin/im-bolt -am test`」顺序义务未由上述实际分组命令证明。保留为未完成的原命令合同事项，是否由既有分组证据替代待独立合同 Review；不为修正文档补跑 Maven，也不把 scoped exit 解释为原命令已运行。
>
> 完成条件：T1 回归不退化，生产实现匹配四分类/有效期/生命周期契约，外部 Bean 无安全假设。T2 不授权上线。
>
> T2 编译合同同步（父批准提前同步；实际检索为12文件，非预估13；不改变fixture业务预期，生产迁移仍T3）：SPI新增typed abstract invoke，BoltRpcClient真实委托，以下现有测试实现同步签名并保留完整target，不增加生产default兼容壳。
>
> - `im-gateway/im-ws-gateway/im-ws-gateway-sdk/src/test/java/com/co/kc/imchat/gateway/ws/sdk/client/GatewayClientTest.java`
> - `im-gateway/im-ws-gateway/im-ws-gateway-server/src/test/java/com/co/kc/imchat/gateway/ws/ImWsGatewayApplicationTest.java`
> - `im-gateway/im-ws-gateway/im-ws-gateway-server/src/test/java/com/co/kc/imchat/gateway/ws/support/BrokerClientTestSupport.java`
> - `im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/application/frame/FrameDispatchAppServiceTest.java`
> - `im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/interfaces/handler/FrameProcessHandlerTest.java`
> - `im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/interfaces/handler/BrokerBoltHandlerTest.java`
> - `im-broker/im-broker-sdk/src/test/java/com/co/kc/imchat/broker/sdk/client/BrokerClientRoutingTest.java`
> - `im-broker/im-broker-sdk/src/test/java/com/co/kc/imchat/broker/sdk/client/BrokerClientTest.java`
> - `im-service/im-message/im-message-server/src/test/java/com/co/kc/imchat/service/message/support/TestCacheConfiguration.java`
> - `im-service/im-message/im-message-server/src/test/java/com/co/kc/imchat/service/message/support/BrokerClientTestSupport.java`
> - `im-service/im-message/im-message-server/src/test/java/com/co/kc/imchat/service/message/infrastructure/notification/BrokerMessageNotifierTest.java`
> - `im-service/im-message/im-message-server/src/test/java/com/co/kc/imchat/service/message/infrastructure/notification/MessagePushServiceTest.java`
>
> 构造调用者额外检索：`im-test/im-routing-ha/src/test/java/com/co/kc/imchat/routingha/AutomaticFailureTlsHaTest.java`、`im-plugin/im-gossip/src/main/java/com/co/kc/imchat/plugin/gossip/client/GossipPeerClient.java`。本组保留仍在使用的旧正式构造入口，最终T3删除；不新增仅测试构造器。
>
> T2首组审查：spec `62b6fd9d` scoped Accept，质量 `216b6abf` Revise三项必须修复。typed入口策略错误改为BoltInvocationException.LOCAL并保留IllegalArgumentException cause，startup配置异常仍保留原始类型；SPI改正常import并说明固定策略/精确身份/过渡边界，构造器注明快照及外部客户端生命周期；启动负例核对具体root cause与keystore原因。补TLS密码toString排除及断言。行为RED11tests/4failures（原入口异常类型及secret泄漏）；修复后selected11/module183通过，证据 `/tmp/bolt-t2-target-policy/revised-*`。当前候选待原质量复审，旧181tests事实保留，不提前T2passing；下一完整受控能力组仍须同步startup正负例。
>
> T2首组修复后质量 `216b6abf` scoped Accept（三must关闭），与spec `62b6fd9d` scoped Accept共同通过首组；selected11/module183为真实最终证据，旧Revise/181记录保留。T2仍唯一active；下一组真实BoltAuthenticatedClient/factory/manager/gate/context/certificate生产能力首次引入，同步正负startup验证，不以typed前缀开关建立不安全fallback。
>
> T2真实controlled transport首组当前 **review `e7dc46c2` Block，修复候选待复审**：acquire过期未退休代际/排空、shutdown与admit非共锁及外部controlled未初始化/已关闭仍通过启动三项。候选在acquire发现stale后身份安全移除所见原生task并drain；native inactive remove不删除替代代际；零inflight排空无强引用残留，closefuture清理draining。closed状态与admission共accounting锁迁移，native关闭在锁外、双shutdown幂等；启动核验实际ownedStarted/native manager运行装配，外部Bean不被validator擅自初始化/关闭。修前expiry0/1inflight+startup RED3fail、shutdown检查删除mutant RED1fail有效；修后focused18/module190通过，精确 `/tmp/bolt-t2-controlled/revised-*` before/diff/SHA/commands/exits/reports。旧14/186成功不覆盖Block，保留初次race probe重复promise及nativeclose先终止future的开发失败归因。T2仍active，待原spec复审后quality，不继续生产范围扩张。
>
> T2生产最新review `0440da79` **Block待复审**：原e7三finding与drainrefs修复已确认；新增init/shutdown所有权窗口。候选将init进入/关闭请求/成功移交放同accounting协议，native init/shutdown锁外；关闭遇初始化由init owner回滚，外部关闭有界等待settled，owner不自等，cleanup失败保留cause并finally通知。真实native option getter spy暂停在manager分配后/native client startup前，不冒称复现managernull精确点；代码协议覆盖分配前窗口。修前RED1failure/0error，修后focused19/module191通过，证据 `/tmp/bolt-t2-controlled/second-revised-*`。shutdown旧mutant仅证明LOCAL优先vs缺nativefuture SecurityException，不能声称业务到达handler。当前待044原review复审与quality，T2仍active。
>
> T2 `0440da79` 再次Block保留：此前init成功发布与finally清除initializing之间有关闭责任空窗。最新最小候选在同一accounting临界区同时ownedStarted=true、initializing=false及owner清除，settled通知锁外；shutdown见commit自己执行native close，见初始化中由owner rollback，native动作仍锁外。无生产测试hook。前commit确定性native getter暂停回归保留；新成功commit-close witness在init Future完成后执行真实关闭，仅可达边界witness，不冒称精确插入旧窗口RED。组合命令240s超时导致一次module无exit，保留不计成功；改去忙loop后分别最终focused20/module192真实exit0。精确 `/tmp/bolt-t2-controlled/third-revised-*`，当前仍044 Block候选待原review复审，不T2passing。
>
> T2当前审查：生命周期spec `e906` Accept，整组质量 `0cb4eb6b` **Block待复审**。新drain窗口候选在锁内无条件登记draining，最后完成只决定close不提前remove；首次drain登记closeFuture监听且回调清引用，native close仍锁外。两窗口真实native pending请求/延迟close生产回归RED2fail/0error，准确说明旧早期probe在native onClose之后只证Security差异，不计泄露；有效probe移至native onClose之前保持future登记，RED命中promise成功cause=null，仅直接证明准入/transport forward，不宣称远端业务执行。修后focused22/module194通过，证据 `/tmp/bolt-t2-controlled/drain-revised-*`。生产imports及过时Invoker注释收敛；设计与Harness登记持续拒绝状态约定及最窄测试/Review-only自动化条件。T2仍active，无完整生产矩阵/full通过声明。
>
> T2 owned transport整组最终局部Review：spec `e906` Accept、独立质量 `fb9c845c` Accept/findings none，关闭drainBlock；finalfocused22/module194通过，证据 `/tmp/bolt-t2-controlled/drain-revised-*`，旧e7/044/0cb Block及失败保持。T2仍active，完整生产资源/终态矩阵未验收。
>
> 剩余T2有限两逻辑组（不再扩T1）：
> 1. 当前生产预算/共享创建组：仅现有 `BoltUnifiedInvokeTest`、`BoltConnectionLifecycleTest`、必要 `BoltAuthenticatedFailureTest`/`BoltAuthenticatedTlsTest`/AutoConfig及现有生产Invoker/manager/factory/context修正。真实typed路径全阶段预算、四分类、短waiter不取消共享任务/独立创建截止、配置上限边界/饱和LOCAL。证据 `/tmp/bolt-t2-production-budget/`，有效行为RED、focused及module最终各一次，双Review后再下一组。
> 2. 生产终态/生命周期组：公开typed同步能力的正常/错误/timeout/sendfailure/close、实际准入额度与到期drain、后台拒绝/自然重建/停止候选收口；原生future/callback hooks按实际受控路径覆盖，不把未暴露的任意业务callback作为已支持公开API。此组完成后T2整体复核，不机械增加旁支。
>
> T2生产预算/共享创建组：spec `ed79ec7a` scoped Accept，质量 `4d78c1cb` Revise指出测试nanos冻结在init之前可能让真实gate提前拒绝，DECODE未真实进入解码。候选仅改两个测试为System.nanoTime()+AtomicLong offset，同源时钟继续运行，序列化/解码hook和共享等待推进offset，不新增生产Clock/seam；SERIALIZATION_FAILURE加强原始IllegalArgumentException及稳定原因断言。revised narrow5/focused26/module205各真实exit0；精确 `/tmp/bolt-t2-production-budget/revised-*`。旧205成功不覆盖finding，当前待原quality复审，生产LOCALstage修复不变，T2仍active。
>
> T2生产预算/shared组最终spec `ed79ec7a`、quality `4d78c1cb` scoped Accept/findings none，旧时源Revise保留；最终narrow5/focused26/module205，Unified SHA `3950e0852722c9d1952417b7346876e5c1dc27309d9956db71626196a828172d`、Lifecycle SHA `db6bc5d2fd821b58964e13d2f0ce5aab8b90073796bfb163b89ba8991a0ac08f`。T2仍active。
>
> 剩余生产验收按有限两组执行：当前RPC容量/终态组（`BoltConnectionLifecycleTest`/`BoltUnifiedInvokeTest`及必要现有client/gate修正），证据 `/tmp/bolt-t2-production-rpc/`；之后初始创建资源/后台收口组（同测试范围及既有factory/manager必要修正）。RPC组覆盖真实typed同步公开入口64合计准入、caller放弃不释放、正常/错误/timeout/sendfailure/close；原生future/callback只可使用真实受控逐命令上下文，不造无caller的测试逃生生产API，若尚缺覆盖须精确登记。资源组实际验证初值2+16/64pool/64waiter和后台自然重建/拒绝/失败收口，不把前组配置1/1或T1原型当默认饱和证明。每组独立双Review，T2完整复核前不passing。
>
> T2生产RPC组当前冻结待Review，任务未完成：容量mutant有效RED1failure、移除nativeCompletion释放mutant有效RED15tests/10failures/0error；旧10errors反证无效留样。最终focused45tests exit0；module单命令300s工具超时无exit，部分XML不能当完整本轮报告，module未通过验收。证据 `/tmp/bolt-t2-production-rpc/` before/diff/SHA/commands/exits与partial reports。Lifecycle SHA `85b602a6f7e23e709af2950c1e1b1e31d619ca29d1a058960a4c833d18b62d64`。监督循环50/50到限，不开始资源后台下一组或T3；等待父spec/quality Review及监督续期。恢复先归因材料成本与运行预算，不盲重跑；剩余生产资源/后台、T2整体复核及T3–T6/full未完成。
>
> T2生产RPC组续期修复候选：监督Loop#2已由用户续期（每5分钟、50轮），取代旧“到限等待续期”的当前恢复状态，但保留旧执行事实。父 `renewed-module.log/.exit` 实际exit0仅覆盖修前冻结源码；旧300s超时和partial报告不改写。spec `d485d1df` **Revise待复审**：cancelled Future及client event-loop barrier均不能保证nativeCompletion/worker完成。仅修改 `BoltConnectionLifecycleTest`，实际caller在finally为首worker发布独立latch；第一响应后bounded await该latch才提交replacement，cancel(false)仍不影响backend，不sleep/重试/加timeout。附加DECODE仅实际腐败分支计数并断言1；无生产/API变更。未声称确定性复现旧race RED，以有限happens-before协议与既有容量反证交独立Review。最终selected4/focused45/module224均零failure/error/skip、各真实exit0，模块独立预算900s、实际约398s；报告文件修改时间全部落在本轮module起止区间（XML无timestamp属性），精确 `/tmp/bolt-t2-production-rpc/revised-*`。Lifecycle SHA `859a6fcfee19e389fee66df9498aa7e871f305497a4322b9abf7663cb23d26a9`。当前仅交父协调原spec复审及后续quality，不开始资源组；T2仍active、T3–T6 not_started、full未运行，不提前passing。
>
> T2 RPC组最终spec `d24f1bc2`、quality `9461d62f` 均scoped Accept/findings none，关闭原 `d485d1df` Revise；selected4/focused45/module224各真实exit0，module实际398s。旧300s超时、修前renewed-module以及历史Review全部保留。T2仍active，不自动passing。
>
> T2剩余范围固定为两组，各自spec/quality双Review后推进；本批先资源组，不同时扩后台：
>
> - [x] 资源组仅新增 `im-plugin/im-bolt/src/test/java/com/co/kc/imchat/plugin/bolt/BoltProductionResourcesTest.java`（避免继续扩Lifecycle大类，复用同包既有policy/server与一份类级真实临时证书；各方法独立endpoint/client清理）。方法清单：`defaultCreationExecutorAdmitsTwoActiveAndSixteenQueuedTargets`（真实2+16饱和，19 LOCAL，释放后所有18目标完成、每池1连接）；`defaultTargetPoolLimitRetainsSixtyFourAuthenticatedTargets`（一个client、64独立port同身份真实TLS目标，65 LOCAL且旧64可复用）；`defaultPendingCallersStayChargedUntilCancelledWorkerActuallyCompletes`（同一真实共享TLS任务，64个pending接受后即使caller timeout/cancel(false)仍拒65，实际worker finally完成后恢复）；`failedSharedCreationClosesCandidatesAndPermitsLaterDemand`（真实身份错误共享失败、候选关闭与池清空、同址合法新需求成功）。不造policy key、不改生产Clock/API；若发现生产bug，仅现有client/manager/factory必要修复并先真实RED。
> - [x] 资源组先审新增方法，临时移除生产manager对应上限或提前返还pending的变异反证，保存before/精确diff/SHA/命令/exit/XML。窄命令 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltProductionResourcesTest -Dsurefire.failIfNoSpecifiedTests=false test`；最终再独立 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test`，每条预算900s、不并发。证据 `/tmp/bolt-t2-production-resources/`；得到独立双Review前不开始后台组。
> - [x] 后台组后续在现有Lifecycle/AutoConfig/AuthTLS及必要现有生产client/manager/factory收口：原生选项构造前固定false且系统属性true仍不能覆盖；heal/management/bare manager/factory/connWarmup/multi拒绝；真实remote child close自然退休后typed重新认证，同址wrongID实服务重建连续3次AUTHENTICATION、池0；queued timeout/stopped late candidate仅最窄缺口，复用已有init失败/关闭实际回归及旧代际不能误删替代证明，不重复T1原型。准确方法清单须在该组开始前另冻结，执行独立双Review；缺公共线程观测不新增test-only生产getter，不关闭static JVM workers。
>
> T2资源组当前候选待双Review：仅新增计划内 `BoltProductionResourcesTest.java` 四方法，类级真实证书共享、每方法独立endpoint清理。实际默认2+16创建饱和、64真实TLS独立port池各1connection及65LOCAL、64pending（含短超时/cancel(false)而backend未完）拒65并在独立worker finally后恢复、真实共享wrongID失败候选关闭/池清空后合法新需求恢复。短暂源级生产manager移除三上限反证为4tests/3failures/0errors，已恢复原SHA，不留生产开关。初次hold漏发readComplete导致1failure/2errors仅属测试实现失败，修后probe4通过，不伪称生产RED。冻结最终selected4/module228各真实exit0、零failure/error/skip；module单独900s预算、实际421s，同轮13报告mtime齐，证据 `/tmp/bolt-t2-production-resources/` before/精确diff/SHA/commands/exits/reports。测试SHA `b58b486b9d8dc442cf102186433a76001a8795e642efa953a3c985f1f193346e`。资源组未获Accept、后台组未开始，T2仍active/full未运行；本候选不自动完成T2。
>
> T2资源组spec `ae2f2ec5` scoped Accept，quality `60f5d4a9` **Revise待复审**，原228成功不覆盖清理finding。候选仅 `BoltProductionResourcesTest.java`：四方法finally改JUnit assertAll让release/client shutdown/worker shutdown与termination/server stop/各hold关闭分别尝试；64 client channels独立close+closeFuture await及!open，各hold内部所有child同样聚合；startServer在成功返回前任何start false/throw由本地owner stop+child清理，原失败保留、清理异常suppressed。无生产/POM/新框架/额外测试API；直接使用现有JUnit聚合机制，不新增框架自测。最终revised selected4/module228各真实exit0且零failure/error/skip，模块独立900s预算一次执行；精确before/diff/SHA/commands/exits/reports及报告mtime见 `/tmp/bolt-t2-production-resources/revised-*`。当前待原quality复审，不进入后台组，T2仍active/full未运行。
>
> T2资源组最终spec `ae2f2ec5`、quality `60f5d4a9` 复审scoped Accept/findings none，两must关闭；revised selected4/module228实际exit0（415s），SHA `9a7e70e1e533e731b9c765bcb0e9fb3ddc625fea4b19f5c8d0dcbc55dcdccdca`。旧Revise/原228成功证据保留，T2仍active。
>
> T2最后生产后台/关闭组冻结范围（开始代码前登记）：仅新增 `im-plugin/im-bolt/src/test/java/com/co/kc/imchat/plugin/bolt/BoltProductionBackgroundTest.java`，必要bugfix仅 `core/BoltAuthenticatedClient.java`、`BoltAuthenticatedConnectionManager.java`、`BoltAuthenticatedConnectionFactory.java`，不改T1/POM。复用同包既有真实证书support，一份类级材料及wrongID材料；不新增框架或生产观测getter。
>
> - [x] `nativeBackgroundOptionsAndPublicCreationEntrypointsStayClosed`：系统属性true与普通RpcClient配置正对照，受控构造前固定false/init后仍false；公开enable、getAndCreate、management/heal、warmup/multi Url、bare manager与factory overload拒绝且server零channel；正常typed按需真实TLS成功。只审计实际受控公开入口，不反射全JVM对象。
> - [x] `remoteChildCloseRetiresNativePoolAndNextDemandReauthenticates`：远端child.close，等待真实native inactive事件后pool移除；下一typed新channel/新handshake成功，无手工remove。
> - [x] `sameAddressWrongIdentityRebuildsFailClosedThenValidServiceRecovers`：原合法目标close，原port换真实wrongID服务，连续3次实际AUTHENTICATION/SSL根因、每候选closeFuture完成/池空/业务0；同址再恢复合法服务成功。
> - [x] `shutdownSettlesRunningSharedAndQueuedCreationWithoutLatePublication`：真实TLS hold中共享caller和queued目标；native任务map公开Future证明都settled，shutdown准入关闭后释放bytes，所有caller LOCAL、所有候选closeFuture完成、queue目标无connect/pool空；仅观察本client实际creation thread集合并join证明其退出，不关闭static JVM worker。若发现未settle真实bug，先RED再最小修复。
> - [x] `queuedCreationDeadlineExpiresWithoutConnecting`：使用原生public task Future及真实creation截止，队列等待耗尽后无新候选；不sleep/重试/假Clock，既有创建截止用例复用，不倍增压力。
> - [x] 复用既有 `BoltConnectionLifecycleTest#closeDuringNativeInitializationWaitsForOwnerRollback+successfulInitializationCommitHandsNativeCloseToShutdown` 和AutoConfig关闭外部bean回归，准确注明真实init关闭窗口，不额外伪造factory失败证明。RED原始真实失败或短暂源级mutant均记录且恢复SHA；selected命令 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltProductionBackgroundTest -Dsurefire.failIfNoSpecifiedTests=false test`；最终独立 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test`，各900s预算、无并发，证据 `/tmp/bolt-t2-production-background/` before/diff/SHA/commands/exits/reports。双Review后请求有限T2退出Review才解锁T3，不自动passing。
>
> 后台组实际开发发现与有限修订：最初CONNECT listener未进入受控manager的listener（RpcClient自有listener并非该manager listener），原生事件分发又不等于creation worker，不能冒称该探针阻塞创建线程。queued截止最终使用privileged test-only spy包装实际生产factory，原方法返回真实已认证候选后在creation worker有界hold，不伪造binding/facts、不支持业务替换；记录factory真实调用数1、queued具体TimeoutException原因及候选closeFuture/!open。初始compile缺原生isRun方法、helper double-stop与CONNECT等待失败全部保留为开发失败，不算生产RED。关闭准入实际RED1test/1failure/0error显示typed postshutdown误归PROTOCOL；最小生产修复仅 `BoltAuthenticatedClient.acquire` 明确LOCAL并保留原IllegalStateException cause，不扩大所有IllegalStateException分类。五方法scope不增加，T2仍active。
>
> T2后台/关闭组冻结候选待双Review：五方法全部完成（真实属性覆盖/公开入口拒绝、远端child自然关闭后新握手、同址wrongID三失败池0再恢复、运行共享+queued停止收口、queued真实submission截止未进入factory）。生产仅client.acquire明确closed准入LOCAL+IllegalStateException cause；有效原始RED1test/1failure/0error。queued privileged factory spy仅包装原生产认证结果后hold，不给业务替换授权、不伪造context/facts；无效旧CONNECT probe和编译/helper失败按真实归因保留。最终selected5/module233（14 suites）各exit0且零failure/error/skip，模块435s、单条900s预算最终仅一次；证据 `/tmp/bolt-t2-production-background/` before/precise diff/SHA/commands/exits/report/mtime。测试SHA `1578b6bb0089d305580c6215c3ca9ecb6f3e11e34f6f2ac9cbfc02af7882836b`；client SHA `b86d648c01afcdb616161d2cff87b5f29ff2c2cfa707148e217e6332793e7897`，manager/factory未变。既有init rollback/成功移交关闭及外部bean回归由本轮module重验，不冒称逐factory.init异常注入。T2仍active，待独立spec/quality后请求有限T2退出Review，不再机械扩T1；T3未开始/full未运行，无commit/deploy。
>
> T2后台/关闭组最终spec `a95087a9`、quality `484893cb` 均scoped Accept/findings none。上述五方法对应最终selected5/module233（14 suites）真实exit0、零failure/error/skip，模块435s，精确 `/tmp/bolt-t2-production-background/`；测试SHA `1578b6bb0089d305580c6215c3ca9ecb6f3e11e34f6f2ac9cbfc02af7882836b`，client SHA `b86d648c01afcdb616161d2cff87b5f29ff2c2cfa707148e217e6332793e7897`。关闭准入误归PROTOCOL的真实RED1test/1failure/0error及修后明确LOCAL、保留IllegalStateException cause成立，不泛化异常分类；既有失败/反证记录保持。创建线程观察使用全局按精确name筛选且该用例串行隔离、当轮仅一个controlled client，join这些实际线程对象是该场景witness，**不证明多client下精确owner归属，也不证明全部静态JVM worker关闭**。
>
> T2全部既定有限逻辑组现已获局部双Accept（目标/配置、受控生命周期、预算/共享创建、RPC、资源、后台/关闭），历史Review IDs、Block/Revise及闭环依据见各组原记录，不删除、不用较晚成功覆盖较早失败。当前完整T2退出Reviewer `a2208471` 正在读取冻结实现；T2仍唯一active、T3–T6 not_started，不能由scoped Accept自动标记T2passing。此时不再写生产/测试或运行Maven，等待有限退出审查；若有must gap仅按批准范围修复，若Accept由父协调T3实施，无需再请求用户继续。full未运行，无commit/deploy。
>
> T2完整退出审查 `a2208471` verdict **Revise**，仅两项有限验证缺口，其余要求已mapped满足，无新增生产bug。本次冻结仅 `BoltUnifiedInvokeTest.java#typedProductionProtocolFailuresRetainOriginalCause`（NON_BOLT_RESPONSE / UNSUCCESSFUL_BOLT_RESPONSE / JSON_DECODE_ERROR三个真实typed调用，PROTOCOL及原cause/稳定消息）和 `BoltConnectionLifecycleTest.java#typedCallerDeadlineExpiresAtProductionGateBeforeEncoding`（真实typed命令、native future已登记后hold写入，真实单调预算耗尽再进gate，实际promise LOCAL+TimeoutException/caller gate budget、forward/encoded/handled均0）。不加第三范围、不改T1/生产API；短暂源码变异分别擦除typed PROTOCOL分类与gate deadline分支，用实际断言RED且恢复源码SHA，不留生产开关。原生RPC timer可先结束caller，但gate实际write promise独立证明deadline分支，不将caller timeout当gate证据。
>
> - [x] 两方法窄RED后恢复GREEN：`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am '-Dtest=BoltUnifiedInvokeTest#typedProductionProtocolFailuresRetainOriginalCause,BoltConnectionLifecycleTest#typedCallerDeadlineExpiresAtProductionGateBeforeEncoding' -Dsurefire.failIfNoSpecifiedTests=false test`。
> - [x] 冻结最终selected4及单独 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test`，各900s预算、一次最终module不并发；证据 `/tmp/bolt-t2-exit-final/` before/diff/SHA/commands/exits/reports。原退出Reviewer复审及独立quality后才T2passing/T3active，不自动passing；旧后台双Accept及233通过事实保留。
>
> T2完整退出仅两must补证候选已冻结：`typedProductionProtocolFailuresRetainOriginalCause` 三真实typed TLS response case均PROTOCOL，前两保留IllegalArgumentException/稳定消息，JSON case保留项目SerializationException直接cause及JsonParseException根因；`typedCallerDeadlineExpiresAtProductionGateBeforeEncoding` 在真实native future登记后hold命令，真实System.nanoTime预算300ms耗尽后写回，实际promise LOCAL/TimeoutException/caller gate budget、forwarded/encoded/handled均0。native timer可先移除future，故变异移除deadline后实际promise SecurityException，而非业务泄漏；不将caller timeout冒称gate证据。有效源级mutant RED4tests/4failures/0errors，生产原字节恢复，初次JSON cause误断言开发失败保留。final selected4/module237各真实exit0且零failure/error/skip，模块409s、单条900s预算最终一次，证据 `/tmp/bolt-t2-exit-final/` before/precise diff/SHA/commands/exits/reports/mtime。Unified SHA `7143926984a5a6185b78d9e000e9efe2b1a08405e98b341aa72bfbdab2beef0c`；Lifecycle SHA `c18eddbe84216870eb54f12630604a9e43f775c52b86f3f1dfd182b356bbaf87`。仅两测试文件最终变更，不增第三范围；当前a2208471 Revise修复候选待原完整退出有限复审及独立quality，T2仍active/T3–T6 not_started/full未运行，无commit/deploy。
>
> T2完整退出最终 `f4ef3381` Accept、独立quality `3729b260` Accept，两有限补证无剩余must；final selected4/module237实际exit0（409s），Unified SHA `7143926984a5a6185b78d9e000e9efe2b1a08405e98b341aa72bfbdab2beef0c`、Lifecycle SHA `c18eddbe84216870eb54f12630604a9e43f775c52b86f3f1dfd182b356bbaf87`，证据 `/tmp/bolt-t2-exit-final/`。T2 passing，T3唯一active/WIP=1，T1passing、T4–T6not_started；原a220 Revise及所有局部Block/Revise保留，full尚未运行。
>
> T5 状态核对（经 b56ec434 Revise 限定）：上述 T2 仅已完成行为按原组证据回填；原三类合并 selector 的 RED/GREEN 及后续原顺序命令义务独立保留未勾，不能用下列分组结果隐含其已执行：目标/config 为 target-policy revised selected11/module183；受控 factory/manager/gate/startup 为 controlled drain-revised focused22/module194；预算为 production-budget revised narrow5/focused26/module205；终态为 production-rpc revised selected4/focused45/module224；资源四方法为 production-resources revised selected4/module228；后台五方法与 init 回归为 production-background selected5/module233；最终两项为 exit-final selected4/module237 及 f4ef3381/3729b260 双 Accept。不是本轮重跑，不改旧窗口或将旧 XML 当 fresh。
>
>
>
> - [x] 对上述客户端测试增加目标捕获断言并完成 typed 迁移行为覆盖：Peer 八方法；Broker 注册/注销/心跳两类、list、首次/后续 state、连接注册/注销、帧、关闭、快照和 ACK 的地址/完整身份断言，不只 probe/report。原 SDK target 捕获 RED 实际命令为 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-sdk -am '-Dtest=BrokerClientTest#hashesGatewayOperationsByGatewayId,BrokerClientRoutingTest#bootstrapsCommittedStateThenRoutesUserDirectlyToRouteBroker' -Dsurefire.failIfNoSpecifiedTests=false test`（`/tmp/bolt-t3-sdk/red-selected.command`，exit1）；最终 selected 为 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-sdk -am -Dtest=BrokerClientTest,BrokerClientRoutingTest -Dsurefire.failIfNoSpecifiedTests=false test`（同目录 `final-selected.command`，exit0）。其余迁移按下文 transport/unified-final 分组证据审查退出，不声称原五模块 RED 顺序已执行。
> - [x] 迁移目标保留、信任及认证失败处理已按实际反证与40ac3088/02665be5接受：`/tmp/bolt-t3-sdk/red-selected.command` target捕获 RED、同目录 ACK mutant记录；`/tmp/bolt-t3-transport/red-gateway.command` target RED、revised-red.command 构造/copy mutant8失败；`/tmp/bolt-t3-unified-final/revised-red.command` rollback1失败。原五模块逐一顺序 RED 未执行，不以 compile 成功重建历史，也不称每个 caller 都有独立 RED。
>
> 原步骤历史（不补造执行；组合、顺序及当时状态不代表当前义务）：原要求「RED 命令按下面五模块顺序逐一执行」未取得完整对应运行证据，与下方五模块顺序 GREEN 义务分别保留。分组行为证据与 40ac3088/02665be5 scoped exit 不证明这些原命令已逐条按序执行；是否接受分组证据替代待独立合同 Review，本组不自行批准。
> - [x] 实施 O1：Nacos 地址与部署配对求交，缺失/冲突 fail closed，不读 metadata identity、不从首响应学身份；committed state 保留完整节点，revision/算法/内容校验不减。明确副作用前拒绝仍只重试一次，保留 operationId/期限；认证失败不得被 ACK/刷新 catch-all 吞掉换节点。
> - [x] Gateway 两操作用 DTO 注册绑定的 workloadIdentity 而非随机 gatewayId；Gossip digest/delta 共用不可变通用目标。Broker 核对观察 ID 与可信配对/committed 绑定，无法匹配拒绝；插件不理解 brokerId/Raft。
> - [x] 同步三消费方 typed 属性/装配、默认 Bean、直接 new 与全部上述 fixture，不增加兼容构造器，不让 null 身份或普通 RpcClient 替身穿过生产装配检查。测试明确替代边界；HA 自用证书 fixture 同步为真实链。
> - [x] 删除 `invokeAuthenticated`、地址版 invoke 和 BoltRpcClient 地址逃生口；执行 `git grep -n 'invokeAuthenticated' -- '*.java'`，预期零命中（exit 1）；检索 `git grep -nE 'new (BrokerClient|GatewayClient|RpcClient)|implements BoltInvoker|\.invoke(Sync|Async|WithCallback|Oneway)\(' -- '*.java'`，逐项归属审计，不能把所有底层协议写一律禁掉。出现本清单外调用者先补准确路径并 Review，再修改，禁止遗漏或目录通配授权。
> - [x] 迁移相关回归已接受：`/tmp/bolt-t3-sdk/final-module.command` 完整 SDK46+Bolt237；transport/final-selected.command 13类60及 final-compile.command；unified-final/final-selected.command 19类116、config-structure-focused.command SDK18及 final-compile.command，均在各原目录命令/exit/manifest 中按轮保存，40ac3088/02665be5 Accept。60/116是 selected、test-compile仅编译，不是五 owner full tests；原下列五条顺序命令未执行。owner 全套回归必须由 T6 同轮完整 reactor 实际报告承接，未验收。
>
> 原步骤历史（不补造执行；组合、顺序及当时状态不代表当前义务）：顺序 GREEN：`mvn -q -pl im-plugin/im-gossip -am test`；`mvn -q -pl im-broker/im-broker-sdk -am test`；`mvn -q -pl im-gateway/im-ws-gateway/im-ws-gateway-sdk -am test`；`mvn -q -pl im-broker/im-broker-server -am test`；`mvn -q -pl im-gateway/im-ws-gateway/im-ws-gateway-server,im-service/im-message/im-message-server,im-service/im-account/im-account-server -am test`。RED 为原调用未携身份/旧构造器未迁移；编译 RED 只用于 API 迁移，发现信任和重试必须另有行为断言。
>
> 完成条件：全部生产路线和直接 fixture 使用新契约，SDK 不依赖 Server，Gossip 不依赖业务类型；未知绑定、错身份、无首份 state、ACK 认证失败均有拒绝断言。不是仅静态改签名。
>
> T3按最多三逻辑组推进：当前组1仅Broker SDK目标保留与bootstrap信任（`BrokerClient.java`、新增已列 `model/BrokerIdentityConfiguration.java`、`BrokerRefresher.java`、现有 `BrokerClientTest.java`/`BrokerClientRoutingTest.java`）；组2 Gossip/Peer/GatewaySDK及各消费方typed配置与直接fixture；组3最终删除旧入口与全消费方/HA/E2E编译运行收口。各组独立双Review，施工期间不发布。当前组1先实际target捕获行为RED（旧地址路径targets为空，不以API编译失败冒充RED），再签名迁移与可信配对/committed节点目标/ACK认证拒绝回归。隔离命令 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-sdk -am -Dtest=BrokerClientTest,BrokerClientRoutingTest -Dsurefire.failIfNoSpecifiedTests=false test`，最终本组模块命令 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-sdk -am test` 各900s/no并发，证据 `/tmp/bolt-t3-sdk/`。
>
> 构造器全检索发现T3原fixture清单外四个直接super调用，明确纳入组2同步：`im-gateway/im-ws-gateway/im-ws-gateway-server/src/test/java/com/co/kc/imchat/gateway/ws/RegistrationLifecycleTest.java`、`handler/FrameHandlerTest.java`、`handler/netty/HandshakeHandlerTest.java`、`server/NettyWebSocketServerTest.java`。BrokerClient新构造器需同步已列WS/Message/Account装配及这些fixture；不新增无信任配置兼容入口，组1独立SDK reactor验证不代表全调用方已同步。显式测试节点名bootstrap仅代表部署配对的一个节点ID，不是新共享bootstrap服务身份。
>
> T3组1当前冻结待独立spec/quality：仅BrokerClient、BrokerIdentityConfiguration、BrokerRefresher及两SDK测试。部署pairs与发现交集、完整committed节点生成typed目标，当前cache原revision/algorithm/content验证保留且拒绝stale刷新影响目标；ACK AUTH及Refresher AUTH原对象拒绝，不fallback。实际旧地址target捕获RED2tests/1failure/0error，ACK AUTH catch源级mutant RED1test/1failure/0error（Refresher同方法后段未达，不冒称单独RED），已恢复源码。final selected18、SDK reactor（SDK46+im-bolt237）真实exit0、全零，469s/900预算，证据 `/tmp/bolt-t3-sdk/` before/精确diff/SHA/commands/exits/reports/mtime。BrokerClient SHA `d9c76bd086a8e3724d219e6a4b9858f0dce21f1f1b14ebb7353a7cac4a5674fe`；IdentityConfig SHA `1332e10ff17373ff21171c25c2415efb02f5125417c502f4388270405e9250d0`。下游WS/Message/Account装配和直接fixture新构造器尚待组2同步，当前mixed worktree下游签名不兼容，不能把本scoped候选当T3passing/全repo编译成功；无stage/commit/deploy，不开始组2写入直到本组双Review。
>
> T3组1 spec `c9d95ee9` scoped Accept，quality `b2274ee9` scoped Revise仅 `BrokerIdentityConfiguration.java` 两处单行Javadoc格式。当前候选仅转换为标准多行Javadoc，未改语义/生产行为/测试；`git diff --check`通过，精确 `/tmp/bolt-t3-sdk/revised-doc.diff`、before与revised SHA；按纯注释变更不重复469s reactor，既有final selected18/SDK46+Bolt237实际exit0适用行为输入保持。待原quality快速复审；下游构造器仍待组2同步，T3active未passing，当前mixed worktree不代表全仓可编译。未扩metadata建议测试，无commit/deploy。
>
> T3组1 spec `c9d95ee9`、quality `b2274ee9` 复审均scoped Accept，Javadoc-only两must关闭；revised Config SHA `e5632cf6f8c26f9af2097b2c6bc5a52ac2715a09f37bd0b2f24d200338fd6b07`，原469s行为证据不重复跑。T3仍active。
>
> T3组2精确增补 `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/infrastructure/config/properties/ClusterProperties.java`（原seed-addresses缺身份，新增typed可信Gossip观察ID/地址/完整身份配对，不能用观察ID拼稳定证书ID）。其余文件按T3原准确scope及四额外WSsuper路径：Gossip client/synchronizer与测试、PeerAdapter与测试、GatewayClient与测试、BrokerGossipScheduler/Properties/RegistryGossipConfiguration/ClientConfiguration、三消费方属性与装配/YML、所有直接fixture构造器同步。固定两阶段同组：transport owners行为RED后迁移，随即恢复consumer/testfixture编译再冻结，组内不宣称半迁移passing。Gossip支持显式trusted观察ID配对，未配置不连接，不把Gossip状态当认证事实；SDK不读serverBean/plugin不读Broker类型。最终selected命令按Gossip→GatewaySDK→BrokerServer及三消费方执行实际类scope，再单条affected reactor `test-compile` 校验所有Java；组3删除旧入口、全T3 Review前不passing。证据 `/tmp/bolt-t3-transport/`，每条隔离仓库/no并发/900s，暂不运行full。
>
> T3组2候选冻结待双Review：准确37路径/SHA及既有变更归属见 `/tmp/bolt-t3-transport/files.txt`、`final.sha`、`RESULT.md`；Gossip通用不可变目标、Peer八操作、Gateway两操作、三consumer typed配对与直接new/super全同步。Gossip部署pair明确observationId+address+expectedURI，重复地址/观察ID/预期URI冲突拒绝，Registry观测ID绝不默认稳定brokerId；选择O1显式配置路线，无新全局identityregistry。AUTH scheduler原对象停止，probe仅transport unhealthy。YML空pair默认failclosed，未引入隐式plaintext/默认证书身份。Gateway target使用DTO注册身份不拼随机gatewayId；授权仍归原Registry/租约拥有者。
>
> 实际red-gateway1test/1failure/0error，编译遗漏/Peer stub旧强转/Gossip新fixture缺正式MTLS策略等开发失败保留且不冒称RED。final selected13类60tests各实际owner XML当轮mtime验证齐、零failure/error/skip、exit0；单条affected BrokerServer/WS/Message/Account/HA/E2E及-am test-compile exit0，下游新构造器编译恢复，此仅编译非HA/E2E行为验收。不重复全Bolt，证据commands/exits/reports/manifest齐。此候选仍待组2spec/quality，不开始组3写入；旧入口删除及最终T3运行门禁/architecture、HA/E2E安全装配仍组3范围，T4开发/入站另行实施，不宣称T3passing/full完成，无stage/commit/deploy。
>
> T3组2 spec `a168` scoped Accept，quality `236be828` **Revise**仅两must：可信配对配置构造边界缺直接测试、record七组件布局/注释。有限修复范围冻结仅 `BrokerGossipProperties.java`（格式与组件说明，不改行为）及新增 `im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/interfaces/scheduler/BrokerGossipPropertiesTest.java`（参数ADDRESS/OBSERVATION_ID/FULL_URI冲突、NAMESPACE及prefix拒绝；source List防御复制与不可变）。先短暂删除相应生产check/List.copyOf源级mutant取得实际构造断言RED再还原，不留生产开关/框架；测试拒绝精确IllegalArgumentException/message，不跑RPC。窄GREEN命令 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=BrokerGossipPropertiesTest -Dsurefire.failIfNoSpecifiedTests=false test`，最终owning focused `-Dtest=BrokerGossipPropertiesTest,BrokerGossipSchedulerTest`，各900s/no并发。证据 `/tmp/bolt-t3-transport/revised-*`，旧60及affected compile证据保留不冒充本轮fresh，无签名/行为变更不重复全reactor；待原quality复审前不删旧入口，T3active未passing。
>
> T3组2 quality `236be828` 两must修复候选冻结：仅record七组件逐行block说明（强调观察ID非稳定ID、部署授权/namespace）和新 `BrokerGossipPropertiesTest`；ADDRESS/OBSERVATION_ID/FULL_URI/NAMESPACE四case、三个非法prefix、source mutable List改写后快照/不可变共8tests，精确异常与消息。短暂源级删除构造checks/copy的mutant RED8tests/8failures/0error，原行为恢复且SHA一致，无开关。narrow8/focused15（config8+scheduler7）真实exit0、全零，`/tmp/bolt-t3-transport/revised-*` before/diffs/SHA/commands/exits/reports/mtime齐。本轮仅owningfocused、不冒称旧13类60fresh；无签名/生产行为变化不重复affected compile/reactor。spec a168 Accept、quality Revise候选待原复审，T3仍active，不开始组3，无commit/deploy。
>
> T3组2 spec `a168`、quality `236be828` 复审整体scoped Accept，两must关闭；revised config8/scheduler7通过，旧60/compile证据保持原轮，不冒称fresh。
>
> T3组3代码前冻结：最终删除 `BoltInvoker`/`BoltClientInvoker` 地址invoke及旧认证方法、逐请求认证helper，删除无caller默认policy构造器；`BoltRpcClient`去地址方法/默认构造器，保留固定typed策略验证。同步所有既有SPI测试fixture为typed-only，`BoltAuthenticatedFailureTest`迁typed受控边界保留四分类/interrupt/late acquisition；`BoltAuthenticatedTlsTest`迁真实controlled typed TLS，原入站URI原生事实测试仍使用本地native RpcClient（不是生产逃生API），保留单预算/协议失败用例。两文件原计划T2已授权、此处精确登记T3迁移。`BoltUnifiedInvokeTest`无policy构造用例改显式空配置；`AutomaticFailureTlsHaTest`生产Peer调用装配改真实BoltAuthenticatedClient+固定policy，原TLS材料/业务链不削弱。原E2E构造已typed但运行拓扑尚未迁mTLS/开发，runtime归T4及T6，不运行已知错误拓扑冒充E2E。其他fixture路径按T3已列准确文件，不新框架/POM/seam。
>
> - [x] 全旧入口检索零命中（所有Java含测试），typed-only fixture不降身份；直接native调用按transport owner/历史原型/入站事实边界记录，不无脑删除架构反例。
> - [x] 原 Bolt 三类实际由 `/tmp/bolt-t3-unified-final/probe-selected.command` 执行 exit0；随后 final-selected.command（19类116）、final-compile.command、ha-focused.command（1）及 architecture.command 的最终记录 architecture-final.exit=0（75 tests、2既有skip），原命令/exit/log/manifest俱在同目录。首次 architecture-first-failed.exit=1（75/1failure/2skip），经配置职责修正再通过，不擦除失败。后续 revised-red.command 回滚1失败、revised-selected.command TLS12+Failure4=16全零及40ac3088/02665be5完整退出 Accept；未覆盖的 owner 全套回归归 T6，不以架构后过注销。
>
> 原步骤历史（不补造执行；组合、顺序及当时状态不代表当前义务）：窄层 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltAuthenticatedFailureTest,BoltAuthenticatedTlsTest,BoltUnifiedInvokeTest -Dsurefire.failIfNoSpecifiedTests=false test`；然后一条affected owners聚焦（含组2类+SDK）和affected `test-compile`（含HA/E2E）。通过后必要owner模块与architecture；每条900s预算、不并发，不重复同因全Bolt，失败先窄层归因。证据 `/tmp/bolt-t3-unified-final/` before/diff/SHA/legacy-search/commands/exits/reports，最终本组双Review+完整T3退出Review前不passing；full仅T6定稿后运行。
>
> T3组3冻结候选待Review：准确20文件及SHA见 `/tmp/bolt-t3-unified-final/files.txt`/`final.sha`，before/diffs精确保留；SPI仅typed，删除ClientInvoker旧地址/逐请求认证helper与默认policy构造器、RpcClient地址wrapper；全Java旧认证入口零命中。历史Failure/TLS回归迁正式typed并保留有价值场景，inbound事实仍native测试边界不冒称生产旁路；HA真实装配受控client。
>
> final selected19类116tests exit0，随后本地配置结构修正复验SDK18tests，manifest准确标每份报告实际轮次；HA真实三Broker focused1 exit0，affected全部test-compile exit0。首次完整architecture75tests/1failure/2skip必须保留：选择器覆盖全部SDK公开record而本地启动策略非RPCwire DTO；按CODING_GUIDE155与批准设计、全引用非wire事实，经父批准将 `BrokerIdentityConfiguration` 精确原路径改final immutable行为class并删除无caller自动accessor，不加Serializable/不改checker。新增此准确文件到组3修复范围，独立Reviewer需审核职责不只正则过关。窄checker之后完整architecture实际exit0（75tests/0failure/0error/2既有skip），证据与selector/规范行/引用归因见RESULT。全T3runtime E2E还归T4显式TLS/开发及T6，不运行旧plain拓扑冒充通过，T5无旁路checker未实施。T3仍active，待组3spec/quality及完整退出Review，无commit/deploy/full。
>
> T3完整spec `40ac3088` Accept；quality `02665be5` Revise仅两must。修复范围代码前冻结：`BoltAuthenticatedTlsTest.java` Endpoint构造失败独立逆序清理、保留原异常及suppressed，新增 `endpointInitializationFailureRollsBackActualAllocatedResources` 在真实server/controlled/raw均分配后确定性失败，断言实际连接/manager/server关闭；fixture-only初始化函数用于调用实际raw init后抛异常，不新增生产seam。`BoltRpcClient.java` 两处、`BoltClientInvoker.java` 一处单行Javadoc转多行，纯格式不改行为。先临时移除fixture回滚取得新方法RED，恢复后 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltAuthenticatedTlsTest,BoltAuthenticatedFailureTest -Dsurefire.failIfNoSpecifiedTests=false test`，900s/no并发；精确 `/tmp/bolt-t3-unified-final/revised-*`。原TLS为11测试方法（部分方法多场景），不是12方法；结果说明更正。T3仍active，原quality复审前不T4，不重复SDK reactor/HA/architecture仅因注释与fixture变更，无commit/deploy。
>
> T3 quality02665be5两must候选冻结：仅TLS Endpoint构造失败独立逆序清理与三处Javadoc。新fixture-only初始化lambda先实际raw.init/controlled acquire建立两个TLS Channel后抛原异常，断言server/client/raw/manager停止及两Channel closeFuture/!open；临时移除constructor rollback有效RED1test/1failure/0error，恢复后TLS12+Failure4共16tests实际exit0、全零。精确 `/tmp/bolt-t3-unified-final/revised-*` before/diffs/SHA/commands/exits/reports。原TLS为11方法、多场景，新加回滚后12，RESULT已更正不是删场景；无生产seam/行为/POM改动，不为注释/fixture重跑SDK reactor/HA/architecture，旧证据保持原轮。完整spec40ac3088 Accept、quality02665be5 Revise候选待原复审，T3active不自动passing/T4active，无commit/deploy。
>
> T3最终完整spec `40ac3088` Accept、quality `02665be5` 复审Accept，两must关闭；revised TLS12+Failure4实际exit0，历史architecture失败/局部Review/旧入口删除事实全部保留。T3passing，T4唯一active/WIP=1，T1–T3passing/T5–T6not_started。
>
> 合同归属候选：上述T3阶段行为按原命令/实际报告与完整退出Review重述；五模块原顺序未执行，owner完整回归归既有T6未勾义务。此diff仍待a1原Reviewer审查，不以checker结果自行批准。
>
>
>
> - [x] 先补 RED：普通入站错误角色、无/多 URI、Subject-only、证书到期等号、新准入过期拒绝，在途响应允许；非成员、scope/audience、Gateway 端点/租约绑定、epoch/Lease/fence 失败仍拒绝。Gateway 必须验证调用 Broker 权限，不把出站握手当业务授权。
> - [x] 实施严格握手 URI 来源与链有效区间事实；生产关闭 trusted-network 回退，服务端强制 client auth；拒绝外部不受控 Bean。Account/Message/Gateway 三者均用真实 Spring 正/负装配测试，不仅测 Broker。
> - [x] 按初始网络契约实施开发模式，加入 production+dev 冲突、缺显式 profile/合成标识、任意私网/外部目标/非回环发布、外网附着拒绝；TLS 失败不会切模式。普通发现/注册/心跳/路由/投递/清理有允许用例；观察票和自动摘流有拒绝用例。
> - [x] 入站/开发阶段行为已由 `/tmp/bolt-t4-transport/red-development.command`、red-inbound.command 的真实反证、通用 selected/模块、owner focused48及 profiles证据接受，a21eb322 scoped exit 映射无遗漏。最新 `/tmp/bolt-t4-post-ca-regression/commands.log` 为 im-bolt 完整262（-am总299），不是五模块 full tests；原五模块组合 RED/GREEN 未执行，五 owner 完整回归由 T6 同轮 reactor 实际报告承接。
> - [x] `bash scripts/test-local-compose-harness.sh` 实际 `/tmp/bolt-t4-topology/local-compose.exit=0`（commands.log）；仅静态合同。Docker actual check-only 仅 create/inspect，非业务 start。
> - [x] startup shell 子自测已有本轮实际成功证据：父 `bash scripts/test-harness.sh` 中调用 `scripts/test-startup-harness.sh`，`/tmp/bolt-t5-health-fixture/logs/test-harness.log:17` 明确 `Startup Harness tests passed.`，set-e 后继续；父488.167s/exit1，不能称父全通过，也不是 Maven startup。
>
> 原步骤历史（不补造执行；组合、顺序及当时状态不代表当前义务）：运行 `mvn -q -pl im-plugin/im-bolt,im-broker/im-broker-server,im-gateway/im-ws-gateway/im-ws-gateway-server,im-service/im-account/im-account-server,im-service/im-message/im-message-server -am test`，RED 为未有严格入站/开发拒绝，实施后 GREEN；运行 `bash scripts/test-local-compose-harness.sh`、`bash scripts/test-startup-harness.sh`，明确网络真实创建与静态 fixture 各证明什么。
> - [x] 阶段真实 Gateway E2E、三 consumer startup 及 D14/CA 已分窗口接受：`/tmp/bolt-t4-d12-e2e/commands.log` 的正式 `MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh e2e` 实际1 case/exit0（verify-e2e-verification.json，04:47:25Z）；`/tmp/bolt-t4-consumer-startup/runtime/commands.log` 三 owning startup selector最终4 tests/exit0（08:36:35–08:37:12Z）；CA三方法及模块见顶部证据，a21eb322 scoped Accept。原独立 `verify.sh behavior` / `verify.sh startup` 入口未执行；Gossip、完整行为、startup/E2E及最终多 Broker TLS/真实 HA 仍须 T6 同轮报告验收，不能由 E2E1/startup4推定全覆盖。D14 原子交付、旧会话、新双端ID/全数组、restart与 CA完整顺序及双向native拒绝不取消；不承诺自动热更新、任意 inplace write一致或零中断。
>
> 原步骤历史（不补造执行；组合、顺序及当时状态不代表当前义务）：聚焦运行 `./scripts/verify.sh behavior`、`./scripts/verify.sh startup`、`./scripts/verify.sh e2e`，验证真实 Gateway 下行/清理、Gossip、三消费方装配和双向多 Broker TLS。H 的替代 Gateway ACK 不代替 E 的真实 Gateway。按 D14 取得原生材料加载的真实用例证据：完整交付后已有 Channel 的双端 session 材料保持，强制物理新 Channel 并核对双端 ID 改变及 client/server 实际 session local/peer 全数组，重启后新材料生效；保留 CA 旧 → 双信任 → 新叶 → 撤旧 CA 完整顺序及双向原生 TLS 允许/拒绝验证，撤旧拒绝不能由 AUTH/timeout 或数组缺失替代。完整原子交付与滚动重启是运维建议，不是新连接 only-restart 门禁；不承诺自动热更新、任意 inplace write 一致或零中断。
>
> 完成条件：入站原有业务安全边界不退化；开发模式的允许/拒绝边界有机器证据而非纯文档。环境不足按准确失败 blocked，不用 mock 宣称全栈启动。
>
> T4固定两逻辑组，当前组1仅通用Bolt入站D10与显式受限开发传输：已列 `BoltRequestProcessor.java`、`BoltRequestContext.java`、`ImBoltProperties.java`、`ImBoltAutoConfiguration.java`、`BoltCertificateValidity.java`，精确增补同core现有 `BoltAuthenticatedClient.java`/`BoltAuthenticatedConnectionFactory.java`/`BoltAuthenticatedConnectionManager.java`/`BoltOutboundGate.java`（dev仍走原owned pool/预算/额度，独立channel开发标志不冒TLS事实）；新增 `core/BoltDevelopmentPolicy.java` 实际共享地址/显式profile校验职责。测试已列 `BoltDevelopmentModeTest.java` 与现有 `BoltRequestProcessorTest.java`/`ImBoltAutoConfigurationTest.java`，必要真实入站集成仅现有 `BoltAuthenticatedTlsTest.java`。group2才改Broker/Gateway权限/consumer profile YAML/Compose/E2E。
>
> - [x] 开发装配正例同真实Spring properties+实际active bolt-dev+synthetic flag；拒production共存、缺profile、缺synthetic、TLS冲突、client/server模式冲突、非loopback/fixed172.30.88/24目标及bind、容器标识缺失。factory/gate不生成tlsVerified或证书fact，使用独立dev channel策略标志；对TLS错误无fallback。mode固定，其他资源上限/逐命令预算不变。peer业务权限不由插件按service白名单猜测，观察/摘流硬拒归group2。
> - [x] 入站每次新请求原生握手成功后校验本次会话local/peer全部X509数组区间，拒缺array/expired/notyetvalid；不从首URI或Subject建立严格身份。peerUriIdentities只为叶URI事实，由owner按固定namespace唯一匹配，现有无transport unit fixture明确不代表生产入站能力。禁止旧无配置生产Processor构造器，现有真实测试传正式MTLS配置/handlers并保留事实边界。
> - [x] 原四类行为由实际超集 selector与增量闭环接受：`/tmp/bolt-t4-transport/second-revised-selected.command` 原四类加 BoltAuthenticatedFailureTest，45通过；后续 ipv6-red/green.command、材料/observer窄验证及模块259关闭，spec4ded/quality937及 observer fb176e22 Accept。CA后模块262再次覆盖，a21eb322最终 scoped退出。早期两次900s timeout及259/1failure保留，不改写为成功。
>
> 原步骤历史（不补造执行；组合、顺序及当时状态不代表当前义务）：窄行为RED后最终selected：`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltDevelopmentModeTest,BoltRequestProcessorTest,ImBoltAutoConfigurationTest,BoltAuthenticatedTlsTest -Dsurefire.failIfNoSpecifiedTests=false test`；冻结后模块单独900s预算一次，证据 `/tmp/bolt-t4-transport/`。依赖/生产API旧地址入口不恢复，组1独立spec/quality后group2，T4未passing。
>
> T4通用组1候选冻结待双Review：准确14源码及SHA见 `/tmp/bolt-t4-transport/files.txt`/`final.sha`，before/diffs/RESULT详行为及边界。固定单mode、actual bolt-dev+禁prod/production+synthetic，server/client一致，MTLS server启动强制TLS/clientAuth；开发同controlledmanager/gate但独立channel标志不造证书principal，不按TLS失败fallback。字面loopback或declared固定172.30.88/24只是地址约束，不能把CIDR当隔离证明；Compose internal/无额外附着/发布限制仍组2必须实现，typed身份仅路由绑定。
>
> RequestProcessor每新请求前真实会话两端全部X509数组D10区间，URI facts仅leaf、不Subject/首URI回退；owner namespace唯一匹配/禁观察尚组2。dev正例Spring真实请求context TLSfalse/URI空，无transport拒绝。有效RED development1fail、server线程真实TLS会话区间mutant2fail/0error（caller时钟不改）；最终selected35/module251实际exit0、全零，模块453s独立900s最终一次。初次directBean签名编译/sslHandler错误开发失败保留，不计RED。T4仍active，待组1spec/quality后组2，不宣称完整T4/网络隔离/生产安全验收，full未运行，无commit/deploy。
>
> T4组1 spec `ed172827` **Block**，旧251成功不覆盖两条实际外部Bean启动旁路；quality未开始。有限修复冻结：现有AutoConfiguration、AuthenticatedClient及DevelopmentModeTest；精确新增 `im-plugin/im-bolt/src/main/java/com/co/kc/imchat/plugin/bolt/core/BoltControlledServer.java`，实际拥有固定选项/启动模式，拒无法证明SSL Context实际配置的任意外部RpcServer，不靠可变option getter猜已建TLS context。Client公开只读实际mode capability有启动validator实际caller；server独立enabled validator不因工厂退让消失，实际mode/bind/profile及clientAuth检查。测试actual production MTLS props+外部dev client、server-only缺profile/prod/实际无clientauth/bind mismatch先RED，匹配owned默认/外部实际装配正例保留；不接管外部init/close。证据 `/tmp/bolt-t4-transport/revised-*`，focused后module900s各独立一次，不改纯JavaEnvironment契约/业务owner组2/POM，无commit/deploy。
>
> T4 ed172827 Block两external Bean旁路修复候选冻结：actual client configuredMode与进程配置核对、实际DEV仍真实Environment校验；新BoltControlledServer实际固定mode/options并拥有原生初始化，独立enabled server validator检查actual mode/bind。任意native external server无法通过公开可变option证明已建SSLContext配置，明确拒绝，不引入capability registry；匹配外部controlled server实际start正例不接管关闭。有效RED5tests/5failures/0error，revised selected41实际exit0/全零。
>
> 本轮revised module单条900s工具超时，无exit/finished，不计通过；partial XML显示BoltAuthenticatedFailureTest interrupt分类fixture在调用acquire前500ms真实预算已耗尽（该方法实际16.32s），原cause相同对象断言失败，旧251不覆盖。未擅改该非当前finding fixture或盲重跑；进程消失后无owned Maven/surefire可jstack，未触外仓wrasse。证据 `/tmp/bolt-t4-transport/revised-module.timeout.txt`、partial-reports/mtime/log保持。当前原spec Block修复候选待复审，quality未开始、module验收缺口明确，T4仍active不进入owner组2，无commit/deploy。
>
> T4外部Bean修复补充归因：首次module实际分类fixture失败发生于mock.acquire之前真实500ms预算耗尽，非mode getter/新包装丢因，XML中该方法16.32s。仅 `BoltAuthenticatedFailureTest.assertFailure` 纯分类永抛mock路径换固定LongSupplier并verify精确target/500与原cause identity；interrupt恢复/清理不改，所有真实late/gate预算用例保持。该原T3既有fixture精确纳入此次受影响修复，不放宽断言。narrow failed1/class4/second-revised-selected45均实际exit0全零。
>
> 相同900s第二模块仍工具超时无exit，不再盲重跑；父 `/tmp/bolt-t4-transport/supervisor-second-run-jstack.txt` 成功快照main在真实keytool waitFor/生成材料，末日志持续到AutoConfig负例，仅证明该时点材料成本，不永久排除其他deadlock。首次attach文件只有not-ready错误，两个module partial/mixed timestamps各自保存，不拼通过。精确second-revised-files/diffs/SHA及focused报告齐，当前spec ed172827 Block修复候选待复审、quality未开始、模块完整验收缺口保持，T4active/owner组2未开，无commit/deploy。
>
> T4通用组spec `4ded8717` scoped Accept关闭原ed172两external Block但模块UNVALIDATED；quality `41ee8452` Revise仅实际IPv6loopback与modulegap。有限修复冻结：`BoltDevelopmentPolicy.java`明确仅添加Java InetAddress真实::1展开字面形式，不DNS/映射/scoped/任意私网；`BoltDevelopmentModeTest.java`真实IPv6controlledserver/client正例及mapped/scoped/DNS拒绝。先实际RED/GREEN，证据 `/tmp/bolt-t4-transport/ipv6-*`。
>
> 另按只读诊断29849fda批准仅测试3文件优化：`BoltSecurityGateTestSupport.java` 提取内嵌可关闭CertificateMaterials（仅dir/password/X509/keystore/profilecache，默认Support构造仍独立材料+native witness），`BoltConnectionLifecycleTest.java`/`BoltUnifiedInvokeTest.java`每类独立@BeforeAll材料，@AfterAll删除断言；全部49cases仍每case独立client/server/channel/executor/counter/clock，不共享wholeSupport。材料算法/有效期/keytool超时/constructor失败清理保持，不改T1调用者或生产seam。证据 `/tmp/bolt-t4-fixture-runtime/` before/diff/SHA；最终49+IPv6/dev focused及单独module900s一次，不承诺耗时必改善，不提高预算/skip。不完整两900timeout及首轮实际failure不改写，不开始owner组2，待quality复审。
>
> T4 IPv6/材料候选冻结：实际IPv6typed请求旧输入失败为1error（服务器拒绝返回失败响应），根因明确expanded0:0:0:0:0:0:0:1被地址policy拒，不冒称断言RED；仅精确literal新增后IPv6真实正例+mapped/scoped/DNS/非loopback拒绝2tests通过。CertificateMaterials仅材料目录/密码/X509/profilecache、无native runtime字段；两类独立@BeforeAll，原49case每case实际client/server等隔离，默认Support仍独立材料与native witness。算法/keytool预算不改，构造失败清理与AfterAll实际目录删除保留。focused Lifecycle34+Unified15+Dev19共68真实exit0。
>
> 本轮最终module259tests真实exit1（247s），0error/skip但1failure：历史T1 `BoltOutboundOriginsGateTest.sendFailureAndCloseReleaseRpcPermitOnce[5]` line333预期native-response-audit已移除而context仍存在。旧两900timeout未被本次较快结束覆盖；此次不称通过、未擅改T1或盲重跑。精确 `/tmp/bolt-t4-fixture-runtime/` before/diffs/SHA/focused/modulecommands/exits/reports；IPv6 `/tmp/bolt-t4-transport/ipv6-*`。当前spec4ded scopedAccept/quality41ee Revise候选待复审及module历史T1失败窄归因，T4active不开始owner组2，无commit/deploy。
>
> 父批准最窄历史T1实际失败修复子组，IPv6/材料候选已独立冻结供quality：仅 `BoltOutboundOriginsGateTest.java#sendFailureAndCloseReleaseRpcPermitOnce` close分支及原native-response-audit observer，在handlerRemoved真实回调发布future并bounded await后仍断言contextnull，替代closeFuture/eventloop barrier无法保证pipeline销毁的先验假设，不删安全断言/延长预算。精确before/SHA与证据 `/tmp/bolt-t4-response-cleanup/`；先六参数原方法窄GREEN，再同新前提最后module900一次，若仍失败不无界重跑。原259/1failure/247s记录不可洗掉，T1 passing历史不代表禁止修实际失败，但不重开T1扩矩阵。
>
> T4通用+IPv6/材料组最新spec `4ded8717`、quality `937c201b` scoped Accept/findings none，IPv6must与外部Bean修复确认；旧41eeRevise、两900timeout、259/1failure保留。独立observer最窄修复最终仅 `BoltOutboundOriginsGateTest.java`：每个实际server Channel专属handlerRemoved CompletableFuture，close分支bounded await相应future后仍检查!open与原pipelinecontextnull；removed回调异常completeExceptionally，不由其他Channel提前完成，不删除安全断言/增timeout。旧closeFuture+eventloop barrier不是pipeline destroy完成保证，原失败准确归因保持。
>
> 窄原方法全6参数真实exit0；最终module259tests真实exit0、零failure/error/skip，02:39:11Z–02:46:25Z=434s，独立900s一次，无性能保证。精确 `/tmp/bolt-t4-observer-lifecycle/` before/source.diff/SHA/commands/exits/reports/mtime，最终源码SHA `62c5b4033ddab4b125f9aabbea4738bf02f521a0b787747913dae7091422d440`。observer修复不在937Accept范围，待父独立Review后才通用组机器缺口关闭及推进owner组2；T4仍active，T5/T6/full未完成，无commit/deploy。
>
> T4通用组1最终spec4ded/quality937 Accept，observer独立spec/test-quality `fb176e22` Accept，narrow6/module259实际exit0全零434s、SHA62c5b4033ddab4b125f9aabbea4738bf02f521a0b787747913dae7091422d440；通用组1关闭非T4passing，历史两900timeout及259/1failure保留。
>
> Owner组2按最多两有限子组推进：当前仅准入规则与开发观察硬拒，下一子组consumer profiles/Compose/E2E。代码前精确增补 `BrokerRequestConfiguration.java`（实际组装chain并传server模式）、`BrokerClusterConfiguration.java`（观察mode绑定）、`BrokerFailureObservationProperties.java`、`BrokerClusterAppService.java#reconcileFailures`、`BrokerPeerAdapter.java`（自动观察硬拒不仅scheduler禁用）、`GatewayBrokerAuthorizer.java`、`GatewayBeans.java`，及原T4已列Caller/Peer/Route拦截器、BrokerRoutingProperties、BoltRequestContext/Processor。Context新增明确developmentPlaintext来源事实，所有现存new context fixture必须同步正式6参数，不加5参数兼容。严格owner仅tlsVerified+叶URI集合按允许namespace唯一匹配，不用peerIdentity；开发只能marker+本地显式flag，普通allowtable保持成员/租约等后续校验，观察即使trusted profiles误配仍拒。
>
> 测试当前 `IdentityAccessInterceptorsTest.java`、`BrokerObservationIdentityTest.java`、`GatewayBrokerAuthorizationTest.java`（此准确实际现有测试名替代预估AuthorizerTest）、原FrameWrite/ConnectionClose测试及实际受影响context fixtures（`BrokerBoltHandlerTest.java`、`RouteLookupHandlerTest.java`、`ConnectionRegisterHandlerTest.java`、`AbstractBrokerRpcHandlerDispatchTest.java`、`BoltAuthenticatedTlsTest.java`）。新增strict URI/noTLS伪造/多identity、dev普通注册允许但probe/report拒绝真实behaviorRED，保原角色/member/lease/fence测试。先focused owning模块，后affected test-compile，不跑T6full；证据 `/tmp/bolt-t4-owner-admission/` before/diff/SHA/commands/exits/reports，独立双Review后下一子组，不无限扩矩阵。
>
> Owner第一子组候选冻结待Review：准确23路径/SHA/原diff见 `/tmp/bolt-t4-owner-admission/`，补实际依赖GatewayProperties默认trusted=false与BrokerObservationRequestConfigurationTest正式构造参数同步到本组精确范围；leaf唯一严格选择不读legacy、Context显式devmarker全构造同步、MTLS trusted flags拒绝、dev普通allowtable与probe/report硬拒、Peer观察API/application自动摘流入口拒绝。有效RED普通伪身份1failure；focused六简单类名实际七owner类47tests全零exit0，affected Broker/WS/Message/Account/HA/E2E compile exit0。仅compile不冒称runtime，旧YML/profile装配与Compose/E2E下一子组未实施，T4仍active/full未运行，无commit/deploy。独立Review重点实际模式与owner observation配置绑定不因mutable配置放宽，当前候选不自动passing。
>
> Owner候选spec91cedb4c Block：mutable observation permission可重开DEV及已有FAIL_BROKER恢复，旧47通过不覆盖。有限修复冻结仅PeerAdapter、ClusterConfiguration、FailureObservationProperties、PeerAdapterTest以及新 `im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/infrastructure/config/BrokerDevelopmentObservationPermissionTest.java`；HA实际Peer构造同步 `AutomaticFailureTlsHaTest.java`，不增加兼容布尔构造。Adapter final许可来自实际BoltAuthenticatedClient.configuredMode，Spring factory验证running且invoker实际拥有该client，应用同既有observationsAllowed事实在任何cluster读取前拒绝。删除options权限field，regression真实DEV装配后普通properties变更仍拒三个入口，prepared未完成FAIL_BROKER但不读取/共识/控制RPC；匹配MTLS保正例。同步批准设计/Harness immutable actual-mode规则和窄回归、Review-only装配来源审计条件。证据 revised-*，focused/compile后停待原spec复审，quality未开始，无commit/deploy。
>
> Owner91cedb4c Block有限候选冻结：删除FailureObservationProperties权限Boolean，PeerAdapter final许可直接由实际BoltAuthenticatedClient.configuredMode构造导出；Spring factory核对实际running transport与BoltClientInvoker引用一致，API probe/report及application reconcile同事实，拒绝在cluster读取前。actualDEV Spring装配后修改普通options，已准备未完成故障operation mock但三个入口均SecurityException且cluster/raft零交互；原mutablefield污染实际RED1failure保留，修后fixture不调用已删除setter而变更timeout普通属性。focused48tests/affectedcompile实际exit0。实际HA构造同步真实client，unit Peer用正式构造client的固定mode不伪permission flag；MTLS单元正例保留但不冒称新MTLS全栈运行。设计与Harness同步immutable实际模式规则/窄回归与来源Review-only自动化条件。精确revised-* before/diff/SHA/commands/reports，当前specBlock候选待原复审、quality未开始，监督到限不开始profiles/Compose/E2E新大组，T4active/T5T6未开，无commit/deploy。
>
> Owner91cedb4c复审Accept，immutable实际模式许可Block关闭；quality59f9d63b Revise唯一must为GatewayBrokerAuthorizer生产java.util.List全限定引用。当前候选仅增加import List并使用简单类型；相邻已有BrokerObservationRequestConfigurationTest方法名按真实默认MTLS/nullBizContext证明更正，不改行为/不增场景。`git diff --check`通过，精确 `/tmp/bolt-t4-owner-admission/final-import-*` before/diff/SHA；纯引用/命名未重复Maven，旧48/compile保原轮事实不冒称fresh。
>
> Loop#2已50/50到限，仅安全收尾本局部组等待原quality快速复审，不开始profiles/Compose/E2E新组。T4仍active未passing、T5/T6未开始/full未运行；当前恢复点为局部spec已Accept、quality格式候选待复审，其余owner/真实开发部署/E2E后续需监督续期再推进，无commit/deploy。
>
> Owner第一子组安全收尾：spec `91cedb4c`、quality `59f9d63b` 复审均Accept，唯一import格式finding关闭，历史mutable许可Block与格式Revise保留。最终GatewayBrokerAuthorizer SHA `bd6ee0b46acd1b8b7fc576eddb432bb14d6c382721229b01218fde1c40937ddb`；仅命名调整测试SHA `72853094453b22ebd284b7f745ec9b2553c828e6178fa4ad52f60ee65ca5a0e6`，before/diff/SHA见final-import-*。旧focused48/affectedcompile仍对应原实际运行，最终import/方法名布局未重跑Maven，不标fresh。
>
> 监督Loop#2已50/50expired，当前无active自动推进，仅暂停handoff。T4仍唯一active但本局部owner组已双Accept；下一恢复步骤为用户续期后实施profiles/Compose实际隔离合同、三consumer startup正负装配及真实Gateway E2E子组，再作T4退出审查。T5/T6 not_started，full未运行，不能T4passing/整任务完成。此状态批次仅文档，无生产/测试/Maven新动作，无commit/deploy。
>
> #### 历史监督 Loop#3 恢复批次（已 expired）
>
> 用户已明确续期监督 Loop#3：每五分钟检查、最多 50 轮；此前 Loop#2 expired 及原暂停记录为历史，不删除。当前 T1–T3 passing、T4 唯一 active、T5–T6 not_started。owner 第一子组 spec91cedb4c/quality59f9d63b 均 Accept，最终 import/方法名调整未重跑 Maven，旧48/compile不标 fresh。本轮 readiness 实际 exit0，唯一 writer，无并行 Maven；不重做已接受 owner 规则。
>
> 本有限子组为 profiles/部署合同；真实 Gateway E2E/startup 下一组。部署使用者说明准确增补 `deploy/local/README.md` 的 Bolt 受限拓扑段（原 T5 稳定文档提前完成本段，不更改其他部署事实）。四模块 `application.yml`/新增 `application-bolt-dev.yml` 已属 T4 scope；仅以 actual Spring Environment 验证默认 MTLS 缺材料、production+dev、缺 synthetic、outside bind、container 缺 declaration 的拒绝（JVM 不自行证明 Docker 拓扑）。独立专用 Compose 不继承既有 broadinfra 网络，准确增补 `deploy/local/compose.bolt-dev.yml`、`scripts/bolt-dev-up.sh`、`scripts/lib/bolt-dev-network.rb` 与 `scripts/test-bolt-dev-contract.sh`；actual Environment 测试使用既有 `BoltDevelopmentModeTest.java`；不新增 JVM 证明文件/协议/注册表。不改安全 HA 为 dev，不引入通用平台或 Docker socket。容器验收将静态合同 fixture 与实际 Docker network/container inspect 分开，不把 CIDR/布尔声明当实际隔离。
>
> - [x] 实际 contract/Environment 反证已接受：`/tmp/bolt-t4-topology/commands.log` 中 red-compose及移除 Account profile 的 red-profile（缺 key-store、1failure，非编译），其余精确修订 RED/GREEN 见 revised-commands.log 与 second/third-revised 日志；88f36f28/f7e70ae7最终 Accept。撤回的 container-proof/container-only 与早期无效 handoff RED 不计需求反证。
>
> 原步骤历史（不补造执行；组合、顺序及当时状态不代表当前义务）：先实际 contract/实际 Environment RED；证据 `/tmp/bolt-t4-topology/` 记录准确命令、退出、日志、报告/mtime及源码 before/diff/SHA，不以编译失败冒充安全 RED。
> - [x] 四显式 profile 默认 MTLS/trusted fallback=false及专用 internal 网络/唯一附件/发布边界已实现并由88f36f28/f7e70ae7接受；`/tmp/bolt-t4-topology/commands.log` consumer21/dev19与实际 Docker check-only各保留原报告，三轮脚本 Block→有限修复→复验记录均保留。实际 Docker仅 create/inspect，不证明业务启动；CIDR/declaration不充当隔离证明。
>
> 原步骤历史（不补造执行；组合、顺序及当时状态不代表当前义务）：最小实现四个显式 profile、默认 MTLS enabled/关闭 trusted fallback；loopback 最小可运行，容器 internal 172.30.88.0/24、无额外附着及非loopback Bolt 发布，与 profile/synthetic 校验合证。
> - [x] `bash scripts/test-bolt-dev-contract.sh` 正反 fixture、`bash scripts/test-local-compose-harness.sh`及 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltDevelopmentModeTest -Dsurefire.failIfNoSpecifiedTests=false test` dev19实际 exit0（`/tmp/bolt-t4-topology/commands.log`）；consumer ConfigData21各原 XML/mtime、actual check-only与三轮修订证据冻结，88f36f28/f7e70ae7最终 Accept。非 full/业务 Docker startup。
>
> 原步骤历史（不补造执行；组合、顺序及当时状态不代表当前义务）：`bash scripts/test-bolt-dev-contract.sh`、既有 `bash scripts/test-local-compose-harness.sh` 与 focused `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltDevelopmentModeTest -Dsurefire.failIfNoSpecifiedTests=false test`，各自准确报告后冻结交父独立 spec/quality Review。三消费方实际装配测试及真实 E2E 留下一有限组，T4 不提前 passing，full 不提前执行。
>
> 父明确收敛：JVM 仍仅校验已批准 profile/synthetic/declaration/literal 地址；实际 Docker internal/subnet/attachments/port 在受控部署脚本 create 后、start 前检查，不引入可复制的证明文件或新安全控制面。任意 privileged 绕开脚本不属 sandbox 承诺。探索性 container-declaration-only Spring 测试实际失败（context本可启动），该断言要求超出已批 O3，故撤回测试、不修改生产 policy；保留 `/tmp/bolt-t4-topology/red-container.*` 作为被纠正的需求探索反证，不计需求 RED。
>
> Loop#3 profiles/部署合同候选冻结：四 owning YAML 默认 MTLS/TLS enabled、trusted fallback 默认 false；四个 bolt-dev profile 显式 synthetic（默认false），loopback 默认与 typed bootstrap 配对。三个 consumer 测试使用 ConfigDataApplicationContextInitializer 实际读取各模块正式 classpath YAML/profile，真实启动 BoltAuthenticatedClient（Gateway另有真实server），不是手工重造 profile；7+7+7 各 owner XML/mtime 本轮 exit0；默认缺材料/production冲突/missing profile/synthetic/outside bind/container缺declaration拒绝原因断言。Account 移除正式 profile 的有效 runtime RED1failure 精确缺 key-store（源码恢复），通用dev19本轮exit0。此前 NO_PROFILE fixture mode conflict失败保留，不计有效RED。
>
> 专用 Compose 与 scripts create/inspect/start：实际 internal bridge/fixed subnet/唯一attachment/实际Env/精确ports；拒绝foreign members与已有project，单Docker命令120s有界，失败仅清理本次project。静态positive+13negative fixture exit0、旧local-compose harness exit0；真实Docker check-only首轮implicit Redis volume拒绝，改明确合成tmpfs后final exit0，容器/网络/项目卷清理查询为空。该最小拓扑拒绝持久/host mounts是本专用合成拓扑限制，不是改写全仓volume政策。旧匿名卷存在且无可靠任务归属，不prune/不删；只读清单保留。真实inspect用既有routing-ha镜像仅create未start，**不证明本轮业务镜像或业务启动**。探索性JVM证明文件提议未实施，撤回的container-only断言不是需求RED；CIDR/declaration不是隔离证明，trusted deployment边界见README。精确18路径/SHA、before与diff/commands/exits/reports见 `/tmp/bolt-t4-topology/`。当前冻结交父独立spec/quality；不开始E2E写入，T4仍active未passing，full/T5/T6未完成，无commit/deploy。
>
> Profiles/deployment spec `88f36f28` **Block** 三项有限修复候选：仅既有 `scripts/bolt-dev-up.sh`、`scripts/lib/bolt-dev-network.rb`、`scripts/test-bolt-dev-contract.sh` 与 `deploy/local/README.md`（无新生产/profile/Java路径）。旧脚本并发无锁/探测状态丢失/inspect缺事实接受已真实反证：有界UNIXSocket握手精确让首调用停于emptyprobe，第二调用旧实现继续create/down，两个probe非零亦旧实现create/down；shell RED三case；schema RED明确九个缺失/错误事实被旧实现接受，保留日志。
>
> 修后按实际Docker endpoint+project原子mkdir独占，持锁贯穿probe/create/inspect/start/cleanup；context解析/两个probe状态分别failclosed。陈旧锁不自动抢占，仅人工核对owner PID与Docker状态；cleanup只由独占区间内实际create返回后标记，失败create部分资源同归本次清理，第二调用不create/start/down。inspect精确false Running/Privileged、必有MountsArray、PortBindings明确Hash或null（缺字段不放行）、network Containers必有Hash及实际project/network Labels。脚本合同同步Chinese边界，不建注册表/证明文件。
>
> 最终shell契约：原positive+13negative、14缺失/类型负例、nullablePortBindings正例、2probe失败+1endpoint解析失败+1并发互斥全部exit0；无sleep轮询，nativeUNIXSocket握手+Timeout有界。真实Docker check-only实际exit0，readonly observer保存本轮nativeinspect事实（Running/Privileged实际false、Mounts[]、PortBindings{}或Gateway明确绑定、network Containers{}），随后本次project容器/网络/卷查询全空。仍旧镜像create/inspect不start、不是业务运行。无Maven重跑，原21/19报告保持原轮非fresh；精确 `/tmp/bolt-t4-topology/revised-*` before/diff/SHA/commands/logs/actualJSON，候选冻结待原spec复审，quality未启动；88Block历史保留，不进入E2E/T4passing，无commit/deploy。
>
> 原spec88复审第二次仍 **Block**：probe状态与schema两项已确认关闭，剩余仅create取消生命周期及caller TMPDIR分叉锁。本轮准确限定已有 `bolt-dev-up.sh`、`test-bolt-dev-contract.sh` 与README；未重做schema/probe生产规则。旧实现确定性RED（second-revised-red2 exit1）明确TMPDIR_BYPASS、CREATE_NOT_CANCELLED、MISSING_DOWN、DOWN_COUNT。取消fixture首轮因第二旧调用亦进入阻塞down而Timeout，保留red日志；后来green首次无log文件（第二正确早拒尚未执行fakeDocker）与Ruby单行rescue错误导致ESRCH均是fixture实现失败，已准确修正，不冒称RED或生产通过。
>
> 有限修复：固定每用户 `/tmp/im-chat-bolt-dev-$UID`（不随TMPDIR），endpoint+project键仍原子mkdir；exclusive+emptyprobe后、create发起前登记partialcleanup责任，TERM/INT先由Bash可中断wait通知本次Ruby supervisor，kill本次独立Docker PGID并wait回收，然后持锁执行down，结束才释放。fixture以socket观察create child PID/取消EOF，断言child已消失才down、down恰一次，并在down阻塞时第三call仍无create/start/down；两不同TMPDIR同key第二call早拒。无sleep循环，原schema/probe合同保持。
>
> second-revised-green3所有合同exit0；真实check-only actualexit0、仅旧镜像create/inspect无业务start，本次project容器/网络/卷全空；失败fixture遗留两锁已人工逐项核对dead owner PID、精确owner-pid/rmdir清理，未运行自动抢占或删其他Docker资源。`/tmp/bolt-t4-topology/second-revised-*`精确before/diff/SHA/log/actualJSON齐。Java/profile未改，无Maven重跑，旧21/19仍原轮；当前冻结等原spec最终复审，quality未启动，保留两次Block，不进入E2E/T4passing，无commit/deploy。
>
> 原spec88第三轮仍 **Block**，仅启动handoff cancel window未闭；此前probe/schema、固定root、已进入create取消均保持。第三有限候选仅原shell/contract/README：Bash trap始终pending-only，后台PID发布后和wait后统一stop/reap；Ruby spawn/handoff/wait整体begin/ensure保护，spawn期间只记录，wait期间才raise中断，ensure只清理本次owned PGID/PID。无生产测试开关/新helper平台。
>
> 新增两项精确instrumented调度fixture：test-only BASH_ENV DEBUG 在 `operation_pid=$!` 前、RUBYOPT TracePoint在真实Ruby spawn后handoff语句边界送TERM，记录实际owned PID，断言退出时无活owned进程/child且down一次。该测试不冒称默认调度穷举所有窗口。初red/red2/red3未检出因fixtureSTDIN已EOF导致fakeDocker立即退出，不计有效RED；改明确IO.pipe阻塞后red4有效exit1、Bash/Ruby各ownedProcessAlive+orphanChild。最终同fixture完整合同exit0；actual check-only一次exit0，精确第三project容器/网络/卷全空，仍旧镜像create/inspect非业务运行。`/tmp/bolt-t4-topology/third-revised-*`精确before/diff/SHA/命令与退出保留。三次Block事实不覆盖，当前冻结待原spec最终复审/quality，Java/profile未变不重跑Maven，T4active/E2E未开/full未运行，无commit/deploy。
>
> Profiles/deployment组最终原spec `88f36f28` **Accept**、quality `f7e70ae7` 全18路径 **Accept/findings none**，历史三次Block保留。consumer21/dev19原轮、actualDocker仅check-only不冒称业务运行。T4仍唯一active，进入真实startup/E2E有限组。
>
> 当前代码前准确增补三个既有startup测试：`im-gateway/im-ws-gateway/im-ws-gateway-server/src/test/java/com/co/kc/imchat/gateway/ws/ImWsGatewayApplicationTest.java`、`im-service/im-account/im-account-server/src/test/java/com/co/kc/imchat/service/account/ImAccountApplicationTest.java`、`im-service/im-message/im-message-server/src/test/java/com/co/kc/imchat/service/message/ImMessageApplicationTests.java`；E2E使用原列 `RealtimeMessagingE2ETest.java`。正式controlled dev beans、actual bolt-dev profile/synthetic loopback，保留外部设施替换边界，不伪TLS、不关闭必须业务Bean。生产仅在真实T4装配bug证明后准确追加scope，不扩shell组。证据 `/tmp/bolt-t4-e2e-startup/`。
>
> - [x] 实际 E2E runtime失败、close leak及正式 Gateway旅程已接受：`/tmp/bolt-t4-d12-e2e/commands.log` / RESULT.md 保存 e2e-focused2 close route leak、focused3 bind冲突及focused4实际1 case通过；`red-close` 为 actual inactive二次注销丢通知反证，随后 actual-delete通知修订/close22及正式 e2e通过。更早 `/tmp/bolt-t4-e2e-startup/` runtime失败保留；不据笼统历史声称最初指定focused RED命令已按原要求发生。443f8df4/d0e434b7及a21接受阶段行为。
>
> 原步骤历史（不补造执行；组合、顺序及当时状态不代表当前义务）：先原E2E focused真实runtime RED（非编译），后最小真实Gateway Spring/controlled Bolt装配与握手/注册/下行/ACK/close/cleanup。
> - [x] 三正式 consumer startup实际受控装配/typed SDK断言最终4 tests全零，`/tmp/bolt-t4-consumer-startup/runtime/commands.log` 与 final/报告、spec996/quality859 Accept；正式 E2E1由 `/tmp/bolt-t4-d12-e2e/verify-e2e-verification.json` 证明。原 startup→正式E2E顺序未发生：最终startup窗口晚于正式E2E；a21接受分窗口阶段退出，不拼同轮/不刷新旧E2E SHA。T6最终同输入复验仍未完成。
>
> 原步骤历史（不补造执行；组合、顺序及当时状态不代表当前义务）：三consumer正式startup移除transport/SDK替身，actual profile受控装配及typed SDK配置断言；先focused owning类，再 `MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh e2e`；每条单独900s/no并发，不提前full。
> - [x] startup、CA及D12/E2E各原证据目录已冻结 diff/SHA/commands/exits/reports/mtime并取得有限spec/quality；最终 `/tmp/bolt-t4-exit-final.md` a21eb322整体 scoped Accept允许T5。不是完整工作树验收或未来diff预批准；最终full/独立实现Review仍归T6。
>
> 原步骤历史（不补造执行；组合、顺序及当时状态不代表当前义务）：最终精确diff/SHA/commands/exits/reports/mtime冻结交父独立spec/quality及完整T4退出审查；未审通过前不T4passing/T5active。
>
> D12/C 代码前设计候选（待独立Review）：评估278937d7推荐不可变DEV endpoint→GatewayURI部署descriptor，既有registered trusted-network principal/租约/Gossip事实不变，MTLS不使用DEVpair作目标选择，无通用prefix特例。只读核对现有外部controlledserver装配及ApplicationRunner→ApplicationReadyEvent顺序，可测试先以两个实际BoltControlledServer port0绑定保留socket，Broker DynamicPropertySource读actualport固定pair，再正式Gateway context接管外部Bean装配并真实注册；不用freeport close/rebind，不新增生产协调器/可变config。完整约束见设计D12，当前不授权生产修改。
>
> 获设计Review后准确增补范围：`im-gateway/im-ws-gateway/im-ws-gateway-sdk/src/main/java/com/co/kc/imchat/gateway/ws/sdk/model/GatewayIdentityConfiguration.java`（新增final不可变行为class）与同SDK `GatewayClient.java`；`im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/infrastructure/config/ClientConfiguration.java`、`infrastructure/config/properties/BrokerProperties.java`；测试现有 `im-gateway/im-ws-gateway/im-ws-gateway-sdk/src/test/java/com/co/kc/imchat/gateway/ws/sdk/client/GatewayClientTest.java`、`im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/interfaces/handler/FrameProcessHandlerTest.java`（直接构造同步），新增 `im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/infrastructure/config/GatewayDevelopmentTargetConfigurationTest.java`。原E2E与三startup测试范围保持。不新增wire/domain/registry字段，不改shell组。
>
> - [x] D12 b5d08806设计Approved后实际 SDK target RED与回归已接受：`/tmp/bolt-t4-d12-e2e/commands.log` red-sdk 选择 GatewayClientTest#developmentRegisteredMarkerNeedsExplicitRoutingDescriptorForBothOperations，exit1实际身份拒绝；sdk-green 为 GatewayClientTest 全6通过，含MTLS/构造/mutation边界，443f8df4/d0e434b7 Accept。
>
> 原步骤历史（不补造执行；组合、顺序及当时状态不代表当前义务）：获D12独立设计Review后，Gateway SDK target捕获/MTLS拒绝/构造验证及mutation先行为RED；`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-gateway/im-ws-gateway/im-ws-gateway-sdk -am -Dtest=GatewayClientTest -Dsurefire.failIfNoSpecifiedTests=false test`，一次900s/no并发。
> - [x] D12 fixed actual mode/owned client同源及immutable pairs已实现并接受：`/tmp/bolt-t4-d12-e2e/commands.log` broker-focused3实际选择 GatewayDevelopmentTargetConfigurationTest,FrameProcessHandlerTest,FrameDispatchAppServiceTest（原集合超集），1+10+6=17全零；hostmarker/配对与MTLS边界不变，443f8df4/d0e434b7 Accept。
>
> 原步骤历史（不补造执行；组合、顺序及当时状态不代表当前义务）：最小实现fixed实际mode+ownedclient/invoker同源配置、immutablepair；工厂正反 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=GatewayDevelopmentTargetConfigurationTest,FrameProcessHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`。确认实际Gateway模式和hostmarker/pair描述边界、不读mutable mode。
> - [x] D12 真实预绑定 E2E focused4后正式verify e2e通过；consumer startup后另冻结最终证据并由a21完整退出审查接受。原 startup→E2E顺序未发生，各窗口/原指纹保留；证据 `/tmp/bolt-t4-d12-e2e/`、`/tmp/bolt-t4-consumer-startup/runtime/final/`，非T6验收。
>
> 原步骤历史（不补造执行；组合、顺序及当时状态不代表当前义务）：E2E按上述真实预绑定/正式context时序运行，focused通过后正式verify e2e；consumer startup完成后冻结精确证据交父独立Review与T4退出审查。未设计Accept前不改SDK构造签名，不重跑Maven。
>
> D12/C 独立设计 reviewer `b5d08806` Approved/Accept，无must；当前实施上述精确SDK/Broker固定策略及真实E2E组，不改O3/D10/D11。原待审段为历史，当前批准取代其暂停状态。证据 `/tmp/bolt-t4-d12-e2e/`。
>
>
> T4状态依据：a21eb322仅接受阶段退出；上述合同归属重述候选须另经a1原Reviewer精确diff审查。分窗口成功不是原宽命令成功，最终同输入集成仍归T6。
>
> 合同归属候选：T4原五模块完整test及独立startup/behavior入口未执行；实际分组证据已逐项列明。全套owner/behavior/startup/E2E/HA由既有T6步骤承接，不取消coverage，也不改checker；本候选不自行关闭原drift/clean失败。
>
> ### T5 无旁路规则、运维文档和 Harness 登记
>
>
> - [ ] 先写架构反例 fixture，执行 `mvn -q -pl im-test/im-architecture-test -am -Dtest=BoltInvocationBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`：RED 为地址/直接业务 RpcClient 旁路或越过拥有者的 raw 业务写入、codec/transport 替换、pipeline/unsafe API 未检出；在现有准确 fixture 路径 `AddressInvocationBypass.java`、`DirectBusinessRpcBypass.java` 内承载违规案例，`ControlledProtocolWrite.java` 承载受控拥有者正例，不新增路径。`BoltInvocationBoundaryTest` 中 `rejectsBusinessRawCodecPipelineAndUnsafeBypasses` / `acceptsControlledOwnerProtocolWrites` 分别证明各违规 API 被拒绝和受控所有权被接受。规则按所有权/API/层边界判断，无一次性类名豁免；不把静态规则当运行期 raw 字节拦截。
> - [ ] 登记全路线不可绕过、认证不计不可达、开发禁止观察票、资源所有权和验证分层。D11 受控装配下全部可达 ctx.write 起点、原生 TLS/encoder/response/control/rawbytes 及会话证书数组事实仍需真实集成及独立审计；Harness 矩阵标清 Review-only 部分及可自动化条件，不假称静态规则证明 pipeline。
> - [ ] 更新配置键、初始数值/饱和 LOCAL、bootstrap/seed 配对、Gateway 身份绑定、无兼容整体重建、外部 Bean 要求、D14 原生材料加载边界、完整原子交付与滚动重启建议及 CA 顺序、测试私钥清理、审计脱敏/固定指标标签、生产证书与部署仍未交付边界。禁止无依据 TPS、硬期限和资源容量保证。
> - [ ] 运行上述聚焦架构命令、`bash scripts/test-harness.sh`、`./scripts/verify.sh architecture` 和 `./scripts/verify.sh quick`；文档检查准确路径/链接与生产属性一致。之后稳定文档、设计和非状态索引必须在 T6 首次冻结前定稿。
>
> 完成条件：规则有正反 fixture，Review-only 内容有理由/自动化条件，文档说明的是实际能力和限制，无地址调用遗漏。
>
> ## T1 完整退出审核后的有限补证
>
> reviewer `5495396a` verdict **Revise**，不解锁T2。完整映射确认其余四组技术原型满足；必须补齐仅两项：同一caller起点贯穿准备/真实共享等待/gate/应用解码，以及包装后四分类/预算优先级。backend interruption、更多压力和锁外通知仅建议，不提升为第三项。
>
> 仅现有 B+U+`plugin/bolt/BoltSharedCreationGateTest.java`、`BoltOutboundOriginsGateTest.java`，确有复用才改 `BoltSecurityGateTestSupport.java`；不生产/POM/新框架。
>
> - [ ] `callerAcceptanceBudgetCoversPreparationWaitGateAndDecode` / `reset-caller-budget`：参数 SERIALIZATION/SHARED_WAIT/DECODE，用序列化前同一单调起点及显式时间；序列化耗尽预算无业务、实际共享任务等待返回时过期无发送且不cancel共享任务、真实TLS成功响应应用解码后耗尽预算最终LOCAL。gate前截止沿用既有逐命令截止等号证据，不另建调用栈。
> - [ ] `wrappedFailuresPreserveClassificationAndBudgetPriority` / `erase-wrapped-failure-source`：小分类表穿透原始cause，预算内受限transport集合、协议/非法/非成功/解码、真实错身份认证与gate认证/本机预算经native发送包装；迟到获取成功或认证失败最终LOCAL但底层cause保留。只测必要真实路径，不乘API异常矩阵。
> - [ ] 两方法各先命中指定不变量RED，再无control窄GREEN；命令 `mvn -q -pl im-plugin/im-bolt -am '-Dtest=BoltSharedCreationGateTest#callerAcceptanceBudgetCoversPreparationWaitGateAndDecode+wrappedFailuresPreserveClassificationAndBudgetPriority' -Dsurefire.failIfNoSpecifiedTests=false test`，RED对应单方法追加准确control。冻结后类级、模块各一次最终验证，保存 `/tmp/bolt-t1-acceptance-final/` before/diff/六文件SHA/commands/exits/reports。异步finally释放所有latch/候选，原生future/map不替换。
> - [ ] 原退出reviewer依据增量及完整映射复审，再独立质量Review；两项补齐无新finding即可Accept解锁T2，不默认T1passing，T2–T6仍需实现及完整验收。
>
> ## T1 当前最终退出结论
>
> 有限补证后退出 reviewer `5495396a` **Accept**、独立质量 `06107abb` **Accept**，无剩余must；父模块重跑174 tests exit0 `/tmp/bolt-t1-acceptance-final/coordinator-module.exit`。预算control3 failures、分类control1 failure均有效，GREEN selected4/class18/module174。完整before/diff/commands/exits/reports和六文件SHA见 `/tmp/bolt-t1-acceptance-final/`。旧编译失败不是RED，历史Block/Revise/父失败保留；本结论取代历史pending，不改写旧事实。
>
> 六文件 SHA-256：Presented `08a04b766643f87cf645de90a12a045532016ce48c1d197d34a49c53b612ff8b`；Outbound `565d85486e25b691e4da297149993c7f76e92517669ce585cc0ee5a1e6933261`；Shared `e86f566df79b70cb11c1624f234483da5f1b3d69be92bced5321c822c4f5c884`；Background `33a7ea1821df0ec4ccebe5cb2cee4b278a4b68dff3d207d92c7a8d4437f6dd83`；ValidatedPath `915906d6bf5f3578ce8fc801a6f846ab3164a94fa404749888ebb5198ab96fb2`；support `37d49fa206be9e18a96f2cc1fd51c7e81b35a875005c7c44e67e3ba253b2e79b`。
>
> T1 passing、T2唯一active，T3–T6 not_started。T1不再追加测试；T2按既定范围开始目标/typed配置/受控装配实际调用，能力引入同时验证外部Bean正负例，再提取工厂/manager/gate及预算生命周期。旧地址入口仅T3同步迁移暂存，不发布；full仍未运行，隔离仓库architecture成功原事实保持。
>
> ## 恢复状态
>
> - 当前任务：T1 passing，T2唯一active、WIP=1；独立退出5495396a Accept及质量06107abb Accept，T3–T6 not_started。
> - 恢复点：开始T2既定生产能力TDD/提取，每逻辑组独立规格/质量Review，保留T1所有历史反证/Block/Revise。只一writer/一Maven，不提交不上线。
> - 验收边界：T1仅安全机制技术退出；T2–T6实现、全路线迁移、入站/开发限制、架构/稳定文档、真实clean/full及最终Review均未完成。
> - 不要修改：无关用户工作树、历史/completed 计划、JRaft 制品/依赖、已完成收尾流程条目；根 PROGRESS 仅改本计划索引/O1–O4 状态，不重写机器结果或旧失败。
>
> ## 回滚与残余风险
>
> - 施工回滚仅撤销本计划拥有的变更，不覆盖用户原改动；保全测试证据和临时材料清理记录。发布回滚必须停流并整体恢复匹配版本/配置/证书，旧版本安全回退须明确接受及保持隔离，不以认证错误走普通 invoke。
> - 未验证路径：已取得观察 witness 和 D10 数组/区间部分证据及 im-bolt 回归；已验证严格叶 URI、未使用呈现成员及原生恢复失败关闭（不承诺恢复可用）；受控发送、资源上限和后台的所列局部证据已获 scoped Accept，完整T1退出仍审核中；全消费方/HA/E2E尚未实施验证；历史默认仓库解析失败保留，使用既有隔离仓库的 architecture 已实际通过；full 未运行，不能宣称完整验收。
> - 已接受边界：caller 是结果接受预算，不保证不可中断 DNS 的硬返回；墙钟回拨风险、无在线吊销、不承诺自动热更新或任意 inplace write 一致（D14），无容量证明，生产签发/私钥/secret/轮换及上线另行治理。历史 Raft/制品/外部环境风险仍由原记录拥有，本计划不宣称消除。
>
> D12实际super构造检索补充准确路径：`im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/application/frame/FrameDispatchAppServiceTest.java`（原T3已有fixture，仅同步正式策略构造）。首broker/e2e编译失败为该遗漏，不计安全RED，先修窄层再推进。
>
> 真实close路径新增最窄生产修复范围：`im-gateway/im-ws-gateway/im-ws-gateway-server/src/main/java/com/co/kc/imchat/gateway/ws/registry/ConnectionRegistry.java` 与现有 `src/test/java/com/co/kc/imchat/gateway/ws/handler/ConnectionCloseHandlerTest.java`（channelInactive真实回调拥有最后unregister与routeAbsent水位，不能提前删除该事实）；真实E2E已产生下行accepted1但Account close后route仍在的runtime失败。Broker已有 `BrokerRequestConfiguration.java` Bean destroyMethod改与plugin正式Processor一致空值，避免RPCserver已拥有Processor后Spring双shutdown；不吞异常/无新no-op。
>
> 73e3 miniReview 选择 actual-delete callback，取代此前拟inactive owner/Attribute方案。准确生产 Registry、server/handler/HandshakeHandler.java、GatewayBeans.java 与BrokerRequestConfiguration.java；必需Consumer<Removal>无兼容noarg，所有直接fixture同步准确路径：
> - `im-gateway/im-ws-gateway/im-ws-gateway-server/src/test/java/com/co/kc/imchat/gateway/ws/handler/ConnectionCloseHandlerTest.java`
> - `im-gateway/im-ws-gateway/im-ws-gateway-server/src/test/java/com/co/kc/imchat/gateway/ws/handler/bolt/FrameWriteHandlerTest.java`
> - `im-gateway/im-ws-gateway/im-ws-gateway-server/src/test/java/com/co/kc/imchat/gateway/ws/handler/netty/HandshakeHandlerTest.java`
> - `im-gateway/im-ws-gateway/im-ws-gateway-server/src/test/java/com/co/kc/imchat/gateway/ws/server/NettyWebSocketServerTest.java`
> - `im-gateway/im-ws-gateway/im-ws-gateway-server/src/test/java/com/co/kc/imchat/gateway/ws/support/monitoring/GatewayMetricsTest.java`
> - `im-gateway/im-ws-gateway/im-ws-gateway-server/src/test/java/com/co/kc/imchat/gateway/ws/registry/ConnectionRegistryMetricsTest.java`
> - `im-gateway/im-ws-gateway/im-ws-gateway-server/src/test/java/com/co/kc/imchat/gateway/ws/registry/ConnectionRegistryTest.java`
> - `im-gateway/im-ws-gateway/im-ws-gateway-server/src/test/java/com/co/kc/imchat/gateway/ws/registry/ConnectionRouteRevisionTest.java`
> - `im-gateway/im-ws-gateway/im-ws-gateway-server/src/test/java/com/co/kc/imchat/gateway/ws/routing/GatewayRouteSnapshotCoordinatorTest.java`
>
> 先原真实E2E leak与red-close留样，callback聚焦回归GREEN后真实E2E及verify e2e；无wire/domain/lease/权限改动。
>
> D12+73e3 cleanup+真实E2E有限组候选冻结：SDK6tests、Broker工厂1+FrameProcess10+FrameDispatch6实际exit0；cleanup最终WS Close7+Registry5+RouteRevision3+Handshake7共22（此前误称selector另触Broker20/总42，现明确撤回：本轮fresh仅WS22，目录中Broker24属旧轮报告，不计same-run）实际exit0。真实E2E focused1及正式verify e2e实际1case exit0，涵握手/真实注册/上行消息与ACK/真实下行push/Account sessionclose/route清理/第二Gateway重连。close原leak及narrow1failure保留；首次D12遗漏super构造编译失败非RED；中间Broker freeport与预绑定Gateway偶遇port冲突后，改全部3个Boltserver实际port0预绑定持有，非重跑碰运气。原Bean double shutdown经destroyMethod空值唯一RpcServer owner修复，最终真实log无LifeCycleException/二次destroy警告。当前source冻结，不开三业务startup。
>
> 本轮机器证据 `/tmp/bolt-t4-d12-e2e/verify-e2e-verification.json`：startedAtEpoch1792730794、finished2026-10-23T04:47:25Z、51s、stabletrue、fingerprint9be02d967104bf5db2df4de2df6e7d147be710f0319069da62a27327b11a48d3、testCount1，命令与XML/mtime匹配。误拷 `verify-e2e-report.json` 实为Oct22 stale全局report，明确不作为本轮证据、不改写其时间；独立e2e入口未刷新global report，不能据此称full完成。精确25文件/相对批次before diff/frozen-source/SHA及报告见同目录，Review待完成，T4仍active。
>
> spec443 Revise仅证据归属文案，生产源码无must；当前更正待原复审。撤回42/46与Broker24属于此次cleanup的声明：WS22 mtime1792730880–81；incidental BrokerRegistry/Revision mtime1792660083、BrokerClose1792725148均旧轮。保留原报告/mtime/错误记录，不重跑Maven、不改生产/测试source。SDK6、独立Brokerfocused17、实际E2E1分别有效；更正精确diff及状态SHA见 `/tmp/bolt-t4-d12-e2e/spec-revise-accounting.diff`、`spec-accounting-state.sha`。
>
> spec443证据纠正后 **Accept**；quality `d0e434b7` Revise仅规范收尾：GatewayClient Objects import简单类型、HandshakeHandler unused import/rollback闲置参数及调用同删、E2E三个unusedimports删除与ownedBoltServers命名纠正。准确三文件无行为变动，diff/SHA `/tmp/bolt-t4-d12-e2e/quality-revise.diff`、`quality-revise.sha`；旧E2E成功仅属于修前SHA，不称当前fresh，无宽E2E重跑。
>
> 窄命令 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-gateway/im-ws-gateway/im-ws-gateway-server -am -Dtest=GatewayClientTest,HandshakeHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test` 与随后E2E `-DskipTests test-compile` 实际均exit1，第一层为enforcer-plugin3.5.0 descriptor central TLS peer shut down；未达源码/测试，不用旧XML冒称fresh。唯一offline窄复验 `mvn -o -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-gateway/im-ws-gateway/im-ws-gateway-server -am -Dtest=GatewayClientTest,HandshakeHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test` exit1：缺本地依赖plexus-xml3.0.0/enforcer-api3.5.0/enforcer-rules3.5.0/maven-resolver-util1.4.1/commons-lang3:3.14.0/commons-codec1.17.0/commons-io2.16.1/bsh2.0b6（原log完整保留）。环境gap准确归属，停止同因重复，不改POM/metadata/制品、不下载。当前有限修候选冻结待原quality复审，startup仍未开展，T4active/full仍必须后续实际运行。
>
> qualityd0e有限等价delta最终Accept，原Revise保留，旧E2E行为证据仍属改前SHA。环境归因更正：quality-narrow Central TLS；quality-e2e-compile代理127.0.0.1:7890拒绝连接（不同原因）；offline缺8项enforcer依赖。当前env无已知proxy来源、无7890listener、私有JRaft隔离repo坐标亦未见，根因未证实，不能根据目录mtime推定外部清理。短报告 `/tmp/bolt-t4-maven-environment/RESULT.md`，不重复同因Maven。按既有精确三startup类实施，编译/启动均标环境阻塞，不能声称通过。
>
> 历史三consumer startup运行前候选（已被最终4tests结果取代）：Gateway去掉fakeInvoker/noopNetty，Account去掉mockInvoker/mockBrokerClient，Message不再Import旧TestCacheConfiguration中的fakeBroker/业务noop，三者显式bolt-dev/synthetic、正式controlledclient与SDK装配断言。仅外部认证/发现/持久层替换；Gateway正式WsAuthenticationManager仅其AccountService Facade为mock，Message正式SocialAdapter仅SocialService Facade为mock（使用TestBean精准替换装配，不mock业务adapter），Redisconfirmablestore替换外部存储边界。环境最早enforcer缺件未解除，无新网络前提，不重复同因Maven；不得声称startup通过。精确三文件before/diff/SHA见 `/tmp/bolt-t4-consumer-startup/`，后续首条必须focused三startup类、不能直接宽gate。
>
> 三startup spec `efcdcc3d` Revise 确认构造前发现候选缺失（不是注册retry）：BrokerClient构造即getInstances并与显式配对求交。仅三测试内部TestConfiguration新增pre-stubbed DiscoveryClient Bean（返回前准备matching service/instance/loopback/port），Account删除空MockitoBean；外部composite discovery按已接受E2E准确exclude，避免双primary。Gateway broker-test/127.0.0.1:12200，Account/Message broker-local-1/127.0.0.1:18020，与各正式profile配对一致。无BeforeEach晚stub、不mockSDK/invoker/认证、不伪注册RPC，真实Broker不可用仍按正式路径处理。原候选/Revise保留，revised.diff/revised.sha见 `/tmp/bolt-t4-consumer-startup/`；环境gap未变，不重复Maven、不补XML，当前仅source候选待原spec复审及quality，T4仍active/full未运行。
>
> 三startup原构造前发现候选source finding已闭，source quality `20d1` Revise仅确定性：Account/Message正式dev profile读取环境可改endpoint/nodeId，而fixture固定。当前仅两测试高优先级@SpringBootTest properties明确固定address=127.0.0.1:18020及expected-peer-identity=spiffe://im-chat/broker/broker-local-1（按正式YAML namespace核对），与preconstructor发现Bean匹配，WS原已固定不动。质量候选diff/SHA `/tmp/bolt-t4-consumer-startup/quality-revise.*`，不改生产profile/SDK，不因环境未变重复Maven，无新报告；compile/runtime仍blocked，原Revise/缺件历史保留，当前待原20d1复审。
>
> ### 历史恢复状态（startup source Review 收口）
>
> 历史运行前三startup审查：spec `efcdcc3d` 原构造前发现候选source finding已闭、source符合；quality `20d1` source-level最终Accept，原Revise及有限修正记录保留。该结论仅源码层，不替代编译/运行：三startup尚未编译、尚未运行，当前earliest enforcer依赖解析环境blocked，私有JRaft当前缺件为后续风险。T4仍唯一active，T5/T6 not_started，无source writer/Maven；待合法network及原批准artifact恢复后先执行已登记三startup focused，不直接宽gate、不作无变化同因retry。范围/命令/设计/source均不变，full仍待T6冻结后实际验收，不标T4完成。
>
> ### 当前恢复状态（D14 用户已批准，独立设计 Accept，有限测试实施，T4 Block）
>
> 当前以顶部 D14 已获独立设计 Accept 的有限测试实施为恢复入口；D13 未批准候选已被取代，不实施。以下运行窗口及其后的运行前历史保留原归属，不是继续等待 secret 批准或重跑旧契约的指令。
>
> 历史有界恢复（原窗口保留，非当前startup状态）：`2026-10-23T07:18:33Z–07:27:05Z` 单次 online 窄 Maven 实际 exit1、512.55s（900s 上限，非超时）；当前 im-bolt 已编译，三个轮换方法实际执行，3 tests / 2 failures / 0 errors / 0 skipped。`liveStoreReplacementKeepsOldMaterialOnExistingAndNewChannels` 在 line280 新 Channel 客户端 local encoded 证书比较得到真实 RED：预期 old CA，实际 new CA；此前旧连接及双端 Channel ID 更换断言已通过，但首个证书断言失败，不能宣称已比较新 Channel 服务端全部数组。`restartedEndpointsPresentReplacementMaterial` 实际通过；CA overlap 在最后撤旧 CA 的 line335→350 缺少原生 SSLHandshakeException，实际 cause 为 SSLException: Bolt session certificate array missing，单独归为取证 API gap，不计已证明 TLS 拒绝。普通 im-bolt 依赖已足以运行（本轮隔离仓库有 Maven 写入，-q 未给出逐项下载进度，不能据此逐项证明远端下载）；私有 JRaft jar/POM 仍缺，仅为后续 owning 模块风险，不阻挡本次 im-bolt。精确命令、原始 XML/mtime/SHA、源码指纹及比较见 `/tmp/bolt-t4-recovery-attempt/`。无生产改动、无 secret copy 授权，不重跑同因失败；T4 仍 active/Block，T5/T6 not_started，三 startup 尚未运行。
>
> 下段为本次运行前历史 source 恢复记录，尚未运行表述已被上述实际窗口取代。
>
> 历史运行前恢复说明（已由顶部 D14 恢复入口取代）：当时不再仅恢复三 startup。预审全文原样保存在 `/tmp/bolt-t4-exit-preaudit.md`。当前 source Review：spec `b183` 最后 SPEC delta 无 must、最终 source/spec **Accept**；quality `efa22cba` 最终源码 **Accept**。原 listener 归属及异步 CONNECT teardown race 两次 Block 保留为历史；最终策略已删除 CONNECT observer/promise，仅从实际失败 cause 链要求原生 SSLHandshakeException。源码保留 native handshakeFuture cause 链不保证每个方向 alert/close 都呈现此类型；runtime 若未呈现必须记录取证 API gap，不退回 AUTH/timeout。三个轮换候选尚未编译、尚未运行，neither RED nor GREEN。三 startup 未运行、earliest Enforcer 依赖环境阻塞及后续批准 m2 私有制品缺件不变；T4 整体预审 0377477c Block 未解除。生产材料修复未授权，等待真实 RED 和设计 Review，不批准 secret copy。T4 唯一 active，T5/T6 not_started；writer 停止，由父继续有界环境恢复监督，无环境新前提不重复构建。 证据 `/tmp/bolt-t4-rotation/quality-revise.diff`、`quality-revise.sha`；本轮仅状态同步，未改测试、生产、设计、命令或范围。
>
> 历史轮换 source/spec `b183` **Block** 及当时修订（已由最终 source Accept 关闭）：fixture 原 `client.addConnectionEventProcessor` 注册到 RpcClient 自有 listener，而受控 manager 使用另一份 listener，`connectedSsl` 会等待超时，不能作为 TLS 拒绝证据。当前仅修本测试 RotationEndpoint：client.init 后、首连接前，将实际 client.getConnectionManager() 返回的 owned manager 转为公开 DefaultConnectionManager，经公开 getConnectionEventListener().addConnectionEventProcessor 注册；未新建 manager/listener，native SslHandler handshakeFuture 断言不变。ConnectionManager 接口没有此 getter，DefaultConnectionManager 的 public getter 及 listener 的 public 注册 API 已只读核对原生字节码。精确 `/tmp/bolt-t4-rotation/spec-revise.diff`、`spec-revise.sha`；当时候选待原 b183 复审再 quality，环境未变无 Maven/keytool/网络，仍未编译/未运行，neither RED nor GREEN。无生产修复授权，T4 active，T5/T6 not_started。
>
> 历史 Quality `efa22cba` **Block** 唯一异步 CONNECT observer teardown race 及当时修订（已由最终 source Accept 关闭）。本次代码前精确范围仍仅 `BoltAuthenticatedTlsTest.java#rejectsAuthority` / `RotationEndpoint`：当前正式 client/server 都 final 且固定 option，受控 factory final/package-private，无已有 CandidateObserver 或可注入的公开 pipeline hook；既有 support 的 EXTENDED_NETTY_CHANNEL_HANDLER 只适用于其独立原生 RpcClient/RpcServer，不能穿过正式受控 option 冻结。不新增 seam/反射/复制 initializer。选择更窄公开失败 cause 取证：删除异步 pipeline observer，CA 拒绝要求调用失败 cause 链包含原生 SSLHandshakeException，不能仅 AUTH 分类或 wrapper SSLException。BoltCertificateValidity.authenticate 在 handshakeFuture 失败后以 SSLException(cause=handshakeFuture.cause()) 包装，factory/manager 保留 cause 链。若运行时对端拒绝只留下 channel-close 且丢失原生原因，本断言必须失败并记录 API/证据缺口，不退回 timeout/分类假绿。原 b183 listener 归属 finding 已关闭的历史保留；最终 source/spec 与 quality 均 Accept，仍未运行、neither RED nor GREEN。
>
> ### D14 设计 Accept 后有限测试组（当前）
>
> 独立设计 `c80596b3` 最终 Accept、无 remaining；用户批准 D14，不实施 D13。唯一代码路径 `im-plugin/im-bolt/src/test/java/com/co/kc/imchat/plugin/bolt/BoltAuthenticatedTlsTest.java`：第一方法改名 `atomicStoreReplacementPreservesExistingSessionsAndNativeNewChannelsReadReplacement`，原子发布完整 store、验证旧连接双端 local/peer 数组与 session 保持、manager remove/双端 closeFuture、新物理 Channel 双端 ID/数组取得新材料；此为该 fixture 的 native 行为验证，不承诺任意热更新。restart 方法保留。CA 方法保留原生 SSLHandshakeException 证据标准；先最窄静态诊断，公开 API 无法取证则明确 gap，不改 AUTH/timeout 假绿、不增生产 seam/reflection/initializer copy。
>
> - [x] 冻结源码/本计划/PROGRESS 后运行一次 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am '-Dtest=BoltAuthenticatedTlsTest#atomicStoreReplacementPreservesExistingSessionsAndNativeNewChannelsReadReplacement+restartedEndpointsPresentReplacementMaterial+caOverlapRestartSequenceAllowsNewAndRejectsRetiredAuthority' -Dsurefire.failIfNoSpecifiedTests=false test`，独立 900s/no 并发；im-bolt 不依赖私有 JRaft，不据其缺件阻挡本命令。
> - [x] 保存 `/tmp/bolt-t4-d14-rotation/` 精确 before/diff/SHA、命令/exit/窗口、原 XML mtime/SHA 与按方法结果；两正例 GREEN 不替代 CA，API gap 单独保留，失败无新前提不宽跑。构建中不改 Git 输入，结束后才状态批次。
> - [ ] 冻结交父独立 spec 再 quality Review；CA 如需新准确 test-only 入口先有限 Review，重大 contract 改变升级父。暂不跑 startup/full/HA，不自动续期 Loop#3，不提交/部署，T4 active/Block 保持。
>
> 本轮结果：`2026-10-23T08:07:11.190826Z–08:07:27.385560Z`，16.19s/exit1/非超时，3 tests/1 failure/0 errors/0 skipped。新 native 原子交付方法与 restart 实际 GREEN（含双端完整 session 数组），CA 最终撤旧首个 negative line341→356 仍为客户端 local 数组缺失 API gap，后一方向未执行；不是整体 GREEN。原 3/2 RED/原 SHA/XML/窗口保留，现 XML/SHA/mtime 与冻结输入核对见 `/tmp/bolt-t4-d14-rotation/RESULT.md`。
>
> CA 只读结论：公开 server CONNECT dispatcher 仍异步，handler teardown 可先于 callback，不能重做历史 observer race。未修改 CA 源码或弱化断言，停止同因重跑。标准 JSSE SSLSocket → 真实 controlled server 的 native negative probe 仅为父 spec mini-Review 文本输入，成功链仍须真实 controlled pair/D10；无实际发送 old CA 证据不能将“无证书拒绝”当撤信任证明，不得自定义/强制 KeyManager 或换材料语义。父 mini-Review 前不实施该入口；T4 active/Block 与资源/三 startup/完整验收义务不变。
>
> ### CA 撤旧取证有限组（a00b284c source-level Accept，当前）
>
> 唯一代码范围 `im-plugin/im-bolt/src/test/java/com/co/kc/imchat/plugin/bolt/BoltAuthenticatedTlsTest.java`：两个 CA 同 Subject DN、不同 key/cert，原 store/alias 保持独立；补同 DN/异 key/encoded、各叶本 CA 验签成功与另 CA 失败、dual 两个独立 CA、new-only 无 old trust、每 store 唯一 leaf private key 断言。正式 controlled pair 成功阶段及双端数组保持；最终两个 negative 用 assertAll 确保均执行，不让首失败遮后方向。禁止强制 alias、custom KeyManager/TrustManager/provider、reflection、CONNECT observer 或生产 seam。
>
> - [x] 构建前冻结本组 source/plan/PROGRESS SHA，运行一次 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am '-Dtest=BoltAuthenticatedTlsTest#caOverlapRestartSequenceAllowsNewAndRejectsRetiredAuthority' -Dsurefire.failIfNoSpecifiedTests=false test`，独立900s/no并发；证据 `/tmp/bolt-t4-ca-retirement/`。
> - [x] 按实际 cause 分方向记录结果，旧3/1报告保留；如仅 server-reject 证据仍缺，可按 a00b284c 已 source-level 接受的标准 JSSE SSLSocket→真实 controlled server negative probe 在本文件补充，先登记准确 helper/命令，不改 provider/manager/alias。probe 必须保存实际 local 旧链并消费原生 SSLHandshakeException，不把无证书/EOF/timeout 当撤旧证明；成功链仍正式 controlled pair+D10。
> - [x] 最终 exact diff/SHA/XML/mtime/窗口冻结交父新 spec 再 quality Review（7c6fa2da/2f8888cd 均Accept）；不是 T4 passing，不跑 startup/full/HA、不提交部署或自动续期Loop。
>
> 同 DN 首轮实际 CA focused：29.36s/exit1，1 test/1 failure/0 errors/skip，材料断言与成功阶段完成，assertAll 两撤旧方向均执行；仅 server 拒旧 client 的 controlled caller 原因为 ConnectionClosedException，client 拒旧 server 方向已满足 native cause。证据保留 `attempt1/`。按已 source-level Accept 策略，新增本文件 `rejectsPresentedClientAuthority`：先保留 controlled pair 失败/零 handler，再标准 KeyManagerFactory+PKIX TrustManagerFactory（不包装、不强制alias）构造 TLS SSLSocket 连接真实 controlled server，connect/read 均3000ms；handshake 返回后比较本次 local/peer 全数组，再 read 必须抛原生 SSLHandshakeException。提前握手失败、无 actual旧链、EOF/timeout/普通SSLException均不能通过。反方向仍原 controlled cause 断言。
>
> - [x] 完整有限组三方法最终 focused 一次：`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am '-Dtest=BoltAuthenticatedTlsTest#atomicStoreReplacementPreservesExistingSessionsAndNativeNewChannelsReadReplacement+restartedEndpointsPresentReplacementMaterial+caOverlapRestartSequenceAllowsNewAndRejectsRetiredAuthority' -Dsurefire.failIfNoSpecifiedTests=false test`，900s/no并发，先冻结SHA，保留 attempt1，按实际报告归属。
>
> CA 有限组最终机器结果：`2026-10-23T08:15:00.930787Z–08:15:28.576155Z`、27.65s/exit0/非超时，三方法3 tests/0 failures/0 errors/0 skipped；输入核对changed=[]。同 DN/异 key 与双信任/new-only/唯一private leaf材料断言通过，正式 controlled pair 成功阶段与全数组通过。撤旧 server 拒旧 client：controlled caller失败/零handler不充当原生证明，另以标准未包装KMF/PKIX TMF SSLSocket→真实controlledserver，startHandshake后实际local旧链/peer新链全数组比较，再read原生SSLHandshakeException；反方向仍原controlledclient native cause断言，assertAll两方向均执行。不是 forcedalias/customprovider/manager，不改变D10/D11或业务授权。
>
> 当前轮换有限组机器 gap 已收敛，待新 spec/quality Review；T4 整体仍 active/Block，资源整体退出、三startup/full/真实HA未验收，不自动passing。`/tmp/bolt-t4-ca-retirement/` 保存最终XML/mtime/SHA/commands/input；attempt1首轮1failure、此前3/1和旧3/2 RED原窗口/指纹不改写。构建后才本状态批次。
>
> ### CA 双 Accept 后回归/三 startup（当前）
>
> CA spec7c6fa2da、quality2f8888cd Accept/findings none，仅有限组关闭，T4 active/Block。
> - [x] 先单独1200s运行 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test`，无并发，运行前冻结输入，记录 `/tmp/bolt-t4-post-ca-regression/` fresh XML/mtime/SHA/exit；失败先窄归因。
> - [x] 模块实际通过后900s运行 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-gateway/im-ws-gateway/im-ws-gateway-server,im-service/im-account/im-account-server,im-service/im-message/im-message-server -am -Dtest=ImWsGatewayApplicationTest,ImAccountApplicationTest,ImMessageApplicationTests -Dsurefire.failIfNoSpecifiedTests=false test`，记录 `/tmp/bolt-t4-consumer-startup/runtime/`。检查owning依赖链及批准私有制品，不把缺jar静态事实替代真实解析结果，不改GAV/metadata/mirror。
> - [ ] 每条结束后才状态批次；如startup源码/Bean失败，仅既定三测试最窄修订，仍保正式controlled SDK/lifecycle及业务Bean，交新spec/quality与完整T4退出Review。不自动T4passing，不跑full/HA/提交部署，不续期Loop。
>
> 模块实际结果：258.86s/exit0，27 fresh XML，-am299 tests全零，其中im-bolt262全零，input changed=[]。原focused/失败报告不覆盖；完整证据 `/tmp/bolt-t4-post-ca-regression/`。批准私有JRaft目录仍不存在；三startup直接POM及SDK/identity链未见im-raft/server依赖，不静态泛化缺件，继续既定三startup真实focused一次。T4仍active/Block，不直接full。
>
> 三 startup 首次实际运行300.36s/exit1：Gateway1/Account2通过，Message1 context error；真实根因为已排除DataSource时真实DbImGroupChatService构造需要SqlSessionFactory，原fixture仅替代Mapper，缺外部DB会话Bean。非依赖/私有JRaft阻塞，源码与Bean已实际运行，原fresh报告保存runtime/attempt1。最窄修订仅既定 `im-service/im-message/im-message-server/src/test/java/com/co/kc/imchat/service/message/ImMessageApplicationTests.java` 增加@MockitoBean SqlSessionFactory，继续保真实Db service/repository/application/SDK/controlledclient，不关闭业务Bean或伪transport。先Message owning focused `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-service/im-message/im-message-server -am -Dtest=ImMessageApplicationTests -Dsurefire.failIfNoSpecifiedTests=false test`，900s；通过后最终三startup既定同命令，先冻结输入、不during状态写。修订交父sourceReview，不自动T4关闭。
>
> Message最窄focused补factory后21.51s/exit1，1error，前一factory缺失已关闭；新earliest为真实AppServiceBeans组装TransactionTemplate缺PlatformTransactionManager（DataSource已显式排除）。DbImGroupChatService/DbImGroupInboxMessageService构造仅保存factory，不openSession；TransactionTemplate构造仅保存transaction manager。仍同一Message startup文件补外部DB事务边界@MockitoBean PlatformTransactionManager，context断言verifyNoInteractions两外部DB依赖，不mockbusiness、不补无意义Configuration或关闭Bean。原focused失败保message-focused/，新owning运行message-focused-revised/；是新根因修订不是无变化重跑。
>
> Message第二次focused17.32s/exit1，事务manager缺失已关闭，新earliest为DistributedLockTemplate缺失：ImLockAutoConfiguration的@ConditionalOnBean(RedissonClient)在mockoverride注册前判定，不能依赖晚mock使其重评。仍仅同Message startup DiscoveryFixture显式装配真实DistributedLockTemplate(new RedissonLockClient(既有mock RedissonClient))，不mockbusiness/lock模板、不加no-op实现；这是外部Redis client替代边界下真实通用锁装配。原message-focused-revised保留，新owning运行message-focused-lock/，不跳过先层。
>
> 最终三startup机器结果：Message owning最后17.59s/exit0、1test全零后，不改冻结输入，最终三startup命令 `2026-10-23T08:36:35.940875Z–08:37:12.704359Z`、36.76s/exit0、Gateway1+Account2+Message1=4tests全零，3freshXML且input changed=[]。Message SqlSessionFactory/transaction manager context verifyNoInteractions通过；真实DistributedLockTemplate+RedissonLockClient只替代外部Redis，业务Bean/正式SDK/controlledtransport保留。原4tests1error、factory修后事务manager缺失、事务修后锁模板缺失三份失败记录保留，各为实际新根因，不计依赖失败或同因盲跑。
>
> 当前本组冻结待新spec/quality及T4完整退出Review，不自动T4passing/T5active；私有JRaft目录仍缺仅后续Broker/HA/full风险，三startup已经实际解析/编译/运行不再被该私件泛阻。`/tmp/bolt-t4-consumer-startup/runtime/RESULT.md`列exactscope/fresh报告/状态批次，`/tmp/bolt-t4-post-ca-regression/RESULT.md`列完整模块证据。未执行startup宽gate/full/HA，未改生产/依赖/脚本、未提交部署。
>
> ### T5 第一有限组：Bolt invocation 架构所有权边界（当前）
>
> 仅新增既定四文件：`im-test/im-architecture-test/src/test/java/com/co/kc/imchat/architecture/BoltInvocationBoundaryTest.java`、同src/test/java下 `fixture/bolt/AddressInvocationBypass.java`、`DirectBusinessRpcBypass.java`、`ControlledProtocolWrite.java`。测试规则按调用API与包所有权，不做类名豁免，不泛禁通用非Bolt transport。D11同JVM可信代码边界，不承诺运行期sandbox，raw witness保持counterevidence。暂不改scripts/Harness/稳定文档。先聚焦命令正常解析一次；若私有JRaft阻断则source候选待Review，未执行不称RED/GREEN，不改制品/mirror/GAV/metadata。
> - [ ] fixture正反与实际规则候选，聚焦 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-test/im-architecture-test -am -Dtest=BoltInvocationBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`，900s/no并发，先冻结输入。RED须missing-rule真实failure，编译/缺件不算RED。
> - [ ] 条件允许取得RED→GREEN；否则明确首层归因，仅sourceReview。冻结 `/tmp/bolt-t5-invocation-boundary/` exactscope/diff/SHA/commands/exit/XML后交spec再quality，不提前T5passing/full。
>
> T5首次focused实际29.70s/exit1，0freshXML，earliest im-raft依赖缺 `com.co.kc.thirdparty.sofa:jraft-core:jar:1.4.1-imchat.1`，正常nexus-knight-repository解析失败（完整cause见run/maven.log）。未达架构testCompile，不计RED/GREEN，四source仅候选待specsource-review；无同因重跑或GAV/mirror/metadata/安装改动。T4passing不回退，T5唯一active，后续运行需恢复批准jar/POM。
>
> T5 source SPEC af6d9e74 Block三项必须修：owner提前跳过地址契约、假codec负例及constructor漏检、fixture强制consumer掩盖WS Bolt业务handler漏检。有限修订精确调整fixture位置以实际生产包判定：原 `fixture/bolt/AddressInvocationBypass.java` 移至 `com/co/kc/imchat/plugin/bolt/spi/fixture/AddressInvocationBypass.java`；原 `DirectBusinessRpcBypass.java` 移至 `com/co/kc/imchat/gateway/ws/handler/fixture/DirectBusinessRpcBypass.java`；原 `ControlledProtocolWrite.java` 移至 `com/co/kc/imchat/plugin/bolt/core/fixture/ControlledProtocolWrite.java`；最小新增 `com/co/kc/imchat/gateway/ws/server/handler/fixture/WebSocketProtocolWrite.java` 作为合法外部WS raw写正例。均位于同 architecture test src/test/java。理由：不同真实owner/consumer/外部WS包需独立Java compilation unit，不能用fixture强制flag或为fixture名称加predicate例外；修正原sourcepath/package不一致。仍同一个BoltInvocationBoundaryTest规则文件，无新框架/生产/依赖/scripts/稳定文档。实际WS Bolt处理器归gateway.ws.handler（与外部WSserver.handler不同），也覆盖handler.bolt后代；正反使用完全相同production predicate。已javap确认DefaultConnectionFactory(Codec,ChannelHandler,ChannelHandler,Configuration)与NETTY_EPOLL_SWITCH原生签名。私件实际block不变，本轮不重跑Maven、不声称编译或RED/GREEN。
>
>
> ## T5 稳定文档与影响映射有限组（当前）
>
> 唯一 writer，前 worker004 停止；不修改 Java/checker、不重开 T1–T4、不提前 T5 passing/T6 active。架构组 source spec af6d9e74 与 quality109 Accept 不代表编译或运行通过。沿用实际私有 GAV 缺件归因，无新前提不重复 Maven、architecture/quick；不改 POM/version/mirror/metadata，不在外仓 build。
>
> 本组精确修改范围：根 `ARCHITECTURE.md`；`im-broker/ARCHITECTURE.md`、`im-gateway/ARCHITECTURE.md`、`im-service/im-message/ARCHITECTURE.md`；B/G/S/W/P/V/A/M+`README.md`；`im-test/im-routing-ha/README.md`、`im-test/im-e2e-test/README.md`、`deploy/local/README.md`；`docs/SECURITY.md`、`docs/RELIABILITY.md`、`docs/references/CODING_GUIDE.md`、`docs/references/HARNESS_GUIDE.md`；`scripts/affected-modules.sh`、`scripts/test-harness.sh`，以及本计划和 PROGRESS 状态。矩阵按当前单一 owner 规则最小增补 `docs/references/HARNESS_RULE_MATRIX.md`，不在 Guide 复制矩阵；这是现有规则登记文件，不新增 checker/框架。批准设计 D14 和设计索引只读，不重写历史批准或日期。
>
> - [x] `./scripts/verify.sh readiness` 实际 exit0；按源码核对 typed 目标、实际 mode/Invoker 同源、bootstrap/seed 配对、D10/D11/D12/D14、双预算/资源和后台禁用，更新上述稳定契约，无新容量/TPS/hard deadline/热加载保证。
> - [x] 在 `scripts/test-harness.sh` 先新增单路径注入映射 fixture；提取同一个 assert_impact 与 fixture 执行，原映射实际 exit1（认证 client 缺四消费方模块），修 `affected-modules.sh` 后同 fixture exit0。只是纯 shell RED/GREEN，不是架构测试 RED/GREEN。
> - [x] 已执行 `bash -n scripts/affected-modules.sh scripts/test-harness.sh`、`git diff --check`、`bash scripts/check-drift.sh`、`bash scripts/test-harness.sh` 与准确路径/链接核对并保存原始 exit/log（执行完成不表示全部通过）。语法/diff/109 个本地路径链接/bolt-dev 合同 exit0；clean 因同一 drift exit1。首次 drift exit1 为原计划历史未勾步骤/Loop 标题归属，先归因不改 completed 事实。首次完整 Harness 被 240s 工具上限终止，已到 Local Compose 子自测通过，非完整通过；一次有界 trace 诊断实际 114.64s/exit1，最早 `test-load-test-harness.sh:592` 资源采样少于四条，未到本组 assert_impact。该子自测与本组无 diff，不扩大修复或同因重跑。
> - [ ] 冻结 `/tmp/bolt-t5-stable-contracts/` 的 before.diff、files.txt、source.diff、final.sha、commands.log、exits、RESULT.md；交父独立 spec 再 quality。所有未运行/blocked 门禁如实保留，不 full/生产/提交，不删除未知 .pi。
>
> T5 稳定契约 spec `994cd31e` 首轮 Revise 仅一项：HA README 误称 BoltControlledServer；已按实际 AutomaticFailureTlsHaTest 的 BoltAuthenticatedClient、原生 RpcServer TLS/client-auth 与正式 Processor/身份成员链修订，不改 HA 实现，不宣称 Spring 受控服务端启动契约已验证。修订候选待原 spec 复审，之后 fresh quality；原 drift/clean exit1、Harness 首次 240s 工具超时及有界诊断采样不足 exit1 全部保留，不因局部 shell GREEN 称 Harness 全通过。本轮仅该段与必要状态，无 Maven/shell 门禁重跑，T5 active/T6 not_started。
>
> ## T5 廉价门禁归因修复有限组（当前）
>
> 稳定 24 文件组 spec `994cd31e` 最终 Accept、quality `03cdba73` Accept/findings none；完整 quality summary 已核对任务 `2026-10-23T09-28-37-445Z_01a16817-4344-70c9-8344-fcd75e5cb9e6.jsonl`。旧 Revise、drift/clean exit1、240s tool timeout 与 trace exit1 保留。T1–T4 passing，T5 唯一 active，T6 not_started；架构 source af6d9e74/quality109 双 Accept 未编译，私有 GAV 实际解析缺件无新前提，不重跑 Maven/architecture/quick/full。
>
> 精确范围：本计划、`PROGRESS.md`、`docs/exec-plans/active/README.md` 的当前状态；`scripts/test-load-test-harness.sh` 的采样 fixture；如确需沉淀测试约定，只改 `docs/references/HARNESS_GUIDE.md` 对应段落。不改生产脚本/Java/依赖/checker/scope exclusions。计划历史宽命令未经实际执行不能为消除 drift 补勾；按原证据区分已完成行为与 T6 同轮运行义务，不改历史/completed。
>
> - [x] 先 git status/readiness，核对原 RESULT/commands/exits/完整 quality summary 和最早失败日志；证据 `/tmp/bolt-t5-gate-repair/`。
> - [x] 定位采样 fixture 固定 5.5s 子进程生命周期与四条样本要求的竞态；只在现有 fake Docker/fixture 用实际采样进度终止 owned target，保留 schema/权限/单调断言。最窄提取同一 fixture 的正例及丢输出 mutation 反例均须实际运行；不得增加生产 test hook 或靠延长 sleep 通过。
> - [x] 完成最窄 fixture 后独立运行 `bash scripts/test-load-test-harness.sh`，单条预算 600s；失败先归因。独立 `bash scripts/test-harness.sh` 单条预算 1800s，实际退出前不改 Git 输入；不复制局部结果当父自测通过。
> - [x] 核对原计划未勾项与 scoped exit 原记录后，真实 `./scripts/verify.sh drift`、`./scripts/verify.sh clean` 各一次；原未执行宽命令保持未执行，无法可靠关闭的 drift 明确归因而非弱化 checker。
> - [x] 冻结 before.diff/files.txt/source.diff/final.sha/commands.log/exits/RESULT.md/logs，交父独立 spec 再 quality；保持 T5 active，不提交/部署/续期 Loop#3。
>
> 采样最窄结果：原 fixture 把 `sleep 5.5` 的目标生命周期当作四次完整 shell/Node/系统命令采集的预算；单条采样无固定耗时上限，因此调度变慢即可少于四条，不是生产采样周期算法已证明出错。现 fake Docker 第五次调用结束 owned target，Ruby 真实 wait/reap，零间隔只测完整记录而周期公式继续显式向量/mutation；同一最终提取块 positive exit0、drop-output mutation exit1（原四条断言），`bash scripts/test-load-test-harness.sh` 独立实际 exit0。原 114.64s/exit1 与父 240s 无正常 exit 保留，timeout 只能归工具预算不足/未结束，旧日志无死锁栈证据，不推定永久热点。下一步父完整自测一次 1800s 冻结窗口。
>
> 廉价组最终归因（未通过项保留）：父 `bash scripts/test-harness.sh` 单独1800s预算实际286.006s/exit124，非工具超时；输入核对 changed=[]。已越过采样，最早 `test-load-test-harness.sh:877` 的 post-restart transient-health restore fixture，stderr `im-audit-server did not become healthy within 5s`。fake Docker 首次 unhealthy inspect 后写 healthy，正式 wait 循环仍执行命令监控及 sleep2，fixture 的5秒窗口依赖宿主调度；没有本轮内部逐命令 trace，不能断言永久生产健康 bug 或某个具体命令耗时，也不把 standalone 子自测 exit0 覆盖父 exit124。原240s工具超时同样只有到 Local Compose 后日志，不能反推同一根因。停止本组，不提高健康预算/改生产等待/扩新fixture矩阵。
>
> 本轮 `./scripts/verify.sh drift` exit1、`./scripts/verify.sh clean` exit1；T2 已按原每组证据补齐，Loop#3 是历史监督小节而非任务，改四级标题；仍保留 T3/T4 未执行宽命令对应未勾步骤，checker 无修改。根因是 scoped exit 允许下一阶段与原详细任务要求原宽命令逐条完成之间的计划合同不一致；需要父明确保留宽命令要求至 T6，或基于原 scoped exit 审核有限等价替代后批准重述步骤，不能靠本 worker 机械补勾。T1–T4 scoped passing 状态按用户保留，非完整全仓验收。
>
> 源码组仅采样 fixture 逻辑改变，需本组独立 spec 再 quality；旧稳定组双 Accept 不复用为本组 Accept。architecture/quick/full仍缺批准私有JRaft真实制品；不重跑同因Maven，不改GAV/POM/repos/metadata，不归档/提交/部署/续期监督。精确证据 `/tmp/bolt-t5-gate-repair/`，标准ignored Harness输出可留，未知.pi/外仓/历史材料未清理。
>
> ### b56ec434 混合 checkbox 事实修订（待原 spec 复审）
>
> 原 spec b56ec434 Revise 仅计划事实表述，采样 fixture 逻辑可接受。已完整读取任务 `2026-10-23T09-49-38-243Z_01a1682a-8043-70c9-8344-fcde113fdfe6.jsonl`。本次仅拆开 T2 首项/末项及 T3 首项：已完成行为记录精确原命令与证据，原未执行的三类合并 selector/顺序及五模块 RED 义务保留 `[ ]`，不自行批准等价替代。先前「T2已补齐」仅属当时回填结论，现由本修订纠正；T1–T4 scoped passing 历史保持，不表示全部原命令已运行。T5 唯一 active，T6 not_started；drift 暂未解决且本轮不重跑，预计新增明确保留的 T2 合同未勾项也会被 checker 拒绝。健康 fixture 另组处理，此时不追加。
>
> 仅本计划与 PROGRESS 状态变化，采样脚本/Harness Guide/索引及其他所有输入均不改；无测试、Maven、廉价门禁重跑或新的 runtime 结论。`/tmp/bolt-t5-gate-repair/spec-revise.diff`、`spec-revise.sha` 与 before/after 指纹保留增量，原 source.diff/final.sha 留样 pre-spec-*，更新候选交父恢复原 spec Review 后再 quality。
>
> ## T5 health fixture 有限组（当前）
>
> 前采样组 spec b56ec434/quality f8c03710 最终 Accept/no findings，原父 Harness286.006s/exit124、drift/clean exit1 保留。T1–T4 scoped passing 不表示全部原命令已执行；T5 唯一 active，T6 not_started。计划合同冲突独立后续 Review，本组不批准等价替代。私有JRaft无新前提，不运行Maven。
>
> 精确范围：`scripts/test-load-test-harness.sh` 的 post-restart transient health fixture 及其同文件 fake Docker；本计划、`PROGRESS.md` 状态；如沉淀可复用约定仅 `docs/references/HARNESS_GUIDE.md`。不改 `scripts/load_test.sh`/生产健康timeout/算法，不加生产hook或新框架，不动依赖/未知.pi/外仓。
>
> - [x] git status/readiness，读取原parent-window/log与真实wait_for_service_health及fake Docker；旧5秒内两次检查依赖宿主调度，历史失败仅作为regression witness，不伪造新确定性旧RED。
> - [x] 在同文件fake Docker记录服务/重启/inspect序列；正例见证每服务重启后首次unhealthy、下一healthy且restore BASE_READY；always-unhealthy真实runner有界失败，核对exit124/具体消息/RESTORING。正例时间预算只防挂，不把固定5秒当性能保证。最窄提取原exact block测试与临时忽略healthy mutant反例，生产源码不留mutation。
> - [x] 先独立 `bash scripts/test-load-test-harness.sh`（600s），通过后冻结完整输入，独立 `bash scripts/test-harness.sh`（单命令1800s）。失败先归因，不用子成功覆盖父失败，不无限吞入新任务；运行中不写状态。
> - [x] 冻结 `/tmp/bolt-t5-health-fixture/` before.diff/files.txt/source.diff/final.sha/commands/exits/logs/RESULT.md交父spec再quality；保持原失败与合同未勾项，T5仍active，无提交部署/Loop3续期。
>
> health组实际结果：最窄提取同一fake setup与正负block最终exit0；第一次负例未重置前轮healthy状态误走restore fast path，开发失败exit1已保留，补测试初始unhealthy后通过。永久unhealthy真实runner检查exit124、`im-audit-server did not become healthy within 3s`与RESTORING；正例每management服务重启后恰两次inspect（unhealthy→healthy）及BASE_READY。忽略healthy的临时runner mutant最终同60s正例预算exit124，实际sequence有1 unhealthy、2至24 healthy，说明失败确因忽略healthy而非没机会观察；此前4s缩短预算mutant只作初步诊断，不作为主要反证。生产源码不变，无旧fixture新确定性RED主张。
>
> 独立load-test子自测exit0；父Harness单命令1800s预算实际488.167s/exit1、输入changed=[]。本轮已越过health到routing-capacity Harness self-test passed，后继脚本是test-harness.sh的空格路径真实check-drift静默调用；其已有计划T2/T3/T4原命令checkbox合同未关闭，未再声称健康失败或全父通过。无内部trace故不伪造该静默调用的逐条新diagnostic；下一合同组可据独立matrix做exactdiff Review，本组不补勾。父日志第17行明确 `Startup Harness tests passed.`，只证明本轮startup shell子自测实际成功，不等于Maven startup或父整体成功。原286.006/exit124、240s工具超时和drift/clean exit1均保留。无Maven/生产改/新cleanup，当前source冻结交父spec再quality，T5active/T6not_started。

### 历史根进度留样（非当前指令）

> # 项目进度
>
> - 当前恢复点：T4整体独立exit reviewer `a21eb322` 最终 scoped Accept/noCritical，允许进入T5；全文 `/tmp/bolt-t4-exit-final.md`。T1–T4 passing，T5唯一active/WIP=1，T6 not_started。startup spec996/quality859 Accept；CA7c6fa2da/2f8888cd Accept；模块-am299/其中im-bolt262与startup4各归独立窗口，不拼full。历史0377477c Block经用户批准D14及实际CA/startup证据关闭，原RED/失败/窗口保留。此为T4阶段退出，不是full/生产/T6 Accept，不归档不提交。Loop#3 expired不自动续期。当前 T5 架构源码组已 source spec af6d9e74/quality109 Accept（尚未编译）；稳定 24 文件组 spec994cd31e/quality03cdba73 最终双 Accept/no findings；现仅 T5 廉价门禁归因修复。原 drift/clean exit1、父自测 240s tool timeout、114.64s diagnostic exit1 保留，不视为通过。
>
> - 历史 D14 有限测试首轮结果（已由后续CA及模块/三startup结果取代）：一次 focused Maven `2026-10-23T08:07:11.190826Z–08:07:27.385560Z`，16.19s、exit1 非超时，3 tests/1 failure/0 errors/0 skipped。新原子完整交付用例实际通过：旧双端 session/材料保持、新物理双端 Channel ID 改变且 local/peer 全数组为新材料；restart 方法实际通过。唯一 CA gap 仍是最终撤旧 server 拒旧 client 的 line341→356，D10 客户端 local 数组缺失先拒，不能宣称 native server 拒绝已证明；后一方向未执行。server CONNECT 公开 callback 仍异步、重现历史 teardown race，不实施同一 observer。已停止 CA 源改，冻结 `/tmp/bolt-t4-d14-rotation/` 交父 spec 再 quality；标准 JSSE SSLSocket negative probe 仅文本提案，须父 spec mini-Review，不能改变 old CA 语义或用强制 KeyManager。旧 3/2 RED 原报告不改写；T4 active/Block、startup/full/HA 未运行。
>
> - CA 本组实施前状态（已由上方最终结果取代）：独立 SPEC `a00b284c` Accept D14 有限变更/两 GREEN 与 CA gap 证据，并 source-level Accept 最窄同 Subject DN、不同密钥 CA fixture 策略。现仅改既有 `BoltAuthenticatedTlsTest.java` 的材料断言与最终双方向 assertAll；先一次 focused CA 运行，不新增 CONNECT observer、强制 alias、自定义 TLS manager/provider 或生产 seam。旧 3/1 failure 证据保留；本轮 source-level Accept 不是运行通过或 T4 Accept。
>
> - CA 有限组最终实际收敛：`2026-10-23T08:15:00.930787Z–08:15:28.576155Z` 最终三方法 focused 27.65s、exit0、3 tests/0 failures/0 errors/0 skipped，冻结输入 changed=[]。同 Subject DN/不同 key fixture 断言通过；server 拒实际旧 client 链由标准 JSSE SSLSocket 对真实 controlled server 的 actual session local/peer 全数组及后续 native SSLHandshakeException 证明，另一方向仍 controlled client native cause；最终 assertAll 两方向实际执行，CA sequence 成功阶段均正式 controlled pair/D10。仅本有限组通过，待新 spec 再 quality Review，不代表 T4/full/HA。首轮同 DN 1/1failure与此前3/1、旧3/2报告各自保留。精确证据 `/tmp/bolt-t4-ca-retirement/`；未新增生产 seam/forced alias/custom manager/provider/CONNECT observer。
>
> - 当前批准边界：只取代同进程所有新 Channel 固定旧材料保证；已有 Channel 保持 session，新 Channel 标准 TLS/严格身份/D10 gate 及 D11、统一 invoke、owner 授权、mode/namespace/mTLS/资源不变。不承诺自动热更新或任意 inplace write 一致；完整原子交付+滚动重启为运维建议，不是 only-restart 门禁。后续唯一测试代码路径 `im-plugin/im-bolt/src/test/java/com/co/kc/imchat/plugin/bolt/BoltAuthenticatedTlsTest.java` 须验证实际 client/server session 全数组与 restart，不能只删 failing assert；CA 顺序/双向原生拒绝与 API gap 定位、三 startup、资源 cleanup 和 T4 退出义务不取消。
>
> - 当前证据审查：独立证据 reviewer `cfe0699d` 已 Accept 本轮 runtime 归属与客户端新 Channel local 材料 reload 真实 RED；该 Accept 不解除 T4 Block，也不授权生产修复或新增私有文件副本。
>
> - 历史有界恢复结果（保留原窗口，非当前startup状态）：`2026-10-23T07:18:33Z–07:27:05Z` 单次 online 窄 Maven 实际 exit1、512.55s（900s 上限，非超时）；当前 im-bolt 已编译，三个轮换方法实际执行，3 tests / 2 failures / 0 errors / 0 skipped。`liveStoreReplacementKeepsOldMaterialOnExistingAndNewChannels` 在 line280 新 Channel 客户端 local encoded 证书比较得到真实 RED：预期 old CA，实际 new CA；此前旧连接及双端 Channel ID 更换断言已通过，但首个证书断言失败，不能宣称已比较新 Channel 服务端全部数组。`restartedEndpointsPresentReplacementMaterial` 实际通过；CA overlap 在最后撤旧 CA 的 line335→350 缺少原生 SSLHandshakeException，实际 cause 为 SSLException: Bolt session certificate array missing，单独归为取证 API gap，不计已证明 TLS 拒绝。普通 im-bolt 依赖已足以运行（本轮隔离仓库有 Maven 写入，-q 未给出逐项下载进度，不能据此逐项证明远端下载）；私有 JRaft jar/POM 仍缺，仅为后续 owning 模块风险，不阻挡本次 im-bolt。精确命令、原始 XML/mtime/SHA、源码指纹及比较见 `/tmp/bolt-t4-recovery-attempt/`。无生产改动、无 secret copy 授权，不重跑同因失败；T4 仍 active/Block，T5/T6 not_started，三 startup 尚未运行。
>
> - 活跃计划：[Bolt 统一 invoke](2026-10-22-bolt-unified-invoke.md)：**T1–T4 passing，T5 唯一 active，T6 not_started，WIP=1**。监督 Loop#3 已执行满 50 轮并 expired，未自动续期；后续后台监督须用户明确续期。未创建 Git 提交、未生产部署。
> - T4 已取得的局部证据：通用传输与 observer 修复已独立 Accept；owner 准入组 spec `91cedb4c`、quality `59f9d63b` Accept；profiles/部署合同组 spec `88f36f28`、quality `f7e70ae7` Accept。Docker 仅有 create/inspect/check-only 证据，不代表业务启动。
> - 真实 Gateway E2E 已执行：D12 开发目标配对及同步删除通知修复后，正式 `./scripts/verify.sh e2e` 于 `2026-10-23T04:47:25Z` 完成，exit0、1 case；覆盖握手、注册、上行/ACK、下行、session close 路由清理和重连。该组 spec `443f8df4`、quality `d0e434b7` 最终 Accept。之后三文件等价质量收尾未取得 fresh 编译；上述运行证据属于修改前 SHA，不代表当前完整工作树通过。
> - 历史三 consumer startup 运行前源码审查：spec `efcdcc3d` finding 已关闭，quality `20d1` source-level Accept；**尚未编译、尚未运行**，不是 startup 或 T4 验收通过。候选及修订证据见 `/tmp/bolt-t4-consumer-startup/`。
> - 历史运行阻塞（本次 im-bolt 恢复结果见顶部；不再泛指全部模块）：在线窄验证遇到 Central TLS 断连，另一次 E2E testCompile 遇到本地代理 `127.0.0.1:7890` 拒绝连接；offline 验证缺少八项 Enforcer 依赖，均未进入源码编译。批准隔离仓库 `/tmp/jraft-artifact-m2` 当前也未见私有 JRaft jar/POM，属于后续缺件；缺失原因未证实。归因见 `/tmp/bolt-t4-maven-environment/RESULT.md`。
> - 历史下一步（被本次恢复状态取代）：独立预审 `0377477c` 已 **Block T4**，不止 startup。当前仅实现原计划轮换 test-only 候选；恢复合法依赖网络及原批准制品后，先三 startup focused，再运行计划顶部三轮换方法，取得真实运行判定及必要设计 Review 后重新 T4 整体退出审查；随后推进 T5 架构规则/稳定文档和 T6 冻结 clean/full、真实 HA、独立 Accept 与状态迁移审查。本有限组 writer 已停止，未执行 Maven；无环境变化不重复同因构建。历史失败、超时、Block/Revise 及旧报告归属保留在计划和证据目录，不将旧报告标为 fresh。
> - 撤回记录：[原Harness平台计划](../history/2026-10-21-harness-verification-resume.md)已按用户确认移至history；保留H1–H4原检查点和H5 blocked，不补勾、不计为completed。它已被精简方案取代，不是当前实施，也不恢复阶段链、journal或复杂归档发布。
> - 当前流程：一次父自测、一次完整reactor、同轮报告/制品证明、一次真实HA；独立入口用于聚焦排查，不在同一full前后重复构建。归档按两时点Review，不改写原full时间、指纹及stale。
> - 保留风险：历史Raft首次复制偶发根因未证明永久修复；私有JRaft发布/双构建可复现性仍为后轨；Bolt统一invoke与连接层认证O1–O4已由用户确认，独立 reviewer `83f8f2a3-df78-41b` 全文/源码/精确增量 Review Approved；O1显式配对、历史O2严格路径截止（已由D10/B会话数组额外区间替换）、O3受限开发、O4无兼容及后台验证后启用均已批准，T1–T3 已 passing，剩余实施与验收由 T4–T6 继续验证；本次仅实现与隔离验收，不生产上线；原Redis容器丢失未恢复事实不因Harness清理通过消失。Audit启动证明仅限隔离基础设施的真实Spring装配，不是外部IAM/数据库/Kafka全栈证明。
> - 未创建Git提交，未删除历史成功/失败证据；原平台文件留样 `/tmp/closeout-simplify-platform-backup/`，新运行是否fresh以[Harness报告](../../../.harness/report.json)、`.harness/verifications/`及`.harness/quality/quality-snapshot.json`为准。状态迁移后原full如实stale，不重复Java/HA。
>
> - 已取代的待决策项：只读评估 `d162ba90` 未发现 Bolt 1.6.6 原生公开的固定 SSLContext/内存材料注入口；D13 私有 secret 副本候选从未批准实施。用户现在已批准 D14 沿原生加载收敛，故取消该候选，不再等待副本授权；私钥权限、同 UID/root 与崩溃残留风险没有被实现解决，原部署 secret 责任仍保留。
>
> - 历史恢复点（运行前）：当前 source Review：spec `b183` 最后 SPEC delta 无 must、最终 source/spec **Accept**；quality `efa22cba` 最终源码 **Accept**。原 listener 归属及异步 CONNECT teardown race 两次 Block 保留为历史；最终策略已删除 CONNECT observer/promise，仅从实际失败 cause 链要求原生 SSLHandshakeException。源码保留 native handshakeFuture cause 链不保证每个方向 alert/close 都呈现此类型；runtime 若未呈现必须记录取证 API gap，不退回 AUTH/timeout。三个轮换候选尚未编译、尚未运行，neither RED nor GREEN。三 startup 未运行、earliest Enforcer 依赖环境阻塞及后续批准 m2 私有制品缺件不变；T4 整体预审 0377477c Block 未解除。生产材料修复未授权，等待真实 RED 和设计 Review，不批准 secret copy。T4 唯一 active，T5/T6 not_started；writer 停止，由父继续有界环境恢复监督，无环境新前提不重复构建。 精确证据 `/tmp/bolt-t4-rotation/`。
>
> - 历史轮换 source/spec `b183` **Block** 及当时修订（已关闭）：原 fixture 观察者接在 RpcClient 自有 listener，实际受控 manager 另有 listener，导致 connectedSsl 超时而非 TLS 拒绝取证。有限修订仅在 client.init 后、首连接前，经实际 owned DefaultConnectionManager 的公开 listener API 注册观察者；保留 native handshakeFuture 断言。候选冻结 `/tmp/bolt-t4-rotation/spec-revise.diff`、`spec-revise.sha`，原 spec `b183` 已复审关闭 observer finding，source/spec Accept；独立 quality `efa22cba` 最终源码 Accept。原 Block 历史保留；未编译/未运行，neither RED nor GREEN，无 Maven/keytool/网络或生产修改。环境缺口与 T4 Block 不变。
>
> - 历史轮换 quality `efa22cba` **Block** 及当时修订（已关闭）：CONNECT 回调独立 executor 与候选 teardown 竞争，原 listener 归属 b183 finding 已关闭但未解决生命周期 race。本次仅 fixture 删除异步 pipeline observer，改检查实际调用保留的 cause 链必须含原生 SSLHandshakeException（authenticate 从失败的 native handshakeFuture.cause 包装），不接受仅 AUTH/普通 SSLException/timeout。正式 final/option 冻结装配无可注入 CandidateObserver/pipeline hook，不新增 seam；若 native cause 未保留则测试明确失败而非假绿。精确 quality-revise.diff/.sha 及 RESULT 见 `/tmp/bolt-t4-rotation/`，spec b183 最后 SPEC delta 无 must、最终 Accept，quality efa22cba 最终源码 Accept。未编译、未运行，neither RED nor GREEN，环境无变化未执行 Maven/keytool/网络。T4 Block/active、T5/T6 not_started 不变。
>
> - 历史 CA 同 DN 首轮 focused 已实际执行：1 test/1 failure，材料断言通过，最终两个方向均执行；client 拒旧 server 原生 cause 通过，server 拒旧 client 仅 controlled ConnectionClosedException，不计原生证明。现按 a00b284c source-level Accept 补同文件标准 JSSE SSLSocket native negative probe，保 actual local旧链全数组+原生 fatal alert，两方向分开取证；不自定义 manager/provider，不新增生产功能。原轮证据保留于 `/tmp/bolt-t4-ca-retirement/attempt1/`。
>
> - CA 双 Accept 后模块回归：单条 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test` 实际258.86s/exit0、27份fresh XML，-am总299 tests/0 failures/errors/skips，其中im-bolt262全零，构建输入changed=[]。证据 `/tmp/bolt-t4-post-ca-regression/`。批准私有 JRaft 目录仍不存在；已读三consumer POM及SDK/identity owning链，未以该静态缺件阻所有consumer，下一步按已登记三startup focused正常解析一次。
>
> - 三startup首次实际：300.36s/exit1，Gateway1、Account2通过；Message1 context error根因为排除外部DataSource后真实DbImGroupChatService缺SqlSessionFactory（原仅mock Mapper）。不是JRaft/Enforcer，实际fresh报告保存 `/tmp/bolt-t4-consumer-startup/runtime/attempt1/`。仅既定Message startup补外部DB SqlSessionFactory mock，保正式业务/SDK/controlled生命周期；先owning focused后最终三startup，修订待sourceReview，T4仍Block。
>
> - 历史T4退出前恢复点（已由a21eb322 scoped Accept取代）：CA有限组spec7c6fa2da/quality2f8888cd Accept；完整im-bolt实际262 tests全零（-am总299）；三startup最终同冻结输入 `2026-10-23T08:36:35.940875Z–08:37:12.704359Z`、36.76s/exit0，Gateway1+Account2+Message1=4 tests全零，3fresh XML、changed=[]。Message仅测试fixture补外部SqlSessionFactory/事务manager并verifyNoInteractions，显式真实DistributedLockTemplate+RedissonLockClient接既有mockRedis，保正式业务/SDK/controlledclient。原4tests1error及后续两次新earliestBean错误分别归档，不改旧报告。完整证据 `/tmp/bolt-t4-consumer-startup/runtime/`；待父本组新spec/quality与完整T4退出Review，T4仍唯一active/Block，T5/T6 not_started，不继续writer/full/HA/commit/deploy。
> - 当前依赖归因纠正：三startup已真实正常解析编译运行，不依赖缺失私有JRaft来启动；私有JRaftjar/POM批准目录仍missing，仅后续Broker/HA/full风险，不能沿用历史“私件阻startup/Enforcer全部blocked”作为当前事实。外仓Maven进程曾出现在ps中，未触碰/终止；本仓各命令严格串行。
>
> - T5当前实际首层阻塞：既定architecture focused正常Maven一次29.70s/exit1，im-raft依赖 `com.co.kc.thirdparty.sofa:jraft-core:jar:1.4.1-imchat.1` 无法从 `nexus-knight-repository (http://maven.aliyun.com/nexus/content/groups/public)` 解析。0fresh XML，未达新架构测试编译，neither RED nor GREEN；私件现为实际触发的架构依赖缺口，不回退T4通过事实。四source候选冻结 `/tmp/bolt-t5-invocation-boundary/` 交source spec Review；无同因重跑/改GAV/metadata/mirror/安装。继续运行需批准jar/POM位置或恢复已批准隔离制品。T4passing/T5active，T6未开始。
>
> - T5 source SPEC af6d9e74 Block三项保留；本轮有限修订：地址契约检查移至owner豁免前；真实DefaultConnectionFactory codec构造与NETTY_EPOLL_SWITCH transport分别负例；删除fixture强制consumer flag，实际gateway.ws.handler业务raw helper（无Bolt依赖）与gateway.ws.server.handler合法WS正例走完全相同production predicate。为包所有权真实化按计划移动三个fixture至actual package目录，最小新增一个WS正例；未加类名豁免/新框架。私有JRaft实际架构block不变，未编译/未跑RED/GREEN，不重跑Maven。冻结spec-revise.diff/.sha待原spec复审，T4passing/T5active。
>
> - T5 稳定契约有限组候选已落地：24 文件精确范围和只读事实源见 `/tmp/bolt-t5-stable-contracts/`；补 typed API、固定实际装配/资源双预算、D10/D11/D12/D14 与开发/生产边界，已有影响映射带入消费方 startup/behavior/E2E/architecture 模块。纯 shell 映射真实 RED exit1 → GREEN exit0；不是架构 Java RED/GREEN。readiness、bash 语法、精确路径链接与 bolt-dev 合同自测通过；drift/clean 实际 exit1 为原计划 T2/T3/T4 未勾步骤及 Loop#3 标题归属。完整 Harness 首次 240s 工具超时，唯一有界 trace 114.64s/exit1，earliest `test-load-test-harness.sh:592` 采样不足四条，未到本组影响断言；不扩大修 load-test、不改 completed 验收事实、不重复同因 Maven。候选待父 spec 再 quality，T5 唯一 active/T6 not_started，full/HA/生产/提交均未执行，Loop#3 不续期。
>
> - T5 稳定契约 spec `994cd31e` 首轮 Revise 仅一项：HA README 误称 BoltControlledServer；已按实际 AutomaticFailureTlsHaTest 的 BoltAuthenticatedClient、原生 RpcServer TLS/client-auth 与正式 Processor/身份成员链修订，不改 HA 实现，不宣称 Spring 受控服务端启动契约已验证。修订候选待原 spec 复审，之后 fresh quality；原 drift/clean exit1、Harness 首次 240s 工具超时及有界诊断采样不足 exit1 全部保留，不因局部 shell GREEN 称 Harness 全通过。本轮仅该段与必要状态，无 Maven/shell 门禁重跑，T5 active/T6 not_started。
>
> - T5 廉价门禁有限组已停止并冻结待父 spec/quality：采样 fixture 从固定5.5s生命周期改为第五次 fake stats 结束 owned target，原schema/0600/单调断言不减，正式生产采样器未改。最窄正例及丢输出反例、独立load-test子自测结果见 `/tmp/bolt-t5-gate-repair/RESULT.md`。父 Harness 仍实际 exit124（post-restart健康fixture），drift/clean仍exit1（T3/T4未执行宽命令checkbox）；不能以局部成功覆盖。T1–T4 scoped passing、T5唯一active、T6not_started 保持，私有JRaft架构编译缺口无新前提不重跑Maven，Loop#3不续期，无提交部署。
>
> - T5 廉价组 spec `b56ec434` Revise 仅混合 checkbox 事实表述；现仅计划拆开 T2 首末及 T3 首项，已完成行为对应精确原分组命令，未执行原 selector/顺序义务独立未勾，等价替代待独立合同 Review。采样源码保持，未重跑测试/Maven/drift；原失败与本组 Revise 均保留，待原 spec 复审再 quality。T1–T4 scoped passing 不表示全部原命令已运行，T5 active/T6 not_started；健康 fixture 不在此次修订范围。
>
> - T5 前采样有限组 spec b56ec434 与 quality f8c03710 最终 Accept/no findings，仅关闭该源码组。父 Harness286.006s/exit124、drift/clean exit1 未关闭。当前开始独立 health fixture 有限组；仅测试 fake Docker/恢复用例及必要约定，不改生产健康判定或合同替代，T5 active/T6 not_started。
>
> - T5 health有限组冻结待spec/quality：按服务inspect序列正例与永久unhealthy负例通过，独立load-test子自测exit0；父Harness本轮488.167s/exit1，已越过health至routing-capacity子自测通过，随后静默真实drift入口仍受计划合同未勾项影响，输入changed=[]。父日志有明确Startup Harness tests passed（仅shell子自测）；不是父全pass/Maven startup。原失败保留，合同文档另组独立Review，不擅勾原commands。证据 `/tmp/bolt-t5-health-fixture/RESULT.md`；T5active/T6not_started，无Maven/提交部署。
>
> - T5 合同归属重述候选已按 a1c20572 有限矩阵整理，精确 diff/SHA 在 `/tmp/bolt-t5-contract-reconciliation/`；此候选随后经独立 c224c8c0 于 `2026-10-23T10:20:30.232Z–10:21:22.595Z` 核对精确 diff，最终 Accept/no findings；原a1 session已释放，新Reviewer已读完整matrix/exactdiff，批准仅限原三文档，不扩展未来diff或T5passing。该冻结输入下实际 drift/clean及单次完整父Harness均exit0，父最终 `Harness script tests passed.`；每条输入changed=[]，证据 `/tmp/bolt-t5-reconciled-gates/`。本末次状态回填与运行输入分开保存，不把回填后SHA冒充原运行fresh。T5架构仍因私有JRaft实际缺件未编译/neither RED nor GREEN，T5唯一active、T6not_started；下一步有限独立证据Review及用户提供合法原制品，不运行Maven/full/HA。T2/T3/T4原未执行组合/顺序紧邻保留，owner完整测试与behavior/startup/E2E/HA由既有T6同轮full未勾步骤承接。sampling specb56ec434/qualityf8c03710、health spec eb0/qualityefe76e56 均最终Accept/no findings；父Harness488.167s/exit1仍保留，startup shell明确成功仅归该子义务。T1–T4 scoped passing、T5唯一active、T6not_started，无full/Maven/提交部署。

## T6 HA 装配有限修复（当前恢复入口）

第三轮 full 已实际980.909s/exit1，2743输入稳定：完整reactor495 fresh XML/2333 tests/0 failures/0 errors/2既有MySQL条件skip（排除8旧XML），同轮制品proof通过后首次真实HA失败。三Broker默认MTLS缺key-store，最早链为brokerPeerAdapter→boltInvoker→boltRpcClient→validateTypedPolicy:57；Redis/Nacos健康、Raft init成功。compose-up49.249s/exit1，未到ready/场景，scenarios0；owned project30344已清空。原件 `/tmp/bolt-t6-acceptance-attempt3/RESULT.md` 保留，不称full通过。

用户授权仅修测试部署fixture，保持T1–T5 scoped passing、T6唯一active。精确范围：`deploy/local/compose.routing-ha.yml`、`scripts/run-routing-ha-scenarios.sh`、新增 `scripts/lib/routing-ha-tls.rb`、新增 `scripts/test-routing-ha-tls.sh`、`scripts/test-routing-ha-process.sh`、`scripts/test-routing-ha-harness.sh`（复制fixture依赖）、`scripts/test-harness.sh`（挂入合同自测）、`deploy/local/README.md`、`im-test/im-routing-ha/README.md`、`docs/references/HARNESS_GUIDE.md`（既有材料隔离约定及合同传感器），以及PROGRESS/本计划/active README三状态文件。不改生产Java、依赖、观察授权/投票/Raft默认、checker证明或DEV/E2E组。

- [x] 先 `./scripts/verify.sh readiness`；保存 `/tmp/bolt-t6-ha-assembly-fix/` before原bytes（含untracked计划）、files/SHA/ignore、进程与原HA日志归因。
- [x] `bash scripts/test-routing-ha-tls.sh` 确定性合同先RED后GREEN：生成独立测试CA/五叶PKCS12、严格URI/DNS配对、有效期/cryptographic trust、0600/0700、缺失/篡改材料及生成失败拒绝；不打印秘密。材料只在runner新建owned临时目录，cleanup失败保留现场，不删除未知路径。
- [x] 修改真实HA Compose挂载及typed bootstrap/Gossip可信配对，采用容器DNS与明确Nacos发现地址交集；不把observationId当稳定BrokerID。`bash -n scripts/run-routing-ha-scenarios.sh scripts/test-routing-ha-tls.sh`、`bash scripts/test-routing-ha-process.sh` 验证原失败传播/清理及材料前检，保留900s原HA合同。
- [ ] 窄实际Compose启动/健康/状态一致与逐实例恢复（使用既有exec JAR并记录SHA；不冒同轮full proof，不改manifest checker），最多一次有界调试；原25场景映射Java证据仍属于full3，不拼新full。若遇生产行为bug立即交父，不偷偷修改。
- [ ] 源码/稳定文档冻结，保存source.diff、commands/exits/windows/logs/reports/RESULT及运行前后SHA交父spec再quality；本组不运行full4/提前passing。完整full新输入真实HA及独立Accept仍归父后续。

本有限组当前冻结交父spec再quality：readiness exit0，TLS合同missing-helper RED exit1→最终GREEN exit0（包括五独立私钥、CA私钥移除、缺失/损坏/公开权限/配置篡改及keytool失败反例），进程/材料清理合同和同轮manifest分支自测均exit0。真实窄Compose调试13:42:46.660–13:42:58.976Z/12.316s/exit18，在tls-create/check和compose-config均exit0后，Redis Pulling时Hub443拒绝连接；尚未到Broker，不能称多Broker MTLS/挂载通过。原误称localmissing已撤回：当前desktop-linux、DOCKER_HOST/CONTEXT/DEFAULT_PLATFORM均unset，同tagredis:7.2.0实际arm64本地存在（sha256:69bb36a0...，RepoDigest c45b9ac4...），渲染Compose无platform/pull_policy，本组未改这些键。full3也是desktop-linux/aarch64；为何尝试Pulling仍未知，不推断跨context/缺cache，不重拉/换tag/切context。owned cleanup-logs/down均exit0、ledger空，材料按trap删除。先前一次debug监督调用多传bash导致0.085s/exit0无实际步骤，不计拓扑证据；后纠正为上述唯一实际Compose调试。

精确source和新增文件、完整before bytes、运行SHA/日志/命令/环境facts见 `/tmp/bolt-t6-ha-assembly-fix/RESULT.md`。未跑Maven、正式ha、full4、clean/quality或完整父自测；用户要求有限组立即冻结，由父独立spec/quality后安排新输入验收，T6仍active、不提前passing。Loop4保持PAUSED，不提交/生产部署/网络恢复平台或未知资源删除。

### HA fixture quality 窄修订（已复核Accept，原Block历史保留）

原spec `e9` 已Accept；quality `ded17413` 最终 **Block/Critical一项** 保留：keytool exit23已wait/reap后，失败raise先于pid清除，ensure可能向复用PID发KILL。当前只改 `scripts/lib/routing-ha-tls.rb` 和既有 `scripts/test-routing-ha-tls.sh`：wait成功后立即清除PID所有权，再以保存status判失败；未回收/timeout仍沿原owned终止清理。测试用真实fake keytool exit23与Process.wait见证，拦截并记录已回收PID上的signal，旧源码确定性RED exit1明确 `keytool signalled after reap`；修复后完整离线TLS合同GREEN exit0、process合同exit0、Ruby/bash语法exit0、readiness exit0。证据 `/tmp/bolt-t6-ha-assembly-fix/quality-revise/` 保留actual before bytes（含untracked计划）、精确source.diff/SHA/命令与窗口。仅本段及PROGRESS同步，无其他13文件收尾、不改原质量Block为Accept。原quality `ded17413` 复核最终Accept/Critical closed，原spec e9 Accept保持；未执行HA/Maven/full或同因Hub失败重试，真实多Broker MTLS与T6验收缺口不变，T6唯一active、Loop4 PAUSED。
## T6 第四轮标准完整验收冻结入口（当前）

HA装配有限组spec `e9` Accept，quality `ded17413` 经PID回收后撤销signal所有权修复最终Accept/Critical closed；旧Block与确定性RED不改写。实际缓存调试独立 `a574d95f` evidence/state Accept/no findings，原件 `/tmp/bolt-t6-ha-actual-debug/RESULT.md`：13:55:50.916582–13:57:39.389691Z，108.472s/exit0，2745 Git-visible输入changed=[]。真实初次Compose健康、HTTP就绪、三Broker状态一致及逐实例恢复通过，材料/容器装配越过原缺key-store；最后Broker-3 Docker health仍starting但HTTP已200 UP并状态一致，不能称最终五Docker同时healthy。原生lease/processor错误与业务授权/观察证明限制保留，不等同25场景、正式HA/full或最终实现Accept。

此前默认runner的Hub443 refused及已有同tag仍Pulling原因未知。本次新增前提仅是当前同tag镜像已被真实up使用、ID/架构未变及材料装配成功，不声称默认网络修复或保证full4成功。离线debug的 `--pull never --no-build` 仅/tmp诊断，不带入正式full；不改source、pull_policy、platform、tag、依赖或auth mode。完整新输入标准full4实际结果才是本轮证明。

唯一writer仅集中更新PROGRESS、本计划、active README三状态文档；before-state保存全部实际Git-visible原bytes，包含untracked计划全文、tracked用户.gitignore及新增helper/test。实际文件数每次枚举，不硬编码；.pi保持用户owned/ignored且关键Loop SHA保存，Loop4 PAUSED，不恢复或扩展exclusions，不干预外仓/既有k6。源码、build、稳定规范与全部文档在运行前定稿，保存前后SHA/精确状态diff到 `/tmp/bolt-t6-acceptance-attempt4/`。

- [ ] 核对本仓无并发Maven，分别执行 `./scripts/verify.sh readiness`、`./scripts/verify.sh clean`，各自记录exit及完整输入稳定性；任一步失败精确归因后停止。
- [ ] clean exit0且输入稳定后，唯一一次 `MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh full`，10800s工具预算：父自测、一次完整reactor、同轮riskreports/artifact proof、正式真实HA及25场景，不跨轮复用full3的2333 tests或debug状态。
- [ ] 运行中及结束后不再Git-visible写入，仅/tmp和标准ignored输出；保存原生exit/窗口、fresh XML mtime/count/skips、gate stage、manifest expected/actual、全输入changed list和owned cleanup事实。失败首先交父最早native owner/root，无盲重跑或新源码修复。成功即时通知父安排正式quality及独立最终实现Review，不把review_required或focused debug Accept当最终Accept。

T1–T5 scoped passing、T6唯一active，不提前passing/归档；full1 463.426s/exit1、full2 707.160s/exit1、full3 980.909s/exit1原记录保留。无重复独立architecture/startup/behavior/E2E/HA，无提交、部署、新平台或未知资源删除。以下旧“当前入口”均为历史阶段，由本节取代；本轮结束后仅/tmp保存实际结果，checkbox不回写Git-visible输入。

## T6 full4 native completion 有限诊断（当前恢复入口）

full4 644.942985s/exit1，64 fresh XML/441 tests/2 failures；原件 `/tmp/bolt-t6-acceptance-attempt4/RESULT.md` 不覆盖。两个失败是既有 `BoltConnectionLifecycleTest.productionNativeCompletionReleasesPermitForSubsequentTypedCall` 的 future,close / callback,close；下一调用预算3000ms，原异常 cause 未完整展示，不能先判生产 bug 或 permit race。T6唯一active，Loop4 PAUSED。

- [x] 阅读 owning 指南/事实源并执行 `./scripts/verify.sh readiness`（exit0）；保存全部 Git-visible before bytes（含未跟踪计划）至 `/tmp/bolt-t6-native-completion-fix/snapshot/before/`。
- [x] 唯一源码范围 `im-plugin/im-bolt/src/test/java/com/co/kc/imchat/plugin/bolt/BoltConnectionLifecycleTest.java`：仅在既有方法增加失败消息及完整 throwable cause、公开 pool/channel/native future/handler 事实及单调耗时；不改预算、生产逻辑、Clock/seam/reflection/sleep。无需 support 文件。active/inflight 无公开 getter，明确不可观测，不臆测。
- [x] 冻结输入后单次 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltConnectionLifecycleTest#productionNativeCompletionReleasesPermitForSubsequentTypedCall -Dsurefire.failIfNoSpecifiedTests=false test`（900s预算）。实际 CsvSource 为15项；不加未注册参数过滤器、不重复碰绿。保存 raw/native log、fresh XML、命令、窗口、退出码和输入前后SHA。
- [x] 复现后先归因；生产缺陷仅交父最小RED及方案，不擅改生产；未复现即报告 gap 并冻结，不继续盲跑或无根据修断言。只有已证明 test bug 才最小TDD修复并模块回归。无full5/HA/广门禁，有限组交独立spec/quality后另决策，不提前passing。

单次诊断focused实际14:20:09.667624–14:20:33.842136Z，24.174s/exit0；fresh owning XML为15 tests/0 failures/0 errors/0 skips，运行前后2745输入稳定。本轮未复现，未取得失败现场counter/cause，不归因生产或测试bug。仅增加失败诊断，不改行为/预算，不追加同方法重跑或模块/广门禁。当前诊断源码及三文件增量冻结交父独立Review；原full4失败保持，不能以本focused GREEN覆盖。

## T6 native completion owning module 单次回归（当前）

诊断增量独立spec `2bbc`、quality `35a08e9d` 最终Accept/no findings，仅诊断，不是fix。用户批准下一次 owning module 回归：`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test`，独立1200s预算，不循环方法或运行full5/HA。本轮不再修改测试/生产、预算或barrier；既有beforeNext getter可能扰动时序，不能以未复现排除竞争。

- [x] 运行前保存全部实际Git-visible before原bytes，含未跟踪计划，至 `/tmp/bolt-t6-native-completion-fix/module-regression/snapshot/before/`，保留HEAD、ignore规则与Loop SHA。
- [ ] 核查本仓无并发Maven，冻结输入后仅上述module命令一次；保留所有报告before/after mtime/SHA、fresh XML、15 native cases、native日志、命令窗口及exit。失败即停止交父，不新跑test或改生产；通过只称模块未复现，不关闭原full4失败。

运行开始后不再Git-visible回填，结果仅写上述/tmp的RESULT；checkbox保留运行前pending事实。T6唯一active、Loop4 PAUSED，原full4 future,close/callback,close cause未知仍保留。不提交/部署、不干预外仓或用户.pi。

## T6 native ordering 有限实验（当前）

SPEC596aac18 Accept 条件已完整读取：仅 owning `BoltConnectionLifecycleTest.java` 新增 `productionNativeCloseCompletionPrecedesManagedConnectionRemoval(String)` future/callback 两参数及同文件事件探针；不改原15case/生产。T6唯一active，full4 cause unresolved，Loop4 PAUSED。

- [x] readiness exit0；全部Git-visible before原bytes（含untracked计划）保存 `/tmp/bolt-t6-native-ordering-experiment/before/`。
- [ ] 在真实 RpcConnectionEventHandler 前暂存 inactive/unregistered，原loop一次释放；暂停期间不调用acquire/next；公开snapshot同old仍在→传播完成且old已移除→一次相同target/slot1/3000ms真实typed成功。5s仅hang保护，事件异常fail，不把invalid fixture当机制RED。
- [ ] 冻结后仅一次 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltConnectionLifecycleTest#productionNativeCloseCompletionPrecedesManagedConnectionRemoval -Dsurefire.failIfNoSpecifiedTests=false test`（900s）；保存输入/报告/窗口/exit。实验后仅/tmp结果，立即交父独立spec/quality，不修原close前提、不跑module/full5/HA。

## T6 无效 native ordering candidate 已撤回（当前恢复入口）

实验实际21.226334095s/exit1，1 fresh XML/2 tests/0 failures/2 errors；释放时 `nativeContext.isRemoved=true`，finally异常可能覆盖原主异常。分类为 invalid fixture / context removed API gap，不是机制RED、不是full4复现或生产缺陷证明。原失败源码、XML、日志与报告保留 `/tmp/bolt-t6-native-ordering-experiment/`。

本次按用户授权仅将 `BoltConnectionLifecycleTest.java` 恢复到该实验before原字节，完整SHA256为 `8f8b6233735d47cc9bcd7e842495f9c48c4d8da6452d38d849f48960cae8c645`，与before manifest一致；原15case及已双Accept的beforeNext诊断保留，未撤其他任务/用户变更。已移除临时必失败候选，不改原close断言。before原bytes（含untracked计划）、精确source.diff/SHA及只读API方案在 `/tmp/bolt-t6-native-close-notification/`。

后续仅候选方案：经actual owned manager公开getter注册按old connection identity过滤的CLOSE listener，close前注册，callback仅完成future；回调后另验公开snapshot已无old，再验证slot1/原3000ms预算真实typed next及新channel/handler delta。优先独立两参数 observational test `closeNotificationObservesNativeRemovalAndNextCallSucceeds`；本次不实施，先交父独立契约Review。既有RpcClient自有listener不是该manager的listener，不重复wrong-owner方案；不拦截/缓冲/重放事件，不增加pipeline探针/handler replacement/reflection。

full4原2 failures cause仍unresolved，null-selector NPE保留为潜在production bug风险，不retry/吞异常。本次readiness exit0，字节/SHA与diff校验不替代测试验收；无Maven/module/full5/HA/网络/生产API或预算修改。T6唯一active、T1–T5 scoped passing、Loop4 PAUSED；冻结交父Review后停止，不提前passing。

## T6 CLOSE notification 纯观察有限实施（当前恢复入口）

独立SPEC `1e2052c2` pure observational方案Accept；仅/tmp报告两处措辞Revise已修正：actual虚调用为BoltAuthenticatedConnectionManager.remove(binding/代际connTasks退休pool)，native executor catch只包execute提交，不包callback Runnable。原invalid实验2 errors保留，已撤candidate不重引。

- [x] readiness exit0；保存全部Git-visible before原bytes（明确含untracked本计划）、完整SHA/ignore/Loop快照到 `/tmp/bolt-t6-native-close-observation/before/`。
- [ ] 唯一源码范围 `im-plugin/im-bolt/src/test/java/com/co/kc/imchat/plugin/bolt/BoltConnectionLifecycleTest.java`，仅新增 `closeNotificationObservesNativeRemovalAndNextCallSucceeds(String)` future/callback两参数。close前actual owned manager公开listener按old identity完成future，callback自身异常completeExceptionally；测试线程等待CLOSE并验公开snapshot无old，再一次原3000ms/slot1 typed next，真实response、handled+1、新connection/channel identity及old writes不增。原15case/诊断不改；不增加close事件拦截/重放/pipeline hook，不改生产/API/依赖/预算。finally独立清理client/worker/server，cleanup异常suppressed到primary，不能覆盖原cause。
- [ ] 核查本仓无并发Maven后冻结全部Git-visible/ignore/Loop输入，仅一次 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltConnectionLifecycleTest#closeNotificationObservesNativeRemovalAndNextCallSucceeds -Dsurefire.failIfNoSpecifiedTests=false test`（900s工具预算）；保存commands/windows/exits、原日志、fresh XML mtime/SHA、source.diff和前后输入SHA。失败即停止，不修更多API或重跑；通过也冻结交父独立source/结果Review，未经批准不改原close分支或跑module/full5/HA。

T6唯一active、full4 root cause仍unknown/unresolved，null-selector NPE仍为潜在production bug；不把本观察证明成瞬时permit释放或full4根因。T1–T5 scoped passing、Loop4 PAUSED，用户ignore/.pi与外仓进程保持原状，无提交/部署。运行后仅/tmp保存事实，checkbox保留运行前状态。

## T6 原close分支 retirement 前提有限校正（当前恢复入口）

新两参数观察源码/实际结果独立SPEC `1e2052c2`、quality `376f9eed` 均Accept/no findings；SPEC最新final允许下一阶段只在原15case的close分支增加actual CLOSE+public retirement前提。此前有效实验及独立结果Review条件已满足，不等于full4具体cause已证明。

- [x] readiness exit0；全部Git-visible before原bytes（明确含untracked本计划）、SHA/ignore清单/Loop快照保存 `/tmp/bolt-t6-native-retirement-premise/before/`。
- [ ] 唯一源码范围 `im-plugin/im-bolt/src/test/java/com/co/kc/imchat/plugin/bolt/BoltConnectionLifecycleTest.java`，仅 `productionNativeCompletionReleasesPermitForSubsequentTypedCall` 的sync/future/callback三个close参数共同补retirement前提：close前actual manager公开listener按old identity完成future，自身异常completeExceptionally；原terminal及barriers后测试线程等CLOSE并验public snapshot无old，再原3000ms typed next，追加handled+1、新connection/channel及old writes不增。非close逻辑、原断言/预算/await、beforeNext诊断及已双Accept新2case不改，不改生产/依赖/API或增加retry。
- [ ] 核查本仓无并发Maven，冻结全部Git-visible输入后仅一次900s focused：`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltConnectionLifecycleTest#productionNativeCompletionReleasesPermitForSubsequentTypedCall+closeNotificationObservesNativeRemovalAndNextCallSucceeds -Dsurefire.failIfNoSpecifiedTests=false test`，实际17参数项；失败保存root cause停止，不循环修控。
- [ ] 仅focused通过且输入稳定后，再一次1200s owning module：`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test`；保存该独立窗口实际fresh XML/count，不拼focused或旧报告。失败停止；通过冻结source/evidence交父独立spec+quality，本组不执行full5/HA。

这是「原next成功前提不足，按已批准完整生命周期回收契约校正」，不是生产修复或新增瞬时permit释放保证。full4具体cause仍unknown/unresolved，null-selector潜在production风险不消失；原验收失败及invalid实验2 errors保留。T6唯一active、T1–T5 scoped passing、Loop4 PAUSED，不标passing；运行中/后仅/tmp记录，无Git-visible回填，state after SHA与运行SHA分开保留。不改.gitignore/Loop/外仓，不提交部署或unknown kill。

## T6 第五轮标准完整验收冻结入口（当前）

原close分支retirement前提修订独立SPEC `2406`、quality `64728544` 最终Accept/no findings：focused 21.720511292s/exit0，1 fresh XML/17 tests/0 failures/0 errors/0 skipped；owning module 167.282869167s/exit0，27 fresh XML/301 tests/0 failures/0 errors/0 skipped（common37、Bolt264），各独立窗口输入稳定。证据 `/tmp/bolt-t6-native-retirement-premise/RESULT.md`，不拼报告。新增纯观察2case独立SPEC `1e2052c2`、quality `376f9eed` Accept，证明CLOSE+public snapshot完成退休后的真实复用；这是明确测试契约前提校正，不是盲repeat green，也不是生产bug修复。原full4具体cause仍unknown/unresolved，null-selector NPE潜在production分类风险不得因新GREEN移除。无效ordering实验21.226334095s/exit1、2 errors仍为invalid fixture/API gap历史，不计机制RED。

HA装配spec `e9`、quality `ded17413` 最终Accept，PID生命周期Critical closed，旧Block/RED保留。有限实际HA调试独立 `a574d95f` Accept/no findings仅限108.472s/exit0、2745输入稳定的装配/状态/恢复，不是25场景或正式full证明。full3缺key-store的fixture修复已在源码；full4早停im-bolt未验其正式HA。默认Hub Pulling原因仍unknown，离线debug flags不带入full，不保证网络恢复。

原full1 463.426s/exit1（2744稳定、父fixture预算）、full2 707.160s/exit1（2743稳定、239 fresh XML/1438 tests、Broker778/2 failures/38 errors）、full3 980.909s/exit1（2743稳定、495 fresh XML/2333 tests/0 failures/0 errors/2既有localMySQLskip，制品proof后真实HA缺key-store、scenarios0）、full4 644.942985s/exit1（2745稳定、64 fresh XML/441 tests/2 failures，im-bolt262/2 failures）各原RESULT、原窗口/失败/stale事实不覆盖，不复用旧reactor/full proof跨输入。

唯一writer本次只集中回填 `PROGRESS.md`、`docs/exec-plans/active/2026-10-22-bolt-unified-invoke.md`、`docs/exec-plans/active/README.md`；全部实现/设计/稳定文档已定稿，无新source/checker/quality exclusions/API/预算修改。证据 `/tmp/bolt-t6-acceptance-attempt5/` 保存beforestate实际原bytes（含untracked计划、新TLS helper/tests）、exactstate.diff、sha、snapshot及readiness/clean/full/RESULT；实际输入数由枚举记录，不硬编码。用户tracked.gitignore纳完整输入；.pi保留用户owned/ignored，Loop关键SHA旁证，Loop4保持PAUSED，不恢复/续期。不干预外仓Maven或既有k6未知PID，不kill未知进程。

- [ ] 冻结全部Git-visible文件、HEAD、ignore及Loop后确认本仓无并发Maven；分别执行 `./scripts/verify.sh readiness`、`./scripts/verify.sh clean`，记录真实独立exit/窗口/输入稳定性；任一步失败先归因停止。
- [ ] 仅clean exit0且输入稳定后，一次标准 `MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh full`（10800s工具预算）。不带--pull never/--no-build等debug flag，不换GAV/版本/mirror；父selftest一次、完整reactor、同轮risk reports/artifact proof、真实HA及25scenarios全部实际完成才称machine full success。无需前后重复独立architecture/startup/behavior/E2E/HA。
- [ ] 运行中及结束后不再Git-visible写入，仅/tmp和标准ignored输出；保存原生exit/完整日志、fresh XML原窗口mtime及module/count/精确既有MySQLskip、stage/processor启动/25case operation与missing milestones，排除旧target XML；before/after输入changed=[]才算稳定。网络或最早native失败原件保留，不绕过，不盲full6或源fix，停止交父独立task。
- [ ] full成功立即通知父并冻结全部machine证据，仍T6active、不回填passing/归档/提交；父后续正式 `./scripts/verify.sh quality` 与独立final implementation Review须考虑full4 unresolved风险，不将review_required或新pass当根因关闭；若Block则重开、修复并适当fresh revalidate。

T1–T5 scoped passing、T6唯一active/WIP=1；无提交、生产部署、新平台、未知.pi删除。此节取代所有此前“当前入口”，旧状态保留为历史，不在full后回写checkbox。正式完整验收与独立Accept后方可另阶段纯状态关闭及精确diff审查。

## T6 full5 ENOSPC 恢复（当前入口）

full5 **incomplete / 验收无法确认，原生退出码未知**；不记 exit1 或 pass。原 worker ENOSPC 后未写 full/run.json、exit 或 post-full snapshot。原证据 `/tmp/bolt-t6-acceptance-attempt5/` 不重建、不覆盖；恢复报告 `/tmp/bolt-t6-enospc-recovery/RESULT.md` 及机器 JSON 保存原路径/SHA。父 Harness 已越过自测；独立本轮 reactor 窗口 2026-10-23T15:10:09.909561–15:18:40Z：495 XML/2335 tests/0 failures/0 errors/2 既有 MySQL 条件 skip，排除8旧XML；manifest48项XML/exec-JAR当前SHA匹配。这些只是 reactor 阶段证据，不是 full 通过。

真实 HA 已 compose-up、五应用healthy、readiness、cluster-consistency和Gateway stop exit0；下一readiness started无finished，cleanup-logs started后Ruby ENOSPC截断。scenarios.jsonl为0 bytes（0条已完成场景证据）；cleanup-down/完成无证，不借旧routing-ha passed/旧cleanup日志。恢复时2745 Git-visible文件（含untracked计划、用户.gitignore）、HEAD/ignore/ignored Loop字节与冻结相同，但缺原终点，不能冒充原运行全程稳定证明。

owned项目 `im-chat-routing-ha-87933` ledger保留，cleanup **blocked**：Docker ps 20s及单次version15s超时，backend存在不等于daemon响应；未down、重启Docker、kill backend或prune。TLS候选 `/private/var/folders/h9/drjh32n10x170kpxrbpl_1p00000gn/T/im-chat-routing-ha-tls.oI0ZBfjE` 与tls-create时间对应，0700/0600原权限保留；daemon未响应，尚缺inspect label/mount最终绑定，不删除候选或其他目录。恢复只读 `verify.sh cleanup` exit0仅其tmp/PID allowlist，不是HA清理证明；恢复readiness exit0不证明Docker/空间健康。

当前可用约4.58GiB，targets实占约1.91GiB，attempt5证据约45.8MiB；缺开始前target/VM基线与峰值，无法计算target delta或证明安全margin足够。父先审本报告，需用户确认Docker维护窗口后精确收口87933并评估空间，再决定新冻结full；本任务不跑full6、不叠HA。T1–T5 scoped passing，T6唯一active/WIP=1、Loop4 PAUSED；full4 cause unknown/unresolved和null-selector潜在production风险保留，原专项Accept不代替最终完整验收/独立final quality。此次仅状态batch，state-after SHA与原运行输入分开，不篡改原报告时间/退出码。无提交、部署或依赖/checker/网络策略修改。

## T6 Docker恢复后的owned清理（当前入口）

用户确认Docker已恢复后，本次仅收口原full5 owned项目 `im-chat-routing-ha-87933`；Docker Server 27.4.0实际响应。六个原容器的Compose project/config_files labels与mount共同确认原文件 `deploy/local/compose.routing-ha.yml` 及材料 `/private/var/folders/h9/drjh32n10x170kpxrbpl_1p00000gn/T/im-chat-routing-ha-tls.oI0ZBfjE`。Docker Desktop返回的 `/host_mnt/private/var/...` 和 `/var/...` 为host路径别名；首次严格字符串核验因此停止且未清理，按显式host映射与realpath归一后重新核对，非凭目录名删除。

- [x] `./scripts/verify.sh readiness` exit0；保存原容器各最多300行日志、label/mount与owned network/volume inspect、原ledger和证据路径/SHA，不打印TLS密码或私钥内容，不复制源码全树。
- [x] 使用原 `scripts/lib/routing-ha-process.rb` 在独立恢复state监督两个cleanup窗口：cleanup-logs 2026-10-24T00:30:29.874269–00:30:30.513281Z/exit0；cleanup-down 00:30:30.580117–00:30:31.095713Z/exit0，均groupGone=true。down精确参数为 `docker compose -p im-chat-routing-ha-87933 -f <原绝对Compose路径> down --volumes --remove-orphans`，环境仅重建原 `ROUTING_HA_TLS_DIR`，未up/pull/build。
- [x] down后project container/network/volume查询均空；另检查全部剩余容器mount无owned TLS引用，材料树无symlink、uid为当前用户、目录0700/文件0600，才删除该精确目录。按原runner成功down合同清空ledger，不修改历史command-group owner、full5退出码/结束时间或full报告。
- [x] 仅hash确认恢复cleanup输入changed=[]；与full5冻结的差异仅前次恢复三状态文件，不能补原full终点。必要状态batch保存三文件before原bytes及精确state.diff，新状态SHA不冒充原运行输入。证据 `/tmp/bolt-t6-docker-recovery/RESULT.md`，原attempt5/enospc记录不覆盖。

当前df available 8,762,925,056 bytes（约8.16GiB）；Docker.raw逻辑63,999,836,160 bytes、实占25,415,987,200 bytes。四个所需原tag的image inspect实际存在，精确IDs见恢复JSON；这不保证默认Compose不会访问Hub，也不保证下一full不会再次写满。仍缺full5容量基线/峰值，不能承诺安全margin。本批次无Maven/full6/父自测、不全局prune/切context/unknown kill，不动外仓k6、用户.gitignore/.pi、私有m2/POM或原日志证据。

这些是新恢复cleanup窗口，不是full5正常结束：full5仍incomplete、原生退出码未知，scenarios.jsonl原0 bytes且无25场景通过证据。T1–T5 scoped passing、T6唯一active/WIP=1、Loop4 PAUSED；full4 cause unknown/unresolved及null-selector潜在production风险保留。父可先独立审阅精确证据/state.diff，再另批重新冻结full6；不自行运行，不提前passing/归档，无提交或部署。

## T6 第六轮标准完整验收冻结入口（当前）

owned87933恢复清理已获独立 `9038f4ce` 最终Accept/no findings，原cleanup writer已completed并停止；恢复证据以 `/tmp/bolt-t6-docker-recovery/RESULT.md`、cleanup-result.json及state.diff实际文件为准。原full5仍incomplete、原生exit未知，495 XML/2335 tests全零/2既有MySQLskip仅属该轮独立reactor阶段，不跨轮复用；所有原失败和未知终点保留。

用户批准仅一次新输入标准full6。原close前提SPEC2406/quality64728544双Accept、HA装配e9/ded双Accept及a574有限实际debug Accept边界保持不变；source、稳定文档、code/依赖/checker均已final，无新生产修改。full4 cause unknown/unresolved及null-selector潜在production风险保留。T1–T5 scoped passing，T6唯一active/WIP=1；Loop4 PAUSED，父与scheduler无writer，唯一执行者本次只集中更新PROGRESS、本计划、active README三个状态文件后冻结。

证据入口 `/tmp/bolt-t6-acceptance-attempt6/`：仅保存三状态文件before原bytes（明确含untracked计划）、精确state.diff、完整Git-visible SHA/size/mode、HEAD、ignore及Loop副本。其他source原bytes引用attempt5冻结sameSHA原件，不重复镜像2745文件；jar保留原路径，报告按实际size仅保存必要压缩XML及小manifest，不复制大型报告树。用户tracked.gitignore纳完整输入，ignored.pi不删除、不扩大quality exclusions，不干预外仓/k6。

- [ ] 运行前确认本仓无Maven并记录df、Docker响应及四canonical tag只读inspect；本次执行guard为available至少3GiB，否则环境阻塞即停，不是生产容量/性能保证。恢复约8GiB不保证full不会ENOSPC；无全局du/prune或未知cache删除。
- [ ] 冻结后顺序 `./scripts/verify.sh readiness`、`./scripts/verify.sh clean`，各真实exit0且输入稳定才进入下一步。
- [ ] 仅一次 `MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh full`（10800s工具预算），标准父selftest、完整reactor、同轮risk/artifact proof、真实HA25场景，不使用debug offline flags、不跨run复用。实际exit及时独立小文件落盘，raw stdout单份，磁盘耗尽时尽力保存但不伪造保证。
- [ ] 运行中及结束后无Git-visible写入，仅/tmp和标准ignored输出；保存真实exit/窗口、changed列表、fresh reports/count/skips及同轮artifact/scenario/owned cleanup证据。最早失败归因后冻结返回，不盲full7/源码修复。成功立即交父正式quality及独立final implementation Review，仍不提前T6passing/自动归档/提交。

此节取代旧当前入口；运行后checkbox保留冻结前状态，事实仅写/tmp。无重复独立architecture/startup/behavior/E2E/HA，无commit/deploy或Loop恢复。完整验收和独立Accept之后才另阶段纯状态closure及精确diff审查。

## T6 第七轮标准完整验收冻结入口（当前）

full6 原验收失败保留：2026-10-24T00:33:42.176042–00:50:09.186324Z，987.004078s/exit1；独立本轮 reactor 495 fresh XML/2335 tests/0 failures/0 errors/2 既有 MySQL 条件 skip、48 项同轮 manifest SHA 匹配，但真实 HA 在历史 registry 前检被拒绝，尚未 spawn 场景。原件 `/tmp/bolt-t6-acceptance-attempt6/RESULT.md` 不覆盖。full5 仍 incomplete/原生 exit 未知，owned87933 恢复清理独立9038f4ce Accept不补原full5终点。

历史 registry 人工隔离 checkpoint 已获独立 d51 closure Accept；精确源、归档绑定及其他六个 session 不变证据见 `/tmp/bolt-t6-registry-quarantine/RESULT.md`。归档 owner 仍 active=true、无 done，明确人工隔离，不伪造 resolved/正常退出。随后公开原生 helper 在正式 registry 唯一一次 `run engine-info 8 docker info` 实际1.073047s/exit0，rawExit0/groupGone=true，新 session 正常 owner inactive/done inactive/无 pidfile；原六个 session 与 archive 未改。原件 `/tmp/bolt-t6-native-runner-recovery/RESULT.md`。这仅证明原生窄入口恢复，不是 supervise 整场景、25 HA、full 或跨轮 reactor 证明。

用户已授权自动推进本次唯一标准 full7。T1–T5 scoped passing、T6 唯一 active/WIP=1；Loop4 保持 PAUSED。唯一 writer 仅本批集中更新 PROGRESS、本计划、active README 三状态文件，随后冻结，父与其他 agent 不写。source、scripts、稳定文档、依赖、pull_policy、quality scope 均已定稿且不改。full4 cause unknown/unresolved 及 null-selector 潜在 production 风险仍交最终独立 Review，不能因新 GREEN 手工降分或关闭真实 finding。

证据 `/tmp/bolt-t6-acceptance-attempt7/`：三状态 before 原 bytes（含 untracked 计划全文）、exactstate.diff、全部实际枚举 Git-visible SHA/size/mode、HEAD/ignore/Loop SHA。其他 source 原 bytes 引用此前 sameSHA 冻结原件，本轮重新 hash，不复制源码全树/JAR。用户 tracked .gitignore 纳完整输入；.pi 保用户 owned/ignored，不删除未知内容、不扩大 exclusions、不恢复 Loop、不干预外仓 build 或 k6。

- [ ] 记录本仓无并发 Maven/full/helper/Compose writer、实际 df（available 至少3GiB，否则停止）、Docker version、四 canonical tag inspect、旧 owned87933 project container/network/volume 查询空；未知其他项目不操作。guard 不保证容量或网络安全，不全局 prune/kill。
- [ ] 冻结后独立顺序 `./scripts/verify.sh readiness` → `./scripts/verify.sh clean`，各真实 exit0、输入稳定才进入下一步，失败即归因停止。
- [ ] clean0 后仅一次 `MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh full`（10800s工具预算），标准父selftest、single Maven 完整 reactor、同轮 risk reports/artifact proof、真实 HA25 场景全部实际；无 --pull never/--no-build 等 debug flags，不借 full6 reactor 或 native engine-info 顶替。
- [ ] 期间及结束后 no Git-visible writes，仅/tmp/标准 ignored 输出。及时独立小文件 checkpoint 原生 exit/窗口，保存 fresh XML/count/skips、manifest/artifact SHA、本轮 steps/scenario case IDs（排除旧 append 窗口）、owned cleanup、空间与完整输入稳定性。失败保留最早 native cause/阶段归属后停止，不盲 full8/源码修复；成功立即交父正式 quality 与独立 final implementation Review，不重跑成功 full 之外 Java/HA 风险 gate，不提前 T6 passing/归档/回填。

此节取代旧当前恢复入口；旧各轮历史、未知退出码及失败原件保留。运行后 checkbox 不回写；完整验收及独立 Accept 后方可另阶段纯状态关闭与 exact diff Review。无 Git commit、生产部署、POM/GAV/version/mirror 修改或新 loop。

## T6 最终正式 Block 与 R1 有限修复（当前入口）

Reviewer7caf 正式十二单元原 JSON 已从指定 session 最后 assistant final 解析保存 `/tmp/bolt-t6-final-block/full-response.json`，逐项核对 requestId、scope 及十二 unitFingerprint 后原样提交 ignored response，无投影、无语义修改。标准 `./scripts/verify.sh quality` 实际 exit0，仅表示合成成功；正式 snapshot **Block / 十二单元 D**，不是 Accept。三个 P2 mustFix 原文及分数、summary、verdict 全部保留。full7 原输入完整成功不改写，修复后不再是新输入 fresh acceptance；full1–6 历史与 full4 unknown cause 不抹。T1–T5 scoped 历史保持，T6 唯一 active/WIP=1，Loop4 PAUSED。

- T6-R1：active，本组只处理 native pool null selection 的 LOCAL 失败与 observed-task compare-remove。
- T6-R2：待独立修复，Gossip observationId/稳定证书身份部署配对，当前不写该源码。
- T6-R3：待独立修复，DEV Compose Gateway 下行配对，当前不写该源码。

精确源码范围仅 `im-plugin/im-bolt/src/main/java/com/co/kc/imchat/plugin/bolt/core/BoltAuthenticatedConnectionManager.java` 与既有 `im-plugin/im-bolt/src/test/java/com/co/kc/imchat/plugin/bolt/BoltConnectionLifecycleTest.java`；不新增 support/seam/反射/Clock/依赖。使用原生 manager 公开 selector API、真实已认证连接和 native RandomSelectStrategy，确定性暂停选择并退休旧连接；分别不发布/发布 replacement 后释放，验证本地失败、无业务发送、新代际不受损。不改身份过期 AUTHENTICATION、caller 预算，不 retry 或取消共享 task。

- [x] 原文标准 quality 在旧源码指纹下完成；readiness exit0；保存状态及两源码 before 原 bytes（含 untracked 计划）、全输入 manifest。
- [ ] 仅新增 `nativeNullSelectionFailsLocallyWithoutRetiringReplacement(boolean)` 两参数 regression；一次 RED `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltConnectionLifecycleTest#nativeNullSelectionFailsLocallyWithoutRetiringReplacement -Dsurefire.failIfNoSpecifiedTests=false test`（900s），要求两项为旧源行为断言失败，非编译错误。
- [ ] manager 最小 null 处理后同命令一次 GREEN（900s），按观察 task compare-remove，明确 LOCAL 保原因。
- [ ] GREEN 后一次 owning module `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test`（1200s）；记录每窗口完整输入 SHA、fresh XML、原退出码和日志，失败先归因停止。
- [ ] 冻结精确 diff 与证据 `/tmp/bolt-t6-r1-null-selection/RESULT.md` 交父独立 spec/quality；不称关闭三个 finding，不跑 full8/HA、不自行最终 Accept、归档或提交。

### R1 SPEC742807ce cleanup 窄修订（当前）

正式 SPEC742807ce Revise 仅针对新 regression 的 finally 所有权；生产 null 分支符合范围。只改既有 BoltConnectionLifecycleTest：保存 primary，assertAll 独立 worker/client/server 清理，cleanup suppressed 到 primary。另补 `nativeEmptyPoolRetiresStillRegisteredObservedTask`：公开 getConnPools/task.get/pool.removeAndTryClose 在 synchronized(actual manager) 内完成真实空池与同线程可重入 acquire，native CLOSE 无法抢先退休 task，明确验证 null 分支自行移除仍登记的 task。无 pipeline/反射/生产 seam。

- [ ] 仅临时删除生产 null 分支 compare-remove，运行一次 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltConnectionLifecycleTest#nativeNullSelectionFailsLocallyWithoutRetiringReplacement+nativeEmptyPoolRetiresStillRegisteredObservedTask -Dsurefire.failIfNoSpecifiedTests=false test`（900s），要求新增 case 行为断言 RED；保存 mutation 原件后原字节恢复生产。
- [ ] 恢复生产原字节后同 focused 命令一次 GREEN（900s），再 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test` 一次（1200s）。每窗输入冻结/fresh XML/exit 保存 `/tmp/bolt-t6-r1-null-selection/spec-revise/`。
- [ ] 冻结交父原 SPEC 复审再 quality；三 P2 正式 Block 不变，R2/R3 不改，不跑 full8/HA。生产最终 SHA 必须与本批 before 一致。

## T6-R2 Gossip 入站部署配对有限修复（当前入口）

R1 SPEC742807ce 与 qualityc1e12 最终 Accept/no findings，Bolt owning module 304 tests 全零；原正式 Reviewer7caf Block 不降级。T6-R2 是当前唯一实施子任务，R3 pending；T6 唯一 active、Loop4 PAUSED，full4 cause unknown 保留。不修改 semantic 分数，不运行 full8/正式 HA、不提交部署。本组完成后冻结交父独立 spec 再 quality。

复用 `BrokerGossipProperties` 不可变配对，`RegistryGossipConfiguration` 发布启动快照 Bean，Scheduler 与正式 `BrokerRequestConfiguration` 共用；`BrokerPeerAdmissionInterceptor` 只对 digest/delta 用 observationId→完整 URI exact match，再以 principal 稳定 brokerId 检查 committed。普通 BrokerPeerRequest 不走映射。DEV 只允许已有显式 development marker/trusted-network Gossip 分支，观察/探测/自动摘流拒绝不变。不从回包、Registry 或 Nacos metadata 学习授权。

- [x] readiness exit0；读取 Broker/plugin/test ownership、架构/README、O1/D12/D14 与7caf完整原文。保存 changed-file before 原字节（明确含 untracked 本计划）及全部 Git-visible hash 到 `/tmp/bolt-t6-r2-gossip-binding/`；df约3.5GiB，不复制源码全树。
- [ ] RED：新增 `im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/infrastructure/gossip/BrokerGossipAdmissionIntegrationTest.java`，实际两端 controlled mTLS，复用 `scripts/lib/routing-ha-tls.rb` 公共 create API 测试材料，正式配置 Scheduler→Synchronizer→Processor/全责任链→业务 Registry merge 与 tombstone；不同 observation/stable URI、missing/wrong observation/fullURI/noncommitted 在 handler 前拒绝，配置 mutation 不改变快照。`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=BrokerGossipAdmissionIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`（900s）。RED 必须是旧准入行为断言失败，不是编译/fixture失败。
- [ ] GREEN：仅上述三生产文件和 `README.md`；同步实际调用方 `IdentityAccessInterceptorsTest.java`、`BrokerObservationAdmissionTest.java`、`BrokerObservationRequestConfigurationTest.java` 及 `im-test/im-routing-ha/.../AutomaticFailureTlsHaTest.java` 构造，不保留兼容构造。相同 focused 命令（900s），补既有 `BrokerGossipPropertiesTest` startup fail-closed 回归。随后 owning module `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am test`（1800s），失败先归因。
- [ ] 加强 `scripts/run-routing-ha-scenarios.sh` 既有真实拓扑：有界轮询各 Broker `/management/brokers` 观察集合与 Gossip 成功记录，实际观察收敛而非只健康 HTTP；`scripts/test-routing-ha-harness.sh` 确定性正反例与 `bash -n`（每命令900s）。本组不实际重跑 full HA，后续同轮 full8 才证明新真实集群。
- [ ] 保存 exact.diff/sha/commands/exits/windows/reports/RESULT，集中回填 PROGRESS/本计划/active README 为 R2 implementation frozen、待独立 Review，R3 pending。冻结后不再 Git-visible 写入，不自行 Accept/关闭 T6。

R2 补充精确范围：既有 `im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/interfaces/scheduler/BrokerGossipPropertiesTest.java` 增 actual startup Bean 冲突/namespace 失败断言；原 bytes 由初始 SHA 核验保存。脚本合同直接执行正式只读 Ruby 算法，覆盖三节点注册、Gateway 删除及缺注册/陈旧删除/无成功 Gossip 的反例；不新增生产 API。原始首轮 fixture 路径错误与 Ruby2.6 语法/API 错误逐次保留并修正，不作为行为 RED。

### R2 implementation 冻结交父（待独立 spec/quality，不是 Accept）

本有限组生产修复与 focused/owning module 已完成，R2 仍待父独立 spec 再 quality；R1 双 Accept 历史保留，R3 pending，Reviewer7caf 正式 Block 不降级，T6 active/WIP=1、Loop4 PAUSED，full4 cause unknown 不抹。

- [x] O1 既有不可变 `BrokerGossipProperties` 由 `RegistryGossipConfiguration` 单启动 Bean 发布，Scheduler 与正式 admission 同源。仅 digest/delta 核对 observationId→完整真实 principal URI exact match，再按稳定 brokerId 校验 committed；普通 Peer stable-ID guard 不变。DEV 仅已有显式 marker/context 普通 Gossip 分支，观察 hard deny 保持。无新 trust source、配置/POM/依赖/兼容构造或 domain 反向依赖。
- [x] `red-behavior` 23.621s/exit1：6 tests/2 assertion failures/0 errors，旧源码合法 mTLS 不能收敛与 mutation 后合法调用被拒；此前 material 路径 fixture error 和混合 assertion/error 原窗口完整保留、不冒充行为 RED。最终 focused `focused-final2` 20.606s/exit0：38 tests 全零。真实两端 controlled mTLS、正式 Scheduler→Synchronizer→Processor/全责任链→Registry merge，注册双向及删除墓碑双向实际收敛；missing/wrong observation/full URI/noncommitted 在 handler 前0转发、meaningful delta0 merge。actual startup Bean 冲突/namespace 拒绝与 mutable config 不扩授权通过。
- [x] `module` 250.200s/exit0：197 fresh XML/1301 tests/0 failures/errors/skips，Broker789、Bolt267；独立窗口 Git-visible changed=[]。`ha-caller-compile` 7.281s/exit0，仅编译迁移后的现有 HA 构造 caller，不声称运行该 HA。模块后一次 guard test 候选只把既有测试配对换为不同观察名，10项 GREEN；该候选随后原字节撤回，最终 source 与 module-before SHA 完全相同，未把候选报告拼入模块结果。
- [x] runner 新增 bounded 45s 只读实际 Registry/Gossip 观察收敛前检，三节点 Broker observation 集合、Gateway 初始注册/优雅移除/恢复集合及成功 Gossip 原记录输出独立 evidence；不新增生产 API、不以健康状态或 WARN 消失代替观察。`script-red` 缺该算法 exit1；Ruby2.6 fixture语法/filter_map错误已归因修正，`script-final` 14.299s/exit0 正反例通过，`syntax` exit0。真实 Compose 新断言尚未运行，只由父后续同轮 full8 验证；不称新真实 HA/25场景已通过。
- [x] `clean` 17.861s/exit0；readiness 初始exit0、git diff --check0。完整 exact.diff/15-file before 原bytes（含 untracked 计划全文）、全输入 SHA、commands/exits/windows/fresh reports 与 RESULT 见 `/tmp/bolt-t6-r2-gossip-binding/`。仅复制改动 before 与小XML，未复制全源码/m2/JAR。最终三状态 after 单独记 SHA，不冒充运行冻结输入。

本次无新增工程约定：仅落实已批准 O1 与既有 immutable/startup/contracts/测试规则，因此未新增 Harness class exception、规则框架或 semantic 改分。无 full8、正式 HA、部署、commit、新 loop；磁盘最终约3GiB，下一次广门禁仍须重新实际 guard，不能保证容量。冻结后不再 Git-visible 写入，交父 source/结果复核。

## T6-R3 DEV Compose 下行配对有限修复（当前入口）

R1 双 Accept、R2 spec745 / quality4f7ca0da 最终 Accept/no findings 保留；Reviewer7caf 原正式 Block 不改写。T6 唯一 active，当前唯一实施子任务 R3，Loop4 PAUSED。

精确范围：`deploy/local/compose.bolt-dev.yml`、`deploy/local/README.md`、`scripts/test-bolt-dev-contract.sh`、既有 `im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/infrastructure/config/GatewayDevelopmentTargetConfigurationTest.java`，以及 PROGRESS/本计划/active README 三状态文件。不改任何生产 Java、namespace 校验、principal/注册/Gossip/lease/观察授权、R1/R2 源码或 HA runner。

- [ ] readiness（已实际 exit0）与 changed-file before 原 bytes（明确含未跟踪计划）、全输入 hash 保存 `/tmp/bolt-t6-r3-dev-compose-pairing/`；先登记本范围再写代码。
- [ ] 扩现有 shell 合同，实际 Compose 渲染 pair 必须对应 Gateway host/实际配置端口，含缺 pair/wrong endpoint 反例；旧 Compose `bash scripts/test-bolt-dev-contract.sh` 必须行为 RED。
- [ ] 扩既有 owning test，从真实 Compose JSON 环境经 Spring ConfigData/Environment binding 得到 BrokerProperties，通过正式 ClientConfiguration 及实际 GatewayClient write/close 捕获 typed target；缺失/错误地址在 invoker 前拒绝。旧 Compose focused 必须行为 RED。
- [ ] Compose 仅显式配置 indexed environment address/完整固定 namespace URI，README 准确说明 synthetic descriptor 不改 trusted-network principal；同合同和 focused GREEN。focused 命令：`mvn -q -o -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=GatewayDevelopmentTargetConfigurationTest,GatewayClientTest,BrokerObservationAdmissionTest -Dsurefire.failIfNoSpecifiedTests=false test`（900s），RED 仅选 GatewayDevelopmentTargetConfigurationTest。
- [ ] `bash -n scripts/test-bolt-dev-contract.sh`、`git diff --check`；保存每窗口 commands/exits/windows/fresh XML/count 与 Git-visible changed=[]，冻结交父 spec 再 quality，不自行整体 Accept。

父批准本组小体积编辑、shell 及 cached focused Maven 当前约2.974GiB继续；focused 前复查至少2GiB，离线无 package/下载、预计增量小于200MB。3GiB guard 仍用于后续 full/真实HA，不改仓库规则。本组不执行 full8、真实 Docker 启动、module/package 或清理缓存，不提交部署。

### R3 implementation 冻结交父（待独立 spec/quality）

R1 双 Accept、R2 spec745 / quality4f7ca0da 最终 Accept/no findings 保留；Reviewer7caf 原正式 Block 不改写。T6 唯一 active，R3 当前 implementation frozen，Loop4 PAUSED。

- [x] readiness exit0，七文件 before 原 bytes（含 untracked active plan）及全输入 hash 保存。
- [x] shell 旧 Compose 行为 RED 1.426s/exit1；配置修复后完整合同 GREEN 6.804s/exit0，实际渲染 pair 对应 Gateway host/正式端口，缺 pair/wrong endpoint 反例拒绝。
- [x] 首轮 fixture 使用源名 compose 未触发 Spring env mapper，旧/候选均失败；原窗口保留为 fixture gap，不冒充行为证明。按实际 Spring API 改为 compose-systemEnvironment 后重新获得旧 Compose focused RED 12.492s/exit1，3 tests/2 assertion failures/0 errors。
- [x] Compose 仅两个 indexed env 键，配对172.30.88.11:18012与spiffe://im-chat/gateway/bolt-dev-gateway；README准确记述 descriptor/marker 区别，生产 Java 零改动。
- [x] 正式 ConfigData/Environment indexed binding + ClientConfiguration 实际 controlled owner + 实际 GatewayClient write/close target capture；仅末端 invoker spy 截断网络，缺失/错误 endpoint 在 invoker 前拒绝。最终 focused 10.988s/exit0，3 fresh XML/20 tests/0 failures/errors/skips（configuration3、既有SDK6、既有observation11）。未拼R2 module1301或旧报告，各窗口Git-visible changed=[]。
- [x] bash -n 与 git diff --check exit0。证据 `/tmp/bolt-t6-r3-dev-compose-pairing/RESULT.md`，exact.diff/sha/commands/exits/windows及fresh XML原件保留，状态回填 SHA 与运行输入分开。

父明确3GiB是full/HA重型预算；本组约2.974GiB开始，focused前均大于2GiB且cached offline，无package/下载、module/full8/HA/Docker up或缓存清理。未运行clean/full，有限组交父决定broader gates与spec/quality，不自行整体Accept/T6passing/归档。不是实际DEV传输证明，不改principal/注册/DTO/Gossip/lease/观察hard deny，不改R1/R2源码、HA25、依赖、Loop或score，无提交部署。

## T6 第八轮标准完整验收冻结入口（当前）

此前各当前入口均为历史，本节唯一有效。R1 null selection LOCAL/observed-task compare-remove，SPEC742/qualityc1e12最终Accept，module304全零；R2 observationId→完整URI及committed、共享immutable配置，SPEC745/quality4f7最终Accept，focused38/module1301全零；R3 DEV Compose pair，SPECb4/quality9e21最终Accept，focused20及shell合同exit0。三组仅局部闭环，原7caf正式十二单元Block/三mustFix/评分保留。R3正式binder/末端target捕获不是实际DEV网络传输；R2新增真实Gossip注册/删除收敛尚待full8。full1–6失败/incomplete/未知exit不改，full7旧输入成功不是postfix fresh；full4具体cause仍unknown。

实际df约15.12GiB满足原3GiBguard并超过5–8GiB建议，不是容量保证。Docker响应正常；格式化image inspect与ls曾矛盾，随后absolute executable原始argv四canonical tag及Redis ID inspect全exit0，旧错误保留。不新增必须全缓存gate，标准Compose按原固定tag实际决定pull。无pull/tag/prune。

唯一writer只集中更新PROGRESS、本计划及active README后冻结。证据 `/tmp/bolt-t6-acceptance-attempt8/`：三状态before原bytes（含untracked计划）、exact-state.diff、全Git实际枚举SHA/smallinputmanifest、HEAD/ignore/Loop完整SHA；旧sameSHA原bytes仅作source identity引用，不复制全树/m2/JAR。用户tracked.gitignore纳全输入，.pi/Loop/quality exclusions不变。

- [ ] 保存本仓无Maven/full/helper/Compose writer、实际df、Docker/四tag及owned资源/registry事实；旧八session正常inactive/done，full5人工archive原active保留，外仓不全局等/kill。
- [ ] 冻结后顺序 `./scripts/verify.sh readiness` → `./scripts/verify.sh clean`，各actual0且全输入稳定才继续，失败先归因停止。
- [ ] 仅一次 `MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh full`（10800s）。单父selftest、完整single Maven reactor、同轮freshreports/artifact proof、真实HA25及新增Gossip注册/删除收敛必须实际执行；无offline/debug flags/跨runproof。
- [ ] 期间及结束后no Git-visible写入，仅/tmp/ignored输出；及时小checkpoint原exit/窗口，记录fresh模块counts/skips、同轮proof/场景/cleanup/input changed。失败first exact cause后停止，不盲full9/源修；成功及时交父并冻结，不回写checkbox/T6passing/归档/分数，父新正式全scope quality request及独立final implementation Review必须完成。成功后不额外重复Java/HA风险gate。

T1–T5 scoped passing、T6唯一active/WIP1、Loop4 PAUSED；无commit/deploy、镜像tag/platform/POM/GAV/版本/checker/PKI改变。完整验收和独立Accept后另阶段纯状态closure。


## 实现验收 checkpoint（状态候选，待独立 Review）

本节取代此前“当前入口”的后续执行指令；原文及历史 checkbox 全部保留，不批量勾绿。T6 前三项实现义务已有真实证据，现仅登记完成；后两项状态关闭义务仍 pending，T6 保持唯一 `active`、WIP=1，计划不迁移、不标整体 `passing`。

- 完整验收原件：`/tmp/bolt-t6-acceptance-attempt8/RESULT.md`，标准 full 实际 exit0；原窗口为 2026-10-24T02:55:41.739025–03:19:43.803458Z。2746 个 Git-visible 输入始末相同；完整原指纹 `6ef777c7e7324c82f117410a999d6709ec1e843928c053ca8a0238a0b0a6942c`，不是本次状态登记后的新指纹。单父自测、完整 reactor、同轮风险报告/制品 proof、真实 HA25 与 R2 注册/删除收敛均以该原件及其小证据为准。
- 最终独立实现 Review `59cb`：Accept、findings=[]，R1/R2/R3 closed。正式 quality 原件 `/tmp/bolt-t6-final-acceptance-quality/RESULT.md`、`full-response.json`、`snapshot`，命令实际 exit0、snapshot Accept；十二单元各 B/10，机器 2/2/0、语义 2/2/2。历史 load/performance stale 与 handoffReadiness=0 保留，不手改分数、时间或 exclusions。
- 关闭账本沿用“恢复状态”已登记的 T1–T5 完成/取代/延期边界；最终集成义务由 full8 承接。full1–6 的失败/incomplete/未知 exit、full7 旧输入成功与 7caf 原 Block/评分均保留。full4 具体 cause 仍 unknown/unresolved，不以本次 Accept 冒充根因证明。full8 精确 TLS 材料路径/独立删除观察缺口与 R3 专用 DEV Compose 非实际网络证明，按最终 Reviewer 接受的边界保留；未知 RphHzdkY 不删除。
- 下一步仅由父安排独立 Reviewer 对照 `/tmp/bolt-state-closeout/` 的 before/after 原 bytes、exact-state.diff、原 full8 与正式 Accept，审查本 checkpoint 和后续两时点阶段归属；本候选尚无 state Review Accept。`docs/PLANS.md` 的全部步骤完成才 passing 与迁移后检查顺序须由独立 Review 明确 checkpoint 归属，不能预勾后两项或默改规则。
- 尚未执行迁移及本次 `./scripts/verify.sh drift`、`./scripts/verify.sh clean`、`./scripts/verify.sh quality` scope 校验、`./scripts/verify.sh cleanup`、`./scripts/verify.sh handoff`。后续按原两时点规则执行，cleanup 先只读 scan，allowlist 审查后才考虑 --apply；保留原 full 时间/指纹及迁移后 stale，不重跑 Java/HA/full。不提交、不部署、不动用户 .gitignore/.pi、私有 m2 或未知材料；Loop4 继续 PAUSED。


## T6 状态关闭清单

独立 Reviewer 40c50a94 已对实际迁移、机械 href 例外及关闭义务精确 Review；本次实现验收及状态关闭已完成，五项廉价检查均已实际完成。本节取代此前 active/未迁移的当前执行指令；历史原文不改写。当前实施任务 none；原验收归属、历史失败、stale 与残余风险仍保留。

- [x] 取得实际 full 和独立 Accept 后才勾选通过及登记各任务 passing。然后仅迁移执行状态内容至 completed，更新 PROGRESS 与 active/completed 索引；独立 Reviewer 比较精确迁移前后 diff 和原验收记录，不仅检查路径。不得此时改需求、命令、范围或实现事实。
- [x] 状态迁移后运行验证分层列出的 drift/clean/quality scope/cleanup/handoff；先审 allowlist 再考虑 cleanup --apply。原 full 时间/指纹/stale 保留，不为纯状态迁移重跑 Java/HA；非状态改动或原验收无效则恢复 active、重新验收。

迁移后首项 `./scripts/verify.sh drift` 实际 exit1，准确日志 `/tmp/bolt-state-closeout/migration/drift.log`：PROGRESS 缺 checker 要求的字面 active plan: none，以及稳定设计/设计索引仍指 active 计划导致断链。前者已在允许的 PROGRESS 状态内修正，未重跑、不能称已验证；后者超出本批允许状态范围，未修改稳定设计或 index、未弱化 checker。按失败先归因停止，clean/quality/cleanup/handoff 均未运行，两项关闭义务仍 pending；交父处理范围边界，不能称整体关闭或 scope fresh。
