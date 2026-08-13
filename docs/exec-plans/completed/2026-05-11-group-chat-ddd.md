# 群聊模块 DDD 完善 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按已确认 spec 完善群聊主链路：群本体 `ImGroup`、用户独有 `ImGroupChat`、接收者独立 `ImGroupInboxMessage`、500 人上限、会话列表和 DDL 同步。

**Architecture:** 群聊拆成三个领域概念：`ImGroup` 表示共享群本体，`ImGroupChat` 表示用户自己的群会话，`ImGroupInboxMessage` 表示用户 inbox 中的群消息。应用层负责协调创建群、进入群、发送、撤回和查询；仓储端口按领域概念拆分，基础设施层通过 MyBatis 持久化到改造后的群表。

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, MapStruct, JUnit 5, AssertJ, Maven.

---

## 参考文档

- Spec: `docs/superpowers/specs/2026-05-11-group-chat-ddd-design.md`
- 当前私聊参考实现：`src/main/java/com/co/kc/imchat/application/ImPrivateAppService.java`
- 当前私聊领域模型：`src/main/java/com/co/kc/imchat/domain/chat/ImPrivateChat.java`
- 当前私聊 inbox 模型：`src/main/java/com/co/kc/imchat/domain/message/ImPrivateInboxMessage.java`

## 关键决策

- 不主动提交 git。每个任务末尾的提交步骤只在用户明确要求时执行。
- `db_im_group` 作为群本体表，对应 `ImGroup`。
- `db_im_group_chat` 作为用户独有群会话表，对应 `ImGroupChat`，不保留 `setting` 字段。
- `db_im_group_inbox_message` 作为群 inbox 消息表，对应 `ImGroupInboxMessage`。
- 旧表 `db_im_group_member`、`db_im_group_receive_message`、`db_im_group_chat_session` 从 `ddl.sql` 移除，新代码不读写。
- `ChatAppService` 负责创建群、进入群、退出聊天、会话列表。
- `ImGroupAppService` 负责群消息发送、撤回、历史、详情和事件消费。
- 会话列表 DTO 当前没有未读数字段，本次只维护领域状态，不扩展 HTTP 输出字段。

## 文件结构

### 新增

- `src/main/java/com/co/kc/imchat/domain/chat/ImGroup.java`：群本体领域模型。
- `src/main/java/com/co/kc/imchat/domain/chat/ImGroupRepository.java`：群本体仓储端口。
- `src/main/java/com/co/kc/imchat/domain/message/ImGroupInboxMessage.java`：群 inbox 消息领域模型。
- `src/main/java/com/co/kc/imchat/domain/message/ImGroupInboxMessageRepository.java`：群 inbox 消息仓储端口。
- `src/test/java/com/co/kc/imchat/domain/GroupDomainModelTest.java`：群领域模型测试。

### 修改

- `src/main/java/com/co/kc/imchat/domain/chat/ImGroupChat.java`：改成用户独有群会话模型。
- `src/main/java/com/co/kc/imchat/domain/chat/ImGroupChatRepository.java`：改为只管理用户群会话。
- `src/main/java/com/co/kc/imchat/domain/chat/ImChatService.java`：按用户群会话构造群聊列表。
- `src/main/java/com/co/kc/imchat/domain/message/ImMessageService.java`：支持 `ImGroupInboxMessage` 事件创建。
- `src/main/java/com/co/kc/imchat/domain/message/ImGroupMessageSentEvent.java`：事件改为 groupId + 成员通知时再映射 chatId。
- `src/main/java/com/co/kc/imchat/domain/message/ImGroupMessageRevokedEvent.java`：同上。
- `src/main/java/com/co/kc/imchat/application/ChatAppService.java`：创建群、进入群按新模型实现。
- `src/main/java/com/co/kc/imchat/application/ImGroupAppService.java`：发送、撤回、查询按 inbox 实现。
- `src/main/java/com/co/kc/imchat/infrastructure/domain/MysqlImGroupChatRepository.java`：拆出群本体和用户群会话持久化职责。
- `src/main/java/com/co/kc/imchat/infrastructure/domain/MysqlImGroupMessageRepository.java`：改造或替换为 `MysqlImGroupInboxMessageRepository`。
- `src/main/java/com/co/kc/imchat/infrastructure/mybatis/entity/DbImGroup.java`：新增群本体 DB 实体。
- `src/main/java/com/co/kc/imchat/infrastructure/mybatis/entity/DbImGroupChat.java`：改为用户群会话 DB 实体，包含 `groupId/lastMessageId/readMessageId/unreadMessageCount`。
- `src/main/java/com/co/kc/imchat/infrastructure/mybatis/entity/DbImGroupInboxMessage.java`：新增群 inbox 消息 DB 实体，包含 `groupId/chatId/userId/receiveTime/readTime`。
- `src/main/java/com/co/kc/imchat/infrastructure/mybatis/service/DbImGroupChatService.java`：按 groupId 查询。
- `src/main/java/com/co/kc/imchat/infrastructure/mybatis/service/DbImGroupMemberService.java`：按 chatId、groupId、userId 查询。
- `src/main/java/com/co/kc/imchat/infrastructure/mybatis/service/DbImGroupMessageService.java`：按 inbox 查询和批量查询。
- `src/main/java/com/co/kc/imchat/transformer/domain/ImChatDomainTransformer.java`：DB 到群本体/用户群会话映射。
- `src/main/java/com/co/kc/imchat/transformer/db/ImChatDbTransformer.java`：群本体/用户群会话到 DB 映射。
- `src/main/java/com/co/kc/imchat/transformer/domain/ImMessageDomainTransformer.java`：DB 到 `ImGroupInboxMessage` 映射。
- `src/main/java/com/co/kc/imchat/transformer/db/ImMessageDbTransformer.java`：`ImGroupInboxMessage` 到 DB 映射。
- `src/main/java/com/co/kc/imchat/transformer/application/ImMessageAppTransformer.java`：DTO 和 notify 映射改为 inbox 消息。
- `src/main/resources/sql/ddl.sql`：同步群表结构，移除旧群表并新增新群表。

---

### Task 1: 领域模型测试先行

**Files:**
- Create: `src/test/java/com/co/kc/imchat/domain/GroupDomainModelTest.java`
- Reference: `src/test/java/com/co/kc/imchat/domain/PrivateDomainModelTest.java`

- [ ] **Step 1: 写 `ImGroupChat` 行为测试**

新增测试覆盖：

```java
@Test
void groupChatReceivesUnreadMessageWhenUserIsNotChatting() {
    ImGroupChat chat = ImGroupChat.builder()
            .id(new ImChatId(101L))
            .groupId(new ImChatId(1001L))
            .userId(new UserId(2L))
            .type(ImChatType.GROUP)
            .unreadMessageCount(0)
            .build();
    ImGroupInboxMessage message = groupMessage(900L, 101L, 1001L, 2L, 1L);

    chat.receiveLatestMessage(message, false);

    assertThat(chat.getLastMessageId()).isEqualTo(new ImMessageId(900L));
    assertThat(chat.getReadMessageId()).isNull();
    assertThat(chat.getUnreadMessageCount()).isEqualTo(1);
}
```

- [ ] **Step 2: 写进入群清未读测试**

```java
@Test
void groupChatReadsToLatestMessage() {
    ImGroupChat chat = ImGroupChat.builder()
            .id(new ImChatId(101L))
            .groupId(new ImChatId(1001L))
            .userId(new UserId(2L))
            .type(ImChatType.GROUP)
            .lastMessageId(new ImMessageId(900L))
            .unreadMessageCount(3)
            .build();

    chat.readToLatest();

    assertThat(chat.getReadMessageId()).isEqualTo(new ImMessageId(900L));
    assertThat(chat.getUnreadMessageCount()).isZero();
}
```

- [ ] **Step 3: 写 `ImGroupInboxMessage` 读、撤回权限测试**

覆盖发送者可撤回、非发送者撤回抛异常、接收者可读自己的 inbox。

- [ ] **Step 4: 运行测试确认失败**

Run: `mvn -q -Dtest=GroupDomainModelTest test`

Expected: 编译失败，提示 `ImGroupInboxMessage`、`ImGroupChat.builder().groupId(...)` 或相关方法不存在。

- [ ] **Step 5: 可选提交**

仅在用户明确要求提交时执行：

```bash
git add src/test/java/com/co/kc/imchat/domain/GroupDomainModelTest.java
git commit -m "test: add group domain model specs"
```

### Task 2: 实现群领域模型

**Files:**
- Create: `src/main/java/com/co/kc/imchat/domain/chat/ImGroup.java`
- Modify: `src/main/java/com/co/kc/imchat/domain/chat/ImGroupChat.java`
- Create: `src/main/java/com/co/kc/imchat/domain/message/ImGroupInboxMessage.java`
- Modify: `src/main/java/com/co/kc/imchat/domain/message/ImGroupMessageStatus.java`

- [ ] **Step 1: 新增 `ImGroup`**

实现字段 `ownerId/name/notification`，继承 `ImChat`，`id` 作为 `groupId` 使用，`type` 为 `GROUP`。

- [ ] **Step 2: 改造 `ImGroupChat`**

保留类名，改成用户群会话字段：`groupId/userId/groupAlias/userAlias/lastMessageId/readMessageId/unreadMessageCount`，并实现 builder、`contain`、`receiveLatestMessage`、`readMessage`、`readToLatest`、`validate`。

- [ ] **Step 3: 新增 `ImGroupInboxMessage`**

参考 `ImPrivateInboxMessage`，字段增加 `groupId/userId/receivedTime/readTime`，行为实现 `receive/read/revoke`。

- [ ] **Step 4: 运行领域测试**

Run: `mvn -q -Dtest=GroupDomainModelTest test`

Expected: PASS。

- [ ] **Step 5: 运行私聊领域测试防回归**

Run: `mvn -q -Dtest=PrivateDomainModelTest test`

Expected: PASS。

### Task 3: 更新 DDL 和 MyBatis 实体

**Files:**
- Modify: `src/main/resources/sql/ddl.sql`
- Create: `src/main/java/com/co/kc/imchat/infrastructure/mybatis/entity/DbImGroup.java`
- Modify: `src/main/java/com/co/kc/imchat/infrastructure/mybatis/entity/DbImGroupChat.java`
- Create: `src/main/java/com/co/kc/imchat/infrastructure/mybatis/entity/DbImGroupInboxMessage.java`
- Delete or stop using: `src/main/java/com/co/kc/imchat/infrastructure/mybatis/entity/DbImGroupMember.java`
- Delete or stop using: `src/main/java/com/co/kc/imchat/infrastructure/mybatis/entity/DbImGroupMessage.java`

- [ ] **Step 1: 新建 `db_im_group` DDL**

新增群本体表 `db_im_group`，字段为 `group_id/owner_id/name/notification`，不包含 `setting`。

- [ ] **Step 2: 新建 `db_im_group_chat` DDL**

字段改为用户群会话：

```sql
`chat_id` BIGINT NOT NULL DEFAULT 0 COMMENT '用户群会话ID',
`group_id` BIGINT NOT NULL DEFAULT 0 COMMENT '群ID',
`user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '用户ID',
`user_alias` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '用户群昵称',
`group_alias` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '用户定义的群备注',
`last_message_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最新消息ID',
`read_message_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已读消息ID',
`unread_message_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '未读消息数量'
```

索引：`uk_chat_id`、`uk_group_user`、`idx_user_id`、`idx_group_id`。

- [ ] **Step 3: 从 DDL 移除旧群表**

删除旧 `db_im_group_member`、`db_im_group_receive_message`、`db_im_group_chat_session` 的 `DROP TABLE` 和 `CREATE TABLE` 段落。

- [ ] **Step 4: 新建 `db_im_group_inbox_message` DDL**

增加 `group_id/user_id/receive_time/read_time`，索引按 spec 添加。

- [ ] **Step 5: 更新实体字段**

新增 `DbImGroup`；`DbImGroupChat` 改为用户群会话实体；新增 `DbImGroupInboxMessage`。旧 `DbImGroupMember` 和 `DbImGroupMessage` 可以删除，或先保留但不再被新代码引用。

- [ ] **Step 6: 编译确认实体影响**

Run: `mvn -q -DskipTests compile`

Expected: 可能失败，提示 transformer、service、repository 仍引用旧字段。这是下一任务要修复的预期结果。

### Task 4: 更新仓储端口和 DB 服务

**Files:**
- Create: `src/main/java/com/co/kc/imchat/domain/chat/ImGroupRepository.java`
- Modify: `src/main/java/com/co/kc/imchat/domain/chat/ImGroupChatRepository.java`
- Create: `src/main/java/com/co/kc/imchat/domain/message/ImGroupInboxMessageRepository.java`
- Modify: `src/main/java/com/co/kc/imchat/infrastructure/mybatis/service/DbImGroupChatService.java`
- Modify or replace: `src/main/java/com/co/kc/imchat/infrastructure/mybatis/service/DbImGroupMemberService.java`
- Modify or replace: `src/main/java/com/co/kc/imchat/infrastructure/mybatis/service/DbImGroupMessageService.java`

- [ ] **Step 1: 新增 `ImGroupRepository` 端口**

方法：`save(ImGroup)`、`find(ImChatId groupId)`、`find(List<ImChatId> groupIds)`。

- [ ] **Step 2: 改 `ImGroupChatRepository` 端口**

方法：`find(ImChatId chatId)`、`findByGroupId(ImChatId groupId)`、`findByUserId(UserId userId)`、`save(ImGroupChat)`、`saveAll(List<ImGroupChat>)`、`contain(ImChatId chatId, UserId userId)`。

- [ ] **Step 3: 新增 `ImGroupInboxMessageRepository` 端口**

按 spec 定义 `saveAll`、`contain`、`find`、`queryDetail`、`queryHistory`、`findByGroupIdAndMessageId`、`findLastMessageList`。

- [ ] **Step 4: 更新 DB service 查询方法**

`DbImGroupService` 按 `groupId` 查询群本体；`DbImGroupChatService` 支持用户群会话的 `chatId`、`groupId`、`userId` 查询；`DbImGroupInboxMessageService` 支持 `chatId + userId + token/messageId` 和 `groupId + messageId` 查询。可以通过重命名旧 service 或新增 service 完成，但新代码命名应对齐新表名。

- [ ] **Step 5: 编译确认端口影响**

Run: `mvn -q -DskipTests compile`

Expected: 仍可能失败，提示基础设施仓储和 transformer 未适配。

### Task 5: 更新 transformer 和基础设施仓储

**Files:**
- Modify: `src/main/java/com/co/kc/imchat/transformer/domain/ImChatDomainTransformer.java`
- Modify: `src/main/java/com/co/kc/imchat/transformer/db/ImChatDbTransformer.java`
- Modify: `src/main/java/com/co/kc/imchat/transformer/domain/ImMessageDomainTransformer.java`
- Modify: `src/main/java/com/co/kc/imchat/transformer/db/ImMessageDbTransformer.java`
- Modify: `src/main/java/com/co/kc/imchat/infrastructure/domain/MysqlImGroupChatRepository.java`
- Modify or rename: `src/main/java/com/co/kc/imchat/infrastructure/domain/MysqlImGroupMessageRepository.java`

- [ ] **Step 1: 更新 chat transformer**

增加 `DbImGroup -> ImGroup`、`ImGroup -> DbImGroup`、`DbImGroupChat -> ImGroupChat`、`ImGroupChat -> DbImGroupChat` 映射。

- [ ] **Step 2: 更新 message transformer**

把群消息 DB 映射从 `ImGroupMessage` 切到 `ImGroupInboxMessage`，DB 实体使用 `DbImGroupInboxMessage`，包含 `groupId/userId/receiveTime/readTime`。

- [ ] **Step 3: 改 `MysqlImGroupChatRepository`**

实现 `ImGroupRepository` 和 `ImGroupChatRepository`，或者拆成两个 repository 类。推荐拆成：

- `MysqlImGroupRepository`：管理 `DbImGroup`。
- `MysqlImGroupChatRepository`：管理 `DbImGroupChat`。

- [ ] **Step 4: 改群 inbox 仓储**

推荐把 `MysqlImGroupMessageRepository` 改名为 `MysqlImGroupInboxMessageRepository`，实现 `ImGroupInboxMessageRepository`。如果为了减少改名范围，也可保留类名但实现新端口。

- [ ] **Step 5: 移除基础设施层对 `DbImGroupChatSessionService` 的依赖**

`MysqlImGroupChatRepository`、`MysqlImGroupMessageRepository` 不再注入或调用 `DbImGroupChatSessionService`。

- [ ] **Step 6: 编译确认**

Run: `mvn -q -DskipTests compile`

Expected: 基础设施层编译通过；应用层可能仍有旧接口调用失败。

### Task 6: 更新应用服务和会话列表

**Files:**
- Modify: `src/main/java/com/co/kc/imchat/application/ChatAppService.java`
- Modify: `src/main/java/com/co/kc/imchat/application/ImGroupAppService.java`
- Modify: `src/main/java/com/co/kc/imchat/domain/chat/ImChatService.java`
- Modify: `src/main/java/com/co/kc/imchat/infrastructure/config/BeanConfig.java`

- [ ] **Step 1: 更新 bean 注入**

确保新增仓储端口和应用服务构造参数能被 Spring 注入。

- [ ] **Step 2: 改创建群逻辑**

`createGroupChat` 合并 owner 和 members，校验 <= 500，创建 `ImGroup` 和每人一条 `ImGroupChat`，返回 owner chatId。

- [ ] **Step 3: 改进入群逻辑**

`enterGroupChat` 校验当前用户拥有该 `chatId`，调用 `imChatService.enterChat` 后 `readToLatest` 并保存。

- [ ] **Step 4: 改发送群消息逻辑**

按 sender chatId 查询、幂等校验、为每个成员创建 `ImGroupInboxMessage`、更新各自 `ImGroupChat`、发布事件。发送者自己的 inbox 消息立即已读；其他成员使用 `UserService.isChatting(memberChatId, memberUserId)` 判断是否立即已读，否则未读数加 1。

- [ ] **Step 5: 改撤回群消息逻辑**

按 sender inbox 校验权限，按 `groupId + messageId` 批量撤回 inbox，发布事件。

- [ ] **Step 6: 改历史和详情查询**

所有查询都先校验 `chatId` 属于当前用户，再查该用户 inbox。

- [ ] **Step 7: 改会话列表群聊部分**

`ImChatService.buildGroupChatDescriptors` 从用户 `ImGroupChat` 出发，批量查 `ImGroup` 获取名称，查当前用户 inbox 最新消息，构造 descriptor。

- [ ] **Step 8: 编译确认**

Run: `mvn -q -DskipTests compile`

Expected: 可能失败在事件/通知 transformer，下一任务修复。

### Task 7: 更新事件、通知和 DTO 映射

**Files:**
- Modify: `src/main/java/com/co/kc/imchat/domain/message/ImMessageService.java`
- Modify: `src/main/java/com/co/kc/imchat/domain/message/ImGroupMessageSentEvent.java`
- Modify: `src/main/java/com/co/kc/imchat/domain/message/ImGroupMessageRevokedEvent.java`
- Modify: `src/main/java/com/co/kc/imchat/transformer/application/ImMessageAppTransformer.java`
- Modify: `src/main/java/com/co/kc/imchat/infrastructure/support/notifier/GroupSentNotifier.java`
- Modify: `src/main/java/com/co/kc/imchat/infrastructure/support/notifier/GroupRevokedNotifier.java`
- Modify: `src/main/java/com/co/kc/imchat/endpoint/consumer/ImGroupMessageSentConsumer.java`
- Modify: `src/main/java/com/co/kc/imchat/endpoint/consumer/ImGroupMessageRevokedConsumer.java`

- [ ] **Step 1: 事件改为 groupId 语义**

`ImGroupMessageSentEvent` 和 `ImGroupMessageRevokedEvent` 携带 `groupId/messageId/senderId/...`，成员自己的 `chatId` 在通知时根据成员 `ImGroupChat` 解析。

- [ ] **Step 2: DTO 映射使用 `ImGroupInboxMessage`**

`ImGroupMessageDTO` 可保持不改，对外仍叫 group message；transformer 输入改为 inbox 模型。

- [ ] **Step 3: 通知命令使用成员 chatId**

`ImGroupAppService.onMessageSent/onMessageRevoked` 根据 `groupId` 查询群内成员 `ImGroupChat`，构造 notify cmd 时传成员自己的 `chatId/userId`，不能再使用旧共享群 `chatId`。

- [ ] **Step 4: 编译确认**

Run: `mvn -q -DskipTests compile`

Expected: PASS。

### Task 8: 应用层测试和回归测试

**Files:**
- Create or Modify: `src/test/java/com/co/kc/imchat/application/GroupChatAppServiceTest.java`
- Modify if needed: existing tests under `src/test/java/com/co/kc/imchat`

- [ ] **Step 1: 添加创建群测试**

覆盖 owner 自动加入、成员去重、501 人抛 `BusinessException`。

- [ ] **Step 2: 添加发送群消息测试**

使用 mock repository 验证每个成员生成一条 `ImGroupInboxMessage`，发送者已读，非聊天接收者未读 +1。

- [ ] **Step 3: 添加重复 token 测试**

当 repository `contain(senderChatId, senderId, token)` 为 true，`sendMessage` 抛 `RepeatException`。

- [ ] **Step 4: 添加撤回测试**

发送者撤回时，`groupId + messageId` 下所有 inbox 都变为 revoked；非发送者撤回抛异常。

- [ ] **Step 5: 添加查询权限测试**

历史和详情使用不属于自己的 `chatId` 时抛 `BusinessException`。

- [ ] **Step 6: 运行新增测试**

Run: `mvn -q -Dtest=GroupDomainModelTest,GroupChatAppServiceTest test`

Expected: PASS。

- [ ] **Step 7: 运行全量测试**

Run: `mvn test`

Expected: PASS。

### Task 9: 收尾检查

**Files:**
- Check: all modified files

- [ ] **Step 1: 查找旧模型残留**

Run:

```bash
rg -n "ImGroupMessageRepository|ImGroupMessage|DbImGroupChatSession|DbImGroupMember|DbImGroupMessage|db_im_group_chat_session|db_im_group_member|db_im_group_receive_message|setting" src/main/java src/main/resources/sql/ddl.sql
```

Expected: 只允许保留有意兼容的 DTO/事件名称；不应有新代码读写旧群表实体、`DbImGroupChatSession` 或群 setting 字段。

- [ ] **Step 2: 检查 git diff**

Run: `git diff --stat`

Expected: 变更集中在群聊领域、应用、基础设施、transformer、DDL 和测试。

- [ ] **Step 3: 汇总验证结果**

记录 `mvn test` 是否通过。如果失败，记录失败测试和原因。

- [ ] **Step 4: 可选提交**

仅在用户明确要求提交时执行：

```bash
git add src/main/java src/main/resources/sql/ddl.sql src/test/java docs/superpowers
git commit -m "feat: complete group chat ddd flow"
```
