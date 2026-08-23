# im-message-facade

消息服务跨模块契约。

- `MessageService`：私聊/群聊发送、已读、撤回和通知 ACK 的跨进程命令契约。
- `ChatService`：私聊准备、群聊创建/加入/移除/解散及群会话摘要。
- `params`、`dto`：Broker、Social 等调用方使用的稳定传输对象。

Facade 不依赖消息 Server，也不暴露消息领域实体或数据库结构。

## 关键技术点

- Broker 通过 `MessageService` 处理上行消息，Social 通过 `ChatService` 维护群成员对应会话。
- Params 明确区分私聊、群聊、已读、撤回和 ACK，避免通用 Map/JSON 侵蚀契约。
- `NotificationAckParams` 使用 `receiptType + userId + chatId + messageId` 重建稳定 `receiptId`；Facade 不暴露 Redis 任务或重投实现。
- Facade 版本升级需兼顾 Broker 和 Social 的滚动发布顺序。

```bash
mvn -q -pl im-service/im-message/im-message-facade -am test
```
