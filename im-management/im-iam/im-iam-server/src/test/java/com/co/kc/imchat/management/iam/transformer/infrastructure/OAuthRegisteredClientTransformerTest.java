package com.co.kc.imchat.management.iam.transformer.infrastructure;

import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthRegisteredClientTransformerTest {

    @Test
    void mapsEveryOAuthGrantTypeToSpring() {
        assertThat(OAuthRegisteredClientTransformer.INSTANCE
                .authorizationGrantTypeFrom(OAuthGrantType.AUTHORIZATION_CODE))
                .isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(OAuthRegisteredClientTransformer.INSTANCE
                .authorizationGrantTypeFrom(OAuthGrantType.REFRESH_TOKEN))
                .isEqualTo(AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(OAuthRegisteredClientTransformer.INSTANCE
                .authorizationGrantTypeFrom(OAuthGrantType.CLIENT_CREDENTIALS))
                .isEqualTo(AuthorizationGrantType.CLIENT_CREDENTIALS);
    }
}
