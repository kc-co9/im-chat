# im-message-server

消息和聊天运行服务，是消息事实、收件箱副本、聊天状态和通知回执任务的所有者。

## 主要职责

- 私聊、群聊消息发送、接收、已读、撤回和历史查询。
- 私聊、群聊会话及当前 `ImChatView` 维护。
- 使用 Gateway 建立的可信用户上下文，并通过 Social Facade 校验好友和群成员关系。
- 将消息领域事件转换为通知，经 Broker SDK 精确推送到在线用户。
- 使用 Redis 保存待确认通知任务、延迟重投标识和重投次数。

## 分层

- `interfaces`：HTTP、Dubbo 和进程内领域事件入口。
- `application`：消息、聊天、通知 ACK 和跨对象用例编排。
- `application/notification`：通知模型、Notifier 选择、回执任务和 ACK 接收规则。
- `domain`：chat、message、social 投影和 sticker 领域模型。
- `adapter`：Social Facade 与其他外部能力适配。
- `infrastructure`：MySQL、Redis、缓存、锁、Broker 通知和仓储实现。

服务名为 `im-message`，默认 HTTP 端口为 `8888`，远程配置从 `SERVICE_GROUP/im-message.yml` 加载。启动依赖 MySQL、Redis、Nacos 和 Dubbo；实时通知通过 Nacos 发现初始 Broker，再由 Broker SDK 定时刷新 Broker 集群快照。

OpenAPI 页面为 `GET /message/api/doc.html`，API description 为 `GET /message/v3/api-docs`。

Message 独占 `im_chat_message` Schema，拥有私聊/群聊会话和收件箱消息表。本地初始化执行模块根目录
[`sql/ddl.sql`](sql/ddl.sql)；群组与用户事实通过 Social、Account Facade 获取，不执行跨 Schema SQL。

## 消息写入流程

```text
WS command
  -> Broker
  -> MessageRpcService
  -> PrivateMessageAppService / GroupMessageAppService
       | validate user, chat, friendship or membership
       | validate message token uniqueness
       | build sender and receiver inbox copies
       v
     transaction
       | save inbox messages
       | update chat and unread state
       | publish domain event
       v
     transaction commit
       -> message listener
       -> @AfterTransactionCommit notification method
```

MySQL 中的消息、收件箱副本和聊天状态是持久化事实。通知只在事务成功提交后开始，Broker 或 Gateway 不可用不会回滚已提交消息。接收方离线时，在线推送结果为空，但其消息副本仍可通过聊天和历史接口读取。

私聊发送会根据接收方的 `ImChatView` 判断是否累加未读数；群聊发送为每个接收人维护各自的收件箱和聊天状态。该判断属于 Message 上下文，不依赖 Account Session、Gateway 或 Broker 状态。

## 通知流程

### 通知类型

| 领域事实 | PUSH command | 接收方 | ACK 的业务动作 |
|---|---|---|---|
| 私聊消息已发送 | `message.private.sent` | 私聊对端用户 | 更新接收方消息为已接收 |
| 私聊消息已撤回 | `message.private.revoked` | 私聊对端用户 | 清理待确认任务 |
| 群聊消息已发送 | `message.group.sent` | 除发送者外的群成员 | 更新该成员消息为已接收 |
| 群聊消息已撤回 | `message.group.revoked` | 拥有群聊会话的群成员 | 清理待确认任务 |

群通知按成员自己的 `chatId` 创建独立通知和 `receiptId`。一个用户可以有多个 WebSocket 连接，Broker/Gateway 会返回逐连接投递结果；任一客户端连接发出的有效 ACK 都会确认该用户对应的回执任务。

### 组件职责

| 组件 | 职责 |
|---|---|
| `ImMessageNotifierInvoker` | 选择 Notifier；先登记回执任务，再发起首次通知 |
| `ImMessageNotifierFactory` | 按通知 Java 类型或 `ReceiptType` 定位 Notifier |
| `AbstractBrokerImMessageNotifier` | 生成 PUSH 帧、`eventId` 和 `receiptId`，记录离线或投递失败结果 |
| `MessagePushService` | 通过 Broker SDK 按接收用户推送帧并记录有限基数指标 |
| `ImMessageConfirmableService` | 创建、重放和确认 `ReceiptTask` |
| `RedisImMessageConfirmableStore` | 保存任务内容、延迟队列标识和重投次数 |
| `NotificationAckAppService` | 执行对应 ACK 业务动作并删除回执任务 |

## ACK 与重投

```text
NotifierInvoker
  -> save ReceiptTask(receiptId, type, notification, 2s)
  -> initial Broker delivery

client ACK before delay expires
  -> update receive state when applicable
  -> remove task + queued receiptId + attempt counter

delay expires without ACK
  -> poll receiptId
  -> load ReceiptTask
  -> increment attempt
  -> deliver again
  -> schedule next delay
  -> stop after 3 redelivery attempts
```

Redis 中没有单独的 `PENDING` 或 `CONFIRMED` 状态。任务内容存在表示仍待确认；ACK 会删除任务。延迟队列中偶尔残留但已经找不到任务内容的 `receiptId` 会被直接跳过。

当前 Redis 结构为：

- `receiptId -> ReceiptTask`：完整通知和回执类型。
- `receiptId -> attempts`：已执行的重投次数。
- 延迟队列：只保存 `receiptId`，先按 `ReceiptType`、再按 `receiptId` hash 分片。

默认重投间隔为 2 秒，首次通知后最多执行 3 次重投。通知允许重复到达，客户端必须按 `eventId`、`receiptId` 或消息标识幂等更新界面。

## 可靠性边界

- 消息事实与在线通知分离；推送失败、接收方离线或部分连接失败不删除消息副本。
- 回执任务保存在 Redis，服务重启后其他实例可以继续消费到期任务。
- 当前机制是有界重投，不是与 MySQL 事务原子提交的 Outbox。
- 当前消费流程会先完成一次重投，再删除旧任务并登记下一轮延迟任务；进程在该窗口崩溃时可能丢失后续重投。
- 达到重投上限后任务被删除，不进行无限重试；用户仍可从持久化消息事实恢复内容。
- ACK、通知和客户端展示必须保持幂等，不能依赖“恰好一次”投递。

跨模块可靠性保证和已知债务见仓库 [RELIABILITY.md](../../../docs/RELIABILITY.md)。

## 聊天视图与未读

- `openPrivateChat`、`openGroupChat` 记录当前查看的聊天并把会话读到最新消息。
- `exitChat` 清理当前查看状态。
- `hidePrivateChat`、`hideGroupChat` 隐藏列表项并同步清理当前查看状态。
- `ImChatView` 按用户保存在 Redis，默认有效期为 2 小时；缺失或过期按“未查看”处理。

## 监控与排查

- Broker 推送使用 `im.message.broker.frame.write{outcome=success|failure}` 计数器。
- `im.message.broker.frame.write.duration` 记录 Broker 调用耗时。
- 指标不使用用户、消息、聊天、连接或 Token 作为标签。
- 接收方离线记录 debug 日志；Broker 未处理或存在失败连接时记录 warn 日志。
- 消费回执重投任务异常时记录 error，并把任务重新放回延迟队列。

## 关键约束

- 应用服务在事务内完成校验与落库，事务提交后再触发通知。
- 分布式锁保护消息幂等临界区，数据库约束仍负责最终防重。
- Message 只通过 Facade 读取 Social 投影，不访问其他服务数据库。
- Broker 和 Gateway DTO 不进入消息领域模型，转换在边界完成。

## 验证

```bash
mvn -q -pl im-service/im-message/im-message-server -am test
./scripts/verify.sh behavior
```
