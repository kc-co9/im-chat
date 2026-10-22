# Broker Adapter 职责重划实施计划

> 实施状态：A1–A5 passing，active任务0。最终完整验收、同轮风险证明及独立quality Accept已满足归档前条件。归档后仅按现行两时点政策审查状态内容与廉价治理，旧失败与未知根因风险保留。
>
> 阅读顺序：[当前关闭账本](#当前关闭账本2026-10-20) → [A5 收尾清单](#a5-删除万能入口并收尾) → [恢复状态](#恢复状态)。历次增量、原始 checkbox、Review 和失败证据完整保留在[整理前执行记录](../history/2026-10-20-broker-adapter-boundaries-execution-history.md)，不再混作当前待办。

## Sprint Contract

- **目标**：拆分 `BrokerClusterAdapter` 职责，由领域聚合、本机 Raft Adapter、Broker 对端 Adapter 和 Gateway 投递 Adapter 分别承担稳定职责。
- **范围**：`BrokerCluster` 领域入口、三个专用 Adapter、类型化 `im-raft` SPI、三个精确 `support.raft` 职责包，以及相关测试、架构规则和文档。后续批准增量还包括 Broker 自有时间契约、自动故障观察与恢复、成员边界和本机锁接入。
- **行为边界**：保持既有路由、Gossip 和 Lease 语义；自动故障增量补齐原 D22，包括 voter 主动探测、Leader 拉独立报告、自动发起与全阶段恢复。其具体职责和时间规则以最新批准设计为准，已取代方案仅保留历史。
- **契约与发布**：类型化增量曾重置持久 envelope 为 v1；后续时间契约升为 v2，拒绝 v1 及旧 v5/v6，不提供兼容别名、不自动删除数据。Broker/SDK/Gateway/Ops 自有字段、配置键和类型变更须同版本成组发布。
- **非目标**：不新增 `BrokerClusterDomainService` 或万能 Facade，不重做 Raft/Gossip，不移动 consensus/membership 包。未来连接层认证与统一 invoke 的生产迁移不纳入本计划；私有制品发布和双构建已明确后轨。
- **所有权**：应用服务负责 fence、quarantine、提交、授权和恢复的跨步骤协调；`BrokerRaftAdapter` 连接领域聚合与通用 `RaftRuntime`，对端调用分别归 Broker/Gateway Adapter。领域不得依赖 Runtime、JRaft、网络客户端、执行器或 Spring。
- **环境与权限**：使用 Java 21、Maven、既有 Docker 与隔离 Maven 仓库 `/tmp/jraft-artifact-m2`。Docker 代理调整已获用户批准；不进一步修改网络策略、镜像、依赖或删除用户数据，不创建 Git 提交。
- **验收**：按窄到宽顺序完成聚焦/模块测试、适用风险门禁、quick/clean/full、独立 quality、cleanup/handoff；全部满足后才归档，再完成关闭审计。

## 事实源

- 设计或产品规格：[BrokerClusterAdapter 职责重划设计](../../design-docs/2026-10-11-broker-cluster-adapter-boundaries-design.md)。后续增量与当前事实源如下。

| 用途 | 文档 |
|---|---|
| 类型化 Raft SPI | [Raft 类型化命令接入](../../design-docs/2026-10-12-typed-raft-command-design.md)，其中 v1 已被后续时间契约取代 |
| 时间与持久格式 | [Broker 自有时间字段契约](../../design-docs/2026-10-14-broker-time-contract-design.md) |
| Transformer 与 Monitoring | [结构归属设计](../../design-docs/2026-10-15-broker-transformer-monitoring-boundaries-design.md) |
| 自动故障观察 | [自动故障观察设计](../../design-docs/2026-10-16-broker-automatic-failure-observation-design.md)，按最新批准决定解释历史步骤 |
| 成员边界 | [共识成员边界设计](../../design-docs/2026-10-16-consensus-membership-boundary-design.md)、[共享成员端点设计](../../design-docs/2026-10-16-shared-membership-endpoint-design.md) |
| Follower 本地候选与发布边界 | [JRaft 私有制品设计](../../design-docs/2026-10-16-jraft-applied-membership-patched-artifact-design.md) |
| 本机锁 | [本机锁设计](../../design-docs/2026-10-17-local-lock-design.md)，最终命名为 `MutexLock` |
| 认证原型与未来设计 | [连接层认证原型](../../design-docs/2026-10-20-bolt-connection-authentication-spike-design.md)、[统一 invoke 生产设计](../../design-docs/2026-10-20-bolt-unified-invoke-design.md)；后者仅为后续设计基线 |
| 当前模块事实 | [Broker Architecture](../../../im-broker/ARCHITECTURE.md)、[Broker README](../../../im-broker/README.md)、[Server README](../../../im-broker/im-broker-server/README.md)、[SDK README](../../../im-broker/im-broker-sdk/README.md) |
| 通用协议事实 | [im-raft README](../../../im-plugin/im-raft/README.md)、[im-gossip README](../../../im-plugin/im-gossip/README.md) |
| 原始执行证据 | [整理前执行记录](../history/2026-10-20-broker-adapter-boundaries-execution-history.md)、[收口前进度历史](../history/2026-10-20-broker-closeout-progress-history.md) |

## 验证分层

以下为已记录的检查点，不代表文档整理后当前工作树的 fresh 证据；实际状态、指纹和结果以 `.harness/report.json` 及 `.harness/verifications/` 为准。历次失败不被后续通过抹除。

| 层级 | 准确入口 | 已记录状态与缺口 |
|---|---|---|
| 环境 | `./scripts/verify.sh readiness` | 收尾阶段已运行；跨会话恢复需重新检查 |
| 模块 | `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server,im-plugin/im-bolt,im-plugin/im-raft -am test` | exit 0，`/tmp/broker-closeout-reactor.log` |
| 架构与风险 | `./scripts/verify.sh architecture`、`behavior`、`startup`、`e2e` | 同隔离 repo 已分别 exit 0，行为 41 场景、启动 10 应用、实时 E2E 1 场景；日志 `/tmp/broker-closeout-{architecture,behavior,startup,e2e}.log` |
| 真实 HA 聚焦 | `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-test/im-routing-ha -am -Dtest=AutomaticFailureTlsHaTest -Dsurefire.failIfNoSpecifiedTests=false test` | 选举空窗等待修正后三项 exit 0，`/tmp/broker-closeout-ha-leader-wait.log`；最终以实际日志命令为准 |
| 容器 HA | `./scripts/verify.sh routing-ha` | 代理修复后 exit 0，25 场景且 owned resources 清理通过，`/tmp/broker-closeout-proxy-env-routing-ha.log` |
| 最终门禁 | `./scripts/verify.sh quick`、`clean`、`full` | 原 quick/clean exit 0；代理修复后 full 首轮 exit 1，HA 等待修正后仍须冻结树重跑 |
| 质量与交接 | `./scripts/verify.sh quality`、`cleanup`、`handoff` | 正式 quality 仍为 Revise；旧响应结构校验 exit 0 不代表语义通过。cleanup 只扫描、未 apply；其余需刷新 |

风险和最终门禁使用同一隔离 repo。下列代理命令仅保留历史复现：7890目前未监听，禁止直接沿用。用户要求处理后Docker Desktop已恢复原系统代理，手动HTTP/HTTPS地址清空；当前直连Docker Hub仍超时，须先取得可用出网条件，不修改镜像或跳门禁：

```bash
export MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2"
export HTTP_PROXY=http://127.0.0.1:7890
export HTTPS_PROXY=http://127.0.0.1:7890
export NO_PROXY=localhost,127.0.0.1,::1
```

## 任务状态

| ID | 目标 | 状态 | 完成条件 |
|---|---|---|---|
| A1 | 领域与状态机基线 | passing | 聚焦基线与架构检查通过 |
| A2 | 本机 Raft Adapter | passing | 唯一 Runtime、状态回调及同步边界明确，Broker 测试通过 |
| A3 | Broker 对端 Adapter | passing | 生产调用方迁移，身份、超时、执行器与失败语义保留 |
| A4 | Gateway 投递 Adapter | passing | Frame 应用迁移，结果、指标与背压行为保留 |
| A5 | 删除万能入口并收尾 | passing | 归档前最终门禁与独立 quality 满足验收要求 |

## 当前关闭账本（2026-10-20）

用户要求先确定后续认证设计、完善 Broker 新手文档及复用 Harness 规则，再关闭本计划。A1–A4 已实施；本轮代码和文档收尾完成，对应独立 Review 无代码 finding。A5 在代理修复后恢复 `active`，仅继续既有最终验证。

| 类别 | 当前处置 | 尚需保留的边界 |
|---|---|---|
| 已取代步骤 | Builder 一次性消费、旧 LocalBroker/通用 Adapter、旧 State/Repository、请求期限、整轮预算和起点转换等保留历史 | 按后续批准决定核对当前代码，不批量勾绿、不伪造历史 RED、不重做已取代架构 |
| failDrain 旧 finding | 已关闭。两条超时路径仅在提交 accepted 后宣称 FAILED；rejected 等待，异常传播 | 聚焦 RED/GREEN 与真实状态机回归保留，未改超时、协议或状态枚举 |
| Follower 本地候选 | 原 Leader-only 阻塞在本地候选及三项 TLS HA 范围解除，制品仅安装于隔离 repo | 不宣称已发布、双构建复现或完整生产交付 |
| 私有制品发布 | 用户已明确后轨，由私有制品设计拥有 | 不追加上游双构建作为本地修复前置；生产发布保证仍未获得 |
| 认证原型与新设计 | 原型有限完成，统一 invoke 生产设计已独立审查 | O1–O4 用户选择与技术可行性仍阻塞实施；自然重连、轮换等未证项不标 passing |
| 文档与 Harness | 新手路线、角色/版本/协议分工和最小复用规则已完成，限定 Review Accept | 不把未来能力写成当前事实，不新增类名例外 |
| 最终证据 | 原 Docker Hub token 网络阻塞已解除；代理后 full 暴露 HA 选举空窗等待问题，聚焦修正已验证 | 冻结后重跑 full 及其余 fresh 门禁，刷新独立 quality；尚不归档 |

### 收尾 Review 与日志索引

- **failDrain**：新增 9 个测试调用；行为 RED 23 项/4 failures 后 GREEN 23 项无失败错误。日志 `/tmp/broker-closeout-drain-{red,green}.{log,exit,xml}`。reviewer `4da4164b-ba32-4e4` 与 `2ede5d35-2602-419` 的 Accept 记录保留。
- **统一 invoke 设计**：reviewer `5151be08-386f-491` Accept-as-design-baseline；不实施新生产代码。
- **Broker 文档/Harness**：四份拥有者文档和最小规则增量获 reviewer `2aa6d774-dc38-4be` 限定 Accept。
- **正式 quality**：reviewer `dfeff792-a36c-408` 对 12 单元结论为 Revise，仅保留最终证据缺口，确认 failDrain finding 关闭。原样响应结构校验 exit 0，未改语义分数或降级。

### 环境排障与最后检查点

1. **原网络失败**：routing-ha 两次 exit 17，原 Temurin 镜像的 `auth.docker.io` 匿名 token 请求直连超时。宿主直连 12 秒超时，经现有 `127.0.0.1:7890` 代理 HTTP 200，未记录 token。日志 `/tmp/broker-closeout-routing-ha.log`、`/tmp/broker-closeout-resume-routing-ha.log`。
2. **旧最终门禁**：quick exit 0/481 秒、clean exit 0/19 秒；full exit 17/548 秒，完成 258 项无失败/错误/跳过后，在 SkyWalking 镜像 token 请求超时，未进入完整 Maven verify。日志 `/tmp/broker-closeout-final-{quick,clean,full}.log`，当时 `worktreeStable=true`。
3. **获批代理调整**：备份 `/tmp/im-chat-docker-proxy-backup/`；Docker Desktop 停机后仅将 `ProxyHTTPMode` 改为 manual，HTTP/HTTPS 代理设为 `http://127.0.0.1:7890`。daemon 拉取原 Temurin/SkyWalking 镜像成功；BuildKit 仍需命令级代理环境变量。未更换镜像或仓库。
4. **环境副作用**：原运行中的 Redis 容器 `friendly_golick`（`441fc6a49276`）stop 后已不存在，恢复 start 失败。怀疑自动删除，但缺完整 inspect，原因未确认；未猜测重建或覆盖数据，不能宣称已恢复。
5. **HA 网络恢复**：代理下 routing-ha exit 0/25 场景，owned resources 清理通过。日志 `/tmp/broker-closeout-proxy-env-routing-ha.log`。
6. **低磁盘处理**：镜像构建后删除本仓 ignored、可再生成的 10 个 exec.jar（约 1.35 GiB）及本轮精确 BuildKit `source.local` 缓存 174.6 MB；未删镜像、卷、报告或源码。
7. **最新 full 失败与修正**：同代理 full 首轮 exit 1，`AutomaticFailureTlsHaTest.awaitAutomaticRemoval` 在选举空窗直接 `Optional.orElseThrow`，未等至既有预算。只改测试为 leaders 列表 `hasSize(1)` 断言后 `getFirst()`，使 Awaitility 可重试且要求唯一 Leader，未改故障、时间或阶段断言。聚焦真实 TLS 三项 exit 0。日志 `/tmp/broker-closeout-proxy-full.log`、`/tmp/broker-closeout-ha-leader-wait.log`。

## 详细任务

### A1 固定领域状态转换和 Raft 回调行为

**文件范围**：`domain/cluster/model/BrokerCluster.java`、原 `adapter/cluster/raft/RaftBrokerClusterStateMachine.java` 及 `BrokerClusterTest`、`BrokerRaftAdapterTest`、`BrokerLeaseGrantAtomicityTest`，均归 Broker Server；状态机后续迁移见批准的类型化增量。

- [x] 固定 operation/revision/fence 校验、epoch 推进、命令分派、快照恢复及同步边界；领域不依赖 Raft 类型。
- [x] 原聚焦测试覆盖规则与原子性，聚焦 Maven 命令及 `./scripts/verify.sh architecture` 通过。原始逐项记录见[历史 A1](../history/2026-10-20-broker-adapter-boundaries-execution-history.md#a1-固定领域状态转换和-raft-回调行为)。

### A2 收敛本机 Raft Adapter

**文件范围**：Broker Server `adapter/cluster/`、`BrokerClusterAdapter.java`、`LocalBroker.java`、`BrokerClusterConfiguration.java` 及 Raft/装配测试；后续删除或嵌套视图决定见历史。

- [x] `BrokerRaftAdapter` 持有唯一 Runtime，负责状态回调、快照、共识提交、membership、ReadIndex 和 close；日志、恢复和 Lease 签发保持同一状态机同步边界。
- [x] 未新增领域转发服务或领域技术依赖；`mvn -q -pl im-broker/im-broker-server -am test` 通过。格式变更以后续批准的 v2 契约为准，不沿用初期「格式不变」作为当前约束。

### A3 拆出 Broker 对端通信 Adapter

**文件范围**：Broker Server `adapter/cluster/BrokerPeerAdapter.java`、旧 Adapter、Cluster/Membership/Frame 应用服务及对端身份、超时、fence、Lease、cleanup、lookup 测试。

- [x] 将读取证明、Lease 准备/安装、fence、cleanup 和 lookup 移至 `BrokerPeerAdapter`，生产调用方不再借旧 Adapter 发起 RPC。
- [x] 保留身份、超时、peer/lookup 独立执行器、单跳和 deadline 约束；quarantine 与 cleanup 证据仍由应用服务协调，Broker reactor 通过。后续观察 RPC 的独立契约变化见自动观察设计及历史，不泛化到普通路由调用。

### A4 拆出 Gateway 投递 Adapter

**文件范围**：Broker Server `adapter/gateway/GatewayDispatchAdapter.java`、`FrameDispatchAppService.java`、`BrokerClusterConfiguration.java`、旧装配及 Frame/Gateway 写帧测试。

- [x] 移出 dispatch、endpoint 转换、结果映射、专用 executor 和指标，Frame 应用接入独立 Adapter。
- [x] 保持逐连接结果、失败分类、指标和背压；聚焦测试、`quick`、`architecture`、`drift` 通过，完整证据保留于历史 A4。

### A5 删除万能入口并收尾

**文件范围**：旧 `BrokerClusterAdapter`、三个专用 Adapter 及调用方/fixture；已批准的各增量精确文件清单保留在[历史执行记录](../history/2026-10-20-broker-adapter-boundaries-execution-history.md)。当前收尾涉及 `AutomaticFailureTlsHaTest.java`、Broker 四份文档、最小 Harness 规范及计划/索引/进度文档，不重新授权其他代码变更。

- [x] 删除旧万能入口、对端/Gateway 委托和过渡 fixture，生产调用方使用专用 Adapter。
- [x] 完成已批准增量、当前文档和限定代码 Review；原始未勾选项按后续决定分类保留，不批量改为通过。
- [x] 关闭 failDrain 旧 finding，交付后续统一 invoke 设计基线及 Broker 新手文档/Harness 最小规范，限定 Review Accept。
- [x] 记录获批代理调整、网络恢复、Redis 容器未恢复副作用及低磁盘处理；不隐去历史失败。
- [x] 修正 HA 选举空窗等待，真实 TLS 三项聚焦 exit 0；不将此窄验证当作 full 通过。
- [x] 冻结代码和文档，使用既有代理/隔离 repo，按窄到宽刷新受最终变更集影响的模块、architecture、behavior、startup、e2e、routing-ha 证据；失败先修复或明确归因，不以后层通过覆盖前层失败。
- [x] 原 quick→clean→full 字面顺序已由用户批准并实施的去重合同取代，不宣称该旧序列在最终输入下执行。当前一次full包含readiness/clean/父自测、一次完整reactor和真实HA，严格同轮风险证明与独立quality已完成，实际成功证据见恢复状态顶部。
- [x] 更新独立 `quality`，执行 `cleanup` 扫描及 `handoff`；Revise/Block 原样保留，不手改分数。仅审查 allowlist 后才可考虑 cleanup `--apply`，本轮不默认删除临时状态。

全部实施步骤及归档前验收满足后，才将 A5 标为 passing，原样移动至 completed，并同步索引和 `PROGRESS.md`；未经用户要求不提交 Git。归档与关闭审计是计划生命周期操作，不是可提前勾选的实施步骤。

#### Gossip 诊断辅助归位与 Adapter 改名（实施证据）

本标题保留为既有设计链接入口。该增量已完成限定实现 Review 与窄验证，详细文件清单、RED/GREEN、逐字等价证明和历史恢复记录见[原始实施证据](../history/2026-10-20-broker-adapter-boundaries-execution-history.md#gossip-诊断辅助归位与-adapter-改名实施证据)。不以该检查点替代 A5 最终门禁。

## 恢复状态

**最终实施验收（覆盖以下历史）**：A1–A5passing。精简计划新合同仅去除重复执行，不减少断言；最终464d59363acb指纹 `./scripts/verify.sh full` exit0/1375秒、2043项/0failures/0errors/2显式可选MySQL条件skip，工作树稳定，gate与后处理成功 `/tmp/audit-fixed-final-full.log`。同轮architecture73/startup17/behavior41/E2E1及真实HA25/清理passed，使用actual-reactor provenance而非重跑假记录。formalReviewer cf150c61当前scope d9fdcc3cb631 Accept、Audit启动Q1关闭；quality/cleanup/handoff均exit0 `/tmp/audit-fixed-final-{quality,cleanup,handoff}.log`。满足本计划机器及独立Review缺口；归档后状态内容差异独立Review＋drift/clean/quality/cleanup/handoff，不重复Java/HA，原full stale照实保留。此passing不声称历史首次Raft复制偶发根因已修、私有JRaft已发布、未来Bolt O1–O4已解决或原Redis已恢复；这些残余边界按关闭账本继续保留。以下“blocked/待full/质量Revise”均为历史检查点，不是新的并行任务。

**本轮中断恢复入口（优先）**：第一层architecture18秒失败为Broker jacoco.exec EOF。用JaCoCo真实ExecFileLoader读36份，仅Broker失败 `/tmp/a5-jacoco-read-audit.log`；原58MiB文件及SHA256移至 `/tmp/a5-truncated-coverage-evidence/` 保留，重生成RaftSpike6项+report通过 `/tmp/a5-jacoco-recovery-narrow.log`，无源码改动。随后同指纹e701f350e3a0恢复architecture148秒/startup220/behavior37/e2e38/quick542/clean24通过；父整组工具限制2400秒到期，full1375秒failed，258项无failure/error，HA子命令canceled+124 `/tmp/a5-resumed-pre-full.log`，未进入完整Mavenverify。不能把外层取消误判成场景自身900秒超时或算法失败；无继续Maven进程。此次受外层预算影响，不再自动重跑。磁盘当前约12GiB。A5blocked/active任务0，下次先只读核对HA ledger与日志以确认取消来源/自有资源清理，再单独以足够外层预算执行所需门禁（不能再将全部步骤塞2400秒），不延长场景自身期限。quality/cleanup/handoff未执行，旧passing不冒称当前fresh；本轮不归档。

**本次恢复入口（覆盖以下历史）**：用户批准后按显式清单删除76个Swift/Xcode可再生成缓存目录，跳过5个symlink，约释放3.21GiB `/tmp/a5-tmp-cache-cleanup-result.json`；2026-10-21恢复前可用7.2GiB，较上次失败前多约3GiB，仍低于建议10GiB，明确保留空间波动风险。私有Maven/其他任务源码补丁日志/容器卷不动。A5active/WIP=1，冻结一次归档前验收批次，所有命令失败立即停，不追加源码/文档优化；通过才迁移归档、先drift，再关闭审计。批次之间仅清单内ignored execjar可回收，不删其他输出/证据；若空间再次不足则blocked不自动重跑。日志 `/tmp/a5-resumed-pre-*`、`/tmp/a5-resumed-post-*`，最终机器结果由Harness拥有，归档后不再改tracked文件。

**最新阻塞（覆盖后文历史）**：用户准许清理后仅移除10个ignored execjar约1.35GiB，可用4GiB；归档前全组通过1ff55fcd1c08，full2041项无failure/error。归档后第一道drift和architecture/startup/behavior/e2e/quick/clean通过312819398911，但full896秒exit1，647项/0failure/error后Broker SpringBoot repackage明确No space left on device（日志4399行） `/tmp/a5-final-postarchive-full.log`。磁盘仅140MiB，停止无重试；随后仅清同白名单execjar释放1437997672bytes `/tmp/a5-disk-exhaustion-exec-cleanup.json`，可用约1.5GiB，未删Docker容器/镜像/卷或私有repo/证据。归档前后构建空间峰值被低估，不能再仅靠jar清理启动full。已恢复active、A5blocked/active任务0，下一步只盘点大项并确认安全清理范围，必须先取得足够空间余量，不擅自清全局Docker或用户数据。归档后quality/cleanup/handoff未执行，旧记录不当作当前通过。

**最终归档入口（覆盖后文历史）**：归档前指纹1ff55fcd1c08下architecture/startup/behavior/e2e/quick/clean/full/quality/cleanup全部passed，handoff exit0，日志 `/tmp/a5-final-prearchive-*`；full950秒、2041项/0failures/errors/2skipped。A1–A5 passing，active任务0，具备归档前条件。保持原计划迁移并同步两索引/PROGRESS，之后冻结tracked文件，先drift再同组门禁关闭审计，日志 `/tmp/a5-final-postarchive-*`。最终结果由Harness当前指纹记录拥有，不再为回填机器结果编辑tracked文件；失败恢复active/重新打开A5。语义Review scope明确排除active/completed计划及PROGRESS，可在scope不变时跨归档复用。保留历史失败、Raft偶发根因未明、私有JRaft后轨、未来认证O1–O4以及Redis未恢复事实，不以passing消除这些边界。

**最终有界窗口（覆盖后文历史）**：用户批准先清磁盘再继续。仅删除10个gitignored可再生成target/*-exec.jar约1.35GiB，证据 `/tmp/a5-space-exec-jar-cleanup.json`；可用约4GiB，Docker仅盘点、未删除容器/镜像/卷，隔离repo/证据保留。导航修正静态Review/独立quality Accept，旧审计失败未隐去。A5 active/WIP=1，冻结仓库后完成architecture/startup/behavior/e2e/quick/clean/full/quality/cleanup/handoff；通过才标passing并迁移计划、索引和PROGRESS（精确活跃计划：none）。归档后第一道drift；通过后才启动关闭审计同组门禁。所有日志 `/tmp/a5-final-prearchive-*` 和 `/tmp/a5-final-postarchive-*`；任何失败停止，不改timeout/跳test/重试full。归档之后结果由Harness拥有，不再编辑tracked文档制造指纹循环。

**最新审计失败入口（优先）**：归档前全套已通过，归档后architecture/startup/behavior/e2e通过，但quick在17秒drift失败 `/tmp/broker-archive-audit-quick.log`，后续clean/full未执行，旧通过不覆盖新指纹。遗漏为PROGRESS无active时须写检查器契约“active plan: none”；四处文档仍链接active计划（Broker ARCHITECTURE两处、Server README、im-raft README）。已按规则恢复本计划active目录、A5 blocked/active任务0，重新打开最终门禁步骤。已盘点：Broker Architecture两处、Server README、im-raft README改为稳定active/README计划索引，去掉过时active/全仓未完成措辞，保留私有发布后轨。PROGRESS当前使用“活跃计划：”前缀；下次无active时必须精确写“活跃计划：none”，不能仅写当前实施任务none。`./scripts/verify.sh drift` exit0 `/tmp/broker-archive-links-drift.log`，diffcheck通过；先前误用check-drift模式exit2仅usage、未执行checker，已纠正为真实drift。本轮未full/归档；当前active下通过不冒称completed路径已验证。三份运行文档修正及剩余7design/1history的12处path-only稳定索引链接已限定reviewer `0fd48d4c-aaa5-41c` Accept；移除原计划Gossip anchor，历史正文/label不变。worker内存模拟active计划不存在/completed存在/索引迁移，含untracked共29本地路径0broken；真实drift exit0 `/tmp/broker-archive-navigation-drift.log`，只证明当前状态。独立增量quality reviewer `b37c696c-28e1-460`语义Accept，脚本quality exit0 `/tmp/broker-archive-navigation-quality.log`，scope204c37b3；其余单元继承前独立审查，未重新全量源码审查，旧响应保留 `.harness/quality/broker-pre-navigation-review.json`。本轮无full/再归档，尚未证明新指纹机器验收；下一步先集中最终状态文件/索引，再建立有界验证窗口，归档迁移后的第一道必须drift，之后才昂贵门禁。磁盘仅约2.6GiB，启动前须核对空间，不能擅自删镜像/卷或用户文件。归档前全部通过与独立Accept不抹去本次审计失败。

**归档恢复入口（覆盖后文历史）**：全部任务passing，active任务0。归档前指纹24f1b52b4770下architecture/startup/behavior/e2e/quick/clean/full全部passed且worktreeStable=true；full2041项/0failures/errors/2skipped，916秒，日志 `/tmp/broker-final-reviewed-{architecture,startup,behavior,e2e,quick,clean,full}.log`。quality独立Accept，cleanup扫描及handoff通过，日志 `/tmp/broker-final-reviewed-{quality,cleanup,handoff}.log`。Reviewer `06d06476-99dc-45c`，语义scope cf0cf8a4；原Revise保留 `/tmp/broker-final-independent-review-revise.json`，README唯一finding已复核关闭。此时满足归档前条件，移动后冻结所有tracked文件，执行architecture/startup/behavior/e2e/quick/clean/full/quality/cleanup/handoff关闭审计；结果只写Harness，不再编辑本计划制造指纹循环。失败恢复active并打开A5。Raft首次复制偶发未证明根因、私有制品后轨、未来认证开放项、Redis未恢复副作用继续保留，不以全套通过消除这些边界。

**当前收尾窗口（覆盖下方历史）**：A5 active，WIP=1。原冻结指纹0d898a下full/quick/clean/behavior/e2e均通过，full2041项/0failures/errors/2skipped。独立reviewer `06d06476-99dc-45c` 唯一Revise为Broker README现行失败叙述；已改稳定证据入口并复审Accept，旧Revise完整保留 `/tmp/broker-final-independent-review-revise.json`。当前quality Accept语义scope cf0cf8a4；README改动后machine证据须刷新，不能沿用旧fresh。集中状态回填后冻结执行architecture/startup/behavior/e2e/quick/clean/full及cleanup/handoff；仅一个批次，不自动重试。不增加实现，保留Raft偶发首次复制根因未明、私有制品后轨和Redis未恢复环境副作用。通过后先满足归档前验收，再移动计划并做归档后关闭审计；未取得证据不勾绿。

**当前批次（优先）**：A5 active/WIP=1。确定性cadence自测通过后，父级Harness完整exit0 `/tmp/cadence-parent-harness-green.log`；输出无错误stub、无同仓Maven、7890可用、约4.1GiB可用。基于采样测试边界修正及父级复核的明确新证据，仅恢复一个full `/tmp/broker-closeout-cadence-fixed-full.log`，期间冻结仓库编辑；新失败或指纹变化则停止，不自动重试。历史Raft首次复制失败仍未复现且根因未证明，保留诊断与风险，不用cadence修正冒称解决它。下方旧“最新”均为历史恢复记录。

**最新恢复入口（优先于下方历史段落）**：A5 blocked，active任务0。RPC边界诊断后指定用例、测试类均单次通过，Broker Server模块一次763项/0failures/errors/skipped（RaftSpike6项）`/tmp/broker-rpc-diagnostic-module.log`。基于该分层证据仅恢复一次full `/tmp/broker-closeout-rpc-observed-full.log`，exit1/97秒、worktreeStable=true，未进入HA/Java；新增诊断准确指向test-load-test-harness.sh:565资源采样cadence Ruby断言，interval出现1.581秒超过原1.5秒，非原fake doctor/ps竞争。此次不放宽断言或继续full，窄层核对发现生产已扣采集耗时，旧fixture注入0.1秒却允许1.25中位数，既会放过完整sleep回退又会因宿主1.581秒调度失败。设计/实现 reviewer `ac6ab206-a99b-471` scopedAccept后，仅自测改为唯一提取生产内联Node表达式、vm固定时钟0/100/600/1000/1500ms精确向量，完整sleep变异在100ms稳定RED（1.000!=0.900） `/tmp/load-resource-cadence-red.log`、原表达式GREEN `/tmp/load-resource-cadence-green.log`。真实采样保留0600/5.5秒至少4条/schema/正递增timestamp，去掉把主机间隔当算法的上限；production周期/sleep/预算均不改。子脚本全套exit0 `/tmp/load-resource-cadence-subtest.log`，bash-n/diffcheck通过；未运行full，下一步仅父Harness集成复核。历史Raft首次复制失败仍未复现/根因未证明，不能以模块通过抹去稳定性风险；下方“当前”原始记录不是并行任务，计划尚不归档。

- **当前任务**：A5 `blocked`，active任务0。进程探测竞争窄修后，父级Harness完整通过 `/tmp/harness-ps-fix-parent-green.log`；无同仓Maven/错误class stub、代理可用后仅恢复一次full `/tmp/broker-closeout-ps-fix-full.log`，exit1/881秒、worktreeStable=true、1222项/0failures/1error。Harness自测及25个容器HA场景通过后，Maven中RaftLibrarySpikeTest.electsNewLeaderAndRecoversFailedVoter在恢复节点追值检查超时：原10秒期限预期10实际0（awaitValues:233，场景:101），该类4项/1error，Broker761项/1error。按约定停止全套。精确核对失败行后纠正：失败在首次apply10后的awaitValues，尚未停Leader或重启，不能称恢复追赶失败。用户批准仅加诊断并单跑该用例：新增懒断言描述各节点role/leader/log/committed/applied/value/FSM error，onError保留super行为，10秒和值断言不变；单场景exit0 `/tmp/raft-first-apply-diagnostics-once.log`，未复现/未宣称修复。独立reviewer `106bf4ed-f03f-40d` 首轮指出inactive日志getter可能覆盖断言，窄修为诊断专属IllegalStateException→unavailable且先保留value/FSMerror，stopped不访问node；新增guard单测试exit0 `/tmp/raft-first-apply-diagnostics-guard.log`，第二轮scopedAccept。原值0根因仍未证明。用户继续后仅运行RaftLibrarySpikeTest整类一次exit0，5项无失败/错误/跳过（约6.7秒）`/tmp/raft-first-apply-class-check.log`，未运行full。原full日志进一步显示：60410节点能向60411/60412发PreVote并收到响应，但对端发往60410的PreVote/RequestVote/AppendEntries持续RPC timeout至原10秒等待结束；60411/60412已选主且能互通。启动日志称三服务成功，未发现60410此前同日志端点占用记录，不能据此排除外部抢占或服务处理问题；证据支持定向通信/响应异常而非已证明应用算法故障。继续只读核对sources.jar/bytecode：RaftGroupService.start在节点初始化后启动RPC，Bolt RpcServer.doStart执行bind.sync，客户端由各Node的AbstractClientService持有并shutdown，并无已证明的跨Node全局共享RpcClient。原日志无绑定冲突、BufferUnderflow或FSM错误证据，不能把“连接复用污染”当已确认原因；现有日志没有60410的入站处理/发送响应记录，也无故障现场指标，根因仍未闭合。未修改源码或再次跑测试。用户批准后仅该测试增加RpcServer/Processor/Context委托诊断，保留原三个executor（两个显式+默认ping）分配/名称/大小/selector与service资源所有权。计数received表示进入processor、responseSubmitted仅表示提交给delegate不代表wire完成、handlerFailure仅同步逸出异常、connectionClosed表示回调；各节点run独立，停止可读/restart重置，不用console。无网络helper单测及实际选举恢复用例各运行一次exit0 `/tmp/raft-rpc-diagnostics-{guard,once}.log`及.log.exit；原故障未复现，不宣称根因已修。限定diff `/tmp/raft-rpc-diagnostics.diff`、基线 `/tmp/RaftLibrarySpikeTest.before-rpc-diagnostics.java`，独立 reviewer `2944612b-0f88-451` 中止恢复后scopedAccept。未加就绪条件/探针/重试，不改超时或生产。后续若失败可用上述边界计数区分请求未入站、处理无响应提交、响应链/客户端问题；当前仍不能凭通过推断历史链路故障原因，不继续full。用户要求继续，readiness通过，扫描2668个class无Unresolved compilation problem stub，无同仓Maven进程，现有代理可用；恢复依据为IAM模块全测及全输出扫描通过。冻结输入执行 `/tmp/broker-closeout-output-clean-full.log`，再失败停止不自动重试。原Harness子级两次/父级一次窄复核通过后仅恢复一个quick/clean/full批次：quick、clean exit0，full exit1/786秒、worktreeStable=true，1805项/0failures/8errors。自测和容器HA25场景通过后，IAM两类旧包路径测试class直接抛Unresolved compilation problem，日志 `/tmp/broker-closeout-final-batch-{quick,clean,full}.log`；没有自动继续重跑full。
- **本轮新诊断与修正**：稳定full `/tmp/broker-closeout-output-clean-full.log` exit1/62秒，新增诊断准确指向fake doctor正例，不是恢复健康fixture；未进入HA/Java测试。wait_pid_with_timeout在kill -0后子进程退出时，ps|tr可返回非零，被errexit/pipefail提前退出，尚未执行wait。原函数确定性反例证明exit1（本应child0），新fixture从实际脚本提取函数、stub状态探测丢失及wait返回0/23，RED `/tmp/load-harness-ps-race-red.log`；仅该探测失败归为空状态、由原wait收集命令退出码后，子脚本全套GREEN `/tmp/load-harness-ps-race-green.log`，bash-n/diffcheck通过。独立 reviewer `97bf051d-c75b-462` scopedAccept；不能证明是所有历史失败的唯一原因，也不声明探测工具故障时有硬时限（fallback对非竞态探测故障同样适用，与原空状态策略边界一致）。未改任何预算/业务断言，不自动恢复full；下一步先父Harness单层复核。
- **旧编译产物统一处置**：源码声明security.oauth包、两旧class仍在security目录，javap确认抛编译错误且正确包class存在；只留样移除ignored错误class `/tmp/broker-closeout-stale-iam-classes/`，正确包两类聚焦和IAM模块test均exit0 `/tmp/broker-closeout-iam-{stale-focused,module}.log`，源码/POM/业务断言不改。一次性扫描2670个compiledclass，另发现Message两旧嵌套错误class，正确包版本存在且同样留样隔离至 `/tmp/broker-closeout-stale-message-classes/`；扫描证据 `/tmp/broker-closeout-compiler-error-scan.txt`，未为通过删合法测试。原Harness偶发exit1根因仍未知，父复核通过不是宣称根因修复；后续不再逐模块碰运气，先保证输出与输入独占再安排最终窗口。
- **本轮窄层诊断**：`bash -x scripts/test-harness.sh` exit1 `/tmp/broker-closeout-network-restored-harness-trace.log`，最后子脚本为test-load-test-harness；该子脚本跟踪在180秒工具时限未完成 `/tmp/broker-closeout-load-harness-trace.log`。最后位于管理服务重启健康fixture，不等于已证明其5秒子预算是根因；read-only调查 `e3cefc22-87f0-42f` 明确无时间证据。仅提取原临时fakefixture末尾restore单独运行exit0，确实BASE_READY `/tmp/broker-closeout-transient-restore-only.log`，因此不能把它当修复或据此再次跑full。后续仅在test-load-test-harness脚本头部增加顶层ERR诊断：冻结原exit/line/command/options，errexit非预期失败输出，set+e预期负例保持；未改健康预算/断言/生产代码。bash-n、退出23+精确command最小反例、预期set+e静默检查通过；独立 reviewer `58cb2807-860e-498` scoped Accept，说明Bash末命令exec等路径不保证trap覆盖，不声称通用诊断。仅该子脚本两次exit0 `/tmp/load-harness-diagnostic-once.log`、`/tmp/load-harness-diagnostic-final.log`，原exit1未复现，不能据此宣称根因修复或再循环full。当前只完成可观测性补强，保留原失败及稳定性缺口；待再次具体诊断证据后窄层修正，不盲目延长时限。HA初始化窄修与原网络/其他失败历史保留。
- **已完成**：本轮代码/文档收尾、限定独立 Review、代理路径修复、25 场景 routing-ha、HA 等待修正后的三项聚焦。正式 quality 仍为 Revise，计划尚未归档。
- **当前缺口**：HA 测试修正与本次文档整理后的稳定全树最终证据、独立 quality 更新和归档后关闭审计。旧日志只证明各自检查点，不证明当前 fingerprint。
- **最近失败的窄层定位（已完成）**：`/tmp/broker-closeout-proxy-full-last.log` 的 full 退出1且worktreeStable=false，进入Maven后Message出现21个Error。五个错误包路径class由javap确认构造器直接抛Unresolved compilation problem，mtime为旧日期；源码实际包com.co.kc.imchat.common.domain，对应正确class另有一份。只留样并移除target/test-classes/com/co/kc/imchat/domain下五个已确认错误class，证据 `/tmp/broker-closeout-stale-message-classes/`，未改源码/POM/IDE设置。正确包五类聚焦21项exit0 `/tmp/broker-closeout-message-stale-focused.log`；Message模块test exit0 `/tmp/broker-closeout-message-module.log`，不以模块通过覆盖旧full失败。未确定具体写入进程，不归咎某IDE；定位时同仓架构install进程已自然结束，未终止他人任务。
- **有界最终批次结果**：失败层Message模块已通过且无同仓Maven构建后，仅运行一次full `/tmp/broker-closeout-bounded-full.log`，exit1/456秒，worktreeStable=true。Harness自测通过；真实AutomaticFailureTlsHaTest初始化阶段1error：Cluster构造器轮询getConsensusMembership时 Applied configuration unavailable，cause为LogManagerImpl.copyAppliedConfiguration的No applied configuration（非旧Leader-only/API不支持），XML及堆栈保留于当前routing-ha reports。本次未复现Message旧class、端口或网络故障。按新流程停止全套重跑，未改测试忽略异常或私有制品。
- **当前窄层修正（已完成）**：补丁契约确认首份已应用配置缺失时拒绝是预期行为；设计 reviewer `049cd197-c693-497` Accept。仅测试Cluster初始化、ticks启动前对外层Applied configuration unavailable且直接cause No applied configuration的IllegalStateException链重试，其他错误传播、原45秒BUDGET不变，不猜membership。新增awaitInitialMembership测试helper实际用于初始化；旧等待行为RED3项/1failure/1error，GREEN3项通过，联合原三项真实TLS HA共6项exit0 `/tmp/ha-applied-readiness-{red,green,ha}.log`。永久暂缺超时、九类非匹配异常原对象立即传播均断言。生产Runtime/补丁/POM不改，限定diff `/tmp/ha-applied-readiness.diff`，实现 reviewer `0b4151bf-b35c-4e7` scoped Accept/findings none。该窄层依据后已安排一次最终批次：`/tmp/broker-closeout-ready-full.log` exit18/541秒，worktreeStable=true，258项无失败/错误/跳过，包括真实TLS HA初始化修正；随后Docker拉routing-ha-redis时访问registry-1.docker.io失败，明确为代理127.0.0.1:7890 connection refused。随即只读socket确认该端口未监听；未启动代理软件/改配置/跳镜像或继续重跑。后续用户要求撤回失效代理依赖，已恢复Docker原系统代理，当前daemon直连仍超时；下一步先取得明确可用的出网条件并窄层确认daemon与BuildKit，再决定最终窗口。完整门禁仍阻塞，全部验收满足才归档。
- **执行限制**：不实现未来认证设计、不追加私有制品双构建/发布、不创建提交；保留所有无关用户 WIP。长验证使用充分工具时限，Maven 串行，运行期间不回填 tracked 文档制造指纹失效。
- **归档后关闭审计**：归档后冻结 tracked 文件，重跑受状态文件指纹影响的 quality/quick/clean/full 和适用风险门禁。失败则恢复 active，并重新打开受影响任务；不能将关闭审计提前计为 passing。
- **历史入口**：[整理前执行记录](../history/2026-10-20-broker-adapter-boundaries-execution-history.md)保留全部旧「当前」「下一步」、原始勾选与失败，不能作为并行任务重新执行。

## 回滚与残余风险

- **回滚范围**：仅撤销对应增量及其准确限定 diff，不恢复整个混合工作树文件覆盖其他 WIP；需要撤销拆分时先恢复对应 wiring，再定位行为差异，不为放行而放宽规则。
- **核心安全边界**：日志应用、快照恢复和本机 Lease 原子性仍需完整验收；v2 不兼容旧数据，保全旧目录但不读取、不自动删除。
- **私有制品**：本地候选能力已验证，但私有 Registry 发布、双构建/输入锁和生产发布保证未交付；隔离 repo 是当前构建前提，不声称默认仓库可构建。
- **认证能力**：原型未证明自然失效/后台 Reconnector、证书轮换、其他写路径、完整端到端预算或初始化失败注入；未来设计不等于当前生产事实。
- **环境副作用与当前配置**：用户要求处理失效代理后，Docker Desktop已从manual恢复原system配置，清空HTTP/HTTPS覆盖地址；仅恢复原代理相关键，其他配置保留，备份 `/tmp/im-chat-docker-proxy-backup/`。重启前无运行容器；daemon拉redis:7.2.0窄检查不再连接7890，但直连registry-1.docker.io仍Client.Timeout awaiting headers `/tmp/im-chat-docker-system-proxy-check.log`，外网条件仍阻塞，不回切无效代理、不启动代理软件。早先Redis容器 `friendly_golick` 未恢复且原因未确认，继续向用户保留告知。
- **验收证据**：历史缓存容量随机失败、工具超时、磁盘不足、字节码/覆盖率产物损坏、网络和并发指纹变化均保留，不泛称当前代码失败，也不冒充通过。本次文档整理会使受影响的全树 fingerprint 证据需要刷新。
