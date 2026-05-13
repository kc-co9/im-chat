package com.co.kc.imchat.domain;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatName;
import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.chat.ImGroup;
import com.co.kc.imchat.domain.chat.ImGroupId;
import com.co.kc.imchat.domain.chat.ImGroupName;
import com.co.kc.imchat.domain.chat.ImGroupRoster;
import com.co.kc.imchat.domain.chat.ImGroupService;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupMember;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.transformer.domain.ImChatDomainTransformer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupDomainServiceTest {

    @Test
    void createRosterCreatesMembersWithOwnerIncluded() {
        ImGroupService service = new ImGroupService(null, null);
        ImGroupId groupId = new ImGroupId(1001L);

        ImGroupRoster roster = service.createRoster(
                groupId,
                new UserId(1L),
                Arrays.asList(new UserId(2L), new UserId(1L)));

        assertThat(roster.getMembers())
                .extracting(member -> member.getUserId().getValue())
                .containsExactly(1L, 2L);
        assertThat(roster.getMembers())
                .allSatisfy(member -> assertThat(member.getJoinTime()).isNotNull());
    }

    @Test
    void createRosterAllowsEmptyMemberIdsAndOnlyKeepsOwner() {
        ImGroupService service = new ImGroupService(null, null);
        ImGroupId groupId = new ImGroupId(1001L);

        ImGroupRoster roster = service.createRoster(groupId, new UserId(1L), null);

        assertThat(roster.getMembers())
                .extracting(member -> member.getUserId().getValue())
                .containsExactly(1L);
    }

    @Test
    void groupRosterInvitesOnlyNewMembers() {
        ImGroupId groupId = new ImGroupId(1001L);
        ImGroupRoster roster = new ImGroupRoster(
                groupId,
                Collections.singletonList(groupMember(groupId, 1L)));

        List<ImGroupMember> members = roster.invite(new UserId(1L), Arrays.asList(new UserId(1L), new UserId(2L), new UserId(2L)));

        assertThat(members)
                .extracting(member -> member.getUserId().getValue())
                .containsExactly(2L);
    }

    @Test
    void groupRosterRejectsInviterOutsideGroup() {
        ImGroupId groupId = new ImGroupId(1001L);
        ImGroupRoster roster = new ImGroupRoster(
                groupId,
                Collections.singletonList(groupMember(groupId, 1L)));

        assertThatThrownBy(() -> roster.invite(new UserId(2L), Collections.singletonList(new UserId(3L))))
                .isInstanceOf(RuntimeException.class);
    }


    @Test
    void emptyGroupAliasUsesGroupNameAsChatName() {
        DbImGroupChat row = new DbImGroupChat();
        row.setChatId(101L);
        row.setGroupId(1001L);
        row.setUserId(1L);
        row.setGroupAlias("");
        row.setLastMessageId(0L);
        row.setReadMessageId(0L);
        row.setUnreadMessageCount(0);
        ImGroupChat chat = ImChatDomainTransformer.INSTANCE.imGroupChatFrom(row);
        ImGroup group = ImGroup.builder()
                .id(new ImGroupId(1001L))
                .type(ImChatType.GROUP)
                .ownerId(new UserId(1L))
                .name(new ImGroupName("group"))
                .build();
        ImChatService service = new ImChatService(null, null, null, null, null, null);

        ImChatName chatName = service.obtainGroupChatName(group, chat.getGroupAlias());

        assertThat(chatName.getValue()).isEqualTo("group");
    }

    private static class FixedSnowflakeId extends SnowflakeId {
        private long next;

        FixedSnowflakeId(long next) {
            super(new TestMachineId());
            this.next = next;
        }

        @Override
        public synchronized Long next() {
            return next++;
        }
    }

    private static class TestMachineId implements com.co.kc.imchat.support.identity.snowflake.ISnowflakeMachineId {
        @Override
        public long getDataCenterId() {
            return 1L;
        }

        @Override
        public long getMachineId() {
            return 1L;
        }
    }

    private ImGroupMember groupMember(ImGroupId groupId, Long userId) {
        UserId memberUserId = new UserId(userId);
        return ImGroupMember.builder()
                .id(new com.co.kc.imchat.domain.chat.ImGroupMemberId(groupId, memberUserId))
                .groupId(groupId)
                .userId(memberUserId)
                .joinTime(java.time.LocalDateTime.now())
                .build();
    }
}
