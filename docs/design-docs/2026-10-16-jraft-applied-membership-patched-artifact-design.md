# JRaft 已应用成员配置访问器私有制品设计

> 状态：已批准。用户已批准私有可复现补丁制品策略及统一执行；独立 reviewer `fd291440` 第二轮 `Accept`，四项 findings 已关闭。后续按「本地最小修复验证与生产交付分轨」及「Runtime 最小接入语义」完成本地候选接入和原三项 HA，详见下节。生产发布/解析、可复现性和最终全仓门禁仍未完成；原方案及旧证据保留为历史，不代表当前仍缺少本地候选。

## 收尾补充：HA初始化等待首份已应用配置（独立设计Review Accept）

reviewer `049cd197-c693-497` 中止恢复后Accept，仅初始化精确异常链重试；确定性序列必须经过实际等待helper，永久暂缺仍超时，不准返回默认配置。

本地稳定full在AutomaticFailureTlsHaTest构造Cluster的就绪轮询暴露 `Applied configuration unavailable`，根因 `No applied configuration`。补丁copyAppliedConfiguration在index<=0或无已应用entry时按契约拒绝，不能回退bootstrap配置；该读取不是等待首份配置的API。现有上游focused awaitStable也在首份apply前轮询，生产Runtime应继续原样失败关闭。

仅HA fixture初始化、ticks尚未启动时允许重试这一精确异常链：外层IllegalStateException消息Applied configuration unavailable、直接cause为IllegalStateException且消息No applied configuration；其他错误/队列满/停止/joint/未知cause必须传播，不catch-all。保持原BUDGET及实际三个voter全量集合断言，成功仍必须读到真实applied配置，超时未就绪即失败。test-only predicate由单独小测试验证可重试缺配置与其余错误不重试，并模拟先暂缺后返回精确预期membership，避免依赖偶然启动时序；不改补丁/POM/生产Runtime或延长等待期限。实际HA三场景窄层验证后独立Review，结果归10-11计划关闭账本。

## 当前本地验证状态

根 POM 已唯一管理 `com.co.kc.thirdparty.sofa:jraft-core:1.4.1-imchat.1`，im-raft 消费该私有坐标。候选只安装在 `/tmp/jraft-artifact-m2`，未发布到 Registry，也未安装到默认 `~/.m2`。其他机器须先按仓库 recipe 准备源码构建并隔离安装，或在发布完成后配置获准 Registry；当前普通 `mvn` 不能自行取得这个尚未发布的私有制品。

本地 Runtime 已用 `Node.getAppliedConfiguration(commandTimeoutMillis)` 读取 Leader、Follower、learner 本机已应用的 stable voters/learners。joint 失败关闭，不扁平化 old/new、不回退初始配置或缓存；超时与中断保留 cause，中断恢复线程标志。同步访问器不允许 FSM callback 及其同步 continuation 重入，相关成员读取完成链已处理线程边界，重入守卫保留。该 API 不提供 quorum/ReadIndex 证明，也不保证与业务状态读取构成原子快照。

当前单候选通过访问器 16 项、Runtime 22 项、Broker 聚焦 87 项及原三项 `AutomaticFailureTlsHaTest`。Runtime reviewer `a682f997`、HA fixture reviewer `cf4028e1` 均 scoped Accept；三项 HA 日志为 `/tmp/follower-ha-three-final.log`。该测试使用真实三 Broker TLS，但 Gateway ACK 是替代边界，不是真实 Gateway E2E。原 Leader-only API 导致的 Follower 行为阻塞仅在本地候选/三项 HA 范围解除。

私有 Registry、双构建、完整依赖输入锁及外部有效模型证据、最终全仓门禁仍未完成；本地定向通过不能替代完整上游及发布验收。A5 保持 active/WIP=1，精确命令、指纹、历史失败归因及下一步由[活跃计划](../exec-plans/active/README.md)维护。以下初始方案与旧证据保留其历史语境，后续决定优先。

## 摘要（初始方案）

设计建立时项目使用 `com.alipay.sofa:jraft-core:1.4.1`。Spike 证明未修改的 `1.4.1` 没有 `Node.getAppliedConfiguration(timeoutMillis)`，应用层无法在不复制 JRaft 内部状态、不依赖 `NodeImpl` 强转、不引入 FQCN 类覆盖的前提下读取本地 FSM 已应用的完整成员配置。目标访问器需要在 FSM 排队点捕获 `appliedIndex`，在 `LogManager` 读锁内复制配置，并保留 voter、learner、joint old/new 成员及配置锚点。

推荐建立一个可复现重建的私有 `jraft-core` 制品：以固定的上游源码提交为输入，仅应用经过 Review 的小补丁，执行完整 JRaft Maven 构建和上游测试，发布到组织批准的私有 Maven Registry，并使用不同的私有坐标或版本。项目只解析这一份完整 `jraft-core` 实现，禁止在应用 classpath 上叠加七个替换类。生产发布与生产解析必须先完成来源提交可验证、全源码构建、完整上游测试、生命周期与真实 HA 验收，以及 Registry 所有权确认；用户已批准的本地来源核验、recipe、隔离安装和消费者编译不等待 Registry 决策。

初始实施只增加本设计和 `third-party/jraft-applied-membership` 的来源准入材料，当时尚无正式 patch 或可构建制品；当前本地候选状态见上节。`/tmp/jraft-applied-spike/sync-spike-only.patch`、Spike 日志和来源 SHA 文件属于研究输入，不复制到仓库，不作为可发布源码或生产证明。

## 背景

Spike 在 `/tmp/jraft-applied-spike/REPORT.md` 中记录了以下事实：

- 本机缓存的 JRaft `1.4.1` 和 `1.3.14` API 都没有所需访问器；没有对更新的官方版本作出可用性声明。
- `sync-spike-only.patch` 验证了七个 JRaft 类型的最小研究修改：`Node`、`NodeImpl`、`FSMCaller`、`FSMCallerImpl`、`LogManager`、`LogManagerImpl` 和 `AppliedConfiguration`。
- 同步接口原型为 `AppliedConfiguration Node.getAppliedConfiguration(long timeoutMillis)`，超时在调用方等待私有 future，FSM 事件只完成私有结果，不把外部回调带入 FSM 线程。
- 结果需要包含不可变的 `appliedIndex`、`configurationAnchor`、new/old voters、new/old learners 和 stable 状态。锚点可以是日志配置 entry 或快照锚点，不能被描述为原始配置变更索引。
- 已完成的真实三 voter 加 learner、Follower、快照重启和不可变性 Spike 测试证明了研究代码的局部可行性；这些测试不等于完整 JRaft 上游测试或生产验收。
- 尚未完成真实 joint transition、learner 安装、lagging follower snapshot install、错误/部分应用、队列与生命周期竞争、stress、完整上游回归套件和项目 HA 验收。

初始调查时根 `pom.xml` 在 `<sofa-jraft.version>` 固定 `1.4.1`，dependency management 使用 `com.alipay.sofa:jraft-core`，`im-plugin/im-raft/pom.xml` 直接声明该依赖。当前未发现 `distributionManagement`、专用 Maven publishing profile 或可复用的私有 Registry 配置；本机也没有可读取的 Maven `settings.xml`。这些检查只读取名称和结构，未输出或接触凭据。

本机缓存可定位到：

- `jraft-core-1.4.1.jar` SHA-256：`bb263a996f3ab831859c579242b6b19eb978d78aaa6d9093e094896e0af26d1b`
- `jraft-core-1.4.1-sources.jar` SHA-256：`0aa1a58047ae16befbb2ac545d48f41a3f2028d08f7de9f06cddf555160a1ef3`
- Spike 还保存了 `/tmp/jraft-applied-spike/source-sha256.txt`、原始源码、补丁和编译/测试日志。

这些 SHA 只能证明当前本机缓存文件的内容。由于缓存 sources JAR 的来源 source commit、上游 tag 签名和源码归属尚未建立可验证链路，不能把它们单独当作可发布源码证明。

## 目标与非目标

- 目标：定义一个完整 JRaft 源码重建、补丁、校验、测试、打包、来源和许可证保留方案。
- 目标：规定项目如何选择唯一的私有坐标/版本，并防止原始 `jraft-core` 与补丁实现同时进入运行时。
- 目标：为后续 implementation plan 提供小规模 recipe/patch/provenance 文件范围和验证边界；法律文件不得受文件数预算限制。
- 目标：记录当前 Artifact Registry 未决状态，以及本地开发安装的有限用途。
- 非目标：本轮不改根 `pom.xml`、`im-plugin/im-raft` 或任何生产代码。
- 非目标：不复制 JRaft 全源码到本仓库，不制作类路径 overlay，不发布制品，不使用凭据。
- 非目标：不承诺某个尚未核验的官方 JRaft 新版本可替代补丁制品。
- 非目标：不改变 JRaft 日志、快照或 protobuf 数据格式。

## 已知事实与约束

- 所属模块：JRaft 是 `im-plugin/im-raft` 使用的第三方基础设施依赖；Broker 只能通过 `im-raft` 已有抽象消费能力。
- 公共 API：访问器返回一次性的不可变快照，不提供 quorum/read-index 证明，也不保证与另一个业务快照读取 API 原子一致。
- 配置语义：`appliedIndex` 是 FSM 应用水位；`configurationAnchor` 是该水位解析出的最新配置 entry 或快照锚点，明确不是原始配置变更索引。
- 生命周期：非正超时、FSM 回调重入、停止/错误节点、队列满、队列服务期间停止或 FSM 错误都必须失败关闭；调用方等待必须在 Node 锁外进行。
- 许可：现有 `jraft-core` POM 未在本地缓存 POM 中声明许可证正文；最终制品必须随上游源码保留 Apache-2.0 `LICENSE`、`NOTICE` 及依赖第三方 notices，并在 provenance 中记录来源。
- 可复现性：构建必须固定 source commit、补丁文件 SHA-256、输入源码包 SHA-256、依赖版本/解析结果、JDK/Maven 版本和构建时区；制品归档需消除或固定时间戳，并说明 RocksDB/JNA 等 OS/native 依赖的验证矩阵。

## 方案概览

```mermaid
flowchart LR
    S[固定上游 source commit] --> P[Reviewed patch]
    P --> V[License/NOTICE + provenance]
    V --> B[隔离 Maven full source build]
    B --> T[完整 JRaft tests + targeted HA tests]
    T --> A[私有唯一 jraft-core 坐标]
    A --> I[im-raft 单一实现依赖]
```

构建输入只允许来自已登记的固定上游源码归档或等价的可验证源码材料。构建脚本先验证来源 SHA、补丁 SHA、补丁应用前基线和许可证文件，再在隔离工作目录执行完整上游 Maven 构建与测试。构建产物、sources、POM、licenses 和 provenance 一起生成校验清单；只有所有必需测试通过才允许进入发布步骤。

运行时只能解析私有坐标指向的一个完整 `jraft-core` JAR。依赖检查必须发现并拒绝 `com.alipay.sofa:jraft-core` 原始版本、旧私有版本或第二份同名实现同时存在。项目的业务仓库只消费该私有制品，不能把补丁类放进 `im-raft` 或其他项目模块。

## 详细设计

### 制品坐标和依赖选择

固定私有 GAV 为 `com.co.kc.thirdparty.sofa:jraft-core:1.4.1-imchat.1`，沿用项目 `com.co.kc` 命名空间并明确第三方来源；本地构建和后续生产发布使用同一 GAV，不依赖 Registry 才确定坐标。不得重写官方 `com.alipay.sofa:jraft-core:1.4.1`。

本仓库实际版本唯一拥有者是根 `pom.xml`，不引入或假定存在 `im-dependencies`。后续根 POM 将管理上述私有 GAV 和版本；`im-plugin/im-raft/pom.xml` 仅声明该受管私有依赖，不重复版本。所有可能传入官方 `com.alipay.sofa:jraft-core` 的传递路径必须显式排除；项目依赖树与最终运行包均必须拒绝官方坐标、不同私有版本或第二份相同核心 FQCN。原 `sofa-jraft.version` 管理项应同步删除或改为私有版本的唯一属性，不能留下两个有效入口。

消费者证明不止检查依赖树：在隔离本地仓库中编译直接调用 `Node.getAppliedConfiguration(long)` 的真实消费者，证明解析到新增 API；再核对 `dependency:tree`、编译 classpath 与最终 JAR 中 `com/alipay/sofa/jraft/Node.class`、`core/NodeImpl.class`、`core/FSMCallerImpl.class` 各只有一个来源，且来自上述私有 GAV。缺少该 API、官方与私有并存、重复 class 都应稳定失败。

### 仓库内构建 recipe、patch 和 provenance

后续构建材料范围如下；不复制七个完整 JRaft 类，不把法律文件强行合并以满足文件数预算：

- `third-party/jraft-applied-membership/README.md`：输入、固定 GAV、许可证、本地验证和生产发布门槛。
- `third-party/jraft-applied-membership/applied-membership.patch`：相对核验过的上游 checkout 根目录的 reviewed patch，包含源码、测试和必要 POM/可复现归档修改；不能原样发布 Spike patch。
- `third-party/jraft-applied-membership/build.sh`：临时隔离目录、SHA/patch 校验、完整 Maven build/test/package、确定性 payload 和证据输出。中文脚本契约明确输入、退出码、隔离、输出及失败传播；默认不发布、不接触凭据。
- `third-party/jraft-applied-membership/provenance.yml`：固定 source commit、源码归档与 patch SHA-256、工具链、依赖摘要、固定 GAV、许可证摘要及验证条件；未知项不得猜测。
- `third-party/jraft-applied-membership/LICENSE` 和 `third-party/jraft-applied-membership/NOTICE`：两个独立的真实法律文件，保留上游原文和适用第三方 notices，不用 README 或自定义合并文本替代。构建时将其放入 binary JAR 与 sources JAR 的 `META-INF/LICENSE`、`META-INF/NOTICE`，对内容逐项验 SHA；完整源输入中的其他 required notices 同样保留。

### 上游 checkout 与 patch 的精确边界

Spike 的路径是从 sources JAR 解压得到的 `a/com/alipay/...`，并不是上游 checkout 路径。正式 patch 必须重新生成，以 checkout 根目录为基准，使用 `git apply --check applied-membership.patch` 检查，然后应用。七个生产类型精确落点为：

- `jraft-core/src/main/java/com/alipay/sofa/jraft/Node.java`
- `jraft-core/src/main/java/com/alipay/sofa/jraft/FSMCaller.java`
- `jraft-core/src/main/java/com/alipay/sofa/jraft/core/NodeImpl.java`
- `jraft-core/src/main/java/com/alipay/sofa/jraft/core/FSMCallerImpl.java`
- `jraft-core/src/main/java/com/alipay/sofa/jraft/storage/LogManager.java`
- `jraft-core/src/main/java/com/alipay/sofa/jraft/storage/impl/LogManagerImpl.java`
- `jraft-core/src/main/java/com/alipay/sofa/jraft/conf/AppliedConfiguration.java`

同时允许且必须 Review 的构建变更为上游根 `pom.xml` 和 `jraft-core/pom.xml`：固定私有发布 GAV、可解析的 parent/依赖关系、固定归档时间与 source/binary 法律文件打包配置。输出私有 POM 不能仍声明官方 GAV 或依赖无法解析的私有 parent；若采用自包含消费 POM，生成过程与内容也必须纳入 patch/recipe 和双构建比较，不能部署时临时手改。

新增 focused suites 的固定路径为 `jraft-core/src/test/java/com/alipay/sofa/jraft/core/AppliedConfigurationLifecycleTest.java`、`jraft-core/src/test/java/com/alipay/sofa/jraft/storage/impl/AppliedConfigurationSnapshotTest.java` 和 `jraft-core/src/test/java/com/alipay/sofa/jraft/core/AppliedConfigurationClusterTest.java`，分别覆盖同步生命周期、日志/快照边界和真实网络 joint/learner/install-snapshot；stress 用这些 suite 内确定性 barriers 和有界轮数，不另加无限循环。它们连同原有完整 `jraft-core/src/test` 套件运行，不能代替上游测试。

任何 patch 只能相对于固定 source commit 应用；上游升级必须更新源码摘要、patch、测试与 Review。当前本地 sources JAR 不包含已核验的完整 checkout 根 POM、测试与 release/source commit 对应证据，不能据此声称完成完整上游重建。后续可在批准的来源获取路径补齐完整上游源码与提交证明；本轮不执行网络获取、不虚构提交 SHA。

### 构建与可复现性

`build.sh` 的默认动作只做离线或预置输入验证、源码构建和测试，不连接生产 Registry。它必须：

1. 检查 Java/Maven 版本、目标 OS/native 依赖条件和工作目录为空。
2. 验证来源归档与 `sourceCommit` 的对应关系。若只有本地 sources JAR 而无可验证 source commit，直接返回 `SOURCE_PROVENANCE_MISSING`，不能宣称 reproducible build。
3. 验证正式 `applied-membership.patch` 的 SHA-256、checkout 基线可应用性和变更范围：七个类型、上述三个 test suite 以及经 Review 的根/module POM 和归档配置；不能只按七个类型过滤而遗漏制品构建变更。
4. 校验 `LICENSE`、`NOTICE` 和上游 POM 中的许可证/依赖来源，输出不含秘密的 JSON 或 YAML provenance。
5. 使用固定 `SOURCE_DATE_EPOCH` 或等价 Maven reproducible-build 配置，固定 UTC 时区、归档顺序和文件权限；记录 JDK、Maven、OS/architecture 以及 RocksDB/JNA native classifier。
6. 运行 JRaft 完整源码测试、打包、sources/javadoc（若上游构建契约要求）和 SHA-256 清单；失败传播，不能用 `-DskipTests` 代替。
7. 在两个独立、全新工作目录及隔离 Maven 本地仓库中按相同固定输入各构建一次，逐字节比较 binary JAR、sources JAR 和消费 POM 的 SHA-256；运行日志、墙钟时间和非确定性测试证据单独归档，不嵌入确定性 payload。验证归档内 LICENSE/NOTICE、source provenance 和其摘要一致。
8. 使用其中一份完整产物执行隔离本地 `install`、新增 Node API 消费者编译、项目侧 `im-raft` integration、依赖/归档单实现检查和真实 HA 验收。Registry 未决不阻塞这些本地步骤；只有生产发布/解析前必须确认 Registry 与权限。

完整源码构建和完整上游测试是制品发布门槛。Spike 的 `verified-compile.log`、`sync-real-test.log`、`sync-boundary-test.log` 和 `sync-lifecycle-test.log` 只能作为设计输入，不能替代这些门槛。

### API 语义与测试范围

后续 patch 的实现必须保持 Spike 已决定的同步接口和以下不变量：

- FSM 事件捕获水位后，在 `LogManager` 读锁内复制 new/old voters 和 learners；返回对象及其集合不可变。
- 未应用配置、索引早于快照锚点、节点停止、FSM error、队列满、超时、线程中断和 FSM 回调重入均失败关闭。
- Snapshot load 完成并由既有 JRaft 路径更新 LogManager 后，后续访问器读取新的快照锚点；读取不能穿透正在进行的 restore。
- 未提交配置后缀不得污染已应用结果；配置冲突截断、learner 变化和 joint-to-stable 迁移必须有确定性测试。
- 超时只约束私有完成等待；若需要严格覆盖 JVM 锁竞争的总墙钟上界，必须另行批准额外 admission 设计，不得在实现中隐含扩大范围。

退出条件必须包括真实 joint transition、add/remove learner、lagging follower snapshot install、snapshot failure、partial apply error、queue full、shutdown/admission/reentry、私有 future 取消与 late completion 隔离、stress 和完整 JRaft 回归套件。项目侧还必须运行 im-raft integration 和当前真实 HA scenario。这些测试未通过时，生产采用保持 `blocked`，但不阻塞来源核验、recipe 开发及本地 RED/GREEN 验证。该同步接口只完成私有 future，不新增 executor；异步公开 future 方案中的 completion executor rejection 不适用。

## 可靠性与安全

构建脚本不能读取或打印凭据，也不能把 Registry URL、token、用户名写入 provenance。发布动作应由受控 CI 或明确的人工命令执行，并与构建 recipe 分离；本设计不选择 Registry URL。上传前必须对制品、sources、POM、license、notice 和 provenance 做摘要核对。

私有制品只允许组织批准的 Registry 作为生产解析来源。当前没有发现可复用的内部发布 profile 或仓库配置，因此 Registry owner、仓库名称、写入权限和 retention policy 是开放交付项。确认前可以用 `mvn install` 将构建结果安装到隔离本地 Maven 仓库供开发验证，但该结果不满足生产来源证明，也不能被描述为发布完成。

依赖单实现检查属于运行安全边界：构建期检查依赖树，交付期检查最终包清单；发现两个版本或同 FQCN 重复实现即失败。禁止把 patch class 放到 `im-raft`、Broker 或测试 fixture 中绕过检查。

## 运维与交付

生产交付顺序为：来源与许可证审核 → 隔离完整构建 → 完整上游测试 → patch 评审 → im-raft integration → 真实 HA 和故障矩阵 → 私有 Registry 发布 → 消费方切换 → 回滚验证。回滚目标是上一个已审核的完整私有制品；不能回滚到“原始 JAR + overlay”。

制品 provenance 必须能回答：源码来自哪个 commit、输入文件是什么、补丁是什么、由什么工具链构建、依赖解析到什么版本、许可证文件是否保留、哪些测试通过、制品 SHA 是什么。每次上游更新都重新生成这些证据并重新 Review。

### Artifact Registry 决策

当前结论为：**Registry 未决，不能发布；开发阶段允许隔离本地 `install`，生产阶段必须使用组织批准的私有 Maven Registry。** 设计不猜测仓库名称、URL 或凭据，也不要求现在向用户索取；生产发布与生产解析前由依赖/发布 owner 确认可写仓库及其权限，并将非秘密的仓库标识写入后续计划与 provenance 模板。

## 风险与回滚

- 风险：本地 sources JAR 没有可验证 source commit，无法证明可重建输入。处理：保持发布阻塞，补齐上游归档、commit、签名或可信来源映射。
- 风险：JRaft patch 横跨 FSM、Node 生命周期和 LogManager 锁，真实 joint transition、snapshot failure 和 stress 仍可能暴露竞态。处理：未完成退出条件前不发布。
- 风险：依赖坐标选择不当会造成原始实现与私有实现并存。处理：使用独立私有坐标或不可歧义私有版本，并在依赖树和最终包做单实现断言。
- 风险：RocksDB/JNA native 行为随 OS/architecture 变化。处理：provenance 记录 native classifier 和验证矩阵；未覆盖的平台保持不支持。
- 回滚：切回上一个完整、带 provenance 的私有制品版本，并重新运行项目侧 integration/HA 检查。
- 后续边界：用户策略已经批准；独立设计复审通过后可统一推进来源核验、recipe、完整源码重建/套件、隔离 install 和根 POM/消费者验证。只有私有发布 profile、生产发布与生产解析等待 Registry owner 和权限确认；不重复向用户请求策略授权。

## 验证策略

- 研究输入复核：读取 `REPORT.md`、`sync-spike-only.patch`、`source-sha256.txt` 与 Spike 日志；不把 `/tmp` 产物当作发布制品。
- Recipe 验证：在空临时目录执行 source checksum、patch checksum、许可证存在性、patch 范围和确定性输出检查。
- JRaft 验证：完整上游 Maven 测试与打包，禁止只编译七个类。
- 行为验证：joint voters/learners、learner 变化、未提交后缀、冲突截断、snapshot install/failure、FSM error、lifecycle/admission、timeout/interruption、queue-full、reentry 和 stress。
- 项目验证：im-raft integration、依赖单实现检查、适用的 architecture/quick/clean/full，以及真实 routing HA 场景；命令和 freshness 在后续 execution plan 固化。
- 发布验证：制品、sources、POM、LICENSE、NOTICE、provenance 的 SHA-256 和私有 Registry 拉取验证；没有 Registry 选择或来源 commit 时，生产发布/解析结果为 blocked，不据此阻塞本地来源核验与开发验证。

## 后续文档与执行

本轮只修改本文及 `third-party/jraft-applied-membership`，不编辑 `docs/design-docs/index.md`、`PROGRESS.md`、A5 active plan、根 POM 或模块代码；这些文件由父会话分别维护。父会话应登记设计 Accept 并在唯一 active execution plan 内安排执行，明确每个 recipe 文件、完整命令、失败归因和恢复步骤；父会话合并用 checkbox 草稿保存在 `/tmp/jraft-patched-artifact-before/parent-plan-checkboxes.md`。

后续实施完成后再按实际稳定事实更新 `im-plugin/im-raft/README.md`、依赖管理文档和必要的可靠性/安全说明。若发现构建 provenance、依赖单实现或第三方许可证形成可复用工程规则，再单独评估是否更新 Harness 规范；本设计不提前修改共享规则。

## 附录 A：备选方案与行业依据

### 方案一：等待官方上游 API 发布

优点是减少长期 fork 维护和私有发布责任。缺点是时间不可控，当前没有已核验的官方新版本提供该访问器，不能满足当前能力阻塞。若上游正式版本后续具备同等语义，需重新比较生命周期、快照锚点和兼容性，而不是自动切换。

### 方案二：完整源码 + reviewed patch + 私有唯一制品（推荐）

优点是只有一份完整实现，类路径和二进制边界清晰，可审计、可回滚、可重复构建。代价是需要维护上游 rebase、完整回归、许可证 provenance 和私有 Registry。该方案符合“保留 patch 与构建 recipe，不做源码 vendoring”的范围。

### 方案三：应用 classpath overlay

Spike 适合用 overlay 做局部验证，但生产会产生重复 FQCN、类路径顺序依赖、二进制兼容和打包工具差异。它不能提供单一完整 JRaft 实现，故拒绝作为生产方案。

### 方案四：把七个完整类 vendoring 到本仓库

可绕过外部制品发布，但会复制大型第三方源码、扩大许可证和安全维护面，并使项目承担与上游源码同步的额外边界。用户已限定“patch 文件 + build recipe”，因此不采用。

本设计没有进行网络研究，也没有引用未核验的外部版本或 Registry 事实。上游许可证、完整源码与提交/release 对应证据是后续来源核验工作的输入缺口，必须在宣称完整可复现构建通过之前补齐；不阻止复审后启动来源核验与 recipe 工作。

## 附录 B：决策记录

| ID | 来源/问题 | 最终决策 | 理由与影响 |
|---|---|---|---|
| D1 | 如何让 `Node.getAppliedConfiguration(timeoutMillis)` 进入生产 | 采用固定上游源码 + reviewed patch + 完整 Maven 构建的私有唯一制品 | 避免 duplicate FQCN overlay；需要完整上游测试、许可证和 provenance 维护 |
| D2 | 是否把 JRaft 全源码复制到项目 | 不 vendoring；仓库只保存 patch、recipe、provenance 和法律文件 | 保持第三方源码边界小，减少维护和许可复制面 |
| D3 | 当前只有本地缓存 sources JAR，能否直接发布 | 不能；缺少可验证 source commit 与来源链 | 本地 SHA 可复核缓存文件，但不足以证明可重建上游输入 |
| D4 | 本地开发如何验证私有制品 | 允许隔离本地 Maven `install`，仅限开发验证 | 没有 Registry 选择、发布权限或完整来源证明时，不得作为生产来源 |
| D5 | 生产 Registry 选择 | 暂保持未决，仅在生产发布/解析前由 Registry owner 确认；本地固定 GAV、来源核验、recipe、完整测试和隔离 install/consumer compile 不等待 Registry | 当前 POM、profile 和 settings 未提供可复用配置；不猜测 URL、凭据，不重复请求已批准的私有制品策略 |
| D6 | 访问器结果语义 | 返回不可变 full membership 快照，包含 applied index、configuration anchor、joint old/new voters/learners 和 stable 状态 | anchor 不冒充 original index；快照与 FSM 应用水位保持明确边界 |

## 后续决定：上游快照测试确定性修复

用户在失败诊断后批准继续，正式补丁允许新增修改 `jraft-core/src/test/java/com/alipay/sofa/jraft/core/NodeTest.java` 的 testSnapshotSync，除此之外不扩大上游测试修改范围。固定源码 commit `cb1a0c7ac16ff146ad8abb04b1bcb035270bb234`；恢复归档 SHA-256 `63a95b4cbc7a3384ca22abaa245cb3fd0fb713d5809381ecdac168656e0d6d0d`，旧最终补丁 SHA-256 `ceb67c578c26b59c4c9529c42c0113e83df4ae13931423c838bf20559b80effd`。仓库旧 Spike 路径补丁和 provenance 不作为最终验收事实，更新 recipe 前须核对恢复材料。

因果诊断 `/tmp/follower-snapshot-diagnostic-1.log`：随机选中 492 次 snapshot，1 次 OK、491 次 EBUSY，日志 1000 条；成功数大于 1 的断言失败后没有执行正常 shutdown，产生节点残留和后续 setup 连锁失败。未补丁版单项也复现此断言；补丁版曾单项通过、完整套件仍失败，不认定补丁导致或修复随机性。该日志是独立插桩诊断，不是验收证据。

独立设计 reviewer `467734ec` 首轮 Revise：本方法 NodeOptions 周期性 snapshot 必须禁用（不能保留覆盖前值的10秒配置），避免与显式请求竞争。回调先记录 Status 并在 finally 释放屏障，测试线程断言两次 OK、两次实际 onSnapshotSave 及完整有序 LogId 相等；不能仅断言两个 OK 回调。已纳入修订。

修复以两批日志和明确采样位置触发两次 snapshotSync，由测试线程通过有界完成屏障等待每次成功后再提交下一批，FSM 线程不能等待自身回调；不新增 sleep、不减少 1000 条日志、不降低成功数和 metadata 比较断言，保留 FSM 外 snapshotSync 拒绝断言。shutdown/join 放在真正 finally 中，不吞掉测试失败。本方法原先不包含 restore 断言，不冒称已有；既有 restore/install suite 必须保留。独立设计 Review 后先在新隔离源码实现，通过聚焦和完整上游测试，再评估正式 patch/recipe 收敛与运行时接入；禁止跳过失败测试或提前切换生产依赖。

## 后续决定：休眠声明与实际构建输入审核

用户批准区分原始缓存 POM 审计与有效/已解析输入准入。未激活 profile（包括外部 IDE m2e profile）的动态坐标声明只记录路径、所属模型/profile、版本及原始 POM SHA，不单凭其存在拒绝本次构建；不添加特定 GAV 白名单。实际仓库路径中的已解析版本与本次有效模型的依赖、插件、扩展仍禁止动态/SNAPSHOT/未解析值，全部 JAR/POM 原始字节继续锁定并比较。该决定替代此前对所有休眠坐标声明一律拒绝的保守策略，不取消 XML 安全解析、报告/跳过校验和发布限制。

当前缓存清单仍是隔离仓库材料的保守采集，不冒称完整的插件执行追踪。原始模型审计与实际输入清单分开输出；无法证明 active/effective 边界时失败关闭或明确阻塞，不能把休眠 profile 排除等同于完整解析依赖图证明。先通过回归测试和独立 Review，再刷新真实双构建；无安装、发布或项目依赖切换授权扩张。

## 后续决定：本地最小修复验证与生产交付分轨

用户明确批准收窄当前工作：只构建一个当前 reviewed 补丁候选 JAR，运行三个 AppliedConfiguration 定向 suite（16 项），核验 API 与单实现，再进行隔离消费者编译、im-raft 接入和原三项真实 HA 回归。使用一个工作目录及现有隔离依赖缓存，Maven 串行执行，不启动无关服务、不设未经测量的 18GiB 硬门槛，不自动清理文件。若实际写盘失败则停止归因。

该决定替代此前将双隔离可复现构建、全量依赖输入锁和完整上游回归作为每次本地修复前置条件的执行顺序；允许本地开发构建和隔离安装/消费者验证，不允许发布或声称生产采用通过。当前单 JAR 不视为最终发布制品；测试 selector 明确只是 16 项定向证据，不修改原 recipe 的完整模式或降低发布验收标准。双构建、完整制品来源/依赖锁、生产 Registry、仓库最终门禁仍独立保留未完成，不能用定向测试替代。

在项目依赖和运行时修改前仍须核对具体 API 语义及独立 Review，不用初始配置或 Leader 远端状态替代 Follower 本机已应用成员事实。原三项 HA 是本轮行为修复判据，未运行或失败时 Follower Block 不关闭。任何 Gate 失败先归因，不同时启动多套 Maven 或无关应用。

## 后续决定：Runtime 最小接入语义

在隔离消费者/单实现检查通过后，RaftRuntime.membership 改用 `Node.getAppliedConfiguration(commandTimeoutMillis)`。现有 RaftMembership 仅表达稳定 voters/learners，不增加新协议：stable 时排序映射；joint 失败关闭，不将 old/new 扁平化，不返回初始配置或缓存。InterruptedException 恢复线程中断标志后保留 cause 抛 IllegalStateException，TimeoutException 同样保留 cause；既有停止/错误/满队列失败继续传播。根 POM 仅保留私有 GAV 的唯一管理版本，im-raft 消费该管理项，不混用官方 core 或 FQCN overlay；只在隔离 Maven 仓库验证本地候选，生产发布/解析仍 blocked。

同步访问器禁止 FSM callback 重入。实际提交 future 及 ReadIndex 完成回调可能由 FSM 线程触发，不能将所有 thenCompose 误认为应用线程。先确定性验证现有 continuation；若暴露重入，仅在真实需要读取成员的路径切换到既有执行资源，保持重入守卫，不引入通用 executor 框架或批量异步化。线程边界调整需具体回归及独立 Review，不由本文预先认定所有调用安全。

## 附录 C：独立 Review

- 首轮：独立 reviewer `fd291440`，结论 `Revise`，四项修订：区分已批准策略/本地执行与生产 Registry 门槛；明确 checkout patch/POM/test 边界；固定根 POM owner、私有 GAV 与单实现消费者证明；删除同步方案不适用的 completion executor rejection。
- 第二轮：同一独立 reviewer `fd291440` 结论 `Accept`，四项 findings 全部关闭；用户既有策略批准有效。
- 进入 `已批准` 前必须由独立上下文检查：来源证明缺口、私有坐标与单实现约束、许可证保留、可复现时间戳/native 依赖、完整测试退出条件、Registry 决策和与用户限定范围的一致性。
- 用户已批准策略和统一执行，无需重复询问。此轮复审时仅开始本地来源核验与 fail-closed recipe 材料，尚未执行完整 patch、构建、测试、安装与消费者验证；该历史状态已由后续本地候选验证取代，生产发布仍未完成。
