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
