# Harness 验证输入隔离与阶段恢复实施计划

> 处置状态：原平台方案已由用户批准的[精简方案](../../design-docs/2026-10-22-closeout-process-simplification-implementation-design.md)取代，阶段链/journal/复杂归档功能已撤下并延期，战术修复保留。不是当前实施计划，不迁入completed、不将H5补勾passing。以下H1–H4 passing仅为原方案历史检查点，不代表当前仍交付这些平台能力；经用户确认从active移至history保存撤回记录，不产生新的A5前置；下文原路径及恢复指令均保留当时语境，不是现行执行入口。

## Sprint Contract

- 目标：按[已批准设计](../../design-docs/2026-10-21-harness-verification-resume-design.md)隔离归档状态输入，支持显式线性阶段执行/恢复，减少重复编译和HA。
- 范围：Harness脚本、确定性fixture、规范与状态文档；不改业务Java、依赖版本、镜像、断言或900秒HA期限。
- 非目标：affected-module推导、自动重试、跨HEAD/跨机器复用、通用DAG和依赖解析平台。
- 验收：状态排除与运行冻结反例通过；阶段证据fail-closed；report/handoff一致；旧失败不覆盖；治理当前执行。实现独立Review及所需最终机器证据齐全才关闭。
- 环境：Shell/Ruby/Node/Git/Maven；实际集成继续使用隔离repo `/tmp/jraft-artifact-m2`。readiness已通过 `/tmp/harness-resume-readiness.log`。不修改全局网络或清理非本任务数据。

## 事实源

- 设计或产品规格：[归档差异审计设计](../../design-docs/2026-10-21-harness-archive-audit-design.md)：用户批准缩减范围，独立reviewer67bfb224第二轮Approved。原[阶段恢复设计](../../design-docs/2026-10-21-harness-verification-resume-design.md)保留H1/H2事实；通用阶段缓存明确延期。
- [根架构](../../../ARCHITECTURE.md)、[计划政策](../../PLANS.md)、[Harness Guide](../../references/HARNESS_GUIDE.md)。现行代码为事实，不把新设计写成已实现。
- `scripts/verify.sh`、`scripts/lib/harness-evidence.sh`、`scripts/harness-report.sh`、`scripts/harness-handoff.sh`、`scripts/routing_ha.sh`。

## 验证分层

先纯fixture RED/GREEN和bash-n/ruby语法，再父Harness、drift/quick/clean。仅触及运行证据边界后才执行一次真实HA；最终集成按新阶段分开调用，足够外层预算，不缩短场景自身期限。未完成实现前不得用设计提前豁免现行规则。新实现不能自动晋级A5历史失败；A5另行明确验收。

## 任务状态

| ID | 任务 | 状态 | 验收 |
|---|---|---|---|
| H1 | 版本化实现输入指纹与反例 | passing | 状态变化不影响scope、真实输入必失效、运行冻结不弱化 |
| H2 | 固定阶段记录、停止边界与互斥 | passing | 新链/attempt、不可变失败、部分非full通过、并发拒绝 |
| H3 | 归档成功基线与失败留存 | passing | 固定必需成功、阶段证据副本、后续失败不可抹去 |
| H4 | 当前状态审计与独立消费者展示 | passing | 原full不改fresh、当前治理、完成指针一致 |
| H5 | 规范同步、Review和最终集成 | blocked | fresh机器证据与独立审查、限定范围 |

## 详细任务

### H1 版本化实现输入指纹与反例

文件：`scripts/lib/harness-evidence.sh`；新增 `scripts/test-harness-verification-evidence.sh`（独立真实Git fixture）；`scripts/test-harness.sh`仅登记自测入口。不修改verify/report freshness。

- [x] 写RED：新增 `harness_verification_input_fingerprint` 缺失时失败；fixture证明PROGRESS/active/completed包括索引迁移只改变完整scope；history/design/README源码脚本依赖/executable/symlink/HEAD变化必须失效；staged状态不改内容scope、Git失败返回unavailable。
- [x] `bash scripts/test-harness-verification-evidence.sh > /tmp/harness-input-red.log 2>&1`，记录真实非零。
- [x] 最小实现与已有内容指纹共享编码，不增加任意排除参数；仅精确状态路径排除，保留HEAD及来源冻结规则，函数注释中文。
- [x] `bash -n scripts/lib/harness-evidence.sh scripts/test-harness-verification-evidence.sh`；新fixture GREEN、父Harness集成验证。源码/路径规则变异须稳定失败。
- [x] 独立限定Review，记录命令证据再标passing。

### H2 固定阶段记录、停止边界与互斥

文件：`scripts/verify.sh`、新增 `scripts/lib/harness-verification-phases.sh`（阶段记录和验证单一职责）、新增 `scripts/test-harness-verification-phases.sh`（固定阶段stub fixture，与H1纯Git输入测试分离）、`scripts/test-harness.sh`，以及 `scripts/test-load-test-harness.sh`、`scripts/test-routing-capacity-harness.sh` 的fixture依赖复制清单；不引入通用runner。H2续跑暂时重新实际执行全部所需阶段，H3才允许满足证明条件的成功复用，不把H2宣传成已节省实际阶段。

- [x] RED固定 `full --through scripts|routing-ha|maven`、`--resume <chain-id>` 参数；旧无参数完整执行；部分成功不是full passed；非法chain/路径/version/type/字段拒绝。
- [x] 实现链ID/attempt、仓库执行互斥（独立构建入口也协调）、链记录锁、原子记录、不可变失败；仓库运行期间仍完整指纹冻结。
- [x] fixture两链并发、强制中断、原始退出码、后处理失败、旧记录不晋级；`bash scripts/test-harness-verification-evidence.sh` GREEN。
- [x] 父级 `bash scripts/test-harness.sh` 集成通过后独立Review；不足之处停在本层修正。

### H3 归档成功基线与失败留存

文件：`scripts/verify.sh`、新增 `scripts/lib/harness-archive-audit.rb`（固定schema/复制清单/基线资格与运行留存，单用途Ruby标准库）、新增 `scripts/test-harness-archive-audit.sh`、`scripts/test-harness.sh`及复制verify的load/capacity fixture清单。不改phase复用逻辑，所有阶段reuseEligible=false。

- [x] RED固定archive-audit prepare入口、严格必需模式成功/稳定/同HEAD及完整指纹、qualityAccept/full实际阶段完整；partial/failed/stale/unknown拒绝。
- [x] 实现prepare基线、原JSON按字节保留＋副本路径清单/摘要、安全ID/无symlink/仓库锁；原attempt产物清理后仍能验证副本。不直接写任何full通过。
- [x] 必需入口原子启动/结束记录，失败→成功和启动→取消仍不能覆盖prepare后的失败事实；只记录非秘密元数据/摘要。
- [x] `bash scripts/test-harness-archive-audit.sh` RED/GREEN、H1/H2回归与父Harness；独立Review，H4 verify入口未实现时明确不宣称归档审计已完成。原H3消费闭包/通用阶段缓存已批准延期，不批量勾绿旧步骤。

### H4 当前状态审计与独立消费者展示

文件：`scripts/harness-report.sh`、`scripts/harness-handoff.sh`、`scripts/verify.sh`、`scripts/lib/harness-archive-audit.rb`、`scripts/test-harness-archive-audit.sh`、`scripts/test-harness.sh`、`scripts/test-handoff-harness.sh`。

- [x] RED verify指定基线后仅状态差异允许，当前readiness/drift/clean/quality/cleanup真实执行；源码/HEAD/普通文档变化、后续失败/未完成必需运行、基线篡改均阻断。
- [x] 原门禁freshness/时间/分数不变，另列archive-state-transition；临时生成候选结果及report/handoff，最后发布完成指针，消费者ID/摘要一致才可关闭，partial/失败不回退旧成功。
- [x] fixture实现迁移前后Java/容器trace不增加；后处理失败/发布中断不可关闭；成功后两消费者都展示passed，旧full仍stale。
- [x] 聚焦fixture/父Harness通过且独立Review，不改自动分数或将A5标passing。

### H5 规范同步、Review和最终集成

本次批准的故障修复事实源：[Routing HA前检与有界清理](../../design-docs/2026-10-21-routing-ha-bounded-runner-design.md)，独立review87a86911第二轮Approved。精确附加scope：`scripts/run-routing-ha-scenarios.sh`、`scripts/routing_ha.sh`、新增 `scripts/lib/routing-ha-process.rb`、新增 `scripts/test-routing-ha-process.sh`、`scripts/test-routing-ha-harness.sh`、`scripts/test-harness.sh`（登记fixture），并更新HarnessGuide/矩阵。Ruby helper仅此runner使用，不建立通用任务框架。

- [x] 真实RED：Engine阻塞时构建调用0；steps起止能区分E2E返回与Compose启动；logs阻塞仍down，失败ledger保留，25场景无弱化。
- [x] 实现单调有界helper、显式PID/PGID所有权/TERM→KILL/回收；预检8秒、logs5/down30、主900+仅清理45秒，配置校验不允许不足宽限；cleanup一次且保留deadline124/signal130143原码。
- [x] `bash scripts/test-routing-ha-process.sh` 和 `bash scripts/test-routing-ha-harness.sh` 通过，握手注入忽略TERM/孙进程/外部取消/重复信号，未知存活保留登记；父Harness集成与独立限定Review。
- [x] 先Engine只读检查再单独HA，不先full；失败停在steps指出边界。

文件：`docs/PLANS.md`、`docs/references/HARNESS_GUIDE.md`、`docs/references/HARNESS_RULE_MATRIX.md`、`README.md`、根 `AGENTS.md`（仅当现行无条件关闭命令与批准新契约冲突，保持英文）；设计/index、本计划/PROGRESS。不新增全仓泛化例外。

- [x] 同步scope/version/命令/输入失败关闭/恢复所有权、规则传感器与移除条件；执行状态文件不复制机器分数。
- [ ] 独立实现Review，先完成全部文档再冻结，`./scripts/verify.sh quick`、`./scripts/verify.sh clean`、阶段化full与真实HA、quality、cleanup/handoff按有界窗口运行。记录确切结果和跳过/复用依据，不自动重试失败。
- [ ] 所有任务passing才归档，按新批准契约做状态治理审计并校验有效实现证据；失败恢复active。A5单独处理，不混作本计划通过。

## 恢复状态

**最终处置入口（覆盖以下历史）**：本计划已撤回平台交付范围并被10-22精简方案取代，H5保持blocked历史状态，active任务0。原H1–H4实现已选择性留样/撤下，不能以其历史passing解释当前能力。用户明确批准延期，运行证据及原Review不删；本文件暂留原路径，仅作撤回记录，不归档completed或扩充任务状态枚举。当前A5/精简方案的独立验收与状态关闭由各自计划拥有，不再与本计划互为无限前置。

**当前唯一恢复入口（2026-10-22）**：用户认为流程本身需要优化，已完成只读范围/重复覆盖审计，未执行任何构建或验证。提案见[收尾收口与去重](../../design-docs/2026-10-22-closeout-process-simplification-design.md)。推荐保留cache/ps采样/HA前检与安全清理战术修复；延期通用chain/attempt/journal/archive状态机，当前resume无成功跳阶段收益。该延期需用户确认，不擅自回撤混合工作树。验收目标为同冻结输入自测一次＋reactor一次＋真实HA一次，必须保留startup映射/tag非零/运行与清理证明，等价消费未实现前不降低现行要求。H5blocked/active任务0；下一步用户选择后制定准确功能闭包和验证清单，不再试跑full。**以下全部恢复段落均为历史证据；其中“优先/最新”仅属当时措辞，不是并行指令。**

**最新窄层恢复（优先）**：540f冻结quick/clean/readiness/architecture/startup/behavior/e2e均passed，单独full `/tmp/cache-fix-final-full.log`817秒exit1/稳定，在scripts阶段 `unknown reap exceeded budget`，未执行HA/Maven，不是cache回归。精确问题为fixture从Ruby spawn前计时要求<1.2秒，将解释器启动/调度纳入helper0.6秒期限。只改 `scripts/test-routing-ha-process.sh`：真实unreaped保留124/未决登记/WNOHANG断言，外层8秒仅防挂；以隔离加载真实finish_owned/reap＋虚拟时钟精确证明deadline0.6内TERM/KILL，+.2越界和阻塞wait突变精确RED `/tmp/ha-reap-budget-red.log`，GREEN `/tmp/ha-reap-budget-green.log`。设计/实现reviewbd31474a scopedAccept；父Harness exit0 `/tmp/ha-reap-budget-parent.log`，生产helper/期限不改，无本轮full。H5blocked/active任务0。下一步先确认最终fixture契约无主机启动精度断言残余，再只安排必要验收，不把新full当排障。正式quality待当前输入刷新，旧Accept不能冒称fresh；无prepare或归档，历史失败保留。

**缓存修正后冻结批次（优先）**：用户继续收尾，非状态直链迁移阻塞0、incomplete journal0。仅清已批准ignored可再生成10个execjar约1.35GiB，记录 `/tmp/cache-fix-final-ignored-jars-cleanup.json`，可用约7GiB（低于建议10GiB、保留波动风险），不删除其他任务/容器数据。H5active/WIP=1，最后输入仅缓存测试修正，无生产改动；执行quick/clean、readiness/architecture/startup/behavior/e2e，独立quality与单独full（外层3600、HA900不变），日志 `/tmp/cache-fix-final-*`。任一失败停止、不自动循环，不改容量/超时/镜像。成功后cleanup/quality/prepare，集中状态内checkbox/两索引/PROGRESS迁移，以archive-audit关闭，不再编辑任何非状态输入。不把原偶发Raft或Docker内部问题标已修根因；A5独立按原验收核对。

**Cache测试窄修完成（优先）**：只改 `im-plugin/im-cache/src/test/java/com/co/kc/imchat/plugin/cache/core/JetCacheLocalValuesTest.java`，原快照更新/不可变/负TTL保留并finallyclose；容量独立真实JetCache2/TTL60/100writes，先非空，再2秒内同一snapshot同时nonempty且<=2，不重放写入/不catch。设计及实现reviewdc2eed2a scopedAccept。容量临时IntegerMAX反证实际Mavenexit1（100项within2秒） `/tmp/cache-capacity-contract-red.log`，恢复2类两项exit0 `/tmp/cache-capacity-contract-green.log`，im-cache模块8项/0fail/error/skippedexit0 `/tmp/cache-module-green.log`，diffcheck通过；生产/POM/依赖不改。H5blocked/active任务0，本轮未full。下一步集中最后输入并刷新受此单测试修正影响的门禁与独立quality，再完整验收；不得把局部通过等同整项关闭。既有b9d1full自测/HA通过仍历史，Maven失败不抹除；极端资源竞争下2秒收敛风险保留，不进一步扩预算。

**Cache根因证据（优先）**：本轮只/tmp独立Java诊断，不改仓库、不Maven/full。实际JetCache2.7.8/Caffeine2.9.3下原顺序复现cleanup后keys>2、getAll同大小；原生Caffeine最大2＋variableExpiry无JetCache也复现，超限在批量读取前。可控队列维护实验稳定100put→一次cleanup size4/tasks1→运行已排队维护后size2/tasks0 `/tmp/cache-maintenance-controlled.log`，同步executor限定300轮无超限作单变量补证。根因是测试将一次cleanup当维护全部完成后严格限容屏障，当前依赖不提供这个即时保证；不归因固定writebuffer批次太小。报告 `/tmp/cache-capacity-rootcause.md`，源码程序/日志全部/tmp，未改缓存实现/依赖/容量或断言。下一步最小测试设计Review：原JetCache快照更新/不可变/过期过滤保留，容量独立要求维护完成后<=2，并反证移除容量限制失败；不sleep/无界retry，不新增生产时源/executor。H5blocked，active任务0，未再次full。

**最新真实失败入口（优先）**：握手修正后父Harness成功，全Markdown模拟归档仅状态引用/非状态阻塞0，冻结b9d1c268c42b下quick765/clean18/readiness2/architecture157/startup450/behavior51/e2e39均passed稳定。单独full `/tmp/harness-final-locked-full.log`2814秒exit1/稳定，chain20261021T124217Z-98803-26873 attempt...31844：scripts passed、routing-ha passed、Maven failed；本次是im-cache JetCacheLocalValuesTest:28容量2快照实际4（value87/90/92/91），44项/1failure/0error，非DockerHA/外层期限。已停止未重试full，未改cache源码/测试/依赖或放宽断言。只读核对JetCacheFactory已有cleanUp，Caffeine2.9.3字节码持锁maintenance/最低writebuffer128，100put不足以支持“固定批次太小”解释，不能笼统归因异步淘汰。下一步仅独立诊断分界native.cleanup后keys→keysetcopy→JetCache.getAll及队列/weightedSize，不额外cleanup改变现场，比较可控维护Executor作为单变量；取得反证再决定修正。H5blocked/active任务0，formal新请求仍未完成更新，不伪造Accept或新baseline；A5不归档。本轮真实HA通过可保留，但不能覆盖后续Maven失败。

**收尾唯一当前入口（覆盖以下历史）**：握手修正后父Harness exit0 `/tmp/harness-handshake-parent-final.log`；全Git-visible Markdown模拟两计划迁移检查 `/tmp/harness-final-migration-preflight.json`，仅PROGRESS与active索引尚有直接引用（两者是状态输入），非状态断链阻塞0。可用约11GiB，diffcheck通过。H5active/WIP=1，固定最后输入，执行quick/clean、风险门禁、独立quality与full，日志 `/tmp/harness-final-locked-*`；成功后cleanup/quality/prepare。A5是否passing须独立核对其原checklist和Review关闭事实，不能因Harness实现完而自动晋级；满足则状态路径内集中回填两计划与索引/PROGRESS再归档，用同一基线archive-audit verify，不再改设计/README/脚本导致重跑。失败原样停止不自动循环，不weakenscope或伪造fresh。当前所有原基线仍保留，但不能覆盖握手fix的新输入。

**最新恢复入口（优先）**：b30完整验收成功1322秒2041项/0fail/error/2skip，独立formal4642a175Accept已原样存 `.harness/quality/reviewer-response.json`，基线archive-20261021T113350-934273e75fc72abb成功。归档前迁移检查遗漏3非状态文档直链，父修稳定索引使旧基线对新输入失效（没有偷偷排除或晋级旧full），限定review0c85b971Accept；6651冻结quick/clean/readiness/architecture/startup/behavior/e2e全部passed。唯一恢复full `/tmp/harness-nav-final-full.log`589秒exit1，在scripts阶段失败，未进入HA/Maven；精确定位test-routing-ha-process publication异常fixture固定sleep0.2早于child ready，后续wait_file Timeout，非生产HA故障。仅fixture改握手PUBLISH_READY（5秒）、异常路径helper无命令deadline/外层8秒，原child退出断言保留，生产期限不变；聚焦exit0 `/tmp/ha-publication-handshake-green.log`，review9d272379scopedAccept。H5blocked/active任务0，未再次full。下一步先父Harness集成确认该握手fix，再集中最终状态后一次有界验收；formalquality因共享doc/test变化须刷新，旧Accept不当当前fresh。所有旧成功/失败原件留存，不归档、不提交Git。

**导航最后修正（优先）**：b30冻结全套门禁均通过，full1322秒2041项无failure/error，正式review4642a175 Accept，prepare已生成archive-20261021T113350-934273e75fc72abb。迁移前发现3份非状态文档直链active路径遗漏（新设计/header、design index、10-20历史入口），改稳定activeREADME索引；此变化真实使旧基线不可用于新输入，不改指纹规则、不晋级旧full。修后不再改其他非状态输入，重新冻结一次必要验收+增量独立Review，再新prepare及状态归档；基线/失败原件保留。日志 `/tmp/harness-nav-final-*`，避免把旧完整通过冒称新输入通过。归档前将两计划状态/checkbox和两索引/PROGRESS集中回填，仅状态路径允许变动；归档后不再编写新结果进tracked。

**最后冻结批次（优先）**：2026-10-21用户要求继续收尾。可用约15GiB，未完成journal0；单独HA已实际通过，无新功能工作。此处集中状态后冻结：先quick/clean，再readiness/architecture/startup/behavior/e2e、独立quality请求，单独full外层3600（主900不改），失败停止；成功后cleanup及独立quality复核，再archive-audit prepare固定完整基线。日志 `/tmp/harness-close-final-*`。全部实施任务passing后原样迁移Harness计划，索引/PROGRESS只状态变化，archive-audit verify完成后不编辑tracked文件。A5不自动晋级；若要同时关闭必须先核对其checklist和当前独立Review，不能仅因Harness通过冒称业务根因已修。旧formalRevise与失败日志保留，未完成完整验收前不prepare。

**真实HA恢复证据（优先）**：Engine dockerinfo8秒上限内返回27.4.0后仅执行一次 `./scripts/verify.sh routing-ha` exit0，436秒、25场景、cleanupStatus=passed、worktreeStable=true，日志 `/tmp/ha-bounded-real-once.log`。steps精确记录：engine-info0.21秒、package16.65、focused-routing-ha52.74、focused-e2e33.17、compose-up149.52、cleanup-logs0.34、cleanup-down11.02，全部原exit0/groupGone=true；本次ledger0字节。Docker恢复及runner修正获得实际运行证据，不宣称历史内部唤醒故障锁根因或旧Maven/Compose停点已证明。H5active/WIP=1，下步只集中最终文档/状态，复审后冻结完整验收；本次不是full，不prepare/归档A5，旧失败保留。后续禁止为补日志再自动跑HA/full；新完整验收一次按计划安排，归档用archive-audit不重复实现验证。

**有界runner窄层完成（优先）**：Engine前检/步骤JSONL/有界logs-down及900+仅清理45已实施；review7fc7bdea先Revise三项有界wait/原子登记/旧PGID所有权，修后发现正常撤销race，确定性RED `/tmp/ha-process-retire-red.log`、GREEN `/tmp/ha-process-retire-green.log` 后剩余findings全部closed scopedAccept。父首失败归档时间精度不同（collector整秒vs审计微秒），生产比较统一为finish.to_i>=startedEpoch>=audit.floor，移除sleep1绕过；RED `/tmp/archive-time-precision-red.log`、GREEN `/tmp/archive-time-precision-green.log`，reviewac3bb1ee Accept。最终父Harness exit0 `/tmp/ha-bounded-parent-verified.log`，diffcheck通过。H5active，仅恢复一个真实routing-ha `/tmp/ha-bounded-real-once.log`（外层1500、主900不改），先Engine只读响应检查；任何失败停止按steps精确命令分析，不full。尚无当前完整验收/prepare基线，A5仍blocked。

**Engine恢复检查点（优先）**：用户授权强制结束残留DockerDesktop进程并重启应用；官方restart90秒停止失败留证后，仅精确SIGKILL8个应用/辅助进程再openDocker，未删容器/卷/镜像/配置或改代理。Engine _ping HTTP200/OK，version0.11秒、ps0.03秒恢复；本次项目im-chat-routing-ha-47627按label查询容器/网络/卷均0。16容器/74卷/6网络inspect元数据私有保存在 `/tmp/ha-engine-recovered-state/`（0700/0600），原failedledger不清空，当前无资源不等于原HA通过。详细 `/tmp/ha-engine-recovery-summary.md`。H5/A5继续blocked，未跑HA/full。下一步只针对已定位runner的Engine前检/命令边界/清理无预算问题，先设计与stub反例，防止再付Java构建成本后才等待失去响应的Engine；不延长900秒。不宣称Docker内部死锁具体原因或历史Maven→Compose精确停点已证明。

**根因调查入口（优先）**：用户指出反复最后一步卡住、效率低，已停止所有验收重跑。只读报告 `/tmp/ha-rootcause-findings.md`：当前Docker Engine响应异常可独立复现（docker version/ps各8秒超时，Compose插件version0.16秒；两个Unixsocket连接成功但_ping各4秒无响应），VM日志01:21Z正常停止后无后续启动，backend仍活；先前idle5分钟触发shutdown。此状态比“镜像网络慢/机器慢”更具体，但不能证明Docker内部唤醒故障锁根因。HA历史E2E报告完成不等于Maven退出，Maven→compose边界未记录，不能猜精确阻塞命令。脚本放大因素已确认：Engine无有界前检，先付Java测试成本；步骤缺begin/end；cleanup先logs再down均无期限，父超时0.2秒KILL使卡logs时清理无证明。compose.log0字节无cleanup.log，ledger登记不证明容器创建/清理。没有重启、删除Docker资源或改配置；下一步需授权恢复Engine后先有界_ping/只读资源核对，不再full。任何脚本修正先设计/反例，不能延长900秒或把历史其他故障统一归因Docker。

**真实验收阻塞（优先）**：H5blocked/active任务0。H5-1限定复审1cfcb7aaAccept，原formalRevise保留，不改分数。修正后冻结fcdc44b14a21：quick822秒/clean23/readiness2/architecture114/startup224/behavior28/e2e36全部passed稳定，日志 `/tmp/harness-h5-frozen-*`。独立一次full外层3600秒，实际1643秒exit124，worktreeStable=true；chain `chain-20261021T084950Z-91708-2846` attempt `attempt-20261021T084950Z-91708-25388`。scripts阶段0/passed，HA started1792573330/finished1792574231约901秒、原退出124，明确触及阶段自身900秒预算，不是外层取消；artifactcollection1因未完成证明，原始124优先保留；Maven阶段未启动，后处理passed不覆盖gatefailed。HA日志末canceled，本次ledger仍登记compose-project `im-chat-routing-ha-47627`，资源清理未证明。磁盘17GiB，不是磁盘首阻塞。禁止重试full/延长超时；下一步只读核对本次HA日志/Compose状态及超时位置，先处理归属资源与阶段诊断，旧A5来源未确认的124不能简单回写成同因。本实现尚无可prepare完整成功基线，H5-2正式证据缺口未关闭；不归档A5/Harness计划。

**最终质量复核入口（优先）**：formal reviewer1cfcb7aa首轮Revise：H5-1为合法gate0/postprocess失败attempt被validator误拒绝；H5-2为首次完整验收未完成。H5-1仅改validator接受finalizationfailed+正整数report/handoff失败码，原gate退出码保留；生产verify(stub工具)往返反例RED `/tmp/archive-finalizer-contract-red.log`、GREEN `/tmp/archive-finalizer-contract-green.log`，后续新成功验收可prepare/verify，旧基线仍阻断新失败，原历史字节不变。原formalRevise原文 `/tmp/harness-h5-independent-revise.json` 不改分数。代码变化后此前7aae指纹门禁仅历史，冻结本次输入，按父Harness/quick/clean、必需风险门禁、单独full（外层3600秒）顺序；真实失败停止不重试。之后独立Reviewer按当前请求/机器JSON复核formalquality，再prepare/归档状态审计；期间不编辑tracked文件。A5仍blocked，不由本计划自动晋级。所有最终日志 `/tmp/harness-h5-frozen-*`。

**H5当前窗口（优先）**：规范同步5文件（AGENTS英文、PLANS、HarnessGuide/矩阵、README），docreview8eab80e4 Accept。最终代码review85fe3f93首Revise为初始化伪journal＋真实中断永久阻塞新prepare；修正journal在锁/CLI初始化后dispatch前，owner PID已退出且同模式严格后继当前稳定成功可在新baseline显式supersede历史incomplete，原始证据不动、旧baseline仍阻断后续失败。RED `/tmp/archive-journal-lifecycle-red.log`，GREEN `/tmp/archive-journal-lifecycle-green.log`、archive回归 `/tmp/archive-audit-regression.log`；第二轮reviewAccept，新增反例登记父Harness，父整套exit0 `/tmp/harness-h5-parent.log`，diffcheck通过。H5active/WIP=1，接下来冻结执行quick/clean再quality/最终风险门禁；尚无新实现完整验收，不提前prepare/归档/A5passing。不跑阶段cache，规范调整仅archive-state审计，既有fullfreshness不改。

**H4交付入口（优先）**：H1–H4passing/H5active，WIP=1。verify指定基线实际执行readiness/drift/clean/quality/cleanup，不跑Java/HA；完整来源与实现差异、后续journal/fullattempt失败阻断，原fullfreshness不变。初GREEN `/tmp/archive-audit-verify-green.log`，review899883bb首轮Revise五项（HA证明未闭包、派生非真实生成、attempt类型、治理开始时间、原验收两时点）；RED旧实现接受6例 `/tmp/archive-audit-review-red.log`，修后GREEN `/tmp/archive-audit-review-green.log`，第二轮scopedAccept。HA detail/25scenarios/空ledger纳入源collector与基线副本；真实report/handoff候选私有生成，7/9退出及rename失败不发布成功；原full验收时间与prepare时间分离、handoff双FP。父fixturefakeHA原仅打印log，已补真实schema证明文件，不弱化collector；父整套exit0 `/tmp/archive-h4-parent-integrated.log`，diffcheck通过。没有真实Maven/HA/full，A5未关闭。下步H5先同步规范与命令，独立Review后冻结最终验收，归档用新审计规则，不再擅自扩实现。

**当前交接（优先）**：H3passing/H4active，WIP=1。archive-audit prepare已接线，verify仍明确pendingH4；保存严格固定成功集合/真实full阶段/quality12unit原JSON及副本映射，journal记录dispatch前启动与最终退出。初RED `/tmp/archive-prepare-red.log`，GREEN `/tmp/archive-prepare-green.log`；H1/H2回归 `/tmp/archive-h1.log`、`/tmp/archive-h2-final.log` exit0。独立review41d5a214首轮Revise四项（quality自定义空unit、generic证据字段、journal配对、CLI取消覆盖）；新增16非法案例RED `/tmp/archive-prepare-review-red.log`，修后GREEN `/tmp/archive-prepare-review-green.log`，第二轮scopedAccept。父fullfixture原fakegit非合法HEAD，改为真实临时Git+忽略生成物（不放宽生产验证）；父全套exit0 `/tmp/archive-prepare-parent-final.log`。ruby-c/bash-n/diffcheck通过，无真实Maven/HA/full，无Git提交。下步H4指定基线状态差异/当前治理/后续失败阻断及report-handoff完成指针；不能用prepare冒称归档关闭，A5仍blocked。旧设计通用复用延期。

**缩减后当前入口（优先）**：用户要求继续归档差异审计，独立reviewer67bfb224设计第二轮Approved。H3active/WIP=1改为基线与失败留存，H4为状态审计/独立展示；原通用阶段缓存及依赖闭包延期（不是已完成）。原full freshness不改，历史验收与当前治理两时刻明确区分。H1/H2代码保留，A5blocked/旧失败不动。只写stub/真实Git反例，不跑真实Maven/HA。

**H3 当前决策点（优先）**：H3blocked/active任务0。探查确认scripts实际读取真实Compose配置（test-local-compose-harness.sh:116）及ignored.harness状态（test-harness unsupported模式），Maven/HA consumed依赖闭包未采集；env+binary摘要不足以证明可复用，不造只对stub有效缓存。保留schema1所有阶段reuseEligible=false并说明原因，未新增依赖解析框架。仅安全子集HA要求本次存在普通非symlink空ledger：实际RED `/tmp/harness-reuse-red.log`，最终GREEN `bash scripts/test-routing-ha-harness.sh` exit0 `/tmp/harness-h3-ledger-narrow.log`，旧空ledger被runner删除/本次缺失拒绝，success显式写空。新增独立reuse测试文件已移除，反例合并现有fixture，不膨胀入口。限定reviewer5c8ac885 scopedAccept，不代表真实HA资源清理或H3完成；无Maven/真实HA/full。建议用户批准缩为archive-only优先，通用阶段缓存延期；需先修设计并复审，不能擅自改approvedscope或把H3勾绿。H1/H2交付仍保留，H4/5未开始。

H1/H2 passing，H3 active/WIP=1。H2固定CLI/chain/attempt/phase记录、独占构建锁和partial非passed已交付，续跑仍全部实际执行、reuseEligible=false（不能宣称H3复用）。初RED `/tmp/harness-phases-red.log`，GREEN `/tmp/harness-phases-green.log`；review2aaba3f6首轮Revise三项（提前passed、schema漏字段、collection覆盖raw退出码），RED捕提前发布exit91 `/tmp/harness-phases-review-red.log`，最终GREENexit0 `/tmp/harness-phases-review-green.log`，第二轮scopedAccept。父fixture漏helpercopy补齐，readiness工具stub不执行Ruby mkdir所以锁限实际构建入口；clean/E2E fixture补真实GitHEAD及生成目录ignore，不弱化生产unavailable失败关闭。父全套exit0 `/tmp/harness-phases-parent-complete.log`，最终集成reviewer1d0fa7cb scopedAccept。bash-n/diffcheck通过，无真实Maven/HA/full。H3下一步收集可证明配置/依赖身份并保守复用，任何未知闭包禁用复用。下方H1历史：H1 RED真实缺函数 `/tmp/harness-input-red.log`、GREEN `/tmp/harness-input-green.log`、移除排除规则变异RED `/tmp/harness-input-mutation.log`、原完整编码兼容 `/tmp/harness-input-compatibility.log`，bash-n/diffcheck通过。父自测首失败由trace精确定位新计划事实源固定前缀遗漏 `/tmp/harness-input-parent-trace.log`、`/tmp/harness-input-drift-attribution.log`；仅修计划bullet后drift exit0 `/tmp/harness-input-drift-green.log`、父自测整套exit0 `/tmp/harness-input-parent-final.log`，未弱化checker。独立e4954a93实现无finding但初Revise集成缺口，33f7b2ae后续静态复核及父日志scopedAccept。尚未切换verify/report freshness，未跑Maven或真实HA。下一步H2固定阶段边界/attempt与互斥的RED fixture；不将新增函数当作恢复已交付。A5保持blocked，旧124来源未确认、ledger清理状态未知；本任务不会删除其证据或修改历史结果。

## 回滚与残余风险

恢复禁用时回到从头执行；新记录只读保留不伪造旧schema。最重要风险是漏掉真实配置/外部制品，首版允许禁用复用，不为提高复用率造框架。实际HA资源残留须单独核对，不以自测通过证明清理。无Git提交授权。
