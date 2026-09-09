# im-ws-gateway-server

## 模块作用

`im-ws-gateway-server` 是 WebSocket 网关运行服务。

它基于 Netty 接入浏览器 WebSocket 连接，通过 Bolt 与 Broker 通信，实现用户连接注册、上行消息路由和下行精准推送。

## 目录结构

```text
im-ws-gateway-server/
  src/main/java/com/co/kc/imchat/gateway/ws/
    ImWsGatewayApplication.java          # 启动类，启用调度任务
    config/                             # Bean 装配和 im.gateway.ws 配置项
    handler/                            # Broker 写帧 RPC Handler
    lifecycle/                          # Netty Server 与 Broker 注册生命周期
    protocol/                           # IM 实时协议帧 JSON 编解码
    registry/                           # 本机连接注册表
    security/                           # WS 握手 token 鉴权
    server/                             # Netty WebSocket Server 与 pipeline
      handler/                          # 握手、心跳、空闲和文本帧 Handler
  src/main/resources/
    application.yml                     # WS/Bolt 端口、Broker 调用和注册刷新配置
```

## 核心职责

- 启动 Netty WebSocket Server。
- 在握手阶段异步调用账号服务校验 Access Token，并向 Broker 注册用户连接。
- 本机维护用户、会话版本、连接 ID 与 Netty Channel 的连接注册表。
- 客户端上行帧转交 Broker 路由到业务服务。
- Broker 下行推送时，按连接 ID 写入本机 Netty Channel。
- 定时向 Broker 注册当前网关和刷新活跃连接快照。

## 上行流程

```text
client
  -> /ws handshake
  -> WsAuthenticationManager
  -> BrokerClient.registerConnection
  -> FrameHandler
  -> BrokerClient.writeFrame
```

## Netty Pipeline

`NettyWebSocketServer#start` 为每个客户端连接注册如下 handler，顺序和职责如下：

```text
IdleStateHandler
  -> HttpServerCodec
  -> HttpObjectAggregator
  -> HandshakeHandler
  -> WebSocketServerProtocolHandler
  -> ConnectionIdleHandler
  -> PingFrameHandler
  -> FrameHandler
```

- `IdleStateHandler`：产生读空闲事件，避免连接长时间无数据但仍占用网关资源。
- `HttpServerCodec`：处理 WebSocket 握手前的 HTTP 请求编解码。
- `HttpObjectAggregator`：把 HTTP 握手请求聚合为 `FullHttpRequest`，便于统一读取路径、query 和 header。
- `HandshakeHandler`：在协议升级前校验 `path` 和 Access Token，认证通过后向 Broker 注册 `userId -> gatewayId` 路由，并把用户、会话版本和连接 ID 写入 Netty Channel。
- `WebSocketServerProtocolHandler`：执行标准 WebSocket 协议升级，处理 WebSocket 帧编解码，并限制单帧最大载荷。
- `ConnectionIdleHandler`：接收读空闲事件，主动关闭空闲连接，后续由断开流程清理本机连接表和 Broker 路由。
- `PingFrameHandler`：处理客户端 ping 心跳，直接返回 pong，避免心跳帧进入业务转发。
- `FrameHandler`：处理文本业务帧，使用 `JsonFrameCodec` 转为 `FrameRequest`，再交给 `FrameForwardService` 转发到 Broker。

关键技术点：

- TCP 粘包/拆包：网关不直接处理裸 TCP 字节流。握手阶段由 `HttpServerCodec` 解析 HTTP 报文，升级后由 `WebSocketServerProtocolHandler` 解析 WebSocket Frame，业务层只接收完整的 `TextWebSocketFrame`。
- 半包请求聚合：握手 HTTP 请求可能分多段到达，`HttpObjectAggregator` 会聚合成 `FullHttpRequest`，握手处理器再读取 path、query 和 header。
- 业务帧边界：浏览器发送的一次 WebSocket message 在 Netty 中以 WebSocket frame 进入 pipeline；`FrameHandler` 只处理文本帧，并用 `JsonFrameCodec` 解析业务 JSON。
- 大包保护：`WebSocketServerProtocolHandler` 通过 `max-frame-payload-length` 限制单帧最大载荷，避免超大帧占用过多内存。
- 握手认证和 Broker 注册发生在 `WebSocketServerProtocolHandler` 之前，注册失败时不会让客户端误以为 WS 已经可用。
- 连接上下文保存在 Netty Channel Attribute 中，包括认证身份、`sessionVersion` 和 `connectionId`。
- 账号认证使用有界专用线程池，Dubbo 调用禁用重试并设置严格超时；验证完成后回到对应 Channel 的 event loop 继续握手。
- 网关只处理协议、连接和路由转发，不在 Netty handler 中写业务逻辑。
- 心跳帧和业务文本帧分离处理，避免心跳干扰业务命令分发。
- 下行推送不经过该入站 pipeline，而是由 Broker 通过 Bolt 调用 `FrameWriteHandler` 后写入本机 Channel。

## 下行流程

```text
Broker
  -> FrameWriteHandler
  -> ConnectionRegistry
  -> Netty Channel
  -> client
```

Gateway 返回逐连接接受或失败结果，但该结果只描述本机写入，不是客户端业务 ACK。客户端收到需确认的消息通知后，会把 ACK 作为普通上行帧经 Broker 交给 Message service；回执任务、接收状态和重投策略均由 Message service 管理。

账号会话替换或退出后，Account service 会经 Broker 发出连接关闭控制。Gateway 根据 `userId + sessionVersion` 只关闭匹配旧会话版本的本机连接，避免乱序控制关闭已重新登录的新连接。

## 配置说明

核心配置前缀为 `im.gateway.ws`，集中绑定到 `GatewayProperties`：

- `port`：Netty WS 监听端口。
- 当前网关实例 ID 由 Bolt 对外地址自动生成，格式为 `gateway-{bolt.host}-{boltPort}`。
- `path`：WebSocket 握手路径，默认 `/ws`。
- `max-frame-payload-length`：单个 WS 帧最大载荷。
- `idle.reader-idle-seconds`：读空闲关闭时间。
- `broker.bolt.load-balance`：Broker 地址选择策略，默认 `HASH`；可选 `HASH`、`ROUND_ROBIN`、`RANDOM`。
- `broker.bolt.timeout-millis`：调用 Broker 超时时间。

Gateway 通过 Nacos 发现初始 Broker 地址，之后由 Broker SDK 定时读取 Broker 集群快照；不配置固定 Broker 地址或 seed address。`im.bolt.client.enabled=true` 用于创建出站 `BoltInvoker`，`im.bolt.server.enabled=true` 用于接收 Broker 下行调用。

本地默认端口为：浏览器 WebSocket `19090`、Gateway 下行 Bolt `12202`。Gateway Bolt 端口不得与同机 Broker Bolt `12200` 或 Broker 管理 HTTP `12201` 重复，否则 Broker 会把下行帧发送到错误的监听器。

## 边界说明

- `server`、`handler`、`registry`、`attributes` 是 Netty WebSocket 接入能力。
- `handler` 是 Broker 调用 WS 网关的内部通信入口，虽然 Bolt 底层基于 Netty，但这里不直接管理客户端 Channel。
- `lifecycle` 只编排服务器和注册流程，不承载消息领域业务。
- Broker 调用细节由 `im-broker-sdk` 的客户端封装。
- 业务处理统一交给 Broker 和后端服务。
- Gateway 不保存消息通知回执任务，也不根据写入结果自行重试业务通知。

## 关键技术点

- `NettyServerLifecycle` 使用 `SmartLifecycle` 管理监听端口，确保 Spring 停止时释放 EventLoop 和 Channel。
- 握手认证发生在协议升级前；Broker 注册失败时连接不会进入可用状态。
- 本机 `ConnectionRegistry` 保存 `userId + sessionVersion + connectionId + Channel`，对外查询仅返回不含 Channel 的只读连接状态。
- Broker 仍只保存 `userId -> gatewayId`，不接收本地连接 ID 或 Session 所有权。
- Broker 下发会话关闭控制时，本机只关闭匹配旧 `sessionVersion` 的连接，乱序控制不会关闭替换后的新会话。
- Gateway 周期性上报活跃用户快照，Broker 通过快照清理异常断连遗留映射。
- Broker 客户端支持 HASH、ROUND_ROBIN、RANDOM；Hash 策略用于相同路由键的稳定选择。
- 下行写入逐连接返回状态，调用方可以区分已接受和失败连接。

## 验证命令

```bash
mvn -q -pl im-gateway/im-ws-gateway/im-ws-gateway-server -am test
```
