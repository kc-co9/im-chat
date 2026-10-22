package com.co.kc.imchat.broker.transformer.adapter;

import com.co.kc.imchat.broker.domain.cluster.model.RouteCleanupResult;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCount;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.DrainGatewayCheckpoint;
import com.co.kc.imchat.broker.sdk.model.dto.DrainGatewayWatermarkDTO;
import com.co.kc.imchat.broker.sdk.model.params.DrainRouteCleanupParams;
import com.co.kc.imchat.broker.sdk.model.result.DrainRouteCleanupResult;

/** 排空操作与单次 cleanup RPC 载荷之间的转换，不执行清理或推进阶段。 */
public final class BrokerDrainTransformer {
    public static final BrokerDrainTransformer INSTANCE = new BrokerDrainTransformer();

    private BrokerDrainTransformer() {
    }

    /** 使用已提交操作的 epoch、旧事件期限和已确认 Gateway 水位构造远端清理请求。 */
    public DrainRouteCleanupParams cleanupParamsFrom(BrokerId caller, BrokerClusterOperation operation) {
        return new DrainRouteCleanupParams(caller.value(), operation.id().value(), operation.acceptedEpoch().value(),
                operation.resultEpoch().value(), operation.drainProgress().oldEventExpireAt().toEpochMilli(),
                operation.drainProgress().gateways().stream()
                        .filter(DrainGatewayCheckpoint::confirmed)
                        .map(gateway -> new DrainGatewayWatermarkDTO(gateway.gatewayId().value(),
                                gateway.confirmedRouteRevision().value(), gateway.confirmedRouteCount().value(),
                                gateway.contentChecksum().value()))
                        .toList());
    }

    /** 将已接受的清理响应转换为供应用流程持久化的汇总。 */
    public RouteCleanupResult routeCleanupResultFrom(DrainRouteCleanupResult result) {
        return new RouteCleanupResult(new RouteCount(result.removedOldRouteCount()),
                new RouteCount(result.residualOldRouteCount()), result.checksumsMatch());
    }

    /** 将本机已完成清理的领域结果转换为 RPC 响应，不表示整个排空操作已结束。 */
    public DrainRouteCleanupResult drainRouteCleanupResultFrom(RouteCleanupResult result) {
        return new DrainRouteCleanupResult(DrainRouteCleanupResult.Status.CLEANED,
                result.removedRouteCount().value(), result.residualRouteCount().value(), result.checksumsMatch(), null);
    }
}
