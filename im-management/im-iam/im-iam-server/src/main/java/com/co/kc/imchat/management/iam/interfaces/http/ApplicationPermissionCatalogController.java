package com.co.kc.imchat.management.iam.interfaces.http;

import com.co.kc.imchat.management.iam.application.ApplicationPermissionAppService;
import com.co.kc.imchat.management.iam.model.io.PermissionCatalogSyncRequest;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationPermissionCatalogSyncCmd;
import com.co.kc.imchat.management.audit.sdk.annotation.Audited;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import com.co.kc.imchat.management.iam.transformer.interfaces.PermissionHttpTransformer;
import com.co.kc.imchat.management.iam.support.security.IamClientContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理应用权限目录注册入口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/iam/permission-catalog")
public class ApplicationPermissionCatalogController {
    private final ApplicationPermissionAppService applicationPermissionAppService;

    @PostMapping("/synchronize")
    @PreAuthorize("hasAuthority('SCOPE_iam.catalog.write')")
    @Audited(type = AuditType.SECURITY, action = "PERMISSION_CATALOG_SYNC",
            targetType = "PERMISSION_CATALOG",
            description = "同步 IAM 权限目录")
    public void synchronize(
            @RequestBody PermissionCatalogSyncRequest request
    ) {
        ApplicationPermissionCatalogSyncCmd command =
                PermissionHttpTransformer.INSTANCE.applicationPermissionCatalogSyncCmdFrom(
                        IamClientContext.clientId(), request);
        applicationPermissionAppService.synchronize(command);
    }
}
