package com.co.kc.imchat.loadtest.cleanup;

import com.co.kc.imchat.service.message.infrastructure.notification.redis.RedisImMessageConfirmableStore;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class LoadTestReceiptCleanerTest {

    @Test
    void delegatesEachExactManifestReceiptOnceOnEveryIdempotentPass() {
        RedisImMessageConfirmableStore store = mock(RedisImMessageConfirmableStore.class);
        LoadTestReceiptCleaner cleaner = new LoadTestReceiptCleaner(store);
        LoadTestBatchManifest manifest = LoadTestBatchManifest.parse(LoadTestBatchManifestTest.validJson());

        cleaner.clean(manifest);
        verify(store).confirm("PRIVATE_MESSAGE_SEND:102:302:401");
        verify(store).confirm("GROUP_MESSAGE_SEND:102:312:402");
        verifyNoMoreInteractions(store);

        cleaner.clean(manifest);
        verify(store, times(2)).confirm("PRIVATE_MESSAGE_SEND:102:302:401");
        verify(store, times(2)).confirm("GROUP_MESSAGE_SEND:102:312:402");
        verifyNoMoreInteractions(store);
    }
}
