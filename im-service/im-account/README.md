# im-account

账号服务聚合模块，是普通用户身份和在线 Session 的唯一事实来源。用户与 Session 由同一部署承载，
但它们是两个生命周期和仓储边界独立的限界上下文。

## 领域位置与上下文地图

| 子域 | 类型 | 限界上下文 | 职责 |
|---|---|---|---|
| 用户子域 | 通用子域 | 用户上下文 | 普通用户身份、邮箱、密码、状态以及管理侧受控变更 |
| 在线会话子域 | 支撑子域 | Session 上下文 | Token 签发与认证、Refresh 凭证轮换、登录版本和会话撤销 |

限界上下文是模型边界，不要求独立部署。用户事实以 `User` 为运行时聚合；`ManagedUser` 是同一批
Account 用户事实的管理写聚合，包含逻辑删除状态，但不把所有权转移给 Admin。

## 统一语言

| 业务术语 | 类型 | 建模名称 |
|---|---|---|
| 普通用户 | 聚合根 | `User` |
| 被管理用户 | 聚合根 | `ManagedUser` |
| 用户标识、邮箱 | 值对象 | `UserId`、`UserEmail` |
| 原始密码、加密密码 | 值对象 | `UserRawPassword`、`UserPassword` |
| 用户状态 | 值对象 | `UserStatus` |
| 用户规则 | 领域服务 | `UserService`、`ManagedUserService`、`PasswordService` |
| 在线 Session | 聚合根 | `Session` |
| Session 版本 | 值对象 | `SessionVersion` |
| Access/Refresh Token | 值对象 | `AccessToken`、`RefreshToken` |
| Refresh Token 指纹 | 值对象 | `RefreshFingerprint` |
| Session 领域能力 | 领域服务 | `SessionService`、`SessionTokenCodec` |

## 关键不变量与生命周期

- 只有 `NORMAL` 用户可以认证；封禁或已逻辑删除的用户不能登录、刷新或通过在线 Access Token 认证。
- 用户主动修改密码必须先校验当前密码；管理侧重置密码、封禁和删除在 Account 内完成，并在事务提交后撤销在线 Session。
- 每个用户当前只保留一个有效 `Session`。重新登录生成新的 `SessionVersion` 并替换旧版本，旧 Access Token、Refresh Token 和连接随之失效。
- Refresh 必须同时匹配在线状态、Session 版本、未过期的 `RefreshFingerprint`，成功后轮换 Access/Refresh 凭证和服务端指纹。
- 退出将 Session 置为离线并清除 Refresh 指纹和过期时间；踢出一个没有在线 Session 的用户保持幂等。

## 协作与状态所有权

- Account 独占 MySQL Schema `im_chat_account`，当前用户事实位于 `db_user`；其他服务不得跨 Schema 读取。
- 在线 `Session`、当前 `SessionVersion`、Refresh 指纹和过期时间由 Account Redis 持有。Account 是唯一 Token 签发方和在线 Session 认证、撤销权威。
- `im-account-facade` 向 Gateway 和业务服务提供集中认证、用户资料及 Session 查询；调用方只依赖契约，不接触 Account Server 或存储。
- `im-account-admin-facade` 只向管理后台开放含已删除用户的投影与受控命令；它不暴露密码、Token、Session 或持久化实体。
- 登录替换、退出及管理侧撤销提交后，可经 Broker best-effort 关闭旧连接；连接控制只加速收敛，不能替代 Session 版本校验。

## 子模块与验证

- [`im-account-facade`](im-account-facade/README.md)：普通账号和 Session 的跨服务 Dubbo 契约。
- `im-account-admin-facade`：高权限普通用户管理契约。
- [`im-account-server`](im-account-server/README.md)：用户注册登录、资料、管理命令和在线 Session 实现。

```bash
mvn -q -pl im-service/im-account -am test
./scripts/verify.sh architecture
```
