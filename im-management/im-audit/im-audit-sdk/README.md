# IM Audit SDK

`im-audit-sdk` 为管理应用提供审计生产者能力。公共入口包括 `AuditClient.submit`、
`AuditTemplate.submit`、`AuditContextCollector.collect`、`@Audited` 和不可变的
`AuditEvent` 契约。显式业务事件使用 `AuditSubmission` 描述业务内容，由模板统一收集
调用上下文、构造事件和隔离投递故障；需要补充可信操作者信息时才使用模板的上下文变换重载。

## 行为边界

- `@Audited` 只使用注解中显式声明的动作、目标和说明；`targetId` SpEL 使用方法声明的真实参数名，例如 `#request.clientId()` 或 `#command.userId()`；标注 `@AuditAttribute` 的参数会按参数名采集，参数对象的一层属性和成功返回值也会采集。
- 密码、Secret、Cookie、Session/CSRF、Access/Refresh Token、授权码、签名材料、SQL 和调用栈
  不得进入属性或说明；敏感字段应在字段或 record component 上使用 `@AuditAttribute(include = false)` 显式排除。
- 事务成功只在提交后投递；回滚不投递成功。失败事件保留并重新抛出原业务异常。
- 收集、构造和传输失败会记录日志及有限基数指标，但不会替换业务结果。
- 生产者一次只选择 Kafka 或异步 HTTP；两种传输不会自动回退。

## 传输

Kafka 模式使用 Spring Cloud Stream `StreamBridge` 和来源专属 Binding，消息键为稳定的
`auditId`。HTTP 模式使用有界线程池，通过 IAM Client Credentials 获取 Token，只重试超时
和 `5xx`，不重试 `4xx`。HTTP-only 应用不需要引入 Kafka Binder。

传输类型、Binding 或 HTTP/IAM 参数使用类型化配置；机器客户端 Secret
只能由 Nacos/部署环境提供。HTTP 身份配置属于 `im.audit.iam.*`，HTTP 端点与线程池属于
`im.audit.http.*`。传输事件不携带 `sourceApp`：HTTP 来源由 IAM 机器身份确定，Kafka
来源由服务端的专属 Binding 确定。

## 包结构

- `model` 保存不可变审计契约，`context` 只负责收集当前调用上下文。
- `transport` 保存传输 SPI 和异常；HTTP 适配放在 `transport.http`，Kafka 适配放在
  `transport.kafka`。HTTP 身份获取属于 HTTP Transport，不单独建立通用 `security` 包。
- `support` 保存事件工厂、模板、切面和失败隔离等 SDK 编排组件；`properties` 保存类型化配置。

```yaml
im:
  audit:
    enabled: true
    transport: http
    http:
      endpoint: https://audit.invalid/internal/audits
    iam:
      token-uri: https://iam.invalid/oauth2/token
      client-id: im-admin-audit
      client-secret: REQUIRED_FROM_NACOS
```

```bash
mvn -q -pl im-management/im-audit/im-audit-sdk -am test
```
