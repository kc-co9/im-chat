# 项目进度

## 当前收口入口（2026-10-29）

- 活跃计划：none，active 任务 0。[Bolt 包职责与 Broker 快照归位](docs/exec-plans/completed/2026-10-25-bolt-package-boundaries.md#attempt6-与复审-r1r52026-10-29) 已关闭：T1–T3 passing；冻结 full attempt8 实际 exit0（routing-ha 25 场景、cleanup passed），独立 quality 复审与迁移精确差异复核均为 Accept；迁移后的 drift、clean、quality、cleanup、handoff 已完成，实际机器结果由当前 Harness 拥有，后续发现验收无效则恢复 active。
- 已知偏差与偶发失败登记为 [技术债 TD-01–TD-04](docs/exec-plans/tech-debt-tracker.md)；D9（移除私有 JRaft 补丁）延后。用户已授权全部改动按独立主题提交，不授权 push；提交准备与取消重复验收的决定见已归档计划末尾，实际提交记录以 Git 历史为准。机器结果入口为 `.harness/report.json`，原 full 时间与指纹照实保留、迁移后如实 stale。下文均为历史检查点。

## Bolt 当前构建检查点（2026-10-29）

- 最新检查点：用户已明确允许构建和测试官方 `https://github.com/sofastack/sofa-jraft` commit `cb1a0c7ac16ff146ad8abb04b1bcb035270bb234` 与当前已 Review 的补丁，仅隔离验证、不发布。修订后的 Leader 单项 Maven 测试已执行：首次因命令工作目录错误在测试前失败；纠正后 1 项出现 setup SPI 实现缺失错误，尚未执行 Leader 断言。已定位为独立准备目录的 epoch-zero 资源时间与旧插件复制比较相互作用，在新隔离副本仅增加 resources overwrite 参数后 focused 1 项全零、四个 SPI 字节一致；完整 NodeTest 已结束 exit0，fresh XML 64 项零失败/错误/跳过。尝试启动正式新双轮构建时工具判定服务超时，命令未执行，不算权限拒绝或构建失败。正式 recipe 不保留 tar mtime，不修改 recipe；此前权限拒绝保留为历史，不再作为当前执行阻塞。readiness 已通过；用户已告知启动 Docker，但随后健康检查被工具判定服务超时阻止，当前 daemon 状态待实测，不沿用旧不可用结论。T3 仍 blocked；磁盘最近观测约 68 GiB 可用，不代表其他环境问题已恢复。

- 用户明确授权固定官方 JRaft commit 与既有补丁的隔离双构建后，`capture-20261029` 第一轮已 exit1，未启动第二轮；精确失败及证据见计划末尾。恢复来源映射已独立批准并写入 provenance；readiness 再次通过。T3 仍未验收，不提前归档。上游 Leader 测试有界观测修订和 Bolt 关闭失败延后传播均已独立设计批准，当前在原任务内窄修验证；Docker daemon/backend 仍不可用，未重置用户环境。下一步按真实结果处理输入/外部模型证明、reviewed lock、消费者及完整验收；未来官方 JRaft 治理简化草案延后，不实施、不发布、不代用户提交。下文旧暂停和阻塞记录作为历史保留，当前顺序以本节及计划末尾为准。

## Bolt 计划收口入口（2026-10-28）

- 当前计划：[Bolt 包职责与 Broker 快照归位](docs/exec-plans/completed/2026-10-25-bolt-package-boundaries.md#当前收口账本2026-10-28)。已汇总实施、取代决定和剩余验证；T3 为 blocked，暂无 active 任务，不归档。用户已要求收口，不再等待 Review 结束通知；当前仍被私有 JRaft 制品缺失及必要跨模块验证缺口阻塞。TLS 与 DECODE 测试取证修正均已获独立局部 Accept，现继续不依赖 JRaft 的 Bolt 模块验证；原失败和历史精确归因限制仍保留，详细结果和下一步见计划。最终机器结果入口为 `.harness/report.json`，本次诊断不产生完整验收，也不把历史通过记为当前验收。

## Gateway 目标职责内聚（2026-10-28）

- 用户批准删除 `GatewayIdentityConfiguration`，独立设计 Reviewer `aa00ec4801ac133f0` Approved；readiness 与 SDK 基线通过，在现有 T3 将原目标选择、校验和不可变快照收回 GatewayClient。SDK baseline/final 各 6 项通过，原体比较一致。独立实现 Reviewer `a578c69c59159b4d9` Revise、源码 findings none，原因是必要 Broker 测试及跨模块编译证据缺失；既有暂停保持，尚未验收。

## Broker bootstrap 职责内聚（2026-10-28）

- 用户批准删除 `BrokerIdentityConfiguration`，独立设计 Reviewer `a6eeb49b80726e483` Approved 后在现有 T3 实施；客户端直接接收可信配对列表，SDK 21 项、三个消费方 33 项窄验证及原体比较已执行。独立实现 Reviewer `abd7615ac6f041374` Revise、源码 findings none，原因是必要 Broker 测试与 E2E 编译证据缺失，不记 Accept。追加 RequestContext/observationsAllowed 注释和单处构造判空删除；旧验证缺口与暂停保留，T3 不关闭，详细证据见 T3。

## Bolt 传输模式枚举提取（2026-10-28）

- 用户批准 `BoltTransportMode` 独立枚举并要求注释，独立设计 Reviewer `aa6dedabbf9c56d38` Approved 后已在现有 T3 实施并补齐类/枚举值注释；类型规范化对比和窄验证已执行，局部独立实现 Reviewer `a4053f269f0ea9d40` Revise、源码 findings none，原因是必要跨模块编译受依赖阻塞，证据见 T3。固定前缀 Revise 与既有依赖/验证缺口保留，不恢复 full/clean/module/architecture/commit。

## 项目角色身份前缀固定化（2026-10-28）

- 用户确认五角色一起固定，已记录[固定前缀设计](docs/design-docs/2026-10-28-fixed-peer-identity-prefixes-design.md)，独立设计 Reviewer `a7ce1f2aa262d78e2` Approved 后在现有 T3 实施；SDK/消费方窄验证已执行，Broker 与 E2E/HA 仍受私有依赖阻塞，独立实现 Reviewer `a28177bf77d7ebedd` Revise、源码 findings none，原因是必要 Broker/跨模块编译证据缺失，不记 Accept，详细命令及实际结果见 T3。私有 JRaft 依赖阻塞、旧 DECODE/TLS 缺口及 full/clean/module/architecture/commit 暂停保留。

## 身份前缀默认值集中（2026-10-28）

- 用户批准 `PeerIdentityConstants`，在现有T3集中五类九处Java默认前缀，配置覆盖和YAML不变；readiness exit0，独立设计Reviewer `a19a522ec3aa516fc` Approved后已实施，九处默认值精确展开一致、独立javac及scoped diff-check通过。Broker聚焦测试因私有jraft-core:1.4.1-imchat.1缺失在执行前阻塞，未继续消费方验证；独立实现Reviewer `ae9d95d394bdfb2f8` Revise、findings none，源码无修改要求，必要验证缺口待依赖恢复后补齐，不记Accept。保留已有TLS Broken pipe与DECODE缺口，不运行full/clean/module/architecture/commit，完整验收未完成。

## 连接创建作用域合并（2026-10-28）

- 用户确认 `ConnectionCreationScope` 命名并授权调整；设计追加节独立Reviewer `a5158f39a13936936` Approved、必须项none。按现有T3合并Factory两个ThreadLocal，保留候选回收、嵌套恢复及原close抛错边界；readiness exit0，合并已实施，修改前后Resources/Background各9项通过；TLS/Lifecycle共63项中1项证书轮换用例原生SSLSocket握手Broken pipe失败，归因未定，停止扩大验证。独立实现Reviewer `a6a913c731e865433` 局部Accept、findings none，不代表边界或完整验收通过。无新增真实嵌套专项回归。仅本增量实现及窄验证，不运行旧DECODE/module/architecture/full/clean/commit，完整验收未完成。

## 初始化阶段方法提取（2026-10-26）

- 当前T3独立追加Client三个private阶段方法原位提取，保持monitor/calls和失败责任；不改正在Review的monitor-startup旧证据。当前58项窄验证已实际通过，展开原体比较一致，局部实现Review待完成，保留Handler和用户排版；旧DECODE/更宽验证暂停不变。

## 最终 monitor/startup 迁移（2026-10-26）

- 当前T3唯一active，最终采用独立monitor串行startup/shutdown，取消5秒等锁及中断退出；无期限等待和自定义回调跨线程join风险明确保留。同步弃用入口迁移及必要真实回归，RED→GREEN和受影响窄测试已执行，跨Broker/HA仅编译；局部独立实现Review待完成，详细证据见T3；旧DECODE及更宽验收缺口不变，不运行full/clean/commit/module/architecture。

## 受控 startup 入口迁移（2026-10-26）

- T3追加弃用init迁移：已核原生动态startup与异常契约，最小同步受控override/super、默认Bean和已确认受控调用；原生/raw/server init不动。当前待实施窄验证及独立Review，不运行module/architecture/full/clean/commit，旧DECODE保留。

## 最小锁边界维护（2026-10-26）

- 现有T3继续处理三处已审计冗余calls外层锁及重复acquireLifecycleLock提取；保留四处复合原子边界、Tracker和Handler，不提取initializeResources。52项生命周期/后台窄验证已实际通过，helper原体比较及四处复合锁检查通过；局部实现Review待完成；旧DECODE及更宽验证暂停保留，不运行full/clean/commit。

## 生命周期串行化增量（2026-10-26）

- 最终 [ReentrantLock 与固定 5 秒 tryLock 设计](docs/design-docs/2026-10-26-bolt-client-serialized-lifecycle-design.md) 独立 Reviewer `a3b3f5a6c96a74ee1` Approved、必须项 none，进入现有 T3 实施，仍唯一 active。先新契约 RED 再最小 GREEN，仅 Lifecycle/ProductionBackground；保留用户排版及 ControlledChannelHandler。新契约 RED 后最小实现 GREEN 已完成，当前等待局部独立实现 Review；详细证据和残余专项覆盖缺口见 active T3。尚未完整验收；旧 DECODE 缺口保留，不运行 module/architecture/full/clean/commit。

## Client 四态生命周期追加（2026-10-26）

- 独立设计 Reviewer `a46adc81a21ae2558` Approved、必须项 none；用户确认继续处理后，按[四态设计](docs/design-docs/2026-10-26-bolt-client-lifecycle-state-design.md)进入现有 T3，仍唯一 active。仅 Client、必要 lifecycle 测试及 README；先旧实现 baseline，再同层验证，保留 Handler 修改。既有 DECODE 失败未归因，不扩大 module/architecture；clean/full/commit 暂停，尚未验收。

## 两处可读性提取追加（2026-10-26）

- 用户批准仅将消息类型白名单直接展开回 Handler.write，删除 validateMessageType；validateInvocation 保留 private void，失败抛异常，统一 catch 与 catch 外 super.write 不变。已实施并同步设计/当前 T3；精确展开比较一致、git diff --check exit0，证据在 job tmp/message-type-inline-*。本次未构建或测试，既有 DECODE 失败仍未归因，不复用旧报告声称新输入通过；T3 保持唯一 active，clean/full/commit 继续暂停。

- 用户授权「你先调整一下看看」，独立设计 Reviewer `a38eedb4de94875cc` Approved、必须项 none，仅覆盖 Tracked 静态内部类与 Optional failure 提取。Optional 阶段 focused103、Bolt267（含 common 共304）均 exit0、零失败/错误/跳过；旧版架构未运行。readiness exit0，保留用户新增 Handler @Override，before/effective-before、精确 diff 和语义比较已保存。
- 用户后续明确「可以」，批准两个 private void 校验方法与 write 单层 RuntimeException catch、super.write 在外；新增属性/校验/drain 意外 RuntimeException 转 promise 的行为变化已写入同一设计，独立设计 Reviewer `a38eedb4de94875cc` 复核 Approved、必须项 none，明确恢复实施；新增边界回归先 RED 后 GREEN，再按 focused→module→architecture 顺序验证，不用旧证据证明新方案；T3 仍唯一 active。clean/full/commit 禁止，实现独立 Review、正式 quality 与完整验收未完成。
- 最终版新增边界回归9项已 RED→GREEN；含步骤注释的 focused112 中既有 DECODE handled 预期1/实际0失败，最窄三参数再现，未扩大 module/architecture。一次临时cause诊断走预期解码后预算路径并通过，但不能证明两次失败根因，失败记录保留；测试诊断行已恢复原bytes/hash。用户追加的 validateInvocation 五阶段注释已补齐，去注释 token 一致，最终 diff/SHA 已刷新；当前仍暂停更宽验证，不冒称已修复或完整通过。

## Bolt 按职责拆包追加（2026-10-25）

- 用户明确认可设计，独立设计 Reviewer `ad3341b14c207845a` Approved、必须项 none，已明确恢复实施；依据 [职责拆包设计](docs/design-docs/2026-10-25-bolt-responsibility-packages-design.md)，T3 仍唯一 active。
- 本轮按批准范围完成 invocation / connection / channel 搬包、Factory/Manager Controlled 命名、最小 public、private 唯一 closed 受控访问及测试/fixture/helper/当前文档同步。顺序 baseline103、focused103、Bolt267（reactor含common共304）、Broker50、consumer/startup37、architecture34 全零且 exit0；E2E/HA 仅 test-compile、Harness 均 exit0。54 个 allowlisted 旧 class 已保存清单/SHA并隔离，无重现；103 条同轮 invocation 与方法/参数清单一致，生产归一对比相同、BoltRpcClient 字节不变。证据见 active T3 与 job tmp。独立实现 Reviewer `ad3341b14c207845a` 局部 Accept、findings none，已独立核对 SHA、归一差异、103 条矩阵、54 个旧产物无重现、可见性及 shared monitor；仅覆盖本轮职责拆包，不覆盖完整混合工作区。正式 quality、full 与真实网络完整验收缺口保留；clean/full/commit 未运行，T3 不关闭、不归档。本次仅同步 Review 状态，无源码或构建变更。

- 活跃计划：[Bolt 包职责与 Broker 快照归位](docs/exec-plans/completed/2026-10-25-bolt-package-boundaries.md)；当前任务：T3。包迁移及聚焦架构/模块测试已通过，追加的 Bolt 11 类与 Broker 快照成员注释已补齐，非注释 token 对比和空白检查通过。用户 Review 批准出站处理器改名为 client.BoltOutboundHandler，3 处装配引用已同步，静态检查通过，改名后尚未编译。用户正在 Review，clean/full 按要求暂停，待明确通知后集中验收；已取消批次不计通过或失败，此前完成记录不替代本轮证据。

## Review 追加范围（2026-10-25）

- 用户批准删除无仓库内生产调用者的 `BrokerClient.listBrokers()` RPC 整链；设计 D6 独立 Review a61ffe608a987aada Approved、无必须修改项后已实施。专用 DTO/响应映射及实际测试引用已清理；`BrokerQueryParams`、String/Instant 注册映射、Registry/Gossip/HTTP 诊断保留，外部消费者未知的不兼容风险已记录。
- 静态搜索与 `git diff --check` 通过；私有 m2 下顺序 Broker focused 25 tests、Message focused 3 tests、LayerBoundary 30 tests，全部零失败/错误/跳过，日志与准确命令见 active 计划。未改前缀命名或其他 Bolt 客户端逻辑。T3 仍唯一 active；独立实现 Review 与完整验收未完成，clean/full 保持用户暂停，未提交。

## 暂停维护交接（2026-10-24）

- 本次仅涉及 `im-plugin/im-bolt/src/main/java/com/co/kc/imchat/plugin/bolt/core/BoltAuthenticatedConnectionFactory.java` 和 `scripts/test-routing-ha-process.sh`。前者展开嵌套异常分类并补充原因注释；后者修复获批的 cleanup 握手、取消同步与异常子进程回收测试。两项均已独立 spec／quality Accept，工厂 focused 59 tests 全零、fixture 子自测及变异反证通过；不等于完整验收通过。
- 最新实际结果：父 Harness exit0；标准全 reactor Maven clean、`verify.sh clean` exit0；full 于 08:50:44–09:08:28 UTC **exit1**，停在 IAM JUnit discovery，无法加载 `DbIamOAuthAuthorization`。未进入本轮真实 HA，348 份 fresh XML／1865 tests 全零只是中途报告，不能当完整 reactor 通过。
- 干净构建期间再次出现 JDT 风格错误 class；此前生成源码明确标注 Eclipse JDT (IDE)，本项目 IDE 与 Maven 共用 target。隔离 javac＋MapStruct 窄实验已通过。具体 writer 及 discovery 因果尚未全部证明，未擅改 IDE 设置或终止 IDE／外仓进程；恢复前须明确隔离共享输出的维护窗口，不再盲目 clean／full。
- **交接原件：`/tmp/bolt-readability/STOP-HANDOFF.md`**。本任务精确差异、两个冻结 SHA、审查、历次失败、编译器调查及下一步边界均在该文件及 `/tmp/bolt-readability/`。恢复前先核对冻结文件，不扩大源码／fixture 范围、不用旧 Bolt full8 替代新维护验收。其他用户改动全部保留；未提交、未部署。
- 以下为**此前已完成的 Bolt 统一 invoke 项目**记录，完成结论只绑定其原验收输入，不代表上述新维护通过。

## 此前已完成项目
- **Bolt 连接层认证与统一 invoke 已完成实现、隔离验收、独立 Review 和状态关闭。** 执行计划已迁移至 [completed](docs/exec-plans/completed/2026-10-22-bolt-unified-invoke.md)，历史失败、Block、修复与验证窗口保留在该计划及对应证据目录，不作为当前待办。
- 监督循环 #4 已删除；未创建 Git commit，未生产部署，未撤销或提交无关用户改动。

## 验收与关闭证据

- 标准 full8：2026-10-24 02:55:41–03:19:43 UTC，exit0，1442.057 秒；2746 个冻结输入稳定。
- 完整 reactor：496 份 fresh XML，**2350 tests，0 failures，0 errors**；2 项既有本地 MySQL 条件 skip，不代表真实 MySQL 分片已验证。
- 同轮制品证明：48 项 expected/actual SHA 匹配；真实 HA **RH01–RH25 全通过**，新增 Gossip 注册、删除与恢复收敛检查通过。owned HA 项目的容器、网络、卷清理后为空。
- 最终独立实现 Review `59cbddd1`：**Accept，findings=[]**，原 T6-R1／R2／R3 均关闭。迁移后的机械差异经独立复核，正式 quality 的当前 scope 和十二个 unitFingerprint 匹配，verdict 为 Accept。
- 状态迁移及用户批准的两个链接重定位，经独立 Reviewer `40c50a94` 精确差异审查 Accept。最终 drift、clean、quality、cleanup（scan-only）、handoff 均实际通过；运行后未再回填 Git 状态。

证据入口：
- full8：`/tmp/bolt-t6-acceptance-attempt8/RESULT.md`
- 最终实现质量：`/tmp/bolt-t6-final-acceptance-quality/RESULT.md`
- 状态迁移与廉价收尾：`/tmp/bolt-state-closeout/`
- 当前机器报告：[Harness report](.harness/report.json)、quality snapshot（`.harness/quality/quality-snapshot.json`，由 quality 生成）。状态迁移后的 stale、原 full 时间及指纹照实保留，不将旧运行刷新为当前 fresh。

## 保留边界与后轨

- 正式质量十二单元均为 **B／10分（满分12）**。历史 performance/load stale 导致机器 `handoffReadiness=0`，未修改分数或扩大排除项；本任务不提供容量或性能保证。
- full4 两个 close 用例的历史具体 cause 仍 unknown；测试已按审查补足完整 retirement 前提，不能将后续通过冒称为该历史 cause 已证实。
- D10 约束当前实际发送／呈现的证书数组，不证明隐藏 CertPath；D11 以受控装配及可信 JVM 代码为边界，不提供恶意进程内 sandbox。D14 沿用原生 TLS 材料加载，D13 私钥快照未实施。
- 专用 DEV Compose 已验证配置绑定及 write/close 目标选择，不据此宣称该部署的真实网络传输已经验证。未知 TLS 目录 `RphHzdkY` 未擅自删除，精确材料删除取证限制保留。
- 生产证书签发、secret 分发、在线吊销、私有 JRaft 正式发布／可复现性、历史 Raft 偶发及 Redis 丢失等后轨不因本次完成而消失。
- [原 Harness 平台计划](docs/exec-plans/history/2026-10-21-harness-verification-resume.md)已撤回并保留 H1–H4 检查点与 H5 blocked，不计 completed，不恢复旧 chain／journal 平台。

## 客户端调用追踪追加（2026-10-25）

- 用户批准最小职责拆分，设计 `2026-10-25-bolt-client-call-tracking-design.md` 已由独立 Reviewer `a35e419d309f04bf8` Approved，必须项 none。T3 仍唯一 active，进入实施及顺序窄验证；readiness exit0。
- 已提取一个 package-private Tracker；保留单 monitor、唯一关闭标志、初始化交接、原生完成与锁外关闭语义。原行为 baseline / 提取后 focused 各62 tests、完整 Bolt267 tests、架构34 tests 均 exit0，全零失败/错误/跳过；精确 admit/drain/Completion 归一 token 对比一致，日志及并发限定符差异事实见 active 计划。
- Tracker 局部独立实现 Reviewer `ac1bb6179c9cf8a01` Accept，findings none；该结论不覆盖完整混合工作区。quiet baseline 日志本身不单独证明退出码或冻结输入，退出码来自当次工具返回；未做专用 mutation。正式 quality 和完整验收未完成，clean/full/commit 继续暂停。
- Factory 依赖评估第一轮 Reviewer `a12ce5a7edf571b89` Revise；接受成本判断，修订为保留 client.manager() 和已固定 snapshot 的模式读取，不实施 bindManager、额外 mode 参数或任何 Factory 生产改动，原 Reviewer 复核 Approved、findings none，两个必须项关闭，仅批准评估后保留现状、零生产修改。Manager 构造执行器但未 started 阶段的新增失败回收不能由现 rollback 证明；不据此宣称现有线程泄漏，不扩生命周期修复。
- D6、前缀迁移及混合改动保留。T3 仍唯一 active；本轮仅文档修订，无 Maven/full/生产修改，不改 BoltRpcClient。

## Peer 身份 URI 前缀迁移（2026-10-25）

- 设计独立 Review `a0b58853328dc8e68` Approved，无必须项；T3 为唯一 active。全仓同概念 Java、配置、实际环境入口和当前文档同步，无旧别名；历史归档和完整 workloadIdentity 不改。
- 实施完成：九入口非默认新键/原生环境与旧键无别名共36个参数化断言先 RED 后 GREEN。顺序窄验证 Broker55、Consumer33、Startup6、SDK24、Architecture4，共122 tests 零失败/错误/跳过，命令均 exit0；精确命令与失败归因见 active 计划。旧名仅留负例、迁移说明及历史记录。
- 默认 URI、拼接/匹配/授权及 MTLS/DEV 不变，外部旧键可能静默回退默认值已在五份 owning README 披露。沿用本轮 readiness exit0；T3 仍唯一 active，独立实现 Review/quality/完整验收未完成，clean/full 按用户要求暂停，未提交。

## Factory / Manager 方法提取（2026-10-26）

- 独立设计Reviewer `a3b3f5a6c96a74ee1` Approved、findings none，按Factory依赖设计追加节实施分类helper及五参数完整创建/发布职责方法，删除acquire纯重抛catch；Client/Handler保留当前用户字节。
- before/SHA已保存，readiness exit0（PATH world-writable警告保留）；Resources/Background 9项、TLS/Lifecycle/DEV/AutoConfiguration 89项依次exit0，全零，原体归一/顺序/Client与Handler字节检查通过，证据已复制job tmp。本增量独立实现Review待完成；scoped diff-check通过，全仓无关Broker尾空白保留。T3仍唯一active，旧DECODE/module/architecture/full/clean/commit不运行。

## 生命周期关闭阶段提取（2026-10-26）

- 三个初始化helper增量已获独立Reviewer `a3b3f5a6c96a74ee1` Accept，但其独立SHA重算因ENOSPC未完成，限制保留。用户批准继续仅提取beginShutdown及三值ShutdownAction；NONE/重入拒绝保持在资源try之前，Handler及record不改。
- 普通df显示可用约3.7 GiB；未删除材料或改临时路径。before/SHA已保存，本增量58项聚焦验证09:07:22–09:08:58 UTC exit0、零失败/错误/跳过，源码运行中不变；freshXML与日志已复制到job tmp/shutdown-method-test，不依赖target保留。本增量局部Review待完成，T3仍唯一active，旧DECODE/module/architecture/full/clean/commit继续暂停，不称完整验收。按后续盘点要求不再启动构建。

## 受控客户端 API 追加（2026-10-25）

- 用户批准 Client 改名与 acquire 包级；独立 Reviewer `a515ed306ad89329e` 首轮 Revise 保留、复核 Approved，三个必须项关闭，已明确恢复实施。T3 仍唯一 active，窄验证待执行，不称验收；clean/full/commit 继续禁止。Invoker 构造、安全检查、分类、Factory/Manager 名称及混合改动保留。

- 本轮窄验证：旧FQCN baseline103、consumer真实invoke baseline37；迁移 focused103、Bolt267（reactor含common共304）、Broker50、Consumer/startup37、Architecture34，全零且exit0。E2E/HA仅test-compile exit0，Harness exit0；首次 focused 编译可见性失败已归因并最小修复，保留失败记录。52个allowlisted旧class隔离，103条same-run invocation及源码方法/参数清单一致，无漏跑双跑；外包javac证明acquire由可访问变为包外不可访问。证据与准确命令见active计划及本轮job tmp。独立实现Review待完成，clean/full/commit未运行，不称验收。

- 受控客户端API局部独立实现 Reviewer `a515ed306ad89329e` Accept、findings none，已核39文件SHA、生产精确归一、103条无漏双跑、52旧产物隔离及API反证。仅本轮局部Review通过，不覆盖完整混合工作区；保留设计首轮Revise与首次编译失败。正式quality、完整验收及真实E2E/HA缺口未消除，T3仍唯一active、不归档。本次仅状态同步，未改源码或运行build/clean/full/commit。
