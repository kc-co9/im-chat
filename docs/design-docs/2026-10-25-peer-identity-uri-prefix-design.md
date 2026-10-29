# Peer 身份 URI 前缀统一命名设计

> 状态：已批准。2026-10-25 用户已批准全仓同概念 `workloadIdentityPrefix` → `peerIdentityUriPrefix` 的改名范围、不保留旧名别名及历史归档处理边界；独立设计 Reviewer `a0b58853328dc8e68` 已给出 Approved，无必须修改项。本文按[技术设计文档规范](../references/DESIGN_DOC_GUIDE.md)及设计模板编写。clean/full 按用户要求暂停；本文不代表实现或完整验收通过。

## 后续决定（2026-10-28）

五种项目角色的部署前缀覆盖能力由[固定前缀设计](2026-10-28-fixed-peer-identity-prefixes-design.md)取代，用户确认且独立设计 Review Approved。本文配置改名、默认值提取及其历次验证记录保留为当时范围；不能用其旧可配置描述覆盖新决定，也不能将先前依赖阻塞和 Revise 改记通过。通用 Plugin 前缀配置不受影响。

## 摘要

将表示远端身份 URI 前缀的现有 `workloadIdentityPrefix` 及带角色限定的同概念名称统一为 `peerIdentityUriPrefix`。改名覆盖 Java 参数、字段、实际存在的访问器、record component、调用点、kebab-case 配置键、对应环境变量引用，以及仍作为当前事实源的文档和部署配置。不保留旧字段、旧访问器或旧配置键的兼容别名。

这是配置与源码命名契约变更，不是身份认证策略变更。默认 URI、前缀与节点 ID 的拼接、`startsWith` 匹配、角色授权、可信目标配对、MTLS/DEV 模式边界均保持原样。`peer` 表示连接或请求的远端，不限于 Broker 集群成员，也包括 Gateway、Message、Account 和 Ops 调用方。

主要交付风险是仓库外配置未迁移。Nacos、环境变量及部署覆盖中的旧键不再绑定，可能静默使用代码或 YAML 默认值；不承诺旧键自动报错，也不为本次改名增加旧键拒绝框架。

## 背景

当前 `workloadIdentityPrefix` 同时用于出站目标命名空间和入站调用方角色匹配。实际值为 `spiffe://im-chat/broker/` 等 URI 前缀，而不是本进程完整身份、证书材料、用户身份或任意字符串标签。原名未明确「远端」和「URI」，容易与本机工作负载身份混淆。

当前代码事实可由以下位置确认：

- `im-broker/im-broker-sdk/.../sdk/model/BrokerIdentityConfiguration.java`：校验绝对 URI 与尾部 `/`，以 `startsWith` 检查 bootstrap 完整身份，以固定前缀加 committed `brokerId` 构造 `BoltTarget.expectedPeerIdentity`。
- `im-broker/im-broker-server/.../model/properties/BrokerGossipSettings.java`：保存启动固定的 Broker 远端身份命名空间，校验 Gossip peer 完整身份属于该前缀且后缀非空。
- `im-broker/im-broker-server/.../infrastructure/config/properties/BrokerRoutingProperties.java`：声明 Gateway、Broker、Message、Account、Ops 的角色前缀；`BrokerRequestConfiguration` 将其注入入站准入链。
- `BrokerCallerIdentityInterceptor`：从受信 `BoltRequestContext.peerUriIdentities()` 中匹配唯一允许的叶证书 URI 身份，再按 operation admission type 约束角色。
- Gateway、Account、Message 的 Broker 配置声明：将 Broker URI 前缀传入 `BrokerIdentityConfiguration`；Broker 的 Gateway push 配置则将 Gateway URI 前缀传入 `GatewayIdentityConfiguration`。

这些用途都描述远端证书 URI 身份的命名空间；DEV 使用同一前缀约束目标描述，但不因此产生证书认证事实。上述路径中的 `...` 是包路径省略，不是可执行命令。

## 目标与非目标

- 目标：全仓仍有效的同概念旧名迁移为清晰一致的新名，配置覆盖可通过非默认值绑定验证，调用点编译一致。
- 目标：保留旧行为及默认值，明确外部配置迁移、混合版本和回滚边界。
- 非目标：批量改名所有 `identityPrefix`、`identityPrefixes`、`prefix`，或改名 URI 前缀策略接口。只有实际采用本次旧名且语义相同的声明与引用属于本次范围。
- 非目标：改名 `workloadIdentity()` 等完整身份、本机身份、证书 URI SAN、私钥、trust store、`expectedPeerIdentity`、`peerUriIdentities` 或业务协议字段。
- 非目标：加强 URI 规范化、修改前缀匹配算法、改变角色授权、增加配置兼容期或旧键 fail-fast 框架。
- 非目标：机械改写已完成计划、历史设计和归档证据，或借改名重构模块、调整包职责、修复其他问题。

## 已知事实与约束

- 模块所有权不变：SDK 选择可信远端目标，Plugin 负责传输与连接认证，接收模块负责角色、成员及业务授权。业务配置留在原有 typed `@ConfigurationProperties` 中。
- 出站 Broker 身份策略和 Gossip 设置已有构造校验；角色前缀配置已有非空校验。本次只改名，不把不同位置的校验强度合并或扩大。
- `BrokerIdentityConfiguration` 的参数改名不改变构造器类型签名，但字段、record component 与实际存在的访问器引用必须同步；不新增仅为兼容而存在的成员。
- 当前工作树包含其他任务的改动，本文只记录获批决策；索引、计划、PROGRESS 和实现同步由对应任务负责。
- 外部 Nacos 数据和部署环境不由仓库搜索完整覆盖，是否存在旧配置必须由部署负责人确认，不能从仓库无命中推导为无需迁移。

## 方案概览

配置源提供远端 URI 前缀，经各 owning module 的配置绑定进入启动固定的目标/授权策略：出站与完整目标身份配对或拼接，入站与已认证的远端 URI 匹配。改名只改变配置绑定入口和 Java 标识符，不移动信任来源或状态所有权。

关键不变量：

1. 同一部署值迁移到新键后，生成的完整目标 URI 与改名前逐字一致。
2. 相同远端身份和 operation 输入产生相同的授权或拒绝结果，包括空后缀、错误角色及多重匹配拒绝。
3. 默认 URI 不变，旧键不再拥有本次配置的绑定入口；不存在双读或优先级兼容逻辑。
4. DEV 配对仍仅描述目标，不替代 MTLS 认证，不提升 principal 或修改租约授权。

## 详细设计

### Java 命名映射

| 旧名 | 新名 | 实际范围 |
|---|---|---|
| `workloadIdentityPrefix` | `peerIdentityUriPrefix` | SDK 身份策略、Gossip 设置、各消费方 Broker 配置、Gateway push 配置，以及参数与引用 |
| `gatewayWorkloadIdentityPrefix` | `gatewayPeerIdentityUriPrefix` | Broker 入站 Gateway 角色配置 |
| `brokerWorkloadIdentityPrefix` | `brokerPeerIdentityUriPrefix` | Broker 入站角色、集群与 Gossip 装配 |
| `messageWorkloadIdentityPrefix` | `messagePeerIdentityUriPrefix` | Broker 入站 Message 角色配置 |
| `accountWorkloadIdentityPrefix` | `accountPeerIdentityUriPrefix` | Broker 入站 Account 角色配置 |
| `opsWorkloadIdentityPrefix` | `opsPeerIdentityUriPrefix` | Broker 入站 Ops 角色配置 |

Lombok 生成的 getter/setter 随字段同步，例如 `getWorkloadIdentityPrefix()` 改为 `getPeerIdentityUriPrefix()`；record component 的 `workloadIdentityPrefix()` 改为 `peerIdentityUriPrefix()`。实际引用与指向这些配置名称的错误提示、注释、测试描述同步更新，但不泛化替换所有「workload identity」术语。

### 配置键与默认值

以下配置路径均是目标状态；对应旧路径将 `peer-identity-uri-prefix` 换成 `workload-identity-prefix`，其余层级不变。

| 新配置路径 | 保持不变的默认值 |
|---|---|
| `im.gateway.ws.broker.peer-identity-uri-prefix` | `spiffe://im-chat/broker/` |
| `im.account.broker.peer-identity-uri-prefix` | `spiffe://im-chat/broker/` |
| `im.message.broker.bolt.peer-identity-uri-prefix` | `spiffe://im-chat/broker/` |
| `im.broker.gateway-push.bolt.peer-identity-uri-prefix` | `spiffe://im-chat/gateway/` |
| `im.broker.routing.gateway-peer-identity-uri-prefix` | `spiffe://im-chat/gateway/` |
| `im.broker.routing.broker-peer-identity-uri-prefix` | `spiffe://im-chat/broker/` |
| `im.broker.routing.message-peer-identity-uri-prefix` | `spiffe://im-chat/message/` |
| `im.broker.routing.account-peer-identity-uri-prefix` | `spiffe://im-chat/account/` |
| `im.broker.routing.ops-peer-identity-uri-prefix` | `spiffe://im-chat/im-ops/` |

环境变量区分两类：Spring 原生 relaxed binding 按点转下划线、删除连字符、转大写处理，例如 `IM_ACCOUNT_BROKER_WORKLOADIDENTITYPREFIX` → `IM_ACCOUNT_BROKER_PEERIDENTITYURIPREFIX`；部署脚本或 `${...}` 显式占位符若采用 `WORKLOAD_IDENTITY_PREFIX` 片段，则同步迁移为 `PEER_IDENTITY_URI_PREFIX`，保留其实际角色和命名空间。不得把自定义占位符拼法冒充 Spring 原生绑定规则。实施时逐一核对真实引用与环境变量绑定测试，不凭本文生成不存在的兼容键。

### 正常与失败路径

新键绑定原部署值后，Broker SDK 继续校验绝对 URI、尾部 `/`、bootstrap 身份前缀和非空后缀，继续以 `peerIdentityUriPrefix + brokerId` 生成完整身份。Nacos 候选与部署可信配对取交集的规则不变。

入站继续对经过认证的 peer URI 执行原有 `startsWith` 和非空后缀检查，并保持唯一匹配和角色准入规则。Gateway 下行继续使用注册的完整身份或既有 DEV 配对，不因名称变化自行生成认证主体。

值非法时沿用原有构造或启动拒绝；只有旧键时不再绑定到新字段。由于仍有默认值，应用可能正常启动却使用非预期命名空间，也可能在后续配对、连接认证或授权阶段失败。具体失败时机依赖配置和运行路径，不能承诺统一启动失败。

## 可靠性与安全

本次不新增 RPC、持久化状态、后台任务或共享可变状态，不改变超时、重试、幂等、并发、网络分区及资源上界。启动快照的固定时点和重建方式保持现状，不增加运行期热切换保证。

安全边界仍由原有证书校验、连接认证和业务准入共同承担。URI 前缀是命名空间约束，不是独立认证凭证；发现 metadata、首响应或 DEV 配对不能升级为可信证书身份。外部旧键导致默认值生效属于真实配置风险，不应通过忽略验证失败或回退明文处理。

## 运维与交付

发布前盘点各环境 Nacos、环境变量、Compose、部署模板、Secret/ConfigMap 引用及运维覆盖，将实际旧键迁移到新键，值保持不变。仓库配置、活动事实文档与真实部署配置必须成套更新，特别检查使用自定义 URI 前缀而非默认值的环境。

不支持旧、新配置键在同一版本中作为别名共同工作。混合版本部署需要各版本使用其对应配置入口；不要假设共用配置一改即对所有版本生效。未确认配置分发和进程版本配套前，不宣称可安全滚动发布。无需迁移数据库、Raft committed state 或证书内容，因为它们不因 Java/config 名称变更而改变。

## 风险与回滚

- 风险：外部旧键未迁移，默认前缀掩盖绑定缺失。应以非默认前缀和完整目标身份核对实际生效值，而非只观察进程是否启动。
- 风险：测试仅使用默认值可能漏掉配置键错误；新键绑定测试必须使用与默认值不同的合法 URI 前缀。
- 风险：误将本机身份、一般前缀变量或策略接口纳入全局替换。实施 Review 应按声明及数据流核对，而非仅看字符串清零。
- 回滚：代码与配置入口成套回退，恢复原键及原值；只回退二进制或只回退 Nacos 配置都可能产生同样的默认值风险。证书和数据无需随本次命名回滚。
- 延后事项：旧键拒绝框架、全局配置审计、身份策略算法调整均不属于本次范围。

## 验证策略

以下是设计要求与待执行命令，不是已通过证据。所有 Maven 命令从仓库根运行，按绑定、启动、SDK、架构顺序推进；前层失败先修复或归因。`-Dsurefire.failIfNoSpecifiedTests=false` 仅允许 `-am` 上游无指定测试，不免除核对目标模块实际测试报告。

### 聚焦绑定与授权

为上述九个配置入口补充或扩展确定性绑定断言：使用非默认前缀检查新键；以旧键单独输入确认它不再覆盖新字段，断言现有默认值而不是虚构 fail-fast；覆盖实际环境变量入口。测试由实施任务放入现有 owning module 配置测试，不在本设计任务新增测试文件。

```bash
mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerRoutingPropertiesTest,GatewayDevelopmentTargetConfigurationTest,BrokerObservationRequestConfigurationTest,BrokerGossipSettingsTest,IdentityAccessInterceptorsTest,BrokerObservationIdentityTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -pl im-gateway/im-ws-gateway/im-ws-gateway-server,im-service/im-account/im-account-server,im-service/im-message/im-message-server -am -Dtest=BoltConsumerConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

保持已有前缀非法值、空后缀、角色拒绝及多重匹配断言；用非默认前缀证明装配到目标策略的值未丢失。不能仅靠编译或全仓无旧字符串命中作为行为证据。

### 启动与 SDK 契约

```bash
mvn -q -pl im-broker/im-broker-server,im-gateway/im-ws-gateway/im-ws-gateway-server,im-service/im-account/im-account-server,im-service/im-message/im-message-server -am -Dtest=ImBrokerApplicationTest,ImWsGatewayApplicationTest,ImAccountApplicationTest,ImMessageApplicationTests -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -pl im-broker/im-broker-sdk,im-gateway/im-ws-gateway/im-ws-gateway-sdk -am -Dtest=BrokerClientTest,BrokerClientRoutingTest,GatewayClientTest -Dsurefire.failIfNoSpecifiedTests=false test
```

启动测试核对新配置装配及现有 MTLS/DEV 前提；SDK 测试核对生成 URI、可信配对、刷新和目标选择不变。依赖不可用或用例跳过必须报告，不当作启动或真实网络通过。

### 架构与完整门禁

```bash
mvn -q -pl im-test/im-architecture-test -am -Dtest=BoltInvocationBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test
git diff --check
```

架构聚焦检查不得引入 SDK→Server 或 Plugin→业务依赖。另以仓库搜索复核当前生产声明、调用和配置是否遗漏实际旧名，允许本文迁移说明及历史归档保留旧名，不为消除命中机械改写历史证据。

当前仅记录设计，不运行上述 Maven 命令；readiness 已实际通过（JDK 21、Maven 3.8.4、Node 24.15.0），Ruby 报告 PATH 中 `/opt/homebrew/bin` 可被所有用户写入的既有警告。clean/full 维持用户暂停，不运行、不以窄验证替代完整验收；恢复及最终验收由主任务在用户明确通知后处理。

## 后续文档与执行

独立设计 Review 已 Approved；实施及完整验收仍需单独记录。设计索引、唯一 active 计划及 PROGRESS 由主任务同步，本文不另建竞争计划，也不修改其他任务状态。

实施时同步 Broker SDK/Server、Gateway Server、Account Server、Message Server 的配置说明及实际涉及该旧名的当前 Architecture、部署和运维文档；历史归档不机械重写。只有发现可复用的新工程规则时才更新 owning specification 与 Harness；本次名称替换不授权新增通用旧键检测框架。

## 附录 A：备选方案与行业依据

- 采用方案：Java 与外部配置同步改名且无别名。收益是长期只有一套准确命名；代价是外部配置必须配套迁移，遗漏可能被默认值掩盖。用户已批准该取舍。
- 不采用方案：仅改 Java、保留旧配置名。能减少外部迁移，但同概念继续存在两套名称，不满足本次全仓统一目标。
- 不采用方案：双键兼容并逐步弃用。适合无法协调的外部发布窗口，但引入优先级、弃用周期和额外绑定逻辑；用户明确要求不保留别名，本次不实施。

方案不新增协议、加密算法或库能力主张，依据为本仓库现有声明和数据流，无需以外部标准替代本地事实核对。

## 附录 B：决策记录

| ID | 来源/问题 | 最终决策 | 理由与影响 |
|---|---|---|---|
| D1 | 用户批准的命名范围 | 全仓同概念旧名统一为 `peerIdentityUriPrefix` 及角色限定变体 | 明确远端 URI 语义，覆盖 Java、配置及环境入口 |
| D2 | 兼容方式 | 不保留旧字段、访问器或配置键别名 | 外部配置必须成套迁移，不能假设旧键仍有效 |
| D3 | 行为边界 | 默认 URI、拼接、`startsWith`、角色授权及模式边界保持不变 | 防止命名任务扩展成认证策略变更 |
| D4 | 配置遗漏风险 | 明示旧键不绑定且可能静默使用默认值，不承诺 fail-fast | 不新增旧键拒绝框架，不夸大启动验证能力 |
| D5 | 搜索与历史边界 | 只改实际同概念旧名，不机械改一般前缀、接口、本机身份及历史归档 | 保留准确语义与历史证据 |
| D6 | 执行与验收约束 | 独立设计 Review Approved；clean/full 保持暂停 | 用户批准不等于独立 Review 或完整验收通过 |

## 追加：Java 默认前缀集中定义（2026-10-28）

用户批准 `PeerIdentityConstants` 命名及实施。新增 `im-common/src/main/java/com/co/kc/imchat/common/constant/PeerIdentityConstants.java`，以 private 构造器、public static final String 定义 DEFAULT_BROKER_URI_PREFIX、DEFAULT_GATEWAY_URI_PREFIX、DEFAULT_MESSAGE_URI_PREFIX、DEFAULT_ACCOUNT_URI_PREFIX、DEFAULT_OPS_URI_PREFIX。值分别逐字保留 broker、gateway、message、account、im-ops 命名空间。

仅替换五个配置类的九个 Java 字段初始值：Broker 的 BrokerProperties、BrokerRoutingProperties，Gateway 的 GatewayProperties，Account 的 AccountBrokerProperties，Message 的 BrokerProperties。配置字段、getter/setter、配置键、绑定和覆盖能力不变；不合并配置 Bean，不改变认证和角色授权，不改 YAML、测试字面量或 Plugin，不新增反向模块依赖。采用现有 common.constant 包，不新增配置框架；相比继承公共配置类或移入 im-bolt，常量更小且不改变配置所有权。

在现有 T3 追加步骤，readiness 已通过。独立设计 Reviewer `a19a522ec3aa516fc` Approved、findings none，仅覆盖本追加节。实施后静态核对九个值等价、编译常量类并运行现有 BrokerRoutingPropertiesTest 与三个消费方 BoltConsumerConfigurationTest。现有 full/clean/module/architecture 暂停及历史失败保留，不用本增量宣称完整验收。没有新业务行为，不为常量提取新增仅验证常量自身的测试或强造 RED。

## 附录 C：独立 Review

- 用户确认：已批准本文记录的命名范围、无别名方案及历史边界。
- 独立设计 Review：`a0b58853328dc8e68`，Approved，无必须修改项。
- 本结论仅为设计批准，不代表实现 Review 或完整验收通过。
