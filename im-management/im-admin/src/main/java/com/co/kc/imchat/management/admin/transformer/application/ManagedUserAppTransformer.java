package com.co.kc.imchat.management.admin.transformer.application;

import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUser;
import com.co.kc.imchat.management.admin.model.cqrs.dto.ManagedUserDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ManagedUserAppTransformer {
    ManagedUserAppTransformer INSTANCE = Mappers.getMapper(ManagedUserAppTransformer.class);

    @Mapping(target = "id", source = "id.value")
    @Mapping(target = "username", source = "username.value")
    @Mapping(target = "email", source = "email.value")
    ManagedUserDTO managedUserDtoFrom(ManagedUser user);

}
