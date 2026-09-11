# im-test

`im-test` 聚合只服务仓库级验证、且不拥有生产代码的测试模块。业务模块的 Unit Test 和 Integration Test 继续与所属实现放在一起；只有需要读取多个模块或组装多个 runtime 的测试进入这里。

## 模块结构

| 模块 | 职责 |
|---|---|
| `im-architecture-test` | 使用 ArchUnit 和 POM 检查模块依赖、分层与公共边界 |
| `im-e2e-test` | 通过真实 HTTP、WebSocket、Bolt 端口验证实时链路黄金旅程 |

## 验证

```bash
./scripts/verify.sh architecture
./scripts/verify.sh e2e
```

完整交付仍使用：

```bash
./scripts/verify.sh full
```
