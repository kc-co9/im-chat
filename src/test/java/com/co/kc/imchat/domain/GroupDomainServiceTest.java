package com.co.kc.imchat.domain;

import com.co.kc.imchat.domain.chat.ImChatName;
import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.group.ImGroup;
import com.co.kc.imchat.domain.group.ImGroupId;
import com.co.kc.imchat.domain.group.ImGroupMemberId;
import com.co.kc.imchat.domain.group.ImGroupName;
import com.co.kc.imchat.domain.group.ImGroupRoster;
import com.co.kc.imchat.domain.group.ImGroupService;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.group.ImGroupMember;
import com.co.kc.imchat.domain.group.ImUserGroupDescriptor;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
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

    @Test
    void emptyGroupMemberUserAliasMapsToUnsetAlias() {
        DbImGroupMember row = new DbImGroupMember();
        row.setGroupId(1001L);
        row.setUserId(1L);
        row.setUserAlias("");
        row.setJoinTime(java.time.LocalDateTime.now());

        ImGroupMember member = ImChatDomainTransformer.INSTANCE.imGroupMemberFrom(row);

        assertThat(member.getUserAlias()).isNull();
    }

    @Test
    void describeUserGroupsCombinesGroupsWithUserGroupChats() {
        RecordingGroupChatRepository groupChatRepository = new RecordingGroupChatRepository();
        ImGroupService service = new ImGroupService(null, groupChatRepository);
        ImGroup alpha = group(1001L, 1L, "alpha");
        ImGroup beta = group(1002L, 1L, "beta");
        ImGroup missingChat = group(1003L, 1L, "missing");
        ImGroupChat alphaChat = groupChat(101L, 1001L, 1L);
        ImGroupChat betaChat = groupChat(102L, 1002L, 1L);
        ImGroupChat otherUserAlphaChat = groupChat(201L, 1001L, 2L);
        groupChatRepository.groupChats.add(alphaChat);
        groupChatRepository.groupChats.add(betaChat);
        groupChatRepository.groupChats.add(otherUserAlphaChat);

        List<ImUserGroupDescriptor> descriptors = service.describeUserGroups(
                new UserId(1L),
                Arrays.asList(alpha, beta, missingChat));

        assertThat(descriptors)
                .extracting(descriptor -> descriptor.getId().getValue())
                .containsExactly(1001L, 1002L);
        assertThat(descriptors)
                .extracting(descriptor -> descriptor.getChat().getId().getValue())
                .containsExactly(101L, 102L);
        assertThat(descriptors)
                .extracting(descriptor -> descriptor.getName().getValue())
                .containsExactly("alpha", "beta");
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
                .id(new ImGroupMemberId(groupId, memberUserId))
                .groupId(groupId)
                .userId(memberUserId)
                .joinTime(java.time.LocalDateTime.now())
                .build();
    }

    private ImGroup group(Long groupId, Long ownerId, String name) {
        return ImGroup.builder()
                .id(new ImGroupId(groupId))
                .type(ImChatType.GROUP)
                .ownerId(new UserId(ownerId))
                .name(new ImGroupName(name))
                .build();
    }

    private ImGroupChat groupChat(Long chatId, Long groupId, Long userId) {
        return ImGroupChat.builder()
                .id(new ImChatId(chatId))
                .type(ImChatType.GROUP)
                .groupId(new ImGroupId(groupId))
                .userId(new UserId(userId))
                .unreadMessageCount(0)
                .build();
    }

    private static class RecordingGroupChatRepository implements com.co.kc.imchat.domain.chat.ImGroupChatRepository {
        private final List<ImGroupChat> groupChats = new java.util.ArrayList<>();

        @Override
        public ImGroupChat find(ImChatId chatId) {
            return null;
        }

        @Override
        public ImGroupChat find(ImGroupId groupId, UserId userId) {
            return null;
        }

        @Override
        public List<ImGroupChat> find(ImGroupId groupId) {
            return Collections.emptyList();
        }

        @Override
        public List<ImGroupChat> find(java.util.Collection<ImGroupId> groupIds) {
            return groupChats.stream()
                    .filter(chat -> groupIds.contains(chat.getGroupId()))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public List<ImGroupChat> find(UserId userId, java.util.Collection<ImGroupId> groupIds) {
            return groupChats.stream()
                    .filter(chat -> chat.getUserId().equals(userId))
                    .filter(chat -> groupIds.contains(chat.getGroupId()))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public List<ImGroupChat> find(UserId userId) {
            return groupChats.stream()
                    .filter(chat -> chat.getUserId().equals(userId))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public List<ImGroupChat> findByUserIdAndChatIds(UserId userId, List<ImChatId> chatIds) {
            return Collections.emptyList();
        }

        @Override
        public List<ImGroupChat> findByUserIdsAndGroupId(ImGroupId groupId, List<UserId> userIds) {
            return Collections.emptyList();
        }

        @Override
        public List<com.co.kc.imchat.domain.message.ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId viewer) {
            return Collections.emptyList();
        }

        @Override
        public void save(ImGroupChat groupChat) {
        }

        @Override
        public void saveAll(List<ImGroupChat> groupChats) {
        }

        @Override
        public boolean contain(ImChatId chatId, UserId userId) {
            return false;
        }
    }
}
