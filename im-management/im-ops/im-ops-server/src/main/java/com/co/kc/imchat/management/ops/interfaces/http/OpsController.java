package com.co.kc.imchat.management.ops.interfaces.http;

import com.co.kc.imchat.management.ops.application.OpsQueryAppService;
import com.co.kc.imchat.management.iam.sdk.security.RequiresPermission;
import com.co.kc.imchat.management.ops.domain.authorization.model.OpsPermission;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerNodeDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerNodeOverviewDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.ClusterOverviewDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.ClusterQueryResultDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.ConnectionRouteDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.GatewayNodeDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.GossipRecordDTO;
import com.co.kc.imchat.management.ops.model.cqrs.query.BrokerGetQuery;
import com.co.kc.imchat.management.ops.model.cqrs.query.BrokerListQuery;
import com.co.kc.imchat.management.ops.model.cqrs.query.ConnectionQuery;
import com.co.kc.imchat.management.ops.model.cqrs.query.DiagnosticQuery;
import com.co.kc.imchat.management.ops.model.cqrs.query.GatewayListQuery;
import com.co.kc.imchat.management.ops.model.cqrs.query.OverviewQuery;
import com.co.kc.imchat.management.ops.model.io.BrokerNodeResponse;
import com.co.kc.imchat.management.ops.model.io.BrokerOverviewResponse;
import com.co.kc.imchat.management.ops.model.io.ClusterQueryResponse;
import com.co.kc.imchat.management.ops.model.io.ConnectionRouteResponse;
import com.co.kc.imchat.management.ops.model.io.GatewayNodeResponse;
import com.co.kc.imchat.management.ops.model.io.GossipRecordResponse;
import com.co.kc.imchat.management.ops.model.io.OpsOverviewResponse;
import com.co.kc.imchat.management.ops.transformer.interfaces.OpsHttpTransformer;
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

/** Ops 集群只读查询接口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class OpsController {
    private final OpsQueryAppService queryAppService;

    @GetMapping("/overview")
    @RequiresPermission(OpsPermission.Code.OVERVIEW_READ)
    public OpsOverviewResponse overview() {
        ClusterOverviewDTO overview = queryAppService.overview(new OverviewQuery());
        return OpsHttpTransformer.INSTANCE.opsOverviewResponseFrom(overview);
    }

    @GetMapping("/brokers")
    @RequiresPermission(OpsPermission.Code.BROKER_READ)
    public ClusterQueryResponse<BrokerNodeResponse> brokers() {
        ClusterQueryResultDTO<BrokerNodeDTO> result =
                queryAppService.brokers(new BrokerListQuery());
        return OpsHttpTransformer.INSTANCE.brokerQueryResponseFrom(result);
    }

    @GetMapping("/brokers/{brokerId}")
    @RequiresPermission(OpsPermission.Code.BROKER_READ)
    public BrokerOverviewResponse broker(@PathVariable String brokerId) {
        BrokerNodeOverviewDTO overview = queryAppService.broker(new BrokerGetQuery(brokerId));
        return OpsHttpTransformer.INSTANCE.brokerOverviewResponseFrom(overview);
    }

    @GetMapping("/gateways")
    @RequiresPermission(OpsPermission.Code.GATEWAY_READ)
    public ClusterQueryResponse<GatewayNodeResponse> gateways() {
        ClusterQueryResultDTO<GatewayNodeDTO> result =
                queryAppService.gateways(new GatewayListQuery());
        return OpsHttpTransformer.INSTANCE.gatewayQueryResponseFrom(result);
    }

    @GetMapping("/connections")
    @RequiresPermission(OpsPermission.Code.CONNECTION_READ)
    public ClusterQueryResponse<ConnectionRouteResponse> connections(
            @RequestParam @Positive Long userId
    ) {
        ClusterQueryResultDTO<ConnectionRouteDTO> result =
                queryAppService.connections(new ConnectionQuery(userId));
        return OpsHttpTransformer.INSTANCE.connectionQueryResponseFrom(result);
    }

    @GetMapping("/gossip/records")
    @RequiresPermission(OpsPermission.Code.DIAGNOSTIC_READ)
    public ClusterQueryResponse<GossipRecordResponse> gossipRecords(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        ClusterQueryResultDTO<GossipRecordDTO> result =
                queryAppService.gossipRecords(new DiagnosticQuery(limit));
        return OpsHttpTransformer.INSTANCE.gossipQueryResponseFrom(result);
    }

}
