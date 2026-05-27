package com.co.kc.imchat.application;

import com.co.kc.imchat.application.model.cqrs.command.im.ImMessageAckCmd;
import com.co.kc.imchat.application.support.notifier.confirmable.ImMessageConfirmableService;
import com.co.kc.imchat.application.support.notifier.confirmable.ImMessageConfirmableStore;
import com.co.kc.imchat.application.support.notifier.receiver.NotificationAckReceiver;
import com.co.kc.imchat.application.support.notifier.task.ReceiptTask;
import com.co.kc.imchat.application.support.notifier.task.ReceiptType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationAckAppServiceTest {

    @Test
    void ackConfirmsNotificationTaskByUserChatAndMessage() {
        RecordingConfirmableStore confirmableStore = new RecordingConfirmableStore();
        NotificationAckAppService appService = new NotificationAckAppService(
                newConfirmableService(confirmableStore),
                Collections.singletonList(new RecordingAckReceiver(ReceiptType.PRIVATE_MESSAGE_REVOKE)));

        appService.confirmMessage(new ImMessageAckCmd(2L, 102L, 900L, ReceiptType.PRIVATE_MESSAGE_REVOKE));

        assertThat(confirmableStore.confirmedReceiptIds)
                .containsExactly("PRIVATE_MESSAGE_REVOKE:2:102:900");
    }

    @Test
    void confirmMessageRunsStrategyAndConfirmsSendNotificationTask() {
        RecordingConfirmableStore confirmableStore = new RecordingConfirmableStore();
        RecordingAckReceiver handler = new RecordingAckReceiver(ReceiptType.PRIVATE_MESSAGE_SEND);
        NotificationAckAppService appService = new NotificationAckAppService(
                newConfirmableService(confirmableStore), Collections.singletonList(handler));

        appService.confirmMessage(new ImMessageAckCmd(2L, 102L, 900L, ReceiptType.PRIVATE_MESSAGE_SEND));

        assertThat(handler.handledCommands)
                .extracting(ImMessageAckCmd::receiptType)
                .containsExactly(ReceiptType.PRIVATE_MESSAGE_SEND);
        assertThat(confirmableStore.confirmedReceiptIds)
                .containsExactly("PRIVATE_MESSAGE_SEND:2:102:900");
    }

    @Test
    void confirmMessageConfirmsNotificationTaskWhenReceiverIsMissing() {
        RecordingConfirmableStore confirmableStore = new RecordingConfirmableStore();
        NotificationAckAppService appService =
                new NotificationAckAppService(newConfirmableService(confirmableStore), Collections.emptyList());

        appService.confirmMessage(new ImMessageAckCmd(2L, 102L, 900L, ReceiptType.PRIVATE_MESSAGE_REVOKE));

        assertThat(confirmableStore.confirmedReceiptIds)
                .containsExactly("PRIVATE_MESSAGE_REVOKE:2:102:900");
    }

    @Test
    void ackRejectsMissingReceiptType() {
        RecordingConfirmableStore confirmableStore = new RecordingConfirmableStore();
        NotificationAckAppService appService =
                new NotificationAckAppService(newConfirmableService(confirmableStore), Collections.emptyList());

        assertThatThrownBy(() -> appService.confirmMessage(new ImMessageAckCmd(2L, 102L, 900L, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("回执类型不能为空");

        assertThat(confirmableStore.confirmedReceiptIds).isEmpty();
    }

    @Test
    void ackRejectsMissingChatId() {
        RecordingConfirmableStore confirmableStore = new RecordingConfirmableStore();
        NotificationAckAppService appService =
                new NotificationAckAppService(newConfirmableService(confirmableStore), Collections.emptyList());

        assertThatThrownBy(() -> appService.confirmMessage(
                new ImMessageAckCmd(2L, null, 900L, ReceiptType.PRIVATE_MESSAGE_REVOKE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("聊天ID不能为空");

        assertThat(confirmableStore.confirmedReceiptIds).isEmpty();
    }

    @Test
    void ackRejectsMissingUserId() {
        RecordingConfirmableStore confirmableStore = new RecordingConfirmableStore();
        NotificationAckAppService appService =
                new NotificationAckAppService(newConfirmableService(confirmableStore), Collections.emptyList());

        assertThatThrownBy(() -> appService.confirmMessage(
                new ImMessageAckCmd(null, 102L, 900L, ReceiptType.PRIVATE_MESSAGE_REVOKE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户ID不能为空");

        assertThat(confirmableStore.confirmedReceiptIds).isEmpty();
    }

    private ImMessageConfirmableService newConfirmableService(RecordingConfirmableStore confirmableStore) {
        return new ImMessageConfirmableService(null, confirmableStore);
    }

    private static class RecordingConfirmableStore implements ImMessageConfirmableStore {
        private final List<String> confirmedReceiptIds = new ArrayList<>();

        @Override
        public void startConfirming(Consumer<ReceiptTask> consumer) {
        }

        @Override
        public void stopConfirming() {
        }

        @Override
        public void offer(ReceiptTask message) {
        }

        @Override
        public void confirm(String receiptId) {
            confirmedReceiptIds.add(receiptId);
        }
    }

    private static class RecordingAckReceiver implements NotificationAckReceiver {
        private final ReceiptType receiptType;
        private final List<ImMessageAckCmd> handledCommands = new ArrayList<>();

        private RecordingAckReceiver(ReceiptType receiptType) {
            this.receiptType = receiptType;
        }

        @Override
        public ReceiptType receiptType() {
            return receiptType;
        }

        @Override
        public void receive(ImMessageAckCmd command) {
            handledCommands.add(command);
        }
    }
}
