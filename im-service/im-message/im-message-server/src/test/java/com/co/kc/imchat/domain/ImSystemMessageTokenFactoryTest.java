package com.co.kc.imchat.common.domain;

import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageToken;
import com.co.kc.imchat.service.message.domain.message.factory.ImSystemMessageTokenFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImSystemMessageTokenFactoryTest {

    @Test
    void groupCreatedBuildsStableTokenFromCreateSystemGroupId() {
        ImMessageToken token = ImSystemMessageTokenFactory.createSystemGroupCreated(new GroupId(1001L));

        assertThat(token.value()).isEqualTo("system:group_created:1001");
    }

    @Test
    void groupDismissedBuildsStableTokenFromGroupId() {
        ImMessageToken token = ImSystemMessageTokenFactory.createSystemGroupDismissed(new GroupId(1001L));

        assertThat(token.value()).isEqualTo("system:group_dismissed:1001");
    }

    @Test
    void groupMemberJoinedBuildsUniqueTokenFromGroupIdAndMessageId() {
        ImMessageToken token = ImSystemMessageTokenFactory.createSystemGroupMemberJoined(
                new GroupId(1001L), new ImMessageId(9001L));

        assertThat(token.value()).isEqualTo("system:group_member_joined:1001:9001");
    }
}
