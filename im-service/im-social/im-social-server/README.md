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

OpenAPI 页面为 `GET /social/api/doc.html`，API description 为 `GET /social/v3/api-docs`。

Social 独占 `im_chat_social` Schema，拥有好友、群组和群成员表。本地初始化执行模块根目录
[`sql/ddl.sql`](sql/ddl.sql)；用户与消息数据只通过对应 Facade 访问。

## 关键技术点

- 好友关系和群组分别建模，但由同一社交上下文维护关系一致性。
- 群成员变更通过 Message Facade 同步会话侧状态，避免直接写消息服务表。
- Account Adapter 只获取用户投影，社交领域不依赖账号领域实体。
- 好友列表与跨服务好友展示查询由应用服务批量读取 Account 用户投影，再由好友领域服务生成不可持久化的 `FriendProfile`；展示名优先使用好友备注，其次使用用户名，账号不存在时才回退为用户 ID。
- Cached Repository 使用旁路缓存装饰 MySQL 仓储，写操作负责失效相关缓存。
- `SocialRpcService` 返回消息投递需要的最小接收人和群摘要数据。

```bash
mvn -q -pl im-service/im-social/im-social-server -am test
```
