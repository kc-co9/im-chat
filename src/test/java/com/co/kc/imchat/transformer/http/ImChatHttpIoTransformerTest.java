package com.co.kc.imchat.transformer.http;

import com.co.kc.imchat.model.cqrs.dto.group.GroupChatOpenDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImPrivateChatOpenDTO;
import com.co.kc.imchat.model.io.group.GroupChatOpenResponse;
import com.co.kc.imchat.model.io.chat.ImPrivateChatOpenResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImChatHttpIoTransformerTest {

    @Test
    void privateChatOpenResponseContainsPeerUserId() {
        ImPrivateChatOpenResponse response = ImChatHttpIoTransformer.INSTANCE
                .imPrivateChatOpenResponseFrom(new ImPrivateChatOpenDTO(101L, 2L));

        assertThat(response.getChatId()).isEqualTo(101L);
        assertThat(response.getPeerUserId()).isEqualTo(2L);
    }

    @Test
    void groupChatOpenResponseContainsGroupId() {
        GroupChatOpenResponse response = ImChatHttpIoTransformer.INSTANCE
                .groupChatOpenResponseFrom(new GroupChatOpenDTO(102L, 1001L));

        assertThat(response.getChatId()).isEqualTo(102L);
        assertThat(response.getGroupId()).isEqualTo(1001L);
    }
}
