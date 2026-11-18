package com.co.kc.imchat.gateway.ws.routing;

import com.co.kc.imchat.gateway.ws.config.GatewayBeans;
import com.co.kc.imchat.gateway.ws.config.properties.GatewayProperties;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayInstanceIdentityTest {

    @Test
    void eachGatewayProcessCreationGetsANewRandomIdentity() {
        GatewayProperties properties = new GatewayProperties();
        ImBoltProperties boltProperties = new ImBoltProperties();
        boltProperties.getServer().setPort(18012);
        GatewayBeans beans = new GatewayBeans();

        GatewayInstanceIdentity first = beans.gatewayInstanceIdentity(properties, boltProperties);
        GatewayInstanceIdentity second = beans.gatewayInstanceIdentity(properties, boltProperties);

        assertThat(first.gatewayId()).startsWith("gateway-127.0.0.1-18012-");
        assertThat(second.gatewayId()).isNotEqualTo(first.gatewayId());
    }
}
