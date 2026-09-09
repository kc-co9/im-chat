package com.co.kc.imchat.management.iam.transformer.interfaces;

import com.co.kc.imchat.management.iam.model.io.PermissionCatalogSyncRequest;
import com.co.kc.imchat.management.iam.model.io.PermissionDefinitionRequest;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationPermissionCatalogSyncCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.ApplicationPermissionDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.ApplicationPermissionDefinitionDTO;
import com.co.kc.imchat.management.iam.model.io.ApplicationPermissionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 权限 HTTP 请求转换器。
 */
@Mapper
public interface PermissionHttpTransformer {
    PermissionHttpTransformer INSTANCE =
            Mappers.getMapper(PermissionHttpTransformer.class);

    @Mapping(target = "clientId", source = "clientId")
    @Mapping(target = "permissions", source = "request.permissions")
    ApplicationPermissionCatalogSyncCmd applicationPermissionCatalogSyncCmdFrom(
            String clientId,
            PermissionCatalogSyncRequest request);

    ApplicationPermissionDefinitionDTO applicationPermissionDefinitionDtoFrom(
            PermissionDefinitionRequest request);

    ApplicationPermissionResponse applicationPermissionResponseFrom(
            ApplicationPermissionDTO permission);
}
