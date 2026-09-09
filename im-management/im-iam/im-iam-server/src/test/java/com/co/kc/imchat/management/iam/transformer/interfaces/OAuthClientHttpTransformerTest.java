package com.co.kc.imchat.management.iam.transformer.interfaces;

import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientStatus;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthClientListDTO;
import com.co.kc.imchat.management.iam.model.enums.IamOAuthGrantTypeEnum;
import com.co.kc.imchat.management.iam.model.io.OAuthClientListResponse;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthClientHttpTransformerTest {

    @Test
    void mapsCompleteClientListResponseWithoutSensitiveFields() {
        OAuthClientListDTO client = new OAuthClientListDTO(
                "im-admin-web",
                9_007_199_254_740_993L,
                9_007_199_254_740_994L,
                "imAdmin",
                "IM Admin Browser",
                Set.of(OAuthGrantType.AUTHORIZATION_CODE, OAuthGrantType.REFRESH_TOKEN),
                Set.of("openid", "profile"),
                Set.of("https://admin.example.com/login/callback"),
                Set.of("https://admin.example.com/"),
                OAuthClientStatus.ACTIVE);

        OAuthClientListResponse response =
                OAuthClientHttpTransformer.INSTANCE.listResponseFrom(client);

        assertThat(response).isEqualTo(new OAuthClientListResponse(
                "im-admin-web",
                9_007_199_254_740_993L,
                9_007_199_254_740_994L,
                "imAdmin",
                "IM Admin Browser",
                Set.of(
                        IamOAuthGrantTypeEnum.AUTHORIZATION_CODE,
                        IamOAuthGrantTypeEnum.REFRESH_TOKEN),
                Set.of("openid", "profile"),
                Set.of("https://admin.example.com/login/callback"),
                Set.of("https://admin.example.com/"),
                "ACTIVE"));
    }
}
