# im-e2e-test

`im-e2e-test` 验证实时链路范围内的端到端行为。测试从真实 WebSocket 客户端进入，穿过 Netty Gateway、Bolt 和 Broker，并通过 Broker management HTTP 观察路由结果。

Account 认证和 Message Facade 实现作为受控测试边界替换，因此本模块不证明 Account 数据库、Message MySQL/Redis、Nacos 或 Dubbo 注册中心可用。全栈环境 E2E 需要单独的环境与数据初始化方案。

## 黄金旅程

- 认证连接在 Broker 中建立正确的用户路由；
- 上行私聊发送和 ACK 到达 Message Facade 边界；
- WebSocket 断开后路由移除；
- Gateway 重启并重新连接后路由重建。

## 验证

```bash
./scripts/verify.sh e2e
```

## Bolt 开发目标与真实边界

黄金旅程也验证真实 Broker → Gateway 下行、session close 清理和重连。fixture 在固定实际 DEV 模式下，用真实预绑定端口生成 D12 部署描述配对，再启动正式 Broker/Gateway context；不伪造 TLS principal，不覆盖原注册/租约事实。所有调用仍是 typed 目标，配对仅作 pool/routing descriptor，开发不发有效观察票、不自动摘流。该旅程不证明 mTLS 安全 HA；后者由 routing-ha 独立拥有。

消费方 startup 只隔离外部设施，不替换正式受控客户端/SDK 生命周期；模块运行与完整验收以实际同指纹报告为准，不能以 stub、静态配对检查或 create/inspect 当业务已启动。
