package com.co.kc.imchat.service.message.domain.message.model;

import com.co.kc.imchat.service.message.domain.message.event.ImMessageEvent;
import com.google.common.annotations.VisibleForTesting;
import com.co.kc.imchat.common.state.DefaultStateMachine;
import com.co.kc.imchat.common.state.StateMachine;

/**
 * 消息读取状态
 */
public enum ImPrivateMessageStatus {
    /**
     * 已发送
     */
    SENT,

    /**
     * 已接收
     */
    RECEIVED,

    /**
     * 已读
     */
    READ,

    /**
     * 已撤回
     */
    REVOKED;


    private static final StateMachine<ImPrivateMessageStatus, ImMessageEvent> STATE_MACHINE = new ImMessageStatusMachine();

    public ImPrivateMessageStatus transition(ImMessageEvent event) {
        return STATE_MACHINE.transition(this, event);
    }

    @VisibleForTesting
    static class ImMessageStatusMachine extends DefaultStateMachine<ImPrivateMessageStatus, ImMessageEvent> {
        public ImMessageStatusMachine() {
            putTransition(ImPrivateMessageStatus.SENT, ImMessageEvent.READ, ImPrivateMessageStatus.READ);
            putTransition(ImPrivateMessageStatus.SENT, ImMessageEvent.RECEIVE, ImPrivateMessageStatus.RECEIVED);
            putTransition(ImPrivateMessageStatus.SENT, ImMessageEvent.REVOKE, ImPrivateMessageStatus.REVOKED);

            putTransition(ImPrivateMessageStatus.RECEIVED, ImMessageEvent.RECEIVE, ImPrivateMessageStatus.RECEIVED);
            putTransition(ImPrivateMessageStatus.RECEIVED, ImMessageEvent.READ, ImPrivateMessageStatus.READ);
            putTransition(ImPrivateMessageStatus.RECEIVED, ImMessageEvent.REVOKE, ImPrivateMessageStatus.REVOKED);

            putTransition(ImPrivateMessageStatus.READ, ImMessageEvent.RECEIVE, ImPrivateMessageStatus.READ);
            putTransition(ImPrivateMessageStatus.READ, ImMessageEvent.READ, ImPrivateMessageStatus.READ);
            putTransition(ImPrivateMessageStatus.READ, ImMessageEvent.REVOKE, ImPrivateMessageStatus.REVOKED);
        }
    }

}
