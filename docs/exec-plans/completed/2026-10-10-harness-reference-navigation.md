# Harness 规则导航与 CI 工具修复计划

## Sprint Contract

- 目标：让 AI 按任务读取 Harness 规则，避免把运行流程、规则登记和历史示例混为同一事实，并修复 GitHub Actions 缺少 `rg` 的环境失败。
- 范围：`docs/references/HARNESS_GUIDE.md`、规则矩阵新入口、`docs/references/index.md`、Harness 反馈台账、Verify/GC workflow 与聚焦 Harness fixture。
- 非目标：不改业务规则、检查阈值、Review-only 状态或生产容量验收；不提交本次改动。
- 验收：原矩阵规则逐条保留且只有一个登记处；入口能按任务定位规则或运行流程；CI 的三个 Harness job 在调用脚本前安装 `ripgrep`；缺工具时报告准确归因；drift、Harness 自测、clean、full 通过。
- 事实基础：[Harness 文档设计](../../design-docs/2026-09-09-domain-readme-and-harness-refinement-design.md)、[Harness Guide](../../references/HARNESS_GUIDE.md)、[HF-001](../../feedback/HARNESS_FEEDBACK.md)。

## 事实源

- 设计或产品规格：[Harness 文档设计](../../design-docs/2026-09-09-domain-readme-and-harness-refinement-design.md)。
- 根 `AGENTS.md`、`ARCHITECTURE.md`、`docs/PLANS.md` 和 `docs/references/HARNESS_GUIDE.md`。
- `.github/workflows/verify.yml`、`.github/workflows/harness-gc.yml` 和 `scripts/test-harness.sh`。

## 验证分层

| 层级 | 命令 | 要求 |
|---|---|---|
| 工具缺失反例 | `PATH=/usr/bin:/bin ./scripts/harness-report.sh` | 返回 2 和明确缺 `rg` 诊断 |
| Harness 聚焦 | `bash scripts/test-harness.sh` | CI 工具断言与规则导航检查通过 |
| 文档与架构 | `./scripts/verify.sh clean`、`./scripts/verify.sh quick` | 链接、规范入口与现有规则不漂移 |
| 完整门禁 | `./scripts/verify.sh full` | 通过；若环境故障，按实际原因保留证据 |

## 任务状态

| ID | 目标 | 状态 | 完成条件 |
|---|---|---|---|
| H1 | 修复 CI 的 Harness 工具前置条件 | passing | 三个 job 安装 `ripgrep`，反例和聚合自测通过 |
| H2 | 分离规则登记与运行入口 | passing | 215 条规则行与原文逐字一致；`clean`、`quick`、`full` 通过，`full` 1683 tests、0 failures，HA 25 项通过 |

## 详细任务

### H1 修复 CI 的 Harness 工具前置条件

文件范围：`.github/workflows/verify.yml`、`.github/workflows/harness-gc.yml`、`scripts/harness-report.sh`、`scripts/test-harness.sh`、`docs/references/HARNESS_GUIDE.md`。

- [x] 在 `scripts/test-harness.sh` 加入 CI 缺 `ripgrep` 的 RED 断言，聚合自测因缺安装步骤退出 1。
- [x] 两个 Verify job 和 GC job 在执行 Harness 前安装 `ripgrep`；`harness-report.sh` 在缺命令时返回明确 WHAT/WHY/FIX，不误报计划状态。
- [x] `bash scripts/test-harness.sh` 退出 0；用受限 PATH 证明 `harness-report.sh` 返回 2 并说明缺 `rg`。

### H2 分离规则登记与运行入口

文件范围：`docs/references/HARNESS_GUIDE.md`、`docs/references/HARNESS_RULE_MATRIX.md`、`docs/references/index.md`、`docs/feedback/HARNESS_FEEDBACK.md`，以及必要的 Harness 文档 fixture。

- [x] 机械迁移完整规则矩阵，215 条规则行与提交前一致，矩阵正文摘要为 `94838b7c8f4b9060c2b645eae56d5db3d86392afad31a6148fae18941ab502aa`；规则文本、所有者、自动检查和 Review 列未改。
- [x] Harness Guide 保留五子系统、验证入口、失败归因、规则生命周期、反馈和清洁状态；references 索引按任务指向规则登记和运行入口，矩阵解释机械覆盖与语义 Review 的区别。
- [x] 用 drift/Harness fixture 验证链接及规则唯一登记；独立 Review 指出的 Coding Guide 导航、CI 安装顺序及报告矩阵传感器问题均已修复，HF-001 按文档退出条件关闭。
- [x] `./scripts/verify.sh clean`、`./scripts/verify.sh quick`、`./scripts/verify.sh full` 均退出 0；最终 full 1683 tests、0 failures、0 errors、2 skipped，HA 25 项通过。

## 恢复状态

- 当前任务：H1/H2 均为 `passing`，WIP=0；归档后关闭审计待核对。
- 已完成：CI 缺 `rg` 的 RED/GREEN、报告准确诊断、规则矩阵迁移、独立文档 Review 与完整门禁。
- 阻塞项：none；生产容量 C1 在独立计划中保持 `blocked`。
- 下一步：归档后核对链接与最终工作树门禁；GitHub runner 安装步骤由下一次 Actions run 实测。
- 不要修改：生产容量计划、业务规则内容、无关工作树变化。

## 回滚与残余风险

- 若文档迁移导致规则丢失或链接断开，恢复规则登记单一入口并重跑 drift；不弱化检查器。
- CI `apt-get` 仍需在 GitHub runner 上复验；本机 fixture 只验证 workflow 契约，不能代替线上 run。
