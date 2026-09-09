package com.co.kc.imchat.service.account.transformer.application;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.account.domain.session.model.AccessCredential;
import com.co.kc.imchat.service.account.domain.session.model.CredentialPair;
import com.co.kc.imchat.service.account.facade.dto.SessionAuthDTO;
import com.co.kc.imchat.service.account.model.cqrs.dto.SignInDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface SessionAppTransformer {
    SessionAppTransformer INSTANCE = Mappers.getMapper(SessionAppTransformer.class);

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
}
