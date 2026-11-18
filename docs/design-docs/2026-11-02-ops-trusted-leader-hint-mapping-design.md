# Ops 可信 Leader 提示映射补充设计

> 状态：已批准。2026-11-02 用户明确选择「批准保守映射」，确认本文完整 PeerId 唯一配对、文本不一致拒绝、保留键冲突 fail-fast、首次注册发布及最多一次原请求跟随的合同；独立 Reviewer `ace3b628dc0670655` 技术设计 Approved，无安全／架构必修项。实施纳入现有联合计划 T11，设计批准不代表运行验收。本文补充[首次接收节点解耦设计](2026-11-02-ops-membership-receiver-routing-design.md)，不改写该切片原验收范围。

> 补充状态：附录 D／H7／H8 已批准实施，四类组合 179 项及真实三 voter 成功／缺失／冲突旅程通过，分别取得独立窄 Accept；运行记录见 T11 与本文末尾。整体 T11、完整模块与冻结验收尚未关闭，当前稳定文档同步待独立 Review。

## 摘要

Broker 的真实 Follower 返回 JRaft `PeerId.toString()`，Ops 现有单跳只接受精确匹配可信发现管理端点的 HTTP URI。单 voter 的真实 CLI 写旅程和人工 HTTP hint 测试不能证明这两种地址可以互通。

推荐候选由 Broker 在首次 Nacos 注册前发布实际初始化 Node 的完整 PeerId，使用同一实例已有的管理地址形成配对。Ops 将 PeerId 当作不透明字符串，只在重新发现的原始实例列表中找到唯一、无冲突的配对后发送一次原命令；不解析 Raft 地址，不猜端口，不消除 priority 或 IPv6 文本差异。缺失、非法和歧义只阻断对应 PeerId 跟随能力，不改变普通诊断、首次接收者选择及原 HTTP 精确可信单跳。

代价是安全优先而非最大可用：语义可能等价但字符串不同、发现滞后、缺 metadata 均可拒绝跟随。项目尚未上线，要求 Broker 与 Ops 统一发布，不引入旧协议兼容分支、双写或猜测 fallback。设计批准与局部实施事实分层记录；映射、身份发布及三 voter 旅程已获窄 Accept，不代表整体交付验收。

## 背景

当前事实可以分别定位到以下文件：

- `im-plugin/im-raft/.../core/RaftRuntime.java` 的 `leaderEndpoint()` 返回实际 Node 已知 Leader 的 `PeerId.toString()`，不是管理 HTTP origin。
- `im-management/im-ops/.../application/BrokerClusterOperationAppService.java` 的 `submitOperation` 先解析 URI 并要求 http／https；真实 PeerId 因而无法进入可信跟随。
- `im-management/im-ops/.../adapter/BrokerClusterOperationAdapter.java` 首次选择发现顺序中的第一个 supported 管理端点；`operateLeader` 重新发现并精确匹配管理 URI。
- `im-management/im-ops/.../infrastructure/discovery/NacosBrokerDiscovery.java` 当前只读取 `management-host`／`management-port`，先以 brokerId 执行 `LinkedHashMap.putIfAbsent`；重复实例的信息会在返回前丢失。
- `OpsCliControllerTest` 的可信单跳测试使用人工 HTTP hint；`OpsBrokerManagementContractE2ETest` 与现有 CLI 写旅程采用单 voter，不能关闭真实 Follower 协议缺口。

本设计对应联合计划 T11 的缺口，不新建并行实施计划，不提前激活 T12。当前状态与历史失败以根 `PROGRESS.md` 和[联合计划](../exec-plans/completed/2026-10-30-official-jraft-and-ops-cli.md)为准。

## 目标与非目标

- 目标：真实 Follower 的完整 PeerId 可映射到同一次可信发现中的唯一管理 origin；映射拒绝时不产生跟随请求，不发送管理凭据。
- 目标：发布值来自实际成功初始化的 Node，先于首次注册；重复实例不能被普通诊断去重掩盖。
- 目标：保持 NOT_LEADER 最多跟随一次、重新发现、原 command／scope／operationId／expectedEpoch／业务目标不变。
- 非目标：普通接收节点自动故障转移、Broker 代理转发、Raft 地址解析器、Leader 选举证明、第二份权威拓扑、跨环境信任、动态端点热更新。
- 非目标：调整 IAM／服务凭证授权、HTTP transport 安全配置、原生 Raft 网络认证、已有覆盖规则或外部 JaCoCo 插桩问题。

## 已知事实与约束

### 所有权与信任来源

`im-raft` 只提供框架无关的本机身份字符串，不依赖 Broker 或 Nacos。Broker 拥有身份发布和 metadata 保留键。Ops 拥有管理地址发现、映射和跟随决策，不依赖 Broker Server 实现，不把 Nacos 信息当作 Raft membership 或 quorum 证明。

本文沿用 Ops 当前运维发现的信任前提：配置好的 Nacos namespace／group／service 及其注册写权限、管理网络和服务凭证必须受部署控制。metadata 配对本身不是签名、证书身份或首次 Bolt bootstrap 信任源，不能抵御拥有可信注册写权限的攻击者。不得把此能力推广为 SDK 的 Bolt bootstrap 信任规则。

### 实际 Node 身份与字符串边界

本地缓存的官方 JRaft 1.4.1 源码表明：`NodeImpl` 构造时复制 serverId，`init` 再把其 priority 设置为 `NodeOptions.electionPriority`；当前 Runtime 未设置该选项，官方默认值为 -1。因此配置 endpoint 经 `PeerId.parse` 再 `toString`，不必等于初始化后 Node 的完整身份。发布值必须读取成功初始化后的 `node.getNodeId().getPeerId().toString()`。

PeerId 的 index／priority 按官方规则序列化；index 为 0 或 priority 为 -1 时可能省略字段。官方 `PeerId.equals` 与完整字符串比较也不是同一合同。IPv6 地址文本不保证压缩／展开等价归一化。Ops 不复制这些规则，不利用 endpoint-only 相等性扩大信任。

### 环境与证据边界

协调方本次 readiness 实际 exit0，工具为 Java 21、Maven 3.8.4、Node 20.19.5；既有 PATH world-writable Ruby warning 保留，不修改系统权限。本文起草未执行构建、容器或运行验证。下述生命周期结论来自缓存源码／字节码静态核对，仍须通过装配和首次注册测试证实。

## 方案概览

```text
Initialized JRaft Node
  -> RaftRuntime local identity String
  -> BrokerRaftAdapter
  -> Broker NacosRegistrationCustomizer
  -> one registration: [raft-peer-id, management-host, management-port]
  -> Ops raw DiscoveryClient snapshot
  -> exact unique pairing check
  -> one management HTTP request with original command
```

配对由同一条注册实例记录承载，不从多个实例拼接。首次接收者仍由普通 `discover()` 按现有顺序选择。只有第一个结果为具名 NOT_LEADER 且提供非空 hint 时才进入跟随；第二次结果原样返回，绝无第三次请求。

## 详细设计

### 1. 身份发布与保留键

候选键名固定为 `raft-peer-id`，归 Broker 所有，不是管理员填写的 Raft endpoint 配置项。值为实际已初始化 Node 的完整非空 PeerId 字符串，不进行 trim、priority 删除或 IPv6 改写。`management-host`／`management-port` 继续沿用现有管理 origin 生成规则，不添加另一套管理 URL 配置。

在现有 `BrokerClusterConfiguration` 中增加官方 `NacosRegistrationCustomizer` Bean，显式以 `BrokerRaftAdapter` 为工厂方法依赖。Runtime 和 Adapter 增加各自有真实生产调用者的字符串读取接缝；不得暴露 JRaft Node／PeerId 对象或为测试增加无调用者包装方法。

customizer 在 registration 初始化回调内取得实际身份并写入 metadata。保留键原先不存在才写入；已存在且与实际值完全相同可接受，存在不同值（包括空白）必须抛出明确配置异常，阻断初始化／首次注册，不静默覆盖。异常不包含凭据或原始响应。其他 customizer 不得修改这个 Broker 保留键；该所有权约束需独立 Review 审计，单个 callback 无法机械保证后续任意第三方 customizer 不篡改。若发现不可排除的后续写者，须停止实施并补设计，不用执行顺序猜测代替保证。

### 2. 注册时序与 Bean 装配

已核对版本为 Spring Cloud Commons 4.3.0、Spring Cloud Alibaba Nacos discovery 2025.0.0.0。官方 `NacosRegistration` 在 `@PostConstruct init()` 内执行 customizer 列表；其 metadata 来自 discovery properties，注册服务随后从 registration 读取 metadata。Web 自动注册由 WebServerInitializedEvent 路径触发；管理 namespace 事件被忽略。Broker 的 Raft 在 Adapter Bean 构造中初始化，显式依赖使 customizer 创建不能先于该初始化成功。

候选 customizer Bean 保持应用层无条件装配，只依赖已必需的 Adapter，不在工厂方法注入 `NacosRegistration`、`NacosDiscoveryProperties` 或 NamingService，不引入 `@ConditionalOnProperty` 应用链开关。Nacos disabled 时此 Bean 可以存在但不被官方注册流程消费；这是需测试确认的装配推论，不是已取得的 startup 证据。不能为了旧 fixture 跳过必需的 Raft 初始化。Raft 初始化失败继续使应用 fail-fast；不得发布配置值替身或空身份。

不在 ApplicationReady 后补 metadata，不增加周期 patch／重新注册器，不修改通用 im-nacos 使其依赖 Broker。依赖或官方回调顺序升级后必须重验。重启会重新读取新 Node 身份并重新注册；运行中改变本机 Raft 身份不在本次合同内，须重启成组发布。

### 3. 分离普通发现与提示解析

保留 `BrokerDiscovery.discover()` 的现有行为及 `BrokerManagementEndpoint` 三字段模型；supported 仍仅表示管理地址可用，不要求有 `raft-peer-id`。不把缺配对标成普通管理端点 unsupported，不扩散到诊断读取或初始写选择。

候选新增公共接缝 `BrokerDiscovery.resolveLeader(String leaderHint)`，返回一个已经验证可信的 `BrokerManagementEndpoint`，失败抛出固定的拒绝异常。方法名及 String 输入是本设计拟定合同；实现只放在现有 `NacosBrokerDiscovery`，不增设 registry、缓存或解析框架。Adapter 的 `operateLeader` 接收原始 String 并调用此接缝，随后发送原 command 和原 scope。AppService 不再先把所有 hint 当作 URI；它只拥有一次跟随编排。

HTTP hint 路径保持原合同：能解析为 http／https URI 时，按现有 `URI.equals` 精确匹配普通 `discover()` 的 supported origin，不新增 URI normalize／DNS 别名／默认端口等价处理。未知 HTTP origin 直接拒绝，不转入 PeerId 路径。HTTP 路径不因无关或缺失 PeerId metadata 被全局封锁；现有按 brokerId 首条去重的普通发现语义也不在本任务改变。

非 HTTP hint 路径只做不透明标识的边界检查：null、空白、包含空白或控制字符以及具有 `://` URL 形态的其他 scheme 拒绝。其余字符串只允许原文精确匹配，不能声称 Ops 已验证 JRaft 语法；可信生产者保证键值来自实际 Node。`ftp://` 等非法 URL 不得借 metadata 匹配变成合法跟随目标。hint 不修剪、不拼接为 URI、不直接作为网络目的地址。

### 4. 原始列表上的唯一配对与冲突检查

PeerId 路径必须重新调用 `DiscoveryClient.getInstances`，把本次原始列表及 metadata 复制为本次不可变观察；所有匹配和冲突检查在这一观察内完成。禁止先调用已按 brokerId 去重的 `discover()` 再判断唯一。

具体接受条件如下，全部满足才返回端点：

1. 原始列表中 `raft-peer-id` 与 hint 按 `String.equals` 完全相等的记录恰好一条；零条或多条均拒绝，即使重复记录声称相同配对也不静默折叠。
2. 该条记录按现有规则得到有效 brokerId 和 supported 管理 origin；PeerId 边界检查有效。
3. 原始列表内不存在另一条具有相同 brokerId 的记录，也不存在另一条具有相同有效管理 origin 的记录。比较在去重之前进行；另一条缺失／非法 PeerId 或管理 metadata 也不能掩盖已经可见的同 brokerId 冲突。
4. 同 PeerId 指向不同 origin、不同 PeerId 共用 origin、重复 brokerId 指向不同 PeerId／origin 均拒绝。其他不相关实例缺失配对只降低自身 PeerId 跟随能力，不全局阻断这个唯一候选。

拒绝发生在任何跟随客户端调用之前。这里「零发送」指零次跟随发送；首次接收者已经返回 NOT_LEADER 的那次发送仍是事实。发现异常同样拒绝，不使用旧快照、不转发到 hint、不尝试第二个普通节点。

该算法保守地拒绝重复注册，包括滚动发布的旧残留；不提供 first-wins、last-wins 或「相同值就忽略」例外。它只证明本次可信观察唯一，不保证注册中心原子全局视图、目标当前是 Leader或发包时记录没有变化。Broker 的鉴权和 Raft 角色／epoch 校验仍是最终副作用边界。

### 5. 错误、竞态与恢复

NOT_LEADER 无 hint 维持当前结果，不发跟随。非法／不可信／歧义提示返回固定拒绝，不向终端输出原始 hint 或响应。第二次仍 NOT_LEADER、业务拒绝或异常均不再自动跟随。选举竞态、超时、连接断开不证明未提交；只允许既有原 operation 查询及显式原记录重试，不改 operationId、不刷新 expectedEpoch。

映射是每次请求的局部观察，不落盘、不长期缓存、不改变 committed 拓扑。无新增线程池、后台任务或不受限历史记录；原始发现列表扫描为线性时间、线性临时空间，不提出未经测量的容量或延迟承诺。

### 6. 拟定实施文件与公共接缝

以下路径相对于仓库根，目录中的 `...` 在本节不作为施工路径使用。

| 生产文件 | 拟定变化 |
|---|---|
| `im-plugin/im-raft/src/main/java/com/co/kc/imchat/plugin/raft/core/RaftRuntime.java` | 成功初始化 Node 的本机身份 String 读取接缝，建议 `localPeerId()` |
| `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/adapter/cluster/BrokerRaftAdapter.java` | 向发布方提供实际本机身份，建议 `getLocalRaftPeerId()` |
| `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/infrastructure/config/BrokerClusterConfiguration.java` | 官方 customizer、显式 Adapter 依赖、保留键冲突拒绝 |
| `im-management/im-ops/src/main/java/com/co/kc/imchat/management/ops/infrastructure/discovery/BrokerDiscovery.java` | `resolveLeader(String)` 合同 |
| `im-management/im-ops/src/main/java/com/co/kc/imchat/management/ops/infrastructure/discovery/NacosBrokerDiscovery.java` | 原始快照精确唯一解析；保留普通 discover 语义 |
| `im-management/im-ops/src/main/java/com/co/kc/imchat/management/ops/adapter/BrokerClusterOperationAdapter.java` | 原始 hint 委托解析，再发送原 command |
| `im-management/im-ops/src/main/java/com/co/kc/imchat/management/ops/application/BrokerClusterOperationAppService.java` | 移除 HTTP-only 前置假设，保持一次跟随 |

不修改 `BrokerManagementEndpoint`、Broker HTTP Response、Raft 持久格式、通用 im-nacos 或 Broker YAML。若实现证据表明这些边界无法成立，先修订设计及范围，不临时扩大。

## 可靠性与安全

可信发现配对只是目的地址约束，不能增加调用者权限或给目标赋予 Leader 权限。服务凭证只经现有正式客户端发送到已选 supported origin，原 audience／scope、写拦截器、幂等和 epoch 校验全部保留。不得开启 HTTP 重定向追随来替代显式单跳。

完整 PeerId 的 index／priority 与 IPv6 文本差异均不等价归一化。若 Follower 获得的 Leader 字符串与该 Leader 实际发布值不同，即使 JRaft 判断同一 peer，也应拒绝。本设计不承诺所有合法 JRaft 配置都可跟随；实际不一致必须以真实测试和部署统一配置暴露，不能放宽匹配。

Nacos 配对缺失、歧义或不可用时牺牲自动跟随可用性，不牺牲凭据目的地约束。管理网络认证和 Nacos 写权限仍是部署前提；不新增 cryptographic binding，也不声称消除了可信注册被攻陷时的凭据泄漏风险。

## 运维与交付

项目未上线，候选交付要求统一发布新 Broker 和 Ops，清理本次部署可归属的陈旧实例后检查首次注册 metadata。不得自动删除他人实例或修改系统权限。`raft-peer-id` 不提供人工覆盖配置；冲突应修复部署配置来源后重新启动。

旧节点缺 metadata 仍可被诊断或接收首次写；若其返回 PeerId，无法找到唯一合法配对则安全拒绝跟随。原可信 HTTP hint 继续支持是保留既有功能，不是新旧 PeerId 双写协议。禁止为旧节点增加端口推导、配置别名或 voter 冒充 learner。

观测只报告固定失败类别和计数，不记录凭据、完整 raw response 或高基数 endpoint 标签。本设计不引入新监控框架或容量基准。发布验收必须确认管理 API 端口与已发布 management metadata 对齐，不能将 Actuator 可访问误当成管理写可达。

## 风险与回滚

- 风险：实际 Node priority 与 Follower 所持 PeerId 文本可能不同；完整字符串策略会拒绝可达 Leader，这是明确的可用性代价，真实三 voter 测试必须覆盖默认部署路径。
- 风险：重复注册、IPv6 文本变化、旧 metadata、发现滞后可造成跟随拒绝；不自动修正发现信息。
- 风险：customizer 的 Bean 图、禁用发现装配和首次注册时机尚无本次动态证明；以下测试通过前不能认定发布路径成立。
- 批准边界：索引在 Proposed 阶段登记；获得需求方对主要取舍确认且独立设计 Approved 后，才转为已批准并纳入 T11 实施。设计审查不替代需求方确认或运行验收。
- 回滚：成组恢复 Broker／Ops 对应版本及匹配配置，重新注册；没有数据格式迁移。回滚后原 Follower 缺口重新存在，不宣称完整运维写可用。不要删除 Raft 日志或改写已存在 operation。
- 延后事项：动态身份变更、签名注册、跨环境发现、通用 PeerId normalization、覆盖插桩修复，均不纳入本设计。

## 验证策略

### 聚焦测试与精确文件

| 测试文件 | 公共接缝及可反驳断言 |
|---|---|
| `im-plugin/im-raft/src/test/java/com/co/kc/imchat/plugin/raft/core/RaftRuntimeTest.java` | 实际初始化 Runtime；index／priority 输入与初始化 Node 的真实输出对照，不能只重新 parse 配置；不得暴露原生对象供生产调用 |
| `im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/adapter/cluster/BrokerRaftAdapterTest.java` | 实际本机身份经 Adapter 无损提供给发布方 |
| `im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/infrastructure/config/BrokerClusterConfigurationTest.java` | 调用官方 customizer 公共回调：缺键写入、相同值可用、冲突抛错；Spring 装配确认 Adapter 先完成、Raft 失败不注册、discovery enabled／disabled 两路径，禁用不要求 Nacos Bean |
| `im-management/im-ops/src/test/java/com/co/kc/imchat/management/ops/infrastructure/discovery/NacosBrokerDiscoveryTest.java` | 原始列表匹配；重复 brokerId 在去重前拒绝；同 PeerId 多 origin／同 origin 多 PeerId／重复同 pair；候选缺失或非法；不相关缺键不阻断合法候选；HTTP 旧语义、普通诊断顺序／去重不变；priority／IPv6 文本不匹配拒绝 |
| `im-management/im-ops/src/test/java/com/co/kc/imchat/management/ops/adapter/BrokerClusterOperationAdapterTest.java` | 原 command 引用及 scope 不变；解析失败零跟随客户端调用；首次 supported 与异常不换普通节点回归 |
| `im-management/im-ops/src/test/java/com/co/kc/imchat/management/ops/application/BrokerClusterOperationAppServiceTest.java` | 接口直接调用方迁移：将原 URI 参数改为同值 String，删除无用 import，保留原单次转发断言，不增加兼容重载 |
| `im-management/im-ops/src/test/java/com/co/kc/imchat/management/ops/interfaces/http/OpsCliLeaderHintMappingTest.java`（新增） | 通过公开 CLI HTTP 接口，使用正式 NacosBrokerDiscovery、AppService 和 Adapter，替换 DiscoveryClient／BrokerManagementClient／IAM 外部边界；验证唯一 PeerId 匹配、最多一次转发、非法或歧义时不转发、响应不泄漏 hint；不 stub resolveLeader |
| `im-management/im-ops/src/test/java/com/co/kc/imchat/management/ops/interfaces/http/OpsCliControllerTest.java` | 保留原 fixture，回归已有 HTTP 地址的可信／不可信匹配及一次转发上限 |
| `im-test/im-e2e-test/src/test/java/com/co/kc/imchat/e2e/OpsCliGovernanceE2ETest.java` | 新增真实至少三 voter 的 Follower 成员写旅程，保留已有旅程 |
| `im-test/im-e2e-test/src/test/java/com/co/kc/imchat/e2e/support/OpsCliGovernanceTopology.java` | 在独享模式启动三个真实 Broker context／JRaft／HTTP；发现替身只转交真实注册 customizer 产生的 metadata，不手造 PeerId 或 HTTP hint |

本机注册集成在配置测试中使用真实官方 `NacosRegistration` 初始化与受控注册服务接收端，捕获第一次 register 的 metadata，禁止以「调用 callback 后 Map 有值」替代时序证明。外部 NamingService 可替换，必须声明不证明真实 Nacos 网络／鉴权。若现有配置测试难以隔离官方装配，先提出准确新增测试文件范围并重新 Review，不在实施时任意扩文件。

### 顺序 RED → GREEN 与真实运行门槛

以下为批准后交给计划的验收顺序，不是当前已执行步骤或通过记录。每个新增行为先取得确定性断言 RED，再做最小生产变更及 GREEN；编译缺 API 和 fixture setup error 不算行为 RED。已存在行为只能登记补充回归，不能制造历史 RED。

1. 先从现有 Ops HTTP 公共接缝增加具名 PeerId 唯一配对成功场景，证明当前 HTTP-only 实现稳定拒绝；再引入发现解析与原 hint 委托，取得 GREEN。随后逐个补歧义、非法、零跟随和 HTTP 保留语义，未通过不推进。
2. 身份发布切片先用真实初始化 Runtime 的身份与配置字符串差异建立可失败观察，并用官方注册初始化／接收端证明当前首次注册缺键；再增加实际身份接缝及 customizer，取得首次注册 GREEN，补冲突与 enabled／disabled 装配回归。不会把当前不存在的 accessor 导致编译失败记作 RED。
3. 合并后启动至少三真实 voter。等待稳定 Leader 和两个 Follower；让真实 DiscoveryClient 替身按 Follower-first 顺序返回各 context 官方 registration 捕获值。通过已认证 CLI／Ops 固定入口提交同一业务目标的操作，观察正式客户端第一次到 Follower、其真实 NOT_LEADER 中为完整 PeerId、第二次到唯一 Leader；不能注入人工 HTTP hint、mock Raft 返回或把 voter 地址用于未加入 learner 身份。使用有合法 DISCOVERED 离线目标的 add-learner 可避免把业务成功与接收者身份混为一谈。
4. 断言原 operationId／epoch／scope／目标／payload 不变、成功登记及查询结果一致、总共两次出站写。独立拒绝场景在真实 Follower 返回后移除或冲突化 Leader 配对，断言仅首次请求发生、无第二次网络请求。第二次换届返回 NOT_LEADER 的上界由确定性 HTTP 接缝证明，不用时序碰运气。
5. 再做 owning module 无 selector 回归、architecture／startup／behavior／E2E 风险覆盖、冻结 clean／full 和独立实现验收。单 voter 成功、mock DiscoveryClient、官方 registration 静态检查均不能替代真实三 voter Follower 旅程。

建议聚焦命令如下；具名方法 selector 在批准后的计划中与最终测试名精确绑定，协调方执行并归档真实结果：

```bash
mvn -q -pl im-plugin/im-raft -am -Dtest=RaftRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterConfigurationTest,BrokerRaftAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -pl im-management/im-ops -am -Dtest=NacosBrokerDiscoveryTest,BrokerClusterOperationAdapterTest,OpsCliControllerTest,OpsCliLeaderHintMappingTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -pl im-test/im-e2e-test -am -Dtest=OpsCliGovernanceE2ETest -Dsurefire.failIfNoSpecifiedTests=false test
```

完整门禁按仓库规则从窄到宽执行，最终 `./scripts/verify.sh clean`、`./scripts/verify.sh full` 的同冻结输入实际报告才构成机器验收；同轮 full 已验证的专用报告不重复运行独立广门禁。外部 JaCoCo `MethodTooLargeException` 的现有缺口继续保留，不能排除依赖或削弱 checker 来宣称覆盖完整。

## 后续文档与执行

本文在 Proposed 阶段登记设计索引。设计得到需求方对主要取舍确认且独立 Approved 后，更新批准状态并将精确范围纳入现有 T11，保持唯一 active；稳定运行文档在实施后根据实际事实同步。

实施后需同步 `im-plugin/im-raft/README.md` 的本机身份接口、`im-broker/ARCHITECTURE.md` 和 `im-broker/im-broker-server/README.md` 的注册顺序／保留键、`im-management/im-ops/README.md` 的双格式 hint 与保守拒绝、`im-test/im-e2e-test/README.md` 的真实及替换边界，以及 `docs/SECURITY.md`／`docs/RELIABILITY.md` 的信任前提和可用性损失。根 `ARCHITECTURE.md` 只补跨模块所有权事实，不提升 Nacos 为共识权威。

这次新增的首次注册身份发布与原始发现歧义拒绝规则应同步 `docs/references/HARNESS_GUIDE.md`／`HARNESS_RULE_MATRIX.md`，以 owning tests 自动约束；多 customizer 的保留键所有权暂为 Review-only，自动化条件是能可靠识别实际完整装配中的后续写者，不新增一次性类名例外。上述范围已在后续文档同步阶段获明确授权；它不授权额外生产、测试、构建或依赖修改。

## 附录 A：备选方案与行业依据

| 方案 | 收益 | 代价与不采用理由 |
|---|---|---|
| 推荐：实际 Node 完整 PeerId 与发现管理 origin 配对 | 消费方不复制 JRaft 语法；最窄扩展当前可信发现单跳 | 依赖首次注册正确与发现新鲜；完整文本匹配降低可用性；需原始列表冲突检测 |
| 显式部署配置 PeerId→管理 origin 允许列表 | 不依赖运行期 metadata 发布，可建立独立部署信任 | 引入第二份维护配置，仍须与实际 Node 身份核对；拓扑／端口变化容易漂移；超出本次推荐范围，不能静默改用 |
| Broker 返回管理 HTTP hint，并在 Broker 内映射实际 Leader | 保留 Ops HTTP-only 接口，映射职责更靠近共识 | Broker 需额外发现／可信映射和故障处理，缺口只是换了所有者；扩大 Broker 对管理面的依赖，本次不推荐 |

根据 Raft／Bolt／管理端口的数值关系推导不是可行的信任方案；仅比较 endpoint 或忽略 priority／IPv6 文本同样改变本次保守合同，不作为 fallback。

官方依据与版本：

- [SOFAJRaft 官方源码](https://github.com/sofastack/sofa-jraft/tree/cb1a0c7ac16ff146ad8abb04b1bcb035270bb234)，对应 JRaft 1.4.1。重点 `jraft-core/src/main/java/com/alipay/sofa/jraft/core/NodeImpl.java`、`entity/PeerId.java`、`option/NodeOptions.java` 与 `util/Endpoint.java`；本次通过本机缓存 sources 核对，不下载或构建。
- [Spring Cloud Commons 官方仓库](https://github.com/spring-cloud/spring-cloud-commons)，4.3.0 的 `AbstractAutoServiceRegistration`，对应 Web 服务自动注册事件路径；本次核对缓存 sources。
- [Spring Cloud Alibaba 官方仓库](https://github.com/alibaba/spring-cloud-alibaba)，本机使用 2025.0.0.0 的 Nacos discovery；`NacosRegistrationCustomizer`、`NacosRegistration.init`、`NacosServiceRegistry.register` 的调用位置通过缓存 JAR 字节码及注解核对，未冒称已验证真实 Nacos 首次网络注册。

## 附录 B：决策记录

2026-11-02 用户明确批准下列保守合同；此前草案与技术 Review 不冒充实施验收。

| ID | 来源／问题 | 最终决策 | 理由与影响 |
|---|---|---|---|
| H1 | 实际 Follower PeerId 与 HTTP-only Ops 不兼容 | 发布实际初始化 Node 的完整 PeerId，由 Ops 精确唯一映射 | 不从配置重建身份；不在 Ops 解析 JRaft |
| H2 | 如何约束新增目的地 | 配对必须来自一次可信原始发现，歧义在去重前拒绝 | 不增加新信任根；普通诊断和原 HTTP 路径保持原语义 |
| H3 | priority／IPv6 等价文本 | 不归一化，不作 alias fallback | 安全边界清晰，但可能拒绝实际可达 Leader |
| H4 | 注册时机与配置冲突 | 官方 customizer 显式依赖 Adapter；保留键冲突 fail-fast | 禁止 ready 后补值或静默覆盖；需首次注册与 disabled 装配证据 |
| H5 | 旧节点与发布 | 未上线统一发布；缺 metadata 只安全拒绝 PeerId 跟随 | 不建设兼容分支、双写或端口猜测 |
| H6 | 自动重试边界 | 具名 NOT_LEADER 最多跟随一次，原 command／scope 不变 | 不自动换普通接收者，不因换届扩成代理链 |

## 附录 C：独立 Review

2026-11-02 独立 Reviewer `ace3b628dc0670655` 技术设计结论 Approved，无安全／架构 Blocker 或 Major。轻量文档问题为一次性代理分工和未登记索引的过时叙述，已改为设计生命周期说明。此结论不代表需求方已批准具体合同，也不是实现或运行验收。

实施测试须明确分层：当前 Ops HTTP fixture 替换整个 BrokerDiscovery；证明真实配对算法的公共 HTTP 场景应使用正式 NacosBrokerDiscovery 与受控 DiscoveryClient 原始实例，不能 stub resolveLeader 后宣称可信解析已贯通。官方首次注册、enabled／disabled 装配和真实三 voter 动态门槛保持不变。实现 Review 继续核对以下风险：

- 实际 Node 身份是否与默认三 voter 的真实 Follower hint 一致；不一致时应停止而非改用弱匹配。
- customizer 的显式依赖是否形成可启动、无环的 Bean 图；Nacos disabled 是否无意要求注册基础设施；是否保留应用必需 Bean 的 fail-fast。
- 是否存在后续 customizer／配置热更新写入保留键；若有，当前最小发布方案能否成立。
- 原始列表冲突是否在 `putIfAbsent` 前检查，缺失／非法配对是否只阻断相关 PeerId 路径。
- HTTP 精确可信路径、普通诊断及首次接收者语义是否保持；是否有凭据发送先于验证。
- 首次注册及真实至少三 voter Follower 验证是否可执行，是否把发现替身误写为真实 Nacos 证明。

若任一关键条件无法满足，结论应为 Revise／Block 并明确缺口，不得自行切换安全信任模型或以未验证假设进入实施。

## 附录 D：健康 Follower 的无副作用拒绝（已批准实施，局部验证通过）

本节保留批准时的问题、候选与验证门槛叙述；其中「当前／尚未」描述批准前基线，不是当前实现状态。实施及运行结果见本节末尾；门槛和安全合同未因实施而降低。

### 问题与边界

真实三 voter CLI 旅程暴露了提示映射之前的可达性缺口：健康 Follower 接收首次写后，成员 workflow 的准入可能直接返回 `UNAVAILABLE`，无法到达 worker 内的 `NOT_LEADER` 检查。运行观察只确认真实非 Leader 接收一次写及「membership workflow admission closed or full」组合分支，不能据此断言内部只有 `roleStopped` 为真。具体运行记录由 T11 计划拥有，不作为本节设计结论的替代。

当前 `BrokerMembershipPermitExecutor.executeMembership` 先处理同 id 的摘要冲突与在途共享，再以 closed／roleStopped／fault／总预算拒绝新 workflow。`BrokerMembershipAppService.executeMembershipOnWorker` 才检查当前 Leader。主治理设计要求角色读取及 Leader hint 的 Node 查询全部在唯一 worker 上；不得为提前拒绝把 `getLeaderEndpoint()` 或 `getLocalBroker().isLeader()` 搬到请求线程。

本补充只拟允许「无对应在途 workflow、未关闭、无 fault、预算未满且有明确跟随观察」的 Follower，无副作用返回 `NOT_LEADER`。不修改全部 `UNAVAILABLE`，不放开 `roleStopped`，不新建 workflow、消耗永久槽预算、排队、登记许可或进入 native。不改变 UNKNOWN 许可、旧 generation、恢复与排空确认的所有权，不改鉴权、持久格式和 Ops 单跳合同。

### 已有接缝与实际缺口

`RaftRuntime.roleObservation()` 已是无 Node 调用的不可变原子观察，但 `Role` 仅包含 term／generation；term 为 0 同时可能表示初始、失权或 Follower，不能证明正在跟随某个 Leader。现有 `leaderEndpoint()` 直接调用 `node.getLeaderId()`，不是可在 caller 使用的快照。`RaftRoleListener.onRoleChanged` 也只有 term／generation，不能凭空获得 hint。因此**仅重排 executor 的现有判断不足以满足合同**。

本地官方 JRaft 1.4.1 sources 的 `StateMachine.onStartFollowing(LeaderChangeContext)` 提供实际 Leader PeerId 和 term，`onStopFollowing` 表示停止跟随；当前 `RaftRuntime.StateAdapter` 尚未覆写这两个事件。候选只复制事件中的完整 `PeerId.toString()` 为字符串，不从配置或发现重建，不在 callback 中调用 Node。这是需要新增并 Review 的发布接缝，不是已有实现能力。

### 推荐候选：事件发布的只读跟随观察

推荐在现有 Runtime 内发布一个不可变、原子替换的本机跟随观察，经 Adapter 向 AppService／executor 提供窄的只读值。具体类型及方法名留给实施 Review 确定；合同至少包含「明确正在跟随」、非空完整 Leader 字符串及对应角色 generation。该观察只是路由提示，不是当前 Leader、quorum 或副作用授权证明。

`roleFault` 与 `stateMachineFailed` 是独立故障事实：前者记录角色发布／listener 首因，后者由 `onApply` 失败及 `onError` 置位。FSM 失败不得改记为 roleFault 或覆盖其首因。无 Node 读取出口至少检查 `closing`、同一 `roleFault` 和 `stateMachineFailed`；禁止在 caller 调用 `hasStateMachineError()`，因为该方法还读取 Node 状态。

最小状态转换固定如下，不新增 token、协议或 generation：

- START：在 `rolePublication` 下，仅当未 closing、未 roleFault、未 stateMachineFailed、当前 `Role.term == 0`、事件 term > 0 且 Leader 非空时，复制完整字符串并绑定当前 generation。generation 0 是合法初始跟随观察。不要求 `status.isOk()`：正常跟随事件可携带非 OK 的 `ENEWLEADER`。
- STOP：在同一边界无条件清空跟随观察，不保留旧 hint。跟随者换 Leader 仅经 STOP／START 更新，不增加角色 generation，不调用 `onRoleChanged(0, ...)`。
- `publishRole`：在原边界内先清空观察，再执行原 generation 推进及 listener 短回调；不改变其已有故障处理和旧槽封闭规则。
- `beginClose`：在 `rolePublication` 下同步清空观察并永久置 closing，保留原关闭语义；迟到 START 不得重新发布。
- FSM 失败：`onApply` 失败路径与 `onError` 在该边界内置 `stateMachineFailed` 并清空观察；该永久标志阻断迟到 START，即使尚未收到 STOP。roleFault 已触发时，读取出口立即拒绝旧观察，后续发布不得使其有效；不为清理缓存给 FaultLatch 新增回调或 SPI。

锁序保持 `rolePublication → listener → executor mutex`。因此 executor 在 mutex 内读取观察必须是**原子、无锁**的完整不可变值读取，绝不反向获取 `rolePublication`；getter 不接受 Supplier／action，也不调用 Node 或外部发现。读取出口可在读取完整值前后复验三个本地门禁，拒绝已经可见的关闭或故障；门禁检查不是跨 Runtime／executor 的全局原子 CAS。

worker 的实际角色／任期查询、首检及每个后续 native 门禁全部保留。此处是对主治理「角色／hint 查询在 worker」规则的窄例外：无 Node 的事件值读取仅用于**纯拒绝**，实际 Node 查询及所有授权判断仍只在 worker；批准实施后须同步主治理设计的该项规则，不能将例外推广到其他调用链。

官方 `FSMCallerImpl` 在同一消费者中按消费序处理 START／STOP_FOLLOWING、LEADER_START／STOP 及 ERROR。首次 Leader start 的配置 closure 也由同一 FSM 消费者执行并调用状态机 `onLeaderStart`；不能只检查显式 LEADER_START 事件路径。该顺序约束支持上述快照转换，但不是 Node 当前状态的同步证明：Node 已进入 ERROR、FSM ERROR 事件尚未消费的不可见窗口，只能记为事件延迟，不能宣称读取出口证明实时健康。

若独立 Review 无法证明官方事件顺序、关闭／fault 失效和 generation 对齐，或发现必须新增 caller Node 查询才能得到可靠 hint，应停止并提交用户设计决策，不能退回直接 getter。不能以缓存陈旧值仍可被 Ops 拒绝为由省略失效规则。

### 准入优先级与竞态

候选保持同一 executor mutex 下的现有身份判定优先级，按以下顺序决策：

1. 已有同 operationId 而 commandDigest 不同：保持原冲突拒绝，不取 hint、不覆盖原 workflow。
2. 摘要相同且原 workflow 尚未完成或仍有 pending：共享原只读派生结果；不改写成 `NOT_LEADER`，不增加槽。原查询／共享不等于授予新 native 权限。
3. 对其余需要新 attempt 的请求，closed、同一 fault latch 已触发或 native／learner／membership 永久槽总预算已满：保持原 `UNAVAILABLE` 硬拒绝。不能因为 Follower 有 hint 而越过其中任何一项；Runtime 已可见的 closing／roleFault／stateMachineFailed 同样不允许产生有效跟随观察，不把 FSM 失败映射为 `NOT_LEADER`。
4. 仅当 `roleStopped` 为真且读取到有效跟随观察、观察 generation 与 executor 当前 generation 一致时，返回带该完整 hint 的 `NOT_LEADER`。这条出口不分配或修改任何 workflow／索引／槽，不请求 pump、不触发服务或 native 阶段。`roleStopped` 为真但初始、选举、无 hint 或观察不一致时，仍为原 `UNAVAILABLE`。
5. 其他请求沿用原准入与 worker 流程；不能通过 hint 判断授予 Leader 能力。worker 的首检、generation／term、revision 及后续许可和 native 复验不变。

准入的本地线性化点是 mutex 内完成上述判断；Runtime 的角色发布不是与该 mutex 共同的 Node 原子 CAS。快照读取后可能换届，允许返回已过时的 `NOT_LEADER`，因为本机没有产生副作用，Ops 仍需重新发现、验证唯一配对且最多跟随一次。可接受的陈旧 hint 不等于允许跨过已可见的 close／roleFault／FSM 失败：读取时这些门禁已封闭必须拒绝，迟到 START 永远不能重开；门禁在最后复验后才变化仍属于非原子观察窗口，不承诺撤回已决定的响应。正常角色 start 已发布 Runtime generation、listener 正等待 executor mutex 时，读取必须无锁且 generation 不匹配即保守拒绝，不能等待 publication 锁或排入新工作来修复观察。

### 文件范围与验证门槛

批准后候选生产范围限于现有 `im-plugin/im-raft/src/main/java/com/co/kc/imchat/plugin/raft/core/RaftRuntime.java`、`im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/adapter/cluster/BrokerRaftAdapter.java`、`im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/application/cluster/BrokerMembershipAppService.java` 与 `BrokerMembershipPermitExecutor.java`。不预先授权修改 listener SPI、线程池、配置或持久模型；如窄只读传递必须扩到这些范围，先补设计 Review。

确定性测试优先使用既有 `RaftRuntimeTest` 的实际 StateMachine callback 捕获／真实 Runtime 接缝，以及 `BrokerMembershipPermitExecutorTest`、`BrokerMembershipWorkflowTest` 的公开 `executeMembershipPermit`、角色回调、可控 worker latch 和原生调用记录。不得新增只供测试的生产构造器或反射改 roleStopped。测试必须分别证明：

- 健康跟随观察下返回 `NOT_LEADER`，完整字符串无损；新 workflow／预算／worker 队列／pump／许可／native 调用零增长，所有 Node 查询零次发生在 caller。用阻塞的既有 worker 证明拒绝不依赖排队执行。
- 同 id 摘要冲突、同事实在途共享优先；旧结果／pending 不被重写。closed、roleFault、stateMachineFailed、满额分别配健康 Follower 作对照，保持拒绝，不拿组合分支代替各独立条件覆盖；两类 fault 独立，FSM 首因不写 roleFault，roleFault 首因不被覆盖。
- generation 0 的正常 START 可发布；非 OK `ENEWLEADER` START 同样可发布。onError 尚未 STOP、onApply 失败、停止跟随、Leader start／stop、close 后均不可读到有效 hint，迟到 START 不恢复。初始但未 START、无 hint、非正事件 term 与 generation 不一致均拒绝；following 换 Leader 不推进许可 generation。
- 用 callback／latch 控制读前后换届，以及 Runtime 已发布新 generation 而 listener 正等待 executor mutex 的窗口：getter 无锁不死锁，观察不匹配时保守拒绝。复验关闭／fault 前后的迟到事件，不以 sleep 碰窗口。已分配 workflow 的迟到结果、UNKNOWN 许可、旧 generation 与 native 拒绝回归不变。
- 事件发布 callback 不调用 Node、不完成应用 Future、不调度新业务；读取快照不调用现有 Node getter 或 `hasStateMachineError()`。结合被阻塞 worker，验证 caller 零 Node、零预算与零 pump；覆盖首次 Leader 配置 closure 对观察的清除。若既有公开测试接缝无法稳定观察某个条件，先报告准确覆盖缺口，不以私有字段赋值制造证明。

先取得具名行为 RED，再做获批的最小实现及相同测试 GREEN；编译失败、fixture setup 失败不算 RED。聚焦命令沿用 `mvn -q -pl im-plugin/im-raft -am -Dtest=RaftRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test` 与 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerMembershipPermitExecutorTest,BrokerMembershipWorkflowTest -Dsurefire.failIfNoSpecifiedTests=false test`，最终 selector 随批准范围绑定。

随后原样重验 `OpsCliGovernanceE2ETest#authenticatedCliFollowsRealFollowerToThreeVoterLeader`：实际三 voter、真实角色、官方首次注册捕获值、认证 CLI 首次 Follower 写与一次 Leader 跟随；业务字段与 scope 不变，实际两次网络写。首旅程实际 passing 后才补缺失／冲突配对的零 follow 场景。没有该实际运行，不能声称本补充关闭端到端缺口。完整验收仍由原 T11 的风险门禁、冻结 clean／full 和独立 Review 决定。

### 备选、风险与决策状态

直接在 caller 调用 Adapter 的角色／hint getter 改动较少，但会新增 Node 访问，违反 worker-only 边界，不采用。另一个方向是由唯一 worker 主动发布路由观察，caller 仍只读缓存；其代价是 Follower 没有新工作时无法保证刷新，需另设受预算约束的触发与失效机制，当前没有可复用的完整接缝，暂不推荐。不得以排入一个新 workflow 来查询 hint，它与本节零预算、零排队目标相矛盾。

事件快照方案增加 Runtime 的发布责任，存在回调顺序及失效遗漏风险；不承诺 hint 永远新鲜。回滚仅恢复本补充代码及对应只读接缝，保留原 `UNAVAILABLE` 安全拒绝，不清理日志或已有 operation，不改变已批准可信映射。未上线，无新配置、格式迁移或兼容双写。窄线程边界已同步主治理设计、Broker 架构／README、im-raft README 及 Harness 规范；这轮稳定文档仍待独立 Review，不据此扩大实施或验收范围。

| ID | 拟议决策 | 状态与待审问题 |
|---|---|---|
| H7 | 健康 Follower 只在既有冲突／共享及硬拒绝之后，以事件发布观察无副作用返回 `NOT_LEADER` | 已批准实施；179 项组合与三 voter 旅程通过，独立窄 Accept，整体 T11 未关闭 |
| H8 | 新增无 Node 调用的跟随观察，不使用 caller getter；保留所有 worker／native 门禁 | 已批准实施；具名事件／锁序／官方首次 closure／零预算证明已获窄 Accept，保留事件延迟限制 |

本节首轮独立 Review 为 `Revise`，原件 `.harness/t11/leader-hint-follower-observation-design-review-attempt1.json`。补齐独立 FSM 故障门禁、最小事件转换、锁序与测试要求后，原 Reviewer 技术复核为 `Approved`，必修发现全部关闭，原件 `.harness/t11/leader-hint-follower-observation-design-review-attempt2.json`。用户本轮随后明确批准实施附录 D／H7／H8；该授权本身不代表实现 Accept 或 T11 验收，不改写首轮 Revise 及原三 voter 失败。

后续实施先取得公开 `executeMembershipPermit` 的行为 RED（期望 NOT_LEADER、实际 UNAVAILABLE），再最小 GREEN。实现首轮 Review 的 Revise 要求补齐真实 Runtime 发布→listener 等待 executor mutex 窗口及官方首次配置 closure→FSM consumer 失效，补齐后四类组合 179 项全零，独立 Accept 原件 `.harness/t11/follower-admission-implementation-review-attempt2.json`，运行原件 `.harness/t11/follower-admission-review-repair-four-class/`。不把编译或 fixture 错误算 RED。

真实三 voter 成功后顺序通过缺失 metadata、冲突配对拒绝，最终 `OpsCliGovernanceE2ETest` 6 项全零、输入稳定，原件 `.harness/t11/leader-hint-governance-class-regression-attempt1/`，独立 Accept `.harness/t11/leader-hint-three-voter-review.json`。发现只转交官方 registration 捕获值，负向变异限定发现边界；NamingService 受控，不证明真实 Nacos 网络。此前响应观察 fixture 失败及首轮拒绝状态预期错误保留。以上均为窄范围证据，整体 T11、覆盖缺口、完整模块和冻结 clean／full 仍由活跃计划拥有。
