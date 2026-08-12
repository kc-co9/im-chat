# im-session

认证令牌和用户上下文插件。

- `TokenService`、`JwtTokenService`：JWT 签发与解析。
- `TokenDTO`：令牌结果。
- `UserContext`、`UserContextHeaders`、`UserContextUtils`：可信用户上下文及其传递约定。

HTTP/WS 接入层负责认证和写入上下文，业务服务只读取已经建立的用户身份，不直接信任外部请求头。

## 关键技术点

- JWT 只承载可验证身份信息，服务端通过统一 `TokenService` 控制签发和解析。
- HTTP Gateway 会先清理外部伪造的用户上下文头，再写入认证后的内部头。
- `UserContext` 使用请求生命周期上下文，使用结束必须清理以避免线程复用污染。

```bash
mvn -q -pl im-plugin/im-session -am test
```
