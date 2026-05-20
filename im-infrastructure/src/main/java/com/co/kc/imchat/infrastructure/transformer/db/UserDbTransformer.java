package com.co.kc.imchat.infrastructure.transformer.db;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.co.kc.imchat.domain.user.model.User;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbUser;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserDbTransformer {

    UserDbTransformer INSTANCE = Mappers.getMapper(UserDbTransformer.class);

    @Mappings(value = {
            @Mapping(target = "id", source = "pkId"),
            @Mapping(target = "userId", source = "id.value"),
            @Mapping(target = "email", source = "email.value"),
            @Mapping(target = "username", source = "username.value"),
            @Mapping(target = "password", source = "password.value"),
            @Mapping(target = "createTime", ignore = true),
            @Mapping(target = "updateTime", ignore = true),
            @Mapping(target = "isDeleted", ignore = true)})
    DbUser dbUserFrom(User user);

}
