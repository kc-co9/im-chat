# Chat Relationship Management Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete friend/private-chat and group membership/settings lifecycle operations with logical deletion and permission-aware messaging.

**Architecture:** Add MyBatis-Plus logical deletion at the DAO layer, then build domain/application operations on top of active records only. Friend deletion, group exit/kick, and chat removal create logically deleted rows so re-add/rejoin creates new friend/member/chat rows and preserves message history. Message sending checks active relationship state before writing inbox records.

**Tech Stack:** Java 8, Spring Boot 2.7, MyBatis-Plus 3.5, MapStruct, JUnit 5, AssertJ, MySQL.

---

## File Map

- `src/main/java/com/co/kc/imchat/infrastructure/mybatis/entity/BaseEntity.java`: add `isDeleted` field with MyBatis-Plus logical delete metadata.
- `src/main/resources/sql/ddl.sql`: add `is_deleted` to all DAO tables and update unique keys where re-create is allowed.
- `src/main/resources/application.yml`: configure MyBatis-Plus logic delete values if annotation-only is not enough.
- `src/main/java/com/co/kc/imchat/infrastructure/mybatis/service/BaseMybatisService.java`: add helper for logical delete by row id if needed.
- `src/main/java/com/co/kc/imchat/infrastructure/mybatis/service/*Service.java`: active queries should keep working through MyBatis-Plus logical delete; add targeted logical delete helpers for friend/chat/member.
- `src/main/java/com/co/kc/imchat/domain/friend/Friend.java`: add alias change, delete semantics cleanup, and active/block checks.
- `src/main/java/com/co/kc/imchat/domain/friend/FriendRepository.java`: add active relationship queries and logical delete operations.
- `src/main/java/com/co/kc/imchat/infrastructure/domain/MysqlFriendRepository.java`: implement active relationship checks and logical delete.
- `src/main/java/com/co/kc/imchat/application/FriendAppService.java`: expose block/unblock/delete/remark semantics and re-add behavior.
- `src/main/java/com/co/kc/imchat/endpoint/http/FriendController.java`: add missing endpoints for block/unblock/change alias.
- `src/main/java/com/co/kc/imchat/model/cqrs/command/friend/*.java` and `src/main/java/com/co/kc/imchat/model/io/friend/*.java`: add remark command/request.
- `src/main/java/com/co/kc/imchat/application/PrivateMessageAppService.java`: enforce active bidirectional friend relationship before send.
- `src/main/java/com/co/kc/imchat/domain/group/Group.java`: add transfer owner and notification update behavior.
- `src/main/java/com/co/kc/imchat/domain/group/GroupRoster.java`: add leave/kick validation helpers.
- `src/main/java/com/co/kc/imchat/domain/group/GroupMember.java`: add user alias update behavior.
- `src/main/java/com/co/kc/imchat/domain/chat/ImGroupChat.java`: add group alias update behavior.
- `src/main/java/com/co/kc/imchat/domain/group/GroupMemberRepository.java`: add logical delete and lookup operations.
- `src/main/java/com/co/kc/imchat/domain/chat/ImGroupChatRepository.java`: add save/delete operations as needed.
- `src/main/java/com/co/kc/imchat/application/GroupAppService.java`: add kick, leave, transfer owner, group notification, group alias, member alias operations.
- `src/main/java/com/co/kc/imchat/endpoint/http/GroupController.java` and `src/main/java/com/co/kc/imchat/endpoint/http/ChatController.java`: expose new HTTP endpoints.
- `src/main/java/com/co/kc/imchat/model/cqrs/command/group/*.java`, `src/main/java/com/co/kc/imchat/model/cqrs/command/chat/*.java`, `src/main/java/com/co/kc/imchat/model/io/group/*.java`: add commands/requests.
- Tests under `src/test/java/com/co/kc/imchat/application`, `src/test/java/com/co/kc/imchat/domain`, `src/test/java/com/co/kc/imchat/infrastructure`, and transformer tests.

---

### Task 1: Add Logical Delete Foundation

**Files:**
- Modify: `src/main/java/com/co/kc/imchat/infrastructure/mybatis/entity/BaseEntity.java`
- Modify: `src/main/resources/sql/ddl.sql`
- Modify: `src/main/java/com/co/kc/imchat/infrastructure/mybatis/service/BaseMybatisService.java`
- Test: `src/test/java/com/co/kc/imchat/infrastructure/mybatis/entity/BaseEntityTest.java` or nearest existing infrastructure test

- [ ] **Step 1: Write failing test for logical delete metadata**

Create a test that reflects `BaseEntity.isDeleted` exists and defaults to active when newly constructed.

- [ ] **Step 2: Run test**

Run: `mvn -q -Dtest=BaseEntityTest test`
Expected: FAIL because `isDeleted` does not exist.

- [ ] **Step 3: Implement BaseEntity logical delete field**

Add:

```java
@TableLogic(value = "0", delval = "id")
@TableField(value = "is_deleted")
private Long isDeleted;
```

If MyBatis-Plus does not support `delval = "id"` as a field expression, implement targeted delete helpers in repository/service using `update set is_deleted = id` and keep `@TableLogic(value = "0", delval = "1")` only for query filtering. Verify generated behavior before relying on it.

- [ ] **Step 4: Update DDL**

Add `is_deleted BIGINT NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，>0-已删除，删除时写入主键ID'` to every table.

Update unique keys:

- `db_user`: `uk_user_id(user_id, is_deleted)`, `uk_email(email, is_deleted)`
- `db_friend`: `uk_user_friend(user_id, friend_user_id, is_deleted)`
- `db_im_private_chat`: `uk_chat_id(chat_id, is_deleted)`, `uk_user_peer(user_id, peer_user_id, is_deleted)`
- `db_im_group`: `uk_group_id(group_id, is_deleted)`
- `db_im_group_member`: `uk_group_user(group_id, user_id, is_deleted)`
- `db_im_group_chat`: `uk_chat_id(chat_id, is_deleted)`, `uk_group_user(group_id, user_id, is_deleted)`

Keep inbox message unique keys unchanged unless tests prove a concrete need.

- [ ] **Step 5: Run full tests**

Run: `mvn -q test`
Expected: PASS.

- [ ] **Step 6: Commit**

Commit message style:

```text
添加DAO逻辑删除能力

1. 为基础实体添加逻辑删除字段
2. 调整DDL唯一键支持删除后重建
3. 补充逻辑删除基础测试
```

---

### Task 2: Friend Alias, Delete, Block, and Re-add

**Files:**
- Modify: `src/main/java/com/co/kc/imchat/domain/friend/Friend.java`
- Modify: `src/main/java/com/co/kc/imchat/domain/friend/FriendStatus.java`
- Modify: `src/main/java/com/co/kc/imchat/domain/friend/FriendRepository.java`
- Modify: `src/main/java/com/co/kc/imchat/infrastructure/domain/MysqlFriendRepository.java`
- Modify: `src/main/java/com/co/kc/imchat/application/FriendAppService.java`
- Modify: `src/main/java/com/co/kc/imchat/endpoint/http/FriendController.java`
- Create: `src/main/java/com/co/kc/imchat/model/cqrs/command/friend/FriendAliasChangeCmd.java`
- Create: `src/main/java/com/co/kc/imchat/model/io/friend/FriendAliasChangeRequest.java`
- Tests: `src/test/java/com/co/kc/imchat/domain/GroupDomainModelTest.java` or create `FriendDomainModelTest`; update `FriendAppServiceTest`

- [ ] **Step 1: Write failing domain tests**

Add tests for:

- `Friend.changeAlias(new FriendAlias("x"))` updates display name.
- `Friend.block()` prevents active relationship.
- `FriendStatus.DELETED` is no longer written by delete operations.

- [ ] **Step 2: Write failing app tests**

In `FriendAppServiceTest`, cover:

- changing alias changes only current user's friend row;
- deleting friend logically deletes only current user's friend row;
- block/unblock endpoints have app-service coverage;
- adding friend after logical delete creates/saves new friend rows and hidden private chats.

- [ ] **Step 3: Implement domain and repository changes**

Add `Friend.isNormal()`, `Friend.isBlocked()`, `Friend.changeAlias(FriendAlias alias)`.

Add repository methods:

```java
boolean isNormalFriend(UserId userId, UserId friendUserId);
void delete(UserId userId, UserId friendUserId);
```

Implement `delete` as logical delete of current user's row.

- [ ] **Step 4: Implement app/controller changes**

Add:

- `blockFriend`
- `unblockFriend`
- `deleteFriend`
- `changeFriendAlias`

Ensure delete no longer physically removes data.

- [ ] **Step 5: Run tests**

Run: `mvn -q -Dtest=FriendAppServiceTest,FriendDomainModelTest test`
Expected: PASS.

- [ ] **Step 6: Commit**

```text
完善好友关系管理

1. 添加好友备注修改能力
2. 优化好友拉黑与删除逻辑
3. 支持删除后重新添加好友
```

---

### Task 3: Enforce Private Message Relationship Rules

**Files:**
- Modify: `src/main/java/com/co/kc/imchat/application/PrivateMessageAppService.java`
- Modify: `src/main/java/com/co/kc/imchat/application/ChatAppService.java`
- Tests: `src/test/java/com/co/kc/imchat/application/PrivateChatAppServiceTest.java`

- [ ] **Step 1: Write failing tests**

Cover:

- sender cannot send if sender deleted receiver friend row;
- sender cannot send if receiver deleted sender friend row;
- sender cannot send if either side is blocked;
- historical messages still query by existing chat id if user owns that chat;
- opening private chat requires active bidirectional friend relation.

- [ ] **Step 2: Implement relationship guard**

Add a private helper in `PrivateMessageAppService`:

```java
private void requireMutualNormalFriends(UserId senderId, UserId receiverId) {
    if (!friendRepository.isNormalFriend(senderId, receiverId)) {
        throw new BusinessException("好友不存在");
    }
    if (!friendRepository.isNormalFriend(receiverId, senderId)) {
        throw new BusinessException("对方已不是你的好友");
    }
}
```

Inject `FriendRepository` into `PrivateMessageAppService` if not already present.

- [ ] **Step 3: Use guard in send/open**

Use it before private send and private chat open.

- [ ] **Step 4: Run tests**

Run: `mvn -q -Dtest=PrivateChatAppServiceTest,FriendAppServiceTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```text
完善私聊关系校验

1. 发送私聊前校验双方好友关系
2. 禁止删除或拉黑关系继续发送私聊
3. 保留历史消息查询能力
```

---

### Task 4: Group Kick, Leave, and Owner Transfer

**Files:**
- Modify: `src/main/java/com/co/kc/imchat/domain/group/Group.java`
- Modify: `src/main/java/com/co/kc/imchat/domain/group/GroupRoster.java`
- Modify: `src/main/java/com/co/kc/imchat/domain/group/GroupMemberRepository.java`
- Modify: `src/main/java/com/co/kc/imchat/infrastructure/domain/MysqlImGroupMemberRepository.java`
- Modify: `src/main/java/com/co/kc/imchat/domain/chat/ImGroupChatRepository.java`
- Modify: `src/main/java/com/co/kc/imchat/infrastructure/domain/MysqlImGroupChatRepository.java`
- Modify: `src/main/java/com/co/kc/imchat/application/GroupAppService.java`
- Modify: `src/main/java/com/co/kc/imchat/endpoint/http/GroupController.java`
- Create commands/requests for `GroupKickMember`, `GroupLeave`, `GroupTransferOwner`
- Tests: `GroupDomainModelTest`, `GroupDomainServiceTest`, `GroupAppServiceTest`

- [ ] **Step 1: Write failing domain tests**

Cover:

- owner can transfer ownership to active member;
- non-owner cannot transfer;
- owner cannot leave before transfer;
- owner cannot kick self;
- owner can kick normal member;
- normal member can leave.

- [ ] **Step 2: Write failing app tests**

Cover repository save/delete effects:

- kicked member's `GroupMember` and `ImGroupChat` are logically deleted;
- leaving member's records are logically deleted;
- group member count decreases;
- re-invite after kick creates new member/chat rows;
- rejoined user cannot query history with old deleted chat id.

- [ ] **Step 3: Implement group domain methods**

Add:

```java
void transferOwner(UserId operatorId, UserId newOwnerId)
void ensureOwner(UserId operatorId)
void decreaseMemberCount(int count)
```

- [ ] **Step 4: Implement repositories**

Add logical delete methods:

```java
void delete(GroupId groupId, UserId userId);
void deleteByGroupIdAndUserId(GroupId groupId, UserId userId);
```

- [ ] **Step 5: Implement app/controller endpoints**

Add endpoints:

- `POST /im/group/kickGroupMember`
- `POST /im/group/leaveGroup`
- `POST /im/group/transferGroupOwner`

- [ ] **Step 6: Run tests**

Run: `mvn -q -Dtest=GroupDomainModelTest,GroupDomainServiceTest,GroupAppServiceTest,GroupChatAppServiceTest test`
Expected: PASS.

- [ ] **Step 7: Commit**

```text
完善群成员生命周期

1. 添加群主转让能力
2. 添加踢出成员与主动退群能力
3. 支持退群后重新入群创建新会话
```

---

### Task 5: Group Settings and Aliases

**Files:**
- Modify: `src/main/java/com/co/kc/imchat/domain/group/Group.java`
- Modify: `src/main/java/com/co/kc/imchat/domain/group/GroupMember.java`
- Modify: `src/main/java/com/co/kc/imchat/domain/chat/ImGroupChat.java`
- Modify: `src/main/java/com/co/kc/imchat/application/GroupAppService.java`
- Modify: `src/main/java/com/co/kc/imchat/application/ChatAppService.java`
- Modify: `src/main/java/com/co/kc/imchat/endpoint/http/GroupController.java`
- Modify: `src/main/java/com/co/kc/imchat/endpoint/http/ChatController.java`
- Add commands/requests for group alias, group notification, member alias.
- Tests: `GroupAppServiceTest`, `GroupDomainModelTest`

- [ ] **Step 1: Write failing tests**

Cover:

- user can update their own group chat alias;
- group owner can update group notification;
- non-owner cannot update group notification;
- member can update their own group member alias;
- alias changes are visible in group list/detail display names.

- [ ] **Step 2: Implement domain methods**

Add:

```java
Group.changeNotification(UserId operatorId, GroupNotification notification)
GroupMember.changeUserAlias(GroupUserAlias alias)
ImGroupChat.changeGroupAlias(GroupAlias alias)
```

- [ ] **Step 3: Implement app/controller endpoints**

Add:

- `POST /im/chat/changeGroupAlias`
- `POST /im/group/changeGroupNotification`
- `POST /im/group/changeGroupMemberAlias`

- [ ] **Step 4: Run tests**

Run: `mvn -q -Dtest=GroupAppServiceTest,GroupDomainModelTest,ImChatAppTransformerTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```text
完善群聊设置能力

1. 添加群备注修改能力
2. 添加群公告修改能力
3. 添加群内昵称修改能力
```

---

### Task 6: Full Verification

- [ ] **Step 1: Run full test suite**

Run: `mvn -q test`
Expected: PASS.

- [ ] **Step 2: Check worktree**

Run: `git status --short`
Expected: only intentional files changed or clean after commits.

- [ ] **Step 3: Manual schema note**

Because there is no old database migration path in this project, ensure `src/main/resources/sql/ddl.sql` is enough for fresh database setup. Do not add migration scripts unless the user asks for old database support.
