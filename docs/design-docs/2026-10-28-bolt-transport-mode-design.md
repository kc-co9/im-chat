# Bolt 传输模式独立枚举设计

> 状态：已批准。用户批准提取 `BoltTransportMode` 并要求补齐注释；独立设计 Reviewer `aa6dedabbf9c56d38` Approved、问题 none。实现及完整验收另见现有 T3。

## 摘要与背景

`ImBoltProperties.Client.Mode` 已被客户端、服务端、Gateway SDK 和项目装配共同使用，不再属于客户端配置内部模型。将其提取到 `im-plugin/im-bolt/src/main/java/com/co/kc/imchat/plugin/bolt/model/enums/BoltTransportMode.java`，配置类只绑定该共享传输概念，不再充当枚举容器。

## 目标与范围

新枚举保留且仅保留 `MTLS`、`DEVELOPMENT_PLAINTEXT`，顺序与名称不变。补充中文类级 Javadoc 及两个值的说明，强调双向 TLS 与受限开发明文，不暗示认证失败可降级。客户端和服务端字段、默认值、比较、方法签名、SDK与全部真实调用/测试引用同步为新类型；不保留旧嵌套枚举或兼容转换。

不改配置键、YAML 值、模式校验、TLS/DEV 安全约束、快照内容、生命周期或预算逻辑。不在本次删除或合并 GatewayIdentityConfiguration/BrokerIdentityConfiguration，不做其他类命名清理。

## 所有权与安全

枚举归通用 im-bolt 的 model.enums，不进入 common，也不携带 Broker/Gateway 等项目角色。消费方依赖已有 Plugin 契约，Plugin 不反向依赖业务模块。模式值本身不是身份事实，DEV 仍不能冒充证书认证，MTLS 不回退明文。

## 兼容、运维与回滚

源码和二进制类型名变更，仓库外 Java 消费者必须同步重编译；不提供无调用者兼容层。Spring 按相同枚举名称绑定，配置文本无需迁移；不改变 wire 数据或证书。回滚应将枚举及所有引用一起恢复，不能只还原配置字段类型。

## 备选方案与决策

| ID | 方案 | 决定与理由 |
|---|---|---|
| D1 | 继续嵌套在Client配置 | 不采用；服务端和SDK依赖客户端配置内部名，所属概念不准确 |
| D2 | 独立BoltTransportMode | 采用；表达共享传输模式，保留值和行为 |
| D3 | 放入common或新增抽象接口 | 不采用；这是Bolt专用概念，无多实现需求 |

## 验证策略与执行

等价类型提取不强造行为RED。保存迁移前源码，精确规范化新旧类型及import后对比非注释Java token；既有模式绑定、MTLS拒绝和DEV装配测试保持独立断言。先运行im-bolt的ImBoltAutoConfigurationTest、BoltRequestProcessorTest、BoltDevelopmentModeTest，再运行GatewayClientTest及三个消费方BoltConsumerConfigurationTest。Broker及E2E/HA直接构造/类型引用静态核对；其编译被私有JRaft缺失阻塞时记录缺口，不修改依赖或扩大门禁。既有full/clean/module/architecture暂停、固定前缀Revise、DECODE/TLS失败保留。

设计独立批准后纳入既有T3步骤及PROGRESS；更新im-bolt README中公开枚举所属类型。无新工程规则，不新增checker、豁免或无行为测试框架。独立实现Review需核对枚举值、配置绑定、引用迁移及实际证据；必要验证缺失不计Accept。

## 独立 Review

独立设计 Reviewer `aa6dedabbf9c56d38` Approved、问题 none；核对值与顺序、模式行为、跨模块所属及源码/二进制迁移边界。不代表实现或完整验收通过。
