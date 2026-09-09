package com.co.kc.imchat.management.iam.transformer.application;

import com.co.kc.imchat.management.iam.domain.application.model.AppKey;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthScope;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClient;
import com.co.kc.imchat.management.iam.domain.application.model.RedirectUri;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthClientDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthClientListDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;

/** IAM OAuth 客户端应用层转换器。 */
@Mapper
public interface OAuthClientAppTransformer {
    OAuthClientAppTransformer INSTANCE = Mappers.getMapper(OAuthClientAppTransformer.class);

    @Mapping(target = "clientId", source = "clientId.value")
    @Mapping(target = "appId", source = "appId.value")
    @Mapping(target = "audienceAppId", source = "audienceAppId.value")
    @Mapping(target = "name", source = "name.value")
    OAuthClientDTO oauthClientDtoFrom(OAuthClient oauthClient);

    @Mapping(target = "clientId", source = "oauthClient.clientId.value")
    @Mapping(target = "appId", source = "oauthClient.appId.value")
    @Mapping(target = "audienceAppId", source = "oauthClient.audienceAppId.value")
    @Mapping(target = "audienceAppKey", source = "audienceAppKey.value")
    @Mapping(target = "name", source = "oauthClient.name.value")
    OAuthClientListDTO oauthClientListDtoFrom(
            OAuthClient oauthClient,
            AppKey audienceAppKey);

    default String scopeFrom(OAuthScope scope) {
        return scope.value();
    }

    default String redirectUriFrom(RedirectUri redirectUri) {
        return redirectUri.stringValue();
    }

    default Set<RedirectUri> redirectUrisFrom(Set<String> values) {
        return values.stream()
                .map(URI::create)
                .map(RedirectUri::new)
                .collect(Collectors.toUnmodifiableSet());
    }
}
