# Broker 生产候选路由容量验收计划

## Sprint Contract

- 目标：在真实隔离候选环境完成 Broker 路由容量测量，给出可审查的拓扑与机器规格结论。
- 范围：`scripts/routing_capacity.sh`、`scripts/run-routing-capacity-scenarios.sh`、`im-test/im-load-test/tools/routing-capacity-driver.mjs`、环境专用 hook、候选部署、监控、容量报告与 Reviewer 复评。
- 非目标：不以本机 Compose、短时演练或模拟指标替代正式容量证据；不放宽既有 30 轮矩阵、阈值或清理条件；不提交凭据。
- 验收：每个拟推荐的真实候选拓扑分别完成五阶段、三次重复、连接/消息两类 workload 共 30 轮；3-voter 与 5-voter 的生产建议不得互借证据。真实指标、故障结果、普通数据不变与资源清理均通过；按角色记录机器规格并取得独立 Reviewer 对完整证据的结论。
- 风险与依赖：需要独占的生产候选环境、WebSocket 入口、k6、可查询监控和具备故障注入权限的六个环境 hook。正式执行约半天，应预约窗口；环境未提供时保持 `blocked`。
- 决策来源：[路由容量 Driver 设计](../../design-docs/2026-10-10-routing-capacity-driver-design.md#验收分轨决策2026-10-10)。本计划承接原 [Broker Review 计划](../completed/2026-09-20-broker-routing-consensus.md)的 T10，不改变其容量契约。

## 事实源

- 设计或产品规格：[路由容量 Driver 设计](../../design-docs/2026-10-10-routing-capacity-driver-design.md)及[权威路由设计](../../design-docs/2026-09-19-broker-routing-consensus-design.md)。
- 根 `ARCHITECTURE.md`、`im-broker/ARCHITECTURE.md`、`im-test/im-load-test/README.md`、`docs/references/HARNESS_GUIDE.md`。
- `im-test/im-load-test/config/routing-capacity.json` 拥有固定矩阵与阈值；`im-test/im-load-test/config/routing-capacity.environment.example.json` 是环境配置契约，不是可运行环境。
- `.harness/routing-capacity/`、`.harness/verifications/routing-capacity.json` 和独立 Reviewer 响应保存机器证据。

## 验证分层

| 层级 | 命令或证据 | 当前结果 |
|---|---|---|
| 工具契约 | `npm test -- --runInBand`（在 `im-test/im-load-test`）、`./scripts/test-routing-capacity-harness.sh` | 本轮通过 211 项 Node 测试及 Harness 自测；源码变更后重跑 |
| 环境演练 | 候选环境上的六个 hook、真实采样、故障注入和 cleanup 演练 | 待候选环境；演练不产生容量资格 |
| 正式测量 | `ROUTING_CAPACITY_ENV_CONFIG=<候选环境配置> ROUTING_CAPACITY_TOPOLOGY_ID=<拓扑> ./scripts/verify.sh routing-capacity` | 缺候选环境，按 fail-closed 拒绝 |
| 交付门禁 | `./scripts/verify.sh clean`、`./scripts/verify.sh full`、`./scripts/verify.sh cleanup`、`./scripts/verify.sh quality` | Broker Review 曾通过；本计划最终输入确定后须刷新 |

## 任务状态

| ID | 目标 | 状态 | 完成条件 |
|---|---|---|---|
| C1 | 候选环境与 hook 演练 | blocked | 每个拟测拓扑的真实环境配置、六个 hook、拓扑/规格与清理演练通过 |
| C2 | 正式容量测量与复评 | not_started | 各拟推荐拓扑各自的 30 轮真实证据、规格结论及独立 Reviewer 复评有效 |

## 详细任务

### C1 候选环境与 hook 演练

文件范围：仓库外的候选环境配置与六个 hook；若通用契约需修正，限定为 `im-test/im-load-test/tools/routing-capacity-driver.mjs`、其测试、`scripts/routing_capacity.sh`、`scripts/run-routing-capacity-scenarios.sh` 及所属文档。

- [ ] 对每个拟推荐的 3-voter/5-voter 拓扑分别确定独占环境、Broker/Gateway 数量、机器规格、WebSocket URL、k6 与监控访问方式；依据预期业务峰值预先固定 connection/message 目标档位与选择理由，不根据本轮结果降低档位。凭据只放仓库外。
- [ ] 对每个拟测拓扑按环境配置示例实现或验证 `prepare`、`fault`、`metrics`、`restore`、`cleanup`、`inspect`；指标从真实监控与原始 k6 summary 读取，不用固定数字。
- [ ] 对每个拟测拓扑分别做短时演练，检查六个 hook 的输入/输出、故障窗口、真实指标来源、失败恢复、普通数据基线和资源清理；记录日志与权限边界。任一拓扑未经演练不得进入 C2；演练结果明确标为非正式容量证据。
- [ ] 若修改通用 driver，先增加失败 fixture，再运行 `npm test -- --runInBand`（`im-test/im-load-test`）、`./scripts/test-routing-capacity-harness.sh`、`./scripts/verify.sh quick`。

### C2 正式容量测量与复评

文件范围：仓库外环境配置与 hook、`.harness/routing-capacity/` 运行证据、根 `README.md` 与受影响模块 README、运维文档、候选规格说明、`docs/references/HARNESS_GUIDE.md` 与本计划；仅缺陷需要时修改通用 driver 与测试。

- [ ] 预约独占窗口，按拟推荐的 3-voter、5-voter 拓扑分别固定目标档位并运行 `ROUTING_CAPACITY_ENV_CONFIG=<候选环境配置> ROUTING_CAPACITY_TOPOLOGY_ID=<拓扑> ./scripts/verify.sh routing-capacity`；各自确认 30 轮矩阵、真实采样与三次重复完整。未测的拓扑与角色在规格表中标为待测，不套用其他拓扑的结果。
- [ ] 核对 `qualificationStatus`、失败轮、资源 headroom、cleanup 与普通数据不变；只有 `verified + passed` 可支撑机器规格结论，失败或不确定时保持开放。
- [ ] 按 Gateway、ACTIVE/LEARNER/INACTIVE Broker、Message/Receipt 和 im-ops 分角色整理测量；规格表区分本地 1 Broker、测试 3 Broker、生产 3/5 voter 和超过 5 Broker 的拓扑，列明 CPU、内存/JVM、磁盘容量/IOPS、网络、该固定档位的已验证承载界限、目标利用率、扩容触发阈值、至少 30% 余量及原始证据。生产建议负载不得高于三次稳定通过档的 70%；单档通过仅证明该档位，不得称为系统绝对容量上限。更高档位或未测角色/拓扑标为“待测”，不得借用其他结论。
- [ ] 将经验证的规格与“待测”状态同步到根和受影响模块 README、运维文档及本计划证据区；不把本机观察填入生产规格。
- [ ] 由独立 Reviewer 核对部署真实性、原始指标、故障恢复与规格结论；保留 `Revise/Block` finding，不手改语义评分。
- [ ] 执行 `./scripts/verify.sh clean`、`./scripts/verify.sh full`、`./scripts/verify.sh cleanup`、`./scripts/verify.sh quality` 和 `./scripts/verify.sh handoff`；只有全部通过且复评可接受才标记 `passing` 并按 `docs/PLANS.md` 归档。

归档后关闭审计（不可提前勾选）：将本计划原样移至 `docs/exec-plans/completed/` 并更新 `PROGRESS.md`、active/completed 索引后，停止修改 tracked 文件；依次执行 `./scripts/verify.sh clean`、`./scripts/verify.sh full`、`./scripts/verify.sh cleanup`、`./scripts/verify.sh quality`、`./scripts/verify.sh handoff`，并核对容量证据和 Reviewer scope fingerprint 在归档后仍 fresh。独立 Review 只有在 scope 排除执行状态文件且 fingerprint 未变化时才复用，否则重新申请 Review。任一步失败则将计划移回 active、把 C2 从 `passing` 重开为 `active`、修复并重新完成关闭流程，不能保留半归档状态。

## 恢复状态

- 当前任务：C1 `blocked`，WIP=0；等待真实候选环境、权限和环境专用 hook。
- 已完成：通用 driver、固定矩阵、fail-closed Harness 和工具契约测试；Broker Review 的功能与 HA 验证不替代正式测量。
- 阻塞项：尚无真实候选拓扑、机器规格、WebSocket/监控访问和六个环境 hook。
- 下一步：确定候选环境负责人及访问方式，准备仓库外环境配置，再做短时演练。
- 不要修改：容量阈值、矩阵、无关业务数据或独立 Reviewer 分数以绕开失败。

## 回滚与残余风险

- 环境演练或正式测量失败时执行 `restore/cleanup`，保留 journal 供恢复；不清理非本批次资源。
- 本机 smoke 与 Broker Review 通过不证明生产候选容量，未经完整矩阵不得推荐机器规格。
