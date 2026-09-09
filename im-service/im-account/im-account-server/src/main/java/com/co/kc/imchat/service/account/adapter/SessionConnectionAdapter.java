package com.co.kc.imchat.service.account.adapter;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionCloseParams;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommit;
import com.co.kc.imchat.plugin.metrics.annotation.Observed;
import com.co.kc.imchat.service.account.domain.session.model.SessionVersion;

/**
 * Publishes best-effort session connection controls.
 */
public class SessionConnectionAdapter {
    private final BrokerClient brokerClient;

    public SessionConnectionAdapter(BrokerClient brokerClient) {
        this.brokerClient = brokerClient;
    }

    @AfterTransactionCommit
    @Observed(name = "im.account.session.connection.close", ignoreFailure = true)
    public void closeConnections(UserId userId, SessionVersion sessionVersion) {
        if (sessionVersion == null) {
            return;
        }
        brokerClient.closeConnections(new ConnectionCloseParams(userId.value(), sessionVersion.value()));
    }

}
