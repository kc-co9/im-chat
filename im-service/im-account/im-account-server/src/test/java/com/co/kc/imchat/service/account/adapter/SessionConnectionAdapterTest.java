package com.co.kc.imchat.service.account.adapter;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionCloseParams;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommit;
import com.co.kc.imchat.service.account.domain.session.model.SessionVersion;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SessionConnectionAdapterTest {

    @Test
    void closesConnectionsOnlyAfterTransactionCommit() throws NoSuchMethodException {
        Method method = SessionConnectionAdapter.class.getMethod(
                "closeConnections", UserId.class, SessionVersion.class);

        assertThat(method.getAnnotation(AfterTransactionCommit.class)).isNotNull();
    }

    @Test
    void publishesConnectionClose() {
        BrokerClient brokerClient = mock(BrokerClient.class);
        SessionConnectionAdapter adapter = new SessionConnectionAdapter(brokerClient);
        ConnectionCloseParams params = new ConnectionCloseParams(42L, "session-old");
        adapter.closeConnections(new UserId(42L), new SessionVersion("session-old"));

        verify(brokerClient).closeConnections(params);
    }
}
