package com.co.kc.imchat.plugin.mq.core;

import com.co.kc.imchat.plugin.mq.model.MqMessage;
import com.co.kc.imchat.plugin.mq.spi.MessagePublisher;
import com.co.kc.imchat.plugin.mq.spi.MessageSubscriber;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class InMemoryMessageBus implements MessagePublisher, MessageSubscriber {
    private final Map<String, List<HandlerRegistration<?>>> handlers = new ConcurrentHashMap<>();

    @Override
    public void publish(MqMessage<?> message) {
        handlers.getOrDefault(message.topic(), List.of())
                .forEach(registration -> registration.tryHandle(message.payload()));
    }

    @Override
    public <T> void subscribe(String topic, Class<T> payloadType, Consumer<T> handler) {
        handlers.computeIfAbsent(topic, ignored -> new CopyOnWriteArrayList<>())
                .add(new HandlerRegistration<>(payloadType, handler));
    }

    private record HandlerRegistration<T>(Class<T> payloadType, Consumer<T> handler) {

        private void tryHandle(Object payload) {
            if (payloadType.isInstance(payload)) {
                handler.accept(payloadType.cast(payload));
            }
        }
    }
}
