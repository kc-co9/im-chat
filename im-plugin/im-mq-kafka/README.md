# im-mq-kafka

Kafka 基础设施插件，通过 Spring Cloud Stream Kafka Binder 为运行服务提供
Kafka Producer、Consumer、绑定、重试和死信配置能力。

本模块不定义通用 MQ SPI，也不拥有业务消息契约。业务模块通过 Spring Cloud
Stream API 声明自身的 Binding 和消息模型，并由本模块提供 Kafka Binder 运行时。

HTTP-only 或不使用 Kafka 的应用不应引入本模块。

## 关键技术点

- Kafka Binder 自身负责 Spring Boot AutoConfiguration，本模块不增加空的包装 Bean。
- Topic、Consumer Group、重试、DLQ 和认证配置由使用方按业务语义声明。
- 业务模块不得依赖 Kafka 原生客户端类型，也不得把业务契约放入本插件。

```bash
mvn -q -pl im-plugin/im-mq-kafka -am test
```
