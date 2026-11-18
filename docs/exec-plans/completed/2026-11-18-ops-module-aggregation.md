# Ops 模块聚合实施计划

## Sprint Contract

- 目标：`im-ops` 聚合 `im-ops-server` 与 `im-ops-cli`。
- 范围：Ops 目录/POM、测试依赖、部署构建路径、Harness 固定模型/路径、当前说明文档。
- 不做事项：不改业务代码、协议、运行服务名、Java 包、数据、凭据与用户 Docker 配置；不提交。
- 验收标准：父子关系正确，CLI 不依赖 Server；固定模型精确 53 项；聚焦回归、clean/full 和独立 Review 通过。
- 风险链路：构建模型、部署 JAR、启动/CLI E2E；由最终 full 的同轮证明与真实 HA 覆盖。
- 依赖输入与所需权限：readiness 已通过；本机 Java/Maven/Node/Docker，现有受控 Harness 环境与网络。

## 事实源

- 设计或产品规格：[批准设计](../../design-docs/2026-11-18-ops-module-aggregation-design.md)，独立 Approved 原件 `/tmp/ops-aggregation-design-review.txt`。
- 根 `ARCHITECTURE.md`、`im-management/AGENTS.md` 与 README、Ops/CLI README、`im-test/AGENTS.md`。
- API、协议或数据契约保持原值。
- 入口：Ops 两个子 POM、`scripts/lib/harness-reactor-evidence.rb`、`scripts/test-reactor-evidence.sh`、`RuntimeDependencyPolicyTest.java`。

## 验证分层

| 层级 | 命令或证据 | 必须/可选 | 当前结果 |
|---|---|---|---|
| 就绪 | `./scripts/verify.sh readiness` | 必须 | 通过 |
| RED | `mvn -q -pl im-test/im-architecture-test -am test '-Dtest=RuntimeDependencyPolicyTest#opsModulesShareCapabilityParent' -Dsurefire.failIfNoSpecifiedTests=false` | 必须 | 预期失败：1 failure、0 errors；原件 `/tmp/ops-aggregation-red.log` |
| 模型 | `bash scripts/test-reactor-evidence.sh` | 必须 | 修订负例后通过；full3 同轮再次通过 |
| 路径/部署/UI | `bash scripts/test-harness.sh`、`bash scripts/test-local-compose-harness.sh`、`bash scripts/test-management-ui-harness.sh` | 必须 | full3 同轮通过；历史启动信号失败保留 |
| 模块/架构 | `mvn -q -pl im-management/im-ops,im-test/im-architecture-test -am test` | 必须 | 首轮 Broker BindException 保留；失败方法窄复验、聚焦 Ops/架构和 full3 完整 reactor 均通过，见详细任务 |
| 完整门禁 | `./scripts/verify.sh clean`、`./scripts/verify.sh full` | 必须 | full3 退出 0，gate/finalization 均 passed、worktreeStable=true；原件 `/tmp/ops-aggregation-acceptance-full3/.harness/verifications/full.json`，真实 HA 25 场景通过 |
| 独立 Review | 只读实现审查及 quality scope | 必须 | 正式 quality 第二轮 Accept，原件 `/tmp/ops-aggregation-acceptance-full3/.harness/quality/`；首轮 Revise 与误报撤回记录保留 |

## 任务状态

| ID | 行为目标 | 范围 | 状态 | 验证证据 | 阻塞/备注 |
|---|---|---|---|---|---|
| T1 | 完整迁移与验收 | 本合同全部文件 | `passing` | full3 完整通过、真实 HA 25 场景、独立 quality Accept | 实现已验收；文档收尾单独记录，不追认原证据 freshness |

## 详细任务

### T1 聚合 Ops Server 与 CLI

输入与边界：依照批准设计，保留全部运行时契约和安全拒绝条件。

文件范围：`im-management/pom.xml`、`im-management/im-ops/**`、原 `im-management/im-ops-cli/**`；`im-test/im-{architecture,e2e}-test/pom.xml`；Architecture 的 `RuntimeDependencyPolicyTest.java`、`ObservabilityConfigurationTest.java`、`ModuleBoundaryTest.java`；`deploy/local/compose.yml`、`app.Dockerfile.dockerignore`；`scripts/{affected-modules,check-drift,check-management-ui,test-harness,test-reactor-evidence,test-local-compose-harness,test-management-ui-harness}.sh`、`scripts/lib/harness-reactor-evidence.rb`；根 README/ARCHITECTURE、management README/AGENTS、`docs/references/{CODING_GUIDE,HARNESS_GUIDE,HARNESS_RULE_MATRIX}.md`、本设计和索引、状态文件。

- [x] 在 RuntimeDependencyPolicyTest 添加父子/packaging/CLI 无 Server 契约，运行 RED 精确命令并确认因旧聚合失败（1 failure、0 errors，`/tmp/ops-aggregation-red.log`）。
- [x] 迁移 Server 源码/UI/README/POM 与 CLI 到新根，新增 Ops 聚合 POM/README；更新测试依赖、部署、精确路径和 affected 路由。原 Server target 留在聚合根，不作为新 Server 验收输入；CLI 既有 target 随目录迁移，正式 full 重建。
- [x] 同步固定 53 项模型与独立 raw/effective/display/body/stdout fixture，保持负例有效；运行 `bash scripts/test-reactor-evidence.sh`。首轮通过后独立 Review Revise 发现两项身份负例仍为 52 项；已改完整清单原位替换并断言 53，第二轮 `/tmp/ops-reactor-fixture2.log` 通过，独立复审 `/tmp/ops-aggregation-fixture-review2.txt` 确认 P2 closed。
- [x] 同步当前文档和规则路径；UI、Compose fixture 通过，父 Harness 在未修改 `test-routing-ha-process.sh` 的 parent INT 等待 cleanup-ready 超时，单独复验同因失败（`/tmp/ops-harness-fixture.log`、`/tmp/ops-ha-process-recheck.log`）。原广 Maven 因 Broker BindException 失败；原失败方法窄复验通过。聚焦 `mvn -q -pl im-management/im-ops,im-test/im-architecture-test -am test '-Dtest=*Ops*,*Cli*,*OperationRequestFile*,*AuthenticationSession*,*Loopback*,*Pkce*,*RuntimeDependencyPolicyTest*,*ObservabilityConfigurationTest*,*ModuleBoundaryTest*,*LayerBoundaryTest*,*CoverageInstrumentationTest*' -Dsurefire.failIfNoSpecifiedTests=false` 通过（`/tmp/ops-focused-green2.log`，Server 124、CLI 182、选定架构 81，零失败/错误）。一次前置同命令因工具 60 秒 timeout 中断，不计通过（`/tmp/ops-focused-green.log`）。
- [x] 冻结完整输入，运行 `./scripts/verify.sh clean`、`./scripts/verify.sh full`；full3 实际退出 0，durationSeconds=2611、worktreeStable=true，真实 HA 25 场景和资源清理通过。原 full2 失败、取消记录及旧产物隔离归因保留。
- [x] 独立实现 Review 与正式 quality Accept；cleanup 扫描和 full 自带 handoff 完成。原归档因稳定设计/index 链接和独立 Reviewer 网络失败延期（`/tmp/ops-closeout-scope-review.log`），此历史保留。用户随后明确要求更新文档，完成设计状态、索引和归档链接同步；这包含稳定文档变更，单独核查，不伪装成纯状态迁移，也不将原 full 晋级为新文档指纹的完整验收。

完成条件：所有必要门禁通过且独立 Accept；未通过保持 active/blocked。

## 恢复状态

- 当前任务：none；T1 `passing`，实现已完成验收，计划已归档。
- 已完成：范围核对、readiness、批准设计和独立设计 Approved；迁移 148 项源码/资源/UI 字节完全相同；UI/Compose fixture 通过，Compose 精确 diff 独立 Approved，clean 第二轮通过（首轮计划字段名缺失已补）。独立实现首轮 Revise 原件 `/tmp/ops-aggregation-code-review.txt` 保留。
- 阻塞项：原 parent INT 超时已归因为本会话使用 Bash 非交互后台 `&` 启动测试，SIGINT 继承为 SIG_IGN，不是 Ops 迁移或进程监督实现缺陷。前台 `python3 subprocess.run(['bash','scripts/test-routing-ha-process.sh'], timeout=240)` 退出 0（`/tmp/ops-process-foreground.log`）；最小探针对比前台 default_int_handler、后台 SIG_IGN。未改任何进程生产或测试代码。旧失败、full 主动取消与 incomplete 保留原义。
- 验收历史：重新冻结后的 full3 已通过（退出 0），使用 Python subprocess 默认信号环境，日志 `/tmp/ops-aggregation-full3.log`、退出 `/tmp/ops-aggregation-full3.exit`。full2 实际退出 1：父 Harness、受控 53 项模型和 Maven 完成，verify 因旧聚合根 target/classes 与新 Server 重复 ImOpsApplication 拒绝，未到真实 HA；worktreeStable=true，原件保留 `/tmp/ops-aggregation-acceptance-full2/`。旧 target 核查无 symlink 后隔离到 `/tmp/ops-aggregation-stale-parent-target`，不删除历史报告；原 verify 精确复验退出 0（`/tmp/ops-post-quarantine-verify.log`），这只是归因验证，不把失败 full 晋级通过。独立正式 quality 第二轮 Accept 已校验接纳（`/tmp/ops-aggregation-quality-response2.json`）；首轮错误链接 finding 已实证撤回，Revise 原件保留。当前无源码/checker 放宽；实现验收完成，不提交。
- 文档收尾：用户要求后更新设计完成状态、设计索引、PROGRESS 与计划目录索引，修正归档链接。变更前快照 `/tmp/ops-doc-closeout-before/`；精确文档 diff、独立复核和廉价检查分别记录于 `/tmp/ops-doc-closeout.diff`、`/tmp/ops-doc-closeout-review.txt` 与 `/tmp/ops-doc-closeout-*.log`，只按实际结果判断，不先记通过。
- 下一步：无实现任务；原 full/HA/quality 验收为归档前实现时点。本次稳定文档变更使原 scope/fingerprint stale，保留该事实；本次不重复 Java/HA，新增文档检查不作为新的完整实现验收。
- 不要修改：归档验收与设计的历史路径/52 项事实；运行配置身份与用户 Docker 状态。

## 回滚与残余风险

- 回滚：反向恢复本次目录/POM/消费者路径；无数据迁移。
- 未验证路径：实现运行验收 none；文档更新后的完整 full 未重跑，原验收不冒充当前 fresh；本次文档检查与独立 Review 以对应日志为准。
- 已接受但需跟踪的风险：none。
