# Active Execution Plans

当前活跃计划：none。[Bolt 包职责与 Broker 快照归位](../completed/2026-10-25-bolt-package-boundaries.md#attempt6-与复审-r1r52026-10-29) 已关闭并归档：T1–T3 passing，冻结 full attempt8 exit0，独立 quality 复审 Accept；迁移后复核与廉价检查由独立审查和当前 Harness 拥有。下文旧「当前」入口均为历史状态。

当前活跃计划：none。[HA 证据时序与维护验收收尾](../completed/2026-10-24-ha-evidence-order-closeout.md)已完成验收、独立 Review、正式 quality 和迁移前关闭检查并归档；实际迁移复核与后置廉价检查另存隔离证据根。下文旧“当前入口”、T6 active 和未完成描述均为历史记录，不授权恢复旧任务。
当前恢复入口：Bolt 实现已验收并已迁移，状态关闭检查已完成。活跃计划/当前实施任务：none。full8 与最终59cb实现 Accept 绑定原冻结输入；40c50a94 已对实际迁移及用户批准 href-only delta 给出 Accept，迁移 Review 已通过；第二时点五项廉价检查已实际完成，状态关闭完成；原验收与残余风险边界不变。Loop4 PAUSED，原时间、指纹、stale、Block 历史与残余限制保留。


此目录保存正在实施的已批准工作。每份计划遵循 [Execution Plan Policy](../../PLANS.md)：定义一个 Sprint Contract，至多一项任务为 `active`，任务状态仅限 `not_started`、`active`、`blocked` 和 `passing`。阻塞任务继续保留记录，但不授权开展无关范围。只有准确命令及其实际结果，或其他可复查证据仍与验证过的工作树或 revision 状态一致时，任务才保持 `passing`；跨会话恢复状态保存在计划内。

## Active plans

- [Bolt 包职责与 Broker 快照归位](../completed/2026-10-25-bolt-package-boundaries.md)：T1–T3 passing，已归档至 completed。

- [收尾流程精简](../completed/2026-10-22-closeout-simplification.md)：实施任务全部passing，完整验收和独立quality Accept已完成；状态迁移关闭审计另见当前Harness和独立审查。

- [Bolt 连接层认证与统一 invoke](../completed/2026-10-22-bolt-unified-invoke.md#t6-状态关闭清单)：实现已验收、已迁移，状态关闭检查已完成；活跃实施任务 none，迁移 Review 与廉价检查均已完成。

- 已撤回的 [Harness 原验证平台记录](../history/2026-10-21-harness-verification-resume.md)移至history；保留原状态和证据，不计为completed，不恢复平台。

- 本地路由 HA 模拟计划已完成并归档。
- [Broker Adapter 职责重划](../completed/2026-10-11-broker-adapter-boundaries.md)：A1–A5passing，最终完整验收和独立Accept已完成；未知历史根因及后轨事项仍保留，不等同生产发布保证。
- Bolt 统一 invoke 已按批准设计登记上述新计划；不恢复历史 A5，不以设计批准替代技术退出证据。

当前T6第二轮full707.160s/exit1，2743输入稳定，父自测通过但Broker778 tests/2 failures/38 errors，真实HA未到；239 fresh XML/1438 tests不等于完整通过。Broker七文件fixture有限组已focused：startup2 tests及related最终89 tests全零，各输入稳定；related首次5 failures保留。冻结交父spec/quality，coexistence独立检查点且未改；见计划当前入口及 `/tmp/bolt-t6-broker-fixtures/`，T6仍active，不改生产安全或重写原失败。

T6后续有限共存组：前七文件spec824/quality87ff最终Accept；`RaftBoltCoexistenceSpikeTest` 已用正式受控DEV pair/owned typed invoke，失败三断言按父批准迁移为PROTOCOL/cause/真实Handler事实，不再声称原response字段可观察。focused1 test全零，后Broker完整module -am1286 tests全零（Broker779），13.627s/245.120s均exit0且输入稳定；证据 `/tmp/bolt-t6-coexistence/`。本组待父独立spec/quality，T6仍active，原full2失败不覆盖，未运行full3/HA。状态回填另SHA，不冒充运行fresh。

## T6 第三轮完整验收冻结入口

Broker七测试文件组已取得独立 spec `824`、quality `87ff` 最终 Accept/no findings，related最终89 tests全零；共存fixture独立 spec `8373`、quality `4f8ba` 最终 Accept/no findings，focused1 test及Broker完整module -am196 fresh XML/1286 tests全零。两组原始运行窗口、输入SHA及回填状态SHA分别保留在 `/tmp/bolt-t6-broker-fixtures/`、`/tmp/bolt-t6-coexistence/`；有限组双Accept不等于最终实现Review或完整验收。

当前取代上述“待父Review/未修复共存”的恢复指令：两组均已闭环，源码、稳定文档、设计、依赖及共享checker冻结，不新增源码修改。唯一writer仅本次集中回填PROGRESS、active计划及active README三状态文件，保存全部Git-visible before原bytes（含未跟踪计划全文）、精确状态diff/SHA、after snapshot与ignore规则及ignored Loop关键SHA于 `/tmp/bolt-t6-acceptance-attempt3/`。实际文件数由snapshot枚举，不硬编码。用户tracked `.gitignore`与有效忽略规则纳入完整输入，`.pi/`保持用户owned且ignored，不扩大quality exclusions。Loop4保持PAUSED，不启动/续期；不干预外仓Maven或既有k6 PID49694。

下一步先确认本仓无并发Maven，运行 `./scripts/verify.sh clean`，仅exit0且输入稳定后单次 `MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh full`（10800s工具预算）；完整执行父自测、一次reactor、同轮risk报告/制品proof及真实HA，不skip、不跨轮partial reuse。clean失败先归因停止；full失败保存最早owner/root cause、fresh XML/count与输入漂移边界交父，不盲重跑。原full1 463.426s/exit1 fixture engine预算、full2 707.160s/exit1 Broker778 tests/2 failures/38 errors历史原件不覆盖。

T1–T5仍scoped passing，T6唯一active/WIP=1。本轮开始后及结束后只写/tmp与ignored验证输出，不再Git-visible回填。full成功即时交父安排正式quality及独立最终实现Review，不手写semantic分数，不把review_required当Accept；不重复同输入独立architecture/startup/behavior/E2E/HA，不提前T6passing或归档。最终实际full+独立Accept后才另阶段两时点纯状态关闭；不提交、不部署、不删除未知.pi。

## T6 HA 装配有限修复（当前恢复入口）

第三轮 full 已实际980.909s/exit1，2743输入稳定：完整reactor495 fresh XML/2333 tests/0 failures/0 errors/2既有MySQL条件skip（排除8旧XML），同轮制品proof通过后首次真实HA失败。三Broker默认MTLS缺key-store，最早链为brokerPeerAdapter→boltInvoker→boltRpcClient→validateTypedPolicy:57；Redis/Nacos健康、Raft init成功。compose-up49.249s/exit1，未到ready/场景，scenarios0；owned project30344已清空。原件 `/tmp/bolt-t6-acceptance-attempt3/RESULT.md` 保留，不称full通过。

用户授权仅修测试部署fixture，保持T1–T5 scoped passing、T6唯一active。精确范围：`deploy/local/compose.routing-ha.yml`、`scripts/run-routing-ha-scenarios.sh`、新增 `scripts/lib/routing-ha-tls.rb`、新增 `scripts/test-routing-ha-tls.sh`、`scripts/test-routing-ha-process.sh`、`scripts/test-routing-ha-harness.sh`（复制fixture依赖）、`scripts/test-harness.sh`（挂入合同自测）、`deploy/local/README.md`、`im-test/im-routing-ha/README.md`、`docs/references/HARNESS_GUIDE.md`（既有材料隔离约定及合同传感器），以及PROGRESS/本计划/active README三状态文件。不改生产Java、依赖、观察授权/投票/Raft默认、checker证明或DEV/E2E组。

- [x] 先 `./scripts/verify.sh readiness`；保存 `/tmp/bolt-t6-ha-assembly-fix/` before原bytes（含untracked计划）、files/SHA/ignore、进程与原HA日志归因。
- [x] `bash scripts/test-routing-ha-tls.sh` 确定性合同先RED后GREEN：生成独立测试CA/五叶PKCS12、严格URI/DNS配对、有效期/cryptographic trust、0600/0700、缺失/篡改材料及生成失败拒绝；不打印秘密。材料只在runner新建owned临时目录，cleanup失败保留现场，不删除未知路径。
- [x] 修改真实HA Compose挂载及typed bootstrap/Gossip可信配对，采用容器DNS与明确Nacos发现地址交集；不把observationId当稳定BrokerID。`bash -n scripts/run-routing-ha-scenarios.sh scripts/test-routing-ha-tls.sh`、`bash scripts/test-routing-ha-process.sh` 验证原失败传播/清理及材料前检，保留900s原HA合同。
- [ ] 窄实际Compose启动/健康/状态一致与逐实例恢复（使用既有exec JAR并记录SHA；不冒同轮full proof，不改manifest checker），最多一次有界调试；原25场景映射Java证据仍属于full3，不拼新full。若遇生产行为bug立即交父，不偷偷修改。
- [ ] 源码/稳定文档冻结，保存source.diff、commands/exits/windows/logs/reports/RESULT及运行前后SHA交父spec再quality；本组不运行full4/提前passing。完整full新输入真实HA及独立Accept仍归父后续。

本有限组当前冻结交父spec再quality：readiness exit0，TLS合同missing-helper RED exit1→最终GREEN exit0（包括五独立私钥、CA私钥移除、缺失/损坏/公开权限/配置篡改及keytool失败反例），进程/材料清理合同和同轮manifest分支自测均exit0。真实窄Compose调试13:42:46.660–13:42:58.976Z/12.316s/exit18，在tls-create/check和compose-config均exit0后，Redis Pulling时Hub443拒绝连接；尚未到Broker，不能称多Broker MTLS/挂载通过。原误称localmissing已撤回：当前desktop-linux、DOCKER_HOST/CONTEXT/DEFAULT_PLATFORM均unset，同tagredis:7.2.0实际arm64本地存在（sha256:69bb36a0...，RepoDigest c45b9ac4...），渲染Compose无platform/pull_policy，本组未改这些键。full3也是desktop-linux/aarch64；为何尝试Pulling仍未知，不推断跨context/缺cache，不重拉/换tag/切context。owned cleanup-logs/down均exit0、ledger空，材料按trap删除。先前一次debug监督调用多传bash导致0.085s/exit0无实际步骤，不计拓扑证据；后纠正为上述唯一实际Compose调试。

精确source和新增文件、完整before bytes、运行SHA/日志/命令/环境facts见 `/tmp/bolt-t6-ha-assembly-fix/RESULT.md`。未跑Maven、正式ha、full4、clean/quality或完整父自测；用户要求有限组立即冻结，由父独立spec/quality后安排新输入验收，T6仍active、不提前passing。Loop4保持PAUSED，不提交/生产部署/网络恢复平台或未知资源删除。
## T6 第四轮标准完整验收冻结入口（当前）

HA装配有限组spec `e9` Accept，quality `ded17413` 经PID回收后撤销signal所有权修复最终Accept/Critical closed；旧Block与确定性RED不改写。实际缓存调试独立 `a574d95f` evidence/state Accept/no findings，原件 `/tmp/bolt-t6-ha-actual-debug/RESULT.md`：13:55:50.916582–13:57:39.389691Z，108.472s/exit0，2745 Git-visible输入changed=[]。真实初次Compose健康、HTTP就绪、三Broker状态一致及逐实例恢复通过，材料/容器装配越过原缺key-store；最后Broker-3 Docker health仍starting但HTTP已200 UP并状态一致，不能称最终五Docker同时healthy。原生lease/processor错误与业务授权/观察证明限制保留，不等同25场景、正式HA/full或最终实现Accept。

此前默认runner的Hub443 refused及已有同tag仍Pulling原因未知。本次新增前提仅是当前同tag镜像已被真实up使用、ID/架构未变及材料装配成功，不声称默认网络修复或保证full4成功。离线debug的 `--pull never --no-build` 仅/tmp诊断，不带入正式full；不改source、pull_policy、platform、tag、依赖或auth mode。完整新输入标准full4实际结果才是本轮证明。

唯一writer仅集中更新PROGRESS、本计划、active README三状态文档；before-state保存全部实际Git-visible原bytes，包含untracked计划全文、tracked用户.gitignore及新增helper/test。实际文件数每次枚举，不硬编码；.pi保持用户owned/ignored且关键Loop SHA保存，Loop4 PAUSED，不恢复或扩展exclusions，不干预外仓/既有k6。源码、build、稳定规范与全部文档在运行前定稿，保存前后SHA/精确状态diff到 `/tmp/bolt-t6-acceptance-attempt4/`。

- [ ] 核对本仓无并发Maven，分别执行 `./scripts/verify.sh readiness`、`./scripts/verify.sh clean`，各自记录exit及完整输入稳定性；任一步失败精确归因后停止。
- [ ] clean exit0且输入稳定后，唯一一次 `MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh full`，10800s工具预算：父自测、一次完整reactor、同轮riskreports/artifact proof、正式真实HA及25场景，不跨轮复用full3的2333 tests或debug状态。
- [ ] 运行中及结束后不再Git-visible写入，仅/tmp和标准ignored输出；保存原生exit/窗口、fresh XML mtime/count/skips、gate stage、manifest expected/actual、全输入changed list和owned cleanup事实。失败首先交父最早native owner/root，无盲重跑或新源码修复。成功即时通知父安排正式quality及独立最终实现Review，不把review_required或focused debug Accept当最终Accept。

T1–T5 scoped passing、T6唯一active，不提前passing/归档；full1 463.426s/exit1、full2 707.160s/exit1、full3 980.909s/exit1原记录保留。无重复独立architecture/startup/behavior/E2E/HA，无提交、部署、新平台或未知资源删除。以下旧“当前入口”均为历史阶段，由本节取代；本轮结束后仅/tmp保存实际结果，checkbox不回写Git-visible输入。

## T6 第五轮标准完整验收冻结入口（当前）

原close分支retirement前提修订独立SPEC `2406`、quality `64728544` 最终Accept/no findings：focused 21.720511292s/exit0，1 fresh XML/17 tests/0 failures/0 errors/0 skipped；owning module 167.282869167s/exit0，27 fresh XML/301 tests/0 failures/0 errors/0 skipped（common37、Bolt264），各独立窗口输入稳定。证据 `/tmp/bolt-t6-native-retirement-premise/RESULT.md`，不拼报告。新增纯观察2case独立SPEC `1e2052c2`、quality `376f9eed` Accept，证明CLOSE+public snapshot完成退休后的真实复用；这是明确测试契约前提校正，不是盲repeat green，也不是生产bug修复。原full4具体cause仍unknown/unresolved，null-selector NPE潜在production分类风险不得因新GREEN移除。无效ordering实验21.226334095s/exit1、2 errors仍为invalid fixture/API gap历史，不计机制RED。

HA装配spec `e9`、quality `ded17413` 最终Accept，PID生命周期Critical closed，旧Block/RED保留。有限实际HA调试独立 `a574d95f` Accept/no findings仅限108.472s/exit0、2745输入稳定的装配/状态/恢复，不是25场景或正式full证明。full3缺key-store的fixture修复已在源码；full4早停im-bolt未验其正式HA。默认Hub Pulling原因仍unknown，离线debug flags不带入full，不保证网络恢复。

原full1 463.426s/exit1（2744稳定、父fixture预算）、full2 707.160s/exit1（2743稳定、239 fresh XML/1438 tests、Broker778/2 failures/38 errors）、full3 980.909s/exit1（2743稳定、495 fresh XML/2333 tests/0 failures/0 errors/2既有localMySQLskip，制品proof后真实HA缺key-store、scenarios0）、full4 644.942985s/exit1（2745稳定、64 fresh XML/441 tests/2 failures，im-bolt262/2 failures）各原RESULT、原窗口/失败/stale事实不覆盖，不复用旧reactor/full proof跨输入。

唯一writer本次只集中回填 `PROGRESS.md`、`docs/exec-plans/active/2026-10-22-bolt-unified-invoke.md`、`docs/exec-plans/active/README.md`；全部实现/设计/稳定文档已定稿，无新source/checker/quality exclusions/API/预算修改。证据 `/tmp/bolt-t6-acceptance-attempt5/` 保存beforestate实际原bytes（含untracked计划、新TLS helper/tests）、exactstate.diff、sha、snapshot及readiness/clean/full/RESULT；实际输入数由枚举记录，不硬编码。用户tracked.gitignore纳完整输入；.pi保留用户owned/ignored，Loop关键SHA旁证，Loop4保持PAUSED，不恢复/续期。不干预外仓Maven或既有k6未知PID，不kill未知进程。

- [ ] 冻结全部Git-visible文件、HEAD、ignore及Loop后确认本仓无并发Maven；分别执行 `./scripts/verify.sh readiness`、`./scripts/verify.sh clean`，记录真实独立exit/窗口/输入稳定性；任一步失败先归因停止。
- [ ] 仅clean exit0且输入稳定后，一次标准 `MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh full`（10800s工具预算）。不带--pull never/--no-build等debug flag，不换GAV/版本/mirror；父selftest一次、完整reactor、同轮risk reports/artifact proof、真实HA及25scenarios全部实际完成才称machine full success。无需前后重复独立architecture/startup/behavior/E2E/HA。
- [ ] 运行中及结束后不再Git-visible写入，仅/tmp和标准ignored输出；保存原生exit/完整日志、fresh XML原窗口mtime及module/count/精确既有MySQLskip、stage/processor启动/25case operation与missing milestones，排除旧target XML；before/after输入changed=[]才算稳定。网络或最早native失败原件保留，不绕过，不盲full6或源fix，停止交父独立task。
- [ ] full成功立即通知父并冻结全部machine证据，仍T6active、不回填passing/归档/提交；父后续正式 `./scripts/verify.sh quality` 与独立final implementation Review须考虑full4 unresolved风险，不将review_required或新pass当根因关闭；若Block则重开、修复并适当fresh revalidate。

T1–T5 scoped passing、T6唯一active/WIP=1；无提交、生产部署、新平台、未知.pi删除。此节取代所有此前“当前入口”，旧状态保留为历史，不在full后回写checkbox。正式完整验收与独立Accept后方可另阶段纯状态关闭及精确diff审查。

## T6 full5 ENOSPC 恢复（当前入口）

full5 **incomplete / 验收无法确认，原生退出码未知**；不记 exit1 或 pass。原 worker ENOSPC 后未写 full/run.json、exit 或 post-full snapshot。原证据 `/tmp/bolt-t6-acceptance-attempt5/` 不重建、不覆盖；恢复报告 `/tmp/bolt-t6-enospc-recovery/RESULT.md` 及机器 JSON 保存原路径/SHA。父 Harness 已越过自测；独立本轮 reactor 窗口 2026-10-23T15:10:09.909561–15:18:40Z：495 XML/2335 tests/0 failures/0 errors/2 既有 MySQL 条件 skip，排除8旧XML；manifest48项XML/exec-JAR当前SHA匹配。这些只是 reactor 阶段证据，不是 full 通过。

真实 HA 已 compose-up、五应用healthy、readiness、cluster-consistency和Gateway stop exit0；下一readiness started无finished，cleanup-logs started后Ruby ENOSPC截断。scenarios.jsonl为0 bytes（0条已完成场景证据）；cleanup-down/完成无证，不借旧routing-ha passed/旧cleanup日志。恢复时2745 Git-visible文件（含untracked计划、用户.gitignore）、HEAD/ignore/ignored Loop字节与冻结相同，但缺原终点，不能冒充原运行全程稳定证明。

owned项目 `im-chat-routing-ha-87933` ledger保留，cleanup **blocked**：Docker ps 20s及单次version15s超时，backend存在不等于daemon响应；未down、重启Docker、kill backend或prune。TLS候选 `/private/var/folders/h9/drjh32n10x170kpxrbpl_1p00000gn/T/im-chat-routing-ha-tls.oI0ZBfjE` 与tls-create时间对应，0700/0600原权限保留；daemon未响应，尚缺inspect label/mount最终绑定，不删除候选或其他目录。恢复只读 `verify.sh cleanup` exit0仅其tmp/PID allowlist，不是HA清理证明；恢复readiness exit0不证明Docker/空间健康。

当前可用约4.58GiB，targets实占约1.91GiB，attempt5证据约45.8MiB；缺开始前target/VM基线与峰值，无法计算target delta或证明安全margin足够。父先审本报告，需用户确认Docker维护窗口后精确收口87933并评估空间，再决定新冻结full；本任务不跑full6、不叠HA。T1–T5 scoped passing，T6唯一active/WIP=1、Loop4 PAUSED；full4 cause unknown/unresolved和null-selector潜在production风险保留，原专项Accept不代替最终完整验收/独立final quality。此次仅状态batch，state-after SHA与原运行输入分开，不篡改原报告时间/退出码。无提交、部署或依赖/checker/网络策略修改。

## T6 Docker恢复后的owned清理（当前入口）

用户确认Docker已恢复后，本次仅收口原full5 owned项目 `im-chat-routing-ha-87933`；Docker Server 27.4.0实际响应。六个原容器的Compose project/config_files labels与mount共同确认原文件 `deploy/local/compose.routing-ha.yml` 及材料 `/private/var/folders/h9/drjh32n10x170kpxrbpl_1p00000gn/T/im-chat-routing-ha-tls.oI0ZBfjE`。Docker Desktop返回的 `/host_mnt/private/var/...` 和 `/var/...` 为host路径别名；首次严格字符串核验因此停止且未清理，按显式host映射与realpath归一后重新核对，非凭目录名删除。

- [x] `./scripts/verify.sh readiness` exit0；保存原容器各最多300行日志、label/mount与owned network/volume inspect、原ledger和证据路径/SHA，不打印TLS密码或私钥内容，不复制源码全树。
- [x] 使用原 `scripts/lib/routing-ha-process.rb` 在独立恢复state监督两个cleanup窗口：cleanup-logs 2026-10-24T00:30:29.874269–00:30:30.513281Z/exit0；cleanup-down 00:30:30.580117–00:30:31.095713Z/exit0，均groupGone=true。down精确参数为 `docker compose -p im-chat-routing-ha-87933 -f <原绝对Compose路径> down --volumes --remove-orphans`，环境仅重建原 `ROUTING_HA_TLS_DIR`，未up/pull/build。
- [x] down后project container/network/volume查询均空；另检查全部剩余容器mount无owned TLS引用，材料树无symlink、uid为当前用户、目录0700/文件0600，才删除该精确目录。按原runner成功down合同清空ledger，不修改历史command-group owner、full5退出码/结束时间或full报告。
- [x] 仅hash确认恢复cleanup输入changed=[]；与full5冻结的差异仅前次恢复三状态文件，不能补原full终点。必要状态batch保存三文件before原bytes及精确state.diff，新状态SHA不冒充原运行输入。证据 `/tmp/bolt-t6-docker-recovery/RESULT.md`，原attempt5/enospc记录不覆盖。

当前df available 8,762,925,056 bytes（约8.16GiB）；Docker.raw逻辑63,999,836,160 bytes、实占25,415,987,200 bytes。四个所需原tag的image inspect实际存在，精确IDs见恢复JSON；这不保证默认Compose不会访问Hub，也不保证下一full不会再次写满。仍缺full5容量基线/峰值，不能承诺安全margin。本批次无Maven/full6/父自测、不全局prune/切context/unknown kill，不动外仓k6、用户.gitignore/.pi、私有m2/POM或原日志证据。

这些是新恢复cleanup窗口，不是full5正常结束：full5仍incomplete、原生退出码未知，scenarios.jsonl原0 bytes且无25场景通过证据。T1–T5 scoped passing、T6唯一active/WIP=1、Loop4 PAUSED；full4 cause unknown/unresolved及null-selector潜在production风险保留。父可先独立审阅精确证据/state.diff，再另批重新冻结full6；不自行运行，不提前passing/归档，无提交或部署。

## T6 第六轮标准完整验收冻结入口（当前）

owned87933恢复清理已获独立 `9038f4ce` 最终Accept/no findings，原cleanup writer已completed并停止；恢复证据以 `/tmp/bolt-t6-docker-recovery/RESULT.md`、cleanup-result.json及state.diff实际文件为准。原full5仍incomplete、原生exit未知，495 XML/2335 tests全零/2既有MySQLskip仅属该轮独立reactor阶段，不跨轮复用；所有原失败和未知终点保留。

用户批准仅一次新输入标准full6。原close前提SPEC2406/quality64728544双Accept、HA装配e9/ded双Accept及a574有限实际debug Accept边界保持不变；source、稳定文档、code/依赖/checker均已final，无新生产修改。full4 cause unknown/unresolved及null-selector潜在production风险保留。T1–T5 scoped passing，T6唯一active/WIP=1；Loop4 PAUSED，父与scheduler无writer，唯一执行者本次只集中更新PROGRESS、本计划、active README三个状态文件后冻结。

证据入口 `/tmp/bolt-t6-acceptance-attempt6/`：仅保存三状态文件before原bytes（明确含untracked计划）、精确state.diff、完整Git-visible SHA/size/mode、HEAD、ignore及Loop副本。其他source原bytes引用attempt5冻结sameSHA原件，不重复镜像2745文件；jar保留原路径，报告按实际size仅保存必要压缩XML及小manifest，不复制大型报告树。用户tracked.gitignore纳完整输入，ignored.pi不删除、不扩大quality exclusions，不干预外仓/k6。

- [ ] 运行前确认本仓无Maven并记录df、Docker响应及四canonical tag只读inspect；本次执行guard为available至少3GiB，否则环境阻塞即停，不是生产容量/性能保证。恢复约8GiB不保证full不会ENOSPC；无全局du/prune或未知cache删除。
- [ ] 冻结后顺序 `./scripts/verify.sh readiness`、`./scripts/verify.sh clean`，各真实exit0且输入稳定才进入下一步。
- [ ] 仅一次 `MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh full`（10800s工具预算），标准父selftest、完整reactor、同轮risk/artifact proof、真实HA25场景，不使用debug offline flags、不跨run复用。实际exit及时独立小文件落盘，raw stdout单份，磁盘耗尽时尽力保存但不伪造保证。
- [ ] 运行中及结束后无Git-visible写入，仅/tmp和标准ignored输出；保存真实exit/窗口、changed列表、fresh reports/count/skips及同轮artifact/scenario/owned cleanup证据。最早失败归因后冻结返回，不盲full7/源码修复。成功立即交父正式quality及独立final implementation Review，仍不提前T6passing/自动归档/提交。

此节取代旧当前入口；运行后checkbox保留冻结前状态，事实仅写/tmp。无重复独立architecture/startup/behavior/E2E/HA，无commit/deploy或Loop恢复。完整验收和独立Accept之后才另阶段纯状态closure及精确diff审查。

## T6 第七轮标准完整验收冻结入口（当前）

full6 原验收失败保留：2026-10-24T00:33:42.176042–00:50:09.186324Z，987.004078s/exit1；独立本轮 reactor 495 fresh XML/2335 tests/0 failures/0 errors/2 既有 MySQL 条件 skip、48 项同轮 manifest SHA 匹配，但真实 HA 在历史 registry 前检被拒绝，尚未 spawn 场景。原件 `/tmp/bolt-t6-acceptance-attempt6/RESULT.md` 不覆盖。full5 仍 incomplete/原生 exit 未知，owned87933 恢复清理独立9038f4ce Accept不补原full5终点。

历史 registry 人工隔离 checkpoint 已获独立 d51 closure Accept；精确源、归档绑定及其他六个 session 不变证据见 `/tmp/bolt-t6-registry-quarantine/RESULT.md`。归档 owner 仍 active=true、无 done，明确人工隔离，不伪造 resolved/正常退出。随后公开原生 helper 在正式 registry 唯一一次 `run engine-info 8 docker info` 实际1.073047s/exit0，rawExit0/groupGone=true，新 session 正常 owner inactive/done inactive/无 pidfile；原六个 session 与 archive 未改。原件 `/tmp/bolt-t6-native-runner-recovery/RESULT.md`。这仅证明原生窄入口恢复，不是 supervise 整场景、25 HA、full 或跨轮 reactor 证明。

用户已授权自动推进本次唯一标准 full7。T1–T5 scoped passing、T6 唯一 active/WIP=1；Loop4 保持 PAUSED。唯一 writer 仅本批集中更新 PROGRESS、本计划、active README 三状态文件，随后冻结，父与其他 agent 不写。source、scripts、稳定文档、依赖、pull_policy、quality scope 均已定稿且不改。full4 cause unknown/unresolved 及 null-selector 潜在 production 风险仍交最终独立 Review，不能因新 GREEN 手工降分或关闭真实 finding。

证据 `/tmp/bolt-t6-acceptance-attempt7/`：三状态 before 原 bytes（含 untracked 计划全文）、exactstate.diff、全部实际枚举 Git-visible SHA/size/mode、HEAD/ignore/Loop SHA。其他 source 原 bytes 引用此前 sameSHA 冻结原件，本轮重新 hash，不复制源码全树/JAR。用户 tracked .gitignore 纳完整输入；.pi 保用户 owned/ignored，不删除未知内容、不扩大 exclusions、不恢复 Loop、不干预外仓 build 或 k6。

- [ ] 记录本仓无并发 Maven/full/helper/Compose writer、实际 df（available 至少3GiB，否则停止）、Docker version、四 canonical tag inspect、旧 owned87933 project container/network/volume 查询空；未知其他项目不操作。guard 不保证容量或网络安全，不全局 prune/kill。
- [ ] 冻结后独立顺序 `./scripts/verify.sh readiness` → `./scripts/verify.sh clean`，各真实 exit0、输入稳定才进入下一步，失败即归因停止。
- [ ] clean0 后仅一次 `MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh full`（10800s工具预算），标准父selftest、single Maven 完整 reactor、同轮 risk reports/artifact proof、真实 HA25 场景全部实际；无 --pull never/--no-build 等 debug flags，不借 full6 reactor 或 native engine-info 顶替。
- [ ] 期间及结束后 no Git-visible writes，仅/tmp/标准 ignored 输出。及时独立小文件 checkpoint 原生 exit/窗口，保存 fresh XML/count/skips、manifest/artifact SHA、本轮 steps/scenario case IDs（排除旧 append 窗口）、owned cleanup、空间与完整输入稳定性。失败保留最早 native cause/阶段归属后停止，不盲 full8/源码修复；成功立即交父正式 quality 与独立 final implementation Review，不重跑成功 full 之外 Java/HA 风险 gate，不提前 T6 passing/归档/回填。

此节取代旧当前恢复入口；旧各轮历史、未知退出码及失败原件保留。运行后 checkbox 不回写；完整验收及独立 Accept 后方可另阶段纯状态关闭与 exact diff Review。无 Git commit、生产部署、POM/GAV/version/mirror 修改或新 loop。

## T6 最终正式 Block 与 R1 有限修复（当前入口）

Reviewer7caf 正式十二单元原 JSON 已从指定 session 最后 assistant final 解析保存 `/tmp/bolt-t6-final-block/full-response.json`，逐项核对 requestId、scope 及十二 unitFingerprint 后原样提交 ignored response，无投影、无语义修改。标准 `./scripts/verify.sh quality` 实际 exit0，仅表示合成成功；正式 snapshot **Block / 十二单元 D**，不是 Accept。三个 P2 mustFix 原文及分数、summary、verdict 全部保留。full7 原输入完整成功不改写，修复后不再是新输入 fresh acceptance；full1–6 历史与 full4 unknown cause 不抹。T1–T5 scoped 历史保持，T6 唯一 active/WIP=1，Loop4 PAUSED。

- T6-R1：active，本组只处理 native pool null selection 的 LOCAL 失败与 observed-task compare-remove。
- T6-R2：待独立修复，Gossip observationId/稳定证书身份部署配对，当前不写该源码。
- T6-R3：待独立修复，DEV Compose Gateway 下行配对，当前不写该源码。

精确源码范围仅 `im-plugin/im-bolt/src/main/java/com/co/kc/imchat/plugin/bolt/core/BoltAuthenticatedConnectionManager.java` 与既有 `im-plugin/im-bolt/src/test/java/com/co/kc/imchat/plugin/bolt/BoltConnectionLifecycleTest.java`；不新增 support/seam/反射/Clock/依赖。使用原生 manager 公开 selector API、真实已认证连接和 native RandomSelectStrategy，确定性暂停选择并退休旧连接；分别不发布/发布 replacement 后释放，验证本地失败、无业务发送、新代际不受损。不改身份过期 AUTHENTICATION、caller 预算，不 retry 或取消共享 task。

- [x] 原文标准 quality 在旧源码指纹下完成；readiness exit0；保存状态及两源码 before 原 bytes（含 untracked 计划）、全输入 manifest。
- [ ] 仅新增 `nativeNullSelectionFailsLocallyWithoutRetiringReplacement(boolean)` 两参数 regression；一次 RED `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltConnectionLifecycleTest#nativeNullSelectionFailsLocallyWithoutRetiringReplacement -Dsurefire.failIfNoSpecifiedTests=false test`（900s），要求两项为旧源行为断言失败，非编译错误。
- [ ] manager 最小 null 处理后同命令一次 GREEN（900s），按观察 task compare-remove，明确 LOCAL 保原因。
- [ ] GREEN 后一次 owning module `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test`（1200s）；记录每窗口完整输入 SHA、fresh XML、原退出码和日志，失败先归因停止。
- [ ] 冻结精确 diff 与证据 `/tmp/bolt-t6-r1-null-selection/RESULT.md` 交父独立 spec/quality；不称关闭三个 finding，不跑 full8/HA、不自行最终 Accept、归档或提交。

### R1 SPEC742807ce cleanup 窄修订（当前）

正式 SPEC742807ce Revise 仅针对新 regression 的 finally 所有权；生产 null 分支符合范围。只改既有 BoltConnectionLifecycleTest：保存 primary，assertAll 独立 worker/client/server 清理，cleanup suppressed 到 primary。另补 `nativeEmptyPoolRetiresStillRegisteredObservedTask`：公开 getConnPools/task.get/pool.removeAndTryClose 在 synchronized(actual manager) 内完成真实空池与同线程可重入 acquire，native CLOSE 无法抢先退休 task，明确验证 null 分支自行移除仍登记的 task。无 pipeline/反射/生产 seam。

- [ ] 仅临时删除生产 null 分支 compare-remove，运行一次 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltConnectionLifecycleTest#nativeNullSelectionFailsLocallyWithoutRetiringReplacement+nativeEmptyPoolRetiresStillRegisteredObservedTask -Dsurefire.failIfNoSpecifiedTests=false test`（900s），要求新增 case 行为断言 RED；保存 mutation 原件后原字节恢复生产。
- [ ] 恢复生产原字节后同 focused 命令一次 GREEN（900s），再 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test` 一次（1200s）。每窗输入冻结/fresh XML/exit 保存 `/tmp/bolt-t6-r1-null-selection/spec-revise/`。
- [ ] 冻结交父原 SPEC 复审再 quality；三 P2 正式 Block 不变，R2/R3 不改，不跑 full8/HA。生产最终 SHA 必须与本批 before 一致。

## T6-R2 Gossip 入站部署配对有限修复（当前入口）

R1 SPEC742807ce 与 qualityc1e12 最终 Accept/no findings，Bolt owning module 304 tests 全零；原正式 Reviewer7caf Block 不降级。T6-R2 是当前唯一实施子任务，R3 pending；T6 唯一 active、Loop4 PAUSED，full4 cause unknown 保留。不修改 semantic 分数，不运行 full8/正式 HA、不提交部署。本组完成后冻结交父独立 spec 再 quality。

复用 `BrokerGossipProperties` 不可变配对，`RegistryGossipConfiguration` 发布启动快照 Bean，Scheduler 与正式 `BrokerRequestConfiguration` 共用；`BrokerPeerAdmissionInterceptor` 只对 digest/delta 用 observationId→完整 URI exact match，再以 principal 稳定 brokerId 检查 committed。普通 BrokerPeerRequest 不走映射。DEV 只允许已有显式 development marker/trusted-network Gossip 分支，观察/探测/自动摘流拒绝不变。不从回包、Registry 或 Nacos metadata 学习授权。

- [x] readiness exit0；读取 Broker/plugin/test ownership、架构/README、O1/D12/D14 与7caf完整原文。保存 changed-file before 原字节（明确含 untracked 本计划）及全部 Git-visible hash 到 `/tmp/bolt-t6-r2-gossip-binding/`；df约3.5GiB，不复制源码全树。
- [ ] RED：新增 `im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/infrastructure/gossip/BrokerGossipAdmissionIntegrationTest.java`，实际两端 controlled mTLS，复用 `scripts/lib/routing-ha-tls.rb` 公共 create API 测试材料，正式配置 Scheduler→Synchronizer→Processor/全责任链→业务 Registry merge 与 tombstone；不同 observation/stable URI、missing/wrong observation/fullURI/noncommitted 在 handler 前拒绝，配置 mutation 不改变快照。`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=BrokerGossipAdmissionIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`（900s）。RED 必须是旧准入行为断言失败，不是编译/fixture失败。
- [ ] GREEN：仅上述三生产文件和 `README.md`；同步实际调用方 `IdentityAccessInterceptorsTest.java`、`BrokerObservationAdmissionTest.java`、`BrokerObservationRequestConfigurationTest.java` 及 `im-test/im-routing-ha/.../AutomaticFailureTlsHaTest.java` 构造，不保留兼容构造。相同 focused 命令（900s），补既有 `BrokerGossipPropertiesTest` startup fail-closed 回归。随后 owning module `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am test`（1800s），失败先归因。
- [ ] 加强 `scripts/run-routing-ha-scenarios.sh` 既有真实拓扑：有界轮询各 Broker `/management/brokers` 观察集合与 Gossip 成功记录，实际观察收敛而非只健康 HTTP；`scripts/test-routing-ha-harness.sh` 确定性正反例与 `bash -n`（每命令900s）。本组不实际重跑 full HA，后续同轮 full8 才证明新真实集群。
- [ ] 保存 exact.diff/sha/commands/exits/windows/reports/RESULT，集中回填 PROGRESS/本计划/active README 为 R2 implementation frozen、待独立 Review，R3 pending。冻结后不再 Git-visible 写入，不自行 Accept/关闭 T6。

R2 补充精确范围：既有 `im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/interfaces/scheduler/BrokerGossipPropertiesTest.java` 增 actual startup Bean 冲突/namespace 失败断言；原 bytes 由初始 SHA 核验保存。脚本合同直接执行正式只读 Ruby 算法，覆盖三节点注册、Gateway 删除及缺注册/陈旧删除/无成功 Gossip 的反例；不新增生产 API。原始首轮 fixture 路径错误与 Ruby2.6 语法/API 错误逐次保留并修正，不作为行为 RED。

### R2 implementation 冻结交父（待独立 spec/quality，不是 Accept）

本有限组生产修复与 focused/owning module 已完成，R2 仍待父独立 spec 再 quality；R1 双 Accept 历史保留，R3 pending，Reviewer7caf 正式 Block 不降级，T6 active/WIP=1、Loop4 PAUSED，full4 cause unknown 不抹。

- [x] O1 既有不可变 `BrokerGossipProperties` 由 `RegistryGossipConfiguration` 单启动 Bean 发布，Scheduler 与正式 admission 同源。仅 digest/delta 核对 observationId→完整真实 principal URI exact match，再按稳定 brokerId 校验 committed；普通 Peer stable-ID guard 不变。DEV 仅已有显式 marker/context 普通 Gossip 分支，观察 hard deny 保持。无新 trust source、配置/POM/依赖/兼容构造或 domain 反向依赖。
- [x] `red-behavior` 23.621s/exit1：6 tests/2 assertion failures/0 errors，旧源码合法 mTLS 不能收敛与 mutation 后合法调用被拒；此前 material 路径 fixture error 和混合 assertion/error 原窗口完整保留、不冒充行为 RED。最终 focused `focused-final2` 20.606s/exit0：38 tests 全零。真实两端 controlled mTLS、正式 Scheduler→Synchronizer→Processor/全责任链→Registry merge，注册双向及删除墓碑双向实际收敛；missing/wrong observation/full URI/noncommitted 在 handler 前0转发、meaningful delta0 merge。actual startup Bean 冲突/namespace 拒绝与 mutable config 不扩授权通过。
- [x] `module` 250.200s/exit0：197 fresh XML/1301 tests/0 failures/errors/skips，Broker789、Bolt267；独立窗口 Git-visible changed=[]。`ha-caller-compile` 7.281s/exit0，仅编译迁移后的现有 HA 构造 caller，不声称运行该 HA。模块后一次 guard test 候选只把既有测试配对换为不同观察名，10项 GREEN；该候选随后原字节撤回，最终 source 与 module-before SHA 完全相同，未把候选报告拼入模块结果。
- [x] runner 新增 bounded 45s 只读实际 Registry/Gossip 观察收敛前检，三节点 Broker observation 集合、Gateway 初始注册/优雅移除/恢复集合及成功 Gossip 原记录输出独立 evidence；不新增生产 API、不以健康状态或 WARN 消失代替观察。`script-red` 缺该算法 exit1；Ruby2.6 fixture语法/filter_map错误已归因修正，`script-final` 14.299s/exit0 正反例通过，`syntax` exit0。真实 Compose 新断言尚未运行，只由父后续同轮 full8 验证；不称新真实 HA/25场景已通过。
- [x] `clean` 17.861s/exit0；readiness 初始exit0、git diff --check0。完整 exact.diff/15-file before 原bytes（含 untracked 计划全文）、全输入 SHA、commands/exits/windows/fresh reports 与 RESULT 见 `/tmp/bolt-t6-r2-gossip-binding/`。仅复制改动 before 与小XML，未复制全源码/m2/JAR。最终三状态 after 单独记 SHA，不冒充运行冻结输入。

本次无新增工程约定：仅落实已批准 O1 与既有 immutable/startup/contracts/测试规则，因此未新增 Harness class exception、规则框架或 semantic 改分。无 full8、正式 HA、部署、commit、新 loop；磁盘最终约3GiB，下一次广门禁仍须重新实际 guard，不能保证容量。冻结后不再 Git-visible 写入，交父 source/结果复核。

## T6-R3 DEV Compose 下行配对有限修复（当前入口）

R1 双 Accept、R2 spec745 / quality4f7ca0da 最终 Accept/no findings 保留；Reviewer7caf 原正式 Block 不改写。T6 唯一 active，当前唯一实施子任务 R3，Loop4 PAUSED。

精确范围：`deploy/local/compose.bolt-dev.yml`、`deploy/local/README.md`、`scripts/test-bolt-dev-contract.sh`、既有 `im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/infrastructure/config/GatewayDevelopmentTargetConfigurationTest.java`，以及 PROGRESS/本计划/active README 三状态文件。不改任何生产 Java、namespace 校验、principal/注册/Gossip/lease/观察授权、R1/R2 源码或 HA runner。

- [ ] readiness（已实际 exit0）与 changed-file before 原 bytes（明确含未跟踪计划）、全输入 hash 保存 `/tmp/bolt-t6-r3-dev-compose-pairing/`；先登记本范围再写代码。
- [ ] 扩现有 shell 合同，实际 Compose 渲染 pair 必须对应 Gateway host/实际配置端口，含缺 pair/wrong endpoint 反例；旧 Compose `bash scripts/test-bolt-dev-contract.sh` 必须行为 RED。
- [ ] 扩既有 owning test，从真实 Compose JSON 环境经 Spring ConfigData/Environment binding 得到 BrokerProperties，通过正式 ClientConfiguration 及实际 GatewayClient write/close 捕获 typed target；缺失/错误地址在 invoker 前拒绝。旧 Compose focused 必须行为 RED。
- [ ] Compose 仅显式配置 indexed environment address/完整固定 namespace URI，README 准确说明 synthetic descriptor 不改 trusted-network principal；同合同和 focused GREEN。focused 命令：`mvn -q -o -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=GatewayDevelopmentTargetConfigurationTest,GatewayClientTest,BrokerObservationAdmissionTest -Dsurefire.failIfNoSpecifiedTests=false test`（900s），RED 仅选 GatewayDevelopmentTargetConfigurationTest。
- [ ] `bash -n scripts/test-bolt-dev-contract.sh`、`git diff --check`；保存每窗口 commands/exits/windows/fresh XML/count 与 Git-visible changed=[]，冻结交父 spec 再 quality，不自行整体 Accept。

父批准本组小体积编辑、shell 及 cached focused Maven 当前约2.974GiB继续；focused 前复查至少2GiB，离线无 package/下载、预计增量小于200MB。3GiB guard 仍用于后续 full/真实HA，不改仓库规则。本组不执行 full8、真实 Docker 启动、module/package 或清理缓存，不提交部署。

### R3 implementation 冻结交父（待独立 spec/quality）

R1 双 Accept、R2 spec745 / quality4f7ca0da 最终 Accept/no findings 保留；Reviewer7caf 原正式 Block 不改写。T6 唯一 active，R3 当前 implementation frozen，Loop4 PAUSED。

- [x] readiness exit0，七文件 before 原 bytes（含 untracked active plan）及全输入 hash 保存。
- [x] shell 旧 Compose 行为 RED 1.426s/exit1；配置修复后完整合同 GREEN 6.804s/exit0，实际渲染 pair 对应 Gateway host/正式端口，缺 pair/wrong endpoint 反例拒绝。
- [x] 首轮 fixture 使用源名 compose 未触发 Spring env mapper，旧/候选均失败；原窗口保留为 fixture gap，不冒充行为证明。按实际 Spring API 改为 compose-systemEnvironment 后重新获得旧 Compose focused RED 12.492s/exit1，3 tests/2 assertion failures/0 errors。
- [x] Compose 仅两个 indexed env 键，配对172.30.88.11:18012与spiffe://im-chat/gateway/bolt-dev-gateway；README准确记述 descriptor/marker 区别，生产 Java 零改动。
- [x] 正式 ConfigData/Environment indexed binding + ClientConfiguration 实际 controlled owner + 实际 GatewayClient write/close target capture；仅末端 invoker spy 截断网络，缺失/错误 endpoint 在 invoker 前拒绝。最终 focused 10.988s/exit0，3 fresh XML/20 tests/0 failures/errors/skips（configuration3、既有SDK6、既有observation11）。未拼R2 module1301或旧报告，各窗口Git-visible changed=[]。
- [x] bash -n 与 git diff --check exit0。证据 `/tmp/bolt-t6-r3-dev-compose-pairing/RESULT.md`，exact.diff/sha/commands/exits/windows及fresh XML原件保留，状态回填 SHA 与运行输入分开。

父明确3GiB是full/HA重型预算；本组约2.974GiB开始，focused前均大于2GiB且cached offline，无package/下载、module/full8/HA/Docker up或缓存清理。未运行clean/full，有限组交父决定broader gates与spec/quality，不自行整体Accept/T6passing/归档。不是实际DEV传输证明，不改principal/注册/DTO/Gossip/lease/观察hard deny，不改R1/R2源码、HA25、依赖、Loop或score，无提交部署。

## T6 第八轮标准完整验收冻结入口（当前）

此前各当前入口均为历史，本节唯一有效。R1 null selection LOCAL/observed-task compare-remove，SPEC742/qualityc1e12最终Accept，module304全零；R2 observationId→完整URI及committed、共享immutable配置，SPEC745/quality4f7最终Accept，focused38/module1301全零；R3 DEV Compose pair，SPECb4/quality9e21最终Accept，focused20及shell合同exit0。三组仅局部闭环，原7caf正式十二单元Block/三mustFix/评分保留。R3正式binder/末端target捕获不是实际DEV网络传输；R2新增真实Gossip注册/删除收敛尚待full8。full1–6失败/incomplete/未知exit不改，full7旧输入成功不是postfix fresh；full4具体cause仍unknown。

实际df约15.12GiB满足原3GiBguard并超过5–8GiB建议，不是容量保证。Docker响应正常；格式化image inspect与ls曾矛盾，随后absolute executable原始argv四canonical tag及Redis ID inspect全exit0，旧错误保留。不新增必须全缓存gate，标准Compose按原固定tag实际决定pull。无pull/tag/prune。

唯一writer只集中更新PROGRESS、本计划及active README后冻结。证据 `/tmp/bolt-t6-acceptance-attempt8/`：三状态before原bytes（含untracked计划）、exact-state.diff、全Git实际枚举SHA/smallinputmanifest、HEAD/ignore/Loop完整SHA；旧sameSHA原bytes仅作source identity引用，不复制全树/m2/JAR。用户tracked.gitignore纳全输入，.pi/Loop/quality exclusions不变。

- [ ] 保存本仓无Maven/full/helper/Compose writer、实际df、Docker/四tag及owned资源/registry事实；旧八session正常inactive/done，full5人工archive原active保留，外仓不全局等/kill。
- [ ] 冻结后顺序 `./scripts/verify.sh readiness` → `./scripts/verify.sh clean`，各actual0且全输入稳定才继续，失败先归因停止。
- [ ] 仅一次 `MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh full`（10800s）。单父selftest、完整single Maven reactor、同轮freshreports/artifact proof、真实HA25及新增Gossip注册/删除收敛必须实际执行；无offline/debug flags/跨runproof。
- [ ] 期间及结束后no Git-visible写入，仅/tmp/ignored输出；及时小checkpoint原exit/窗口，记录fresh模块counts/skips、同轮proof/场景/cleanup/input changed。失败first exact cause后停止，不盲full9/源修；成功及时交父并冻结，不回写checkbox/T6passing/归档/分数，父新正式全scope quality request及独立final implementation Review必须完成。成功后不额外重复Java/HA风险gate。

T1–T5 scoped passing、T6唯一active/WIP1、Loop4 PAUSED；无commit/deploy、镜像tag/platform/POM/GAV/版本/checker/PKI改变。完整验收和独立Accept后另阶段纯状态closure。

T3 追加已批准 Peer 身份 URI 前缀全仓命名迁移；唯一实施者顺序执行窄验证，完整验收仍暂停。
