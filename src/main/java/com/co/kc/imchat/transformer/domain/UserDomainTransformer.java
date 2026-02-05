package com.co.kc.imchat.transformer.domain;

import com.co.kc.imchat.domain.session.Session;
import com.co.kc.imchat.domain.user.User;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbUser;
import com.co.kc.imchat.model.cqrs.dto.user.SessionDTO;
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
            @Mapping(target = "incrId", source = "id"),
            @Mapping(target = "id.value", source = "userId"),
            @Mapping(target = "email.value", source = "email"),
            @Mapping(target = "username.value", source = "username"),
            @Mapping(target = "password.value", source = "password")})
    User userFrom(DbUser dbUser);

    @Mappings(value = {
            @Mapping(target = "userId.value", source = "userId"),
            @Mapping(target = "chatId.value", source = "chatId"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "signInTime", source = "signInTime"),
            @Mapping(target = "signOutTime", source = "signOutTime")
    })
    Session sessionFrom(SessionDTO sessionDTO);
}
