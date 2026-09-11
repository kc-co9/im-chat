# 执行工件模板融合实施计划

> **执行约束：** 用户要求本阶段不使用 subagent。除非用户明确要求，否则不创建 Git commit。

## Sprint Contract

- 目标：用一个计划模板和可执行结构检查吸收 DTPet 模板的有效契约，不引入第二套 Harness 工作区。
- 范围：执行计划模板、计划规范、active plan drift fixture/checker、Code Review 输出契约、相关索引与 Harness Guide。
- 不做事项：不实现 E2E、feature catalog、revision-bound evidence、运行可观测性或自动 Agent loop；不新增独立 startup/handoff/clean/quality 模板。
- 验收标准：active plan 缺少必需章节时 drift 失败；标准模板覆盖完整计划契约；Review 输出包含结论、证据与风险；Harness fixture、clean、quick 通过。
- 风险链路：文档所有权、active plan 生命周期、跨会话恢复、checker 误报。
- 依赖输入：`docs/PLANS.md`、`PROGRESS.md`、`CODE_REVIEW_GUIDE.md`、`check-drift.sh`、DTPet 模板目录。

## 事实源

- 设计：[执行工件模板融合设计](../../design-docs/2026-09-10-execution-artifact-template-integration-design.md)。
- 外部参考：DTPet `docs/harness/templates`，仅吸收机制，不复制目录。
- Harness 规则：`docs/references/HARNESS_GUIDE.md`。
- 相关代码入口：`scripts/check-drift.sh`、`scripts/test-harness.sh`。

## 验证分层

| 层级 | 命令或证据 | 必须/可选 | 当前结果 |
|---|---|---|---|
| RED fixture | `./scripts/test-harness.sh` | 必须 | 缺验证分层、缺状态表、非法 `done` 均先按预期失败 |
| Harness fixture | `./scripts/test-harness.sh` | 必须 | 通过 |
| 清洁状态 | `./scripts/verify.sh clean` | 必须 | 通过，8 秒 |
| 快速门禁 | `./scripts/verify.sh quick` | 必须 | 通过，150 秒 |
| 完整门禁 | `./scripts/verify.sh full` | 必须 | 通过，270 秒；1002 个测试零失败 |
| 差异检查 | `git diff --check` | 必须 | 通过 |

## 任务状态

| ID | 行为目标 | 范围 | 状态 | 验证证据 | 阻塞/备注 |
|---|---|---|---|---|---|
| T1 | 固定计划模板和计划规范 | docs | `passing` | 唯一模板、PLANS 必需章节和 Review 输出契约已落地；结构 fixture RED/GREEN | - |
| T2 | 拒绝结构不完整的 active plan | scripts/fixtures | `passing` | 缺失验证分层、缺少状态表和非法 `done` 状态均完成 RED/GREEN fixture | - |
| T3 | 固定证据化 Review 输出 | docs | `passing` | Code Review Guide 已固定三种结论、证据、缺口、风险和 Harness 沉淀字段 | - |
| T4 | 完成验证与归档 | docs/scripts | `passing` | 临时 index drift、Harness fixture、clean、quick、full 和 diff 检查全部通过 | - |

## 恢复状态

- 当前任务：`none`，全部任务已经完成。
- 已完成：DTPet 模板审计、唯一执行计划模板、PLANS 结构说明、证据化 Review 输出，以及缺章节和非法状态的 RED/GREEN fixture。
- 阻塞项：`none`。
- 下一步：本计划已经归档；后续按独立计划推进 revision-bound evidence、E2E、feature catalog 和运行可观测性。
- 不要修改：E2E、feature catalog、Harness report revision 绑定和运行时启动能力。

## 回滚与残余风险

- 回滚：删除计划模板及结构 checker，恢复 PLANS、Review Guide 和 Harness Guide 对应说明。
- 残余风险：Markdown 结构检查只验证稳定标题和窄状态枚举，不判断 Sprint Contract 的业务语义质量；语义仍由 Review 负责。
