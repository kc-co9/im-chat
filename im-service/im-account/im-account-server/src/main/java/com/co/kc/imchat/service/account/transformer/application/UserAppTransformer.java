package com.co.kc.imchat.service.account.transformer.application;

import com.co.kc.imchat.service.account.domain.user.model.User;
import com.co.kc.imchat.service.account.facade.dto.UserProfileDTO;
import com.co.kc.imchat.service.account.facade.dto.UserProfileFindDTO;
import com.co.kc.imchat.service.account.model.cqrs.dto.UserDetailDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface UserAppTransformer {
    UserAppTransformer INSTANCE = Mappers.getMapper(UserAppTransformer.class);

    @Mapping(target = "userId", source = "id.value")
    @Mapping(target = "email", source = "email.value")
    @Mapping(target = "username", source = "username.value")
    UserDetailDTO userDetailDtoFrom(User user);

    @Mapping(target = "userId", source = "id.value")
    @Mapping(target = "username", source = "username.value")
    @Mapping(target = "email", source = "email.value")
    UserProfileDTO userProfileDtoFrom(User user);

    List<UserProfileDTO> userProfileDtoListFrom(List<User> users);

    @Mapping(target = "found", expression = "java(true)")
    @Mapping(target = "userId", source = "id.value")
    @Mapping(target = "username", source = "username.value")
    @Mapping(target = "email", source = "email.value")
    UserProfileFindDTO userProfileFindDtoFrom(User user);
}
