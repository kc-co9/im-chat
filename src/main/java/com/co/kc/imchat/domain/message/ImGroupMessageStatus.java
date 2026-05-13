package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.support.state.DefaultStateMachine;
import com.co.kc.imchat.support.state.StateMachine;
import com.google.common.annotations.VisibleForTesting;

/**
 * 消息读取状态
 */
public enum ImGroupMessageStatus {
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


    private static final StateMachine<ImGroupMessageStatus, ImMessageEvent> STATE_MACHINE = new ImMessageStatusMachine();

    public ImGroupMessageStatus transition(ImMessageEvent event) {
        return STATE_MACHINE.transition(this, event);
    }

    @VisibleForTesting
    static class ImMessageStatusMachine extends DefaultStateMachine<ImGroupMessageStatus, ImMessageEvent> {
        public ImMessageStatusMachine() {
            putTransition(ImGroupMessageStatus.SENT, ImMessageEvent.REVOKE, ImGroupMessageStatus.REVOKED);
            putTransition(ImGroupMessageStatus.SENT, ImMessageEvent.RECEIVE, ImGroupMessageStatus.RECEIVED);
            putTransition(ImGroupMessageStatus.SENT, ImMessageEvent.READ, ImGroupMessageStatus.READ);

            putTransition(ImGroupMessageStatus.RECEIVED, ImMessageEvent.READ, ImGroupMessageStatus.READ);
            putTransition(ImGroupMessageStatus.RECEIVED, ImMessageEvent.REVOKE, ImGroupMessageStatus.REVOKED);

            putTransition(ImGroupMessageStatus.READ, ImMessageEvent.READ, ImGroupMessageStatus.READ);
            putTransition(ImGroupMessageStatus.READ, ImMessageEvent.REVOKE, ImGroupMessageStatus.REVOKED);
        }
    }

}
