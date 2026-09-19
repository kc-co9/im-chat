package com.co.kc.imchat.loadtest.cleanup;

import com.co.kc.imchat.service.message.infrastructure.notification.redis.RedisImMessageConfirmableStore;

import java.util.Objects;

final class LoadTestReceiptCleaner {

    private final RedisImMessageConfirmableStore store;

    LoadTestReceiptCleaner(RedisImMessageConfirmableStore store) {
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    void clean(LoadTestBatchManifest manifest) {
        for (String receiptId : manifest.receiptIds()) {
            store.confirm(receiptId);
        }
    }
}
