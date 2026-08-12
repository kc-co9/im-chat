# im-account

账号服务聚合模块。

- `im-account-facade`：账号和会话查询的跨服务 Dubbo 契约。
- `im-account-server`：用户注册登录、资料查询和在线会话实现。

## 关键技术点

- Facade 与 Server 分离，调用方只依赖账号契约。
- 用户事实持久化到 MySQL，在线 Session 使用 Redis 支撑多实例共享。
- HTTP 认证入口与内部 Dubbo 查询入口复用同一应用和领域规则。

```bash
mvn -q -pl im-service/im-account -am test
```
