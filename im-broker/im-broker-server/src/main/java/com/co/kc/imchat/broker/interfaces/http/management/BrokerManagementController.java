package com.co.kc.imchat.broker.interfaces.http.management;

import com.co.kc.imchat.broker.support.diagnostic.service.BrokerDiagnosticService;
import com.co.kc.imchat.broker.transformer.interfaces.BrokerManagementHttpTransformer;
import com.co.kc.imchat.broker.support.diagnostic.model.io.BrokerNodeResponse;
import com.co.kc.imchat.broker.support.diagnostic.model.io.BrokerOverviewResponse;
import com.co.kc.imchat.broker.support.diagnostic.model.io.ConnectionMigrationResponse;
import com.co.kc.imchat.broker.support.diagnostic.model.io.ConnectionRouteResponse;
import com.co.kc.imchat.broker.support.diagnostic.model.io.DiagnosticSummaryResponse;
import com.co.kc.imchat.broker.support.diagnostic.model.io.GatewayNodeResponse;
import com.co.kc.imchat.broker.support.diagnostic.model.io.GossipRecordResponse;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.BrokerNodeDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.BrokerOverviewDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.ConnectionMigrationRecordDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.ConnectionRouteDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.DiagnosticSummaryDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.GatewayNodeDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.GossipRecordDTO;
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
public class BrokerManagementController {
    private final BrokerDiagnosticService diagnosticService;

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
        List<ConnectionRouteDTO> connections = diagnosticService.connections(userId);
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
        List<GossipRecordDTO> records = diagnosticService.gossipRecords(limit);
        return BrokerManagementHttpTransformer.INSTANCE.gossipRecordResponseFrom(records);
    }

    @GetMapping("/migrations")
    public List<ConnectionMigrationResponse> migrations(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit
    ) {
        List<ConnectionMigrationRecordDTO> migrations = diagnosticService.migrationRecords(limit);
        return BrokerManagementHttpTransformer.INSTANCE.connectionMigrationResponseFrom(migrations);
    }
}
