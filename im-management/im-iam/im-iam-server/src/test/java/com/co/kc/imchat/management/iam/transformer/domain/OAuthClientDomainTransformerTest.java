package com.co.kc.imchat.management.iam.transformer.domain;

import com.co.kc.imchat.management.iam.domain.application.model.RedirectUri;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthClientDomainTransformerTest {

    @Test
    void convertsPersistentJsonToDomainValuesAndWritesDeterministicJson() {
        Set<RedirectUri> redirectUris = OAuthClientDomainTransformer.INSTANCE
                .redirectUrisFrom("[\"https://b.example/callback\",\"https://a.example/callback\"]");

        assertThat(redirectUris)
                .extracting(RedirectUri::value)
                .containsExactlyInAnyOrder(
                        URI.create("https://a.example/callback"),
                        URI.create("https://b.example/callback"));
        assertThat(OAuthClientDomainTransformer.INSTANCE.redirectUrisFrom(redirectUris))
                .isEqualTo("[\"https://a.example/callback\",\"https://b.example/callback\"]");
    }
}
