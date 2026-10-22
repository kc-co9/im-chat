package com.co.kc.imchat.broker.support.raft.codec;

import com.co.kc.imchat.broker.model.cqrs.command.BeginBrokerClusterOperationCommand;
import com.co.kc.imchat.broker.support.raft.model.BrokerClusterSnapshot;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MembershipEndpointWireCompatibilityTest {
    // 固定载荷由迁移前实现生成，不能用当前编码结果替代兼容性输入。
    @Test
    void preMigrationV2CommandRetainsEndpointShapeAndValue() throws Exception {
        byte[] fixture = fixture("membership-command-v2.json");
        JsonNode envelope = JsonUtils.getMapper().readTree(fixture);
        assertThat(envelope.path("formatVersion").asInt()).isEqualTo(2);
        assertThat(envelope.path("commandType").asText()).isEqualTo("BEGIN_BROKER_CLUSTER_OPERATION");
        JsonNode payload = JsonUtils.getMapper().readTree(envelope.path("payload").asText());
        assertThat(payload.path("proposal").path("membershipEndpoint"))
                .isEqualTo(JsonUtils.getMapper().readTree("{\"value\":\"10.0.0.2:18120\"}"));
        BeginBrokerClusterOperationCommand decoded = (BeginBrokerClusterOperationCommand)
                BrokerClusterCommandCodec.INSTANCE.decode(fixture);
        assertThat(decoded.proposal().membershipEndpoint()).isEqualTo(new MembershipEndpoint("10.0.0.2:18120"));
        byte[] encoded = BrokerClusterCommandCodec.INSTANCE.encode(decoded);
        assertThat(JsonUtils.getMapper().readTree(encoded)).isEqualTo(envelope);
        assertThat(new String(encoded, StandardCharsets.UTF_8)).doesNotContain("com.co.kc", "@class");
    }

    @Test
    void preMigrationV2SnapshotRetainsOperationEndpointAndStringBindings() throws Exception {
        byte[] fixture = fixture("membership-snapshot-v2.json");
        JsonNode json = JsonUtils.getMapper().readTree(fixture);
        assertThat(json.path("formatVersion").asInt()).isEqualTo(2);
        assertThat(json.path("operations").get(0).path("membershipEndpoint"))
                .isEqualTo(JsonUtils.getMapper().readTree("{\"value\":\"10.0.0.2:18120\"}"));
        assertThat(json.path("membershipEndpoints").path("broker-b").asText()).isEqualTo("10.0.0.2:18120");
        BrokerClusterSnapshot decoded = BrokerClusterSnapshot.decode(fixture);
        assertThat(decoded.operations().getFirst().membershipEndpoint()).isEqualTo(new MembershipEndpoint("10.0.0.2:18120"));
        assertThat(decoded.membershipEndpoints()).containsExactlyEntriesOf(java.util.Map.of("broker-b", "10.0.0.2:18120"));
        byte[] encoded = BrokerClusterSnapshot.encode(decoded);
        assertThat(JsonUtils.getMapper().readTree(encoded)).isEqualTo(json);
        assertThat(new String(encoded, StandardCharsets.UTF_8)).doesNotContain("com.co.kc", "@class");
    }

    private byte[] fixture(String name) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/raft/" + name)) {
            assertThat(input).isNotNull();
            return input.readAllBytes();
        }
    }
}
