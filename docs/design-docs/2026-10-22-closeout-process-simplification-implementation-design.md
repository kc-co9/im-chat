# 收尾流程精简实施设计

> 状态：已批准，实施中。独立 reviewer `905aa25e-9499-454` 修订后 Approved；用户已批准[收口提案](2026-10-22-closeout-process-simplification-design.md)的保留／延期边界。S1/S2 代码及聚焦验证已完成，S3 文档与一次真实 full 验收未完成，不承诺实际性能收益。旧成功/失败记录不删除、不升级，A5 仍须真实验收。

## 收口范围

保留缓存测试修正、ps/wait原退出码修复、确定性采样检查、HA Engine前检/步骤诊断/有界安全清理，以及完整Git指纹冻结、独立Review。停止新增Harness平台功能。

撤下本轮新增的full链、--through/--resume、archive-audit prepare/verify、全模式journal/supersession及候选发布功能。闭包包含verify入口、report/handoff archive消费者、实现输入指纹及专属fixture调用、规范中的归档特权。对应新helper/fixture按精确清单留样至 `/tmp` 再从工作树移除，不操作其他用户文件。原 `.harness/full-chains`、runs、archive-audits、review响应和日志全部保留为历史，旧schema不能支持当前通过。没有Git reset/整文件restore。

## 最终执行顺序

唯一最终入口仍为 `./scripts/verify.sh full`：

1. readiness、clean（含drift）。
2. 父Harness自测一次。
3. **一次 `mvn -q verify`**，输出写 `.harness/runtime/full-maven.log`，保持Maven原失败码；从Maven开始之前记录本轮epoch和完整指纹，之后复核冻结。
4. 从同轮Surefire报告校验Architecture、startup-smoke完整映射/Nacos隔离、behavior/e2e非零及通过，记录完整模式证据的来源。这里不再运行Maven。
5. 一次真实HA，复用刚刚通过reactor的聚焦场景JUnit报告。真实Compose拓扑、Gateway/Broker重启、25场景映射和清理全部保留。任何前置失败不启动容器。
6. 记录full实际测试汇总、报告与handoff；独立quality和cleanup按原入口执行。

quick仍是迭代入口，不在同输入最终full之前强制执行；如果已运行quick，可以用于定位，但不是再次全套的前置。独立architecture/startup/behavior/e2e/routing-ha入口继续可用，用于开发与窄层诊断；不得要求完成full前后把这些入口逐一重跑。

## 同轮报告与独立证明

只引入专用于当前reactor报告的校验逻辑，不做跨运行缓存、module推导或chain管理。Maven前枚举并删除本轮必需XML和Compose所用Broker/Gateway两个exec JAR，确认删除成功；同秒旧文件也不能替代新执行。epoch严格为正、有限且不晚于当前时间的本次起点，拒绝0/负数/NaN/未来时间，父full直接传入，不读取历史参数。启动脚本拆出“生成/校验manifest”与“运行Maven”，增加明确的 `--reports-since <epoch>` 模式：前者保留应用唯一映射与Nacos隔离检查，后者只接受相应测试本轮报告（mtime至少为Maven起点）。独立无参数行为保持。

behavior/E2E/architecture校验从当前源码枚举固定类型/tag的测试类，按完整classname定位报告；不可只扫描当前target任意XML累计。要求每个必需报告存在、本轮时间、不为零且无failure/error；跳过记录不当作必需断言实际执行。源码映射无法可靠解析则明确失败，不猜测覆盖。E2E日志引用同轮full-maven.log，不伪装成单独E2E重新运行。HA manifest使用当前源码唯一解析完整classname，方法selector必须命中实际执行且无skipped/failure/error的testcase，整类selector不能用部分成功掩盖任一必需testcase跳过；歧义/无法解析失败。去重前检与最终25场景映射使用同一规则，不继续只按短类名和suite状态猜测。

full在这些校验成功后为对应模式生成正常verification记录，保留同一HEAD/完整fingerprint、实际执行窗口及 `evidenceSource=full-reactor`、报告路径/摘要、日志路径；不生成独立命令已执行的假记录，command明确是full的同轮报告校验。归档前所有模式仍必须经过真实同轮执行与验证；report/handoff不改变freshness。

HA runner默认仍自行package并运行聚焦测试，保持独立调用完整能力。仅full内部传入本轮reactor报告起点时，runner跳过重复package和五轮聚焦Maven，先验证所需应用可执行JAR存在且不早于本轮、场景manifest所有JUnit selector对应本轮有效报告，再运行真实拓扑。记录 `evidenceSource=full-reactor` 与起点；不接受缺失/非法参数。reactor校验成功时写本次临时清单，固定所用XML和两个exec JAR路径及SHA256；HA消费前及最终结果发布前复核。文件被覆盖/缺失则失败，不在源码冻结之外默许target变化。清单仅属于此次full，不持久化成跨运行缓存。此选项不是跨运行恢复或跳测试通过开关：父full必须先得到Maven退出0和完整内容冻结；runner自身仍验证具体报告/制品，失败不启动容器、不填25条成功。

本机Git外依赖或主机时钟不在此方案获得新的可复用保证。复用严格局限一次full进程内：源码冻结和同轮报告证明必要断言执行；没有旧结果被追认为fresh。

## 归档政策

撤下复杂归档特权后不恢复“改状态就重复full”的循环。以明确两时点关闭规则替代：全部任务passing所依据的是归档前同一冻结输入的真实完整验收及独立Accept；归档后只允许 `PROGRESS.md`、active/completed计划及这两个目录索引变化，实际执行drift、clean、quality scope校验和cleanup/handoff。原full保持历史时间/完整指纹，归档后stale照实展示；不写新的full passed。

迁移前由独立Reviewer核对精确归档差异和原验收/Review记录，迁移后再次核对只有上述状态路径变化。这个步骤是受控的人工Review，不新增基线复制、journal或发布状态机。既有Git-visible包含非状态变化则重新验收，不能泛排除docs。后续发布需要当前环境复测时另外执行，不将归档Review解释为环境健康。

这条规则必须同时同步AGENTS、PLANS与HarnessGuide/矩阵；机械drift校验路径/任务结构，范围差异/原验收归属暂为Review-only。自动化升级条件为出现可靠、低成本的归档差异正反fixture且不重新引入chain/journal，不为A5加一次性类名例外。

## 失败与输出

原命令退出码优先，运行中任何完整输入变化都失败，包括仅状态文件变化；Git不可用不能生成成功。取消终止实际命令组并回收，保留已修HA自己的监督语义。普通full记录 `gateStatus` 与 `finalizationStatus` 可保留为直接字段，但不要求链文件或journal；后处理失败非overall成功。report/handoff正常生成不读取已延期archive结果。

保留一个简单仓库构建互斥（可内联小函数或移到已有evidence库，不依赖延期phasehelper），防止同仓并发构建覆盖target。不给readiness/只读报告加不必要互斥。

## 精确实现边界

- 入口/证据/消费者：`scripts/verify.sh`、`scripts/lib/harness-evidence.sh`、`scripts/harness-report.sh`、`scripts/harness-handoff.sh`。
- 去重证明：`scripts/harness-startup.sh`、专用 `scripts/lib/harness-reactor-evidence.rb`（固定报告校验，不作通用schema框架）、`scripts/run-routing-ha-scenarios.sh`、`scripts/routing_ha.sh`（仅注明同轮来源，保留Engine/清理）。
- fixture：父Harness、startup、handoff、load/capacity中的复制列表，新增或合并同轮报告契约fixture；保留HAprocess与cache/ps/采样反例。
- 移除工作树新增文件：`lib/harness-verification-phases.sh`、`lib/harness-archive-audit.rb`、`test-harness-verification-evidence.sh`、`test-harness-verification-phases.sh`、`test-harness-archive-audit.sh`、`test-harness-archive-journal-lifecycle.sh`。全部先留样，不删运行证据。
- 文档：AGENTS、README、PLANS、HarnessGuide/矩阵、设计索引/旧设计状态、两active计划及PROGRESS。

## 验证合同

先RED/GREEN隔离fixture证明：full顺序/自测1次/reactor1次；Java失败容器调用0；同轮报告/应用映射/tag/非零/时间/失败/跳过拒绝；同秒旧XML未刷新、目标方法skipped而同类其他方法passed、epoch非法/未来、XML/JAR摘要在消费前变化均拒绝；旧full扩展参数必须明确返回2；HA standalone保持原能力，内部同轮模式缺report/jar拒绝且Maven调用0；后处理失败原码/锁/指纹冻结保持。grep检查无延期helper硬引用，历史文档不当可执行入口。

先语法/fixture/独立实现Review，最后只运行 `full` 一次＋quality/cleanup，不能再人为串quick/architecture/startup/behavior/e2e形成重复。测试全绿不代表Docker内部根因已修；首次新流程验收失败停在对应层。归档只做状态治理和Review，不重新Java/HA。

## 风险、取舍与延期

最大风险是去重后遗漏专用证明，因此先以缺报告、错映射、零执行和旧时间反例保护，不靠“reactor包含模块”推定。Maven先行改变故障顺序但不减少断言；一次full后HA只追加真实运行证据。移除平台能减少维护面，但不保证长测试本身无偶发失败。通用恢复、缓存、跨机器及全模式事件历史均延期。

## Review

首轮 reviewer `905aa25e-9499-454` Revise三项：mtime同秒旧文件、selector精确执行/跳过、target摘要采集与复核时点。已补Maven前清理必需输出、严格epoch、源码唯一完整类名与共享testcase规则、本次XML/JAR摘要清单，第二轮同reviewer Approved，三项closed。此处为原设计 Review 结论；当前 S1/S2 已实施，S3 及真实 full 验收尚未完成，不新增 chain 或缓存。
