# im-lock

基于 Redisson 的分布式锁插件。

## 核心组件

- `@DistributeLock`：声明式加锁。
- `DistributeLockAspect`：解析锁键并执行临界区。
- `DistributedLockTemplate`：编程式锁模板。
- `LockOptions`：封装锁过期和获取等待策略。
- `LockClient`、`RedissonLockClient`：锁实现 SPI 与默认实现。

业务模块负责定义锁场景和业务键，插件只提供锁执行语义。使用前需要配置可用的 `RedissonClient`。

## 关键技术点

- 注解切面根据业务场景和参数构造稳定锁键，避免不同用例误用同一锁空间。
- 模板统一处理获取、执行和释放，释放前校验当前线程持有状态。
- 模板同时提供 `Callable<T>` 和 `Runnable` 重载；有返回值和无返回值用例都统一使用 `execute`。
- 不传 `LockOptions` 时默认使用自动续期且不等待；`AUTO_RENEW_DEFAULT_WAIT` 提供 100ms 的短等待，`autoRenew(waitTimeMs)` 用于显式等待策略。
- `LockOptions.AUTO_RENEW_NO_WAIT`、`AUTO_RENEW_DEFAULT_WAIT` 表达常用语义；只有调用方确实需要自定义过期时间时才使用 `LockOptions.of(...)` 或底层时间参数重载。
- 模板原样传播运行时异常和 `Error`，并将回调抛出的受检异常统一包装为 `LockException`。
- 分布式锁只保护临界区，数据库唯一约束和业务幂等仍需保留。

```bash
mvn -q -pl im-plugin/im-lock -am test
```
