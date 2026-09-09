package com.co.kc.imchat.management.iam.transformer.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthScope;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClient;
import com.co.kc.imchat.management.iam.domain.application.model.RedirectUri;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamOAuthClient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** IAM OAuth 客户端领域对象与持久化实体转换器。 */
@Mapper
public interface OAuthClientDomainTransformer {
    OAuthClientDomainTransformer INSTANCE = Mappers.getMapper(OAuthClientDomainTransformer.class);

    @Mapping(target = "clientId.value", source = "oauthClientId")
    @Mapping(target = "appId.value", source = "appId")
    @Mapping(target = "audienceAppId.value", source = "audienceAppId")
    @Mapping(target = "name.value", source = "name")
    @Mapping(target = "clientSecret.value", source = "clientSecretHash")
    OAuthClient oauthClientFrom(DbIamOAuthClient oauthClient);

    @Mapping(target = "id", source = "pkId")
    @Mapping(target = "oauthClientId", source = "clientId.value")
    @Mapping(target = "appId", source = "appId.value")
    @Mapping(target = "audienceAppId", source = "audienceAppId.value")
    @Mapping(target = "name", source = "name.value")
    @Mapping(target = "clientSecretHash", source = "clientSecret.value")
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    DbIamOAuthClient dbOAuthClientFrom(OAuthClient oauthClient);

    default Set<OAuthScope> scopesFrom(String scopes) {
        return Arrays.stream(scopes.split(" "))
                .filter(scope -> !scope.isBlank())
                .map(OAuthScope::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    default String scopesFrom(Set<OAuthScope> scopes) {
        return scopes.stream()
                .map(OAuthScope::value)
                .sorted()
                .collect(Collectors.joining(" "));
    }

    default Set<OAuthGrantType> grantTypesFrom(String grantTypes) {
        return Arrays.stream(grantTypes.split(" "))
                .filter(grantType -> !grantType.isBlank())
                .map(OAuthGrantType::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    default String grantTypesFrom(Set<OAuthGrantType> grantTypes) {
        return grantTypes.stream()
                .map(OAuthGrantType::name)
                .sorted()
                .collect(Collectors.joining(" "));
    }

    default Set<RedirectUri> redirectUrisFrom(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        List<String> values = JsonUtils.fromJson(json, new TypeReference<>() {
        });
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
                .map(URI::create)
                .map(RedirectUri::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    default String redirectUrisFrom(Set<RedirectUri> redirectUris) {
        List<String> values = redirectUris.stream()
                .map(RedirectUri::stringValue)
                .sorted()
                .toList();
        return JsonUtils.toJson(values);
    }
}
