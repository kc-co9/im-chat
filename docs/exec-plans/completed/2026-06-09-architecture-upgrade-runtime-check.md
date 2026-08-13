# 架构升级运行时检查

## 当前可验证范围

本阶段已经完成服务化骨架、HTTP Gateway、WS Gateway 协议、Broker 路由、Message 到 Broker 的推送端口，以及 Broker-backed message notifier。

当前仍保留旧 `im-bootstrap`、Spring STOMP WebSocket 和 Redis Pub/Sub 通知链路，原因是完整 message 业务实现尚未迁入 `im-service/im-message/im-message-server`。在迁入前直接删除旧 Redis consumer/notifier 会破坏现有应用。

## 本地构建检查

```bash
mvn -q test
```

期望：全量 Maven reactor 测试通过。

## HTTP Gateway 检查

启动依赖：

```text
Nacos
im-account-server
im-social-server
im-message-server
im-http-gateway
```

路由期望：

```text
/api/account/** -> im-account
/api/social/**  -> im-social
/api/message/** -> im-message
```

当前 `im-account-server`、`im-social-server`、`im-message-server` 仍处于骨架/过渡状态，HTTP runtime 检查应等业务实现迁入后执行。

## WS/Broker 检查

启动依赖：

```text
im-ws-gateway
im-broker
im-message
```

期望链路：

```text
client request frame
  -> im-ws-gateway JsonFrameCodec
  -> GatewayRealtimeService
  -> BrokerRealtimeClient
  -> im-broker BrokerRealtimeService
  -> MessageRealtimeClient
  -> im-message MessageRealtimeFacade
```

当前已通过单元测试覆盖：

```text
JsonFrameCodecTest
RealtimeCommandDispatcherTest
GatewayRealtimeServiceTest
BrokerRealtimeServiceTest
MessagePushServiceTest
BrokerMessageNotifierTest
```

## Redis Pub/Sub 替换条件

切换旧 Redis 广播前必须满足：

1. `PrivateMessageAppService`、`GroupMessageAppService`、`NotificationAckAppService` 对应能力迁入 `im-message-server`，或 `im-message-server` 能稳定委派到这些用例。
2. `im-message-server` 生成通知后调用 `BrokerMessageNotifier`。
3. Broker 可以按 `userId -> connections` 找到目标 Gateway。
4. Gateway 可以把 push frame 写入目标 connection。
5. 客户端 ACK 能通过 Gateway -> Broker -> Message 更新 receipt 状态。
6. HTTP 历史查询/离线补拉仍可用。

满足以上条件后，才能移除：

```text
RedisPublisher
RedisSubscriber
AbstractRedisImMessageNotifier
im-interfaces endpoint consumer
SimpMessagingTemplate 下行推送
```

## 后续推荐拆分任务

1. 迁移 `im-message` 业务实现。
2. 迁移 `im-account` 业务实现。
3. 迁移 `im-social` 业务实现。
4. 将旧 HTTP Controller 分别迁入业务服务。
5. 将旧 Spring STOMP Controller 替换为 WS Gateway JSON frame 命令。
6. 完成 Redis Pub/Sub 到 Broker 定向投递切换。
