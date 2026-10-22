package com.co.kc.imchat.broker.transformer.application;

import com.co.kc.imchat.broker.model.cqrs.dto.BrokerNodeDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.ConnectionRouteDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.GatewayNodeDTO;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerRegistration;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/** 已注册领域对象到诊断快照的声明式转换。 */
@Mapper
public interface BrokerDiagnosticAppTransformer {
    BrokerDiagnosticAppTransformer INSTANCE = Mappers.getMapper(BrokerDiagnosticAppTransformer.class);

    BrokerNodeDTO brokerFrom(BrokerRegistration source);

    @Mapping(target = "gatewayId", source = "gatewayId.value")
    @Mapping(target = "host", source = "host.value")
    @Mapping(target = "port", source = "port.value")
    GatewayNodeDTO gatewayFrom(GatewayRegistration source);

    @Mapping(target = "registeredAt", expression = "java(source.connectedAt())")
    @Mapping(target = "lastSeenAt", expression = "java(source.refreshedAt())")
    @Mapping(target = "userId", expression = "java(source.userId().value())")
    @Mapping(target = "gatewayId", expression = "java(source.gatewayId().value())")
    ConnectionRouteDTO connectionFrom(ConnectionRegistration source);
}
