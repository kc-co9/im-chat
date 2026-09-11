# Code Review Guide

使用与实现过程隔离的上下文审查变更。审查者先读取目标设计、active plan、`ARCHITECTURE.md` 和 diff，不继承实现者的解释。

## Review Order

1. **Intent**：实现是否解决用户目标，是否遗漏明确要求。
2. **Behavior**：成功、失败、重试、并发和兼容场景是否有证据。
3. **Architecture**：依赖方向、数据所有权和协议边界是否保持。
4. **Simplicity**：是否引入没有调用方的抽象、配置或兼容层。
5. **Reliability and security**：故障是否隔离，输入是否验证，敏感数据是否泄露。
6. **Verification**：按 [单元测试规范](UNIT_TEST_GUIDE.md) 和 [编码规范](CODING_GUIDE.md) 检查断言、组件边界、场景深度、Mock 边界和确定性，确认测试验证行为而不是实现细节，Harness 没有被绕过。

## Output

按严重程度输出 findings，每条包含文件与行号、可复现风险和建议修复。没有问题时明确说明，并列出仍未覆盖的测试或运行时风险。总结放在 findings 之后。

不要用命名或格式建议掩盖行为错误，也不要仅凭实现者的说明判定正确。

正式验收或需要跨会话保留的 Review 使用以下证据化结构，不使用脱离证据的数字评分：

1. **结论**：只能是 `Accept`、`Revise` 或 `Block`。
2. **Findings**：按严重程度排列；没有发现时明确写 `none`。
3. **验证证据**：记录实际执行的命令、结果及其对应的工作树或 revision。
4. **证据缺口**：列出未执行的必要验证、原因和影响；没有则写 `none`。
5. **残余风险**：区分已接受风险与必须修复问题；没有则写 `none`。
6. **Harness 沉淀**：判断重复问题应进入规范、fixture、自动门禁还是保持 Review-only，并说明原因。

结论规则：存在必须修复的正确性、安全、数据或兼容问题时为 `Block`；存在非阻断但交付前应修正的问题或证据缺口时为 `Revise`；只有必要验证充分且不存在未处理 finding 时才为 `Accept`。
