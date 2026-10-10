package com.co.kc.imchat.management.ops.transformer.infrastructure;

import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerNodePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerOverviewPayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.ConnectionRoutePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.GatewayNodePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.GossipRecordPayload;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerNodeDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerOverviewDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.ConnectionRouteDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.GatewayNodeDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.GossipRecordDTO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/** Broker 协议载荷与 Ops 投影转换器。 */
@Mapper
public interface BrokerClientTransformer {
    BrokerClientTransformer INSTANCE = Mappers.getMapper(BrokerClientTransformer.class);

    BrokerOverviewDTO brokerOverviewDataFrom(BrokerOverviewPayload payload);

    List<BrokerNodeDTO> brokerNodeDataFrom(List<BrokerNodePayload> payloads);

    List<GatewayNodeDTO> gatewayNodeDataFrom(List<GatewayNodePayload> payloads);

    List<ConnectionRouteDTO> connectionRouteDataFrom(List<ConnectionRoutePayload> payloads);

    List<GossipRecordDTO> gossipRecordDataFrom(List<GossipRecordPayload> payloads);

}
