# im-social

社交服务聚合模块。

- `im-social-facade`：好友、群组和群成员查询契约。
- `im-social-server`：好友关系与群组领域实现。

## 关键技术点

- 好友与群组归属同一社交服务，但保持独立聚合和仓储边界。
- Message 只通过 Facade 获取关系投影，服务之间不共享数据表。
- 群成员变化经跨服务契约维护消息会话，不直接操作消息领域模型。

```bash
mvn -q -pl im-service/im-social -am test
```
