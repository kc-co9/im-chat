package com.co.kc.imchat.broker.interfaces.scheduler;

import com.co.kc.imchat.broker.application.local.LocalGatewayCleanupAppService;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;

/**
 * 定时触发本机 Gateway 租约过期清理，不负责删除顺序或 Gossip 失败重试判断。
 */
public final class GatewayCleanupScheduler {
    private final LocalGatewayCleanupAppService cleanupService;

    public GatewayCleanupScheduler(LocalGatewayCleanupAppService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(fixedDelayString = "${im.broker.routing.gateway-cleanup-delay-millis:30000}")
    public void cleanup() {
        cleanupService.cleanupExpired(Instant.now());
    }
}
