# im-test

`im-test` 聚合只服务仓库级验证、且不拥有生产代码的测试模块。业务模块的 Unit Test 和 Integration Test 继续与所属实现放在一起；只有需要读取多个模块或组装多个 runtime 的测试进入这里。

## 模块结构

| 模块 | 职责 |
|---|---|
| `im-architecture-test` | 使用 ArchUnit 和 POM 检查模块依赖、分层与公共边界 |
| `im-e2e-test` | 通过真实 HTTP、WebSocket、Bolt 端口验证实时链路黄金旅程 |
| `im-load-test` | 保存负载测试的纯契约、测试侧工具和显式压测场景；Maven 不自动发起真实负载 |
| `im-routing-ha` | 保存 25 类路由高可用场景清单，并校验每类场景都有可定位的真实拓扑或聚焦测试证据 |

## 验证

```bash
./scripts/verify.sh architecture
./scripts/verify.sh e2e
./scripts/verify.sh routing-ha
npm --prefix im-test/im-load-test test
mvn -q -pl im-test/im-load-test -am test
```

`im-load-test` 的 Maven `test` 只编译和运行测试代码，不启动 Compose、k6 或真实负载。完整交付仍使用：

```bash
./scripts/verify.sh full
```

`im-routing-ha` 的 Maven 测试只校验 25 类场景清单完整、标识唯一且每项声明证据来源。真实拓扑由 `verify.sh routing-ha` 启动；runner 只接受本轮新生成且通过的 Surefire 用例，并把拓扑探针和测试方法写入 `.harness/routing-ha/scenarios.jsonl`。因此单独运行模块测试不等于 routing-ha 通过。
