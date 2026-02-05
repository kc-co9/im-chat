package com.co.kc.imchat.support.state;

/**
 * 状态机接口
 *
 * @param <S> 源状态
 * @param <E> 变更事件
 */
public interface StateMachine<S, E> {
    /**
     * 状态转换
     *
     * @param state 源状态
     * @param event 状态变更事件
     * @return 目标状态
     */
    S transition(S state, E event);
}
