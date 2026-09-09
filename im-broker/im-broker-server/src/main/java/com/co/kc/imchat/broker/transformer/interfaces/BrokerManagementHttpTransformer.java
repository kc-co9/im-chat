package com.co.kc.imchat.broker.transformer.interfaces;

import com.co.kc.imchat.broker.support.diagnostic.model.dto.BrokerOverviewDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.BrokerNodeDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.ConnectionMigrationRecordDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.ConnectionRouteDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.DiagnosticSummaryDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.GatewayNodeDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.GossipRecordDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.io.BrokerNodeResponse;
import com.co.kc.imchat.broker.support.diagnostic.model.io.BrokerOverviewResponse;
import com.co.kc.imchat.broker.support.diagnostic.model.io.ConnectionMigrationResponse;
import com.co.kc.imchat.broker.support.diagnostic.model.io.ConnectionRouteResponse;
import com.co.kc.imchat.broker.support.diagnostic.model.io.DiagnosticSummaryResponse;
import com.co.kc.imchat.broker.support.diagnostic.model.io.GatewayNodeResponse;
import com.co.kc.imchat.broker.support.diagnostic.model.io.GossipRecordResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/** Broker 诊断模型到 HTTP 响应的声明式转换。 */
@Mapper
public interface BrokerManagementHttpTransformer {
    BrokerManagementHttpTransformer INSTANCE = Mappers.getMapper(BrokerManagementHttpTransformer.class);

    BrokerOverviewResponse brokerOverviewResponseFrom(BrokerOverviewDTO source);

    DiagnosticSummaryResponse diagnosticSummaryResponseFrom(DiagnosticSummaryDTO source);

    List<BrokerNodeResponse> brokerNodeResponseFrom(List<BrokerNodeDTO> source);

    List<GatewayNodeResponse> gatewayNodeResponseFrom(List<GatewayNodeDTO> source);

    List<ConnectionRouteResponse> connectionRouteResponseFrom(List<ConnectionRouteDTO> source);

    List<GossipRecordResponse> gossipRecordResponseFrom(List<GossipRecordDTO> source);

    List<ConnectionMigrationResponse> connectionMigrationResponseFrom(
            List<ConnectionMigrationRecordDTO> source);
}
