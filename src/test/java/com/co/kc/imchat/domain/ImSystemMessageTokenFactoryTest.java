package com.co.kc.imchat.domain;

import com.co.kc.imchat.domain.group.GroupId;
import com.co.kc.imchat.domain.message.ImMessageToken;
import com.co.kc.imchat.domain.message.ImSystemMessageTokenFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImSystemMessageTokenFactoryTest {

    @Test
    void groupCreatedBuildsStableTokenFromCreateSystemGroupId() {
        ImMessageToken token = ImSystemMessageTokenFactory.createSystemGroupCreated(new GroupId(1001L));

        assertThat(token.getValue()).isEqualTo("system:group_created:1001");
    }

    @Test
    void groupDismissedBuildsStableTokenFromGroupId() {
        ImMessageToken token = ImSystemMessageTokenFactory.createSystemGroupDismissed(new GroupId(1001L));

        assertThat(token.getValue()).isEqualTo("system:group_dismissed:1001");
    }
}
