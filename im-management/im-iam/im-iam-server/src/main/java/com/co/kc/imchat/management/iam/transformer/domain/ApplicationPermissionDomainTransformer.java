package com.co.kc.imchat.management.iam.transformer.domain;

import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermission;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationPermission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/** IAM 权限目录项与持久化实体转换器。 */
@Mapper
public interface ApplicationPermissionDomainTransformer {
    ApplicationPermissionDomainTransformer INSTANCE = Mappers.getMapper(ApplicationPermissionDomainTransformer.class);

    @Mapping(target = "id.value", source = "permissionId")
    @Mapping(target = "appId.value", source = "appId")
    @Mapping(target = "code.value", source = "code")
    @Mapping(target = "name.value", source = "name")
    @Mapping(target = "description.value", source = "description")
    ApplicationPermission permissionFieldsFrom(DbIamApplicationPermission permission);

    default ApplicationPermission permissionFrom(DbIamApplicationPermission entity) {
        if (entity == null) {
            return null;
        }
        ApplicationPermission permission = permissionFieldsFrom(entity);
        permission.setPkId(entity.getId());
        permission.setRowVersion(entity.getVersion());
        return permission;
    }

    @Mapping(target = "id", source = "pkId")
    @Mapping(target = "version", source = "rowVersion")
    @Mapping(target = "permissionId", source = "id.value")
    @Mapping(target = "appId", source = "appId.value")
    @Mapping(target = "code", source = "code.value")
    @Mapping(target = "name", source = "name.value")
    @Mapping(target = "description", source = "description.value")
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    DbIamApplicationPermission dbPermissionFrom(ApplicationPermission permission);
}
