# Harness Guide

Harness 的目标是让仓库能够解释约束、快速发现偏差并留下可复现证据，而不是持续增加检查数量。业务和编码规则分别由 [Architecture](../../ARCHITECTURE.md)、[Coding Guide](CODING_GUIDE.md)、[SQL Guide](SQL_GUIDE.md)和[Unit Test Guide](UNIT_TEST_GUIDE.md)定义；本文只说明这些规则如何进入自动反馈和 Review。

## 反馈层级

根据问题性质选择最窄、最快且可靠的传感器：

```text
明确语法反模式 -> drift/style checker
稳定依赖边界   -> ArchUnit
业务规则       -> unit/behavior test
编译与集成     -> Maven module test
运行保障       -> metrics/logs/health
无法准确机械判断 -> Review checklist
```

快速门禁不连接 MySQL、Redis、Nacos 或外部网络。涉及真实运行环境的验证应放到集成测试、部署检查或运行监控，不伪装成本地静态规则。

## 当前传感器

| 入口 | 责任 | 典型输出 |
|---|---|---|
| `im-architecture` | 模块依赖、分层、Facade/SDK 和插件边界 | ArchUnit findings |
| `scripts/check-java-style.sh` | 低误报 Java 结构规则 | 文件或包级诊断 |
| `scripts/check-sql.sh` | DDL、Mapper XML 和注解 SQL | SQL 来源及违规原因 |
| `scripts/check-drift.sh` | 聚合静态检查、文档链接和仓库漂移 | 全部违规组 |
| `scripts/test-*-harness.sh` | 检查器正例、反例和非回归 fixture | Harness 自测结果 |
| `scripts/verify.sh affected` | 根据变更范围选择模块 | 受影响模块验证 |
| `scripts/verify.sh quick` | drift、Harness 自测和架构检查 | 日常快速反馈 |
| `scripts/verify.sh behavior` | 路由、Gossip、投递和确认行为 | 跨模块行为证据 |
| `scripts/verify.sh full` | 完整 Maven 与 Harness 门禁 | 交付前证据 |
| `scripts/verify.sh report` | 汇总最近验证和治理信号 | `target/harness/report.json` |

脚本只负责它能准确识别的范围。规范中存在但矩阵标记为 Review 的规则，不得在文档中暗示已自动执行。

## 规则矩阵

| 规则 | 规范所有者 | 自动检查 | Review |
|---|---|---|---|
| 模块与 DDD 依赖方向 | Architecture/Coding | ArchUnit | 是 |
| Facade/SDK 不依赖 server | Architecture | ArchUnit | 是 |
| 插件不依赖业务模块 | Architecture | ArchUnit | 是 |
| 生产代码显式类型、无字段注入 | Coding | Drift checker | 是 |
| 生产测试替身、控制台输出、手动 `ObjectMapper` | Coding/Test | Drift checker | 是 |
| 成组简单 getter/setter | Coding | Java style checker | 是 |
| AppService 标量业务入参 | Coding | Java style checker | 是 |
| 包内能力接口、技术实现和模型明显混放 | Coding | Java style checker 的窄规则 | 是 |
| 领域业务标识使用值对象 | Coding | 不自动化 | 是 |
| 领域服务包含真实业务决策 | Coding | 不自动化 | 是 |
| Adapter 使用领域对象作为业务边界 | Coding | 不自动化 | 是 |
| 条件 Bean 装配闭合 | Coding | 聚焦启动测试 | 是 |
| 不增加测试专用生产 API | Coding/Test | 本轮不自动化 | 是 |
| 测试不使用真实等待 | Unit Test | Drift checker | 是 |
| 禁用测试必须说明原因 | Unit Test | Drift checker | 是 |
| DDL、危险 SQL、`${...}`、静态无条件写入 | SQL | SQL checker + fixture | 是 |
| Mapper `SELECT *` | SQL | SQL checker + fixture | 是 |
| 动态 SQL、索引合理性和执行计划 | SQL | 不自动化 | 是 |

“是”表示 Review 仍需检查语义和例外，不代表重复执行同一机械扫描。

## 从问题到规则

Review finding 同时满足以下条件时，才适合转为自动门禁：

1. 代表真实缺陷或稳定架构边界，而不是个人偏好。
2. 能通过语法、依赖、测试或运行信号准确判断。
3. 当前仓库已满足规则，或例外范围和退出条件明确。
4. 失败信息能够指出问题位置、原因和修复方向。
5. 执行成本符合所在反馈层，不显著拖慢日常验证。

不能稳定机械判断的内容写入其所有者规范并由 Review 检查。例如领域服务尺度、Adapter 业务语义、构造器是否包含校验、方法是否过长，都不使用宽泛正则。

## Fixture 要求

每条脚本规则至少具备：

- 一个应失败的最小反例。
- 一个应通过的正常示例。
- 容易误判时增加一个 false-positive 非回归示例。
- 对应的稳定诊断文本断言。
- 独立临时目录，不读取本机服务或污染工作树。

解析多行 SQL、Java 注解或 XML 时，优先提取结构再判断。无法可靠重建的动态形式交给测试与 Review，不为了扩大覆盖牺牲信号质量。

## 规则生命周期

1. **Proposed**：记录问题证据、预期行为和潜在误报。
2. **Observed**：扫描仓库基线，确认影响范围和历史例外。
3. **Enforced**：增加检查、清晰诊断和正反 fixture。
4. **Monitored**：观察耗时、误报、偶发失败和绕过情况。
5. **Revised or Removed**：架构变化、信号价值降低或已有更强检查时，修改或删除规则及引用。

删除失效规则不是降低质量。持续产生噪声的检查会掩盖真实失败，必须连同 fixture、文档和例外一起调整。

## 维护责任

- 修改模块的人同步更新模块 README、测试和相关局部规则。
- 修改根架构、脚本或 CI 的人评估全仓影响，并更新根级入口和共享规范。
- Review 发现重复问题时指出规范所有者；实现者负责补充规则、测试或技术债务退出条件。
- 没有明确所有者、验证方式或使用场景的规则不进入 Harness。
- 不为让当前变更通过而删除测试、降低断言、关闭规则或扩大排除范围。

## 反馈与报告

[Harness 反馈台账](../feedback/HARNESS_FEEDBACK.md)记录误报、偶发测试和性能问题，至少包含复现证据、所有者、退出条件和状态。开放问题进入每周治理检查；阻塞日常开发的误报应立即处理。

`target/harness/report.json` 汇总：

- Harness 传感器是否存在。
- 最近各验证模式的状态和耗时。
- 最近一次新鲜完整验证的 Surefire 测试统计。
- active/stale plan 和开放技术债务数量。
- Harness 反馈台账中的开放误报、偶发测试和性能问题。

这些信号不等于源码质量评分。耗时用于发现反馈变慢，失败和台账用于定位噪声，不能通过减少测试或降低规则改善数字。

## 维护节奏

- Pull Request：检查新增规则的必要性、精度、诊断、fixture 和执行成本。
- 每周 GC：检查陈旧计划、技术债务和 Harness 反馈。
- 架构或规范变化：同步修改矩阵、传感器和文档入口。
- 出现误报：先登记并增加复现 fixture，再收窄解析器或规则；不要全局绕过。

## 落地示例

Gossip TTL 测试曾通过 `Thread.sleep` 等待过期，结果依赖机器调度。该问题依次沉淀为：

```text
Review finding
  -> Unit Test Guide 禁止真实等待
  -> 可控时间回归测试
  -> check-drift.sh 扫描 Thread.sleep/TimeUnit.sleep
  -> quick/full 持续验证
```

这类链路是 Harness 的基本单位：规范解释原因，测试证明行为，门禁阻止稳定反模式再次出现，Review 处理无法机械判断的语义。
