package com.co.kc.imchat.broker.infrastructure.chain.interceptor;

import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerAdmissionContractValidator;
import com.co.kc.imchat.broker.sdk.access.BrokerAdmissionType;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerHealthReportParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerProbeParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayHeartbeatParams;
import com.co.kc.imchat.broker.sdk.model.params.QuorumReadIndexParams;
import com.co.kc.imchat.broker.sdk.model.params.RouteLeaseInstallParams;
import com.co.kc.imchat.broker.sdk.model.params.RouteLeasePrepareParams;
import com.co.kc.imchat.broker.sdk.model.params.RouteLookupParams;
import com.co.kc.imchat.broker.sdk.model.params.RoutingFenceParams;
import com.co.kc.imchat.plugin.gossip.model.param.GossipDigestParams;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerAdmissionContractValidatorTest {

    @Test
    void acceptsCapabilitiesRequiredByEachAccessProfile() {
        assertThatCode(() -> BrokerAdmissionContractValidator.validateRequestType(
                BrokerBoltOperation.HEARTBEAT_GATEWAY, GatewayHeartbeatParams.class))
                .doesNotThrowAnyException();
        assertThatCode(() -> BrokerAdmissionContractValidator.validateRequestType(
                BrokerBoltOperation.GOSSIP_DIGEST, GossipDigestParams.class))
                .doesNotThrowAnyException();
        assertThatCode(() -> BrokerAdmissionContractValidator.validateRequestType(
                BrokerBoltOperation.LOOKUP_CONNECTION_ROUTES, RouteLookupParams.class))
                .doesNotThrowAnyException();
        assertThatCode(() -> BrokerAdmissionContractValidator.validateRequestType(
                BrokerBoltOperation.WRITE_FRAME, BrokerFrameWriteParams.class))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsRequestTypeThatDoesNotImplementProfileCapability() {
        assertThatThrownBy(() -> BrokerAdmissionContractValidator.validateRequestType(
                BrokerBoltOperation.HEARTBEAT_GATEWAY, Object.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("capability");
    }

    @Test
    void controlOperationsAcceptOnlyTheirTypedRequests() {
        assertThatCode(() -> BrokerAdmissionContractValidator.validateRequestType(
                BrokerBoltOperation.FETCH_QUORUM_READ_INDEX, QuorumReadIndexParams.class))
                .doesNotThrowAnyException();
        assertThatCode(() -> BrokerAdmissionContractValidator.validateRequestType(
                BrokerBoltOperation.PREPARE_ROUTE_LEASE, RouteLeasePrepareParams.class))
                .doesNotThrowAnyException();
        assertThatCode(() -> BrokerAdmissionContractValidator.validateRequestType(
                BrokerBoltOperation.INSTALL_ROUTE_LEASE, RouteLeaseInstallParams.class))
                .doesNotThrowAnyException();
        assertThatCode(() -> BrokerAdmissionContractValidator.validateRequestType(
                BrokerBoltOperation.FENCE_ROUTING_EPOCH, RoutingFenceParams.class))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> BrokerAdmissionContractValidator.validateRequestType(
                BrokerBoltOperation.FETCH_QUORUM_READ_INDEX, RouteLeasePrepareParams.class))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("capability");
        assertThatThrownBy(() -> BrokerAdmissionContractValidator.validateRequestType(
                BrokerBoltOperation.FETCH_QUORUM_READ_INDEX, Object.class))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("capability");
    }
    @Test
    void observationOperationsRequireExactTypedRequestsAndOwnAdmissionProfile() {
        assertThatCode(() -> BrokerAdmissionContractValidator.validateRequestType(
                BrokerBoltOperation.PROBE_BROKER, BrokerProbeParams.class))
                .doesNotThrowAnyException();
        assertThatCode(() -> BrokerAdmissionContractValidator.validateRequestType(
                BrokerBoltOperation.FETCH_BROKER_HEALTH_REPORT, BrokerHealthReportParams.class))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> BrokerAdmissionContractValidator.validateRequestType(
                BrokerBoltOperation.PROBE_BROKER, BrokerHealthReportParams.class))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("capability");
        Assertions.assertThat(BrokerBoltOperation.PROBE_BROKER.admissionType())
                .isEqualTo(BrokerAdmissionType.BROKER_OBSERVATION);
    }
}
