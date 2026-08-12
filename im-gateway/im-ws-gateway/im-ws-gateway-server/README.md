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
    application.yml                     # WS 端口、Broker 地址、注册刷新等配置
```

## 核心职责

- 启动 Netty WebSocket Server。
- 在握手阶段校验 token，并向 Broker 注册用户连接。
- 本机维护 `connectionId -> Netty Channel` 映射。
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
  -> FrameForwardService
  -> BrokerClient.processFrame
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
- `HandshakeHandler`：在协议升级前校验 `path` 和 token，认证通过后向 Broker 注册 `userId -> gatewayId -> connectionId` 路由，并把认证身份写入 Netty Channel。
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
- 连接上下文保存在 Netty Channel Attribute 中，包括认证身份和 `connectionId`。
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

## 配置说明

核心配置前缀为 `im.gateway.ws`，集中绑定到 `GatewayProperties`：

- `port`：Netty WS 监听端口。
- 当前网关实例 ID 由 Bolt 对外地址自动生成，格式为 `gateway-{bolt.host}-{boltPort}`。
- `path`：WebSocket 握手路径，默认 `/ws`。
- `max-frame-payload-length`：单个 WS 帧最大载荷。
- `idle.reader-idle-seconds`：读空闲关闭时间。
- `broker.bolt.address`：Broker Bolt 地址，多个地址用英文逗号分隔。
- `broker.bolt.load-balance`：Broker 地址选择策略，默认 `HASH`；可选 `HASH`、`ROUND_ROBIN`、`RANDOM`。
- `broker.bolt.timeout-millis`：调用 Broker 超时时间。
- `register.enabled`：是否启用注册刷新任务。

## 边界说明

- `server`、`handler`、`registry`、`attributes` 是 Netty WebSocket 接入能力。
- `handler` 是 Broker 调用 WS 网关的内部通信入口，虽然 Bolt 底层基于 Netty，但这里不直接管理客户端 Channel。
- `lifecycle` 只编排服务器和注册流程，不承载消息领域业务。
- Broker 调用细节由 `im-broker-sdk` 的客户端封装。
- 业务处理统一交给 Broker 和后端服务。

## 关键技术点

- `NettyServerLifecycle` 使用 `SmartLifecycle` 管理监听端口，确保 Spring 停止时释放 EventLoop 和 Channel。
- 握手认证发生在协议升级前；Broker 注册失败时连接不会进入可用状态。
- 本机 `ConnectionRegistry` 保存具体 Channel，Broker 只保存 `userId -> gatewayId`，避免远端操作 Netty 对象。
- Gateway 周期性上报活跃用户快照，Broker 通过快照清理异常断连遗留映射。
- Broker 客户端支持 HASH、ROUND_ROBIN、RANDOM；Hash 策略用于相同路由键的稳定选择。
- 下行写入逐连接返回状态，调用方可以区分已接受和失败连接。

## 验证命令

```bash
mvn -q -pl im-gateway/im-ws-gateway/im-ws-gateway-server -am test
```
