package com.co.kc.imchat.service.account.transformer.domain;

import com.co.kc.imchat.service.account.domain.session.model.Session;
import com.co.kc.imchat.service.account.domain.session.model.SessionStatus;
import com.co.kc.imchat.service.account.domain.session.model.RefreshFingerprint;
import com.co.kc.imchat.service.account.domain.session.model.SessionVersion;
import com.co.kc.imchat.service.account.domain.user.model.User;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.service.account.model.cqrs.dto.SessionDTO;
import com.co.kc.imchat.service.account.model.enums.SessionStatusEnum;
import com.co.kc.imchat.service.account.infrastructure.mybatis.entity.DbUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface UserDomainTransformer {
    UserDomainTransformer INSTANCE = Mappers.getMapper(UserDomainTransformer.class);

    List<User> userListFrom(List<DbUser> userList);

    @Mapping(target = "pkId", source = "id")
    @Mapping(target = "id.value", source = "userId")
    @Mapping(target = "email.value", source = "email")
    @Mapping(target = "username.value", source = "username")
    @Mapping(target = "password.value", source = "password")
    User userFrom(DbUser dbUser);

    @Mapping(target = "userId", source = "userId.value")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "signInTime", source = "signInTime")
    @Mapping(target = "signOutTime", source = "signOutTime")
    @Mapping(target = "sessionVersion", source = "sessionVersion.value")
    @Mapping(target = "refreshFingerprint", source = "refreshFingerprint.value")
    SessionDTO sessionDtoFrom(Session session);

    SessionStatus sessionStatusFrom(SessionStatusEnum sessionStatusEnum);

    SessionStatusEnum sessionStatusEnumFrom(SessionStatus sessionStatus);

    default Session sessionFrom(SessionDTO sessionDTO) {
        if (sessionDTO == null) {
            return null;
        }
        return Session.builder()
                .userId(new UserId(sessionDTO.getUserId()))
                .status(FunctionUtils.mappingOrNull(sessionDTO.getStatus(), this::sessionStatusFrom))
                .signInTime(sessionDTO.getSignInTime())
                .signOutTime(sessionDTO.getSignOutTime())
                .sessionVersion(FunctionUtils.mappingOrNull(sessionDTO.getSessionVersion(), SessionVersion::new))
                .refreshFingerprint(FunctionUtils.mappingOrNull(sessionDTO.getRefreshFingerprint(), RefreshFingerprint::new))
                .refreshTokenExpiresAt(sessionDTO.getRefreshTokenExpiresAt())
                .build();
    }

}
