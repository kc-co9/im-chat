package com.co.kc.imchat.domain.message;

import com.google.common.annotations.VisibleForTesting;
import com.co.kc.imchat.common.support.state.DefaultStateMachine;
import com.co.kc.imchat.common.support.state.StateMachine;

/**
 * 消息读取状态
 */
public enum ImMessageStatus {
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


    private static final StateMachine<ImMessageStatus, ImMessageEvent> STATE_MACHINE = new ImMessageStatusMachine();

    public ImMessageStatus transition(ImMessageEvent event) {
        return STATE_MACHINE.transition(this, event);
    }

    @VisibleForTesting
    static class ImMessageStatusMachine extends DefaultStateMachine<ImMessageStatus, ImMessageEvent> {
        public ImMessageStatusMachine() {
            putTransition(ImMessageStatus.SENT, ImMessageEvent.READ, ImMessageStatus.READ);
            putTransition(ImMessageStatus.SENT, ImMessageEvent.RECEIVE, ImMessageStatus.RECEIVED);
            putTransition(ImMessageStatus.SENT, ImMessageEvent.REVOKE, ImMessageStatus.REVOKED);

            putTransition(ImMessageStatus.RECEIVED, ImMessageEvent.READ, ImMessageStatus.READ);
            putTransition(ImMessageStatus.RECEIVED, ImMessageEvent.REVOKE, ImMessageStatus.REVOKED);

            putTransition(ImMessageStatus.READ, ImMessageEvent.READ, ImMessageStatus.READ);
            putTransition(ImMessageStatus.READ, ImMessageEvent.REVOKE, ImMessageStatus.REVOKED);
        }
    }

}
