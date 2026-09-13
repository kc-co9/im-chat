package com.co.kc.imchat.management.iam.transformer.domain;

import com.co.kc.imchat.management.iam.domain.authorization.model.IamPermissionCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamInternalRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** IAM 内部角色领域对象与持久化实体转换器。 */
@Mapper
public interface IamRoleDomainTransformer {
    IamRoleDomainTransformer INSTANCE = Mappers.getMapper(IamRoleDomainTransformer.class);

    @Mapping(target = "id.value", source = "roleId")
    @Mapping(target = "code.value", source = "code")
    @Mapping(target = "name.value", source = "name")
    IamRole roleFieldsFrom(DbIamInternalRole entity);

    default IamRole roleFrom(DbIamInternalRole entity) {
        if (entity == null) {
            return null;
        }
        IamRole role = roleFieldsFrom(entity);
        role.setPkId(entity.getId());
        role.setRowVersion(entity.getVersion());
        return role;
    }

    @Mapping(target = "id", source = "pkId")
    @Mapping(target = "version", source = "rowVersion")
    @Mapping(target = "roleId", source = "id.value")
    @Mapping(target = "code", source = "code.value")
    @Mapping(target = "name", source = "name.value")
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    DbIamInternalRole dbRoleFrom(IamRole role);

    default Set<IamPermissionCode> permissionsFrom(String permissions) {
        if (permissions == null || permissions.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(permissions.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(IamPermissionCode::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    default String permissionsFrom(Set<IamPermissionCode> permissions) {
        return permissions.stream()
                .map(IamPermissionCode::value)
                .sorted()
                .collect(Collectors.joining(","));
    }
}
