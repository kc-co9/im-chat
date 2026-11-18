# Broker 本地路由 HA 模拟验收计划

## Sprint Contract

- 目标：用本地 Docker 完成 Broker/Gateway 上下线、故障恢复和路由状态收敛模拟验证。
- 范围：`scripts/routing_capacity.sh`、`scripts/run-routing-capacity-scenarios.sh`、`im-test/im-load-test/tools/routing-capacity-driver.mjs`、环境专用 hook、候选部署、监控、容量报告与 Reviewer 复评。
- 非目标：不以本机 Compose、短时演练或模拟指标替代正式容量证据；不放宽既有 30 轮矩阵、阈值或清理条件；不提交凭据。
- 验收：完成 3-voter/2-Gateway 的启动、25 个 HA 故障恢复场景和清理；生产容量测量不在本次范围内。
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
| C1 | 本地 HA 模拟 | passing | 本地 HA 演练、故障恢复和清理通过 |
| C2 | 正式容量测量与复评 | passing | 按用户决定从本次范围移除，不作为本地模拟完成条件 |

## 详细任务

### C1 本地 HA 模拟

文件范围：仓库外的候选环境配置与六个 hook；若通用契约需修正，限定为 `im-test/im-load-test/tools/routing-capacity-driver.mjs`、其测试、`scripts/routing_capacity.sh`、`scripts/run-routing-capacity-scenarios.sh` 及所属文档。

- [x] 使用本地 Docker 3-voter/2-Gateway 拓扑完成启动、25 个故障恢复场景和清理；结果仅作为本地模拟证据。
- [x] 本地场景覆盖 Gateway 下线/恢复、三个 Broker 分别下线/恢复，以及多数派状态收敛；证据见 `.harness/routing-ha/scenarios.jsonl` 和 `.harness/verifications/routing-ha.json`。
- [x] 生产候选环境与正式容量测量按用户决定移出本次范围；本地结果不声明生产容量。
- [x] 本地 `routing-ha` 已覆盖 Gateway 下线/恢复、三个 Broker 分别下线/恢复、多数派收敛和清理。
- [x] 未修改通用 driver；已运行 `./scripts/test-routing-capacity-harness.sh`，并完成 drift 验证。

### C2 正式容量测量与复评（本次范围外）

文件范围：仓库外环境配置与 hook、`.harness/routing-capacity/` 运行证据、根 `README.md` 与受影响模块 README、运维文档、候选规格说明、`docs/references/HARNESS_GUIDE.md` 与本计划；仅缺陷需要时修改通用 driver 与测试。

- [x] 用户决定不执行正式生产容量测量，本项不纳入本次交付。
- [x] 生产 qualification、机器规格、独立复评和正式容量门禁均因用户决定不纳入本次交付而不执行。

归档后关闭审计（不可提前勾选）：将本计划原样移至 `docs/exec-plans/completed/` 并更新 `PROGRESS.md`、active/completed 索引后，停止修改 tracked 文件；依次执行 `./scripts/verify.sh clean`、`./scripts/verify.sh full`、`./scripts/verify.sh cleanup`、`./scripts/verify.sh quality`、`./scripts/verify.sh handoff`，并核对容量证据和 Reviewer scope fingerprint 在归档后仍 fresh。独立 Review 只有在 scope 排除执行状态文件且 fingerprint 未变化时才复用，否则重新申请 Review。任一步失败则将计划移回 active、把 C2 从 `passing` 重开为 `active`、修复并重新完成关闭流程，不能保留半归档状态。

## 恢复状态

- 当前任务：本地模拟已完成，WIP=0；正式生产容量不在本次范围内。
- 已完成：通用 driver、固定矩阵、fail-closed Harness、工具契约测试，以及本地 Docker routing-ha 25 场景演练；本地 HA 不替代正式测量。
- 阻塞项：none（正式生产容量不在本次范围内）。
- 下一步：归档本地模拟计划。
- 不要修改：容量阈值、矩阵、无关业务数据或独立 Reviewer 分数以绕开失败。

## 回滚与残余风险

- 环境演练或正式测量失败时执行 `restore/cleanup`，保留 journal 供恢复；不清理非本批次资源。
- 本机 smoke 与 Broker Review 通过不证明生产候选容量，未经完整矩阵不得推荐机器规格。
