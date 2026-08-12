package com.co.kc.imchat.broker.transformer;

import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

/**
 * Broker 注册表对象转换器。
 */
@Mapper(imports = Instant.class)
public interface RegistryTransformer {
    RegistryTransformer INSTANCE = Mappers.getMapper(RegistryTransformer.class);

    default GatewayEndpointDTO gatewayEndpointFrom(String gatewayId, String host, int port) {
        return gatewayEndpointFrom(gatewayId, host, port, Instant.now());
    }

    @Mapping(target = "gatewayId", source = "gatewayId")
    @Mapping(target = "host", source = "host")
    @Mapping(target = "port", source = "port")
    @Mapping(target = "registeredAt", source = "now")
    @Mapping(target = "lastSeenAt", source = "now")
    GatewayEndpointDTO gatewayEndpointFrom(String gatewayId, String host, int port, Instant now);

    default BrokerEndpointDTO brokerEndpointFrom(String brokerId, String host, int port) {
        return brokerEndpointFrom(brokerId, host, port, Instant.now());
    }

    @Mapping(target = "brokerId", source = "brokerId")
    @Mapping(target = "host", source = "host")
    @Mapping(target = "port", source = "port")
    @Mapping(target = "registeredAt", source = "now")
    @Mapping(target = "lastSeenAt", source = "now")
    BrokerEndpointDTO brokerEndpointFrom(String brokerId, String host, int port, Instant now);

    @Mapping(target = "lastSeenAt", expression = "java(Instant.now())")
    BrokerEndpointDTO refreshBrokerHeartbeat(BrokerEndpointDTO broker);

    @Mapping(target = "lastSeenAt", expression = "java(Instant.now())")
    GatewayEndpointDTO refreshGatewayHeartbeat(GatewayEndpointDTO gateway);
}
