package com.co.kc.imchat.management.audit.sdk.client;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.audit.sdk.model.AuditEvent;
import com.co.kc.imchat.management.audit.sdk.support.AuditFailureReporter;
import com.co.kc.imchat.management.audit.sdk.transport.AuditTransport;
import lombok.RequiredArgsConstructor;

/** 默认隔离投递通道同步故障的审计客户端。 */
@RequiredArgsConstructor
public class DefaultAuditClient implements AuditClient {
    private static final String TRANSPORT_STAGE = "transport";

    private final AuditTransport transport;
    private final AuditFailureReporter failureReporter;

    @Override
    public void submit(AuditEvent event) {
        AssertUtils.argNotNull("audit event must not be null", event);
        try {
            transport.send(event);
        } catch (RuntimeException failure) {
            failureReporter.report(TRANSPORT_STAGE, failure);
        }
    }
}
