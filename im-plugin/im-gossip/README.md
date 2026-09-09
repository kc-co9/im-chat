# im-gossip

`im-gossip` 提供基于 digest/delta 的通用 Gossip 状态同步能力。模块负责状态差异计算、增量交换、
版本比较和内存状态存储，不理解具体业务载荷，也不负责集群成员发现、定时调度或状态持久化。

## 模块职责

- 生成并比较本地状态摘要。
- 计算节点之间缺失或版本落后的状态。
- 通过 Bolt RPC 与 peer 交换摘要和增量。
- 使用混合逻辑版本解决跨节点状态顺序冲突。
- 保存增量、更新和删除标记，并按 TTL 清理删除标记。
- 通过 `GossipSyncStore` 将同步结果交给业务模块落地。

以下能力由接入模块负责：

- 节点注册、发现和失效检测。
- Gossip 调度周期、peer 选择和 fanout 策略。
- 业务状态的序列化、反序列化和本地投影。
- 需要持久化时的存储实现。

## 核心组件

| 组件 | 说明 |
|------|------|
| `GossipSynchronizer` | 执行 digest/delta 双向同步算法 |
| `GossipPeerClient` | 基于 `im-bolt` 调用远端 Gossip 接口 |
| `GossipSyncStore` | 业务模块接入 Gossip 的状态存储接口 |
| `InMemoryGossipEntryStore` | 通用内存增量状态表 |
| `GossipVersion` | 包含时间、逻辑计数器和节点 ID 的混合逻辑版本 |
| `GossipDigestEntry` | 仅包含 key 和 version 的状态摘要 |
| `GossipDeltaEntry` | 包含实体类型、变更动作和载荷的完整增量 |

## 同步流程

发起节点主动执行一次同步：

```text
发起节点                                  响应节点
   |                                        |
   |------ GossipDigestParams ------------->|
   |       nodeId + 本地 digest              |
   |                                        | 比较双方版本
   |<----- GossipDigestResult ---------------|
   |       较新 deltas + staleKeys           |
   |                                        |
   | 合并响应节点返回的 deltas                |
   |                                        |
   |------ GossipDeltaParams --------------->|
   |       staleKeys 对应的本地 deltas        |
   |                                        | 合并增量
```

`GossipSynchronizer#syncPeer` 完成发起侧流程：

1. 将本地 digest 发送给 peer。
2. 合并 peer 返回的较新 deltas。
3. 根据 peer 返回的 `staleKeys` 读取本地 deltas。
4. 将这些 deltas 推送给 peer。

方法返回本次合并与推送的 delta 总数，供接入模块记录同步诊断指标；插件本身不保存诊断历史。

响应侧分别调用：

- `handleDigest(GossipDigestParams)`：比较摘要并返回双方差异。
- `handleDelta(GossipDeltaParams)`：合并 peer 补发的增量。

## 版本模型

每条状态使用 `GossipVersion`：

```text
timestamp + counter + nodeId
```

- `timestamp`：推进状态的物理时间线。
- `counter`：区分同一毫秒内的连续更新。
- `nodeId`：在不同节点产生相同时间和计数器时提供稳定顺序。

节点合并远端状态时会推进本地逻辑时钟，保证后续本地更新高于已经观察到的远端版本。
`InMemoryGossipEntryStore` 使用单 key 原子更新，避免并发写入导致版本回退。

## 状态模型

`GossipDeltaEntry` 包含：

| 字段 | 说明 |
|------|------|
| `key` | 业务实体的 Gossip 唯一键 |
| `version` | 用于冲突比较的混合逻辑版本 |
| `entityType` | 当前支持的 `BROKER`、`GATEWAY` 或 `CONNECTION` 实体类型 |
| `operation` | `ADDED`、`UPDATED` 或 `REMOVED` |
| `payload` | 由业务模块解释的字符串载荷 |

插件不会解析 `payload`。接入模块应保证同一种 `entityType` 使用稳定的载荷格式。

## 接入方式

业务模块需要实现 `GossipSyncStore`：

```java
public interface GossipSyncStore {
    List<GossipDigestEntry> digest();

    List<String> keysNewerThan(List<GossipDigestEntry> remoteDigest);

    List<String> keysOlderThan(List<GossipDigestEntry> remoteDigest);

    List<GossipDeltaEntry> deltas(List<String> keys);

    void merge(List<GossipDeltaEntry> deltas);
}
```

如果使用内存状态表，可以在业务适配层中组合 `InMemoryGossipEntryStore`：

```java
InMemoryGossipEntryStore entries = new InMemoryGossipEntryStore(
        localNodeId,
        removedTtlMillisSupplier);
```

然后创建同步客户端和同步器：

```java
GossipPeerClient peerClient = new GossipPeerClient(boltInvoker);
GossipSynchronizer synchronizer = new GossipSynchronizer(syncStore, peerClient);
```

RPC service、digest operation 和 delta operation 通过 `GossipSyncOperations` 由业务模块传入，插件不写死业务协议名称。

## 删除标记

删除操作不会立即移除状态，而是写入 `REMOVED` 增量，使删除信息能够传播到其他节点。删除标记超过 TTL 后，
`InMemoryGossipEntryStore` 会按本机首次观察到该删除版本的时间进行清理。

删除标记 TTL 应大于节点允许离线后重新加入集群的最长时间。否则，离线节点携带的旧状态可能在删除标记清理后重新传播。

## 使用限制

- `InMemoryGossipEntryStore` 不提供进程重启后的状态恢复。
- Gossip 提供最终一致性，不提供强一致读写或事务语义。
- peer 不可用时的重试、退避和熔断由调用方负责。
- 集群成员发现和失效节点清理由业务模块负责。

## 关键技术点

- Digest 只交换 key/version，Delta 才携带完整载荷，降低常规反熵流量。
- 混合逻辑版本以 timestamp、counter、nodeId 提供跨节点稳定全序比较。
- 单 key 合并只接受更高版本，保证并发同步不会让状态版本回退。
- 删除使用带 TTL 的 `REMOVED` 增量传播，不能直接物理删除状态。
- 插件不负责成员发现和调度，业务接入层决定 peer、fanout 和同步周期。

## 验证命令

```bash
mvn -q -pl im-plugin/im-gossip -am test
```
