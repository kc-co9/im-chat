package com.co.kc.imchat.broker.interfaces.scheduler;

import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 触发健康观察用例；实际在途闸门由应用拥有，入口不以外层超时释放。
 */
@Slf4j
@RequiredArgsConstructor
public final class BrokerFailureProbeScheduler {
    private final BrokerClusterAppService service;

    @Scheduled(fixedDelayString = "${im.broker.failure-observation.probe-interval:1s}")
    public void probe() {
        try {
            service.probeFailures().whenComplete((ignored, failure) -> {
                if (failure != null) {
                    log.warn("Broker observation round failed", failure);
                }
            });
        } catch (RuntimeException failure) {
            log.warn("Broker observation round could not start", failure);
        }
    }
}
