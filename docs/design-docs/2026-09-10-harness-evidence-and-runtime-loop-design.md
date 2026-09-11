# Harness 证据与运行闭环设计

## 背景

当前仓库已经具备分层文档、执行计划、静态与架构门禁、聚焦行为测试、验证报告和清洁状态检查，但剩余缺口集中在证据真实性和真实运行反馈：历史验证没有绑定当前 worktree；`behavior` 是 JVM 内行为测试而非黑盒 E2E；readiness 不检查应用启动；产品行为没有长期状态目录；Metrics、Logs 和 Health 尚未形成可重复 workload；CI 没有完整执行这些入口。

这些问题必须按依赖顺序处理。没有 revision-bound evidence，后续 E2E 结果仍可能冒充当前证据；没有稳定黄金旅程，自动 loop 只会放大验证缺口。

## 总体设计

### 1. Revision-bound evidence

验证开始和结束时计算仓库状态指纹。指纹包含 `HEAD`、相对 `HEAD` 的 tracked diff，以及按路径排序的非忽略 untracked 文件内容；`target` 等 Git ignore 产物不参与。每份 verification JSON 记录：

- mode 与规范化命令；
- 开始和结束 `HEAD`；
- 开始和结束 worktree fingerprint；
- worktree 是否在验证期间保持稳定；
- 状态、耗时和完成时间。

源码或文档变化后，Harness report 将旧证据标为 `stale`，不再把历史 `full` 测试统计称为 `current`。共享指纹逻辑放在 `scripts/lib/harness-evidence.sh`，避免 verify 与 report 各自实现。

### 2. 能力状态与报告语义

保留原 `score` 兼容字段，同时增加语义明确的 `sensorCoverage`。五个子系统不再因文件存在直接标记为 `implemented`：文件缺失为 `missing`；入口存在但没有匹配当前 worktree 的成功证据为 `present` 或 `stale`；存在新鲜成功证据为 `verified`。

报告继续是观测入口，不是质量总分。`failed` 表示当前执行失败，`stale` 表示证据与当前 worktree 不一致，二者不得被 `passed` 掩盖。

### 3. 最小实时 E2E

新增测试聚合模块 `im-test`，并把现有纯测试模块 `im-architecture` 移动、改名为 `im-test/im-architecture-test`。新的实时链路测试位于同级 `im-test/im-e2e-test`。两个子模块都不拥有生产代码：Architecture Test 执行静态依赖与分层约束，E2E Test 通过真实网络端口组装运行链路。

```text
im-test
├── im-architecture-test
└── im-e2e-test
```

E2E 数据流：

```text
JDK WebSocket Client
  -> Netty WebSocket Server
  -> BrokerClient / Bolt
  -> Broker Server
  -> Message Facade test boundary
  -> Broker Management HTTP
```

测试使用真实 Netty、Bolt、Broker registry、Gateway registry、JSON codec 和 HTTP management endpoint。Account 与 Message 的远端实现使用测试边界替身，避免把 MySQL、Redis、Nacos 可用性混入每日确定性门禁。黄金旅程验证：

1. 有效 token 建立 WebSocket 后，Broker HTTP 能查询到用户路由；客户端伪造 userId 不覆盖认证身份。
2. 私聊发送帧和通知 ACK 穿过 WebSocket/Bolt/Broker 到达 Message Facade 边界，并返回协议响应。
3. 断开连接后路由移除；Gateway 重启并重连后路由重建。

这是实时链路范围内的 E2E，但不宣称覆盖 Message MySQL/Redis 持久化。存储行为继续由模块集成测试提供证据，并在 feature catalog 中标为对应证据层级；未来包含 Account、Message、MySQL、Redis 和 Nacos 的全栈 E2E 可在独立环境计划中扩展。

### 4. Feature catalog

在 `docs/product-specs/FEATURES.md` 维护关键用户可观察行为，而不是复制所有实施任务。每行包含 ID、Owner、行为、验证入口、覆盖状态和证据边界。静态覆盖状态使用 `uncovered`、`partial`、`covered`、`blocked`；它只回答稳定验证是否存在，不缓存某次运行结果。当前 worktree 是否已经执行并通过由 Harness report 的 freshness 回答，避免把 revision 写回被指纹覆盖的文件形成自引用。

Drift 检查状态枚举、必填列、唯一 ID 和非空验证入口。执行计划继续记录当前工作，PROGRESS 继续索引 active plan，三者不互相复制。

### 5. Runtime observability

`verify.sh e2e` 把完整输出保存在 `.harness/runtime/e2e.log`，并在 verification JSON 中记录日志路径、执行测试数和黄金旅程标识。测试通过 Broker management HTTP 验证运行状态，使固定 workload、运行信号和验收结果处于同一闭环。

本阶段不引入 OpenTelemetry Agent task trace。已有应用 TraceId、Metrics 和 Health 仍由运行模块拥有；Harness 只记录足够重放本次验证的命令、代码状态与日志。

### 6. 完整门禁与 CI

`verify.sh full`内部执行 readiness、clean、Harness fixture 和 Maven verify，避免只靠 AGENTS 约定串联。`im-test/im-e2e-test` 随 Maven full 执行；`verify.sh architecture` 和 `verify.sh e2e` 分别提供两个测试子模块的聚焦入口。

GitHub Actions 使用 `.nvmrc` 安装 Node，在 affected/full 前运行 readiness 和 clean。Broker、Gateway、common、Bolt 或 E2E 相关变更纳入 `im-test/im-e2e-test` 影响映射。当前文档、脚本和报告统一改用新的 Architecture Test 路径；completed plan 中的历史路径和命令保持原样。

## 失败处理

- 指纹不可计算时证据状态为 `unavailable`，报告不得称为 verified。
- 验证期间 worktree 变化时本次验证失败并记录起止指纹。
- E2E 端口启动、协议握手、路由查询或 Facade 调用任一步失败时保留日志，并输出 WHAT/WHY/FIX。
- E2E 测试必须使用有界条件等待，不使用 `Thread.sleep`。
- 外部环境不可用不影响确定性 E2E；需要 MySQL/Redis/Nacos 的验证明确保持为更高层环境证据。

## 不采用的方案

1. 只给验证结果增加 commit SHA：不能识别同一 commit 下的 dirty worktree。
2. 用 Docker Compose 启动全部生产依赖作为每日门禁：当前链路初始化、数据准备和 Nacos/Dubbo 装配成本过高，容易把基础设施波动混入每日 E2E；本轮先固定实时链路范围。
3. 用文件存在计算 Harness 成熟度：只能衡量入口覆盖率，不能证明能力有效。
4. 立即引入自动 Agent loop 或 Graph：真实锚点和证据状态稳定之前不具备可靠停止条件。

测试模块继续平铺在仓库根目录的方案也不采用。`im-architecture` 已经是纯测试模块，在新增 E2E Test 时建立 `im-test` 聚合层可以明确生产模块与仓库级测试模块的边界，并为未来契约或性能测试留下稳定位置。

## 验证策略

每项行为修改先增加 fixture 或测试并确认 RED，再实施 GREEN。阶段验收包括：Harness fixture、feature catalog fixture、E2E 聚焦验证、clean、quick、full、临时 index Markdown 链接检查和 Harness report JSON 解析。

## 执行计划

实施任务和实时证据记录在 [Harness 证据与运行闭环实施计划](../exec-plans/completed/2026-09-10-harness-evidence-and-runtime-loop.md)。
