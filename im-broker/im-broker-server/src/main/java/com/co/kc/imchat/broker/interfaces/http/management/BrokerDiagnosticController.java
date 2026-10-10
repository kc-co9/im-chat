package com.co.kc.imchat.broker.interfaces.http.management;

import com.co.kc.imchat.broker.model.cqrs.query.ConnectionRoutesQuery;
import com.co.kc.imchat.broker.model.cqrs.query.GossipRecordsQuery;
import com.co.kc.imchat.broker.application.diagnostic.BrokerDiagnosticAppService;
import com.co.kc.imchat.broker.transformer.interfaces.BrokerManagementHttpTransformer;
import com.co.kc.imchat.broker.model.io.BrokerNodeResponse;
import com.co.kc.imchat.broker.model.io.BrokerOverviewResponse;
import com.co.kc.imchat.broker.model.io.ConnectionRouteResponse;
import com.co.kc.imchat.broker.model.io.DiagnosticSummaryResponse;
import com.co.kc.imchat.broker.model.io.GatewayNodeResponse;
import com.co.kc.imchat.broker.model.io.GossipRecordResponse;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerNodeDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerOverviewDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.ConnectionRouteDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.DiagnosticSummaryDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.GatewayNodeDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.GossipRecordDTO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Broker 节点只读管理接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/management")
public class BrokerDiagnosticController {
    private final BrokerDiagnosticAppService diagnosticService;

    @GetMapping("/broker/overview")
    public BrokerOverviewResponse overview() {
        BrokerOverviewDTO overview = diagnosticService.overview();
        return BrokerManagementHttpTransformer.INSTANCE.brokerOverviewResponseFrom(overview);
    }

    @GetMapping("/brokers")
    public List<BrokerNodeResponse> brokers() {
        List<BrokerNodeDTO> brokers = diagnosticService.brokers();
        return BrokerManagementHttpTransformer.INSTANCE.brokerNodeResponseFrom(brokers);
    }

    @GetMapping("/gateways")
    public List<GatewayNodeResponse> gateways() {
        List<GatewayNodeDTO> gateways = diagnosticService.gateways();
        return BrokerManagementHttpTransformer.INSTANCE.gatewayNodeResponseFrom(gateways);
    }

    @GetMapping("/connections")
    public List<ConnectionRouteResponse> connections(@RequestParam @Positive Long userId) {
        List<ConnectionRouteDTO> connections = diagnosticService.connections(new ConnectionRoutesQuery(userId));
        return BrokerManagementHttpTransformer.INSTANCE.connectionRouteResponseFrom(connections);
    }

    @GetMapping("/gossip")
    public DiagnosticSummaryResponse gossip() {
        DiagnosticSummaryDTO summary = diagnosticService.gossipSummary();
        return BrokerManagementHttpTransformer.INSTANCE.diagnosticSummaryResponseFrom(summary);
    }

    @GetMapping("/gossip/records")
    public List<GossipRecordResponse> gossipRecords(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit
    ) {
        List<GossipRecordDTO> records = diagnosticService.gossipRecords(new GossipRecordsQuery(limit));
        return BrokerManagementHttpTransformer.INSTANCE.gossipRecordResponseFrom(records);
    }

}
