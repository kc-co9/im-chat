package com.co.kc.imchat.management.iam.transformer.domain;

import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamAdministrator;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/** IAM 管理员领域对象与持久化实体转换器。 */
@Mapper
public interface AdministratorDomainTransformer {
    AdministratorDomainTransformer INSTANCE =
            Mappers.getMapper(AdministratorDomainTransformer.class);

    @Mapping(target = "id.value", source = "administratorId")
    @Mapping(target = "username.value", source = "username")
    @Mapping(target = "email.value", source = "email")
    @Mapping(target = "password.value", source = "passwordHash")
    Administrator administratorFrom(DbIamAdministrator administrator);

    @Mapping(target = "id", source = "pkId")
    @Mapping(target = "administratorId", source = "id.value")
    @Mapping(target = "username", source = "username.value")
    @Mapping(target = "email", source = "email.value")
    @Mapping(target = "passwordHash", source = "password.value")
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    DbIamAdministrator dbAdministratorFrom(Administrator administrator);
}
