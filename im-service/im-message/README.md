# im-message

消息服务聚合模块，拥有聊天、消息、收件箱副本、当前聊天视图和通知回执任务。

- `im-message-facade`：消息、会话及社交侧会话维护契约。
- `im-message-server`：私聊/群聊消息、聊天状态、通知、ACK 和有界重投实现。

## 关键技术点

- 消息事实与在线投递分离，推送失败不改变已提交消息。
- Gateway 建立可信用户上下文，Social 通过 Facade 提供关系投影，Broker 负责实时路由。
- 领域事件在事务提交后触发通知，Redis 待确认任务提供客户端 ACK 与有界重投。
- 详细消息、通知、ACK 和失败语义见 [im-message-server README](im-message-server/README.md)。

```bash
mvn -q -pl im-service/im-message -am test
```
