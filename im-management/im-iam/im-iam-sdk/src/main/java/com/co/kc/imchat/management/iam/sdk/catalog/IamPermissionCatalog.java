package com.co.kc.imchat.management.iam.sdk.catalog;

import java.util.List;

/** 当前管理应用拥有的权限全量快照。 */
public interface IamPermissionCatalog {
    List<IamPermissionDefinition> permissions();
}
