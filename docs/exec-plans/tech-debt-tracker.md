# Technical Debt Tracker

Track deliberate, actionable exceptions that cannot be resolved in the current change. Each item must have evidence, impact, an owner or owning module, and an exit condition.

| ID | Area | Evidence | Impact | Exit condition | Status |
|----|------|----------|--------|----------------|--------|
| TD-01 | im-gateway SDK / Bolt DEV | `GatewayClient.java:107` 无条件 `setIsolatedContainerNetwork(true)`；来源：Bolt 包职责计划 Review W7 | 仅 DEV 模式；真实 acquire 仍按实际策略复核地址，不构成绕过，但 SDK 声明的隔离网络不随部署配置变化 | 改为由调用方配置或部署声明决定，并有正反测试 | open |
| TD-02 | scripts / Harness | `scripts/test-bolt-dev-contract.sh` 未被 `verify.sh` 或 Harness 自测调用；来源：Review W8 | DEV 合同只能手动执行，回归不会在 quick/full 中失败 | 接入既有 Harness 自测入口并有负例证明会失败，或删除该脚本 | open |
| TD-03 | im-bolt 失败分类 | DEV 非开发地址在 `BoltControlledClient.acquire` 拒绝时，`BoltClientInvoker` 已进入传输阶段，经 typedFailure 归 `PROTOCOL`；统一 invoke 设计表要求目标/配置无效归 `LOCAL`；三个 `BoltConsumerConfigurationTest` 固定现状；来源：复审 R1 | 分类与设计不一致；probe 只把 `TRANSPORT_UNAVAILABLE` 计为 UNHEALTHY，运行影响低 | 经设计确认后改为 LOCAL（acquire 前校验或包装异常）并同步消费方断言，或在设计中批准该偏差 | open |
| TD-04 | im-test / routing-ha Harness | 2026-10-29 偶发失败：attempt3 Harness 进程 helper KILL 窗口 fixture；attempt4 `AutomaticFailureTlsHaTest` 测试端口预约竞争；`stoppedFollowerAutomaticallyFencesAndRebuildsWithIndependentTlsReports` 的 follower report RPC 断言位于 await 之外（`AutomaticFailureTlsHaTest.java:320-323`），单次为 0，随后 3/3 通过；attempt7 `stoppedLeaderRequiresFreshReportsAndReplacementCompletesDegradedWithoutGatewayAck` 在 `:317` 要求每个存活节点都有 epoch0 watermark，但 watermark 只在 fence ACK 路径写入，fence RPC（500ms 超时）未确认时生产按设计走 quarantine 后提交，测试断言强于设计保证；`FenceResult` 未记录日志，无法确认是哪个节点 | full 可能偶发失败需重跑；均为测试基础设施，未见生产缺陷证据 | 分别修复为有界等待/端口独占、watermark 断言区分 ACK 与 quarantine 路径后，在多次重复运行中稳定通过 | open |

Do not use this file as a general backlog or a substitute for fixing a failing verification rule.
