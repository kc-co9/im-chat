# im-identity

`im-identity` 统一提供 Snowflake ID 算法、机器 ID 分配接口、静态实现以及
基于 Redis 租约的动态机器 ID 分配能力。插件根据配置创建
`ISnowflakeMachineId` 和 `SnowflakeId`，业务模块不自行选择或构造实现。
Redisson 是可选依赖，STATIC 模式不会被动引入 Redis。

## 配置

```yaml
spring:
  application:
    name: im-iam

im:
  identity:
    snowflake:
      mode: REDIS
      data-center-id: 0
      # 未配置时使用 spring.application.name
      namespace: im-iam
      lease-duration: 10m
      heartbeat-interval: 30s
```

`mode` 必填。固定机器 ID 使用 STATIC：

```yaml
im:
  identity:
    snowflake:
      mode: STATIC
      data-center-id: 1
      machine-id: 1
```

STATIC 模式不需要 Redisson。REDIS 模式要求 Redisson 类和
`RedissonClient` Bean 同时存在，缺失时启动失败，不会静默回退为固定机器 ID。
应用提供自定义 `ISnowflakeMachineId` 或 `SnowflakeId` Bean 时，自动配置会回退，
但仍须声明 `mode`，使运行策略在配置中保持可见。

## 运行语义

- 每个 JVM 使用独立随机 owner ID，不以 IP 地址判断实例身份。
- 分配、续租和释放通过 Lua 原子校验 owner。
- 本地使用单调时钟维护租约截止线，JVM 长暂停超过租约后不会继续生成 ID。
- 续租失败后本地机器 ID 立即失效；恢复 Redis 所有权前，业务 ID 生成失败。
- 正常关闭时主动释放；异常退出后由租约超时回收。

Redis 是 REDIS 模式的启动和运行依赖。`heartbeat-interval` 必须小于
`lease-duration`，且 `lease-duration` 至少为 1 秒。不同命名空间可以复用相同
机器槽位；需要跨服务保证 ID 唯一的应用必须配置相同的 `namespace`。

租约时长在构造器校验后作为 Lua 数字字面量写入分配脚本，不通过 Redisson 默认 Codec 的
`ARGV` 传递；字符串参数继续使用同一 Codec，以兼容已有租约字段和 owner 编码。

## 验证

```bash
mvn -q -pl im-plugin/im-identity -am test
```
