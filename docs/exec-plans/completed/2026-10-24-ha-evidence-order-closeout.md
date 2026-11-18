# HA 证据时序与维护验收收尾

## Sprint Contract

- 目标：修复全新状态目录的 HA 日志生成晚于证据映射，并完成既有两文件维护验收。
- 范围：`scripts/run-routing-ha-scenarios.sh`、`scripts/test-routing-ha-harness.sh`、`docs/references/HARNESS_GUIDE.md`，以及本计划、`PROGRESS.md` 和 active/completed 索引。既有 factory 与 process fixture 保持冻结。
- 不做事项：不改 Java、安全策略、证据校验器、质量排除、IDE 设置；不提交、不部署，不处理容量或生产证书后轨。
- 验收标准：新目录回归先 RED 后 GREEN；日志采集失败不能发布场景，异常路径继续清理并保留原失败；冻结相同源码在隔离工作树完成 clean/full、独立 Review 及当前 quality。
- 风险链路：真实 HA 的日志采集、证据映射和 EXIT cleanup。
- 环境：用户保留 VS Code，使用既有临时 detached worktree；Docker 仅操作本轮 owned 项目。磁盘需实际核对。

## 事实源

- 设计或产品规格：[Routing HA 前检与有界清理](../../design-docs/2026-10-21-routing-ha-bounded-runner-design.md)。本任务修正证据生成顺序，保持既有拓扑、预算及失败关闭契约。
- `ARCHITECTURE.md`、`docs/PLANS.md`、`docs/references/HARNESS_GUIDE.md`、`docs/references/UNIT_TEST_GUIDE.md`。
- 入口：runner 的 write_scenario_evidence、cleanup；harness-reactor-evidence.rb 的 ha_evidence。
- 原失败：`/private/var/folders/h9/drjh32n10x170kpxrbpl_1p00000gn/T/im-chat-isolated-acceptance-m9g5_2y2/RESULT.md`。full exit1、reactor2350全零/2skip；映射前缺 compose.log，EXIT cleanup 后才生成。原记录不覆盖。

## 验证分层

| 层级 | 命令或证据 | 当前结果 |
|---|---|---|
| 就绪 | `./scripts/verify.sh readiness` | 本轮 exit0 |
| RED/GREEN | `bash scripts/test-routing-ha-harness.sh` | RED exit1，GREEN exit0 |
| 语法 | `bash -n scripts/run-routing-ha-scenarios.sh scripts/test-routing-ha-harness.sh`、`git diff --check` | exit0 |
| 集成 | `./scripts/verify.sh clean`、`MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh full` | attempt3 隔离运行均 exit0；原窗口与失败保留 |
| 审查 | 独立 Review、`./scripts/verify.sh quality` | 最终实现及当前正式 quality 均 Accept，quality exit0 |

## 任务状态

| ID | 目标 | 状态 | 证据 |
|---|---|---|---|
| T1 | 回归与最小日志时序修复 | passing | RED/GREEN 与独立 scoped Accept |
| T2 | 隔离完整验收及最终 Review | passing | attempt3 full exit0；最终实现与正式 quality 均 Accept |
| T3 | 纯状态关闭与清理 | passing | 迁移前精确状态 Review Accept；五项廉价检查exit0；归档后另复核实际迁移差异 |

## 详细任务

### T1 回归与最小日志时序修复

精确范围：runner、自测、Harness Guide、本计划与进度/索引。

- [x] 加强现有同轮分支 fixture：新目录 evidence-mapping 必须已有真实 runner 收集的 compose.log；旧 runner 行为 RED。`/tmp/ha-evidence-order-red.log` exit1，明确报 topology logs must be collected before evidence mapping。
- [x] runner 在映射前有界采集日志；采集失败传播，EXIT 原有日志/资源清理不变。
- [x] 同一自测 GREEN，补日志采集失败的反例，运行上述语法和 diff 检查。`/tmp/ha-evidence-order-green.log` exit0；采集失败37、不映射、无场景、down及空ledger断言通过；语法与 diff 检查exit0。
- [x] 更新既有证据约定并独立审查精确修复差异；不新增框架。独立 `ha-order-review.md` findings none、实现 scoped Accept，完整验收 pending。EXIT 复采可覆盖同轮诊断的既有粒度风险保留，不宣称日志不可变。

### T2 隔离完整验收及最终 Review

范围：同一冻结输入的临时工作树与 ignored 验收产物，不改源码。

- [x] 同步 Git 可见输入，核对 HEAD、内容/权限、完整指纹及磁盘/Docker 前提。
- [x] clean 通过后单次 full，保留原 exit、fresh reports、HA、输入稳定性与 owned cleanup。attempt2 因计划缺设计链接 clean exit1，full 未运行；补齐后 attempt3 readiness/clean/full 均exit0。full 15:18:04–15:35:12 UTC，1028秒；496 fresh XML/2350 tests/0 failures/0 errors/2 MySQL条件skip，48摘要一致，真实RH01–25及cleanup通过，起止9c476f6e7c781f07885e1137bd3c18c347e67b43d35dac5744644508cae9feb2一致。
- [x] 实际全通过后完成必要独立 Review 和匹配当前指纹的 quality；Revise/Block 不降级。`final-implementation-review.md` Accept、findings none；`final-quality-response.json` 十二单位 Accept/findings=[]，正式quality合成exit0。证据根与原失败目录相同，attempt3及上述原件保留；不得把原失败轮次重写为通过。

### T3 纯状态关闭与清理

范围仅状态文档与任务 owned 临时输出。

- [x] 完整验收与独立 Accept 后迁移计划，整理 active 索引冲突，精确状态 diff 交独立复核。迁移前 Review 首轮Revise的过期状态已修正并复审Accept；实际迁移差异另交同一Reviewer复核，原记录保留。
- [x] 运行 drift/clean/quality scope/cleanup scan/handoff；不以纯状态变更重跑 full。`state-closeout/` 五项exit0，迁移后的同类廉价检查另存，不刷新原full时间或指纹。
- [x] 保存必要证据，核对无使用者后提出临时 worktree 删除；未知资源不删除。全部证据保留在隔离证据根，worktree 未删除；删除为待用户授权的可选清理，不是验收缺口。

## 恢复状态

- 当前任务：none，实施及迁移前关闭检查已完成。
- 已完成：T1/T2 passing，最终完整验收、独立实现Accept及当前十二单位quality Accept；原两文件保持原冻结 SHA。
- 阻塞项：none；原失败历史保留，不刷新旧运行。当前状态更新使原full指纹stale属于事实，不重跑full。
- 下一步：实际迁移差异独立复核及迁移后廉价检查，结果仅存证据根，不回写本冻结归档；临时worktree保留至证据安置及用户删除授权。
- 不要修改：无关用户改动、原 completed 计划、IDE、证据 checker、Java。

## 回滚与残余风险

- 回滚仅本任务精确差异，其他 mixed worktree 保留。
- 未验证路径：两项既有真实 MySQL 条件 skip、生产证书/secret、性能容量及专用 DEV 网络传输不由本任务证明；本任务修复、真实 HA 和最终质量均已验收。
- 残余风险：磁盘与宿主调度、私有依赖、历史未知根因均不因本任务消失。
