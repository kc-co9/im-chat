package com.co.kc.imchat.management.iam.transformer.application;

import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamPermissionCode;
import com.co.kc.imchat.management.iam.model.cqrs.dto.AuthenticatedAdministratorDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.AdministratorDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Set;

/**
 * IAM 管理员应用层转换器。
 */
@Mapper
public interface AdministratorAppTransformer {
    AdministratorAppTransformer INSTANCE = Mappers.getMapper(AdministratorAppTransformer.class);

    @Mapping(target = "administratorId", source = "administrator.id.value")
    @Mapping(target = "username", source = "administrator.username.value")
    @Mapping(target = "email", source = "administrator.email.value")
    @Mapping(target = "permissions", source = "permissions")
    AuthenticatedAdministratorDTO authenticatedAdministratorDtoFrom(Administrator administrator, Set<IamPermissionCode> permissions);

    default String permissionFrom(IamPermissionCode iamPermissionCode) {
        return iamPermissionCode.value();
    }

    @Mapping(target = "id", source = "id.value")
    @Mapping(target = "username", source = "username.value")
    @Mapping(target = "email", source = "email.value")
    AdministratorDTO iamAdministratorDtoFrom(Administrator administrator);
}
