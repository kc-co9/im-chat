# Group Member and Chat Boundary Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separate group membership fields from per-user group chat state according to the approved boundary spec.

**Architecture:** Keep `ImGroup.ownerId` as the current group owner. Move group member display data to `ImGroupMember`/`db_im_group_member`, and keep only per-user conversation state in `ImGroupChat`/`db_im_group_chat`. Group creation still creates group, member, and chat rows synchronously.

**Tech Stack:** Java 8, Spring Boot 2.7, MyBatis-Plus, MapStruct, JUnit 5, AssertJ.

---

## File Structure

- Modify `src/main/java/com/co/kc/imchat/domain/chat/ImGroupChat.java`: remove `userAlias`.
- Modify `src/main/java/com/co/kc/imchat/domain/chat/ImGroupMember.java`: add `joinTime`.
- Modify `src/main/java/com/co/kc/imchat/infrastructure/mybatis/entity/DbImGroupChat.java`: remove `userAlias`.
- Modify `src/main/java/com/co/kc/imchat/infrastructure/mybatis/entity/DbImGroupMember.java`: add `joinTime`.
- Modify `src/main/java/com/co/kc/imchat/transformer/db/ImChatDbTransformer.java`: remove group-chat user alias mapping and add member join time mapping.
- Modify `src/main/java/com/co/kc/imchat/transformer/domain/ImChatDomainTransformer.java`: remove group-chat user alias mapping and add member join time mapping.
- Modify `src/main/resources/sql/ddl.sql`: remove `db_im_group_chat.user_alias`; add `db_im_group_member.join_time`.
- Modify `src/test/java/com/co/kc/imchat/application/GroupChatAppServiceTest.java`: assert group members get join time and group chats do not expose user alias.
- Run full verification with `mvn clean test`.

## Task 1: Add Test Coverage for the Boundary

**Files:**
- Modify: `src/test/java/com/co/kc/imchat/application/GroupChatAppServiceTest.java`

- [ ] **Step 1: Write failing assertions**

In `createGroupChatPersistsGroupMembersSeparatelyFromGroupChats`, assert:

```java
assertThat(groupMemberRepository.savedMembers)
        .allSatisfy(member -> assertThat(member.getJoinTime()).isNotNull());
```

Also assert group chat no longer has member alias once the production field is removed by relying on compilation: no test should call `getUserAlias()` on `ImGroupChat`.

- [ ] **Step 2: Run focused test**

Run:

```bash
mvn -q -Dtest=GroupChatAppServiceTest test
```

Expected: compilation fails because `ImGroupMember#getJoinTime()` does not exist yet.

## Task 2: Move Fields to the Correct Domain Models

**Files:**
- Modify: `src/main/java/com/co/kc/imchat/domain/chat/ImGroupChat.java`
- Modify: `src/main/java/com/co/kc/imchat/domain/chat/ImGroupMember.java`
- Modify: `src/main/java/com/co/kc/imchat/application/ChatAppService.java`

- [ ] **Step 1: Remove group chat user alias**

Delete this field and builder method from `ImGroupChat`:

```java
private ImGroupUserAlias userAlias;
public Builder userAlias(ImGroupUserAlias userAlias) { ... }
```

- [ ] **Step 2: Add member join time**

Add to `ImGroupMember`:

```java
private LocalDateTime joinTime;
```

Add builder method:

```java
public Builder joinTime(LocalDateTime joinTime) {
    member.setJoinTime(joinTime);
    return this;
}
```

Update validation to require `joinTime`.

- [ ] **Step 3: Set join time during group creation**

In `ChatAppService#createGroupChat`, create one `LocalDateTime now = LocalDateTime.now()` before building members and pass:

```java
.joinTime(now)
```

- [ ] **Step 4: Run focused test**

Run:

```bash
mvn -q -Dtest=GroupChatAppServiceTest test
```

Expected: test may still fail at DB/entity transformer compilation until Task 3 is complete.

## Task 3: Update DB Entities, Transformers, and DDL

**Files:**
- Modify: `src/main/java/com/co/kc/imchat/infrastructure/mybatis/entity/DbImGroupChat.java`
- Modify: `src/main/java/com/co/kc/imchat/infrastructure/mybatis/entity/DbImGroupMember.java`
- Modify: `src/main/java/com/co/kc/imchat/transformer/db/ImChatDbTransformer.java`
- Modify: `src/main/java/com/co/kc/imchat/transformer/domain/ImChatDomainTransformer.java`
- Modify: `src/main/resources/sql/ddl.sql`

- [ ] **Step 1: Update entities**

Remove `userAlias` from `DbImGroupChat`.

Add to `DbImGroupMember`:

```java
private LocalDateTime joinTime;
```

- [ ] **Step 2: Update DB transformer**

In `dbImGroupChatFrom`, remove:

```java
dbGroupChat.setUserAlias(...);
```

In `dbImGroupMemberFrom`, add:

```java
dbGroupMember.setJoinTime(member.getJoinTime());
```

- [ ] **Step 3: Update domain transformer**

In `imGroupChatFrom`, remove:

```java
.userAlias(new ImGroupUserAlias(dbImGroupChat.getUserAlias()))
```

In `imGroupMemberFrom`, add:

```java
.joinTime(dbImGroupMember.getJoinTime())
```

- [ ] **Step 4: Update DDL**

Remove from `db_im_group_chat`:

```sql
`user_alias` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '用户群昵称',
```

Add to `db_im_group_member`:

```sql
`join_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入群时间',
```

- [ ] **Step 5: Run compile**

Run:

```bash
mvn -q -DskipTests compile
```

Expected: compile passes.

## Task 4: Verify Behavior

**Files:**
- Test: full project

- [ ] **Step 1: Run focused tests**

Run:

```bash
mvn -q -Dtest=GroupChatAppServiceTest test
```

Expected: `GroupChatAppServiceTest` passes.

- [ ] **Step 2: Run full tests**

Run:

```bash
mvn clean test
```

Expected: build succeeds with all tests passing.

- [ ] **Step 3: Scan for stale references**

Run:

```bash
rg "DbImGroupChat::getUserAlias|getUserAlias\\(|setUserAlias\\(|user_alias" src/main/java src/main/resources/sql/ddl.sql
```

Expected: no `user_alias` references for `DbImGroupChat` or `db_im_group_chat`; only `ImGroupMember`/`DbImGroupMember` member alias references may remain.
