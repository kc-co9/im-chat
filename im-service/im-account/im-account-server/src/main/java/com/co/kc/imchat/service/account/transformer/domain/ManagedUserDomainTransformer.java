package com.co.kc.imchat.service.account.transformer.domain;

import com.co.kc.imchat.service.account.domain.user.model.ManagedUser;
import com.co.kc.imchat.service.account.infrastructure.mybatis.entity.DbUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ManagedUserDomainTransformer {
    ManagedUserDomainTransformer INSTANCE = Mappers.getMapper(ManagedUserDomainTransformer.class);

    @Mapping(target = "userId.value", source = "userId")
    @Mapping(target = "pkId", source = "id")
    @Mapping(target = "username.value", source = "username")
    @Mapping(target = "email.value", source = "email")
    @Mapping(target = "password.value", source = "password")
    @Mapping(target = "deleted", source = "isDeleted")
    @Mapping(target = "createdAt", source = "createTime")
    @Mapping(target = "updatedAt", source = "updateTime")
    ManagedUser managedUserFrom(DbUser user);

    default Boolean deletedFrom(Long value) {
        return value != null && value != 0L;
    }

}
