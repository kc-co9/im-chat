# im-account-server

账号运行服务，负责用户身份、认证和在线会话。

## 分层

- `interfaces/http`：注册、登录和用户查询 HTTP API。
- `interfaces/rpc`：`AccountService` Dubbo 实现。
- `application`：账号、用户和会话用例编排。
- `domain/user`、`domain/session`：用户与会话模型、仓储和领域服务，包括与具体 Token 格式无关的 Session credential 签发与认证能力。
- `adapter`：账号服务调用 Broker 等外部系统的出站适配器。
- `infrastructure`：MySQL 用户仓储、Redis Session、JetCache，以及 BCrypt、JWT、HMAC 等领域服务技术实现和技术配置。

服务名 `im-account`，默认 HTTP 端口 `8886`，远程配置从 `SERVICE_GROUP/im-account.yml` 加载。依赖 MySQL、Redis、Nacos 和至少一个已注册 Broker，并通过 `im.dubbo.enabled` 控制 Dubbo。

## 关键技术点

- 用户是账号事实，MySQL 持久化；Session 是运行态，Redis 持久化并支持跨实例查询。
- 密码只以 BCrypt 哈希保存，原始密码值对象不进入仓储模型。
- Cached Repository 装饰 MySQL Repository，缓存失效不能改变账号业务结果。
- HTTP 负责登录、刷新和注册，Dubbo Facade 提供集中式 Access Token 认证、用户资料和会话状态给内部服务。
- 登录替换或退出在 Session 事务提交后通过 Broker 发送旧会话关闭控制。该控制是 best-effort 加速机制，Session version 在线校验仍是撤销事实。
- Domain、Application、Interface、Infrastructure 依赖方向保持向内，远程和数据库类型不进入领域模型。

## 令牌签发与刷新策略

- 登录成功后生成新的 `sessionVersion`，一次性签发 Access Token 和 Refresh Token，并在同一事务中保存当前 Session、Refresh Token 指纹和过期时间。
- Access Token 默认有效期为 `2h`，只用于 HTTP 认证和 WebSocket 握手；客户端在剩余 `15m` 内主动调用刷新接口，不通过普通请求静默签发新令牌。
- Refresh Token 默认有效期为 `30d`，只用于刷新接口。刷新时必须校验签名、令牌类型、有效期、当前 Session version 和服务端保存的指纹。
- 刷新成功后同时轮换 Access Token 和 Refresh Token，并原子保存新的 Refresh Token 指纹；旧 Refresh Token 立即失效，重复或并发使用旧令牌返回统一认证失败。
- 每个用户当前只保留一个有效 Session。再次登录会生成新的 `sessionVersion` 并替换旧凭证，旧设备的 Access Token、Refresh Token 和 WebSocket 连接都会失效或被关闭。
- Access Token 过期但 Refresh Token 仍有效时，客户端可以在不重新输入账号密码的情况下恢复会话；Refresh Token 过期或校验失败时必须重新登录。

## 会话控制配置

- `im.session.jwt.enabled`：启用 JWT codec，账号服务必须为 `true`。
- `im.session.jwt.secret`：JWT 签名密钥，至少 64 个 UTF-8 字节；仓库值仅用于本地，部署时由 Nacos 的同名配置覆盖。
- `im.bolt.client.enabled`：启用 Broker SDK 所需的 `BoltInvoker`，账号服务必须为 `true`。
- `im.account.broker.timeout-millis`：Broker 调用超时时间，默认 `3000`。

账号服务通过 Nacos 发现初始 Broker 地址，之后由 Broker SDK 定时读取 Broker 集群快照。本地不配置固定 Broker 地址或 seed address。

连接关闭结果通过 `im.account.session.connection.close{outcome=success|failure}` 计数器和 `im.account.session.connection.close.duration` timer 暴露；指标不包含用户、Session、连接或 Token 标签。

```bash
mvn -q -pl im-service/im-account/im-account-server -am test
```
