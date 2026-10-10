package com.co.kc.imchat.broker.sdk.enums;

import com.co.kc.imchat.broker.sdk.access.BrokerAdmissionType;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.broker.sdk.access.ServiceDataPlaneRequest;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionCloseParams;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;

import com.co.kc.imchat.common.model.enums.FrameType;
import com.co.kc.imchat.common.model.io.FrameRequest;
import com.co.kc.imchat.common.model.io.FrameResponse;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerBoltAccessContractTest {

    @Test
    void classifiesEveryOperationForFailClosedAdmission() {
        assertThat(EnumSet.allOf(BrokerBoltOperation.class))
                .allSatisfy(operation -> assertThat(operation.admissionType()).isNotNull());
        assertThat(BrokerBoltOperation.GOSSIP_DIGEST.admissionType())
                .isEqualTo(BrokerAdmissionType.BROKER_PEER);
        assertThat(BrokerBoltOperation.INSTALL_ROUTE_LEASE.admissionType())
                .isEqualTo(BrokerAdmissionType.BROKER_CONTROL);
        assertThat(BrokerBoltOperation.REGISTER_GATEWAY.admissionType())
                .isEqualTo(BrokerAdmissionType.GATEWAY_BOOTSTRAP);
        assertThat(BrokerBoltOperation.APPLY_GATEWAY_ROUTE_SNAPSHOT.admissionType())
                .isEqualTo(BrokerAdmissionType.GATEWAY_ROUTE);
        assertThat(BrokerBoltOperation.WRITE_FRAME.admissionType())
                .isEqualTo(BrokerAdmissionType.SERVICE_DATA_PLANE);
    }

    @Test
    void derivesRequiredCallerRoleFromDataPlaneSemantics() {
        ServiceDataPlaneRequest inbound = BrokerFrameWriteParams.inbound(
                1L, "connection-1", new FrameRequest("1", "cmd", "seq", "trace", Map.of()));
        ServiceDataPlaneRequest outbound = BrokerFrameWriteParams.outbound(
                1L, new FrameResponse("1", FrameType.PUSH, "cmd", null, "trace",
                        0, null, Map.of()));
        ServiceDataPlaneRequest close = new ConnectionCloseParams(1L, "session-1");

        assertThat(inbound.requiredCallerRole()).isEqualTo(BrokerCallerRole.GATEWAY);
        assertThat(outbound.requiredCallerRole()).isEqualTo(BrokerCallerRole.MESSAGE);
        assertThat(close.requiredCallerRole()).isEqualTo(BrokerCallerRole.ACCOUNT);
    }
}
