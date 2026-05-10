package com.co.kc.imchat.transformer.db;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.co.kc.imchat.domain.user.User;
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
            @Mapping(target = "password", source = "password.value")})
    DbUser dbUserFrom(User user);

}
