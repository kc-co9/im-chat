package com.co.kc.imchat.management.audit.infrastructure.config;

import com.co.kc.imchat.management.iam.sdk.catalog.IamPermissionCatalog;
import com.co.kc.imchat.management.iam.sdk.catalog.IamPermissionDefinition;

import java.util.List;

/** Audit 向 IAM 注册的权限目录全量快照。 */
public class AuditPermissionCatalog implements IamPermissionCatalog {
    @Override
    public List<IamPermissionDefinition> permissions() {
        return List.of(
                new IamPermissionDefinition(
                        "audit:read",
                        "查看集中审计",
                        "查看 BUSINESS 与 SECURITY 审计列表和详情"),
                new IamPermissionDefinition(
                        "audit:export",
                        "导出集中审计",
                        "按受限时间范围导出审计记录"));
    }
}
