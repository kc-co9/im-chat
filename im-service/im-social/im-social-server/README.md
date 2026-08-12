# im-social-server

好友与群组运行服务。

## 分层与职责

- `interfaces/http`：好友和群组管理 API。
- `interfaces/rpc/SocialRpcService`：供消息服务查询社交关系。
- `application`：好友、群组用例编排。
- `domain/friend`、`domain/group`：好友和群组领域模型。
- `adapter/account`、`adapter/message`：跨服务 Facade 适配。
- `infrastructure`：MySQL、JetCache 仓储实现与 Bean 装配。

服务名 `im-social`，默认 HTTP 端口 `8887`，远程配置从 `SERVICE_GROUP/im-social.yml` 加载。依赖 Account/Message Facade、MySQL、Redis、Nacos 和 Dubbo。

## 关键技术点

- 好友关系和群组分别建模，但由同一社交上下文维护关系一致性。
- 群成员变更通过 Message Facade 同步会话侧状态，避免直接写消息服务表。
- Account Adapter 只获取用户投影，社交领域不依赖账号领域实体。
- Cached Repository 使用旁路缓存装饰 MySQL 仓储，写操作负责失效相关缓存。
- `SocialRpcService` 返回消息投递需要的最小接收人和群摘要数据。

```bash
mvn -q -pl im-service/im-social/im-social-server -am test
```
