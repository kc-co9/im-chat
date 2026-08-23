package com.co.kc.imchat.service.account.transformer.application;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.account.domain.session.model.AccessCredential;
import com.co.kc.imchat.service.account.domain.session.model.CredentialPair;
import com.co.kc.imchat.service.account.domain.user.model.User;
import com.co.kc.imchat.service.account.facade.dto.UserProfileDTO;
import com.co.kc.imchat.service.account.facade.dto.UserProfileFindDTO;
import com.co.kc.imchat.service.account.facade.dto.SessionAuthDTO;
import com.co.kc.imchat.service.account.model.cqrs.dto.UserDetailDTO;
import com.co.kc.imchat.service.account.model.cqrs.dto.SignInDTO;
import com.co.kc.imchat.service.account.model.io.TokenPairResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface AccountAppTransformer {
    AccountAppTransformer INSTANCE = Mappers.getMapper(AccountAppTransformer.class);

    @Mapping(target = "userId", source = "id.value")
    @Mapping(target = "email", source = "email.value")
    @Mapping(target = "username", source = "username.value")
    UserDetailDTO userDetailDtoFrom(User user);

    @Mapping(target = "userId", source = "id.value")
    @Mapping(target = "username", source = "username.value")
    @Mapping(target = "email", source = "email.value")
    UserProfileDTO userProfileDtoFrom(User user);

    @Mapping(target = "found", expression = "java(true)")
    @Mapping(target = "userId", source = "id.value")
    @Mapping(target = "username", source = "username.value")
    @Mapping(target = "email", source = "email.value")
    UserProfileFindDTO userProfileFindDtoFrom(User user);

    @Mapping(target = "userId", source = "userId.value")
    @Mapping(target = "accessToken", source = "credentials.access.token.value")
    @Mapping(target = "accessTokenExpiresAt", source = "credentials.access.expiresAt")
    @Mapping(target = "refreshToken", source = "credentials.refresh.token.value")
    @Mapping(target = "refreshTokenExpiresAt", source = "credentials.refresh.expiresAt")
    SignInDTO signInDtoFrom(UserId userId, CredentialPair credentials);

    @Mapping(target = "userId", source = "userId.value")
    @Mapping(target = "sessionVersion", source = "sessionVersion.value")
    @Mapping(target = "accessTokenExpiresAt", source = "expiresAt")
    SessionAuthDTO sessionAuthDtoFrom(AccessCredential credential);

    TokenPairResponse tokenPairResponseFrom(SignInDTO signInDTO);

}
