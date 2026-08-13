# Harness Guide

Harness 与生产代码一样需要维护。它的目标是缩短可靠反馈周期，而不是持续增加检查数量。

## 维护责任

- 修改某个模块的人负责同步更新该模块的文档、测试和局部规则。
- 修改根级架构、脚本或 CI 的人负责评估所有模块的影响，并更新根 `AGENTS.md`、README 或共享规范。
- Review 发现重复问题时，审查者负责提出沉淀位置；实现者负责在当前变更或技术债务台账中落实退出条件。
- 没有明确所有者、验证方式或使用场景的规则不应加入 Harness。

## 从问题到规则

Review finding 满足以下条件时，优先转化为自动门禁：

1. 问题代表真实缺陷或稳定的架构边界，而不是个人偏好。
2. 可以通过语法、依赖、测试或运行信号准确判断。
3. 当前仓库能够满足规则，或例外范围明确且有退出条件。
4. 失败信息能够说明问题和修复方向。
5. 执行成本适合所在反馈层，快速门禁不引入外部系统和长时间等待。

不能稳定机械判断的问题写入编码、测试、可靠性或安全规范，并由 Review 检查。不要为了自动化而引入高误报正则。

## 规则生命周期

每条自动规则经历以下生命周期：

1. **Proposed**：记录问题证据、预期行为和可能误报。
2. **Observed**：扫描当前仓库，确认影响范围和历史例外。
3. **Enforced**：增加自动检查、失败信息和至少一个能证明规则有效的测试或脚本场景。
4. **Monitored**：通过 Harness 报告和反馈台账观察耗时、误报及绕过情况。
5. **Revised or Removed**：规则与架构不再一致、误报成本过高或已有更强检查替代时，修改或删除规则及相关文档。

删除规则不是降低质量。已经失去信号价值的检查会制造噪声，应连同引用、例外和台账记录一起清理。

## 反馈质量

`target/harness/report.json` 记录以下客观信号：

- Harness 传感器是否存在；
- 最近各验证模式的状态和耗时；
- 最近一次成功完整验证对应的 Surefire 测试数、失败、错误和跳过数量；没有新鲜完整验证时状态为 `unknown`；
- active/stale plan 和开放技术债务数量；
- [Harness 反馈台账](../feedback/HARNESS_FEEDBACK.md)中开放的误报、偶发测试和性能问题数量。

这些信号不等于源码质量评分。耗时用于发现反馈变慢，失败和台账用于定位噪声；不能通过删除测试或降低规则来改善数字。

维护节奏如下：

- Pull Request：检查新增规则的必要性、精度、错误信息和执行成本。
- 每周 GC：检查陈旧计划、开放技术债务和 Harness 反馈台账。
- 出现误报或偶发测试：立即登记反馈，写明复现证据、所有者和退出条件。
- 架构发生变化：同步修订规则和文档，不保留与当前设计冲突的历史约束。

## 推荐拓扑

拓扑用于收窄允许的结构变化，不用于批量生成空模块。新增同类能力时优先复用已验证的拓扑，并由架构测试验证边界。

### 业务服务

```text
im-<service>/
├── im-<service>-facade   # RPC contracts, DTOs, errors
└── im-<service>-server   # application, domain, infrastructure, interfaces
```

server 实现 facade；跨服务仅依赖 facade；领域层不依赖 Spring、RPC 或持久化实现。

### 插件

```text
im-<capability>/
├── properties
├── auto-configuration
├── core or spi
└── focused auto-configuration tests
```

插件不依赖 Broker、Gateway 或业务服务；默认配置可独立启动；Bean 支持条件装配。

### RPC Handler

```text
operation enum -> typed params -> handler -> typed result
```

公共模型位于 SDK/facade；handler 只做协议适配和用例委派；声明式转换优先使用 MapStruct。

### Registry

```text
domain registry interface -> infrastructure implementation -> event projection
```

接口表达领域操作而不是存储细节；内存实现保证并发一致性；Gossip、Redis 等同步策略不污染领域接口。

## 典型落地示例

下面使用仓库中的真实改进说明一条反馈如何沉淀为 Harness 能力。

### Review Finding

Gossip 删除状态的测试通过 `Thread.sleep(5)` 等待 TTL 过期。测试结果依赖机器调度和执行速度，时间较短时可能尚未过期，时间加长又会拖慢测试。

### 编码规范

[单元测试规范](UNIT_TEST_GUIDE.md)明确：测试不得使用真实等待验证时间过期，时间应通过 `Clock`、`LongSupplier` 或其他正式抽象控制。

这条规则约束的是确定性，不是代码格式。生产实现仍使用系统时间，但测试能够显式推进时间。

### 回归测试

`InMemoryGossipEntryStoreTest` 使用 `AtomicLong` 作为可控时钟：

1. 在固定时间写入 removed 状态；
2. 显式推进 TTL；
3. 调用 digest；
4. 断言 removed entry 已被压缩。

测试不再依赖线程调度，并直接表达业务时间边界。

### 漂移门禁

`scripts/check-drift.sh` 检查测试源码中的 `Thread.sleep` 和 `TimeUnit.*.sleep`。规则加入前先扫描全仓并修复现有实例，因此启用时没有历史噪声。

```text
Review finding
  -> 确定性测试规范
  -> 可控时间回归测试
  -> 漂移门禁
  -> quick/full/CI 持续验证
```

如果未来发现该规则误判合法的并发测试，应先登记反馈并评估更准确的同步方式，而不是直接增加全局排除。
