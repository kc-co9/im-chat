package com.co.kc.imchat.management.monitor.interfaces.http;

import com.co.kc.imchat.management.monitor.application.MonitorQueryAppService;
import com.co.kc.imchat.management.iam.sdk.security.RequiresPermission;
import com.co.kc.imchat.management.monitor.domain.authorization.model.MonitorPermission;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.BrokerNodeDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.BrokerNodeOverviewDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.ClusterOverviewDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.ClusterQueryResultDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.ConnectionRouteDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.GatewayNodeDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.GossipRecordDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.MigrationRecordDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.query.BrokerGetQuery;
import com.co.kc.imchat.management.monitor.model.cqrs.query.BrokerListQuery;
import com.co.kc.imchat.management.monitor.model.cqrs.query.ConnectionQuery;
import com.co.kc.imchat.management.monitor.model.cqrs.query.DiagnosticQuery;
import com.co.kc.imchat.management.monitor.model.cqrs.query.GatewayListQuery;
import com.co.kc.imchat.management.monitor.model.cqrs.query.OverviewQuery;
import com.co.kc.imchat.management.monitor.model.io.BrokerNodeResponse;
import com.co.kc.imchat.management.monitor.model.io.BrokerOverviewResponse;
import com.co.kc.imchat.management.monitor.model.io.ClusterQueryResponse;
import com.co.kc.imchat.management.monitor.model.io.ConnectionRouteResponse;
import com.co.kc.imchat.management.monitor.model.io.GatewayNodeResponse;
import com.co.kc.imchat.management.monitor.model.io.GossipRecordResponse;
import com.co.kc.imchat.management.monitor.model.io.MigrationRecordResponse;
import com.co.kc.imchat.management.monitor.model.io.MonitorOverviewResponse;
import com.co.kc.imchat.management.monitor.transformer.interfaces.MonitorHttpTransformer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Monitor 集群只读查询接口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MonitorController {
    private final MonitorQueryAppService queryAppService;

    @GetMapping("/overview")
    @RequiresPermission(MonitorPermission.Code.OVERVIEW_READ)
    public MonitorOverviewResponse overview() {
        ClusterOverviewDTO overview = queryAppService.overview(new OverviewQuery());
        return MonitorHttpTransformer.INSTANCE.monitorOverviewResponseFrom(overview);
    }

    @GetMapping("/brokers")
    @RequiresPermission(MonitorPermission.Code.BROKER_READ)
    public ClusterQueryResponse<BrokerNodeResponse> brokers() {
        ClusterQueryResultDTO<BrokerNodeDTO> result =
                queryAppService.brokers(new BrokerListQuery());
        return MonitorHttpTransformer.INSTANCE.brokerQueryResponseFrom(result);
    }

    @GetMapping("/brokers/{brokerId}")
    @RequiresPermission(MonitorPermission.Code.BROKER_READ)
    public BrokerOverviewResponse broker(@PathVariable String brokerId) {
        BrokerNodeOverviewDTO overview = queryAppService.broker(new BrokerGetQuery(brokerId));
        return MonitorHttpTransformer.INSTANCE.brokerOverviewResponseFrom(overview);
    }

    @GetMapping("/gateways")
    @RequiresPermission(MonitorPermission.Code.GATEWAY_READ)
    public ClusterQueryResponse<GatewayNodeResponse> gateways() {
        ClusterQueryResultDTO<GatewayNodeDTO> result =
                queryAppService.gateways(new GatewayListQuery());
        return MonitorHttpTransformer.INSTANCE.gatewayQueryResponseFrom(result);
    }

    @GetMapping("/connections")
    @RequiresPermission(MonitorPermission.Code.CONNECTION_READ)
    public ClusterQueryResponse<ConnectionRouteResponse> connections(
            @RequestParam @Positive Long userId
    ) {
        ClusterQueryResultDTO<ConnectionRouteDTO> result =
                queryAppService.connections(new ConnectionQuery(userId));
        return MonitorHttpTransformer.INSTANCE.connectionQueryResponseFrom(result);
    }

    @GetMapping("/gossip/records")
    @RequiresPermission(MonitorPermission.Code.DIAGNOSTIC_READ)
    public ClusterQueryResponse<GossipRecordResponse> gossipRecords(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        ClusterQueryResultDTO<GossipRecordDTO> result =
                queryAppService.gossipRecords(new DiagnosticQuery(limit));
        return MonitorHttpTransformer.INSTANCE.gossipQueryResponseFrom(result);
    }

    @GetMapping("/migrations")
    @RequiresPermission(MonitorPermission.Code.DIAGNOSTIC_READ)
    public ClusterQueryResponse<MigrationRecordResponse> migrations(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        ClusterQueryResultDTO<MigrationRecordDTO> result =
                queryAppService.migrationRecords(new DiagnosticQuery(limit));
        return MonitorHttpTransformer.INSTANCE.migrationQueryResponseFrom(result);
    }
}
