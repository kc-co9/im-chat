# im-session

认证令牌和用户上下文插件。

- `token/codec`：JWT 编解码实现。
- `token/model`：Token 数据和值类型。
- `UserContext`、`UserContextHeaders`、`UserContextUtils`：可信用户上下文及其传递约定。

HTTP/WS 接入层负责认证和写入上下文，业务服务只读取已经建立的用户身份，不直接信任外部请求头。

## 关键技术点

- JWT 只承载可验证身份信息；Access/Refresh Token 使用显式密钥和可注入时间源签发、校验。
- JWT codec 通过 `im.session.jwt.enabled=true` 显式启用，应用可提供自己的 `JwtTokenCodec` Bean 覆盖默认实现；插件不提供业务认证或旧式本地 TokenService。
- `im.session.jwt` 默认 Access TTL 为 `2h`、Refresh TTL 为 `30d`、刷新阈值为 `15m`。启用 JWT 的应用必须配置至少 64 UTF-8 字节的 `secret`；部署环境通过 Nacos 覆盖本地配置，不生成临时签名密钥。
- HTTP Gateway 会先清理外部伪造的用户上下文头，再写入认证后的内部头。
- `UserContext` 使用请求生命周期上下文，使用结束必须清理以避免线程复用污染。

```bash
mvn -q -pl im-plugin/im-session -am test
```
