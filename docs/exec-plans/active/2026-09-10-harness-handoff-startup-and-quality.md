# Harness 交接、启动与质量评审实施计划

## Sprint Contract

- 目标：补齐自动交接、全部应用标准启动检查、受控清理、AI Reviewer 六维评分和模块 A/B/C/D 质量快照。
- 范围：`scripts` Harness 入口及 fixture；Account、Social、Message、HTTP Gateway、WebSocket Gateway、Broker、IAM、Admin、Monitor、Audit 的 Spring Boot application startup smoke；质量模型与 Reviewer 契约；根 README、AGENTS、Harness Guide、索引、CI 证据上传。
- 不做事项：不自动删除未知文件或业务数据；不自动启动外部基础设施全栈；不在脚本中配置模型密钥；不提交本地质量产物；不修改与本任务无关的业务行为。
- 验收标准：handoff 可从当前证据自动生成；全部可部署应用进入拒绝零测试的 startup 门禁；cleanup 默认只读且 apply 严格限界；独立 AI Reviewer 结果可校验并与自动三维评分合成；每个质量单元输出可解释等级；README 逐项说明目的与边界。
- 风险链路：`full` 后生成派生产物不能改变 worktree fingerprint；startup 测试必须显式隔离真实外部环境；cleanup apply 只允许 canonical path 位于 `.harness/tmp` 或已证明陈旧的本仓库 PID 记录；AI Reviewer 结果只能跨被明确排除的执行状态变化复用，review scope 或 unit fingerprint 变化必须复评；CI 上传隐藏目录必须显式开启 hidden files。
- 所有权边界：各 Server 模块只拥有自身 startup smoke 及依赖替身；`scripts` 拥有派生报告、清理和评分编排；执行计划/PROGRESS 仍拥有任务状态；AI Reviewer 只输出 Review 结果，不编辑生产代码或稳定文档。
- 依赖输入与所需权限：课程中文模板、当前 Harness report/verification JSON、Git worktree、Ruby、Maven/JUnit；只有质量语义评分使用独立上下文 AI Reviewer。

## 事实源

- 设计或产品规格：[Harness 交接、启动与质量评审设计](../../design-docs/2026-09-10-harness-handoff-startup-and-quality-design.md)。
- 根与局部 Architecture：`ARCHITECTURE.md` 及 Gateway、Broker、Message 局部 Architecture。
- API、协议或数据契约：课程 evaluator rubric、quality document、session handoff、init 和 clean-state 模板；`docs/references/HARNESS_GUIDE.md`。
- 相关代码入口：`scripts/verify.sh`、`scripts/harness-report.sh`、`scripts/lib/harness-evidence.sh`、`scripts/test-harness.sh`、Spring Boot application tests、`.github/workflows/verify.yml`。

## 验证分层

| 层级 | 命令或证据 | 必须/可选 | 当前结果 |
|---|---|---|---|
| RED fixture | `./scripts/test-harness.sh` | 必须 | 未运行 |
| 聚焦测试 | `./scripts/verify.sh handoff`、`./scripts/verify.sh startup`、`./scripts/verify.sh cleanup`、`./scripts/verify.sh quality` | 必须 | 未运行 |
| 架构或契约 | `./scripts/verify.sh architecture`、quality Schema/fingerprint 校验 | 必须 | 未运行 |
| 行为或运行环境 | `./scripts/verify.sh e2e`、全部 application startup smoke | 必须 | 未运行 |
| 完整门禁 | `./scripts/verify.sh clean`、`./scripts/verify.sh full` | 必须 | 未运行 |

## 任务状态

| ID | 行为目标 | 范围 | 状态 | 验证证据 | 阻塞/备注 |
|---|---|---|---|---|---|
| T1 | 固化交接、启动、清理和质量契约 | design、plan、docs | `passing` | `check-drift.sh`、独立计划 Review 问题已修正 | - |
| T2 | 自动生成 revision-bound handoff | handoff script、verify、fixture | `passing` | `bash scripts/test-handoff-harness.sh`、`bash scripts/test-harness.sh` | - |
| T3 | 验证全部应用标准启动路径 | startup tags、verify、fixture | `passing` | `bash scripts/test-startup-harness.sh`、真实 `harness-startup.sh`：10 application smoke 通过 | - |
| T4 | 提供只读及显式受控清理 | cleanup script、verify、fixture | `passing` | `bash scripts/test-cleanup-harness.sh` | - |
| T5 | 生成并校验 AI Reviewer 质量评审 | quality script、prompt、model、fixture | `passing` | `bash scripts/test-quality-harness.sh`；缺 response 返回 3，合法 response 生成快照 | - |
| T6 | 更新说明、CI 并完成质量评审与归档 | docs、workflow、independent reviewer、snapshot | `active` | `.harness` 迁移及聚合 fixture 通过；`mvn -q clean` 前后关键证据 SHA-1 一致；沙箱外 `full` 通过 1003 tests | 仍需刷新独立 Reviewer evidence 后归档 |

## 恢复状态

- 当前任务：T6，更新说明、CI 并完成质量评审与归档。
- 已完成：T1-T5 既有能力；Harness 状态已从 Maven 输出目录迁移到 Git ignore 的 `.harness/`，保留原 Reviewer response；新 clone、E2E 日志缺失、full 测试摘要、CI hidden artifact、陈旧 response 和相同 review scope 复用 fixture 已通过；`mvn -q clean` 前后关键证据 SHA-1 一致；沙箱外 `./scripts/verify.sh full` 通过 1003 tests、零失败。
- 阻塞项：`none`。
- 下一步：刷新 E2E 和当前 scope 的独立 AI Review，完成提交；计划归档在最终质量结论和关闭审计完成后执行。
- 不要修改：现有业务行为、未知 untracked 文件、真实 Git index 和外部基础设施数据。

## 任务 1：固化契约

- [x] 记录课程模板与当前 Harness 的映射、边界和旧结论变更原因。
- [x] 创建 active plan 并更新 `PROGRESS.md`、design/active indexes。
- [x] 运行 drift，确认文档结构和链接通过。

## 任务 2：自动交接报告

- [x] 为当前、失败、陈旧和无 active plan 场景增加 RED fixture。
- [x] 实现 JSON/Markdown handoff 生成器和 `verify.sh handoff`。
- [x] 让 full 完成后刷新 report/handoff；fixture 证明提前失败仍执行后处理并保留原失败码，且 full 原本成功但任一后处理失败时整体失败。
- [x] 验证生成产物不改变 worktree fingerprint。

## 任务 3：全部应用标准启动路径

- [x] 自动枚举全部 Spring Boot application 与 startup-smoke test，增加缺失、重复、零测试 RED fixture。
- [x] 为缺少真实 context smoke 的应用补测试并统一 Tag；逐个确认 Nacos、Dubbo、数据库、Redis 和远端 Facade 都被关闭或替换。
- [x] 实现 `verify.sh startup`，核对实际测试数和应用数。
- [x] 实现 `verify.sh init` 的 readiness、基础验证、startup 和启动命令展示契约；默认不启动后台进程，只有 `RUN_START_COMMAND=1` 才执行显式命令。

## 任务 4：受控清理

- [x] 增加默认只读、tmp allowlist、canonical/symlink 逃逸、PID provenance/liveness、apply 限界和幂等 RED fixture。
- [x] 实现仅针对 `.harness/tmp` 和已证明陈旧且属于本仓库的 PID 记录的扫描/删除，不终止进程。
- [x] 证明未知 untracked 文件、日志、构建输出和运行进程不被删除。

## 任务 5：AI Reviewer 质量评审

- [x] 定义中文质量模型与英文 AI-only Reviewer prompt/JSON Schema。
- [x] 增加 request、response review scope fingerprint、unit fingerprint、Schema、分数、证据和硬性上限 RED fixture。
- [x] 实现自动三维评分、review request 和 response 合并。
- [x] 实现执行状态文件排除、未变化单元结果复用，以及共享规则、根 Architecture 或公共构建输入变化时的全单元失效。
- [x] 生成机器 JSON 与中文 Markdown 质量快照。

## 任务 6：开发者说明、CI、质量评审与归档

- [x] 先用 fixture 将 Harness 状态根目录契约改为 `.harness/` 并确认 RED；增加 E2E 日志缺失时 report 降级，以及新 clone 空状态返回 `incomplete/review_required` 的测试。
- [x] 在 `.gitignore` 加入 `/.harness/`，统一修改脚本路径，并把旧 Maven 输出位置的本地产物一次性移动到 `.harness/`。
- [x] 更新当前文档和 CI Artifact 路径，设置 `include-hidden-files: true`；保留 completed plan 中的历史证据原文。
- [x] README 分点说明各 Harness 能力为什么存在、入口、证据和边界。
- [x] 更新 Harness Guide、reference/design/plan indexes、AGENTS workflow 和 CI artifact。
- [x] 运行 fixture、startup、cleanup、architecture、quick、e2e、clean、full 的预归档验证。
- [ ] 生成最终 review scope 的质量请求，使用独立上下文 AI Reviewer 完成全部待评审单元，并校验 A/B/C/D 快照。
- [ ] 所有实施任务拥有证据后写为 passing，归档计划并把 PROGRESS 切回 `none`；此后禁止修改 tracked 文件。

## 归档后关闭审计

本节不是任务状态表中的实施任务，不在证据存在前标记 passing。它只刷新归档操作影响的 worktree-bound evidence。

1. 在归档后的最终 fingerprint 上重新运行 `./scripts/verify.sh startup`、`./scripts/verify.sh cleanup`、`./scripts/verify.sh e2e`、`./scripts/verify.sh clean` 和 `./scripts/verify.sh full`，并确认 report/handoff 新鲜。
2. 再次运行 `./scripts/verify.sh quality`；review scope 未变化时复用已验证的 AI Reviewer 结果，并合成最终 fingerprint 的机器分数与快照。
3. 只读确认 `git diff --check`、full/quality freshness、旧 active plan 路径不存在且 completed 索引可达。
4. 任一步失败时，恢复 active plan、把受影响任务重新标为 active，修复后完整重复收尾协议。

## 回滚与残余风险

- 回滚：移除新增 verify 模式、脚本、startup Tag、质量文档和 CI 产物上传；现有 readiness/clean/quick/full/report/e2e 保持兼容。
- 未验证路径：真实 MySQL、Redis、Nacos、Kafka 和远程 Dubbo 全栈启动不属于本计划。
- 已接受但需跟踪的风险：AI Reviewer 具有非确定性，因此响应必须绑定 fingerprint、列证据并受机器上限约束；复评差异通过 findings 和 verdict 暴露。
