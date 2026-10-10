# Harness Feedback Tracker

记录 Harness 自身产生的误报、偶发测试和反馈性能问题。每条记录必须包含证据、所有者和退出条件；修复后保留记录并将状态改为 `CLOSED`。

| ID | Type | Evidence | Owner | Exit condition | Status |
|----|------|----------|-------|----------------|--------|
| HF-001 | PERFORMANCE | 原 `HARNESS_GUIDE.md` 569 行混合规则矩阵与运行流程；215 条规则行已原样迁入 `HARNESS_RULE_MATRIX.md`，Guide 缩至 350 行 | Harness 文档维护者 | references 索引按任务路由，规则所有者/检查器/Review 列保持唯一登记；Markdown 链接、drift 与 Harness fixture 检查通过 | CLOSED |

Type 使用 `FALSE_POSITIVE`、`FLAKY_TEST` 或 `PERFORMANCE`。状态使用 `OPEN` 或 `CLOSED`。
