# 执行工件模板融合设计

## 背景

DTPet 的 `docs/harness/templates` 使用模板固定实施计划、启动检查、会话交接、清洁状态、质量快照和评审结果的字段。模板本身只是过程工件的结构契约；只有产生实例、进入工作流并接受验证时，才形成实际 Harness 能力。

IM Chat 已由 `docs/PLANS.md`、active execution plan、`PROGRESS.md`、`verify.sh readiness/clean` 和 Code Review Guide 分别拥有这些职责。整体复制模板目录会制造第二套入口，并使计划、恢复状态和 Review 结论发生漂移。

## 决策

只新增 `docs/exec-plans/TEMPLATE.md`，作为跨模块、跨会话和高风险工作的唯一实施计划模板。模板统一包含：

- Sprint Contract；
- 事实源；
- 验证分层；
- 任务状态；
- 恢复状态；
- 回滚与残余风险。

`docs/PLANS.md` 继续拥有计划语义和生命周期，模板只提供填写骨架。`check-drift.sh` 检查每份 active plan 是否包含必需章节；`verify.sh clean` 已调用 drift，因此不再实现第二套结构检查。

启动就绪和清洁状态继续由 `verify.sh readiness/clean` 与 AGENTS 工作流承担。会话交接继续由 active plan 的恢复状态和根 `PROGRESS.md` 承担。它们不生成独立模板实例。

Code Review Guide 增加证据化输出契约：结论只能是 `Accept`、`Revise` 或 `Block`，同时记录 findings、验证证据、证据缺口、残余风险和 Harness 沉淀判断。不引入容易被主观优化的数字评分。

## 不采用的方案

1. **复制完整 `docs/harness/templates`**：与现有文档所有权重复，文件存在也不能证明模板被使用。
2. **所有小任务强制生成计划和交接文件**：增加维护成本，并与 `docs/PLANS.md` 的小型维护豁免冲突。
3. **人工 A/B/C/D 质量快照**：没有稳定、可复现的评分输入时容易陈旧；后续应优先从 revision-bound verification、E2E 和开放风险生成客观状态。

## 验证

- Fixture 证明 active plan 缺少必需章节时 drift 失败，并输出 WHAT/WHY/FIX。
- Fixture 证明符合模板结构的 active plan 不触发结构违规。
- `scripts/test-harness.sh`、`verify.sh clean`、`verify.sh quick` 和 `git diff --check` 通过。

## 后续边界

本轮不实现黑盒 E2E、行为级 feature catalog、revision-bound evidence 或运行可观测性闭环。这些能力需要独立设计和验证，不与模板融合捆绑。

## 执行计划

实施步骤和证据记录在 [执行工件模板融合实施计划](../exec-plans/completed/2026-09-10-execution-artifact-template-integration.md)。
