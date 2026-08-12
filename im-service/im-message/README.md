# im-message

消息服务聚合模块。

- `im-message-facade`：消息、会话及社交侧会话维护契约。
- `im-message-server`：私聊/群聊消息、会话、通知和回执实现。

## 关键技术点

- 消息事实与在线投递分离，推送失败不改变已提交消息。
- Account/Social 通过 Facade 提供身份和关系投影，Broker 负责实时路由。
- 领域事件在事务提交后触发通知，Redis 回执任务提供客户端确认与重投。

```bash
mvn -q -pl im-service/im-message -am test
```
