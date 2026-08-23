# im-metrics

基于 Micrometer 和 Spring AOP 的声明式方法指标插件。

## 主要内容

- `@Observed`：声明需要统计的方法调用。
- `@IgnoreException`：声明失败后不影响主流程的 best-effort 方法。
- `ObservedAspect`：统一记录成功次数、失败次数和执行耗时。
- `IgnoreExceptionAspect`：捕获普通异常，可按注解配置记录 warn 日志后忽略。
- `MetricsCollector`：创建并更新 Micrometer 指标。
- `ImMetricsAutoConfiguration`：在存在 `MeterRegistry` 时自动装配切面。

业务模块只依赖 `@Observed`，不直接持有 `MeterRegistry`、`Counter` 或 `Timer`。

## 使用方式

```java
@Observed(
        name = "im.account.session.connection.close",
        ignoreFailure = true)
public void closeConnections(Long userId, String sessionVersion) {
    brokerClient.closeConnections(new ConnectionCloseParams(userId, sessionVersion));
}
```

`name` 是指标基础名称，插件会追加 `outcome=success|failure` 标签，并创建同名的
`.duration` Timer。`tags` 使用 Micrometer 的 key/value 数组格式。`ignoreFailure` 只适合
best-effort 的旁路动作；默认值为 `false`，记录失败后继续抛出异常。

`@IgnoreException` 用于隔离指标采集等非核心动作：默认记录 warn 日志，使用
`@IgnoreException(log = false)` 可以关闭日志。它只捕获 `Exception`，不会吞掉 `Error`；被忽略的方法应为无返回值或允许返回 `null`，不得用于隐藏核心业务失败。

## 关键技术点

- 自动配置只在应用提供 `MeterRegistry` 时生效，不自行创建监控注册中心。
- 指标切面不记录方法参数，避免把用户 ID、Token、连接 ID 等高基数或敏感数据写入指标。
- `MetricsCollector` 的写指标方法使用 `@IgnoreException` 隔离 Micrometer 失败，因此监控系统异常不会改变被观测方法的业务结果。
- 与 `@AfterTransactionCommit` 一起使用时，事务提交切面优先执行，确保耗时统计覆盖提交后的实际动作。
- 指标记录失败不应改变核心业务语义；旁路通知可显式使用 `ignoreFailure=true`。

```bash
mvn -q -pl im-plugin/im-metrics -am test
```
