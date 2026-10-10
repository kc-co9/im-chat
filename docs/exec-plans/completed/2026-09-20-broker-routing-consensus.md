# Broker 权威路由与 DDD Review 收口计划

## Sprint Contract

- 目标：完成 Broker DDD、应用编排、接口/基础设施与请求责任链的初轮 Review 收口，刷新真实验证证据。
- 范围：im-broker、受影响的 Gateway/Message 契约、im-raft/im-cache、Architecture Test、E2E、routing-ha、Harness 与当前文档。
- 非目标：不提交 Git，不把本机开发测试当作生产容量结论，不伪造独立 Reviewer 响应。2026-10-10 用户授权使用子代理进行独立 Review。生产候选容量定规格由[独立容量计划](../active/2026-10-10-broker-routing-capacity.md)承接。
- 验收：Broker 全模块、architecture、startup、behavior、e2e、routing-ha、quick、clean、full、cleanup/handoff 取得当前证据；独立 Reviewer 针对本计划代码/集成范围复评并记录结论。容量资格不由本计划宣称。
- 已批准决策：领域 model/repository/service；应用层直接编排；Adapter 接收领域值；缓存使用 JetCache；责任链按配置顺序执行，业务 Handler 位于链尾，不保留 stage、Terminal 或 support 空接口。
- 环境：Java 21、Maven 3.8.4、Node 20、Docker；仅清理已确认的生成目录和 Harness 自有资源。
- 计划压缩：原始 1523 行任务与过程证据完整保存在 [Review 历史](../history/2026-10-09-broker-review-history.md)，原 PROGRESS 在 [进度历史](../history/2026-10-09-broker-progress-history.md)。历史材料不是第二份活跃计划。
- 原任务映射：T1–T9、T11–T12 的实现记录保留历史；本计划的 T13 统一刷新其受影响验收。原 T10 的完整容量条件于 2026-10-10 用户确认后移交[独立容量计划](../active/2026-10-10-broker-routing-capacity.md)，未取消或降低。范围拆分是验收管理变更，不代表容量已通过。

## 事实源

- 根 ARCHITECTURE.md、im-broker/ARCHITECTURE.md 及各 owning module README。
- 设计或产品规格：[权威路由设计](../../design-docs/2026-09-19-broker-routing-consensus-design.md)、[Raft 插件](../../design-docs/2026-09-29-raft-plugin-design.md)、[恢复领域职责](../../design-docs/2026-10-07-broker-route-recovery-domain-design.md)。
- docs/references/CODING_GUIDE.md、HARNESS_GUIDE.md、UNIT_TEST_GUIDE.md、QUALITY_REVIEW_PROMPT.md。
- 机器结果以 .harness/report.json、.harness/verifications/ 和 .harness/runtime/ 为准；旧日期的 passing 不替代当前证据。

## 验证分层

| 顺序 | 命令 | 要求 |
|---|---|---|
| 1 | ./scripts/verify.sh readiness | 工具和环境 |
| 2 | mvn -q -pl im-broker/im-broker-server -am clean test | 全模块行为，修复失败后再扩大 |
| 3 | ./scripts/verify.sh architecture | 模块/包边界 |
| 4 | ./scripts/verify.sh startup | 所有应用启动 |
| 5 | ./scripts/verify.sh behavior | 批准的 realtime 场景 |
| 6 | ./scripts/verify.sh e2e | HTTP/WebSocket/Bolt 跨模块边界 |
| 7 | ./scripts/verify.sh routing-ha | 3 Broker + 2 Gateway，25 项，资源清理 |
| 8 | ./scripts/verify.sh quick | drift、Harness 自测、architecture |
| 9 | ./scripts/verify.sh clean 和 ./scripts/verify.sh full | 完整代码与集成门禁 |
| 10 | ./scripts/verify.sh cleanup、quality、handoff | 资源与独立 Reviewer，不把 review_required 当通过 |

## 任务状态

| ID | 目标 | 状态 | 完成条件 |
|---|---|---|---|
| T13 | 初轮 Review 集成验证、Harness 与文档收敛 | passing | 代码/集成独立 Review `Accept`，稳定工作树 `full` 1683 tests、0 failures、0 errors、2 skipped，HA 25 项通过，quality 退出 0；容量缺口由独立计划跟踪 |

## 详细任务

### T13 初轮 Review 收口

文件范围：im-broker 全部受影响源码/测试；im-service/im-message 测试夹具；im-test/im-e2e-test；scripts/run-routing-ha-scenarios.sh；当前 Architecture/README、Harness、Coding Guide 与状态文件。

- [x] 核对所有 Review 决策及历史未完成项，不撤销用户手改内容。
- [x] 删除过时责任链规则，保持身份拒绝和 Lease 环绕；将完整性检查放测试。
- [x] 压缩活跃计划与 PROGRESS，保留完整历史。
- [x] 修复 query 参数分类、Gossip 调度参数归属、Message 旧构造器和 E2E bootstrap 字段。
- [x] 核对异常契约：未知 quorum/提交失败继续异常传播，测试证明不授权、不登记操作。
- [x] 执行 Broker reactor clean test；首次运行退出 0，最终重跑被 IDE/JDT 写入异常 class 干扰，仍需无并发编译重验。
- [x] 刷新 architecture；磁盘不足后清理确认的生成依赖，复跑退出 0。
- [x] 执行 startup、behavior、e2e，均退出 0；E2E 使用 clean test 排除旧 target。
- [x] routing-ha 重跑通过 25 项场景并清理自有资源；quick 重跑退出 0，证据和 resource ledger 已刷新。
- [x] 显式 Gateway 注销改为 Gossip-before-delete，失败保留注册和连接供重试，聚焦测试退出 0。
- [x] 应用层 `BrokerMembershipSchedulerTest` 换届标志/恢复操作聚焦测试退出 0；真实多节点 Raft 换届与成员恢复由 routing-ha 25 项场景覆盖。
- [x] clean 已退出 0。Docker Desktop 后端无响应导致首次 `full` 的 routing-ha 超时退出 124；重启 Docker 后，独立 routing-ha 25 项及完整 `verify.sh full` 均退出 0。`full` 汇总 1680 tests、0 failures、0 errors、2 skipped；HA 自有资源清理完成。
- [x] HARNESS_GUIDE 补充 Gateway 删除事实顺序和失败回归约束；未满足的验收维持开放状态。
- [x] 移除 FrameDeliveryAppProperties、BrokerDiagnosticAppProperties、BrokerClusterAppProperties 重复配置投影；源 typed properties 保留在基础设施配置层，由装配层提取所需 Duration、身份值及领域参数传入应用服务，避免应用层反向依赖 infrastructure。验证：Broker reactor test、architecture、quick。
- [x] 实施 docs/design-docs/2026-10-10-routing-capacity-driver-design.md 对应 driver、环境配置契约、取消恢复与测试；文件范围 im-test/im-load-test/tools/routing-capacity-driver.mjs、tests/routing-capacity-driver.test.mjs、scripts/routing_capacity.sh、scripts/run-routing-capacity-scenarios.sh。验证：Node 211 tests、test-routing-capacity-harness.sh、quick、clean、full。
- [x] 同步 load-test README、HARNESS_GUIDE、设计 index；刷新 quality request，由独立子代理只读评审并输出响应，执行 verify.sh quality。Reviewer verdict 为 Revise，生产候选容量环境缺口保留。
- [x] 独立 Broker 范围 Review 发现 Gateway 关闭在副作用前登记幂等成功；修正为进行中/完成两阶段，关闭失败释放占位，新增失败重试和进行中拒绝测试。`mvn -q -pl im-gateway/im-ws-gateway/im-ws-gateway-server -am -Dtest=ConnectionCloseHandlerTest,ConnectionCloseDeduplicatorTest -Dsurefire.failIfNoSpecifiedTests=false test` 退出 0；待复评和完整门禁刷新。
- [x] 完整门禁发现 `RaftLibrarySpikeTest` 在 voter 与 learner 分别申请端口时复用了已释放的临时端口，触发 `BindException`；测试实例改为记录已分配端口并跳过重复值。`mvn -q -pl im-broker/im-broker-server -Dtest=RaftLibrarySpikeTest test` 退出 0；完整门禁待重跑。
- [x] Harness 聚合运行曾在 routing-ha 超时反例附近偶发传播 124；`test-routing-ha-harness.sh` 的超时 fixture 改用 `exec sleep`，使被终止进程直接成为进程组首进程，保持 1 秒超时与 124 断言。`bash scripts/test-harness.sh` 已退出 0；完整门禁继续复核。
- [x] 对拆分后的 Broker 代码/集成验收范围进行独立复评；Gateway 关闭去重 Block finding 已修复，Reviewer 对代码/集成范围给出 `Accept`、无剩余代码 finding。当前 `./scripts/verify.sh full` 退出 0（1683 tests、0 failures、0 errors、2 skipped），`./scripts/verify.sh quality` 退出 0；其整体结论仍为 `Revise`，因为独立容量计划缺真实候选环境。归档后门禁另按下方关闭审计执行。
- [x] 归档后首次 `full` 的 HA 中 `broker-ha-1` 向 Nacos 注册时报客户端仍为 `STARTING`；隔离 Compose 健康条件补充命名 gRPC 9848。`bash scripts/test-routing-ha-harness.sh` 先 RED 后 GREEN，真实 `./scripts/verify.sh routing-ha` 25 项通过且资源清理完成；`quick/clean/full` 继续刷新。失败证据不以旧 HA 通过覆盖。
- [x] `full` 的聚合 Harness 自测在 `routing-ha` 成功 fixture 默认 2 秒预算下偶发误超时；xtrace 已证实 124 发生于 success fixture，而非超时反例。普通 fixture 调整为 15 秒，专门超时反例仍为 1 秒；`bash scripts/test-routing-ha-harness.sh`、`bash scripts/test-harness.sh`、`quick`、`clean` 和 `full` 均退出 0；最新 `full` 汇总 1683 tests、0 failures、0 errors、2 skipped，HA 25 项通过。

归档后关闭审计（不可提前勾选）：T13 达到 `passing` 后将本计划原样移至 `docs/exec-plans/completed/`，同步 `PROGRESS.md`、active/completed 索引及容量计划对本计划的链接，然后停止修改 tracked 文件。依次执行 `./scripts/verify.sh readiness`、`./scripts/verify.sh architecture`、`./scripts/verify.sh startup`、`./scripts/verify.sh behavior`、`./scripts/verify.sh e2e`、`./scripts/verify.sh routing-ha`、`./scripts/verify.sh quick`、`./scripts/verify.sh clean`、`./scripts/verify.sh full`、`./scripts/verify.sh cleanup`、`./scripts/verify.sh quality`、`./scripts/verify.sh handoff`，核对证据 fingerprint。独立 Review 的 scope 排除执行状态文件且 fingerprint 未变化时才复用；否则重新申请。任一步失败则将本计划移回 active、把 T13 从 `passing` 重开为 `active`，修复后重新执行关闭流程。

## 恢复状态

- 当前任务：T13 `passing`，归档后关闭审计进行中，WIP=0；容量计划 C1 因候选环境缺失而 blocked。
- 已完成：初轮 Review 实现、聚焦测试、architecture/startup/behavior/e2e/quick/clean/cleanup/handoff；Gateway 注销失败恢复。
- 当前环境：Docker Desktop 后端已重启恢复，独立 routing-ha 25 项场景及完整 `verify.sh full` 均通过；HA 资源账本为空。首次 full 超时属于当时 Docker daemon 无响应，保留历史失败证据。
- 已知外部缺口：真实生产候选拓扑、机器规格与容量测量已移交独立容量计划；代码/集成范围独立复评 `Accept`，整体质量 `Revise` 保留容量缺口。
- 下一步：执行归档后关闭审计，核对新 fingerprint；容量环境由独立计划跟踪。
- 不要修改：无关 worktree 用户变更、阈值/白名单以绕开失败、语义质量分数。

## 回滚与残余风险

- 不执行破坏性 Git 回滚；保持所有无关改动。
- 本机 Compose 不证明生产 Raft 原生端口安全部署或生产容量；这些风险继续由独立容量计划跟踪。
- Gateway 显式注销的 Gossip 删除重放和成员换届恢复串联证据仍在验收表，不静默延期。
- 本次归档后关闭审计失败，计划已恢复 active 并重开 T13；本计划最终归档仍不代表独立容量计划完成。
