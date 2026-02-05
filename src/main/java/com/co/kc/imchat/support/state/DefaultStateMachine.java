package com.co.kc.imchat.support.state;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.co.kc.imchat.support.exception.TransitionException;

import java.util.Map;

public class DefaultStateMachine<S, E> implements StateMachine<S, E> {
    private final Map<String, S> stateTransitions = Maps.newHashMap();

    protected void putTransition(S source, E event, S target) {
        stateTransitions.put(Joiner.on("_").join(source, event), target);
    }

    @Override
    public S transition(S state, E event) {
        S target = stateTransitions.get(Joiner.on("_").join(state, event));
        if (target == null) {
            throw new TransitionException("state = " + state + " -> " + "event = " + event);
        }
        return target;
    }
}
