package com.co.kc.imchat.management.iam.transformer.interfaces;

import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthSessionDTO;
import com.co.kc.imchat.management.iam.model.io.OAuthSessionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

/** IAM OAuth 会话 HTTP 边界转换器。 */
@Mapper
public interface OAuthSessionHttpTransformer {
    OAuthSessionHttpTransformer INSTANCE = Mappers.getMapper(OAuthSessionHttpTransformer.class);

    OAuthSessionResponse oauthSessionResponseFrom(OAuthSessionDTO session);

    default Long epochMilliFrom(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}
