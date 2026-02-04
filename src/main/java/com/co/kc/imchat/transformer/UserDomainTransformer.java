package com.co.kc.imchat.transformer;

import com.co.kc.imchat.domain.user.User;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface UserDomainTransformer {
    UserDomainTransformer INSTANCE = Mappers.getMapper(UserDomainTransformer.class);

    List<User> userListFrom(List<DbUser> userList);

    @Mappings(value = {
            @Mapping(target = "id.value", source = "userId"),
            @Mapping(target = "email.value", source = "email"),
            @Mapping(target = "username.value", source = "username"),
            @Mapping(target = "password.value", source = "password")})
    User userFrom(DbUser dbUser);

}
