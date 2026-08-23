package com.co.kc.imchat.service.message.application;

import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.message.adapter.social.SocialAdapter;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatType;
import com.co.kc.imchat.service.message.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.service.message.domain.message.repository.ImGroupInboxMessageRepository;
import com.co.kc.imchat.service.message.domain.message.service.ImMessageService;
import com.co.kc.imchat.service.message.model.cqrs.command.chat.ImGroupChatOpenCmd;
import com.co.kc.imchat.service.message.domain.chat.repository.ImGroupChatRepository;
import com.co.kc.imchat.service.message.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.service.message.domain.chat.repository.ImPrivateChatRepository;
import com.co.kc.imchat.service.message.domain.chat.service.ImChatService;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatView;
import com.co.kc.imchat.service.message.domain.chat.repository.ImChatViewRepository;
import com.co.kc.imchat.service.message.domain.chat.repository.ImChatViewRepository;
import com.co.kc.imchat.service.message.model.cqrs.command.chat.ChatExitCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.chat.ImPrivateChatOpenCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.chat.PrivateChatHideCmd;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ImChatViewApplicationTest {
    @Test
    void openingPrivateChatEntersPresenceAndExitClearsIt() {
        InMemoryPresenceRepository presenceRepository = new InMemoryPresenceRepository();
        ImPrivateChatRepository chatRepository = mock(ImPrivateChatRepository.class);
        SocialAdapter socialAdapter = mock(SocialAdapter.class);
        ImPrivateChat chat = ImPrivateChat.builder()
                .id(new ImChatId(101L))
                .userId(new UserId(1L))
                .peerUserId(new UserId(2L))
                .type(ImChatType.PRIVATE)
                .build();
        when(chatRepository.find(new UserId(1L), new UserId(2L))).thenReturn(Optional.of(chat));
        when(chatRepository.find(new ImChatId(101L)))
                .thenReturn(Optional.of(chat));

        ChatAppService service = new ChatAppService(
                chatRepository,
                null,
                null,
                new ImChatService(chatRepository, null, null),
                socialAdapter,
                null,
                null,
                presenceRepository);

        service.openPrivateChat(new ImPrivateChatOpenCmd(1L, 2L));
        assertThat(presenceRepository.presence)
                .isEqualTo(new ImChatView(new UserId(1L), new ImChatId(101L)));

        service.exitChat(new ChatExitCmd(1L));
        assertThat(presenceRepository.presence).isNull();

        service.openPrivateChat(new ImPrivateChatOpenCmd(1L, 2L));
        service.hidePrivateChat(new PrivateChatHideCmd(1L, 101L));
        assertThat(chat.getStatus()).isEqualTo(
                com.co.kc.imchat.service.message.domain.chat.model.ImChatStatus.HIDDEN);
        assertThat(presenceRepository.presence).isNull();
    }

    @Test
    void openingGroupChatEntersPresence() {
        InMemoryPresenceRepository presenceRepository = new InMemoryPresenceRepository();
        ImGroupChatRepository chatRepository = mock(ImGroupChatRepository.class);
        ImGroupInboxMessageRepository inboxRepository = mock(ImGroupInboxMessageRepository.class);
        ImMessageService messageService = mock(ImMessageService.class);
        SocialAdapter socialAdapter = mock(SocialAdapter.class);
        ImGroupChat chat = ImGroupChat.builder()
                .id(new ImChatId(201L))
                .groupId(new GroupId(1001L))
                .userId(new UserId(1L))
                .type(ImChatType.GROUP)
                .unreadMessageCount(0)
                .build();
        when(chatRepository.find(new ImChatId(201L)))
                .thenReturn(Optional.of(chat));
        when(messageService.readUnreadGroupMessages(chat, new UserId(1L))).thenReturn(java.util.List.of());

        ChatAppService service = new ChatAppService(
                null,
                chatRepository,
                inboxRepository,
                new ImChatService(null, chatRepository, null),
                socialAdapter,
                messageService,
                null,
                presenceRepository);

        service.openGroupChat(new ImGroupChatOpenCmd(1L, 201L));

        assertThat(presenceRepository.presence)
                .isEqualTo(new ImChatView(new UserId(1L), new ImChatId(201L)));
    }

    private static final class InMemoryPresenceRepository implements ImChatViewRepository {
        private ImChatView presence;

        @Override
        public void save(ImChatView presence) {
            this.presence = presence;
        }

        @Override
        public void clear(UserId userId) {
            presence = null;
        }

        @Override
        public Optional<ImChatView> find(UserId userId) {
            return Optional.ofNullable(presence);
        }
    }
}
