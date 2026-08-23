package com.co.kc.imchat.service.message.support;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.embedded.LinkedHashMapCacheBuilder;
import com.alicp.jetcache.support.BroadcastManager;
import com.alicp.jetcache.template.QuickConfig;
import com.co.kc.imchat.service.message.application.notification.confirmable.ImMessageConfirmableStore;
import com.co.kc.imchat.service.message.application.notification.task.ReceiptTask;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerListResult;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.service.message.domain.social.model.FriendDisplay;
import com.co.kc.imchat.service.message.domain.social.model.GroupMessageRecipient;
import com.co.kc.imchat.service.message.domain.social.model.GroupSummary;
import com.co.kc.imchat.service.message.adapter.social.SocialAdapter;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatView;
import com.co.kc.imchat.service.message.domain.chat.repository.ImChatViewRepository;
import com.co.kc.imchat.service.message.domain.chat.repository.ImChatViewRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@TestConfiguration
public class TestCacheConfiguration {

    @Bean
    @Primary
    CacheManager cacheManager() {
        return new MemoryCacheManager();
    }

    @Bean
    @Primary
    ImMessageConfirmableStore imMessageConfirmableStore() {
        return new NoOpConfirmableStore();
    }

    @Bean
    @Primary
    BrokerClient brokerClient() {
        return BrokerClientTestSupport.client(new EmptyPushBoltInvoker());
    }

    private record EmptyPushBoltInvoker() implements BoltInvoker {
        @Override
        @SuppressWarnings("unchecked")
        public <T, R> R invoke(String address, String service, String operation, T request,
                               Class<R> responseType, int timeoutMillis) {
            if (BrokerBoltOperation.LIST_BROKERS.operation().equals(operation)) {
                return (R) new BrokerListResult(List.of());
            }
            if (!BrokerBoltOperation.WRITE_FRAME.operation().equals(operation)) {
                return null;
            }
            BrokerFrameWriteParams params = (BrokerFrameWriteParams) request;
            return (R) BrokerFrameWriteResult.writeResult(params.userId(), List.of(), List.of());
        }
    }

    @Bean
    @Primary
    ImChatViewRepository imChatViewRepository() {
        return new ImChatViewRepository() {
            @Override
            public void save(ImChatView presence) {
            }

            @Override
            public void clear(UserId userId) {
            }

            @Override
            public java.util.Optional<ImChatView> find(UserId userId) {
                return java.util.Optional.empty();
            }
        };
    }

    @Bean
    @Primary
    SocialAdapter socialAdapter() {
        return new SocialAdapter(unusedRemoteService(com.co.kc.imchat.service.social.facade.SocialService.class)) {
            @Override
            public void ensureFriendshipActive(Long userId, Long peerUserId) {
            }

            @Override
            public void ensureGroupMember(Long groupId, Long userId) {
            }

            @Override
            public List<GroupMessageRecipient> getGroupMessageRecipients(Long groupId) {
                return List.of();
            }

            @Override
            public List<FriendDisplay> getFriendDisplays(Long userId, List<Long> friendUserIds) {
                return List.of();
            }

            @Override
            public List<GroupSummary> getGroupSummaries(List<Long> groupIds) {
                return List.of();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T unusedRemoteService(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException("unused remote service");
                });
    }

    private static class NoOpConfirmableStore implements ImMessageConfirmableStore {
        @Override
        public void offer(ReceiptTask task) {
        }

        @Override
        public void confirm(String receiptId) {
        }

        @Override
        public void startConfirming(Consumer<ReceiptTask> consumer) {
        }

        @Override
        public void stopConfirming() {
        }
    }

    private static class MemoryCacheManager implements CacheManager {
        private final Map<String, Cache<?, ?>> caches = new ConcurrentHashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public <K, V> Cache<K, V> getCache(String area, String cacheName) {
            return (Cache<K, V>) caches.get(cacheKey(area, cacheName));
        }

        @Override
        public void putCache(String area, String cacheName, Cache cache) {
            caches.put(cacheKey(area, cacheName), cache);
        }

        @Override
        public BroadcastManager getBroadcastManager(String area) {
            return null;
        }

        @Override
        public void putBroadcastManager(String area, BroadcastManager broadcastManager) {
        }

        @Override
        @SuppressWarnings("unchecked")
        public <K, V> Cache<K, V> getOrCreateCache(QuickConfig config) {
            return (Cache<K, V>) caches.computeIfAbsent(
                    cacheKey(config.getArea(), config.getName()),
                    key -> LinkedHashMapCacheBuilder.createLinkedHashMapCacheBuilder().limit(100).buildCache());
        }

        private String cacheKey(String area, String cacheName) {
            return area + ":" + cacheName;
        }
    }
}
