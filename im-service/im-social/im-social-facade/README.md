# im-social-facade

社交服务跨模块契约。

`SocialService` 提供好友关系检查、好友展示、群成员检查、群成员列表、消息接收人和群摘要查询。`params`、`dto` 用于 Dubbo 边界，不包含社交领域实体和持久化类型。

## 关键技术点

- 消息服务只通过本契约获取好友和群成员投影，不读取社交服务数据库。
- 批量 DTO 减少群消息发送时的远程调用次数。
- 契约对象按调用方需要裁剪，避免把完整群组聚合跨服务传播。

```bash
mvn -q -pl im-service/im-social/im-social-facade -am test
```
