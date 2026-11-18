# 收尾流程精简实施计划

> 实施状态：S1–S3 passing，active任务0。真实完整验收及独立quality Accept已满足归档前条件；状态迁移后的关闭审计另行核对，不把该审计预记为通过。旧失败和延期边界保留。原Harness H5及A5保持blocked，本计划选择性撤下平台功能并去重，不改旧结果、不提交Git。实施采用subagent-driven-development，禁止整文件git restore。

## Sprint Contract

- 目标：按用户批准的收口边界，保留必要故障修复，延期阶段链/归档平台，最终一次自测＋一次reactor＋一次真实HA。
- 范围：Harness入口/报告/fixture/规范；不改业务生产、缓存容量、依赖/镜像或HA900秒与25场景。
- 验收：回撤闭包无硬引用，独立入口兼容，默认full Java失败不启动容器；同轮startup/behavior/E2E/HA证据检查不弱化；真实full一次、独立quality及廉价关闭审计。归档后旧full如实stale。
- 约束：大量混合WIP保留，延期文件先/tmp留样；`.harness`运行历史不删除。环境只用既有隔离repo，本计划readiness通过 `/tmp/closeout-simplify-readiness.log`。

## 事实源

- 设计或产品规格：[精简实施设计](../../design-docs/2026-10-22-closeout-process-simplification-implementation-design.md)，用户确认边界、独立reviewer905aa25e第二轮Approved。
- [收口提案](../../design-docs/2026-10-22-closeout-process-simplification-design.md)、[PLANS](../../PLANS.md)、[Harness Guide](../../references/HARNESS_GUIDE.md)、[根架构](../../../ARCHITECTURE.md)。实现完成前不提前用新规则豁免旧验收。

## 验证分层

先只读差异和依赖盘点；隔离fixture RED/GREEN＋语法＋独立Review。父Harness是集成，不先跑quick再full。最终冻结后只跑full一次（含全部等价校验）、独立quality、cleanup/handoff。失败先窄层；无依据不重复full。原脚本/报告diff已保留 `/tmp/closeout-simplify-harness-before.diff`。

## 任务状态

| ID | 任务 | 状态 | 验收 |
|---|---|---|---|
| S1 | 选择性撤下链式平台及其fixture依赖 | passing | 默认入口与原失败语义、旧证据保留、无硬引用 |
| S2 | 同轮Java报告等价校验及HA去重 | passing | 自测1/reactor1/HA1，零旧报告或跳过假通过 |
| S3 | 规范/状态同步和最终验收 | passing | 独立Review、一次full、两时点关闭审计 |

## 详细任务

### S1 选择性撤下链式平台及其fixture依赖

文件：`scripts/verify.sh`、`scripts/lib/harness-evidence.sh`、`scripts/harness-report.sh`、`scripts/harness-handoff.sh`、`scripts/test-harness.sh`、`scripts/test-handoff-harness.sh`、`scripts/test-load-test-harness.sh`、`scripts/test-routing-capacity-harness.sh`。

延期新文件先留样：`scripts/lib/harness-verification-phases.sh`、`scripts/lib/harness-archive-audit.rb`、`scripts/test-harness-verification-evidence.sh`、`scripts/test-harness-verification-phases.sh`、`scripts/test-harness-archive-audit.sh`、`scripts/test-harness-archive-journal-lifecycle.sh`。保留HAprocess、cache、ps及采样fixture。

- [x] 新增/调整父stub RED：旧扩展参数返回2；普通full按readiness/clean/selftest/Java/HA线性执行，Java失败HA调用0，full失败后report/handoff仍尝试且原始码优先。
- [x] 留样延期文件和相关diff，撤入口source/dispatch/journal/candidate消费者及4类专属fixture，保留原完整指纹失败关闭、单一构建锁和取消安全，不删.harness历史。
- [x] `bash -n`受影响shell、父fixture与load/capacity/handoff聚焦GREEN；grep无延期helper硬引用，独立Review。S2未实现前同轮优化不宣称交付。

### S2 同轮Java报告等价校验及HA去重

文件：`scripts/verify.sh`、新增 `scripts/lib/harness-reactor-evidence.rb`、`scripts/harness-startup.sh`、`scripts/run-routing-ha-scenarios.sh`、`scripts/routing_ha.sh`、新增 `scripts/test-reactor-evidence.sh`、`scripts/test-startup-harness.sh`、`scripts/test-routing-ha-harness.sh`、`scripts/test-routing-ha-process.sh`（新增helper依赖与真实Git前提）、父Harness fixture。

- [x] RED枚举当前源码、清旧必需XML/两个execJAR，严格本次epoch；同秒旧文件、missing/零测试/失败/skip/模糊selector/未来epoch拒绝。
- [x] 保留startup映射/隔离校验，增加只消费本轮报告模式；FQCN及方法实际执行判定统一，Maven后固定XML/JAR摘要，本次HA前和结果发布前复核。
- [x] full先Java后HA，HA内部同轮模式跳过重复package/5focused调用，仅在证明完整后运行真实Compose/故障/清理；standalone原行为保持；Java失败容器0，缺报告/制品不启容器。
- [x] 聚焦 `bash scripts/test-reactor-evidence.sh`、startup/HA/父stub GREEN，实际trace计数1/1/1，无真实Maven/HA作分支测试；独立Review。

### S3 规范/状态同步和最终验收

文件：`AGENTS.md`（英文）、`README.md`、`docs/PLANS.md`、`docs/references/HARNESS_GUIDE.md`、`docs/references/HARNESS_RULE_MATRIX.md`、旧设计/索引、三个计划及PROGRESS。正式quality Q1附加窄scope：`im-management/im-audit/im-audit-server/src/test/java/com/co/kc/imchat/management/audit/ImAuditApplicationTest.java`，只补真实Application隔离上下文与关键Bean断言，不修改生产/POM/UI或扩写Harness。

- [x] Q1真实RED捕获只有注解无上下文，最小真实@SpringBootTest隔离MySQL/Redis/Nacos/Kafka/IAM，实际环境配置和关键Audit Bean断言；聚焦单类GREEN与独立复审，先窄后宽，不自动full。

- [x] 移除平台功能承诺/归档特权，明确同轮等价证明与去重唯一验收集合。归档两时点内容差异独立Review＋drift/clean/quality/cleanup/handoff，原fullstale不改写；机器未覆盖语义标Review-only。
- [x] 独立代码/规范Review，通过后固定非状态输入，一次 `./scripts/verify.sh full`，再独立quality/cleanup/handoff；原A5按实际验收单独核对，不自动晋级。
- [x] 已核对全部实施与归档前条件。passing后归档当前计划，状态内容差异独立Review、廉价治理检查仍属于生命周期关闭审计，结果由当前Harness及独立审查拥有；失败恢复active，不借旧基线或删除失败记录。

## 恢复状态

**归档实施验收检查点（覆盖以下历史）**：当前实现指纹464d59363acb下full exit0/1375秒、2043项0failures/0errors/2显式条件skip，工作树稳定，gate/finalization/report/handoff均成功 `/tmp/audit-fixed-final-full.log`；同轮architecture73/startup17/behavior41/E2E1、真实HA25场景清理通过。Audit真实隔离上下文2项已执行，正式Reviewer cf150c61当前scope d9fdcc3cb631 Accept并关闭Q1，质量脚本exit0 `/tmp/audit-fixed-final-quality.log`，cleanup/handoff exit0 `/tmp/audit-fixed-final-{cleanup,handoff}.log`。旧Revise原文保留 `.harness/quality/review-history/before-audit-q1-fix-revise.json`，不改分数。S1–S3满足归档前条件，归档后仅状态内容Review及廉价drift/clean/quality/cleanup/handoff；原full stale照实显示，不再Java/HA。旧平台计划明确撤回/延期非completed，A5按自己的契约决定，不声称未知历史Raft/Docker根因或Redis副作用已消失。

**Audit修正后唯一验收窗口**：Q1单文件真实隔离启动已通过聚焦/模块和独立Review，用户继续要求收尾。当前约11GiB；迁移前全Git-visibleMarkdown盘点无非状态直链阻塞 `/tmp/audit-fixed-final-migration-refs.json`。集中此状态后冻结所有非状态输入，唯一full `/tmp/audit-fixed-final-full.log`（自测1/reactor1/真实HA1，外层3600），不叠quick/各独立风险构建。独立formalquality同时核对当前scope，之后cleanup/handoff；真实失败停对应层，不自动重跑。通过后A5按原验收独立核对，将已取代quick顺序如实分类，不冒称未执行命令已执行；旧平台计划明确撤回/延期仍保留原路径，不假归档completed。仅两份实际完成计划迁移，精确状态内容Review＋廉价drift/clean/quality/cleanup/handoff，不再执行Java/HA。非状态补改将使此窗口重新失效，因此不做便利优化。

**Q1窄修完成（唯一当前入口）**：只改Audit ImAuditApplicationTest，真实@SpringBootTest加载生产Application，自有6服务/Controller非mock且active；持久化/IAM协作者隔离、真实配置绑定，关闭外部Nacos/DBRedisKafka/Stream binding并断言无连接Bean/绑定。RED旧测试contextnull `/tmp/audit-startup-red.log`，GREEN2项0fail/error/skip `/tmp/audit-startup-green.log`；review90d8a600 scopedAccept关闭Q1，不能解释为外部基础设施安全集成。Audit SDK/Server及依赖reactor模块命令 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-management/im-audit/im-audit-sdk,im-management/im-audit/im-audit-server -am test` exit0 `/tmp/audit-startup-module-green.log`，SDK35/Server47项无失败错误跳过，未改POM/生产/UI。S3仍blocked/active任务0，原formalRevise保留，不能凭scopedAccept手改旧formalJSON。下一步只有补最终输入验收和独立formalReview，原1317秒full真实成功仅覆盖Q1修正前，当前不冒称fresh，不扩写Harness/自动重跑整套。本轮未full、未UI构建、未提交Git；UI未改动，不以此声称UI已复核。

**当前验收结果（唯一入口）**：修正条件分类后父Harness exit0 `/tmp/closeout-classified-parent.log`；真实精简full exit0/1317秒/工作树稳定，2042项0failures/0errors/2显式MySQL条件skip，gate/finalization均passed，真实HA25场景及清理通过，日志 `/tmp/closeout-classified-real-full.log`。cleanup扫描exit0 `/tmp/closeout-classified-cleanup.log`。formal reviewercc726ca8当前scope独立Revise，唯一Q1为Audit startup-smoke仅检查@SpringBootApplication，Nacos隔离仅未使用常量，没有真实context；源码已核实，非本次去重引入，但不能称10应用完整启动。原样JSON `.harness/quality/reviewer-response.json` 及 `/tmp/closeout-simplified-quality-revise.json`，不手改分数。S3blocked/active任务0，下步仅审查并补真实隔离Audit startup测试或由用户明确延期该能力（不能继续标启动通过），聚焦验证，不扩大Harness/自动full。当前实现全量测试/HA已实际成功，但qualityfinding未关故不能passing/归档A5。旧Harness平台已撤回/延期，不能补勾H5伪装交付；先保留原路径及历史，不违法移completed/history。关闭资格审查55bc3733要求A5旧quick字面步骤注明被批准去重合同取代，不伪称旧顺序执行；原full迁移后stale如实保留。

**条件分类后最终窗口（唯一当前）**：仅helper/fixture修类级EnabledIfSystemProperty条件Architecture，manifest单列条件报告并如实记录全部skip及原因；mandatory仍strict，类体条件/未知组合不给豁免。RED `/tmp/reactor-conditional-red.log`、GREEN `/tmp/reactor-conditional-green.log`；组合条件补反例RED `/tmp/reactor-conditional-combined-red.log`、GREEN `/tmp/reactor-conditional-combined-green.log`，review243c159f第二轮scopedAccept。startup fixture通过；不调用prepare、不删除旧产物，临时manifest用保存同轮真实XML/JAR postcheck通过 `/tmp/reactor-conditional-saved-report-check.log`（只诊断，非freshfull）。Guide已写准确条件语义。S3active/WIP=1，父Harness后冻结唯一full `/tmp/closeout-classified-real-full.log`，无独立风险重复构建；通过后独立quality/cleanup/handoff与两时点归档Review，失败停窄层。可用约14GiB，不追加新功能，不提交Git。

**首次真实运行结果（唯一当前入口）**：精简full782秒exit1/工作树稳定 `/tmp/closeout-simplified-real-full.log`；父自测通过、一次Maven reactor退出0，本次窗口2042项/0failures/0errors/2skipped。随后校验器拒绝 `LocalShardingRoutingIntegrationTest` 的2个skip（类级EnabledIfSystemProperty im.test.local.mysql=true，当前未设置，XML明确记录条件未满足）。HA尚未启动。这是新增checker把可选环境集成测试纳入必需Architecture组的契约错误，不是业务测试失败，不能笼统放过skip。全仓旧XML另含29个历史errors，不属于本次Maven窗口，不误报为此次失败。下一步仅修必需/条件测试分类设计并补fixture，仍要求startup/behavior/e2e/HA所有必需case实际执行；条件测试运行资格由源码/运行参数解释，不按类名例外。当前未再次full、未删本次报告/证据、未运行容器。本次已验证Java先行能在容器前停止；不能据此宣称完整验收或耗时收益已通过。

**本次唯一冻结窗口（覆盖后文历史）**：用户授权清理并运行。仅删除 `/tmp/dtpet-follow-refund.SNP6TT/.isolated-m2` 中与其他本机Maven仓库逐字节SHA256相同的66834个公开缓存文件，释放7131774324bytes；私有/SNAPSHOT10391文件和2个无匹配文件保留，外层源码/补丁/日志不动，本项目jraft-artifact-m2不动。清单/结果 `/tmp/isolated-m2-approved-cleanup-{manifest,result}.json`；可用约14GiB。S3active/WIP=1，代码/规范已独立Review Accept，固定此状态后不编辑非状态文件，唯一 `./scripts/verify.sh full` 日志 `/tmp/closeout-simplified-real-full.log`（外层3600，HA900不改）；无重复quick/独立risk构建。通过后独立quality/cleanup/handoff，按精确状态内容Review关闭，不重建archive平台。失败停对应层，不自动再full。旧证据/风险保留，不提交Git。

**S3当前唯一入口（优先，以下为历史）**：S1/S2passing，S3blocked/active任务0。AGENTS/README/PLANS/HarnessGuide/矩阵及旧设计状态已同步：完整验收仅一次自测/reactor/真实HA，quick非相邻前置，旧--through/--resume/archive发布特权撤下；两时点归档为原实现验收＋精确状态内容差异独立Review/廉价治理，旧full照实stale。代码集成review92274291 Accept，规范review430ac3ba Accept；父Harness原S2exit0 `/tmp/closeout-s2-parent-final.log`，本轮廉价drift exit0 `/tmp/closeout-s3-drift.log`、diffcheck通过。磁盘起始2GiB，按已批准白名单清8个ignored可再生成execjar释放1183812238bytes `/tmp/closeout-s3-ignored-jars-cleanup.json`，可用仅约3.6GiB，不再低余量启动full。未删其他项目/容器/卷/私有repo/历史证据；原误prepare删除ignored旧XML/两jar事实继续保留，当前target不能当新验收。下一步须用户提供/批准足够安全空间（建议至少10GiB）后，固定所有非状态输入，唯一full一次，再独立quality/cleanup/handoff；不叠quick或独立startup/behavior/e2e构建。首次实际full/性能尚无证据，不标S3passing或自动A5passing，不Git提交。

**S2完成入口（优先）**：S1/S2passing，S3active/WIP=1。新固定reactor helper277行（非chain/cache），full同轮清旧XML/2JAR→一次Maven→精确FQCN/testcase/tag及startup映射检查→固定摘要→HA只真实拓扑，standalone保持自建。Helperreview118d2688先Block外部symlink清理，整批安全预检和缺/错/skip/manifest畸形/IAMfallback反例后Accept；日志 `/tmp/closeout-s2-safety-{red,green}.log`。集成review64888df8先Revise detail业务字段被丢及manifest未固定身份，根身份保护集合保留旧/未知业务字段、入口后固定SHA前后复核，真实record路径smoke/capacity及自洽替换RED/GREEN `/tmp/closeout-s2-review-{red,green}.log`，复审Accept。父首失败因process fixture未复制新增helper/无真实Git，补fixture专用ha-check stub、原evidence库、临时GitHEAD，生产不弱化；聚焦exit0 `/tmp/closeout-s2-process-caller-green.log`，最终父整套exit0 `/tmp/closeout-s2-parent-final.log`，diffcheck通过。没有真实Maven/HA/full。子任务误对realroot执行prepare删除必需ignored旧XML/2execjar已披露，源码/.harness历史未删，selection `/tmp/reactor-real-selection.json`；当前target不足验收，不重建来掩盖。下步S3同步政策/旧设计状态，冻结一次真实full，不叠quick或各独立构建；磁盘低需仅盘点/已批清理后再决定。

S1passing/S2active，WIP=1。选择性回撤留样 `/tmp/closeout-simplify-platform-backup/`，新增123/删1677净减1554行，脚本中延期helper/API硬引用0，战术修复/.harness历史保留。父Harness一次exit0 `/tmp/closeout-s1-parent-green.log`，full局部fixture `/tmp/closeout-s1-target-green.log`；reviewd598d683发现freshincomplete聚合passed，真实RED `/tmp/closeout-s1-incomplete-red.log`、GREEN `/tmp/closeout-s1-incomplete-green.log`，第二轮scopedAccept。语法/diffcheck通过。当前full已经Java在前，但HA仍standalone重复构建，S2未交付不得宣称完全去重。下一步实现本次报告等价校验，源码映射/skip/旧报告/两execjar摘要反例先行；不跑真实Maven/HA/full。原A5/H5blocked，证据不删，不Git提交。

## 回滚与残余风险

只针对任务新增功能留样和回撤，禁止覆盖用户WIP。回滚可从留样恢复，但不能把旧archive成功解释为现行授权。故障修复与生产行为保留；Docker内部故障、原Redis丢失、Raft偶发未知、私有制品后轨等不由本流程优化消除。
