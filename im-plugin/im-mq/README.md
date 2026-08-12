# im-mq

轻量消息发布订阅抽象，目前提供进程内实现。

- `MessagePublisher`、`MessageSubscriber`：消息总线 SPI。
- `MqMessage`：消息载荷。
- `InMemoryMessageBus`：同一 JVM 内同步发布订阅实现。

当前实现不提供跨实例投递、持久化、重试或消费确认，不能代替生产消息中间件。

## 关键技术点

- Publisher/Subscriber SPI 将业务事件与消息中间件实现隔离。
- 内存总线适合单进程解耦和测试，消息发布与消费处于同一进程生命周期。
- 更换远程 MQ 时应保持消息契约，补充幂等、重试、顺序和失败处理语义。

```bash
mvn -q -pl im-plugin/im-mq -am test
```
