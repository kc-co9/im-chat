# Harness 交接、启动与质量评审设计

## 背景

当前仓库已经具备分层验证、revision-bound evidence、实时 E2E、执行计划和 Harness report，但开发者仍需要自行完成三类拼装工作：把当前证据整理成交接摘要、逐个判断各应用是否具备标准启动路径、把机器证据与语义 Review 合成为模块质量判断。

课程模板中的 `session-handoff.md`、`init.sh`、clean-state checklist、evaluator rubric 和 quality document 给出了这些工件应回答的问题。本项目不复制静态模板，而是把模板字段接入现有 `verify.sh`、执行计划和 worktree fingerprint，使生成结果能够证明来源、发现陈旧并重复执行。

本设计取代[执行工件模板融合](2026-09-10-execution-artifact-template-integration-design.md)中“不生成独立 handoff”和“不采用数字评分”的阶段性结论。变化的前提是：handoff 与质量快照均为 `.harness` 下的派生产物，不拥有任务状态；评分输入绑定当前 fingerprint，语义分数由独立 AI Reviewer 提供并接受 Schema、证据和硬性上限校验。

## 设计目标

1. 自动生成下一会话可以直接使用的交接报告，同时保持执行计划和 `PROGRESS.md` 为状态事实源。
2. 为所有可部署 Spring Boot 应用提供统一、可枚举、拒绝零测试的标准启动检查。
3. 提供只读清理扫描和显式、限界的 Harness 自有临时文件清理，不触碰未知文件或业务数据。
4. 沿用课程六维 0-2 分 rubric，由机器证据和独立 AI Reviewer 分工评分。
5. 为关键模块生成 A/B/C/D 质量快照，说明分数、上限、证据和缺口，而不是只给一个无法解释的字母。

## 交接报告

新增 `scripts/harness-handoff.sh`，输出：

- `.harness/session-handoff.json`：供 Coding Agent 和后续脚本读取。
- `.harness/session-handoff.md`：供开发者快速阅读。

报告包含课程模板要求的“当前已验证、本轮改动、仍损坏或未验证、下一步最佳动作、常用命令”。内容来自 Git 状态、`PROGRESS.md`、active execution plan、Feature catalog 和 `.harness/report.json`。报告记录 HEAD 与 worktree fingerprint；源证据不完整或陈旧时必须如实标记，不推断为通过。

handoff 是派生视图，不反向修改 `PROGRESS.md`、执行计划或 Feature catalog。这样既降低交接整理成本，也避免生成文件成为第二个任务状态所有者或触发 fingerprint 自引用。

`verify.sh handoff` 可单独刷新交接报告。`verify.sh full` 在记录自身验证结果后再刷新 report 与 handoff；即使 full 提前失败，也尽量执行两个派生步骤，同时保留最初的失败退出码。report 或 handoff 后处理失败只会让原本成功的 full 变为失败，绝不能把失败门禁转换为成功。

## 初始化、标准启动与清理

### Init

`verify.sh init` 对齐课程 `init.sh` 的职责：检查环境、执行基础验证、验证标准启动入口，并打印仓库的启动顺序和命令。只有显式设置 `RUN_START_COMMAND=1` 时才执行开发启动命令；默认不留下后台进程。

### Startup smoke

`verify.sh startup` 枚举仓库中的全部 `@SpringBootApplication`，并要求每个应用都有 `@Tag("startup-smoke")` 的 Spring context 测试。入口清理旧报告、执行对应测试并核对实际测试数等于应用数，防止新增 Server 未进入标准启动门禁或 Tag 配置错误造成零测试假通过。每个测试必须显式关闭 Nacos/Dubbo 等远程发现与协议入口，并对 DataSource、Redis、远端 Facade 等必需边界使用可控替身；fixture 和 Review 同时检查这些隔离属性，避免误连开发者机器上的真实基础设施。

该门禁验证“应用入口、Spring 配置装配和受控依赖下的标准启动路径”，不声称已经启动 MySQL、Redis、Nacos、Kafka 或远程 Dubbo 全栈。真实基础设施联调应使用未来独立设计的 `stack` 模式，避免把受控 startup smoke 误称为生产拓扑验证。

### Cleanup

`verify.sh cleanup` 默认只扫描：Harness 自有临时目录、Harness PID 记录、已停止或不属于当前仓库的陈旧 PID，以及明确的中间产物。发现问题时给出 WHAT/WHY/FIX，但不删除任何内容。

`verify.sh cleanup --apply` 只允许删除 canonical path 位于 `.harness/tmp` 下的文件和 `.harness/pids` 下已经证明陈旧且记录当前仓库 provenance 的 PID 文件。符号链接逃逸、无法解析的路径、仍存活的 PID、缺少仓库标识或命令不匹配的 PID 记录都只报告、不删除，也不终止进程。它不得清理未知 untracked 文件、构建产物、日志、数据库或缓存。`clean` 继续负责交接状态门禁，`cleanup` 负责 Harness 自有垃圾的生命周期，两者职责不同。

## AI Reviewer 质量评分

### 六维分工

沿用课程 evaluator rubric 的六个维度，每项 0-2 分：

| 维度 | 评分者 | 证据 |
|---|---|---|
| Correctness | 独立 AI Reviewer | 产品规格、模块 README/Architecture、变更和测试行为 |
| Verification | 自动计算 | 当前 fingerprint 的验证记录、测试数和 freshness |
| Scope discipline | 独立 AI Reviewer | 计划范围、Git diff、模块所有权和无关改动 |
| Reliability | 自动计算 | startup、E2E、full、失败记录和开放可靠性风险 |
| Maintainability | 独立 AI Reviewer | 代码结构、命名、文档和局部可理解性 |
| Handoff readiness | 自动计算 | clean、report、handoff、计划恢复区和证据完整度 |

脚本不能假装完成语义判断，也不内嵌模型密钥。首次运行 `verify.sh quality` 时，它为当前 review scope 生成英文的 AI-only review request；Coding Agent 使用独立上下文的 AI Reviewer 读取该请求和列出的仓库证据，并写回结构化 JSON。再次运行 `verify.sh quality` 后，脚本校验 reviewer 身份、review scope fingerprint、Schema、分值范围、结论和证据路径，再合并自动分数。请求同时记录生成时的 HEAD/worktree fingerprint 供追溯，但语义结果的新鲜度由 review scope 判定。

开发者的标准使用方式是要求 Coding Agent“执行质量评审”。Coding Agent 负责运行证据准备、启动独立 Reviewer、接收结果并重新执行质量入口；开发者不需要手工填写表格。Reviewer 给出 `Revise` 或 `Block` 时，开发者只需决定是否修复、接受风险或缩小交付范围。

### 评分含义与硬性上限

- `0`：缺失、失败或没有可复查证据。
- `1`：部分满足，存在明确缺口或证据不足。
- `2`：满足要求，并给出当前 fingerprint 下的具体证据。

总分与等级：A 为 11-12，B 为 9-10，C 为 6-8，D 为 0-5。

为防止平均分掩盖关键失败，先计算总分，再应用上限：

- full、编译或必需测试失败：D。
- Critical 架构或安全 finding：最高 C。
- 变更涉及实时链路但 E2E 缺失或陈旧：最高 B。
- 没有当前 fingerprint 的 full evidence：状态为 `incomplete`，不输出可交付等级。
- Reviewer 结论为 `Revise`：最高 B；`Block`：D。

### 质量单元与增量复评

质量快照按可理解且有所有权的单元生成，而不是机械地为每个 POM 打分：`im-common`、`im-plugin`、`im-gateway`、`im-broker`、Account、Social、Message、IAM、Admin、Monitor、Audit 和 `im-test`。

首次建立基线时 AI Reviewer 评审全部单元。之后 review request 根据模块内容 fingerprint 复用未变化单元的语义结果，只要求复评发生变化的单元；共享规则、根 Architecture 或公共构建输入变化时保守扩大到全部单元。`reviewScopeFingerprint` 覆盖生产代码、测试、稳定 Architecture/README/规范和 Harness 实现，但排除 `target`、`PROGRESS.md`、执行计划实例及 active/completed 索引等执行状态文件。复用的是 response 内同时绑定独立 unit fingerprint 的单元结果；任何 review scope 输入变化都必须生成新请求。输出为：

- `.harness/quality/review-request.json`：当前待评审单元和证据清单。
- `.harness/quality/reviewer-response.json`：独立 Reviewer 的结构化回答。
- `.harness/quality/quality-snapshot.json`：机器可读综合结果。
- `.harness/quality/quality-snapshot.md`：中文模块 A/B/C/D 快照和缺口说明。

这些文件都是忽略的运行产物。需要长期比较时，只提交明确评审通过且绑定已提交 revision 的 baseline，避免把每次本地工作树评分写入稳定文档。

## 文档与开发者入口

根 README 用分点方式解释每类 Harness 能力的目的、入口、产物和边界；算法、评分 Schema、Reviewer 操作契约和例外由 `HARNESS_GUIDE.md` 与新的 `QUALITY_MODEL.md` 维护。AI-only Reviewer prompt 使用英文，开发者需要阅读的说明使用中文。

## 本地状态目录

Harness 运行状态统一保存在仓库根目录的 `.harness/`，并通过 `/.harness/` 整体加入 `.gitignore`。该目录不属于 Maven 构建输出，因此 `mvn clean` 不得删除其中的 verification、report、handoff、AI Reviewer response、质量快照或 E2E 日志。

目录保持单一生命周期：`report.json`、`session-handoff.*`、`startup-manifest.json`、`verifications/`、`quality/`、`runtime/`、`tmp/` 和 `pids/` 全部位于 `.harness/`。模块自己的 `target/surefire-reports` 仍由 Maven 管理，不迁移。CI 上传 `.harness` 时必须显式允许 hidden files；E2E verification 记录存在但 `runtime/e2e.log` 缺失时，report 必须把运行证据降级，不能只凭 JSON 声明完整。

迁移不为旧 Maven 输出位置提供兼容写入或双读逻辑，避免形成两个状态目录。已有本地产物一次性移动到 `.harness/`，保留 AI Reviewer response；当前脚本、fixture、文档和 CI 只引用新路径。历史 completed plan 中记录的旧运行路径属于当时证据，可以保留原文。

`.harness/` 不得保存唯一的架构决策、领域约束、任务状态或操作规范；这些信息必须继续由 Git 内的 AGENTS、Architecture、README、design/product spec、`PROGRESS.md` 和 active plan 拥有。新 clone 没有 `.harness/` 时，report 返回 `incomplete`，quality 返回 `review_required` 并生成新请求，`init/full/handoff` 可以重新建立本机证据。空状态是可恢复状态，不是通过状态。

## 验证策略

- Handoff fixture 覆盖成功、失败、陈旧证据和无 active plan。
- Startup fixture 覆盖应用/测试一一对应、缺失 Tag、零测试和真实启动 smoke。
- Cleanup fixture 证明默认只读、`--apply` 只删除 Harness 自有目标且幂等。
- Quality fixture 覆盖首次生成 request、陈旧 response、非法分数、缺证据、上限和 A/B/C/D 映射。
- 使用一次独立 AI Reviewer 为全部质量单元建立初始语义基线。
- 最终运行 `startup`、`cleanup`、`quick`、`e2e`、`full`、`quality` 和文档漂移检查。

## 最终证据协议

质量结果必须针对最终 review scope，而机器证据必须针对最终 tracked worktree。收尾顺序固定为：完成所有生产代码、测试和稳定文档修改；执行预归档 full/E2E/startup；生成质量请求并由独立 AI Reviewer 完成语义评审；校验质量结果；据此把全部实施任务写为 passing；移动计划到 completed 并把 `PROGRESS.md` 恢复为 `none`。归档只改变被 review scope 明确排除的执行状态文件，因此语义评审保持新鲜；随后禁止修改 tracked 文件，并重新执行 full/report/handoff/quality，刷新最终 worktree-bound 机器证据。该最后一步是归档后的关闭审计，不是需要预先标记 passing 的实施任务。关闭审计失败时必须恢复 active plan、重新打开受影响任务，修复后重复整个收尾协议。

## 执行计划

实施步骤与证据记录在 [Harness 交接、启动与质量评审实施计划](../exec-plans/active/2026-09-10-harness-handoff-startup-and-quality.md)。
