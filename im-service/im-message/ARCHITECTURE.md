# Message Architecture

## 上下文与职责

`im-message` 在一个部署服务中承载 Chat 与 Message 两个内部上下文：Chat 管理用户视角的私聊/群聊会话、已读和当前查看状态；Message 管理私聊/群聊收件箱副本、发送、接收、已读、撤回与通知事实。模型边界不等于部署边界，但两个上下文通过 Domain Service 和 Application Service 显式协作。

## 存储拓扑

```text
MySQL im_chat_message（持久化事实）
  db_im_private_chat
  db_im_group_chat
  db_im_private_inbox_message
  db_im_group_inbox_message

Redis（可过期运行状态）
  userId -> ImChatView
  receiptId -> ReceiptTask
  receiptId -> attempts
  ReceiptType/hash shard -> delayed receiptId queue
```

MySQL 是聊天和消息恢复来源。Redis Chat View 只参与未读判断；Receipt 状态只参与通知确认与重投。任何 Redis 状态都不替代消息副本或聊天事实。

## 写入与通知边界

```mermaid
sequenceDiagram
    participant Client as 调用方
    participant App as Message AppService
    participant DB as MySQL
    participant Receipt as Redis Receipt Store
    participant Broker
    Client->>App: 发送 Command
    App->>App: 校验关系、会话和 Token 幂等
    App->>DB: 事务保存 Chat 与 Inbox 副本
    DB-->>App: commit + domain event
    App->>Receipt: 保存 ReceiptTask
    App->>Broker: 首次在线投递
```

- 私聊为发送者和接收者分别保存收件箱副本；群聊为每个目标成员保存用户自己的会话与消息副本。
- 唯一索引以 chat/user/token/message 组合阻止同一用户视角的重复消息，数据库约束负责最终防重。消息发送的分布式锁按会话事实划分：私聊使用排序后的双方 userId，群聊使用 groupId；锁使用自动续期并最多等待 100 ms，竞争超时以业务错误形成有界背压，不能让开放式流量无限占用 RPC 线程。解析锁范围的发送者 Chat 查询发生在锁外；私聊锁内重新读取双方 Chat，群聊锁内读取 fanout 所需的成员 Chat，两者均在锁内检查 messageToken 幂等并提交成员副本，事务提交且释放锁后再发布发送事件。messageToken 只负责幂等，客户端重试应复用原 Token，不能把它放入互斥 key 后让同一会话的正常并发绕过保护。
- 群消息 fanout 的新 Inbox 使用 MyBatis batch insert；已有 Inbox 状态和成员 Chat 通过固定字段集合的 MyBatis-Plus Lambda Update 以 `ExecutorType.BATCH` 更新，每条 SQL 都包含 `user_id` 分片键与旧 `rowVersion`。成员 Chat 的该入口只允许更新最新消息、已读消息、未读数、状态和活跃时间。ShardingSphere 路由并合并逻辑批次结果，MyBatis Service 在提交前检查 `BatchResult` 中每条更新的影响行数，只有全部为 `1` 才视为成功，否则抛出乐观锁或数据访问异常并回滚。批量保存后的领域对象不再继续持久化，因此不回填数据库技术状态。
- 通知发生在 MySQL 提交后。Confirmable 通知先持久化 ReceiptTask，再由虚拟线程执行首次在线投递；虚拟线程启动失败或 Broker/Gateway RuntimeException 由 invoker 隔离并交给重试任务，不占用发送事务/会话锁，不回滚消息事实，也不把已提交发送返回成失败。未启用重试的通知保持同步执行并传播异常。
- private/group 发送 AppService 分别使用固定指标 `im.message.private.send`、`im.message.group.send` 记录锁、校验、事务和提交后动作的聚合 duration；不记录任何用户、会话、群、消息或 token 标签。
- ReceiptTask 与 MySQL 不原子提交，因此当前机制不是 Outbox；进程在提交后、Receipt 保存或重排队列窗口崩溃时可能丢失通知机会。

当前群消息采用 fanout-on-write，在发送事务中生成成员 Inbox/Chat 副本，以写放大换取用户读取、未读和状态维护的局部性。fanout-on-read 或异步/混合 fanout 会改变事务、可见性和恢复语义；只有容量数据证明同步成员副本成为瓶颈时才重新设计，详细权衡见模块 README。

## ACK 与有界重投

```mermaid
flowchart TD
    Save[首次投递前保存 ReceiptTask] --> Poll[delay 到期轮询 receiptId]
    Poll --> Exists{任务仍存在?}
    Exists -->|否| Skip[清理 attempts 并跳过]
    Exists -->|是| Limit{超过重投上限?}
    Limit -->|是| Drop[删除任务，消息仍保留在 MySQL]
    Limit -->|否| Deliver[attempt + 1 并重新投递]
    Deliver --> Delay[登记下一次 delay]
    Ack[客户端 ACK] --> Confirm[执行接收状态动作]
    Confirm --> Remove[删除 task / attempts / queued id]
```

重复投递是允许的，客户端与 ACK 处理必须幂等。Gateway 写入成功只证明本机 Channel 接受写入，不证明客户端处理完成。

## 跨上下文与跨服务协作

- Gateway 建立可信用户上下文，Message 不信任外部伪造身份。
- Social Facade 提供好友、群和成员投影；Message 不访问 Social 表。
- Social 好友提交后的 `preparePrivateChat` 是一次 RPC、一个 Message 事务的双侧会话准备；按 canonical user pair 有界加锁，任一侧已存在时只补缺失侧，避免跨服务串行两次调用形成单侧事实。
- Broker 只负责在线路由；Message 拥有 ReceiptTask、ACK 业务动作和重投策略。
- `ImChatView` 属于 Message，不放入 Account Session、Gateway 或 Broker。

## 依赖规则

- `im-message-facade` 只包含跨服务契约，不依赖 Server。
- Domain Repository 描述 Chat/Message 语义；MySQL、Redis、MyBatis 和 Redisson 实现在 infrastructure。
- Application Service 编排事务、锁、跨上下文调用和事件；领域模型不依赖 Spring 或传输 DTO。
- 详细表、Key、索引、配置和故障排查由模块 README 与 Server README 维护，本文只固定所有权和一致性边界。
