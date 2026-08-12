package com.co.kc.imchat.service.message.application.notification.receipt;

import com.co.kc.imchat.service.message.application.PrivateMessageAppService;
import com.co.kc.imchat.service.message.model.cqrs.command.im.ImMessageAckCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.im.ImPrivateMessageReceiveCmd;
import com.co.kc.imchat.service.message.application.notification.receiver.PrivateMessageSendAckReceiver;
import com.co.kc.imchat.service.message.application.notification.task.ReceiptType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PrivateMessageSendAckReceiverTest {

    @Test
    void receiveMapsAckToPrivateReceiveCommandByCommandFieldNames() {
        PrivateMessageAppService appService = mock(PrivateMessageAppService.class);
        PrivateMessageSendAckReceiver receiver = new PrivateMessageSendAckReceiver(appService);

        receiver.receive(new ImMessageAckCmd(2L, 102L, 900L, ReceiptType.PRIVATE_MESSAGE_SEND));

        ArgumentCaptor<ImPrivateMessageReceiveCmd> commandCaptor =
                ArgumentCaptor.forClass(ImPrivateMessageReceiveCmd.class);
        verify(appService).receiveMessage(commandCaptor.capture());
        ImPrivateMessageReceiveCmd command = commandCaptor.getValue();
        assertThat(command.chatId()).isEqualTo(102L);
        assertThat(command.userId()).isEqualTo(2L);
        assertThat(command.messageId()).isEqualTo(900L);
    }
}
