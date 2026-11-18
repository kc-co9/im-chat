# 官方 JRaft 治理迁移与自有运维 CLI 实施计划

> 归档状态（2026-11-07）：T1–T12 `passing`，`bootstrapComplete=false`。新冻结 full／真实 HA、独立完整 T11 Review、正式 quality 与状态迁移 Review 均已通过；不自动提交。
>
> 本页维护当前合同、任务状态和下一步。[完整执行历史](../history/2026-10-30-official-jraft-and-ops-cli-execution-history.md)保留拆分前全部正文，包括精确文件清单、命令、失败、Review 与旧 PROGRESS 原件。历史中的“当前”、待办和权限不得覆盖本页。历史正文的相对路径沿用拆分前 active 目录或其明确声明的仓库根基准，不因搬移自动重解释。

## Sprint Contract

- 目标：移除私有 JRaft 依赖和补丁，保留自动故障接管、受控在线成员维护，提供经 IAM/Ops/Audit 接入的自有交互式 CLI。
- 范围：im-raft、Broker SDK/server、Management IAM SDK/server、Ops、Ops CLI、相关测试与规则生命周期。按 T1–T12 顺序，WIP=1。
- 非目标：旧版本兼容、数据自动删除、自动漂移修复、resetPeers、旧许可超时抢占、强制解锁、无人值守、Device Flow、Refresh Token、通用管理平台、无关重构。
- 验收：批准设计的不变量有确定性负例；新 voter 计票、降级后复制和新格式恢复成立；CLI 权限与执行许可独立；实际完整门禁、独立 Review Accept 和正式 quality 均通过。
- 风险：原生完成分类、FSM 队列/Node 写锁、迟到执行、两步角色切换、同 revision 观察分母、自移除确认、CLI 认证、撤销持久化与并发、双安全链、UNKNOWN operation 重试。
- 环境：Java 21、Maven、Node；真实 HA 另需 Docker 与拓扑资源。官方候选 1.4.1，固定 upstream commit `cb1a0c7ac16ff146ad8abb04b1bcb035270bb234`；来源或平台不符即停止归因。
- 权限：一般“继续”只推进批准设计的实现和隔离验证，不新增真实 prepare／清除／full／HA、业务连接或网络权限。此前一次 smoke/report 授权已消费，不自动重跑。不发布、push、提交或清理用户数据。
- 本批明确授权：B3 正式模型采集／校验、model-prepare、model-verify（完整 `mvn -q verify`）及 manifest verify/check 已执行并获限定 Accept。用户随后明确要求推进独立 full／HA、取消路径和正式 Review，允许按既有入口修复失败后冻结重验。清理仍仅有效模型 jar 模块的 `target/classes`、`target/generated-sources/annotations`、`target/maven-status/maven-compiler-plugin`、`target/jacoco.exec`、`target/site/jacoco`，以及既有必需 Surefire XML／exec JAR 和本批 manifest／summary；不删除整个 target、不扩至业务数据，不含提交。

## 事实源

- 设计或产品规格：[治理迁移设计](../../design-docs/2026-10-28-official-jraft-governance-simplification-design.md)、[CLI 安全设计](../../design-docs/2026-10-30-ops-cli-authentication-design.md)：已取得独立设计 Accept。
- [自有 namespace 覆盖设计](../../design-docs/2026-11-03-owned-namespace-coverage-instrumentation-design.md)：T11/B1–B3 的详细合同，尤其第 3、4 节。
- 根 `ARCHITECTURE.md`、Broker 局部 Architecture、Management 最近 `AGENTS.md` 与 owning README；工程规则由 Unit Test/Coding/Harness/Code Review Guide 拥有。
- [完整执行历史](../history/2026-10-30-official-jraft-and-ops-cli-execution-history.md)：原任务详细步骤、Management 文件清单、全部恢复点及原验收归属，不是新运行证明。
- `PROGRESS.md` 是状态索引；`.harness/report.json` 与各批原件拥有机器证据，不重写旧时间、指纹、退出码或 stale。
- 原实施基线 `359b5893522997edc382a70f214fc80bed4c4090`；每轮先核对混合工作区，保留无关改动。

## 验证分层

| 层级 | 命令／要求 | 当前边界 |
| --- | --- | --- |
| 环境 | `./scripts/verify.sh readiness` | 本轮已通过，不证明 Docker 或业务健康 |
| 隔离回归 | `bash scripts/test-reactor-evidence.sh`、`bash scripts/test-java-style-harness.sh` | 按影响运行；最终冻结输入、完整日志与退出码一致 |
| 语法／漂移 | Ruby／shell 语法、限定 `git diff --check`、`./scripts/verify.sh clean` | 输入变化使运行失效，失败先归因 |
| 窄反馈 | owning Maven `-pl ... -am -Dtest=... -Dsurefire.failIfNoSpecifiedTests=false test` | XML 须非零测试；真实命令按批授权 |
| 风险 | `./scripts/verify.sh affected`；独立 architecture/startup/behavior/e2e 用于诊断 | setup、skip、局部 Accept 不代替风险覆盖 |
| 最终验收 | 冻结后 `./scripts/verify.sh clean`、`./scripts/verify.sh full` | 本轮 full 已通过；同轮报告满足 architecture/startup/behavior/e2e 风险义务，不重复昂贵门禁 |
| 收口 | `./scripts/verify.sh quality`、`cleanup`、`handoff` 与独立 Review | 历史全局 `review_required` 保留；局部 Accept 不替换它 |

## 任务状态

| ID | 目标／所有者 | 状态 | 证据导航（完整命令均保留历史） |
| --- | --- | --- | --- |
| T1 | 官方候选与通用配置 API／im-raft | passing | 历史 T1；`.harness/t1-reaccept/full-attempt3/` |
| T2 | 已提交观察资格／Broker | passing | 历史 T2；`.harness/t2-reaccept/full-attempt2/` |
| T3 | 持久化步骤与许可／Broker domain/FSM | passing | 历史 T3 完整验收与状态关闭 |
| T4 | 本机封闭与跨节点确认／Broker SDK/server | passing | 历史 T4 验收收尾 |
| T5 | 完整维护与漂移准入／Broker | passing | 历史 T5 验收收尾 |
| T6 | 官方依赖切换与补丁退役／POM/Harness | passing | 历史 T6 验收收尾 |
| T7 | public client 与撤销／IAM | passing | 历史 T7；DB-enabled 实测及 revision2 状态 Review |
| T8 | 管理员 Bearer 与双链／IAM SDK/Ops | passing | 历史 T8 第二轮验收及状态 Review |
| T9 | CLI API 与窄 operation 查询／Ops | passing | 历史 T9 第三轮冻结验收 |
| T10 | 自有交互 CLI／Ops CLI | passing | 历史 T10 冻结验收及状态迁移 |
| T11 | 全流程、文档与冻结验收 | passing | 新 full／HA 与完整独立 Accept：`.harness/t11/b5-final-20261106/` |
| T12 | 独立质量关闭与状态迁移 | passing | 正式 quality `Accept`；证据合同回归、廉价收尾与最终状态迁移 Review 已完成 |

## 详细任务

### T1 官方候选与通用配置 API

- [x] 原实现、实验、完整验收与 Review 已关闭；精确范围和历史失败见历史 T1，不重新实施。

### T2 已提交观察资格

- [x] 原实现、完整验收与状态 Review 已关闭；详细范围与证据见历史 T2。

### T3 目标步骤与持久化许可

- [x] 原实现与验收已关闭；文件范围、RED/GREEN 和状态记录见历史 T3。

### T4 本机封闭与跨节点确认

- [x] 原实现与完整 SPEC／QUALITY 验收已关闭；文件和命令见历史 T4。

### T5 完整维护、同步确认与漂移准入

- [x] 原实现与冻结验收已关闭；详细范围及历史失败见历史 T5。

### T6 依赖切换与补丁退役

- [x] 官方依赖切换及规则退役已关闭；精确文件、命令和独立 Review 见历史 T6。

### T7 IAM public client 与撤销

- [x] IAM 相关任务已关闭；精确源码／测试／装配范围见历史 T7 与 Management 文件清单，保留原扩展范围。

### T8 管理员 Bearer 与双链

- [x] 原实现、完整验收及状态 Review 已关闭；文件和命令见历史 T8。

### T9 Ops CLI API 与窄 operation 查询

- [x] 原接口与查询范围已关闭；文件和完整验收见历史 T9。

### T10 自有交互 CLI

- [x] 登录、固定命令、打包与验收已关闭；文件、命令及历史失败见历史 T10。

### T11 全流程与冻结验收

保持五批义务，不重开已通过子片，也不以其替代剩余验收。

- [x] **B1 完整输出合同**：既有 Ruby reader／self-test 与 Harness 文档，完整 stdout 模板及组合验证获限定 Accept。原证据见历史 B1。
- [x] **B2 真实采集／安全接线**：正式三采/check、工具来源、有限环境、取消／锁及 standalone config 合同获整体限定 Accept。历史失败及信任边界保留，不自动重复真实采集。
- [x] **B3 实际覆盖／产物**：新完整快照、full／HA 与独立完整 T11 Accept 已取得，原件 `.harness/t11/b5-final-20261106/`；以新验收时点关闭技术义务。旧冻结文档精确差异仍属历史未审定，不追认旧迁移；新时点后的状态 diff 另由 T12 审查。
- [x] **B4 剩余业务证明**：IAM scope、第二管理员接续审计、独立同步门禁及许可认证负例已获独立限定 `Accept`；`.harness/t11/b4-gap-fix-20261106/b4-final-review.json`，不扩大至 T11／full／真实容器 HA 或 quality。
- [x] **B5 冻结完整验收**：新冻结 full／真实 HA、cleanup 只读扫描及独立完整 T11 `Accept` 已通过。当前只回填任务状态，正式 quality 与新状态精确 diff Review 保留给 T12，不把局部或历史原件当新验收。

#### B3 当前范围与步骤

- [x] **现有代理最小接入**：仅在 `scripts/verify.sh` 的 full HA 受控子阶段增加固定 `HTTPS_PROXY=http://127.0.0.1:7890`；主模型/Maven 等阶段保持三键。隔离回归已验证固定值、非 HA 不继承、大小写代理与 Docker 覆盖隔离、取消矩阵和失败传播；旧实现 RED exit46、修正后 GREEN exit0，设计与实现 Review 均 `Accept`。证据 `.harness/t11/proxy-admission-20261106/`。

实现范围：根 `pom.xml`、`scripts/lib/harness-reactor-evidence.rb`、`scripts/test-reactor-evidence.sh`、`scripts/check-java-style.sh`、`scripts/test-java-style-harness.sh`、architecture 包的 `RuntimeDependencyPolicyTest.java`、`CoverageInstrumentationTest.java`、architecture POM；同步 owning README、Guide／Matrix、本计划及 PROGRESS。HA 故障窄修追加 `scripts/bin/docker-credential-desktop` 及本地 ignored v2 的 `tools.json`／`aggregate.json` 精确来源绑定，不扩大 PATH 或修改用户 Docker config，不引入通用编排框架。

- [x] **凭据助手 launcher 兼容修复**：formal 监督窄 build 暴露 `scripts/bin/docker-credential-desktop` 由 BuildKit 子进程启动时，Ruby `RbConfig` 找不到 `uname`；最小修复固定助手子进程 `PATH=/usr/bin:/bin`，以 Ruby `-e` 保留协议 stdin，既有来源校验不变。`.harness/t11/launcher-stdin-20261106/` 覆盖旧版 heredoc 丢 stdin 的 RED、严格 GREEN、缺失／错误 PATH、含空格／空参数、标准流及退出码；独立 Review 为 `Accept`。不改 Docker 配置与认证语义。

- [x] package 所有权、class 身份／路径／摘要、MapStruct 期待集、普通生产输出存在性：回归与各子片限定 Accept，证据见历史 B3。
- [x] 实际双 agent smoke 与两个 owner report：`.harness/t11/agent-smoke-20261104T234728Z/`，三精确 Maven 命令均 exit0，独立 `review.json` Accept。证明 probe／exec／classId／session／XML，不证明生成鲜度；一次授权已消费。
- [x] 五种受控输出清理接线：`.harness/t11/b3-clear-resume/attempt2` 完整隔离 self-test exit0、冻结 SHA 一致、独立 Accept。类型槽、非目录祖先、状态提前删除、模型摘要层级问题已修；失败、失效 GREEN、常量 warning 保留，未清真实输出。
- [x] **同轮输出时间与摘要**。既有 Ruby validate/check 检查 production class 与全部 generated Java 非空、时间窗口；绑定完整 generated 源集合，拒绝新增／丢失／替换。`.harness/t11/b3-freshness/red.exit=1` 命中旧 class 接受缺陷；冻结 `attempt2` 完整 `bash scripts/test-reactor-evidence.sh` exit0、前后 SHA 相同，独立 `review.json` 限定 Accept／findings none。旧／空／未来生成源及验证后新增、删除、替换均有断言。语法、限定 diff、readiness、clean、cleanup、handoff 通过；首 GREEN 失败为旧 fixture 在 prepare 前写 class 导致诊断前提改变，已调整 identity 优先与重写时机，未弱化原断言。Reviewer 误启重复隔离测试已取消，不算证据；常量 warning 保留。mtime 不能代替实际 prepare 清除 provenance。
- [x] **report manifest checker／隔离验收**。按已批准[窄 synthetic 合同](../../design-docs/2026-11-05-jacoco-synthetic-report-inventory-design.md)，`scripts/lib/harness-reactor-evidence.rb` 的 header flags／C-F-R／同一 manifest 已接入，`scripts/test-reactor-evidence.sh` 补齐回归，原设计、Guide／Matrix、索引已同步。证据 `.harness/t11/b3-report-integration/`：行为探针对旧基线报“executed production module accepted without exec/report”，新实现按 pair incomplete 拒绝（两者 exit1 含义不同）；冻结完整 `bash scripts/test-reactor-evidence.sh` attempt2 实际 exit0、源码前后／当前 SHA 一致，独立 `review.json` 限定 Accept／findings none。Review 修复全树 counter、all-skipped fork 及真实 synthetic header 重读覆盖。初始缺方法 RED 不代替行为证据，运行中修改 heredoc 导致 attempt1 exit2，不计 GREEN；旧失败／Revise／常量 warning 保留。当时尚未授权真实 Maven／清除／full／HA；后续授权与执行结果见正式链路和冻结完整重验，不沿用该历史限制作为当前阻塞。
- [x] **B3 正式链路限定验收**。原批准命令 `mvn -q -pl im-test/im-architecture-test -am -Dtest=RuntimeDependencyPolicyTest,CoverageInstrumentationTest -Dsurefire.failIfNoSpecifiedTests=false test` 实际 exit0（28＋1 tests，0 failures/errors/skipped）。正式模型→清理→完整重建→manifest verify/check 全部 exit0；52 modules、1893 classes、65 mappers，需报告的 32 modules 中 C/F/R=1857/54/1803；全部本轮 Surefire 3007/0/0/24。无 MethodTooLarge／IllegalClassFormatException，2139 个绑定文件摘要获独立重算；`.harness/t11/b3-formal-20261105/review.json` 限定 Accept。不是 `verify.sh full`、真实 HA 或取消协议验收，不关闭 B3 整体。
- [x] **full 的 C locale 故障修复**：共享文本 reader 显式 UTF-8，中文／C locale CLI 回归已获有效 RED、完整隔离 GREEN、空环境 manifest check 和独立限定 Accept；新 full 实际越过 reactor-check。证据 `.harness/t11/b3-locale-fix/` 与 `.harness/t11/b3-full-20261105/retry-utf8/review.json`。此前 compose-up 失败仅属于历史运行事实，不覆盖后续已通过的冻结 full 与真实 HA。
- [x] **HA 凭据助手准入决定**：用户确认只信任当前 Docker Desktop 助手及 Docker 原协议调用范围；只读来源与独立设计技术 Accept 已归档 `.harness/t11/docker-credential-source/`。不得泛信任 `/usr/local/bin`、绕过认证或修改用户 Docker 配置。
- [x] **凭据助手最小实现与隔离验收**：固定 launcher、既有 source reader／fixture、owning 设计和 Guide／Matrix 已同步。重复来源 SHA 引用、lexical metadata 和协议流 fixture 缺口已修；首轮 Block 保留。`bash scripts/test-reactor-evidence.sh` attempt3 exit0、五文件前后／现场 SHA 一致，正式 reader／完整 model-execution 只读校验、语法、clean、cleanup 只读及 handoff exit0；独立 `independent-review-attempt3.json` 限定 Accept／findings none。attempt2 因合成 Ruby 子进程 PATH 警告混入 stderr 而 exit1／Revise，修正仅固定合成子进程 PATH，严格流断言与生产三键环境不变。
- [x] **真实窄 build**：在三键、已审 HA 固定代理环境和既有 HA Compose 下，由 `routing-ha-process.rb run credential-build 300` 监督执行 `docker compose ... build broker-ha-1 gateway-ha-1`；`.harness/t11/launcher-build-20261106/build.exit` 为 `0`，前后 HEAD／指纹稳定，Broker/Gateway 镜像 digest 已记录。此前 registry `DeadlineExceeded` 失败保留，不改 Docker 配置、tag、pull policy 或认证语义。
- [x] **冻结完整重验**：`./scripts/verify.sh full` 已通过，`.harness/verifications/full.json` 记录 `3007/0/0/24`、`reportExitCode=0`、`handoffExitCode=0`、`worktreeStable=true`；真实 HA `.harness/verifications/routing-ha.json` 记录 `3 Broker + 2 Gateway`、`25` 场景、`cleanupStatus=passed`。同轮产物与原件保留；B3 组合技术 Review 已为 `Accept`，不等同 T11 独立完整 Review 或状态迁移通过。前序交接记载该 full 曾被 pager 暂停后恢复；原日志与进程时间线仍须由完整 Review 核对，不据正常退出推断取消路径重新实测通过。

#### B4 剩余业务与 B5 完整验收

- [x] 2026-11-06 首批两项 focused Maven 与精确 `./scripts/verify.sh affected` 均实际 exit0，指纹一致；原件 `.harness/t11/b4-20261106/`。这只证明既有测试通过，不证明四项剩余要求已覆盖；误加 `--help` 的尝试已取消，不计通过。
- [x] 四项缺口测试及两个 owning README 已补齐，独立静态复核无残余必补项；新增范围未涉及生产代码。`.harness/t11/b4-gap-fix-20261106/ops-attempt1/` 与 `ha-attempt4/` 各为 8 tests、0 failures/errors/skipped，实际 exit0；HA 最后微补验证完整 operation、revision 与含 learners 的 native 配置不变。前两次 HA 编译错误保留，不算行为 RED。
- [x] 冻结输入执行精确 `./scripts/verify.sh affected`，实际 exit0、751 秒、前后 HEAD／fingerprint 稳定；523 份同轮 XML 为 3009/0/0/24，DEV／伪造 incarnation 与旧确认不能释放新许可两方法均执行且无 skip/error/failure。原件与完整源码快照见 `affected-attempt1/`，正式独立复审 `b4-final-review.json` 为限定 Accept；原 Revise 未覆盖。
- [x] B4 修复范围限定上述三个测试文件及两个 owning README：独立 scope 拒绝保留 write authority；第二管理员真实登录接续并核对实际审计；独立 LEARNER_SYNCING 门禁及正式 ReadIndex 确认后解除；真实 mTLS 拒绝错误身份与旧 incarnation／错误许可。未修改生产代码，既有 RBAC／HA 局部 Accept 不重新记缺失或扩大。按窄两类测试、affected 顺序验证，原四项 finding 已正式关闭。
- [x] 实际运行 `mvn -q -pl im-test/im-e2e-test -am -Dtest=OpsCliGovernanceE2ETest -Dsurefire.failIfNoSpecifiedTests=false test`、`mvn -q -pl im-test/im-routing-ha -am -Dtest=AutomaticFailureTlsHaTest -Dsurefire.failIfNoSpecifiedTests=false test`、`./scripts/verify.sh affected`；三项 exit0 并取得独立限定 Accept。不把 focused／affected 代替完整验收。
- [x] B5：`.harness/t11/b5-final-20261106/` 保存最终 2837 项源码 bytes／mode／SHA、全部 2139 项绑定产物及 523 份 XML。`./scripts/verify.sh clean`、`./scripts/verify.sh full` 实际 exit0；full 2425 秒、3009/0/0/24，真实 HA 为 3 Broker＋2 Gateway、25 场景且 cleanup passed。前后指纹 `ccf54cff19e9682247749e1e0086108ad23b9c8545a7709d25a27468cf48d54b` 一致；`t11-final-review.json` 为完整 T11 Accept、findings none、必要 gaps none。cleanup 只读 exit0。外层归档首个 cp 因 zsh PATH 变量绑定失败，随后已用绝对命令补存且独立重算一致，不改写实际 full exit0。原后处理 report 中间时点 unknown 与 overall stale 保留；廉价 report 刷新 exit0、tests current/full fresh/HA fresh，整体旧 sensor stale 不冒充 quality 成功。

### T12 独立质量审查与关闭

- [x] T11 完整验收后已由独立上下文取得完整 T11 Accept；首次状态切换精确 diff Review 为 Accept，原件 `t11-state-review.json`。廉价 drift／clean／cleanup／handoff 均 exit0，非状态输入未变，quality scope 与冻结时点一致；不是整份计划归档 Accept。
- [x] 正式独立 Reviewer 在完整 evidence allowlist 上完成自洽 `Accept`；旧 README-only response、无依据 `Revise`、v2/v3 response 与全部历史结果均保留在 `.harness/t12/20261107/`，未手改评分。
- [x] T11 passing 后，独立上下文完成批准设计／实际 diff Review；最新正式 quality 为 `Accept`，非状态修复已按影响完成回归。
- [x] 正式 Review 证据合同已修正：request 列出完整 Git-visible evidence 闭包，response 严格使用 allowlist；独立合同审定、实现审定与 fixture 负例均已完成。
- [x] 实际完整验收与独立 Accept 后，按 PLANS 两时点规则仅迁移状态：计划移至 `completed`，PROGRESS 和目录索引同步；未改稳定设计、需求、命令或实现事实。
- [x] 独立 Reviewer 比较精确迁移 diff 与原验收；迁移后廉价 drift/clean、quality scope、cleanup、handoff 均完成，不为纯状态重复 full，不刷新旧时间或 stale。
- [x] 未 stage／commit／push；保留实际结果与残余风险。

## 恢复状态

- 2026-11-06 launcher 续接：前序循环完整套件暴露 Ruby 解释 shell、复制文件不可执行、根参数重复消费及 heredoc 丢 stdin；相关放宽已撤回，协议断言恢复精确匹配。当前修复使用秒级合成真实 launcher bytes 测试先 RED 再 GREEN，完整套件、冻结 SHA 和独立 `Accept` 保存在 `.harness/t11/launcher-stdin-20261106/`；正式窄 build 随后 exit0。前序直接调用真实助手的诊断不计入验收，后续仍只走 Docker 原协议边界。
- 2026-11-06 续接：计划主体 UTF-8、七个必需章节与本地链接完整，未发现重复正文；本轮只精简 active README 与 PROGRESS，精简前全文逐字保存在[入口原文](../history/2026-11-06-active-index-progress-before-trim.md)，不删除旧证据或改变验收状态。正式 `model-execution` 只读校验 exit0，readiness 与首轮 clean exit0。宿主 `/usr/bin/curl` 对 `https://registry-1.docker.io/v2/` 的无凭据探测在 8 秒连接期限后 exit28／HTTP 000；这不是 Docker daemon 网络探测，不能定位旧 build 超时的具体根因，也不证明助手调用。磁盘本轮观察为 6.6–7.1 GiB 可用（99%）；网络恢复前提未成立，未重跑 build／full／HA。最终文档校验与 Review 另以本轮原件为准。
- 本轮空间清理：删除 39 个 ignored `target/` 目录，释放约 1.4 GiB；`./scripts/verify.sh cleanup` 只读扫描 exit0。未删除 `.harness` 历史证据、Docker/Maven 缓存或用户数据。后续用户明确要求彻底删除已退役的私有 JRaft 第三方目录；旧配方、法律材料与 provenance 不再保留，历史事实仍在执行历史中。
- 当前 T1–T12 passing；新 full／HA、独立完整 T11 Accept、正式 quality Accept 与状态迁移 Review 均已取得。旧冻结文档差异、旧 quality Revise 与失败记录继续作为历史事实保留，不追认旧迁移。
- 最近实际 full：launcher 修复后冻结重验通过，`.harness/verifications/full.json` 记录 7122 秒、`3007/0/0/24`、`reportExitCode=0`、`handoffExitCode=0` 和稳定工作树；真实 HA `.harness/verifications/routing-ha.json` 记录 `3 Broker + 2 Gateway`、`25` 个场景和 `cleanupStatus=passed`。旧 UTF-8／compose-up 失败仍保留在 `.harness/t11/b3-full-20261105/`，不覆盖本轮成功原件。
- 最新窄修：精确助手来源／launcher 与隔离回归已获独立限定 Accept；原 Block／Revise 和此前 registry 超时失败继续保留。随后在已审固定代理环境完成正式窄 build，`.harness/t11/launcher-build-20261106/build.exit=0`，前后 HEAD／指纹稳定并记录镜像 digest。未修改 tag、pull policy、Docker 配置或认证语义。
- 2026-11-06 重试：早先三键窄 build 的 `DeadlineExceeded`、直连 registry 超时及代理诊断对照均为历史事实；成功窄 build、full、HA 原件已单独归档。当前不重复昂贵验证，转入 B4 缺口与独立完整 T11 Review。
- 最新实施：共享 reader 显式 UTF-8 和 C locale 中文 CLI 回归，修前实际 RED；完整隔离 attempt3 exit0、源码前后 SHA 一致，空环境真实旧 manifest 只读 check exit0。证据 `.harness/t11/b3-locale-fix/`；前两轮 fixture 错误（负例 mutation no-op、suite 身份不符）及过程 Revise 保留，不算生产 RED。第一轮日志被第二轮误覆盖，仅保留原 SHA 与工具输出，不能声称原件完整。不扩大 HOME/JAVA_HOME/PATH 三键环境、不绕过 check；最终复审后冻结重验。原 freshness／report 限定 Accept、撤回候选、失败和 Revise 均保留。
- 本次文档维护把拆分前全文原样迁至 history，仅重建当前入口；任务 passing、运行授权、历史验收均不变。旧 T1 active、T7 blocked、B2 未验收等是当时检查点，不是当前状态。
- 本轮收尾（历史记录）：可用空间最新观察约 14 GiB，不代表完整验证峰值容量保证。Broker/Gateway `target/*-exec.jar` 曾随清理移除，`.harness/t11/docker-helper-build-20261106/jar-inventory.txt` 曾为空；当时要求先安排原批准模型／重建流程，不能把旧 manifest 或旧 JAR 当本轮产物。随后已取得新的窄 build、full 和 HA 原件。廉价收尾原件保存在 `.harness/t11/closeout-20261106/`；该目录不构成完整验收或独立 Review。
- 收尾：T11 精确状态 Review、正式 quality、合同回归及最终状态迁移 Review 均已完成；不得直接探测助手、输出凭据、换镜像／认证语义或变更用户 Docker config。
- 全局状态：计划已归档；历史 quality `review_required`、旧冻结文档取证缺口与失败记录保留，不改写原时间、指纹或 stale。

## 回滚与残余风险

- 源码／依赖／格式一致回退，不用旧二进制读取新格式数据。可禁 CLI Client/API 阻止新请求，不撤销已提交 operation／业务事实。
- D10/D16 可能无限期阻塞维护和接管；4096 槽保守不回收、失败预留消耗容量。终端失陷、端口占用失败关闭、IAM 不可用停止新请求等已接受边界保留。
- smoke 允许复用旧 generated/classes；隔离清理接线通过不是实际生成鲜度。预检不防恶意同 UID 并发替换，沿用已审信任范围。
- 文档拆分不等于计划完成或历史验收重认证。历史链接基准按本页开头说明解析。
