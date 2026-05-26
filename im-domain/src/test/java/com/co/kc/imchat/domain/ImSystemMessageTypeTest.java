package com.co.kc.imchat.domain;

import com.co.kc.imchat.domain.message.model.ImSystemMessageType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImSystemMessageTypeTest {

    @Test
    void groupCreatedBuildsTokenWithSystemPrefix() {
        assertThat(ImSystemMessageType.GROUP_CREATED.token(1001L).value())
                .isEqualTo("system:group_created:1001");
    }

    @Test
    void groupDismissedBuildsTokenWithSystemPrefix() {
        assertThat(ImSystemMessageType.GROUP_DISMISSED.token(1001L).value())
                .isEqualTo("system:group_dismissed:1001");
    }

    @Test
    void groupMemberJoinedBuildsTokenWithSystemPrefix() {
        assertThat(ImSystemMessageType.GROUP_MEMBER_JOINED.token(1001L).value())
                .isEqualTo("system:group_member_joined:1001");
    }

    @Test
    void systemMessageTypeProvidesDefaultContent() {
        assertThat(ImSystemMessageType.GROUP_CREATED.content()).isEqualTo("群聊已创建");
        assertThat(ImSystemMessageType.GROUP_DISMISSED.content()).isEqualTo("群聊已解散");
        assertThat(ImSystemMessageType.GROUP_MEMBER_JOINED.content()).isEqualTo("成员加入群聊");
    }

    @Test
    void groupMemberJoinedBuildsContentWithJoinedNames() {
        assertThat(ImSystemMessageType.GROUP_MEMBER_JOINED.joinedContent("alice、bob"))
                .isEqualTo("alice、bob 加入群聊");
        assertThat(ImSystemMessageType.GROUP_MEMBER_JOINED.joinedContent(""))
                .isEqualTo("成员加入群聊");
        assertThat(ImSystemMessageType.GROUP_MEMBER_JOINED.joinedContent(null))
                .isEqualTo("成员加入群聊");
    }
}
