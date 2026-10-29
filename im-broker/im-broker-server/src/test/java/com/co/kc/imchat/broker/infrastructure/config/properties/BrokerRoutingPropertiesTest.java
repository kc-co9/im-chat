package com.co.kc.imchat.broker.infrastructure.config.properties;

import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerRoutingPropertiesTest {

    @Test
    void computesConservativeLeaseAndQuarantineFromOneValidatedConfiguration() {
        BrokerRoutingProperties properties = new BrokerRoutingProperties();
        properties.setMaxLeaseDuration(Duration.ofSeconds(4));
        properties.setRenewalInterval(Duration.ofSeconds(1));
        properties.setMaxClockDriftAllowance(Duration.ofMillis(100));
        properties.setLeaseSafetyMargin(Duration.ofMillis(200));
        properties.setMaxSchedulingPause(Duration.ofSeconds(2));
        properties.setNetworkSafetyMargin(Duration.ofMillis(500));

        properties.validate();

        assertThat(properties.leaseGrantDuration()).isEqualTo(Duration.ofMillis(3_700));
        assertThat(properties.quarantineDuration()).isEqualTo(Duration.ofMillis(6_600));
    }

    @Test
    void rejectsRenewalThatCannotCompleteWithinConservativeLease() {
        BrokerRoutingProperties properties = new BrokerRoutingProperties();
        properties.setMaxLeaseDuration(Duration.ofSeconds(2));
        properties.setRenewalInterval(Duration.ofSeconds(2));
        properties.setMaxClockDriftAllowance(Duration.ofMillis(100));
        properties.setLeaseSafetyMargin(Duration.ofMillis(100));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("renewalInterval");
    }

    @Test
    void rejectsNonPositivePendingLeaseCapacity() {
        BrokerRoutingProperties properties = new BrokerRoutingProperties();
        properties.setLeasePendingCapacity(0);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("routing capacities");
    }
    @ParameterizedTest
    @ValueSource(strings = {"NEW_KEY", "NEW_ENV", "OLD_KEY", "OLD_ENV"})
    void ignoresRemovedGatewayPrefixConfiguration(String source) {
        String key = "im.broker.routing." + (source.startsWith("OLD")
                ? "gateway-workload-identity-prefix" : "gateway-peer-identity-uri-prefix");
        String custom = "spiffe://prefix-binding.example/gateway/";
        org.springframework.core.env.StandardEnvironment environment = new org.springframework.core.env.StandardEnvironment();
        environment.getPropertySources().remove("systemProperties");
        environment.getPropertySources().remove("systemEnvironment");
        if (source.endsWith("ENV")) {
            String variable = key.replace("-", "").replace('.', '_').toUpperCase(java.util.Locale.ROOT);
            environment.getPropertySources().addFirst(new org.springframework.core.env.SystemEnvironmentPropertySource(
                    "systemEnvironment", java.util.Map.of(variable, custom)));
        } else {
            environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "binding-test", java.util.Map.of(key, custom)));
        }
        BrokerRoutingProperties properties = org.springframework.boot.context.properties.bind.Binder.get(environment)
                .bindOrCreate("im.broker.routing", BrokerRoutingProperties.class);
        properties.validate();
        assertThat(org.springframework.beans.BeanUtils.getPropertyDescriptor(
                BrokerRoutingProperties.class, "gatewayPeerIdentityUriPrefix")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEW_KEY", "NEW_ENV", "OLD_KEY", "OLD_ENV"})
    void ignoresRemovedBrokerPrefixConfiguration(String source) {
        String key = "im.broker.routing." + (source.startsWith("OLD")
                ? "broker-workload-identity-prefix" : "broker-peer-identity-uri-prefix");
        String custom = "spiffe://prefix-binding.example/broker/";
        org.springframework.core.env.StandardEnvironment environment = new org.springframework.core.env.StandardEnvironment();
        environment.getPropertySources().remove("systemProperties");
        environment.getPropertySources().remove("systemEnvironment");
        if (source.endsWith("ENV")) {
            String variable = key.replace("-", "").replace('.', '_').toUpperCase(java.util.Locale.ROOT);
            environment.getPropertySources().addFirst(new org.springframework.core.env.SystemEnvironmentPropertySource(
                    "systemEnvironment", java.util.Map.of(variable, custom)));
        } else {
            environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "binding-test", java.util.Map.of(key, custom)));
        }
        BrokerRoutingProperties properties = org.springframework.boot.context.properties.bind.Binder.get(environment)
                .bindOrCreate("im.broker.routing", BrokerRoutingProperties.class);
        properties.validate();
        assertThat(org.springframework.beans.BeanUtils.getPropertyDescriptor(
                BrokerRoutingProperties.class, "brokerPeerIdentityUriPrefix")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEW_KEY", "NEW_ENV", "OLD_KEY", "OLD_ENV"})
    void ignoresRemovedMessagePrefixConfiguration(String source) {
        String key = "im.broker.routing." + (source.startsWith("OLD")
                ? "message-workload-identity-prefix" : "message-peer-identity-uri-prefix");
        String custom = "spiffe://prefix-binding.example/message/";
        org.springframework.core.env.StandardEnvironment environment = new org.springframework.core.env.StandardEnvironment();
        environment.getPropertySources().remove("systemProperties");
        environment.getPropertySources().remove("systemEnvironment");
        if (source.endsWith("ENV")) {
            String variable = key.replace("-", "").replace('.', '_').toUpperCase(java.util.Locale.ROOT);
            environment.getPropertySources().addFirst(new org.springframework.core.env.SystemEnvironmentPropertySource(
                    "systemEnvironment", java.util.Map.of(variable, custom)));
        } else {
            environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "binding-test", java.util.Map.of(key, custom)));
        }
        BrokerRoutingProperties properties = org.springframework.boot.context.properties.bind.Binder.get(environment)
                .bindOrCreate("im.broker.routing", BrokerRoutingProperties.class);
        properties.validate();
        assertThat(org.springframework.beans.BeanUtils.getPropertyDescriptor(
                BrokerRoutingProperties.class, "messagePeerIdentityUriPrefix")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEW_KEY", "NEW_ENV", "OLD_KEY", "OLD_ENV"})
    void ignoresRemovedAccountPrefixConfiguration(String source) {
        String key = "im.broker.routing." + (source.startsWith("OLD")
                ? "account-workload-identity-prefix" : "account-peer-identity-uri-prefix");
        String custom = "spiffe://prefix-binding.example/account/";
        org.springframework.core.env.StandardEnvironment environment = new org.springframework.core.env.StandardEnvironment();
        environment.getPropertySources().remove("systemProperties");
        environment.getPropertySources().remove("systemEnvironment");
        if (source.endsWith("ENV")) {
            String variable = key.replace("-", "").replace('.', '_').toUpperCase(java.util.Locale.ROOT);
            environment.getPropertySources().addFirst(new org.springframework.core.env.SystemEnvironmentPropertySource(
                    "systemEnvironment", java.util.Map.of(variable, custom)));
        } else {
            environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "binding-test", java.util.Map.of(key, custom)));
        }
        BrokerRoutingProperties properties = org.springframework.boot.context.properties.bind.Binder.get(environment)
                .bindOrCreate("im.broker.routing", BrokerRoutingProperties.class);
        properties.validate();
        assertThat(org.springframework.beans.BeanUtils.getPropertyDescriptor(
                BrokerRoutingProperties.class, "accountPeerIdentityUriPrefix")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEW_KEY", "NEW_ENV", "OLD_KEY", "OLD_ENV"})
    void ignoresRemovedOpsPrefixConfiguration(String source) {
        String key = "im.broker.routing." + (source.startsWith("OLD")
                ? "ops-workload-identity-prefix" : "ops-peer-identity-uri-prefix");
        String custom = "spiffe://prefix-binding.example/ops/";
        org.springframework.core.env.StandardEnvironment environment = new org.springframework.core.env.StandardEnvironment();
        environment.getPropertySources().remove("systemProperties");
        environment.getPropertySources().remove("systemEnvironment");
        if (source.endsWith("ENV")) {
            String variable = key.replace("-", "").replace('.', '_').toUpperCase(java.util.Locale.ROOT);
            environment.getPropertySources().addFirst(new org.springframework.core.env.SystemEnvironmentPropertySource(
                    "systemEnvironment", java.util.Map.of(variable, custom)));
        } else {
            environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "binding-test", java.util.Map.of(key, custom)));
        }
        BrokerRoutingProperties properties = org.springframework.boot.context.properties.bind.Binder.get(environment)
                .bindOrCreate("im.broker.routing", BrokerRoutingProperties.class);
        properties.validate();
        assertThat(org.springframework.beans.BeanUtils.getPropertyDescriptor(
                BrokerRoutingProperties.class, "opsPeerIdentityUriPrefix")).isNull();
    }

}
