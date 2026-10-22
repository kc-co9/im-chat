# im-lock

提供基于 Redisson 的分布式锁，以及不依赖 Redis 的本机锁。

## 核心组件

- `MutexLock`：实现 JDK `Lock` 的本机非线程所有权锁。
- `@DistributeLock`：声明式加锁。
- `DistributeLockAspect`：解析锁键并执行临界区。
- `DistributedLockTemplate`：编程式锁模板。
- `LockOptions`：封装锁过期和获取等待策略。
- `LockClient`、`RedissonLockClient`：锁实现 SPI 与默认实现。

业务模块负责定义锁场景和业务键，插件只提供锁执行语义。分布式锁使用前需要配置可用的 `RedissonClient`；`MutexLock` 可直接构造，无需 Spring 或 Redis。

## 本机锁

```java
MutexLock lock = new MutexLock();
```

一个实例对应一个互斥域，需要互斥的调用方必须共享同一实例，不能每次调用重新创建。锁不绑定线程，允许获取线程将释放责任交给异步完成线程；它不提供按 key 查找、全局单例或跨进程保证。

- 非重入、非公平：同一线程已获取后再次 `tryLock()` 返回 false，再次 `lock()` 会等待释放。
- `lock()` 等待至成功，保留中断标记；`lockInterruptibly()` 和限时 `tryLock(time, unit)` 支持中断。
- `tryLock()` 不等待；零或负超时的限时获取仍尝试立即获取，失败返回 false。
- `unlock()` 允许跨线程调用；空闲时释放抛出 `IllegalMonitorStateException`。`newCondition()` 不支持，抛出 `UnsupportedOperationException`。
- 不自动过期或释放。调用方必须保证一次成功获取只释放一次，且底层任务实际结束后才释放。外层等待超时不等于任务结束。
- 没有线程或 token 所有权校验：占用期间无法识别错误释放，也无法阻止旧回调的重复释放误释放后续任务。空闲释放检查不代表完整的重复释放检测。
- 成功释放前的写入对随后成功获取可见；跨线程交接任务和释放责任仍需要正常的 happens-before 关系。

实现使用 AQS 的二态独占同步，不创建后台线程。内部同步状态不代表线程所有权。现有 `DistributedLockTemplate` 及注解仍遵循 Redisson 的线程持有语义，不能用于跨线程释放本机锁。本轮未提供本机 Template 或自动装配。

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
