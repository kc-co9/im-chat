package com.co.kc.imchat.service.account.transformer.db;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.co.kc.imchat.service.account.domain.user.model.User;
import com.co.kc.imchat.service.account.domain.user.model.ManagedUser;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.service.account.domain.user.model.UserStatus;
import com.co.kc.imchat.service.account.domain.user.model.UserQueryCondition;
import com.co.kc.imchat.service.account.infrastructure.mybatis.entity.DbUser;
import com.co.kc.imchat.service.account.infrastructure.mybatis.enums.DbUserStatus;
import com.co.kc.imchat.service.account.infrastructure.mybatis.query.DbUserQueryCondition;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.Optional;

@Mapper
public interface UserDbTransformer {

    UserDbTransformer INSTANCE = Mappers.getMapper(UserDbTransformer.class);

    @Mapping(target = "id", source = "pkId")
    @Mapping(target = "userId", source = "id.value")
    @Mapping(target = "email", source = "email.value")
    @Mapping(target = "username", source = "username.value")
    @Mapping(target = "password", source = "password.value")
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    DbUser dbUserFrom(User user);

    @Mapping(target = "id", source = "pkId")
    @Mapping(target = "userId", source = "userId.value")
    @Mapping(target = "email", source = "email.value")
    @Mapping(target = "username", source = "username.value")
    @Mapping(target = "password", source = "password.value")
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    DbUser dbUserFrom(ManagedUser managedUser);

    DbUserStatus dbUserStatusFrom(UserStatus status);

    @Mapping(target = "userId", source = "userId", qualifiedByName = "userIdValue")
    @Mapping(target = "username", source = "username", qualifiedByName = "userNameValue")
    @Mapping(target = "email", source = "email", qualifiedByName = "userEmailValue")
    @Mapping(target = "status", source = "status", qualifiedByName = "dbUserStatusValue")
    DbUserQueryCondition dbUserQueryConditionFrom(UserQueryCondition condition);

    @Named("userIdValue")
    default Optional<Long> userIdValue(Optional<UserId> value) {
        return value.map(UserId::value);
    }

    @Named("userNameValue")
    default Optional<String> userNameValue(Optional<UserName> value) {
        return value.map(UserName::value);
    }

    @Named("userEmailValue")
    default Optional<String> userEmailValue(Optional<UserEmail> value) {
        return value.map(UserEmail::value);
    }

    @Named("dbUserStatusValue")
    default Optional<DbUserStatus> dbUserStatusValue(Optional<UserStatus> value) {
        return value.map(this::dbUserStatusFrom);
    }
}
