package com.co.kc.imchat.infrastructure.transformer.domain;

import com.co.kc.imchat.domain.chat.model.ImChatId;
import com.co.kc.imchat.domain.session.model.Session;
import com.co.kc.imchat.domain.session.model.SessionStatus;
import com.co.kc.imchat.domain.user.model.User;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.infrastructure.model.SessionDTO;
import com.co.kc.imchat.infrastructure.model.SessionStatusEnum;
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
            @Mapping(target = "pkId", source = "id"),
            @Mapping(target = "id.value", source = "userId"),
            @Mapping(target = "email.value", source = "email"),
            @Mapping(target = "username.value", source = "username"),
            @Mapping(target = "password.value", source = "password")})
    User userFrom(DbUser dbUser);

    @Mappings(value = {
            @Mapping(target = "userId", source = "userId.value"),
            @Mapping(target = "chatId", source = "chatId.value"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "signInTime", source = "signInTime"),
            @Mapping(target = "signOutTime", source = "signOutTime")
    })
    SessionDTO sessionDtoFrom(Session session);

    default Session sessionFrom(SessionDTO sessionDTO) {
        if (sessionDTO == null) {
            return null;
        }
        Session session = new Session(new UserId(sessionDTO.getUserId()));
        session.setChatId(FunctionUtils.mappingOrNull(sessionDTO.getChatId(), ImChatId::new));
        session.setStatus(FunctionUtils.mappingOrNull(sessionDTO.getStatus(), this::sessionStatusFrom));
        session.setSignInTime(sessionDTO.getSignInTime());
        session.setSignOutTime(sessionDTO.getSignOutTime());
        return session;
    }

    default SessionStatus sessionStatusFrom(SessionStatusEnum sessionStatusEnum) {
        return SessionStatus.valueOf(sessionStatusEnum.name());
    }

    default SessionStatusEnum sessionStatusEnumFrom(SessionStatus sessionStatus) {
        return SessionStatusEnum.valueOf(sessionStatus.name());
    }
}
