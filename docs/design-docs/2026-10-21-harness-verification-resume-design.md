# Harness 验证输入隔离与阶段恢复设计

> 状态：已被取代／延期。当前契约见[精简实施设计](2026-10-22-closeout-process-simplification-implementation-design.md)：阶段链、恢复、实现输入指纹例外与通用缓存不属当前交付；完整 Git 输入冻结保留。下文为历史批准方案和 Review 记录，不作为可执行能力承诺，旧失败状态不变。

## 后续范围调整

用户批准优先[归档差异审计](2026-10-21-harness-archive-audit-design.md)，延期尚未实现的通用阶段成功复用与消费依赖闭包采集；本设计后文保留原批准方案与Review历史，不再据其启动阶段缓存实施。H1输入指纹、H2分段执行/attempt/互斥已实施但尚未整体验收，H3仍未通过。新方案独立Review完成前不修改现行门禁政策。

## 摘要

将“运行时输入冻结”与“运行结束后的证据复用”分开。任何验证运行期间仍检查完整 HEAD 和工作树指纹；仅在运行结束后，明确列出的代码验证阶段允许忽略计划状态文件变化。drift、clean、readiness 和归档状态审计始终检查当前状态，不能复用旧治理结果。

将 full 拆成原有顺序的阶段证据，支持显式恢复同一验证链。成功且输入、配置、制品及产物仍有效的阶段可复用；失败、中断、缺失或不认识的证据必须执行或拒绝恢复。恢复产生新 attempt，不把旧失败改成成功。

本方案减少重复执行，不减少断言、场景数量、环境清理要求或超时约束。不新增服务、数据库、调度器或依赖框架。

## 背景

`scripts/verify.sh` 的 full 顺序是 readiness、clean、Harness 自测、routing HA、全仓 Maven verify。当前只有 full 整体记录，没有阶段 checkpoint；晚阶段失败无法表达哪些前置成功可以继续使用。

`scripts/lib/harness-evidence.sh` 将 HEAD、所有 Git 可见文件的内容、路径、执行位和 symlink 编入完整指纹。计划归档、PROGRESS 和索引变化也会使既有实现验收 stale。当前 `docs/PLANS.md` 因此要求归档后刷新相应机器验证，重复编译、打包和容器演练成本高。

已有 `load-test-evidence.sh` 使用状态文档排除指纹，但只属于压测路径，不能直接套给 clean 等真实读取状态文件的检查。当前 report 和 handoff 使用完整指纹，必须一起调整消费者，不能让同一证据在不同出口无解释地 fresh/stale。

历史日志包含磁盘耗尽、截断覆盖率文件、测试偶发失败以及退出124。`routing_ha.sh` 自身有900秒期限、超时返回124；外层工具也可能取消子进程。不能仅凭124确认取消来源，更不能靠恢复机制隐去失败。

## 目标与非目标

- 归档状态变更后只重跑治理检查，不重复有效的代码／运行验证。
- full 中断后支持显式、可审计的阶段恢复，不再依赖聊天记录决定跳过哪些命令。
- 保留完整来源指纹、失败历史、真实执行与复用区别，以及 report/handoff 一致性。
- 不做模块自动推导、自动重试、后台轮询、远程证据缓存或 CI 调度迁移。
- 不解决 Broker 首次复制偶发根因、私有 JRaft 发布、未来认证迁移或 Docker 全局运维问题。
- 不自动将 A5 旧证据升级；改造后仍需一条符合新契约的成功验证链。

## 已知事实与约束

所有权属于仓库 Harness：`scripts/verify.sh`、证据库、report/handoff 和脚本 fixture。业务源码及测试语义不变。

复用只接受同一 HEAD、本机、显式验证链中的本版本记录。不同 commit 暂不复用，即使文件内容相同。Git 不可用、指纹 unavailable、未知 schema、配置不明或依赖身份不能确认时失败关闭。

`.harness/` 是忽略的本机证据，不是防恶意篡改的安全存储；摘要用于检测过时、错配和意外修改，不宣称密码学可信执行。证据路径必须限定在仓库 Harness 目录内，不接受任意外部执行命令或路径穿越。

## 方案概览

```text
full（新链）/ full --resume <chain-id>
  → 当前 readiness、clean
  → Harness 自测：执行或复用有效成功
  → routing HA：执行或复用有效成功
  → Maven verify：执行或复用有效成功
  → 新 attempt 汇总
  → report、handoff

仅归档状态变化
  → 当前 drift、clean、计划／链接审计
  → 校验既有实现证据仍有效
  → quality scope 校验、report、handoff
```

readiness/clean 每次真实执行，不进入可复用集合。阶段顺序不改；复用不是省略验收条件，而是引用此前已执行且仍有效的同一阶段证明。

## 详细设计

### 两类指纹，不全局替换 freshness

保留完整 `worktreeFingerprint` 和起止 HEAD 检查，继续负责审计来源与运行冻结。新增版本化 `verificationInputFingerprint`，编码规则与完整内容指纹一致，仅排除以下状态路径：

- 根 `PROGRESS.md`；
- `docs/exec-plans/active/**`；
- `docs/exec-plans/completed/**`。

上述范围包含两个计划目录的 README。history、design-docs、规则指南、AGENTS、源码、测试、POM、脚本、部署和普通 README 一律包含；不新增泛化 `docs/**` 排除或类名例外。HEAD 另行要求相同。

仅允许复用的阶段和它们的 full 汇总使用实现输入判定。readiness、clean、drift 仍使用完整当前输入；quality 继续使用已有语义 scope，不沿用实现指纹替代 Reviewer。首版独立 architecture/startup/behavior/e2e 保持完整 scope，不扩大复用范围。归档后按当前文档重新评估治理；实现未变时不强制重复这些独立运行，但其完整 scope 已过时就单列 stale，不能标为 passed。full 只有显式测试清单及执行证明覆盖某项要求时才能证明该项覆盖，不能据阶段名称推定覆盖，更不能隐式覆盖独立模式失败。

运行期间即使只改 PROGRESS，也使该次运行不稳定，不产生可复用成功。状态排除只允许“完成之后的治理变化”，不允许运行中编辑。

### 显式验证链与 attempt

`full [--through scripts|routing-ha|maven]` 创建新链并输出 chain ID；`full --resume <chain-id> [--through scripts|routing-ha|maven]` 只恢复指定链。`scripts` 对应 Harness 自测，三个值是固定顺序的停止边界，默认 `maven`；不是新增任意阶段 API，也不是“找到最近 passed 就跳过”。例如 `full --through scripts` 保存前段链证据，后续必须显式带该 ID 恢复。到达部分边界且后处理成功可退出 0，但只表示 stage complete，不生成 full passed；只有到达 `maven` 且整链满足要求才可 full passed。

链及 attempt ID 由 Harness 生成并限定安全字符。仓库级执行互斥锁保护共享工作目录、`target` 与本地安装产物，链记录锁保护该链证据写入；不同链并发也立即拒绝。所有触及这些共享输出的入口（包括独立模式）必须协调同一执行锁，不能只锁 resume 入口。HA state、detail 和证据路径均按 attempt 唯一分配，避免旧状态串入。

成功阶段记录至少包含 schema/version、chain/attempt ID、阶段枚举、固定命令身份、起止时间、原始退出码、起止 HEAD/完整指纹、稳定标记、实现输入指纹、配置与依赖摘要、产物清单及摘要。写入先临时文件再原子替换；缺字段、错误类型、错误链、未知阶段和版本拒绝。失败记录也持久保留；强制结束留下 incomplete，不因为只有部分 JSON 就视为成功。

每个恢复 attempt 重新运行 readiness/clean，逐阶段判断：允许集合内且此前成功、冻结有效、当前输入匹配、依赖/配置/产物未变才引用；否则实际执行该阶段。实现输入变化时拒绝继续旧链并提示创建新链，避免同一链悄悄混合两套代码。

首版按 scripts → routing-ha → maven 线性失效：任一阶段需要实际重跑，就使该阶段之后的旧成功全部不可复用，按顺序执行；不引入 DAG 或精细依赖调度。

阶段执行后立即再次验证输入。失败或未知结果停止，不执行下游。取消来源只在有观察证据时记录为外层取消或阶段 deadline，否则标为 unknown；不能由124推断。

### 配置、依赖与产物

复用键绑定阶段实际生效的输入，不只记录默认命令。采集契约如下；秘密仅保存摘要，不记录原值。

| 输入 | 必须绑定的身份 |
|---|---|
| 工具 | 解析后的可执行文件路径、版本，含 JDK、Maven、Docker/Compose |
| Maven 配置 | 实际 global/user settings（含显式 `-s`、`-gs`）内容摘要、仓库路径、参数、effective POM、`.mvn` 配置及 `MAVEN_OPTS` 等实际覆盖 |
| 环境 | 实际消费的 `MAVEN_*`、`JAVA_*`、`ROUTING_HA_*`、代理及测试覆盖；敏感环境值保存摘要 |
| 外部入口 | `SCENARIO_COMMAND` 的实际命令与所引用外部脚本内容摘要，不只记录路径 |
| 容器 | Compose effective config 摘要、实际 image ID/digest；不能仅绑定同名 tag |

每阶段 preflight 使用相同配置生成 effective POM 和已解析的 dependency/plugin 清单，纳入实际消费的传递依赖、父 POM、插件及其依赖的精确 JAR/POM 路径与内容摘要，以及实际 settings 摘要，形成 consumed manifest；不对整个 `.m2` 做摘要。无法完整确认消费闭包（含动态解析、未知外部入口或配置）时，禁用该阶段复用，不能猜测清单，也不为此新增环境服务或解析框架。

manifest 在阶段执行前冻结；执行结束核对实际消费与冻结清单一致、同一制品未在执行期间被替换，否则不产生可复用成功。无法可靠核对也禁用复用。恢复前重核清单；后续阶段仅添加无关依赖不使前段失效，修改前段消费制品则使其失效。

仓库 reactor 自身 install 输出与外部依赖分开记录为 produced artifacts，绑定生产阶段及产物摘要；不能把本阶段预期生成的产物误当成外部冻结输入。下游消费这些输出时引用生产阶段及摘要；生产阶段重跑或产物变化，下游必须重跑。无法区分来源时禁用复用，不用旧本地安装包猜测来源。

产物保存到链内独立路径，不依赖后续被覆盖的 `target/surefire-reports`：

- 自测保存退出码和必要结果证据。
- HA 保存该阶段自己的场景清单、运行输出与清理证明；25个场景、当前执行的testcase证明和ledger清空要求不变。缺资源文件视为证据不完整，不能等同空ledger。
- Maven保存阶段自身开始时间之后的报告快照与汇总；恢复时不能扫描现有target拼出旧结果。

配置或依赖变化使相关阶段及其下游失效，不能把成功后修改制品的环境当作同一验证输入。跨attempt消费只读产物，文件缺失/摘要改变拒绝复用。证据保留由已有cleanup allowlist管理，禁止借本任务删除其他会话状态或Docker资源。

### 汇总与报告

新 full attempt 分别列出真实执行和复用阶段、来源 attempt、输入 scope、停止边界及判定原因。只有当前治理检查、所有必需阶段及后处理完整成功时，才生成该 attempt 的 full 成功汇总。当前报告显式指向最新 attempt；传统 `full.json` 仅作该索引，不能因最新失败或部分完成退回展示旧成功。历史记录不可变并保留来源引用。

新 attempt 只有完整成功才能标记 supersedes 同链旧失败；旧失败原记录仍保留。跨链不 supersede，独立必需模式的失败继续阻塞，不能由 full 成功或复用静默消除。缺失、部分完成、失败和后处理失败均不能产生 overall pass；stale 与 passed 分列，不能把“无需重跑”写成“已通过”。

report、handoff、quality机器消费方统一理解版本化scope，显示“当前状态校验通过／实现证据有效／哪些阶段复用”，不把部分有效写成整体fresh。旧schema记录保留历史，不能自动获得新scope资格。

后处理失败仍返回非零，并分别显示 gateStatus 与 finalizationStatus；不能只看到full passed就忽略report/handoff失败。detail只接收当前attempt的白名单业务字段，不允许覆盖外层status、指纹、阶段ID或退出码。

## 可靠性与安全

不改变HA的900秒、用例等待期限、测试断言或失败传播。调用方应为单阶段设置足够外层预算，不再把整组长命令塞入不足的2400秒工具窗口。

环境探测每次执行，复用不代表服务持续可用，也不构成生产容量证明。phase失败或cancelled不能复用；输入变动/锁冲突/证据不完整返回可诊断错误，不自动重试。

## 运维与交付

旧命令无参数保持从头执行语义；恢复需要显式chain ID。归档后先做廉价状态治理审计，机器报告确认实现scope仍匹配，才可使用既有实现证据关闭计划；规则与实现必须一起交付，不能先口头放松现有验收。

实现范围预计包含 evidence/verify/report/handoff、相关自测、HA证据出口、PLANS、Harness Guide与规则矩阵，以及根README命令说明。无需修改生产Architecture或业务安全契约。实施前把准确文件与分步RED/GREEN写入独立执行计划；A5保持blocked，新Harness计划至多一个active任务。

## 风险与回滚

最大风险是排除真实输入、配置漏采及错用旧阶段产物。首版保守多失效，不引入任意自定义排除和跨HEAD复用。消费闭包的采集成本需测量，但不以省时为由省略制品身份；无法证明闭包则不复用。

回滚为禁用恢复、要求从头执行，保留历史新schema只读；不要将旧记录转换成新成功。延期模块自动推导、远程缓存、TTL策略及跨机器复用。

## 验证策略

使用真实临时Git仓库与stub工具，先RED后GREEN，避免用容器长跑验证脚本分支：

- 计划增删/迁移、PROGRESS变化：实现scope不变，完整scope改变，治理检查必须重跑。
- 源码、测试、POM、脚本、设计/历史/普通README、symlink、执行位变化：实现scope必须改变；Git失败/未知scope不能fresh。
- 运行中只改状态文档仍失败；HEAD变化拒绝复用。
- 完整链成功、各 `--through` 边界退出 0 但非 full passed、显式 ID 续跑、晚阶段失败恢复、线性下游失效；failed/interrupted 不能跳过，旧失败不变。
- settings、外部脚本、effective config、私有 JAR/POM、镜像身份和报告变更；闭包不全禁用复用，同制品执行中变化拒绝成功，无关依赖新增不失效，reactor 产出绑定生产阶段。
- 缺字段/错类型/version/attempt/摘要、路径穿越、同链及跨链并发、独立入口争用共享输出全部失败关闭，HA state/detail 不跨 attempt 串用。
- HA缺任一场景、非空或缺ledger、旧detail错配、超时124原样保留；Maven汇总绑定本阶段而非旧target。
- 后处理失败仍非零；最新失败/部分 attempt 不回退旧成功，同链仅完整成功可 supersede，独立必需模式失败仍阻塞；report/handoff 一致，旧 schema 不静默迁移。
- 归档端到端fixture：先完成实现证明，再移动计划，只有治理trace增加，Maven/容器调用计数不增加；任何真实输入变化必须拒绝这条捷径。

集成验证先脚本语法与上述fixture，再quick/clean。真实HA仅在HA阶段证据/资源边界修改后运行一次，随后最终全量证明新链；中断恢复fixture优先stub，不为证明跳过而重复真实长链。精确命令由批准后的执行计划固定，不把本文当作已通过证据。

## 后续文档与执行

同步 `docs/PLANS.md`、`docs/references/HARNESS_GUIDE.md`、规则矩阵及README实际入口。正文在设计批准前不修改现行规范。设计独立Review后请用户复核，再建立实施计划；不创建Git提交。

## 附录 A：备选方案

1. 维持全树fingerprint和整套重跑：语义简单、最保守，但状态归档导致大量无效重编译，继续放大本机空间与调度问题。
2. **采用双scope＋显式同链阶段恢复**：增加局部证据契约复杂度，换取可审计复用；本次推荐。
3. affected-module自动依赖推导：可进一步缩小Java测试，但需要正确解析跨模块/配置/共享规则影响，首版成本和漏测风险更高，延期。

本方案依据本仓脚本与已有压测scope模式，不引入第三方平台能力或声称行业标准保证。

## 附录 B：决策记录

| ID | 来源/问题 | 最终决策 | 理由与影响 |
|---|---|---|---|
| D1 | 用户要求缩小收尾资源成本并确认继续 | 先隔离归档状态输入并支持验证阶段恢复，不做模块自动推导 | 保持必要测试和失败语义，优先删除重复工作 |
| D2 | 状态排除是否全局影响freshness | 仅对显式实现证据生效，治理检查始终当前执行 | PROGRESS和active计划是clean/drift真实输入 |
| D3 | 旧失败能否自动升级 | 新attempt显式引用有效成功阶段，旧失败保留 | 规则优化不是给A5旧失败改分 |
| D4 | 是否放宽HA/测试时间 | 不改变场景期限，只正确配置外层调用预算 | 防止用资源慢或124猜测掩盖真实错误 |

## 附录 C：独立 Review

首轮独立 Reviewer：`sixindependentreviewer93cdc182`；结论：**Revise**。六项修订如下，记录修订不表示复审通过：

1. 消费依赖闭包须 preflight 冻结并核对精确制品/settings，区分 reactor 产出；闭包不全禁用复用，不做全 `.m2` 摘要。
2. 明确固定 `--through` 边界、新链 ID 与显式续跑，部分退出 0 不等于 full passed。
3. 任一阶段重跑使全部下游失效，首版不引入 DAG。
4. 增加仓库执行互斥和链记录锁，覆盖独立入口，HA state/detail 按 attempt 隔离。
5. 明确实际工具、settings、环境、外部脚本及 Compose/image 身份；动态未知输入禁用复用。
6. 明确最新 attempt、不可变历史、同链完整成功 supersedes、独立失败阻塞及缺失/部分/后处理失败语义；独立模式首版保持完整 scope，归档 stale 不冒充 passed。

第二轮独立 reviewer `93cdc182-46fb-4c3`：**Approved**，完整复读修订191行，六项逐一closed，findings none。此结论仅为设计复审，不证明实现、运行或成本收益。用户已批准方向，实施仍等待用户确认书面方案。本文尚未实施。
