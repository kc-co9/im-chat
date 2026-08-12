package com.co.kc.imchat.service.message.interfaces.rpc;

import com.co.kc.imchat.service.message.application.GroupMessageAppService;
import com.co.kc.imchat.service.message.application.NotificationAckAppService;
import com.co.kc.imchat.service.message.application.PrivateMessageAppService;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageType;
import com.co.kc.imchat.service.message.facade.params.PrivateMessageSendParams;
import com.co.kc.imchat.service.message.model.cqrs.command.im.ImPrivateMessageSendCmd;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MessageRpcServiceTest {

    @Test
    void delegatesPrivateSendCommand() {
        PrivateMessageAppService privateMessageAppService = mock(PrivateMessageAppService.class);
        GroupMessageAppService groupMessageAppService = mock(GroupMessageAppService.class);
        NotificationAckAppService notificationAckAppService = mock(NotificationAckAppService.class);
        MessageRpcService facade = new MessageRpcService(
                privateMessageAppService,
                groupMessageAppService,
                notificationAckAppService);
        PrivateMessageSendParams params = new PrivateMessageSendParams(1L, 10L, "token", "TEXT", "hello");

        facade.sendPrivateMessage(params);

        verify(privateMessageAppService).sendMessage(new ImPrivateMessageSendCmd(
                1L,
                10L,
                "token",
                ImMessageType.TEXT,
                "hello"));
    }
}
