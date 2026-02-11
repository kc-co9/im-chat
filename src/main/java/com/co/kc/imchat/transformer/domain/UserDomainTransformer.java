package com.co.kc.imchat.transformer.domain;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.session.Session;
import com.co.kc.imchat.domain.session.SessionStatus;
import com.co.kc.imchat.domain.user.User;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbUser;
import com.co.kc.imchat.model.cqrs.dto.user.SessionDTO;
import com.co.kc.imchat.model.enums.SessionStatusEnum;
import com.co.kc.imchat.support.utils.FunctionUtils;
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

    default Session sessionFrom(SessionDTO sessionDTO) {
        Session session = new Session(new UserId(sessionDTO.getUserId()));
        session.setChatId(FunctionUtils.mappingOrNull(sessionDTO.getChatId(), ImChatId::new));
        session.setStatus(INSTANCE.sessionStatusFrom(sessionDTO.getStatus()));
        session.setSignInTime(sessionDTO.getSignInTime());
        session.setSignOutTime(sessionDTO.getSignOutTime());
        return session;
    }

    SessionStatus sessionStatusFrom(SessionStatusEnum sessionStatusEnum);
}
