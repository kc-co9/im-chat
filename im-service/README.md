# im-service

业务服务聚合模块，按限界上下文拆分账号、社交和消息服务。每个服务通常由 `*-facade` 与 `*-server` 组成。

| 服务 | 职责 |
|------|------|
| `im-account` | 用户身份、认证、资料与在线会话 |
| `im-social` | 好友关系、群组和群成员 |
| `im-message` | 聊天、私聊/群聊消息、通知和待确认回执任务 |

Facade 只定义跨服务契约；Server 实现领域、应用、HTTP/RPC 和基础设施。跨服务调用依赖 Facade，不依赖其他 Server。

## 关键技术点

- 按账号、社交、消息限界上下文拆分数据所有权和领域模型。
- 服务间通过 Dubbo Facade 交换裁剪后的数据投影，不共享数据库和内部实体。
- HTTP 入口经 Gateway，实时入口经 WS Gateway/Broker，业务服务不直接维护客户端连接。

```bash
mvn -q -pl im-service -am test
```
