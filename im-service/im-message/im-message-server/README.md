# im-message-server

消息和会话运行服务，是消息事实与消息业务规则的所有者。

## 主要职责

- 私聊、群聊消息发送、读取和撤回。
- 私聊/群聊会话维护。
- 通过 Account/Social Facade 校验身份、好友和群成员关系。
- 消息事件转通知，经 Broker SDK 精确推送到在线用户。
- Redis 保存通知确认状态和延迟重投任务。

## 分层

- `application`：消息、会话、ACK 和通知用例。
- `domain`：chat、message、account/social 投影和 sticker 模型。
- `interfaces`：HTTP 与 Dubbo 服务入口。
- `adapter`：Account/Social 远程契约适配。
- `infrastructure`：MySQL、Redis、缓存、锁和仓储实现。

服务名 `im-message`，默认 HTTP 端口 `8888`，远程配置从 `SERVICE_GROUP/im-message.yml` 加载。启动依赖 MySQL、Redis、Nacos；实时通知还依赖 Broker Bolt 地址。

## 关键技术点

- MySQL 保存消息事实、收件箱副本和会话状态，在线推送失败不回滚已提交消息。
- 应用服务在事务内完成校验与落库，事务提交后再触发通知，避免推送未提交数据。
- Account/Social Adapter 将远程 DTO 转为本地领域投影，领域层不依赖 Dubbo Params。
- 通知工厂按消息事件选择私聊/群聊、发送/撤回实现，并经 Broker 精确路由。
- 需要确认的通知在 Redis 保存 `PENDING/CONFIRMED` 状态，延迟任务只保存 receiptId，降低队列元素体积。
- 分布式锁保护消息幂等临界区，但数据库约束仍负责最终防重。

```bash
mvn -q -pl im-service/im-message/im-message-server -am test
```
