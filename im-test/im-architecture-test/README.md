# im-architecture-test

`im-architecture-test` 是仓库级架构反馈模块，通过 ArchUnit 检查编译后的项目类，防止模块依赖和分层边界在持续修改中发生漂移。它由 `im-test` 聚合，不包含生产代码。

## 关键技术点

- 使用 ArchUnit 执行确定性的包依赖检查；
- 集中依赖主要运行模块，避免在每个业务模块中复制架构规则；
- 检查 `im-common`、插件、SDK、Facade、业务服务和领域层的依赖方向；
- 通过 `scripts/verify.sh` 纳入本地开发和 CI 的统一验证流程。

## 运行

```bash
./scripts/verify.sh architecture
```

或者直接运行：

```bash
mvn -q -pl im-test/im-architecture-test -am test
```

## 规则维护

架构规则必须表达稳定且可以机械判断的边界。新增规则前先确认当前代码满足该约束，并提供能够证明规则有效的测试场景。

如果规则失败，应优先修复依赖方向。确有合理例外时，应将例外缩小到明确的类或包并记录原因，不能整体关闭规则，也不能使用宽泛白名单掩盖新的违规。
