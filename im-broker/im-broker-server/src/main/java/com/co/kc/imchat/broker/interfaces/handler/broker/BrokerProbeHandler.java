package com.co.kc.imchat.broker.interfaces.handler.broker;

import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.BrokerProbeParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerProbeResult;
import com.co.kc.imchat.broker.transformer.interfaces.BrokerHealthObservationInterfaceTransformer;
import org.springframework.stereotype.Component;

/** 委托应用用例处理本轮观察，不持有观察窗口或多数判定。 */
@Component
public final class BrokerProbeHandler extends AbstractBrokerRpcHandler<BrokerProbeParams, BrokerProbeResult> {
    private final BrokerClusterAppService appService;

    public BrokerProbeHandler(BrokerClusterAppService appService) {
        super(BrokerBoltOperation.PROBE_BROKER);
        this.appService = appService;
    }

    @Override
    protected BrokerProbeResult process(BrokerProbeParams params) {
        return BrokerHealthObservationInterfaceTransformer.INSTANCE.probeResultFrom(
                appService.probeBroker(BrokerHealthObservationInterfaceTransformer.INSTANCE.probeQueryFrom(params)));
    }
}
