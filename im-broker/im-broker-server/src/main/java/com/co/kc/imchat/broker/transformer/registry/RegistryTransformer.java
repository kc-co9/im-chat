package com.co.kc.imchat.broker.transformer.registry;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerRegistration;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRegistration;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayHost;
import com.co.kc.imchat.broker.domain.connection.model.GatewayPort;
import com.co.kc.imchat.broker.domain.connection.model.GatewayPrincipalId;
import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
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

    /** 仅在跨进程调用或响应边界转换为 SDK 端点。 */
    @Mapping(target = "gatewayId", source = "gatewayId.value")
    @Mapping(target = "host", source = "host.value")
    @Mapping(target = "port", source = "port.value")
    @Mapping(target = "workloadIdentity", source = "principalId.value")
    GatewayEndpointDTO gatewayEndpointFrom(GatewayRegistration registration);

    /** Broker 列表 RPC 响应的传输模型。 */
    BrokerEndpointDTO brokerEndpointFrom(BrokerRegistration registration);

    default GatewayRegistration gatewayEndpointFrom(
            String gatewayId, String host, int port, String workloadIdentity) {
        return gatewayEndpointFrom(gatewayId, host, port, workloadIdentity, Instant.now());
    }

    default GatewayRegistration gatewayEndpointFrom(String gatewayId, String host, int port,
                                                    String workloadIdentity, Instant now) {
        return new GatewayRegistration(new GatewayId(gatewayId), new GatewayHost(host),
                new GatewayPort(port), new GatewayPrincipalId(workloadIdentity), now, now);
    }

    default BrokerRegistration brokerEndpointFrom(String brokerId, String host, int port) {
        return brokerEndpointFrom(brokerId, host, port, Instant.now());
    }

    @Mapping(target = "brokerId", source = "brokerId")
    @Mapping(target = "host", source = "host")
    @Mapping(target = "port", source = "port")
    @Mapping(target = "registeredAt", source = "now")
    @Mapping(target = "lastSeenAt", source = "now")
    BrokerRegistration brokerEndpointFrom(String brokerId, String host, int port, Instant now);

    @Mapping(target = "lastSeenAt", expression = "java(Instant.now())")
    BrokerRegistration refreshBrokerHeartbeat(BrokerRegistration broker);

    @Mapping(target = "lastSeenAt", expression = "java(Instant.now())")
    GatewayRegistration refreshGatewayHeartbeat(GatewayRegistration gateway);
}
