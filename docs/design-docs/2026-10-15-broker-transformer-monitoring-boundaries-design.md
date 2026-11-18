# Broker Transformer 与 Monitoring 归属设计

> 状态：用户已批准全部范围与命名；独立设计 reviewer `7cf18e69-3af8-449` Accept、findings none，允许纳入既有 A5 实施。

## 摘要

本次仅调整 Broker Server 的转换器和指标观察器归属，不改变转换、采样、查询或装配行为。转换器按实际能力归入 application、interfaces、adapter、registry；指标观察器归精确 `support.monitoring`，作为单独批准的技术观察例外，不放宽其他 support 包。

## 背景

现有 `transformer.domain.BrokerClusterDomainTransformer` 负责 committed state 与领域状态的边界转换；另有 Adapter 专用转换器被放在 `transformer.infrastructure`。两个 MeterBinder 位于 `infrastructure.monitoring`，职责是只读技术观察，并不拥有基础设施资源。

## 目标与非目标

- 目标：用实际职责命名和分包，建立精确架构规则及有效负例，同步当前文档。
- 非目标：不改运行行为，不拆类，不增加 projection 目录、兼容壳或构造器，不迁移 application/interfaces 转换器，不扩大所有 support 的依赖权限，不关闭已有 A5 验收缺口。

## 已知事实与约束

- 所属模块和数据所有权：Broker Server；领域聚合、Registry 和 Adapter 继续拥有原事实，Monitoring 不拥有业务状态。
- 兼容、运行或安全约束：纯内部类名/包名迁移；MapStruct 的生成映射、Spring `@Component` 与 `MeterBinder` 保留；指标名、标签、采样时机、查询及异常行为均不变。
- 依赖输入与环境：当前共享工作树有大量无关 WIP，禁止整体恢复文件或提交。接手归档位于 `/tmp/broker-structural-baseline/tree.tar`，status 位于 `/tmp/broker-structural-initial-status.txt`；readiness 已通过，日志 `/tmp/broker-boundary-readiness.log`。

## 方案概览

```text
transformer.application  -> 应用输出映射（原 2 类保留）
transformer.interfaces   -> 入口映射（原 3 类保留）
transformer.adapter      -> 集群状态及 Raft/Drain/Fence/Lease 边界映射
transformer.registry     -> Registry 边界映射
support.monitoring       -> Spring/Micrometer 只读技术观察器（精确包）
```

## 详细设计

### 转换器迁移

- `transformer.domain.BrokerClusterDomainTransformer` 改为 `transformer.adapter.BrokerClusterStateTransformer`，所有 main/test 引用同步。
- `transformer.infrastructure` 中 `BrokerClusterRaftTransformer`、`BrokerDrainTransformer`、`BrokerFenceTransformer`、`BrokerLeaseTransformer` 移至 `transformer.adapter`，类名不变。
- 同包 `RegistryTransformer` 移至 `transformer.registry`，类名不变。
- `transformer.application` 两类及 `transformer.interfaces` 三类原地保留，只按引用迁移 import/类名，不改变映射方法或生成方式。

### 精确 Monitoring 技术观察例外

`infrastructure.monitoring.BrokerMetrics` 与 `RoutingAuthorityMetrics` 移至精确 `support.monitoring`，直接测试同步包名及目录。保留 Spring 组件与 Micrometer 集成；允许只读 Domain、Registry、Adapter 查询。

禁止依赖 application、interfaces、infrastructure、config、lifecycle；禁止直接网络 client、RaftRuntime 和执行器的拥有/运行职责；禁止业务决策、业务写入；Domain 禁止反向依赖 support。`support.monitoring` 不允许后代包。

此例外不继承纯 `support.gossip` 的禁止 Adapter 规则，也不授权其他 support 使用 Adapter、Spring 或 Micrometer。机械规则保护精确包和稳定类型依赖；只读调用、业务决策及写入语义由 Review 审查，待有低误报的明确写 API 清单或语义 checker 时自动化，不使用类名一次性豁免。

### 迁移与测试

先收集受影响引用及聚焦测试基线，新增架构约束和逐项负例，实际运行架构 red 后才搬迁生产文件。负例覆盖被禁止的层/配置/生命周期、网络、Runtime、执行器、后代包及 Domain 反向依赖；正例证明精确 Monitoring 中 Spring/Micrometer 与只读 Adapter 查询不被错误拒绝。

机械迁移仅改 package、import 和指定类名；通过接手归档对逐文件归一化比对，证明其余字节等价。不要批量改写历史设计/计划引用。

## 可靠性与安全

未修改超时、重试、幂等、并发、权限、网络或资源上界；只读观察不得参与业务授权与状态迁移。指标名称及标签不变，不新增敏感或高基数字段。

## 运维与交付

不增加配置、指标或部署步骤。Spring 扫描根仍覆盖新包。无需运行态数据迁移或兼容包装，不修改 Raft 持久格式。

## 风险与回滚

- 风险：遗漏 caller/import、MapStruct 生成实现残留、将 Monitoring 误套纯 support.gossip 限制，或将例外扩散至所有 support。
- 回滚：仅逆向本轮精确搬迁及架构/文档增量；使用归档核对，禁止覆盖接手 WIP。
- 延后事项：原 A5 的历史 full、routing-ha、行为 finding 及证据缺口保留。

## 验证策略

- 聚焦测试或契约检查：现有 Transformer、BrokerMetrics、RoutingAuthorityMetrics、Raft snapshot/codec 测试；新 LayerBoundaryTest 架构负例先 red 后 green；机械等价及旧引用检查。
- 集成与运行验证：`mvn -q -pl im-broker/im-broker-server -am test`、`./scripts/verify.sh architecture`、`git diff --check`。无 transport/wiring/行为改动，不新增 behavior/e2e/startup 要求。
- 完整门禁：依序 `./scripts/verify.sh quick`、`./scripts/verify.sh clean`、`./scripts/verify.sh full`；有界超时，日志存 `/tmp`，前层失败先归因再继续。独立实现 Review 后记录实际结果，不把窄测试当 full。

## 后续文档与执行

独立设计 Review 通过后，追加既有 [Adapter 计划 A5](../exec-plans/active/README.md) 的精确施工清单与证据，不创建另一 active task，并同步 `PROGRESS.md`。更新 `MODULE_STRUCTURE_GUIDE.md` 的归属规范、Broker Architecture/Server README 当前导航及 `HARNESS_GUIDE.md`、`HARNESS_RULE_MATRIX.md` 的规则、Review-only 范围与自动化条件。根 Architecture、Reliability/Security 不变：本次不改变跨模块拓扑或运行保证。

## 附录 A：备选方案与行业依据

- 保留现有目录：改动最少，但不能准确表达 Adapter 边界映射和只读技术观察归属。
- 所有转换器搬入一个包、所有 support 统一放宽：目录简单，但丢失能力所有权并扩散依赖权限，不采用。
- 采用本设计：仅精确搬迁现有类，保留 application/interfaces，Monitoring 单独例外；无需新增抽象或外部依赖。

## 附录 B：决策记录

| ID | 来源/问题 | 最终决策 | 理由与影响 |
|---|---|---|---|
| D1 | 用户批准最终命名 | `BrokerClusterStateTransformer` 位于 `transformer.adapter`，无 projection 目录 | 表达真实状态边界转换，不制造领域服务语义 |
| D2 | 用户批准转换器分组 | 原四个技术转换器归 adapter，RegistryTransformer 归 registry；application/interfaces 不搬 | 能力归属明确，最小变更 |
| D3 | 用户批准 Monitoring 例外 | 两指标类归精确 support.monitoring，保留 Spring/Micrometer 与只读 Adapter 查询 | 独立技术观察规则，不继承 support.gossip 的纯工具限制，不扩大所有 support |
| D4 | 用户批准施工约束 | 混合工作树隔离归档、先设计 Review/架构 red、A5 WIP=1、不提交 | 可独立审计机械等价，保护现有 WIP 与历史验收缺口 |

## 附录 C：独立 Review

- 独立设计 reviewer `7cf18e69-3af8-449`：Accept、findings none。建议正负 fixture 与生产共用参数化规则，逐名断言违规依赖；保留 Gossip 对 Spring/Adapter 的禁令。施工按该建议执行。
