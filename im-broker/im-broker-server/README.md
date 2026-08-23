# im-broker-server

Broker 运行服务，提供 Bolt RPC 入口、运行态注册表、连接归属迁移和 Broker 间 Gossip 同步。

## 代码结构

```text
broker/
  config/                    # 客户端、注册表和配置属性装配
  domain/registry/           # Broker、Gateway、Connection 注册表及内存实现
  domain/service/            # 用户连接归属与迁移
  domain/store/              # Gossip 业务状态投影
  interfaces/handler/        # Broker/Gateway/Connection/Frame RPC Handler
  interfaces/listener/       # 注册事件到 Gossip 状态的适配
  lifecycle/                 # 本机注册、Gossip、连接迁移任务
  support/                   # peer client 与内部事件发布
  transformer/               # SDK、状态对象转换
```

## 运行模型

- Broker、Gateway 和用户到 Gateway 的映射均保存在各自内存注册表中。
- 用户连接按 `userId` 在 Broker 集群中确定归属；成员变化后迁移到新 Broker。
- Broker/Gateway/Connection 状态通过 `im-gossip` 最终一致同步。
- 上行帧通过 Dubbo 调用 message service；下行帧通过 WS Gateway SDK 精确写入网关。
- 会话关闭控制先按 `userId` 定位归属 Broker，再路由到持有该用户连接的 Gateway。
- 本模块不保存聊天历史和 WebSocket `connectionId` 集合。

## 配置

- `im.broker.instance`：本机 Broker 地址，ID 自动生成。
- `im.broker.cluster`：种子、fanout、同步超时和删除状态 TTL。
- `im.broker.registry`：Broker/Gateway/Connection TTL。
- `im.broker.gateway-push`、`im.broker.peer-call`：网关与 peer 调用超时。
- `im.bolt.server.port`：Broker Bolt 端口，同时作为非 Web Nacos 服务端口。

启动类为 `ImBrokerApplication`，默认 Bolt 端口 `12200`。

## 关键技术点

- 用户到 Gateway 的映射按 `userId` 分片到归属 Broker，避免每个 Broker 保存全量连接索引。
- 任意 Broker 都可接收连接注册、注销和关闭请求；非归属节点通过 `BrokerPeerClient` 转交归属 Broker。
- `InMemoryConnectionRegistry` 同时维护 user 和 gateway 两个方向的索引，使按用户查询和按网关快照清理都能直接定位。
- Broker 成员变化后先把连接映射迁移到新归属节点，远端确认成功后再删除本地数据。
- 注册表变化先发布内部事件，再由 Listener 写入 Gossip 状态，注册业务与同步机制解耦。
- Gossip 提供最终一致性；连接写入仍由归属 Broker 串联，不能把 Gossip 当作强一致事务日志。
- 删除状态通过 `REMOVED` 增量和 TTL 传播，防止离线节点重新带回旧注册数据。
- 关闭控制携带旧 `sessionVersion`，Broker 不解释该字段，只把它透传给 Gateway 做本地连接筛选。

```bash
mvn -q -pl im-broker/im-broker-server -am test
```
