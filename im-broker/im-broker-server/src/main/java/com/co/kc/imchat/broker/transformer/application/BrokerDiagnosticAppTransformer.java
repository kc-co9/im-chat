package com.co.kc.imchat.broker.transformer.application;

import com.co.kc.imchat.broker.support.diagnostic.model.dto.BrokerNodeDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.ConnectionRouteDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.GatewayNodeDTO;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.dto.UserGatewayDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/** Registry 传输模型到诊断快照的声明式转换。 */
@Mapper
public interface BrokerDiagnosticAppTransformer {
    BrokerDiagnosticAppTransformer INSTANCE = Mappers.getMapper(BrokerDiagnosticAppTransformer.class);

    BrokerNodeDTO brokerFrom(BrokerEndpointDTO source);

    GatewayNodeDTO gatewayFrom(GatewayEndpointDTO source);

    @Mapping(target = "registeredAt", source = "connectedAt")
    @Mapping(target = "lastSeenAt", source = "refreshedAt")
    ConnectionRouteDTO connectionFrom(UserGatewayDTO source);
}
