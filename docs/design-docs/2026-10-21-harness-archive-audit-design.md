# Harness 归档差异审计设计

> 状态：已被取代／延期。当前以[精简实施设计](2026-10-22-closeout-process-simplification-implementation-design.md)的两时点人工状态范围 Review 关闭；archive-audit、基线复制、journal 与发布状态机已撤下，不保留旧归档特权。下文历史设计及 Review 记录原样保留，不作为当前命令说明。

## 摘要

不再尝试证明 Maven、Docker 和自测的全部外部输入在两个运行时刻之间未变化，也不缓存或跳过阶段执行。只解决一个确定的问题：一次完整验收成功之后，仅移动计划、修改索引或 PROGRESS，是否必须重新证明同一份实现。

新增显式归档审计证据。它证明“指定成功验收后的 Git 可见差异仅为批准的状态文件，并且当前状态治理检查通过”，不证明外部环境仍健康或原测试重新执行。原 full 和风险门禁记录的时间、完整指纹、状态与 freshness 均保持不变；report/handoff 另列归档审计，不把原 stale 改为 fresh。

## 背景与目标

A5 多次实现验收通过后，因归档改变完整工作树指纹，被要求重复 Java、打包和容器 HA。通用阶段复用又要求工具、环境、settings、镜像和消费依赖闭包，当前自测还有真实 Compose 与 ignored 状态输入，难以用小改动安全证明。

本设计将“实现交付的历史成功证明”和“当前环境重新验证”区分开。目标是降低纯状态归档成本；非目标为环境缓存、恢复跳阶段、跨HEAD复用、模块影响推导及自动晋级旧失败。需要当前环境健康证明的发布、容量或运行诊断仍须实际执行对应门禁。

## 已知事实与约束

归档审计归 scripts Harness 所有，不改变业务源码、测试断言和HA期限。完整 fingerprint 仍是原始验证事实；H1的 `verification-input-v1` 仅用于比较排除根PROGRESS与active/completed状态目录后的内容，并包含HEAD。状态目录以外的README、设计、history、规范、AGENTS、源码、测试、脚本、依赖声明或部署变化均拒绝归档捷径。

本机证据可被用户修改，不提供恶意篡改防护；摘要和schema仅检测意外错配。Git失败、未知版本、缺字段、路径越界、symlink或证据缺失均失败关闭。无新依赖、环境服务或通用缓存。

## 详细设计

### 一次验收后固定基线

新增固定入口 `./scripts/verify.sh archive-audit prepare`，只生成待归档基线，不移动计划。前提为当前工作树冻结，readiness、architecture、startup、behavior、e2e、quick、clean、full、quality、cleanup均为当前完整指纹和HEAD下稳定成功；full不能partial/incomplete/后处理失败，quality必须独立Accept。此严格集合是本任务默认验收集合，首版不开放任意模式排除列表。full自己的容器HA覆盖由该次full完整阶段证明，不用旧routing-ha独立记录补齐。

CLI参数及互斥前置拒绝不创建“已启动验证”记录；journal只在真实验证dispatch前写入。真正取消仍留下incomplete，不删除或改写。新prepare可在确认原owner PID已退出、且同模式存在严格晚于原启动的当前完整HEAD/fingerprint稳定成功journal时，将旧incomplete作为历史中断纳入基线，明确记录supersession和原始/后继证据摘要。owner仍存活或旧incomplete缺少可核对owner时拒绝；此规则只允许重新完成验收后准备新基线，不解除既有基线对后续中断/失败的阻断。

prepare在仓库互斥锁下再次核对当前完整/实现指纹，复制所需JSON及full成功attempt证据到 `.harness/archive-audits/<id>/baseline/`。保存ID、schema、HEAD、完整与实现指纹、时间、固定必需模式以及每个证据的摘要。full阶段证明复制其当前链的实际scripts/routing-ha/maven记录。原始JSON按字节保存并摘要，不重写绝对artifacts路径；另存固定归档清单，将原引用映射为baseline内副本相对路径和SHA256。验证只用该清单读取副本，不访问原attempt绝对路径；原产物随后被allowlist清理不影响已完整保存的基线。缺失、不稳定或任一失败拒绝。无完整阶段契约的旧full记录不自动升级，必须执行一次新实现下完整验收。

保存完整 Git-visible 路径清单与内容摘要（含执行位/symlink），以及状态文件集合的摘要，供后续明确列出归档差异；不保存秘密原文或把全仓内容复制到证据。prepare日志退出0只表示基线准备成功，不代表归档关闭。

### 移动状态之后完成审计

`./scripts/verify.sh archive-audit verify <id>` 仅接受Harness生成的安全ID。取得同一仓库执行互斥后，加载且严格验证基线，核对本机HEAD未变、证据及引用产物摘要未变、实现指纹等于prepare，并列出相对基线的所有Git可见增删改路径。只允许根PROGRESS、active/completed目录变化；真实输入变化（包括新untracked源文件、设计、history、规范）拒绝，不回退旧成功。

当前阶段执行readiness、drift、clean（含计划结构/链接检查）、quality scope校验及cleanup扫描；这些是真实当次执行的治理证明。quality重新运行正常脚本，必须同一reviewScope且原Reviewer Accept，不能把手改response当复核。若其scope也变了，必须重新Review，不使用归档捷径。调用前后仍要求完整工作树冻结，状态文件运行中变化也失败。

为必需模式增加最小的不可变启动/结束记录：命令运行前原子写入ID、模式、HEAD/实现指纹、启动时间和incomplete，结束后保存原退出码与完成状态；原latest JSON接口保留，不引入事件总线。prepare保存已观察运行集合的边界，verify枚举之后的同HEAD/实现指纹启动记录，不能按完整fingerprint的freshness过滤；full同时核对attempt历史。启动未完成、失败后又成功等均不能因latest JSON覆盖而丢失。检查基线后新生成的同实现HEAD/实现指纹失败记录：必需模式若在prepare后真实执行且failed/partial/incomplete，必须阻断；不能用基线成功掩盖更新失败。新增不同mode失败若属于原必需集合以外，报告保留事实，不静默移除或把本审计解读为全仓绿色。基线不覆盖后续环境失败、代码失败或被取消验证；若prepare后的实际验证失败，需重新完成成功验收再prepare新基线。

成功时原子写新的archive-audit结果，包含基线ID与摘要、原验收时间及完整指纹、当前HEAD/完整指纹、未变化的实现指纹、状态差异路径、当前治理检查结果、明确 `evidenceKind=archive-state-transition`。不覆盖原门禁记录，不创建新的full passed、不替换原验证时间、不改语义分数、不标记任何阶段reused。

若校验或治理失败，返回非零并保留本次失败结果，不推进关闭；原成功基线只读。取消留下incomplete，不允许结果消费者回退到此前成功审计。report/handoff失败单列finalizationStatus并使命令非零，原始失败优先。终结采用有限顺序：先保存incomplete；治理完成后仅在本次私有临时目录构造待发布成功结果及其report/handoff派生文件，生成器通过显式内部输入读取待发布对象，不能对外发布候选成功。两份生成成功且最终完整冻结检查通过后，以原子rename发布结果及派生文件，最后发布指向审计ID/结果摘要的完成指针。消费者必须核对完成指针、结果摘要及派生文件标识一致才显示可关闭；中途崩溃/错配为unavailable，不回退旧成功。任一步失败保留failed/incomplete，清理自己的临时发布目录；不以无限重跑report解决发布顺序。

### 消费者与计划政策

report/handoff继续原有fresh/stale判定，不全局改算法。新增独立展示段：归档差异审计是否通过、引用的原验收时间/指纹、当前治理时间/指纹、历史运行证据不是当前环境复测。审计自身只有当前完整指纹、HEAD、冻结及基线摘要都满足才有效，运行后再改状态文件也会stale。最终告知可写“实施验收通过，归档审计通过”，不得写“旧full在当前环境重新通过”。

`docs/PLANS.md`、`AGENTS.md`及Harness Guide调整：有本版本审计成功可作为纯状态迁移的关闭证据，免于重复实现运行；没有基线或有真实输入变化仍遵循完整验收。不能为了关闭A5临时例外，必须有通用正反fixture和独立Review。

归档前全部步骤passing才移动计划；归档后验证通过则关闭，不再回填tracked结果制造指纹循环。验证失败恢复active并重新打开受影响任务。H3通用复用明确延期，不强行勾绿；当前Harness计划需据此调整任务和非目标，保留原设计Review历史。

## 可靠性与安全

prepare/verify采用仓库锁、原子JSON、安全ID和限定证据根；引用路径必须规范化后位于该基线目录内且无symlink。基线捕获与完成前后均核对Git内容，运行中编辑失败。摘要可检测制品变动但不是外部依赖身份承诺；真实运行证据只陈述原时间点的观察。

不降低测试数量、阈值、超时和清理约束。归档校验不是发布健康门禁、运行环境认证或跨机可信证明。A5当前没有满足新prepare条件的成功基线，仍blocked。

## 风险与回滚

风险是消费者把历史验收与当前运行混淆，因此不改原freshness、不生成新的full成功，明确分开两个时间点。prepare严格门槛意味着首次需要完整验收，但后续纯归档不再重复；不承诺已节省运行时长。

回滚为禁用归档入口，恢复完整验证规则，旧审计只读保留。通用阶段复用、依赖闭包采集与跨机器证据延期，已实现H2分段入口仍每次真实执行。

## 验证策略

真实临时Git仓库与stub，不通过真实HA反复验证分支：

- 真实RED：入口缺失/旧规则会要求再跑；有效基线→状态迁移→verify成功，Java/容器trace调用次数不增加。
- prepare缺任意必需成功、当前partial/incomplete/后处理失败、stale、不稳定、unknownschema、quality非Accept、阶段证据缺失必须拒绝；旧A5记录不自动升级。
- 非法resume/参数及锁冲突不留下伪启动；真实取消保留原始incomplete，新基线仅在owner已退出且同模式严格后继的当前稳定成功时记录supersession，live/unknown owner拒绝。原基线仍阻断所有后续失败/中断。
- 仅PROGRESS/索引/计划迁移允许；源码、普通README、设计/history、脚本、POM、部署、执行位、symlink、HEAD变化拒绝。
- 证据/产物缺失或摘要改变、越界/symlink、未知ID、错字段/类型、两链并发/独立构建冲突、运行中状态修改全部失败。
- prepare后新失败/partial/incomplete必须阻断，包括失败→成功仍拒绝旧基线、启动→取消但latest仍是旧成功；成功审计后改变状态必须stale。
- 真实绝对路径artifact记录按字节保留及清单复制后，删除原attempt产物，审计仍能核验副本；不得读取越界或symlink。
- report/handoff成功后都显示最终passed；任一生成失败或发布中断无完成指针/摘要错配时均不可关闭，不修改旧full结果。
- 治理失败、取消和后处理失败均非成功，原失败码优先；历史成功不能遮蔽最新失败。

先语法、聚焦fixture、父Harness再quick/clean；最终一次完整验收用于生成真实基线，归档后只实际执行廉价治理审计。确切实施文件与命令由更新的执行计划列出，不以本文宣称验收。

## 后续文档与执行

实施范围：verify固定入口、单用途archive-audit脚本/fixture、report/handoff独立展示及其fixture，PLANS、Harness Guide/矩阵、AGENTS、根README、当前Harness计划与PROGRESS。业务Architecture不变。独立设计Review后才能实施，不创建Git提交。

## 附录 A：备选方案

- 全树指纹下每次归档重复完整运行：简单但成本高，原有可靠退路。
- 通用阶段缓存：省更多运行，但当前外部消费闭包不可证明，延期。
- **指定基线的状态迁移审计**：只消除状态归档重复，明确不证明环境未变，契约更小且不篡改原结果，本轮采用。

## 附录 B：决策记录

| ID | 来源/问题 | 决策 | 理由与影响 |
|---|---|---|---|
| D1 | 用户要求减少电脑资源及收尾耗时 | 优先归档差异审计，通用阶段缓存延期 | 避免为了缓存构建复杂依赖解析框架 |
| D2 | 外部配置和依赖不可证明不变 | 原验收仅陈述原时刻事实，不声称当前环境通过 | 归档只证明实现输入不变及当前治理成功 |
| D3 | 旧A5失败如何处理 | 新prepare严格门槛，不自动升级旧失败 | 新规则不能作为修分捷径 |
| D4 | H2分段入口保留吗 | 保留但每阶段实际执行，不声称缓存 | 可以控制单次外层预算，不改变测试期限 |

## 附录 C：独立 Review

首轮 reviewer `67bfb224-57c4-4c0`：Revise，三项为后续失败事实来源、绝对产物路径与基线副本冲突、后处理发布顺序。已补最小启动/结束留存、原始JSON加独立复制清单、临时结果生成及最后完成指针；补对应确定性反例。第二轮 reviewer `67bfb224-57c4-4c0` Approved，三项closed/findings none。设计批准不代表已实施或机器验收；施工继续归既有Harness实施计划。
