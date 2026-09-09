package com.co.kc.imchat.management.monitor.infrastructure.config.beans;

import com.co.kc.imchat.management.iam.sdk.catalog.IamPermissionCatalog;
import com.co.kc.imchat.management.iam.sdk.catalog.IamPermissionDefinition;
import com.co.kc.imchat.management.monitor.domain.authorization.model.MonitorPermission;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.util.Arrays;
import java.util.List;

/**
 * Monitor IAM 权限目录与方法级授权配置。
 */
@Configuration
@EnableMethodSecurity
public class MonitorSecurityBeans {

    @Bean
    public IamPermissionCatalog monitorPermissionCatalog() {
        return new MonitorPermissionCatalog();
    }

    private static class MonitorPermissionCatalog implements IamPermissionCatalog {
        @Override
        public List<IamPermissionDefinition> permissions() {
            return Arrays.stream(MonitorPermission.values())
                    .map(permission -> new IamPermissionDefinition(
                            permission.getCode(),
                            permission.getDisplayName(),
                            permission.getDescription()))
                    .toList();
        }
    }
}
