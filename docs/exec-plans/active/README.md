# Active Execution Plans

## 当前计划

当前活跃计划：无。[Ops 模块聚合](../completed/2026-11-18-ops-module-aggregation.md) 已完成并归档，T1 `passing`；原实现验收证据及文档收尾边界见计划。最近完成的[Broker 成员应用流程可读性](../completed/2026-11-09-broker-membership-flow-readability.md) 已归档，T1–T3 均为 `passing`。

最近完成的[官方 JRaft 治理迁移与自有运维 CLI](../completed/2026-10-30-official-jraft-and-ops-cli.md)已归档：T1–T12 `passing`，正式 quality 与状态迁移 Review 均为 `Accept`。

## 历史与规则

- 拆分前的完整命令、失败、Review、旧进度和恢复记录保存在[执行历史](../history/2026-10-30-official-jraft-and-ops-cli-execution-history.md)，仅作历史事实，不覆盖当前状态。
- 本轮精简前的索引与进度全文保存在[入口原文](../history/2026-11-06-active-index-progress-before-trim.md)，保留原字节数和 SHA256。
- 目录规则见[执行计划规范](../../PLANS.md)。计划保持 WIP=1；只有同一输入上的可复查证据才能维持 `passing`。
- 当前不提交、不 push、不修改用户 Docker 配置，不删除用户数据、旧证据、镜像或缓存。
