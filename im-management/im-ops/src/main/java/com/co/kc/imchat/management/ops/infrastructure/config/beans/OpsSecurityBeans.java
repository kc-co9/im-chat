package com.co.kc.imchat.management.ops.infrastructure.config.beans;

import com.co.kc.imchat.management.iam.sdk.catalog.IamPermissionCatalog;
import com.co.kc.imchat.management.iam.sdk.catalog.IamPermissionDefinition;
import com.co.kc.imchat.management.ops.domain.authorization.model.OpsPermission;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.util.Arrays;
import java.util.List;

/**
 * Ops IAM 权限目录与方法级授权配置。
 */
@Configuration
@EnableMethodSecurity
public class OpsSecurityBeans {

    @Bean
    public IamPermissionCatalog opsPermissionCatalog() {
        return new OpsPermissionCatalog();
    }

    private static class OpsPermissionCatalog implements IamPermissionCatalog {
        @Override
        public List<IamPermissionDefinition> permissions() {
            return Arrays.stream(OpsPermission.values())
                    .map(permission -> new IamPermissionDefinition(
                            permission.getCode(),
                            permission.getDisplayName(),
                            permission.getDescription()))
                    .toList();
        }
    }
}
