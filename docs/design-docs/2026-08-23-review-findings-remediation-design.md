# Review Findings Remediation Design

## 背景

集中式认证、聊天视图和 Broker 连接控制完成后，代码 Review 发现五个跨组件行为缺口：Session 写操作未使用统一锁键、Session TTL 短于 Refresh Token、刷新接口未被服务端放行、打开私聊未清理未读、连接关闭请求未转发到 owner Broker。

同时，根 README 在整理后层次更清晰，但 DDD、Harness 和运行架构的入口说明过于精简，不利于首次阅读时建立整体模型。

## 决策

### Session 一致性

登录、刷新和退出在解析出 `UserId` 后，统一使用 `SESSION_WRITE:userId` 分布式锁。由于登录和刷新无法在方法进入前得到 `UserId`，应用服务使用 `DistributedLockTemplate` 表达动态临界区，不新增只做委托的协调服务，也不把锁或 CAS 下沉 Repository。

Session Redis TTL 使用 `JwtProperties.refreshTokenTtl`，使服务端认证状态至少覆盖 Refresh Token 的可用窗口；每次登录或刷新保存 Session 时重置 TTL。

### HTTP 刷新

`/user/refreshToken` 与登录、注册一样是服务端公开路径。它只依赖请求体中的 Refresh Token，不要求 Gateway 构造的用户上下文。

### 私聊未读

`ImPrivateChat` 提供 `readToLatest()` 领域行为，同时更新 `readMessageId` 和 `unreadMessageCount`。打开私聊时激活聊天、清理未读，再保存聊天和 `ImChatView`。

### Broker 关闭路由

连接关闭 Handler 与连接注册、注销和帧处理保持相同的 owner 路由规则。非 owner Broker 通过 `BrokerPeerClient` 转发完整的 `ConnectionCloseParams`，owner Broker 才查询连接路由并调用目标 Gateway。

### README

根 README 恢复三块入口级说明：DDD 分层和依赖方向、Harness 检查层级与使用方式、当前运行组件和数据所有权。稳定细则仍由 `ARCHITECTURE.md` 和 `docs/references/**` 持有，README 不复制完整规则矩阵。

## 验证

- Session 应用测试证明三类写操作均使用同一个 `userId` 锁键。
- Cache 配置测试证明 Session TTL 等于 Refresh Token TTL。
- Web Interceptor 测试证明刷新路径无需内部身份头。
- Message 应用测试证明打开已有未读的私聊后未读归零。
- Broker Handler 测试覆盖 local owner 和 remote owner。
- 最终运行 Harness、behavior、quick 和 full 验证。
