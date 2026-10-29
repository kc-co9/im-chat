# im-ws-gateway-sdk

## 模块作用

`im-ws-gateway-sdk` 是 WS 网关对内部服务暴露的调用 SDK。

当前主要给 Broker 使用，用于把实时帧写入指定 WS 网关中的用户连接，或关闭指定旧会话版本的连接。SDK 内包含调用参数、返回对象、Bolt RPC 标识，以及按网关地址发起调用的客户端。

## 目录结构

```text
im-ws-gateway-sdk/
  src/main/java/com/co/kc/imchat/gateway/ws/sdk/
    GatewayClient.java           # Broker 访问 WS 网关的 Bolt 客户端
    enums/                       # Bolt service / operation 与写入状态
    model/
      params/                    # RPC 入参
      dto/                       # 单次写入明细
      result/                    # RPC 结果
```

## 契约说明

- `GatewayFrameWriteParams`：包含目标用户和需要写入的实时帧；具体连接由目标网关本地查找。
- `GatewayFrameWriteDTO`：单个连接的接受或失败明细。
- `GatewayFrameWriteResult`：聚合当前网关的逐连接写入结果。
- `ConnectionCloseParams`：包含目标用户和需要关闭的旧 `sessionVersion`。
- `GatewayBoltService` / `GatewayBoltOperation`：Bolt 内部通信使用的服务名和操作名。
- `GatewayClient`：按明确的 WS 网关地址发起写帧或关闭连接调用；网关查找由 Broker 负责。

## 依赖边界

- SDK 模块只包含内部调用所需的轻量对象和客户端，不依赖 Netty、Spring Boot 运行服务。
- SDK 不维护连接索引，不做路由决策。
- 内部处理实现放在 `im-ws-gateway-server`，Broker 依赖 SDK 发起调用。

## 关键技术点

- RPC service/operation 使用稳定枚举，避免调用方散落字符串协议。
- 写帧结果逐连接返回状态，支持部分成功而不是整批二元成功/失败。
- DTO 保持纯传输语义，不引用 Netty Channel 或 Server 内部 Registry。

## 使用场景

```text
Broker
  -> GatewayClient
  -> GatewayBoltService.FRAME / CONNECTION
  -> GatewayBoltOperation.WRITE_FRAME / CLOSE_CONNECTIONS
  -> WS Gateway Server
  -> local ConnectionRegistry
  -> matching Netty Channel
  -> WebSocket client
```

写帧成功仅表示目标 Gateway 已接受对应连接的本次写入，不表示客户端已经处理通知。客户端业务 ACK 会沿上行链路返回 Message service，由 Message service 确认回执任务。

## 验证命令

```bash
mvn -q -pl im-gateway/im-ws-gateway/im-ws-gateway-sdk -am test
```

## 固定目标策略

`GatewayClient` 构造器直接接收 `BoltInvoker`、`timeoutMillis`、`BoltTransportMode mode` 和 `List<BoltTarget> developmentTargets`，内部校验并保存模式与配对快照。`GatewayIdentityConfiguration` 包装类型已删除，不保留兼容构造器；外部 Java 消费者须同步参数并重编译。目标身份使用固定 `PeerIdentityConstants.GATEWAY_URI_PREFIX`（`spiffe://im-chat/gateway/`），不提供项目前缀覆盖；mode 来自实际受控客户端，Broker 装配校验 running client 与 Invoker 同源。MTLS 仅从注册的严格 Gateway URI 构造 `BoltTarget`，完整身份在连接层匹配，不用地址或配对替代证书。

DEVELOPMENT_PLAINTEXT 只在合法 trusted-network marker 与 endpoint literal host 相等后，按完整地址查唯一部署配对。该 URI 仅为 pool/routing descriptor，不是认证证明，不写回 principalId、DTO/Gossip，也不改变 Gateway 租约。配对快照拒绝重复地址、非法 URI/端点；MTLS 不使用 DEV 配对选目标。写帧和关闭调用都使用统一 typed invoke，无旧兼容入口；开发网络限制见 [im-bolt](../../../im-plugin/im-bolt/README.md)。

固定五角色命名空间与环境 CA 隔离见[安全规范](../../../docs/SECURITY.md#固定项目角色身份命名空间)。通用 `im.bolt.client.allowed-peer-uri-prefixes` 仍可配置，不能扩展项目角色规则。旧项目前缀键无别名且不再生效，未知属性可能被忽略，不保证启动失败；自定义前缀部署须协调二进制、证书身份与配置迁移，不能只升级二进制或在回滚时只恢复旧键。
