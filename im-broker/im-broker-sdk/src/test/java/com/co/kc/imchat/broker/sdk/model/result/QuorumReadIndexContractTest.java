package com.co.kc.imchat.broker.sdk.model.result;

import com.co.kc.imchat.broker.sdk.access.BrokerAdmissionType;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.QuorumReadIndexParams;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuorumReadIndexContractTest {

    @Test
    void operationUsesNeutralReadIndexNameAndRetainsBrokerControlAdmission() {
        assertThat(BrokerBoltOperation.FETCH_QUORUM_READ_INDEX.operation()).isEqualTo("fetchQuorumReadIndex");
        assertThat(BrokerBoltOperation.FETCH_QUORUM_READ_INDEX.admissionType())
                .isEqualTo(BrokerAdmissionType.BROKER_CONTROL);
    }

    @Test
    void responseSerializesAppliedReadIndexWithoutLegacyFieldAlias() {
        QuorumReadIndexResult result = new QuorumReadIndexResult("broker-b", 12L);
        ObjectNode payload = JsonUtils.getMapper().valueToTree(result);
        assertThat(payload.has("clusterId")).isFalse();
        assertThat(payload.path("nodeId").asText()).isEqualTo("broker-b");
        assertThat(payload.path("quorumReadIndex").asLong()).isEqualTo(12L);
        assertThat(payload.has("appliedReadIndex")).isFalse();
        assertThat(JsonUtils.fromJson(payload.toString(), QuorumReadIndexResult.class)).isEqualTo(result);
    }

    @Test
    void emptyRequestRetainsTypedJsonBoundary() {
        QuorumReadIndexParams request = new QuorumReadIndexParams();
        ObjectNode payload = JsonUtils.getMapper().valueToTree(request);

        assertThat(payload.isEmpty()).isTrue();
        assertThat(JsonUtils.fromJson(payload.toString(), QuorumReadIndexParams.class))
                .isEqualTo(request);
    }
}
