package com.co.kc.imchat.broker.interfaces.handler.broker;

import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.BrokerHealthReportParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerHealthReportResult;
import com.co.kc.imchat.broker.transformer.interfaces.BrokerHealthObservationInterfaceTransformer;
import org.springframework.stereotype.Component;

/** 委托应用用例处理本轮观察，不持有观察窗口或多数判定。 */
@Component
public final class BrokerHealthReportHandler extends AbstractBrokerRpcHandler<BrokerHealthReportParams, BrokerHealthReportResult> {
    private final BrokerClusterAppService appService;

    public BrokerHealthReportHandler(BrokerClusterAppService appService) {
        super(BrokerBoltOperation.FETCH_BROKER_HEALTH_REPORT);
        this.appService = appService;
    }

    @Override
    protected BrokerHealthReportResult process(BrokerHealthReportParams params) {
        return BrokerHealthObservationInterfaceTransformer.INSTANCE.reportResultFrom(
                appService.getHealthReport(BrokerHealthObservationInterfaceTransformer.INSTANCE.reportQueryFrom(params)));
    }
}
