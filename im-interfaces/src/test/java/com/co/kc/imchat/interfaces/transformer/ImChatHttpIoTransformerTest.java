package com.co.kc.imchat.interfaces.transformer;

import com.co.kc.imchat.application.model.cqrs.dto.group.GroupChatOpenDTO;
import com.co.kc.imchat.application.model.cqrs.dto.group.GroupCreateDTO;
import com.co.kc.imchat.application.model.cqrs.dto.im.ImChatItemDTO;
import com.co.kc.imchat.application.model.cqrs.dto.im.ImPrivateChatOpenDTO;
import com.co.kc.imchat.domain.message.model.ImMessageTypeEnum;
import com.co.kc.imchat.interfaces.transformer.ImChatHttpIoTransformer;
import com.co.kc.imchat.interfaces.model.io.chat.ImChatListResponse;
import com.co.kc.imchat.interfaces.model.io.group.GroupChatOpenResponse;
import com.co.kc.imchat.interfaces.model.io.group.GroupCreateResponse;
import com.co.kc.imchat.interfaces.model.io.chat.ImPrivateChatOpenResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

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

    @Test
    void groupCreateResponseContainsOnlyGroupId() {
        GroupCreateResponse response = ImChatHttpIoTransformer.INSTANCE
                .groupCreateResponseFrom(new GroupCreateDTO(1001L));

        assertThat(response.getGroupId()).isEqualTo(1001L);
    }

    @Test
    void chatItemResponseContainsLastMessageAndSendTime() {
        LocalDateTime sendTime = LocalDateTime.of(2026, 5, 15, 18, 30);
        ImChatItemDTO dto = new ImChatItemDTO();
        dto.setChatId(101L);
        dto.setLastMessageType(ImMessageTypeEnum.TEXT);
        dto.setLastMessageContent("hello");
        dto.setLastMessageTime(sendTime);

        ImChatListResponse.ImChatItem response = ImChatHttpIoTransformer.INSTANCE.imChatItemFrom(dto);

        assertThat(response.getLastMessageType()).isEqualTo(ImMessageTypeEnum.TEXT);
        assertThat(response.getLastMessageContent()).isEqualTo("hello");
        assertThat(response.getLastMessageTime()).isEqualTo(sendTime);
    }
}
