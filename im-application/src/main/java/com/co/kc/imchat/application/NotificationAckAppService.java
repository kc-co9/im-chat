package com.co.kc.imchat.application;

import com.co.kc.imchat.application.model.cqrs.command.im.ImMessageAckCmd;
import com.co.kc.imchat.application.support.notifier.confirmable.ImMessageConfirmableService;
import com.co.kc.imchat.application.support.notifier.receiver.NotificationAckReceiver;
import com.co.kc.imchat.application.support.notifier.task.ReceiptType;
import com.co.kc.imchat.common.utils.FunctionUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class NotificationAckAppService {
    private final ImMessageConfirmableService imMessageConfirmableService;
    private final Map<ReceiptType, NotificationAckReceiver> notificationAckReceivers;

    public NotificationAckAppService(ImMessageConfirmableService imMessageConfirmableService,
                                     List<NotificationAckReceiver> notificationAckReceivers) {
        this.imMessageConfirmableService = imMessageConfirmableService;
        this.notificationAckReceivers = FunctionUtils.mappingMap(notificationAckReceivers, NotificationAckReceiver::receiptType, Function.identity());
    }

    public void confirmMessage(ImMessageAckCmd command) {
        NotificationAckReceiver receiver = notificationAckReceivers.get(command.receiptType());
        if (receiver != null) {
            receiver.receive(command);
        }
        imMessageConfirmableService.confirm(command.receiptType().receiptId(command.userId(), command.chatId(), command.messageId()));
    }
}
