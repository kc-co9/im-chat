# Harness 运行门禁增强实施计划

> **执行约束：** 用户要求本阶段不使用 subagent。除非用户明确要求，否则不创建 Git commit。

## 迭代契约（Sprint Contract）

### 目标

在现有 `verify.sh` 和报告/检查器中增加 readiness、clean、五子系统报告与 WHAT/WHY/FIX 诊断，不增加竞争入口。

### 范围

- `scripts/verify.sh` 的 `readiness`、`clean` 模式。
- `scripts/harness-report.sh` 的五子系统和外部规范适配报告。
- `check-drift.sh`、`check-java-style.sh`、`check-sql.sh` 的诊断格式。
- `scripts/test-harness.sh` 及对应 checker fixture。
- README、AGENTS、Harness Guide 的入口和规则说明。
- 根 `PROGRESS.md` 的当前状态索引及 drift 一致性检查。

### 非目标

- Makefile、feature list、自动 loop、新增 shell 脚本。
- 自动清理工作树、修改 Git index 或把正常未跟踪源码判为临时文件。
- 追逐外部 Harness 原始评分。

### 验收标准

- `verify.sh readiness` 对当前环境通过，对缺失/不兼容版本输出 WHAT/WHY/FIX 并失败。
- `verify.sh clean` 在合法工作树通过，对 WIP 违规、恢复区缺失和明确临时工件失败，不删除任何文件。
- Harness report 保留兼容字段并输出五子系统状态、证据与外部适配分类。
- 三个核心 checker 的代表性失败均包含 WHAT/WHY/FIX。
- README 环境版本与 POM/前端 lockfile 一致。
- `.java-version` 与 `.nvmrc` 提供默认版本 pin，readiness 接受约束范围内的兼容版本。
- PROGRESS 与 active plan 一致，README 不承载当前任务状态。
- `readiness`、`clean`、Harness fixture、`quick`、`full` 全部通过。

## 任务状态

| 任务 | 状态 | 证据 |
|---|---|---|
| 1. Readiness 门禁 | `passing` | compatible fixture RED 后 GREEN；Java 17、Maven 3.8.3、Node 20.18、缺少 npm 均输出 WHAT/WHY/FIX；当前环境通过 |
| 2. Clean 与 Progress 门禁 | `passing` | clean 模式 RED 后 GREEN；正常未跟踪源码和 `debugging-guide.md` 通过，双 active task、缺恢复区、临时工件失败且不自动删除；PROGRESS 漂移 fixture RED/GREEN |
| 3. 五子系统报告 | `passing` | report fixture RED 后 GREEN；兼容字段、五子系统与 external adaptation JSON 解析通过 |
| 4. WHAT/WHY/FIX 诊断 | `passing` | Drift、Java style、SQL 代表性 fixture 先因缺少标签 RED，统一诊断后全部 GREEN |
| 5. 文档与最终验证 | `passing` | 临时 index 链接与空白检查通过；`readiness` 1 秒、`clean` 8 秒、`quick` 152 秒、`full` 248 秒，均通过；全量测试 1002 个、零失败 |

## 恢复状态

- 当前任务：`none`，全部任务已经完成。
- 已完成证据：readiness、clean、PROGRESS、report、WHAT/WHY/FIX 的 RED/GREEN fixture 和真实入口已通过；临时 index 链接检查、`git diff --check`、quick/full 全部通过。
- 外部审计：从 `18/70`、Critical `4/7` 提升到 `28/71`、Critical `6/7`；剩余 Critical 已归因为 Maven/嵌套 lockfile 识别差异。
- 阻塞项：`none`。
- 下一步：本计划已经归档；后续新增 Harness 能力时按规则生命周期继续演进。

## 任务 1：Readiness 门禁

- [x] 增加正确版本、缺失命令和不兼容 Java/Maven/Node fixture。
- [x] 运行 fixture，确认因 `readiness` 模式不存在而 RED。
- [x] 在 `verify.sh` 实现命令存在性、版本解析和范围检查。
- [x] 修正 README Maven 版本并记录 Node 要求。
- [x] 运行 GREEN fixture 和当前环境 readiness。

## 任务 2：Clean 与 Progress 门禁

- [x] 增加合法状态、多个 active task、缺失恢复区和临时工件 fixture。
- [x] 确认 RED 后实现 drift、Shell 语法、diff、WIP、恢复区和临时工件检查。
- [x] 证明 clean 不删除文件且正常未跟踪源码不失败。
- [x] 新增有界 `PROGRESS.md`，并用 drift fixture 验证 active plan 链接与 `none` 状态。

## 任务 3：五子系统报告

- [x] 扩展隔离 report fixture，先断言五子系统和适配字段并确认 RED。
- [x] 保留现有字段，新增子系统状态/证据和 `equivalent`/`not_applicable`/`deferred`。
- [x] 运行 report fixture 与真实 `verify.sh report`。

## 任务 4：WHAT/WHY/FIX 诊断

- [x] 为 drift、Java style、SQL 各增加一个代表性失败断言并确认 RED。
- [x] 最小修改诊断聚合，保留现有规则名、文件位置和测试断言。
- [x] 运行三个 checker Harness。

## 任务 5：文档与最终验证

- [x] 更新 README、AGENTS、Harness Guide、设计/计划索引。
- [x] 使用临时 index 检查新文档链接和空白。
- [x] 运行 `verify.sh readiness`、`clean`、`quick`、`full` 与 `git diff --check`。
- [x] 完成整体 Review；通过后移动计划到 completed 并刷新最终只读证据。

## 回滚

删除新增模式和 JSON 字段即可回滚；保留原 `quick/full/report` 接口和兼容字段，避免影响既有调用者。
