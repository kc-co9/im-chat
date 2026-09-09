# im-social

社交服务聚合模块。

- `im-social-facade`：好友、群组和群成员查询契约。
- `im-social-server`：好友关系与群组领域实现。

## 关键技术点

- 好友与群组归属同一社交服务，但保持独立聚合和仓储边界。
- `Friend` 只表达 Social 拥有的好友关系事实；`FriendProfile` 是当前用户视角下组合好友关系与 Account 用户投影得到的派生资料，不持久化。
- `FriendDisplayName` 是 `FriendProfile` 的最终展示名称，按好友备注、Account 用户名、好友用户 ID 的顺序确定；它不等同于用户名或备注。
- Message 只通过 Facade 获取关系投影，服务之间不共享数据表。
- 群成员变化经跨服务契约维护消息会话，不直接操作消息领域模型。

```bash
mvn -q -pl im-service/im-social -am test
```
