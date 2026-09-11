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
