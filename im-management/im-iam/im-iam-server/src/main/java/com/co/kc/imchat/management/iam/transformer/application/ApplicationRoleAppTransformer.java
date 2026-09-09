package com.co.kc.imchat.management.iam.transformer.application;

import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRole;
import com.co.kc.imchat.management.iam.model.cqrs.dto.ApplicationRoleDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ApplicationRoleAppTransformer {
    ApplicationRoleAppTransformer INSTANCE = Mappers.getMapper(ApplicationRoleAppTransformer.class);

    @Mapping(target = "id", source = "role.id.value")
    @Mapping(target = "code", source = "role.code.value")
    @Mapping(target = "name", source = "role.name.value")
    @Mapping(target = "status", source = "role.status")
    ApplicationRoleDTO applicationRoleDtoFrom(ApplicationRole role);

    default Long longFrom(ApplicationPermissionId permissionId) {
        return permissionId.value();
    }
}
