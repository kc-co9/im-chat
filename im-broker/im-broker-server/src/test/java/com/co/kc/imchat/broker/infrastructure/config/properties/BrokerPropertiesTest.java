package com.co.kc.imchat.broker.infrastructure.config.properties;

import com.co.kc.imchat.broker.application.cluster.BrokerMembershipAppService;
import com.co.kc.imchat.broker.infrastructure.config.BrokerRequestConfiguration;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;


class BrokerPropertiesTest {
    @Test
    void membershipConfirmationDefaultsRemainIndependentOfOutboundCalls() {
        BrokerProperties properties = new BrokerProperties();
        assertThat(properties.getMembershipConfirmation().getMaxConcurrentWaiters()).isEqualTo(8);
        assertThat(properties.getMembershipConfirmation().getTimeoutMs()).isEqualTo(3000);
        properties.getPeerCall().setMaxConcurrentRequests(2);
        properties.getPeerCall().setTimeoutMs(99);
        assertThat(properties.getMembershipConfirmation().getMaxConcurrentWaiters()).isEqualTo(8);
        assertThat(properties.getMembershipConfirmation().getTimeoutMs()).isEqualTo(3000);
    }

    @Test
    void membershipConfirmationOverridesBindToTheirTypedOwner() {
        BrokerProperties properties = bind(3, 120);
        assertThat(properties.getMembershipConfirmation().getMaxConcurrentWaiters()).isEqualTo(3);
        assertThat(properties.getMembershipConfirmation().getTimeoutMs()).isEqualTo(120);
        assertThat(properties.getPeerCall().getMaxConcurrentRequests()).isEqualTo(8);
        assertThat(properties.getPeerCall().getTimeoutMs()).isEqualTo(3000);
        assertThat(new BrokerRequestConfiguration()
                .membershipPermitDrainedHandler(mock(BrokerMembershipAppService.class), properties).brokerOperation())
                .isEqualTo(BrokerBoltOperation.CONFIRM_MEMBERSHIP_PERMIT_DRAINED);
    }

    @ParameterizedTest
    @CsvSource({"0, 3000", "-1, 3000", "8, 0", "8, -1"})
    void handlerRejectsNonPositiveConfirmationBudget(int cap, int timeout) {
        BrokerProperties properties = bind(cap, timeout);
        assertThatThrownBy(() -> new BrokerRequestConfiguration()
                .membershipPermitDrainedHandler(mock(BrokerMembershipAppService.class), properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("waiters and timeout must be positive");
    }

    private static BrokerProperties bind(int cap, int timeout) {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "im.broker.membership-confirmation.max-concurrent-waiters", cap,
                "im.broker.membership-confirmation.timeout-ms", timeout));
        return new Binder(source).bind("im.broker", Bindable.of(BrokerProperties.class)).orElseThrow(IllegalStateException::new);
    }
}
