package com.co.kc.imchat.management.iam.transformer.domain;

import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleType;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamApplicationRoleType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Set;

/** IAM 应用角色与持久化实体转换器。 */
@Mapper
public interface ApplicationRoleDomainTransformer {
    ApplicationRoleDomainTransformer INSTANCE = Mappers.getMapper(ApplicationRoleDomainTransformer.class);

    @Mapping(target = "id.value", source = "role.roleId")
    @Mapping(target = "appId.value", source = "role.appId")
    @Mapping(target = "code.value", source = "role.code")
    @Mapping(target = "name.value", source = "role.name")
    @Mapping(target = "permissionIds", source = "permissionIds")
    ApplicationRole roleFrom(DbIamApplicationRole role, Set<ApplicationPermissionId> permissionIds);

    @Mapping(target = "id", source = "pkId")
    @Mapping(target = "roleId", source = "id.value")
    @Mapping(target = "appId", source = "appId.value")
    @Mapping(target = "code", source = "code.value")
    @Mapping(target = "name", source = "name.value")
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    DbIamApplicationRole dbRoleFrom(ApplicationRole role);

    DbIamApplicationRoleType dbRoleTypeFrom(ApplicationRoleType type);
}
