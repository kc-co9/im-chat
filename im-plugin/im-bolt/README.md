# im-bolt

基于 SOFA Bolt 1.6.6 的内部 RPC 插件，拥有受控客户端、连接认证、身份隔离池、编码前 typed-command 屏障、服务端启动和请求分发。本文描述当前源码契约，不表示已生产部署；设计边界见 [D10/D11/D14](../../docs/design-docs/2026-10-20-bolt-unified-invoke-design.md)，验收进度见 [PROGRESS](../../PROGRESS.md)。

## 调用与所有权

唯一业务调用入口为：

```java
<T, R> R invoke(BoltTarget target, String service, String operation,
                T request, Class<R> responseType, int timeoutMillis);
```

`BoltTarget(address, expectedPeerIdentity)` 是不可变地址与完整预期 URI 身份，不携带模式、信任开关或 URI 前缀。`BoltInvoker`、`BoltRpcClient` 都使用该目标；不提供地址版调用、旧认证入口或兼容构造。SDK、Gossip 和 Peer Adapter 必须同步重建；业务 wire payload 不因目标类型而增加身份字段。首次发现不能从响应或 Nacos metadata 学习信任身份。

`BoltControlledClient` 替代原 `BoltAuthenticatedClient`，不保留旧 FQCN 兼容壳；外部 Java 消费方必须重新编译。`acquire(BoltTarget, int)` 为 `invocation.BoltClientInvoker` 的真实跨包协作保持 public，仍完整校验运行状态、目标和 DEV 地址；业务调用使用正式 `BoltInvoker` / `BoltRpcClient`，public 技术入口不构成绕过受控传输的许可。wire 和配置键不变。

`BoltControlledClient` 在构造时复制策略，拥有原生工厂、manager、gate、创建执行器与池。池 key 区分地址和完整身份，每个新 Channel 在交付前完成认证，取池连接时重验绑定。MTLS 模式按固定前缀选择 namespace，仅对对端叶证书要求该 namespace 下恰有一个 URI SAN，并与完整预期身份精确相等；不回退 Subject、首 URI、地址或响应负载。前缀不是完整身份匹配的替代。

Spring 启用客户端时，装配校验要求实际运行的 `BoltControlledClient` 与 `BoltClientInvoker.usesControlledClient` 同源、实际模式与配置一致。外部普通 `RpcClient`、任意自定义 Invoker 或替换 manager/codec/transport/扩展不能冒充受控装配。服务端要求 `BoltControlledServer` 与配置的 mode/bind 匹配。外部受控 Bean 的初始化和关闭由提供方负责；默认 Bean 由 Spring 初始化、关闭。原生进程共享 workerGroup 不由单个客户端关闭。

`BoltRequestHandler` 只提供 `handle(BoltRequestContext, String)`；Processor 总是传输上下文，按 service/operation 分发。Broker/Gateway 在身份或上下文缺失时失败关闭；传输认证不能替代成员、角色、scope/audience、Gateway 租约、epoch、Lease/fence 或业务幂等。

客户端、服务端及 SDK 共享 `model.enums.BoltTransportMode`，取代原 `ImBoltProperties.Client.Mode`；外部 Java 消费者须同步重编译。枚举值仍为 `MTLS`、`DEVELOPMENT_PLAINTEXT`，配置键与配置文本不变，不提供旧嵌套类型兼容层。

## TLS 会话与发送边界

标准原生 JSSE 仍执行握手信任验证。D10 在握手成功后读取当前 session 的 local/peer 证书数组：两端必须非空且所有成员均为 X.509；包含呈现的根/锚和未参与路径验证的成员。额外准入区间为 `latestNotBefore <= now < earliestNotAfter`，取全部成员有效期的交集。缺失、读取失败、空区间或类型不符均拒绝，不降级只查叶证书。

这些数组不是实际 CertPath；配置但未呈现的证书不参与额外区间，不承诺监控隐藏验证路径。握手后、每次新发送及入站新业务准入都检查区间。到期拒绝新请求，已准入请求按原 RPC 预算完成；过期 Channel 排空至原生 RPC terminal 后关闭，不因某 caller 放弃等待清掉其他请求的在途计数。

D11 的编码前保证限于受控 invoke 和经审计的公开 RpcClient typed-command 路径（含 Connection/CONNECT），缺少逐命令本机上下文即拒绝。原生 TLS、encoder、response、control 来源单独信任并审计。禁止业务代码任意预编码 ctx.write、替换 codec/transport、修改非受控 pipeline 或 unsafe 写入；架构规则约束拥有者/API，不是运行期任意 raw 字节拦截。装配代码和获准原生实现属于同 JVM 可信边界，不提供恶意进程内 sandbox，也不承诺启动检查能永久阻止特权修改。

## 预算、失败与资源

共享连接创建预算由 manager 拥有；caller 从序列化前到解码后使用独立单调结果接受预算。短 caller 超时只停止自己的等待，不 cancel 共享任务或关闭其他 caller 的连接。创建超时后迟到候选关闭；caller 迟到不发新请求、不接受迟到成功。同步 DNS/原生调用可能仍在运行，因此不是方法硬返回期限，不得用无限异步任务伪造 deadline。

| Failure | 含义 |
|---|---|
| `AUTHENTICATION` | TLS、证书区间、URI 缺失/歧义/不符；禁止明文或普通池回退 |
| `LOCAL` | 本机接受预算、共享创建等待、资源饱和、目标身份策略或 caller 预算无效、中断；不计不可达，中断恢复标志 |
| `TRANSPORT_UNAVAILABLE` | 预算内可明确归因的连接失败或原生连接/RPC 超时；不是任意断连 |
| `PROTOCOL` | 非 BoltResponse、不成功响应或响应解码/协议非法；DEV 模式下非开发地址目标在获取连接时被拒绝，现有实现也归此类（Invoker 在 acquire 前已进入传输阶段）。这与统一 invoke 设计“目标/配置无效归 LOCAL”不一致，属已知偏差，记为 TD-03，由消费方测试固定现状；不证明远端故障 |

异常保留 cause；获取连接在 caller 预算后才成功或失败时，caller 结果为 `LOCAL`，底层认证原因仍可保留。probe/report 仅把可归因传输失败计入 UNHEALTHY，其余 UNKNOWN；不自动重放结果未知的副作用。

下表均为 `im.bolt.client` 下的初始值，不是实测容量或 TPS 保证。数值必须为正，当前仅支持每目标一个连接；限额饱和为 `LOCAL`，不会自动淘汰其他活跃目标来腾出池容量。

| 配置键 | 初始值 |
|---|---|
| `enabled` / `mode` | `false` / `MTLS` |
| `allowed-peer-uri-prefixes` | `[]`；启用时必填、绝对 URI、以 `/` 结尾且互不重叠 |
| `connection-creation-timeout` | `3s` |
| `creation-threads` / `creation-queue-capacity` | `2` / `16` |
| `max-target-pools` / `max-connections-per-target` | `64` / `1` |
| `max-pending-callers` / `max-inflight-rpcs` | `64` / `64` |
| `background-reconnect-enabled` / `connection-warmup-enabled` | `false` / `false`；当前开启即拒绝 |

首版禁用原生 reconnect、monitor/heal 和预热，连接失效后按需安全重建，不复制旧 Channel 认证。候选由失败工厂关闭，入池/排空连接归 manager/client，Invoker 不关闭共享连接。初始化失败反向回收已分配资源；停机拒绝新调用、收口创建和原生在途并关闭实例资源。caller 不再等待不等于底层完成。

## 配置与材料交付

`im.bolt.server.enabled` 默认 `false`，`host` 默认 `0.0.0.0`，`port` 默认 `12200`，`mode` 默认 `MTLS`；服务应显式限制监听接口。客户端和服务端同时启用时模式必须一致。

两侧 `tls.enabled` 默认 `false`，`tls.need-client-auth` 默认 `true`，`tls.key-store-type` 默认 `PKCS12`。MTLS 需启用 TLS，设置 `tls.key-store` 和 `tls.key-store-password`；服务端需 client auth。同一 keystore 提供本机私钥链和信任 CA，密码从部署 secret 注入，不入日志或仓库。完整属性见 [ImBoltProperties](src/main/java/com/co/kc/imchat/plugin/bolt/properties/ImBoltProperties.java)。

D14 固定启动选项及路径，不固定文件字节。已有 Channel 保持原 TLS session；新 Channel 可能经原生加载读到更新文件，不承诺 watcher、每次一定 reload 或任意同路径原地并发写一致。不实现 D13 私钥文件副本。

建议部署方在到期前完整原子交付材料并滚动重启，观察双向新连接后再推进下一节点；这是运维建议，不是 only-restart 门禁。CA 顺序为旧材料 → 双信任 → 新叶 → 撤旧 CA，各阶段验证允许关系、撤旧双向原生 TLS 拒绝和重启。正式签发、secret 分发/轮换与生产上线另行交付；本插件不提供在线吊销，不消除原私钥权限、同 UID/root 或墙钟异常风险。测试 CA/私钥只用于临时目录并由 fixture 清理，不可用于生产。

## 受限开发模式

明文仅允许显式 `DEVELOPMENT_PLAINTEXT`，激活独立 `bolt-dev` 且无 `prod`/`production` profile，两侧 `development.synthetic-data=true`、TLS 关闭。地址仅为字面回环，或声明 `development.isolated-container-network=true` 的受控 `172.30.88.0/24`。声明和 CIDR 本身不是隔离证明：容器还需 [受控 create/inspect/start 路径](../../deploy/local/README.md#bolt-受限开发拓扑) 验证实际 internal 网络及附着。

允许发现、注册、心跳、路由、投递和清理，不提供有效观察票或自动故障摘流。生产禁止明文，也不因 TLS 失败回退开发模式。审计保留阶段和 cause，禁止私钥、密码及请求内容；身份仅进入受控日志，不作为无限基数指标标签，指标维度应固定 mode/phase/failure。

## 验证

```bash
mvn -q -pl im-plugin/im-bolt -am test
```

真实 TLS、资源、后台禁用和材料测试属于模块边界；架构规则、消费方 startup、behavior、真实 Gateway E2E 和 HA 各有独立证据范围，模块通过不能替代全仓门禁。

## 实现包职责

`invocation` 拥有 `BoltClientInvoker`、`BoltInvocationContext` 与 `BoltClientCallTracker`，分别负责调用编排、调用预算及原生调用准入/完成。`connection` 的 `BoltControlledConnectionFactory` 与 `BoltControlledConnectionManager` 负责候选创建/认证和原生池复用/退休；Factory 仍经 Client 回查同一个 Manager。`channel.BoltOutboundHandler` 在原 pipeline 位置执行逐命令出站校验。`client` 仅保留固定装配/生命周期/连接获取的 `BoltControlledClient` 与独立校验目标策略的 `BoltRpcClient`。移动类型的旧 FQCN 以及旧 Authenticated Factory/Manager 名称不保留兼容壳。

跨包技术协作只开放实际调用所需的构造器/方法、不可变 Binding 和固定 AttributeKey；不接纳可替换工厂、manager 或 gate。Client 以 private final 引用持有唯一 Tracker，Tracker 的 private volatile closed 只通过读取和单向关闭准入方法访问。Tracker 实例 monitor 与 Client 生命周期交接共享，关闭准入与调用登记保持原子性；原生终结幂等释放额度，Channel 在锁外关闭，不以 caller 放弃等待或业务 callback 执行代替实际完成。public 内部 API 不扩大 D11 的同 JVM 信任边界。

Client 以私有 `READY / INITIALIZING / RUNNING / TERMINATED` 表达生命周期阶段，构造期间保留 null 以兼容原生父类 option 回调，构造结束即冻结选项。枚举不复制 Tracker.closed。独立 lifecycleMonitor 串行执行完整 startup/shutdown，短 Tracker monitor 仅负责准入及状态提交，不包围资源等待。默认 Spring Bean 与受控调用使用 startup；继承的弃用 init 仍经原生动态分派进入受控 startup，不新增兼容包装。monitor 获取没有超时或中断退出，预中断不取消等待；持锁工作不返回时 shutdown 可能无期限排队。外部重启属于运维约定，不是插件已验证的健康或恢复保证。

初始化中其他线程 shutdown 获锁后才处理关闭，不中途取消初始化；同线程重入 shutdown 明确拒绝。已获锁的初始化失败按原进度回滚，主异常及 suppressed 清理原因仅由启动调用者接收，不向后到 shutdown 广播。TERMINATED 仅表示生命周期及清理尝试结束，不保证清理成功。运行期并发后到 shutdown 获锁后幂等返回，不额外等待物理 Channel 关闭或广播首个关闭失败；自定义生命周期回调若跨线程等待另一个 startup/shutdown，可能形成 monitor 等待循环；不提供任意扩展的死锁或全局进展保证。

正常 RUNNING 关闭时，Manager 等待创建线程失败不会提前截断原生 scanner/处理器清理：Client 以同步拥有者作用域延后 Manager 异常，原生后续清理尝试后仍向调用者重抛首异常，后续异常保留为 suppressed，中断标志恢复。直接 Manager.shutdown 仍直接抛错；不保证 scanner 已物理终止，也不保证某个 UserProcessor 自身抛错后其他处理器仍关闭，不增加关闭重试或原生部分 startup 回收承诺。

`server` 拥有受控服务端和请求处理。`security` 提供两端共享的 `BoltTlsSessionFacts` 与 `BoltDevelopmentValidator`；前者描述实际会话证书数组的有效期/身份事实，不代表隐藏 CertPath 或在线吊销，后者校验显式 DEV 条件与端点。自动配置保留根包，配置绑定仍在 `properties`，不保留旧 `core` 兼容类型。
