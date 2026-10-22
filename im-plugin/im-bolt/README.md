# im-bolt

基于 SOFA Bolt 的内部 RPC 插件，统一客户端调用、服务端启动和请求分发。

## 核心组件

- `BoltInvoker`：调用抽象；`invokeAuthenticated` 在同一池内连接上完成 TLS 握手、受信证书 URI SAN 唯一性校验和请求，序列化、连接获取、握手、请求及解码共用一个单调接受预算。
- `BoltClientInvoker`、`BoltRpcClient`：同步 RPC 客户端实现。
- `BoltRequestHandler`：业务 Handler SPI，只提供 `handle(BoltRequestContext, String)`；传输上下文可缺失，身份准入由业务入口负责。
- `BoltRequestProcessor`：按 service/operation 分发请求，并从本次连接保留握手成功标志和完整 URI SAN 集合。
- `ImBoltProperties`：客户端开关，以及服务端监听地址和端口配置。

依赖方只实现 `BoltRequestHandler`，不直接操作 SOFA Bolt 请求对象。启用服务端时使用 `im.bolt.server.enabled=true`，通过 `im.bolt.server.host` 和 `im.bolt.server.port` 配置监听地址；host 默认 `0.0.0.0`，容器共享宿主网络时应显式限制为 `127.0.0.1` 或受控接口。

## 认证调用与失败分类

`invokeAuthenticated(address, service, operation, request, responseType, timeoutMillis, expectedPeerIdentity, identityPrefix)` 只接受经过 TLS 握手验证、证书链受信且 `identityPrefix` 命名空间下恰有一个 URI SAN 且等于 `expectedPeerIdentity` 的对端；不回退到 Subject、地址或响应负载，也不在校验后重新从地址选择连接。失败通过 `BoltInvocationException.failure()` 区分 `TRANSPORT_UNAVAILABLE`、`AUTHENTICATION`、`PROTOCOL` 和 `LOCAL`，并保留原始 cause；只有明确的连接失败或超时才是传输不可达。

连接获取可能等待既有池任务或同步 DNS，SOFA Bolt 1.6.6 无法保证方法在预算内硬返回。迟到的连接不发送请求，迟到的成功结果不被接受；本机预算耗尽及连接获取在预算后才失败归为 `LOCAL`，不能作为目标不可达证据。调用方须用既有有界执行器和在途闸门约束阻塞任务，不因等待超时释放尚未结束的任务。

## 连接层认证原型（仅测试，生产未接入）

`BoltConnectionAuthenticationSpikeTest` 用真实 TLS 和公开工厂/Channel hook 验证认证前业务发送屏障，普通 Url RPC 不依赖逐调用证书检查；目标身份在 connect 前绑定 Channel，池 key 包含地址和完整身份，CONNECT 回调直接使用 Connection 的测试路径也受 gate 限制。负对照证明“handler 未执行”不等于业务命令未提前放行；修正后测试区分写入尝试、gate 后放行及明确认证拒绝原因。

这是有界原型，不改变当前生产 `invokeAuthenticated` 或 Bean。复用、同址异身份并发和显式移除后重建已测试；自然失效回收/后台 Reconnector、证书轮换、其他 handler 的 ctx.write 路径及完整端到端预算仍未证明，不能据原型直接切换生产。设计与风险见 [连接层认证原型](../../docs/design-docs/2026-10-20-bolt-connection-authentication-spike-design.md)。

## 关键技术点

- 请求使用 `service + operation` 两级标识分发，避免 Handler 依赖传输层命令对象。
- `BoltRequestProcessor` 建立 Handler 索引并统一处理 JSON 请求和响应。
- 客户端与服务端可独立启用，适配只调用、只提供或双向 peer 场景。
- 显式依赖 Hessian，避免 Bolt 默认序列化器在运行时缺类。

```bash
mvn -q -pl im-plugin/im-bolt -am test
```
