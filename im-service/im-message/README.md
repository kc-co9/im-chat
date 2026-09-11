# im-message

消息服务聚合模块，承载聊天与消息两个核心限界上下文，并拥有收件箱副本、当前聊天视图和通知回执任务。
两个上下文共享一个部署，但各自通过 Chat 与 Message 语言表达职责；模型边界不等于部署边界。

上下文关系、存储拓扑和跨存储一致性边界以 [Message Architecture](ARCHITECTURE.md) 为准。

## 领域位置与上下文地图

| 子域 | 类型 | 限界上下文 | 职责 |
|---|---|---|---|
| 聊天子域 | 核心域 | Chat 上下文 | 用户视角的私聊/群聊会话、会话列表、已读位置、未读数和当前聊天视图 |
| 消息子域 | 核心域 | Message 上下文 | 私聊/群聊消息、用户收件箱副本、接收/已读/撤回状态和提交后的通知 |

## 统一语言

| 上下文 | 业务术语 | 类型 | 建模名称 |
|---|---|---|---|
| Chat | 聊天会话公共模型 | 聚合根抽象 | `ImChat` |
| Chat | 私聊会话、群聊会话 | 具体聚合根 | `ImPrivateChat`、`ImGroupChat` |
| Chat | 聊天标识、名称、类型、状态 | 值对象 | `ImChatId`、`ImChatName`、`ImChatType`、`ImChatStatus` |
| Chat | 当前聊天视图 | 值对象 | `ImChatView` |
| Chat | 聊天规则 | 领域服务 | `ImChatService` |
| Message | 消息公共模型 | 聚合根抽象 | `ImMessage` |
| Message | 私聊收件箱消息、群聊收件箱消息 | 具体聚合根 | `ImPrivateInboxMessage`、`ImGroupInboxMessage` |
| Message | 消息标识、Token、内容 | 值对象 | `ImMessageId`、`ImMessageToken`、`ImMessageContent` |
| Message | 消息规则 | 领域服务 | `ImMessageService` |
| Message | 私聊消息变化 | 领域事件 | `ImPrivateMessageSentEvent`、`ImPrivateMessageRevokedEvent` |
| Message | 群聊消息变化 | 领域事件 | `ImGroupMessageSentEvent`、`ImGroupMessageRevokedEvent` |

## 关键不变量与生命周期

- 聊天记录属于单个用户视角：私聊双方各有自己的 `ImPrivateChat`，群成员各有自己的 `ImGroupChat`；隐藏或已读状态不会改写他人的会话。
- `ImChat` 和 `ImMessage` 复用公共标识、校验与行为，但没有独立的通用仓储；应用通过 `ImPrivateChatRepository`、`ImGroupChatRepository`、`ImPrivateInboxMessageRepository` 和 `ImGroupInboxMessageRepository` 直接持久化、查询具体聚合根。
- 每条消息先形成发送方和接收方各自的收件箱副本，再与对应聊天状态在 MySQL 事务内提交；消息 Token 的唯一性由领域检查和数据库约束共同保证。
- 只有消息归属方可以接收或读取自己的副本，只有发送者可以撤回；已撤回消息不再暴露原内容。
- 打开聊天会记录 `ImChatView` 并把会话读到最新消息；切换聊天直接替换当前视图，退出或隐藏当前聊天会清除它。
- 未读判断只读取 Message 拥有的 `ImChatView`，不依赖 Account Session、Gateway 连接或 Broker 路由状态。

## 协作与状态所有权

- Message 独占 `im_chat_message` Schema。聊天、消息、每用户收件箱副本、已读位置和未读数是 MySQL 持久化事实。
- `ImChatView(userId, chatId)` 是 Message Redis 中的可过期当前视图；缺失或过期表示用户当前未查看该聊天。
- Redis 回执任务、延迟标识和重投次数只支撑客户端 ACK 与有界重投，是可恢复的投递状态，不是消息事实，也不是与 MySQL 原子提交的 Outbox。
- Message 只通过 Social Facade 获取好友、群成员、接收人和展示投影；Social 决定关系资格，Message 决定聊天、收件箱和消息状态。
- 消息事实提交后才经 Broker SDK 通知在线用户。Broker 只负责实时路由和投递，失败或离线不会回滚、删除持久化消息副本。

## 存储方案

| 存储 | 数据 | 权威性与生命周期 |
|---|---|---|
| MySQL `db_im_private_chat` | 每个用户视角的私聊会话、读位置、未读数、隐藏和活跃状态 | 私聊会话事实；按 `chat_id` 及 `user_id + peer_user_id` 保持唯一 |
| MySQL `db_im_group_chat` | 每个成员视角的群聊会话、群备注、读位置、未读数和活跃状态 | 群聊会话事实；按 `chat_id` 及 `group_id + user_id` 保持唯一 |
| MySQL `db_im_private_inbox_message` | 私聊双方各自的消息副本和接收/已读/撤回时间 | 私聊消息事实；Token 与 messageId 在聊天内唯一 |
| MySQL `db_im_group_inbox_message` | 群成员各自的消息副本和接收/已读/撤回时间 | 群聊消息事实；Token 与 messageId 在用户聊天视角内唯一 |
| Redis `im:message:presence:chat:{userId}` | 当前 `ImChatView` 的 chatId | 2 小时 TTL 的短期状态；缺失按未查看处理，只影响未读判断 |
| Redis Receipt Map/Queue | `ReceiptTask`、重投次数和延迟队列中的 receiptId | 可恢复的通知状态；达到上限、ACK 或 TTL 后删除，不是消息事实 |

私聊和群聊都采用“用户收件箱副本”模型，而不是只保存一条全局消息再临时计算每个用户状态。这样接收、已读、撤回、历史游标和未读数都能在用户自己的会话边界内处理，代价是群聊发送需要按成员写入多份副本。

### 写扩散与读扩散权衡

当前方案属于 **fanout-on-write（写扩散）**：消息发送时为目标用户生成收件箱副本，并同步维护用户自己的 Chat 状态。

| 方案 | 优势 | 代价 |
|---|---|---|
| 当前写扩散 | 历史、未读、接收/已读/撤回状态天然按用户隔离；读取只访问用户自己的 Chat/Inbox；离线后仍可直接恢复 | 群消息写入量随成员数增长；事务更大，热点群会增加数据库写压力；成员列表必须在发送时确定 |
| fanout-on-read（读扩散） | 发送时只写一条群消息，写入成本基本不随成员数线性增长；适合超大群或高发送频率 | 读取时要组合群消息、成员关系、加入/退出边界和用户 read cursor；未读与可见性计算更复杂，读热点和缓存一致性压力更高 |

本项目当前优先保证用户视角状态简单、可恢复，因此选择写扩散。它不是对所有群规模都最优：当实际监控显示大群成员数、群消息写 QPS、单事务行数或写延迟成为主要瓶颈时，应基于容量数据评估异步 fanout、分批写入或大小群混合策略；这些方案当前均未实现。

Message 当前没有在生产 Repository 上声明 JetCache 方法缓存；持久化查询直接以 MySQL 为事实源。Redis 的 `RMapCache` 是 Receipt Store 的实现，不应被描述为聊天或消息查询缓存。详细 Key、索引、写入顺序和失败窗口见 [Message Server README](im-message-server/README.md)。

## 子模块与验证

- [`im-message-facade`](im-message-facade/README.md)：消息、聊天以及 Social 侧会话维护的跨服务契约。
- [`im-message-server`](im-message-server/README.md)：消息写入、通知、ACK、重投、可靠性和运行配置的详细说明。

```bash
mvn -q -pl im-service/im-message -am test
./scripts/verify.sh behavior
```
