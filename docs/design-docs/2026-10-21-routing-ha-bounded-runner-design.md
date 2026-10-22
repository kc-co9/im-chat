# Routing HA 前检与有界清理设计

> 状态：已批准，战术修复保留。Engine 前检、命令边界、有界安全清理与会话进程所有权属于[精简实施设计](2026-10-22-closeout-process-simplification-implementation-design.md)保留范围；不随阶段/归档平台撤下，不改变25场景、900秒期限或失败事实。真实新流程验收尚未完成；下文保留原设计与 Review 历史。

## 背景与边界

当前Docker Desktop的服务端曾不响应本地_ping，官方停止90秒超时，用户授权强制重启应用后毫秒级恢复。不能据此证明Docker内部死锁机制，也不能从历史E2E XML生成推断Maven进程已退出。原runner先付构建/Java成本、缺命令起止标记；cleanup先logs再down均无期限，父超时0.2秒后KILL导致清理被卡住。这些代码事实可独立修正，避免依靠另一次full取证。

本轮不新增阶段缓存、依赖/镜像迁移、自动重启Docker或代理变更，不以Engine恢复或无资源宣称HA通过。

## 固定执行契约

1. 实际runner在Maven及Compose资源创建之前执行 `docker info` 有界前检，默认8秒，非零/超时直接失败，不执行构建。纯插件version或compose config不作为Engine健康证明。预检前不登记“可能创建”的项目；状态目录仍保存诊断。Engine检查不证明后续持续健康。
2. 显式步骤边界写stdout及本次 `diagnostics/steps.jsonl`，包含固定step名、started/finished、UTC时间、原退出码；固定命令数组不打印秘密环境或任意shell拼接。必须覆盖package、各focused Maven（尤其E2E）、compose config/up、就绪/一致性、stop/start、证据映射及cleanup。测试完成与Maven返回可区分；某步骤只有started就能定位未知结果，不填假finished。
3. 共用小型有界命令工具仅用于Engine检查、cleanup诊断和down：单调时钟期限，超时124，保留非零码；TERM/INT转发给自己的子进程组并回收，不能孤立留下Docker CLI。固定命令由调用方参数数组传入，不eval。不把sleep循环放Bash；等待由Ruby监督。
4. cleanup单次执行，禁止EXIT/TERM递归。logs最多5秒，即使失败仍尝试本次唯一Compose project的down，最多30秒；down成功才清账本。logs失败可记诊断而不冒充清理失败，down失败/超时保留账本并非零。Engine前检失败且从未尝试创建资源，则不调用logs/down，明确未创建，不把未知资源当成功清理。
5. 主执行仍900秒。父监督到期标记timeout并TERM本次场景进程组，保留原124；额外最多45秒只允许终止/清理，不能继续场景或计成功。监督者等待退出，有必要时到清理宽限结束KILL并回收。正常失败原码优先，正常成功但down失败返回清理码。执行预算与清理宽限分开记录，不把宽限当延长场景通过条件。
6. 外部TERM/INT同样停止场景并有界清理，记录原因signal/timeout，不凭124推断外层取消。若退出后仍有账本/PID残留，原失败证据保留，禁止写成功detail。自定义scenario测试仍使用原接口，不引入新服务或通用任务框架。

### 进程所有权与取消

外层shell仅启动父Ruby supervisor；supervisor单独创建场景shell进程组并持有其PID。场景的每个外部命令均经同一小helper监督：helper留在场景组，命令另建自有组，PID/PGID登记到本次state私有文件，helper负责转发取消并回收。步骤工具可无运行期限（仍受父900秒限制），Engine/logs/down使用具名期限；不允许绕过helper留下无法终止的Maven或Compose子组。

父supervisor锁存首次取消原因；执行期限到固定124，外部INT/TERM固定130/143，转发一次TERM给场景组。场景signal trap明确设置退出码并退出，EXIT唯一入口做cleanup；进入cleanup后忽略重复INT/TERM，不再次触发cleanup或转发到清理helper。cleanup可以启动logs/down，但绝不返回主流程。外层shell也将首次信号转发给supervisor并等待其有界终结，不让其脱离工具调用存活。

helper先写拥有的命令组登记，再开始等待；到期或取消先TERM，短终止窗口后KILL该组，即使直接命令已退出也核对拥有组的存活并处理孙进程；回收直接子进程后才清登记。终止窗口默认1秒、在具名命令总预算内预留，预算结束即最终KILL，不无限wait。若无法证明拥有组已消失，保留PID登记并非零，不能清账本冒称成功。

父45秒是从第一次取消起整个活动命令终止及cleanup尾程的总上限。场景shell退出后仍核对本次helper登记的组，不凭shell退出宣称清理完成；到尾程上限只终止本次登记组与场景组，并回收直接子进程，不扫描或操作其他用户进程。原deadline124或signal130/143优先于清理失败码。

Engine前检8秒、logs5秒、down30秒、helper终止窗口1秒及父45秒清理宽限可用具名ROUTING_HA_*参数覆写以测试短预算，均为正数并校验；主900秒配置保留原语义。具名命令期限必须大于终止窗口；宽限至少覆盖活动helper终止窗口＋logs＋down＋监督开销（默认至少2秒），否则参数拒绝，不能默默截断已承诺清理。

## 验证与交付

先新增stub反例：Engine挂住快速124且Maven/Composeup调用0；正常Engine后先E2E完成再up；挂E2E能从边界定位；logs挂住但down仍执行；down失败账本保留；正常成功25场景/空账本；总期限超时保留124且宽限内trap结束；信号不重复cleanup且无监督子进程残留。测试参数覆盖秒级，不执行真实Maven/容器，不仅断言字符串。补忽略TERM、带孙进程、helper运行时外部取消与cleanup重复TERM/INT；通过就绪握手发送信号，断言原退出码、cleanup恰好一次、logs超时后down被执行、已登记PID/PGID无存活、失败无成功detail，不靠固定sleep猜时序。

独立Review后更新计划H5精确文件scope，实施于 `scripts/run-routing-ha-scenarios.sh`、`scripts/routing_ha.sh`、小型bounded helper和现有/新增专属stub fixture；同步HarnessGuide与规则矩阵。不改其余业务代码或放宽规则。聚焦通过后只执行Engine只读前检及单个真实HA，不直接full；真实HA失败停止在步骤日志指出的命令。

## 取舍与恢复

仅增日志不解决无响应Engine浪费成本与清理阻塞；仅增Engine前检不能覆盖运行中失联。采用三项最小配套，不自动修复Docker。额外45秒仅用于资源回收，代价为失败命令可能稍晚返回，收益为不再0.2秒强杀清理。可回滚脚本，不清历史失败。完成后首次新基线仍需要真实完整验收，归档审计不能替代。

## 决策与 Review

- 用户要求根因取证后继续，仅处理已证runner缺口；Docker内部具体故障与历史命令边界未知保留。
- 原900秒/25场景/清账本成功条件不变；失败无重试。
- 首轮独立reviewer `87a86911-c812-4ed` Revise：进程拓扑/取消传播、强制终止尾程及子组回收两项；已补显式所有权、锁存信号/一次清理、预算内TERM→KILL、登记组检查及握手反例，第二轮同reviewer Approved，两项closed，不代表实现通过。
