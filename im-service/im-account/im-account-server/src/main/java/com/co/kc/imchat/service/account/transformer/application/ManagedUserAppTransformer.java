package com.co.kc.imchat.service.account.transformer.application;

import com.co.kc.imchat.service.account.domain.user.model.ManagedUser;
import com.co.kc.imchat.service.account.model.cqrs.dto.ManagedUserDTO;
import com.co.kc.imchat.service.account.model.cqrs.dto.ManagedUserListDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ManagedUserAppTransformer {
    ManagedUserAppTransformer INSTANCE = Mappers.getMapper(ManagedUserAppTransformer.class);

    @Mapping(target = "userId", source = "userId.value")
    @Mapping(target = "username", source = "username.value")
    @Mapping(target = "email", source = "email.value")
    ManagedUserDTO managedUserDtoFrom(ManagedUser managedUser);

    @Mapping(target = "userId", source = "userId.value")
    @Mapping(target = "username", source = "username.value")
    @Mapping(target = "email", source = "email.value")
    ManagedUserListDTO managedUserListDtoFrom(ManagedUser managedUser);
}
