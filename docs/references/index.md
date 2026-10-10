# Engineering References

这里保存当前开发和 Review 使用的稳定工程规范。架构事实见根 [ARCHITECTURE.md](../../ARCHITECTURE.md)，具体方案决策见 [design-docs](../design-docs/index.md)。

## 开发时

- Java、DDD、CQRS、Adapter、Spring 和组件使用：[Coding Guide](CODING_GUIDE.md)
- 模块采用 DDD、技术分层或混合模式的选择：[Module Structure Guide](MODULE_STRUCTURE_GUIDE.md)
- DDL、Mapper XML 和 MyBatis 注解 SQL：[SQL Guide](SQL_GUIDE.md)
- 单元测试边界、断言和确定性：[Unit Test Guide](UNIT_TEST_GUIDE.md)

## Review 与交付时

- Findings 优先的独立审查流程：[Code Review Guide](CODE_REVIEW_GUIDE.md)
- 提交边界、验证和提交信息：[Git Guide](GIT_GUIDE.md)
- 按规则查所有者、当前检查器及 Review 边界：[Harness Rule Matrix](HARNESS_RULE_MATRIX.md)
- 运行验证、归因失败、维护 fixture 和处理反馈：[Harness Guide](HARNESS_GUIDE.md)
- 机器证据与独立 AI Reviewer 的六维评分：[Quality Model](QUALITY_MODEL.md)

规范正文只在其所有者文档维护。其他 README、设计和计划应链接到这里，不复制整段通用规则。

定位具体规则时先读对应所有者规范，再在规则矩阵中搜索业务能力或检查器；不要把矩阵中的历史传感器名称当作新的业务定义，也不要因 `Review=是` 推断该规则已自动执行。
