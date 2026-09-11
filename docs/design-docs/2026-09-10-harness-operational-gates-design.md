# Harness 运行门禁增强设计

## 背景

外部 Harness 审计已经证明 im-chat 具备主要能力，但外部脚本无法识别 Maven reactor、`scripts/verify.sh`、active execution plan 等等价机制。继续复制 Makefile、feature list 或模板脚本只会提高模板分数，不会提高反馈质量。

本阶段补足四个真实缺口：工作前环境就绪检查、任务结束的清洁状态检查、按五个子系统输出能力报告，以及核心 checker 的 WHAT/WHY/FIX 诊断。

同时新增根 `PROGRESS.md` 作为全仓当前状态索引，解决稳定 README 与执行中状态混放时的理解歧义。它不替代 active execution plan：PROGRESS 只保存活跃计划、当前任务、阻塞项、下一步和最近验证，详细任务与证据仍由计划拥有。

外部审计原始结果从 `18/70`、Critical `4/7` 提升到 `28/71`、Critical `6/7`；剩余 Critical 是工具无法识别 Maven dependencyManagement 与嵌套 UI package-lock 的结构误判，不通过复制根 lockfile 处理。

## 决策

### Readiness

在现有 `scripts/verify.sh` 增加 `readiness` 模式，不新增脚本。检查：

- Java 满足根 POM Enforcer 的 `[21,22)`。
- Maven 满足 `[3.8.4,4)`；根 README 的 Maven 3.9+ 修正为 POM 的真实要求。
- Node 满足当前 Vite 7 的 `^20.19.0 || >=22.12.0`。
- npm、Ruby、Git、`rg` 可执行，并输出实际版本或路径。

根 `.java-version` 推荐 JDK 21，`.nvmrc` 推荐当前验证过的 Node 20.19.5；它们提供默认环境 pin，readiness 仍以 POM/Vite 的兼容范围判定。

失败诊断说明 WHAT、WHY、FIX。Fixture 通过隔离 PATH 注入版本，不依赖开发机安装状态。

### Clean

在 `verify.sh` 增加 `clean` 模式，组合现有能力：drift、`bash -n scripts/*.sh`、`git diff --check`、active plan WIP/恢复区检查，以及未跟踪临时/调试工件检查。正常的未跟踪源码不自动失败，只输出工作树摘要；`.tmp`、`.orig`、`.rej`、调试日志等明确临时形状失败。

`clean` 不删除文件、不暂存、不提交，也不替代 `full`。

### Progress

根 `PROGRESS.md` 面向开发者和新 Agent 会话，控制为有界状态面板。创建、切换或归档 active plan 时同步更新；`check-drift.sh` 验证所有 active plan 都在 PROGRESS 当前工作区出现，没有 active plan 时必须明确记录 `none`。

README 只链接 PROGRESS，不保存当前任务状态。Harness report 读取 PROGRESS 作为 State 子系统证据。暂不增加 `feature_list.json`：机器任务队列或自动 loop 出现前，计划任务表已经提供足够的结构化状态。

### 五子系统报告

`harness-report.sh` 保留现有兼容字段，同时新增 Instruction、Tools、Environment、State、Feedback 的状态和证据列表，以及外部规范的 `equivalent`、`notApplicable`、`deferred` 适配结果。状态由证据文件/入口是否存在计算，不把单一总分解释为源码质量。

### WHAT/WHY/FIX

核心 `check-drift.sh`、`check-java-style.sh`、`check-sql.sh` 的违规输出统一包含：

- WHAT：违反了什么规则及位置。
- WHY：该规则保护的风险或边界。
- FIX：最窄的修复方向。

现有详细文件列表和 fixture 断言保留。Management UI 等专用 checker 后续在修改其规则时再统一，不在本阶段批量改写。

## 非目标

- 不新增 Makefile、feature list、自动 Agent loop 或第二套脚本目录。
- 不自动删除临时文件，不修改真实 Git index。
- 不用外部 `18/70` 作为发布阈值。
- 不要求每个小任务运行 `full`；仍按风险分层验证。

## 验证

- Readiness 对正确版本、缺失命令、Java/Maven/Node 不兼容分别提供 RED/GREEN fixture。
- Clean 对干净状态、多个 active task、缺失恢复区和临时工件提供 fixture。
- Report fixture 验证五子系统和外部适配 JSON，不移除兼容字段。
- Progress fixture 验证 active plan 链接一致和无 active plan 的 `none` 状态。
- 三个核心 checker 的代表性违规输出都断言 WHAT/WHY/FIX。
- 最终运行 `readiness`、`clean`、`quick`、`full` 和 `git diff --check`。

## 执行计划

实施步骤和证据记录在 [Harness 运行门禁增强实施计划](../exec-plans/completed/2026-09-10-harness-operational-gates.md)。
