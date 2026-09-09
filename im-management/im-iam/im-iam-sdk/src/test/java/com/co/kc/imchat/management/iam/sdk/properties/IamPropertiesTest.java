package com.co.kc.imchat.management.iam.sdk.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IamPropertiesTest {

    @Test
    void bindsTheCanonicalHierarchy() {
        Map<String, Object> values = canonicalValues();
        values.put("im.iam.application.clients.catalog.client-id", "im-admin-catalog");
        values.put("im.iam.application.clients.catalog.client-secret", "catalog-secret");

        IamProperties properties = bind(values);

        assertThat(properties.issuer()).isEqualTo(URI.create("https://iam.example.com"));
        assertThat(properties.application().key()).isEqualTo("imAdmin");
        assertThat(properties.application().clients().web().clientId())
                .isEqualTo("im-admin-client");
        assertThat(properties.application().clients().catalog().configured()).isTrue();
        assertThat(properties.application().session().encryptionKeyBytes()).hasSize(32);
    }

    @Test
    void preservesSecurityAndTimeoutDefaultsAcrossTheNewHierarchy() {
        IamProperties properties = properties(
                Base64.getEncoder().encodeToString(new byte[32]),
                null,
                new IamProperties.CatalogClient(null, null));

        assertThat(properties.application().session().cookieName())
                .isEqualTo("IM_IAM_SESSION");
        assertThat(properties.application().session().secureCookieEnabled()).isTrue();
        assertThat(properties.application().session().sameSite()).isEqualTo("Lax");
        assertThat(properties.application().session().ttl()).isEqualTo(Duration.ofHours(8));
        assertThat(properties.http().connectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.http().readTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.introspection().staleTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void validatesSessionAndIntrospectionSecurityBoundaries() {
        assertThatThrownBy(() -> properties(
                Base64.getEncoder().encodeToString(new byte[16]),
                null,
                new IamProperties.CatalogClient(null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> properties(
                Base64.getEncoder().encodeToString(new byte[32]),
                Duration.ofMinutes(6),
                new IamProperties.CatalogClient(null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("five minutes");
    }

    @Test
    void requiresHttpsOutsideLoopbackDevelopment() {
        IamProperties.Application application = properties(
                Base64.getEncoder().encodeToString(new byte[32]),
                null,
                new IamProperties.CatalogClient(null, null))
                .application();

        assertThatThrownBy(() -> new IamProperties(
                true,
                URI.create("http://iam.example.com"),
                application,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> new IamProperties(
                true,
                URI.create("/iam"),
                application,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
        assertThat(new IamProperties(
                true,
                URI.create("http://localhost:18092"),
                application,
                null,
                null).issuer())
                .isEqualTo(URI.create("http://localhost:18092"));
    }

    @Test
    void doesNotExposeClientOrSessionSecretsThroughRecordStrings() {
        IamProperties properties = properties(
                Base64.getEncoder().encodeToString(new byte[32]),
                null,
                new IamProperties.CatalogClient(
                        "im-audit-catalog",
                        "catalog-secret"));

        assertThat(properties.toString())
                .doesNotContain("client-secret")
                .doesNotContain("catalog-secret")
                .doesNotContain(properties.application().session().encryptionKey());
    }

    @Test
    void rejectsMissingProviderApplicationClientAndSessionConfiguration() {
        IamProperties properties = properties(
                Base64.getEncoder().encodeToString(new byte[32]),
                null,
                new IamProperties.CatalogClient(null, null));
        IamProperties.Application application = properties.application();

        assertThatThrownBy(() -> new IamProperties(
                true, null, application, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issuer");
        assertThatThrownBy(() -> new IamProperties(
                true, properties.issuer(), null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("application");
        assertThatThrownBy(() -> new IamProperties.Application(
                " ", application.clients(), application.session()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key");
        assertThatThrownBy(() -> new IamProperties.Clients(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("web client");
        assertThatThrownBy(() -> new IamProperties.Application(
                application.key(), application.clients(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("session");
    }

    @Test
    void binderRejectsEveryMissingMandatorySetting() {
        List<String> requiredSettings = List.of(
                "im.iam.issuer",
                "im.iam.application.key",
                "im.iam.application.clients.web.client-id",
                "im.iam.application.clients.web.client-secret",
                "im.iam.application.clients.web.redirect-uri",
                "im.iam.application.clients.web.post-logout-redirect-uri",
                "im.iam.application.session.encryption-key");

        for (String requiredSetting : requiredSettings) {
            Map<String, Object> values = canonicalValues();
            values.remove(requiredSetting);

            assertThatThrownBy(() -> bind(values))
                    .as("missing property %s", requiredSetting)
                    .isInstanceOf(BindException.class);
        }
    }

    @Test
    void acceptsAbsentOrCompleteCatalogCredentialsAndRejectsPartialCredentials() {
        IamProperties.CatalogClient absent = new IamProperties.CatalogClient(null, null);
        IamProperties.CatalogClient complete =
                new IamProperties.CatalogClient("im-audit-catalog", "catalog-secret");
        IamProperties.Clients clients = new IamProperties.Clients(
                properties(
                        Base64.getEncoder().encodeToString(new byte[32]),
                        null,
                        absent)
                        .application()
                        .clients()
                        .web(),
                null);

        assertThat(absent.configured()).isFalse();
        assertThat(complete.configured()).isTrue();
        assertThat(clients.catalog().configured()).isFalse();
        assertThatThrownBy(() -> new IamProperties.CatalogClient("im-audit-catalog", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both");
    }

    @Test
    void binderAcceptsAbsentCatalogAndRejectsEitherPartialCredential() {
        assertThat(bind(canonicalValues()).application().clients().catalog().configured())
                .isFalse();

        Map<String, Object> clientIdOnly = canonicalValues();
        clientIdOnly.put(
                "im.iam.application.clients.catalog.client-id",
                "im-admin-catalog");
        assertThatThrownBy(() -> bind(clientIdOnly))
                .isInstanceOf(BindException.class);

        Map<String, Object> clientSecretOnly = canonicalValues();
        clientSecretOnly.put(
                "im.iam.application.clients.catalog.client-secret",
                "catalog-secret");
        assertThatThrownBy(() -> bind(clientSecretOnly))
                .isInstanceOf(BindException.class);
    }

    private static IamProperties properties(
            String encryptionKey,
            Duration staleTtl,
            IamProperties.CatalogClient catalog
    ) {
        IamProperties.WebClient web = new IamProperties.WebClient(
                "im-admin-client",
                "client-secret",
                URI.create("https://admin.example.com/iam/callback"),
                URI.create("https://admin.example.com/"));
        IamProperties.Session session =
                new IamProperties.Session(encryptionKey, null, null, null, null);
        IamProperties.Application application = new IamProperties.Application(
                "imAdmin",
                new IamProperties.Clients(web, catalog),
                session);
        return new IamProperties(
                true,
                URI.create("https://iam.example.com"),
                application,
                null,
                new IamProperties.Introspection(staleTtl));
    }

    private static Map<String, Object> canonicalValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("im.iam.enabled", true);
        values.put("im.iam.issuer", "https://iam.example.com");
        values.put("im.iam.application.key", "imAdmin");
        values.put("im.iam.application.clients.web.client-id", "im-admin-client");
        values.put("im.iam.application.clients.web.client-secret", "client-secret");
        values.put(
                "im.iam.application.clients.web.redirect-uri",
                "https://admin.example.com/iam/callback");
        values.put(
                "im.iam.application.clients.web.post-logout-redirect-uri",
                "https://admin.example.com/");
        values.put(
                "im.iam.application.session.encryption-key",
                Base64.getEncoder().encodeToString(new byte[32]));
        return values;
    }

    private static IamProperties bind(Map<String, Object> values) {
        Binder binder = new Binder(new MapConfigurationPropertySource(values));
        return binder.bind("im.iam", Bindable.of(IamProperties.class))
                .orElseThrow(() -> new AssertionError("IAM configuration is missing"));
    }
}
