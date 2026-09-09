package com.co.kc.imchat.management.iam.sdk.catalog;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.concurrent.atomic.AtomicBoolean;

/** 在当前版本权限目录尚未同步时将应用 Readiness 标记为不就绪。 */
public class IamCatalogHealthIndicator implements HealthIndicator {
    private final AtomicBoolean synchronizedCatalog = new AtomicBoolean();

    @Override
    public Health health() {
        return synchronizedCatalog.get()
                ? Health.up().withDetail("catalog", "synchronized").build()
                : Health.down().withDetail("catalog", "not synchronized").build();
    }

    public void synchronizedSuccessfully() {
        synchronizedCatalog.set(true);
    }

    public void synchronizationFailed() {
        synchronizedCatalog.set(false);
    }
}
