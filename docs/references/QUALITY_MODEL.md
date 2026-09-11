# 质量模型与 AI Reviewer 流程

本模型沿用课程 evaluator rubric 的六个维度，每项 0-2 分。`verification`、`reliability`、`handoff readiness` 由当前 revision 的机器证据计算；`correctness`、`scope discipline`、`maintainability` 必须由独立上下文 AI Reviewer 填写。

AI Reviewer 不修改源码、计划或稳定文档，只返回结构化 JSON。响应必须包含 request 的 `reviewScopeFingerprint`、每个质量单元的 unit fingerprint、三项语义分数、证据路径、findings 和 `Accept`/`Revise`/`Block` 结论。脚本校验 Schema、分值范围和证据路径后才会生成快照。

总分为 12 分：A=11-12、B=9-10、C=6-8、D=0-5。full/编译失败直接为 D；Critical 架构或安全问题最高 C；实时变更缺少新鲜 E2E 最高 B；Reviewer 为 Revise 最高 B、Block 为 D。没有当前 full evidence 时输出 `incomplete`。

开发者只需让 Coding Agent 执行“质量评审”：Agent 运行 `./scripts/verify.sh quality` 生成请求，使用独立上下文 Reviewer 完成 JSON，再运行同一命令生成中文快照。没有 response，或 response 的 request/scope 与当前代码不匹配时，命令以 `review_required` 退出，不会把缺失或陈旧 Review 当成 0 分或通过。
