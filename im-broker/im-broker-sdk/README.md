# im-broker-sdk

`im-broker-sdk` 定义 Broker 的内部调用契约和默认客户端，不包含 Broker 服务端、Registry 或 Handler 实现。

## 目录

```text
broker/sdk/
├── BrokerClient.java
├── BrokerSdkAutoConfiguration.java
├── enums/          # Bolt service/operation、负载均衡和结果状态
├── lifecycle/      # Broker 地址快照刷新
├── loadbalance/    # HASH、ROUND_ROBIN、RANDOM 地址选择
└── model/
    ├── dto/
    ├── params/
    └── result/
```

## 地址发现

`BrokerClient` 构造时通过 Spring `DiscoveryClient` 查询 `ServiceName.IM_BROKER`，把发现到的 `host:port` 列表作为首次调用的 bootstrap 地址。没有可用实例时直接启动失败，不使用写死的 seed address。

首次建立调用能力后，`BrokerRefresher` 在应用就绪时及之后每 30 秒调用 `LIST_BROKERS`，从 Broker 侧获取按实例 ID 排序的集群快照并原子替换本地地址列表。刷新失败只记录警告并保留最近一次可用快照。

因此两套发现能力职责不同：

- Nacos 负责初始 Broker 地址。
- Broker registry/Gossip 负责运行期 Broker 集群快照和实时路由状态。

消费方必须启用 `im.bolt.client.enabled=true`，使 `im-bolt` 自动配置创建 `BoltInvoker`。

## 客户端边界

- `BrokerClient` 封装 Bolt service/operation、超时、地址选择和模型转换，调用方不接触 Bolt 实现。
- Gateway 注册、连接同步、帧写入和会话关闭均使用 typed params。
- `ConnectionCloseParams` 携带用户和旧 `sessionVersion`，供 Broker 路由到 Gateway；SDK 不判断会话有效性。
- HASH 用于稳定业务路由，ROUND_ROBIN 和 RANDOM 用于无固定路由键的调用。
- SDK DTO 是跨进程契约，不作为业务服务领域模型使用；Adapter 在边界完成转换。
- Broker/Gateway 本地状态、Redis、Gossip entry 和 Handler 只存在于 server。
- 下行写入结果描述 Gateway 接受与失败的连接集合，不等同于 Message notification ACK。

```bash
mvn -q -pl im-broker/im-broker-sdk -am test
```
