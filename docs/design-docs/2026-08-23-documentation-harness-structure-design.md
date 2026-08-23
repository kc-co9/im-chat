# Documentation And Harness Structure Design

## 背景

项目已经完成一轮以 Harness 驱动的 SQL、认证、会话、Broker 控制链路、指标和聊天视图状态开发。实践中形成了新的稳定约束，但当前文档存在三个问题：

1. 根 README 同时承担业务介绍、领域建模、架构、运行、Harness 和开发规范，篇幅过长且内容重复。
2. `HARNESS_GUIDE.md` 混入大量编码规则正文，与 `CODING_GUIDE.md` 的职责重叠。
3. 已完成计划和设计文档仍保留中间方案，例如 local profile 临时 JWT 密钥、认证结果 `valid` 字段和已经更名的领域类型，容易被误认为当前实现。

本次整理不改变生产行为，也不新增宽泛的自动检查。目标是让开发者和 Coding Agent 能快速找到当前事实、实现约束和验证方式。

## 文档分层

文档按“入口、当前事实、稳定规范、决策过程、执行记录”分层：

| 层级 | 文档 | 唯一职责 |
|---|---|---|
| 项目入口 | `README.md` | 项目概览、核心业务链路、模块地图、启动入口和文档导航 |
| 当前架构 | `ARCHITECTURE.md` | 当前运行拓扑、模块边界、依赖方向和数据所有权 |
| Agent 规则 | `AGENTS.md` | Coding Agent 的执行顺序、修改权限、验证和完成标准 |
| 稳定规范 | `docs/references/*.md` | 编码、SQL、测试、Git、Review 和 Harness 维护规则 |
| 横切保障 | `docs/RELIABILITY.md`、`docs/SECURITY.md` | 当前可靠性与安全保证、风险和验证要求 |
| 设计决策 | `docs/design-docs` | 方案背景、替代方案、已确认决策及其后果 |
| 执行记录 | `docs/exec-plans` | 实施步骤、验证证据和最终偏差，不作为当前架构事实来源 |
| 业务规格 | `docs/product-specs` | 已确认的用户可观察行为和验收边界 |

同一规则只在一个稳定规范中保留完整正文。其他文档使用链接和一句上下文说明，不复制整段规则。

## 根 README 整理

根 README 保留以下一级结构：

1. 项目概览。
2. 核心业务能力与关键实时链路。
3. 当前架构和模块地图。
4. 本地运行与验证入口。
5. 文档导航。
6. 许可证。

README 保留一份简洁的领域词汇表，作为新读者理解业务模型的入口；领域词汇不迁入 `ARCHITECTURE.md`，也不新建词汇专题。完整消息投递步骤链接到已批准的业务规格和相关设计文档，Harness 原理和提交信息示例分别链接到 Harness 与 Git 指南。README 不再维护第二份编码规则和第二份架构事实。

模块 README 只描述模块职责、公开能力、关键配置、运行行为和局部验证命令。跨仓库通用规则链接到 `docs/references`，不在每个模块重复。

## 稳定规范整理

### Coding Guide

`CODING_GUIDE.md` 按开发时的决策顺序组织：

1. Java 类型与可读性。
2. DDD 分层与依赖方向。
3. 应用服务和 CQRS。
4. Adapter 与跨服务边界。
5. Spring、配置和 Bean 装配。
6. Transformer、持久化和缓存。
7. 指标、事件与异常隔离。
8. 命名、Lombok、record 和构造方式。
9. 自动检查与 Review 边界。

本轮实践需要保留的规则包括：

- 不可变属性载体优先使用 `record`；普通可变 Bean 使用 Lombok，避免成组手写简单访问器。
- 应用服务公开用例使用单个 Command、Query 或参数对象；不暴露标量业务入参。
- 领域模型和领域服务使用值对象表达业务标识；时间与枚举可以直接使用，边界基础类型由 Transformer 转换。
- 领域服务必须承载业务决策或多对象协作，纯技术委托不包装成领域服务。
- Adapter 的公开业务输入输出使用领域对象，外部 SDK 模型只停留在 Adapter 内部。
- 聚合重建使用 Builder 或重建数据对象；简单构造不为形式统一强行进入领域服务。
- 不增加仅供测试使用的生产构造器、兼容方法或 no-op 实现。
- 多个相关配置使用 typed `@ConfigurationProperties`；条件 Bean 的消费者和提供者必须具有一致的启用条件，避免缺失依赖导致启动失败。
- 事务、锁和旁路指标优先使用已有注解或模板；指标失败应隔离并记录，不能影响核心业务结果。
- 公共契约、配置项和非显然行为提供简洁 Javadoc，自解释的私有委托方法不要求注释。

### Harness Guide

`HARNESS_GUIDE.md` 只描述 Harness 的治理方式：

- 问题如何从 Review 进入规范、测试、ArchUnit 或脚本。
- 自动化规则需要满足的低误报、确定性、错误提示和 fixture 条件。
- 当前传感器及其责任边界。
- 误报、偶发测试、性能退化和规则退出流程。
- 自动检查与 Review 的规则矩阵。

编码规则的完整解释链接到对应规范，不在 Harness 指南开头堆叠长段落。

### SQL Guide

`SQL_GUIDE.md` 保持独立，明确：

- Mapper SQL 禁止 `SELECT *`，`COUNT(*)` 允许。
- 覆盖根 DDL、Mapper XML 和受支持的 MyBatis 注解 SQL。
- 自动检查危险 DDL、`${...}`、静态无 `WHERE` 写入、命名和必要 DDL 结构。
- 动态 SQL 重建、索引合理性、执行计划和数据规模由测试与 Review 判断。

## Harness 规则矩阵

文档使用单一矩阵说明规则落点，避免“规范写了但不知道是否自动检查”：

| 规则 | 规范 | 自动检查 | Review |
|---|---|---|---|
| DDD 依赖方向 | Architecture/Coding | ArchUnit | 是 |
| AppService 标量入参 | Coding | Java style checker | 是 |
| 成组简单 getter/setter | Coding | Java style checker | 是 |
| 包内职责明显混放 | Coding | Java style checker 的窄规则 | 是 |
| 领域业务基础类型 | Coding | 暂不自动化 | 是 |
| Adapter 领域对象边界 | Coding | 暂不自动化 | 是 |
| 测试真实等待 | Unit Test | Drift checker | 是 |
| SQL 安全与结构 | SQL | SQL checker + fixture | 是 |
| SQL 索引和动态语义 | SQL | 不自动化 | 是 |
| 条件 Bean 装配闭合 | Coding | 启动测试 | 是 |
| 测试专用生产 API | Coding/Unit Test | 本轮不自动化 | 是 |

不新增“每包最多 N 个类”、禁止所有构造器、禁止所有 getter/setter、方法长度等高误报规则。

## 历史文档处理

设计文档保留当时决策，但对已经被后续确认推翻的关键运行行为增加“最终实现说明”。完成计划保留步骤和 RED/GREEN 证据，只允许在开头追加最终偏差摘要；不修改、删除或重新勾选原始任务内容。该追加规则是 `docs/exec-plans/completed/README.md` 中“不得按后续架构改写完成计划”的显式补充，不改变历史证据。

本轮历史注释只修改以下两个文件：

- `docs/design-docs/2026-08-14-centralized-session-authentication-design.md`：追加最终实现说明，记录 JWT 启用时必须提供显式密钥、认证失败抛统一业务异常、成功 DTO 不包含 `valid`，以及最终领域类型以当前源码为准。
- `docs/exec-plans/completed/2026-08-14-centralized-session-authentication.md`：在标题后追加最终偏差摘要，记录 local 临时密钥方案被取消、`valid` 字段被取消、最终的 `SessionService`、`SessionTokenCodec`、`RefreshFingerprint` 等命名；原计划正文保持不变。

Broker 服务发现和 Bolt 启用条件属于当前运行事实，只更新 `ARCHITECTURE.md`、`im-broker/im-broker-sdk/README.md`、`im-service/im-account/im-account-server/README.md` 及其当前配置说明，不写入历史认证计划。

## 受影响文件

| 文件 | 整理目标 |
|---|---|
| `README.md` | 缩短项目入口，保留简洁领域词汇、核心链路、运行和导航 |
| `ARCHITECTURE.md` | 补齐认证、Session version、聊天视图和 Broker 发现的当前事实 |
| `docs/references/index.md` | 按使用场景提供稳定规范导航 |
| `docs/references/CODING_GUIDE.md` | 重排规则并补齐本轮稳定编码边界 |
| `docs/references/HARNESS_GUIDE.md` | 收敛为治理流程、传感器和规则矩阵 |
| `docs/references/SQL_GUIDE.md` | 校对 SQL 规则和自动检查边界 |
| `docs/design-docs/index.md` | 登记本设计并校正当前/历史状态 |
| 两份认证历史文档 | 只追加上述最终实现说明或偏差摘要 |
| Account、Broker SDK、Session 插件 README | 校正当前配置、运行链路和模块职责 |

其他模块 README 只做一致性扫描；没有具体过期事实时不进行格式化或措辞改写。

## 验证

文档整理后执行：

1. 通过 `./scripts/check-drift.sh` 执行现有本地 Markdown 链接检查，不新增重复入口。
2. SQL 和 Java style Harness fixture 测试。
3. `./scripts/check-drift.sh`。
4. `./scripts/verify.sh quick`。
5. 文档或脚本改动完成前执行 `./scripts/verify.sh full`。

不得通过删除规则、扩大排除范围或改写历史证据来使验证通过。
