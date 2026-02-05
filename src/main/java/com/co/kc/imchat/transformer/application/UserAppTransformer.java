package com.co.kc.imchat.transformer.application;

import com.co.kc.imchat.domain.session.Session;
import com.co.kc.imchat.model.cqrs.dto.user.SessionDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserAppTransformer {
    UserAppTransformer INSTANCE = Mappers.getMapper(UserAppTransformer.class);

    @Mappings(value = {
            @Mapping(target = "userId", source = "userId.value"),
            @Mapping(target = "chatId", source = "chatId.value"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "signInTime", source = "signInTime"),
            @Mapping(target = "signOutTime", source = "signOutTime")
    })
    SessionDTO sessionDtoFrom(Session session);
}
