# 项目角色身份 URI 前缀固定化设计

> 状态：已批准。用户已确认 Broker、Gateway、Message、Account、Ops 五种角色一起固定；独立设计 Reviewer `a7ce1f2aa262d78e2` Approved、必须修改项 none，仅批准本设计，不代表实现验收。本文取代前缀命名设计中这五种项目角色前缀可由部署覆盖的决定；通用 Plugin 身份约束不变。

## 摘要

将项目角色身份命名空间固定在 `im-common` 的 `PeerIdentityConstants`，删除对应的部署覆盖字段、配置项及装配传参。常量由 `DEFAULT_*_URI_PREFIX` 改为 `*_URI_PREFIX`，固定值逐字保留：Broker 为 `spiffe://im-chat/broker/`、Gateway 为 `spiffe://im-chat/gateway/`、Message 为 `spiffe://im-chat/message/`、Account 为 `spiffe://im-chat/account/`、Ops 为 `spiffe://im-chat/im-ops/`。

这是身份命名约定的行为变更，不只是去重。使用自定义角色前缀的部署必须先确认能迁移到固定 URI，不能仅升级二进制。证书 CA、信任材料、完整身份、节点 ID 和地址仍由原有部署及运行流程拥有。

## 背景、目标与非目标

目前五个配置类九个字段允许覆盖角色前缀，SDK、Gossip 设置和授权装配继续传递这些值。此前常量提取仅集中默认值，没有取消覆盖。用户选择统一项目约定，避免不需要的配置自由度和参数传播。

目标是项目目标构造、bootstrap 配对校验及入站角色匹配使用同一常量；配置入口及当前文档不再宣称这些项目前缀可覆盖。非目标是不删除或重新命名 `BrokerIdentityConfiguration`，不取消可信 bootstrap 配对，不修改节点动态刷新、路由一致性、证书验证算法或通用 Bolt/Gossip 能力。

## 所有权与不变量

- common 仅保存无框架依赖的常量，不能反向依赖 Server 或 Plugin。
- SDK 选择目标，Plugin 验证传输身份，接收模块决定角色与业务准入，所有权不迁移。
- 项目角色 URI 前缀只有上述五个固定值；部署属性不再改变这些角色规则。
- 发现地址和响应 metadata 不能制造信任配对。bootstrap 仍由部署显式提供地址与完整预期身份，Broker SDK 校验固定 Broker 命名空间及现有冲突规则。
- committed state 中节点地址和 ID 继续更新，后续目标由最新地址和固定 Broker 前缀加 ID 生成；前缀固定不意味着拓扑固定。
- 通用 `im.bolt.client.allowed-peer-uri-prefixes`、证书 URI SAN、测试用任意 trust domain 和通用解析函数参数不因本次项目约定被删除。Plugin 不引入对 PeerIdentityConstants 的角色依赖。

## 详细设计

删除 Gateway、Account、Message 的 Broker 前缀字段，Broker Gateway push 前缀字段及 routing 下五角色前缀字段。移除对应 YAML 键、相关环境占位符及生产装配 getter 调用，值改由常量提供。删除只为传播这些固定前缀存在的项目级构造参数及状态副本；不机械删除所有名为 prefix 的变量。

保留 BrokerIdentityConfiguration 及 GatewayIdentityConfiguration 对象，保留真实使用的目标列表及校验行为；若构造参数唯一用途是允许项目 Broker/Gateway 前缀变化，则删除该参数并在内部使用对应常量。BrokerGossipSettings、GatewayBrokerAuthorizer、BrokerPeerAdapter 等项目级参数按实际调用语义处理；通用插件参数不改。测试同步实际签名，并将「可覆盖项目角色前缀」断言迁移为「固定角色约定、不接受错误命名空间」契约，不删弱角色拒绝及唯一身份检查。

旧配置键不保留别名，也不新增旧键拒绝框架。Spring 未知属性可能被忽略，因此迁移说明必须明确旧覆盖不再生效；不声称旧键统一启动失败。错误角色和自定义命名空间的 bootstrap、MTLS 或授权仍在原有校验位置拒绝，不回退明文或从证书自动学习新规则。

## 可靠性、安全与运维

固定 URI 不是认证凭据，不替代证书信任链、有效期、URI 精确匹配、成员和业务权限。不同环境可以使用相同命名空间但必须保持 CA/信任材料隔离；生产不得信任测试 CA。固定命名空间不提供独立多租户隔离能力。

本次不改变超时、重试、线程、资源上界、Raft 协议或持久化格式。既有默认值部署的 wire 身份不变；自定义前缀部署需协调证书与配置迁移，不支持无前提混合版本。回滚需要二进制、前缀配置和证书身份匹配，不能只恢复旧键。

## 验证策略

先在 SDK/项目授权层新增或改写固定命名空间的确定性契约并执行 RED，再删除字段与参数、统一常量并执行 GREEN。保留测试独立字面量，不把 expected 值全部替换成生产常量。聚焦验证顺序为 SDK 目标校验与构造、Broker 配置与授权、Gateway/Account/Message 配置装配；项目调用签名至少 test-compile，禁止凭搜索清零声称行为通过。

当前私有依赖 `com.co.kc.thirdparty.sofa:jraft-core:1.4.1-imchat.1` 在隔离仓库缺失，前一常量提取的 Broker Maven 在解析时失败。实施前先使用不依赖 im-raft 的 SDK 窄层验证；遇同因阻塞保留日志，不反复扩大命令，不修改依赖或用旧产物冒充新输入。既有 DECODE、TLS Broken pipe 和 full/clean/module/architecture 暂停事实保留；完整验收仍需恢复环境后按原任务执行。

静态检查区分历史与当前文档，核对生产无项目前缀覆盖入口、Plugin 无角色耦合、当前 README/Architecture 与配置一致。独立实现 Review 必须核对安全拒绝、配置迁移及实际报告，必要测试未运行不能记 Accept。

## 文档与执行

独立设计批准后纳入现有 `2026-10-25-bolt-package-boundaries` T3，保持唯一 active，不另建竞争计划。更新根 Architecture、受影响模块 README/Architecture、Security/Reliability 中实际相关描述及部署文档，历史设计和旧验收记录不重写。对现有前缀设计增加取代链接，记录此前常量提取的 Revise，不把历史验证缺口抹掉。

固定项目身份约定属于可复用规则：在 Security 中记录 owning specification，在 Harness 中登记最窄可靠的现有测试或 Review-only 条件；不建立一次性类名豁免或通用旧键框架。未形成新算法或框架，外部库行为沿用现有事实，不新增性能承诺。

## 备选方案与决策

| ID | 议题 | 决定 | 理由与影响 |
|---|---|---|---|
| D1 | 默认值还是固定值 | 五角色一起固定，取消覆盖 | 用户确认项目统一约定，减少无真实需求的配置与参数 |
| D2 | 保留每部署自定义命名空间 | 不采用 | 适合多组织交付或外部身份平台，但当前不需要，增加信任输入分散 |
| D3 | 自动从证书或发现推导命名空间 | 不采用 | 首次信任不能从未可信来源学习，也不能让任意角色证书改变授权规则 |
| D4 | 通用插件 | 保留通用前缀配置及验证能力 | 角色语义归项目SDK/Server，不让Plugin依赖业务角色 |
| D5 | 身份对象 | 保留对象，仅简化无意义的前缀传参 | 是否删除对象另行讨论，不混入本次行为变更 |
| D6 | 旧键 | 删除入口，无兼容别名，不额外fail-fast | 必须披露未知属性可能被忽略，不静默宣称兼容 |

## 后续职责调整（2026-10-28）

本设计当时保留两个身份对象。后续用户批准仅删除 BrokerIdentityConfiguration 包装并将原校验、快照、发现交集与目标构造收回 BrokerClient，见[职责内聚设计](2026-10-28-broker-bootstrap-inline-design.md)；固定五角色及可信配对约束不变，Gateway 身份对象在该轮仍保留。后续用户另行批准删除 Gateway 包装，见 [Gateway 目标职责内聚设计](2026-10-28-gateway-target-inline-design.md)，原模式、快照与安全限制保留。本段不改写本设计原范围或既有验证事实。

## 独立 Review

独立设计 Reviewer `a7ce1f2aa262d78e2` Approved、必须修改项 none。核对五角色固定值、通用 Plugin 边界、安全迁移和 SDK 窄验证路线；实施前将精确范围和命令纳入现有 T3。本结论不覆盖实现或完整验收。
