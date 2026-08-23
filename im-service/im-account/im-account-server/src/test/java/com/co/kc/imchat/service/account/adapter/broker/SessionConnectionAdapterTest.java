package com.co.kc.imchat.service.account.adapter.broker;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionCloseParams;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.account.domain.session.model.SessionVersion;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SessionConnectionAdapterTest {

    @Test
    void publishesConnectionClose() {
        BrokerClient brokerClient = mock(BrokerClient.class);
        SessionConnectionAdapter adapter = new SessionConnectionAdapter(brokerClient);
        ConnectionCloseParams params = new ConnectionCloseParams(42L, "session-old");
        adapter.closeConnections(new UserId(42L), new SessionVersion("session-old"));

        verify(brokerClient).closeConnections(params);
    }
}
