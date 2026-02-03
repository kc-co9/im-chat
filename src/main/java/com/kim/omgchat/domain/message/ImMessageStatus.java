package com.kim.omgchat.domain.message;

import com.google.common.annotations.VisibleForTesting;
import com.kim.omgchat.common.support.state.DefaultStateMachine;
import com.kim.omgchat.common.support.state.StateMachine;

/**
 * 消息读取状态
 */
public enum ImMessageStatus {
    /**
     * 已发送
     */
    SENT,

    /**
     * 已读
     */
    READ,

    /**
     * 已撤回
     */
    REVOKED;


    private static final StateMachine<ImMessageStatus, ImMessageEvent> STATE_MACHINE = new TradeOrderStatusMachine();

    public ImMessageStatus transition(ImMessageEvent event) {
        return STATE_MACHINE.transition(this, event);
    }

    @VisibleForTesting
    static class TradeOrderStatusMachine extends DefaultStateMachine<ImMessageStatus, ImMessageEvent> {
        public TradeOrderStatusMachine() {
            putTransition(ImMessageStatus.SENT, ImMessageEvent.READ, ImMessageStatus.READ);
            putTransition(ImMessageStatus.SENT, ImMessageEvent.REVOKE, ImMessageStatus.REVOKED);

            putTransition(ImMessageStatus.READ, ImMessageEvent.READ, ImMessageStatus.READ);
            putTransition(ImMessageStatus.READ, ImMessageEvent.REVOKE, ImMessageStatus.REVOKED);
        }
    }

}
