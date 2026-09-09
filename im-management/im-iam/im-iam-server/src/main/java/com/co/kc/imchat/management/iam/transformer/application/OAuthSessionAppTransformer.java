package com.co.kc.imchat.management.iam.transformer.application;

import com.co.kc.imchat.management.iam.domain.session.model.OAuthSession;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthSessionDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/** OAuth 授权会话应用层转换器。 */
@Mapper
public interface OAuthSessionAppTransformer {
    OAuthSessionAppTransformer INSTANCE =
            Mappers.getMapper(OAuthSessionAppTransformer.class);

    @Mapping(target = "id", source = "authorizationId.value")
    @Mapping(target = "administratorId", source = "administratorId.value")
    @Mapping(target = "username", source = "username.value")
    OAuthSessionDTO oauthSessionDtoFrom(OAuthSession session);
}
