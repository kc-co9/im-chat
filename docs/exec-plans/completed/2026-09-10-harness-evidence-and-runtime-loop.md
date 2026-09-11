# Harness 证据与运行闭环实施计划

> **执行约束：** 按用户要求单任务串行推进，不使用 subagent。除非用户明确要求，否则不创建 Git commit。

## Sprint Contract

- 目标：让验证证据绑定当前代码状态，建立实时链路 E2E、行为目录和可查询运行证据，并在 CI 中执行完整生命周期。
- 范围：verification/report、Harness 文档状态、`im-test/im-architecture-test` 迁移、`im-test/im-e2e-test`、feature catalog、运行日志、影响分析和 GitHub Actions。
- 不做事项：不启动自动 Agent loop/Graph；不以 Docker Compose 引入完整生产依赖；不把实时链路 E2E 描述为包含全部数据基础设施的全栈 E2E。
- 验收标准：旧证据在 worktree 变化后自动 stale；实时黄金旅程使用真实 HTTP/WebSocket/Bolt 通过；feature catalog 可机械校验；E2E 日志可查询；CI 固定 Node 并执行 readiness/clean；最终 full 通过。
- 风险链路：验证证据兼容性、Git worktree 指纹性能、Maven 模块依赖、实时端口生命周期、CI 时长与稳定性。
- 依赖输入与所需权限：Git/JDK/Maven/Node/Ruby；Maven 需要写本地仓库；不需要外部数据库、Redis、Nacos 或网络服务。

## 事实源

- 设计：[Harness 证据与运行闭环设计](../../design-docs/2026-09-10-harness-evidence-and-runtime-loop-design.md)。
- Harness 规范：`docs/references/HARNESS_GUIDE.md`。
- 产品行为：`docs/product-specs/im-realtime-approved-scenarios.md`。
- 架构：根、Gateway 与 Broker `ARCHITECTURE.md`。
- 相关入口：`scripts/verify.sh`、`scripts/harness-report.sh`、`scripts/check-drift.sh`、`.github/workflows/verify.yml`。

## 验证分层

| 层级 | 命令或证据 | 必须/可选 | 当前结果 |
|---|---|---|---|
| Harness RED/GREEN | `./scripts/test-harness.sh` | 必须 | 通过；evidence、catalog、full/CI、嵌套 Shell 正反 fixture 全部 GREEN |
| E2E RED/GREEN | `./scripts/verify.sh e2e` | 必须 | 通过，20 秒，1 个黄金旅程 |
| 清洁状态 | `./scripts/verify.sh clean` | 必须 | 通过，10 秒 |
| 快速门禁 | `./scripts/verify.sh quick` | 必须 | 通过，178 秒 |
| 完整门禁 | `./scripts/verify.sh full` | 必须 | 通过，301 秒；1003 个测试零失败 |
| 文档与报告 | 临时 index drift、JSON 解析、`git diff --check` | 必须 | 74 个变更文件链接/drift、JSON 和 diff 检查通过 |

## 任务状态

| ID | 行为目标 | 范围 | 状态 | 验证证据 | 阻塞/备注 |
|---|---|---|---|---|---|
| T1 | 让 verification 与 report 绑定当前 revision/worktree | scripts/fixtures | `passing` | verification 字段、stale/unavailable、子系统 freshness 与 sensorCoverage 均完成 RED/GREEN；真实旧 full 已判 stale | - |
| T2 | 修正文档中的能力状态 | Harness/README/AGENTS | `passing` | Harness Guide 已将 readiness、E2E、observability 和 clean 改为真实部分/未应用状态；drift 通过 | - |
| T3 | 聚合仓库级测试并建立真实网络实时黄金旅程 | im-test/root architecture | `passing` | Architecture Test 新路径通过；E2E 首次发现 management 401，修正内部只读路径后认证/发送/ACK/断开/重启旅程通过 | Account/Message 数据基础设施仍为明确边界替身 |
| T4 | 建立关键行为 feature catalog | product-specs/drift | `passing` | 重复 ID、空验证入口、非法状态 RED/GREEN；9 个关键行为已登记并通过 Harness fixture | - |
| T5 | 保存并报告 E2E 运行证据 | verify/report/reliability | `passing` | fixture 与真实 `verify.sh e2e` 通过；24 秒、1 个 workload、日志路径和 report runtimeEvidence 均 fresh/verified | - |
| T6 | 收紧 full、影响分析和 CI 生命周期 | verify/affected/GitHub Actions | `passing` | full 顺序、SDK/Server/Bolt E2E 映射、两个 CI job Node/readiness/clean、专用 E2E 与 artifact 均完成 RED/GREEN fixture | - |
| T7 | 完成整体 Review、完整验证和归档 | 全部范围 | `passing` | 首轮 final full 通过；Review 后补充缺 full evidence 的 incomplete RED/GREEN，并移除 PROGRESS 机器结果缓存 | - |

## 恢复状态

- 当前任务：`none`，全部任务已经完成。
- 已完成：T1/T2；`im-test/im-architecture-test` 迁移通过；实时 E2E 已完成 WebSocket、HTTP、Bolt 和 Broker runtime 的 RED/GREEN，并修复 management 401。
- 阻塞项：`none`。
- 下一步：本计划已经归档；机器结果仅写入 ignored Harness report，后续全栈 E2E 使用独立设计与环境契约。
- 不要修改：业务领域行为、生产消息存储实现、自动 Agent loop/Graph。

## 回滚与残余风险

- 回滚：各任务保持独立能力边界，可分别移除 evidence helper、恢复根 `im-architecture` 路径、移除 E2E module、feature catalog checker、运行日志或 CI 步骤。
- 未验证路径：生产 MySQL/Redis/Nacos/Dubbo 全栈黑盒环境不在本轮范围。
- 已接受但需跟踪的风险：实时 E2E 的边界替身证明协议与路由装配，不证明生产远端服务及数据基础设施可用；全栈 E2E 仍需后续独立环境计划。
