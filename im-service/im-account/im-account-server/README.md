# im-account-server

账号运行服务，负责用户身份、认证和在线会话。

## 分层

- `interfaces/http`：注册、登录和用户查询 HTTP API。
- `interfaces/rpc`：`AccountService`、`AccountSessionService` Dubbo 实现。
- `application`：账号、用户和会话用例编排。
- `domain/user`、`domain/session`：用户与会话模型、仓储和领域服务。
- `infrastructure`：MySQL 用户仓储、Redis Session、JetCache 和 BCrypt。

服务名 `im-account`，默认 HTTP 端口 `8886`，远程配置从 `SERVICE_GROUP/im-account.yml` 加载。依赖 MySQL、Redis、Nacos，并通过 `im.dubbo.enabled` 控制 Dubbo。

## 关键技术点

- 用户是账号事实，MySQL 持久化；Session 是运行态，Redis 持久化并支持跨实例查询。
- 密码只以 BCrypt 哈希保存，原始密码值对象不进入仓储模型。
- Cached Repository 装饰 MySQL Repository，缓存失效不能改变账号业务结果。
- HTTP 负责登录注册，Dubbo Facade 提供 Token、用户资料和会话状态给内部服务。
- Domain、Application、Interface、Infrastructure 依赖方向保持向内，远程和数据库类型不进入领域模型。

```bash
mvn -q -pl im-service/im-account/im-account-server -am test
```
