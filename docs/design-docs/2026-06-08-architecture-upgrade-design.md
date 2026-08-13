# IM 架构升级设计

## 背景

当前 IM 服务是一个 Spring Boot 多模块应用。实时连接使用 Tomcat 上的 Spring WebSocket/STOMP，消息事实保存在 MySQL，在线会话和缓存状态依赖 Redis，跨实例通知通过 Redis Pub/Sub 广播到所有应用实例。每个实例收到广播后，再尝试通过本机 `SimpMessagingTemplate.convertAndSendToUser` 投递给本机 WebSocket 用户队列。

这个方案实现简单，也能支持多实例部署，但扩容效率较低。每条通知都会发送到所有实例，真正持有目标用户连接的实例通常只有一个或少数几个。

本次升级的目标是用 Broker 定向路由替换 Redis 全实例广播，同时把项目推进到更清晰的服务化架构。

## 目标

1. 将普通 HTTP 流量和 IM 实时流量拆成两条独立运行链路。
2. 将 Tomcat/Spring STOMP WebSocket 替换为 Netty WebSocket 网关。
3. 将 Redis Pub/Sub 全实例广播替换为 Broker 定向投递。
4. 引入 Nacos 作为注册中心和配置中心。
5. 普通服务间业务调用使用 Dubbo。
6. Gateway、Broker、Message 之间的 IM 实时链路使用 SOFA Bolt。
7. 第一阶段轻微拆分现有业务域为 account、social、message。
8. 消息事实、业务 ACK 状态、重投和补偿仍归 message 服务负责。
9. 为后续更细粒度的业务服务拆分预留空间。

## 非目标

1. 不保留 STOMP 兼容性，新 WebSocket 协议使用自定义 IM JSON 协议。
2. 不把 IM Broker 做成通用 HTTP 网关。
3. 第一阶段不把每个限界上下文都拆成独立服务。
4. 不把业务消息状态下沉到 Gateway 或 Broker。
5. 不移除 HTTP 历史查询和离线补拉能力。

## 目标运行架构

```text
HTTP:
client
  -> im-http-gateway
  -> im-account / im-social / im-message

Realtime:
web client
  -> im-ws-gateway
  -> im-broker
  -> im-message
  -> im-broker
  -> im-ws-gateway
  -> receiver client

Governance:
Nacos registry + Nacos config
```

HTTP 和实时流量刻意分离。HTTP 网关只处理普通请求的入口治理；IM Broker 只处理实时连接路由和定向推送，不混普通 HTTP 请求。

## 模块结构

项目结构参考 `life-platform` 的 `common/plugin/gateway/service` 风格，同时保留 IM 专用 Broker。

```text
im-chat
  im-common
    # 通用异常、Result、工具类、基础枚举

  im-plugin
    im-cache
    im-lock
    im-datasource
    im-session
    im-web
    im-dubbo
    im-bolt
    im-mq

  im-gateway
    im-http-gateway
      # Spring Cloud Gateway，普通 HTTP 入口

    im-ws-gateway
      im-ws-gateway-facade
      im-ws-gateway-server

  im-broker
    im-broker-facade
    im-broker-server

  im-service
    im-account
      im-account-facade
      im-account-server

    im-social
      im-social-facade
      im-social-server

    im-message
      im-message-facade
      im-message-server
```

### 命名规则

`facade` 模块放跨进程接口、请求响应 DTO、枚举和错误码。调用方只依赖 facade，不依赖服务实现。

`server` 模块是可启动服务实现，放 Spring Boot 启动类、provider 实现、业务编排和基础设施装配。

`gateway` 模块是对外入口层。`broker` 模块是 IM 实时链路中间层。`service` 模块承载业务服务。

## 业务服务拆分

第一阶段做轻量业务拆分：

```text
im-account
  注册、登录、token、用户资料

im-social
  好友、拉黑、群组、群成员、关系校验

im-message
  会话、私聊/群聊消息、收件箱副本、已读、撤回、ACK、重投
```

这样可以避免一次性拆成太多服务，导致发消息主链路跨越过多服务。等新运行架构稳定并有压测数据后，再评估是否继续拆 relation、group、chat、message、delivery。

## HTTP 链路

```text
client
  -> im-http-gateway
  -> im-account-server / im-social-server / im-message-server
```

`im-http-gateway` 基于 Spring Cloud Gateway，负责：

1. HTTP 统一入口。
2. 路由匹配。
3. 鉴权前置。
4. 限流扩展点。
5. 灰度路由扩展点。
6. CORS、日志和基础安全策略。

业务服务直接暴露自己的 HTTP Controller：

```text
/api/account/** -> im-account-server
/api/social/**  -> im-social-server
/api/message/** -> im-message-server
```

普通服务间业务调用使用 Dubbo：

```text
im-message-server -> Dubbo -> im-social-server
im-message-server -> Dubbo -> im-account-server
```

第一阶段不单独引入 HTTP facade 服务。Spring Cloud Gateway 直接路由到对应业务服务。

## 实时 WebSocket 链路

新实时链路使用 Netty WebSocket 和自定义 IM JSON 协议。

```text
client
  -> im-ws-gateway
  -> SOFA Bolt
  -> im-broker
  -> SOFA Bolt / Dubbo
  -> im-message
```

第一阶段 WebSocket 只覆盖实时消息命令：

```text
message.private.send
message.private.read
message.private.revoke
message.group.send
message.group.read
message.group.revoke
notification.ack
```

登录、好友管理、群组管理、会话列表、历史消息查询、消息详情和离线补拉仍走 HTTP。

### IM JSON Frame

客户端请求：

```json
{
  "version": "1",
  "type": "request",
  "cmd": "message.private.send",
  "seq": "10001",
  "traceId": "abc",
  "body": {}
}
```

服务端响应：

```json
{
  "version": "1",
  "type": "response",
  "cmd": "message.private.send",
  "seq": "10001",
  "code": 0,
  "message": "OK",
  "body": {}
}
```

服务端推送：

```json
{
  "version": "1",
  "type": "push",
  "cmd": "message.private.sent",
  "eventId": "evt-xxx",
  "receiptId": "xxx",
  "body": {}
}
```

协议扩展通过 codec 和版本边界处理：

1. `JsonFrameCodec` 是默认 v1 codec。
2. 后续可以新增二进制 codec，不影响 Gateway 路由。
3. 后续如有老客户端迁移诉求，可以新增兼容 codec。
4. `version` 固定作为每个 frame 的协议版本字段。

## Broker 路由

`im-broker` 是 IM 实时路由层，负责：

1. Gateway 注册。
2. 连接注册和注销。
3. `userId -> connections` 映射。
4. Gateway 到 message 的实时命令转发。
5. Message 到 Gateway 的定向投递。

路由状态使用：

```text
Broker 内存热路由 + Redis TTL 兜底
```

连接模型：

```text
userId -> [
  {
    gatewayId,
    connectionId,
    deviceId,
    platform,
    connectedAt,
    lastSeenAt
  }
]
```

Gateway 通过调用 Broker 写入路由状态：

1. 连接鉴权成功后，Gateway 向 Broker 注册连接。
2. 连接断开时，Gateway 向 Broker 注销连接。
3. Gateway 批量刷新连接活性，不为每个客户端心跳单独调用 Broker。

同一用户允许多个连接在线。定向推送默认发送给目标用户的全部在线连接。

## ACK、重投和离线恢复

系统区分连接层投递和业务层 ACK。

```text
im-ws-gateway
  连接、心跳、协议编解码、socket 写入成功/失败

im-broker
  路由查询、目标 Gateway/Connection、投递转发

im-message
  业务 ACK、消息状态、重投、补偿
```

ACK 语义按用户维度计算：

1. 一个用户可以有多个连接。
2. 推送发送到该用户所有在线连接。
3. 任一连接 ACK，即确认该用户已收到通知。
4. `im-message` 持有 receipt 状态和有限重投逻辑。
5. 客户端仍使用 HTTP 历史查询和离线补拉作为最终恢复路径。
6. 客户端必须按 `messageId`、`eventId` 或 `receiptId` 做幂等展示。

现有 Redis ACK 和延迟重投思路保留，但重投时不再通过 Redis Pub/Sub 广播，而是调用 Broker 做定向投递。

## 技术选型

```text
注册中心/配置中心: Nacos
HTTP 网关: Spring Cloud Gateway
普通业务 RPC: Dubbo
实时内部 RPC: SOFA Bolt
WebSocket: Netty
路由兜底: Redis TTL
消息事实: MySQL
ACK/重投: Redis + 延迟重投任务
```

Dubbo 和 SOFA Bolt 同时存在，但边界必须清晰：

1. Dubbo 用于普通业务服务间调用。
2. SOFA Bolt 用于 Gateway/Broker/Message 的实时链路通信。
3. 两套通信的线程池、超时、重试策略要隔离，避免普通查询流量阻塞实时投递。

## 迁移阶段

### 第一阶段：技术架构升级

1. 创建新的 Maven 模块结构。
2. 引入 Nacos、Spring Cloud Gateway、Dubbo、SOFA Bolt 和 Netty。
3. 将现有代码拆入 `im-account`、`im-social`、`im-message`。
4. HTTP API 通过 `im-http-gateway` 路由。
5. 实现 Netty WebSocket Gateway 和 IM JSON 协议。
6. 实现 Broker 连接注册、路由查询和定向推送。
7. 用 Broker 定向投递替换 Redis Pub/Sub 通知广播。
8. 保留业务 ACK、重投和离线补拉语义。

### 第二阶段：边界和可靠性优化

1. 度量消息发送延迟、推送延迟、重投率和路由 miss 率。
2. 根据数据决定是否拆 relation、group、chat 或 delivery。
3. 只在指标证明必要时引入读模型或事件同步。
4. 增加熔断、链路追踪、灰度发布和压测看板。

## 待定实现细节

1. Broker 调用 `im-message` 时是否只使用 SOFA Bolt，还是允许非实时子调用使用 Dubbo。
2. Nacos namespace/group 命名规则。
3. Redis 路由兜底 key schema。
4. 定向投递失败后的重投次数和退避策略。
5. Gateway 鉴权使用 query token、header，还是首帧 auth。
