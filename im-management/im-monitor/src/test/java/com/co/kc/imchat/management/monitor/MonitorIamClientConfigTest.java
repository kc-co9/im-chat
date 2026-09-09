package com.co.kc.imchat.management.monitor;

import com.co.kc.imchat.management.iam.sdk.properties.IamProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorIamClientConfigTest {

    @Test
    void providesUsableLocalIamClientDefaults() throws Exception {
        IamProperties properties = properties();
        IamProperties.WebClient webClient = properties.application().clients().web();
        IamProperties.Session session = properties.application().session();

        assertThat(properties.issuer()).isEqualTo(URI.create("http://localhost:18090"));
        assertThat(properties.application().key()).isEqualTo("imMonitor");
        assertThat(webClient.redirectUri())
                .isEqualTo(URI.create("http://localhost:18092/iam/callback"));
        assertThat(webClient.postLogoutRedirectUri())
                .isEqualTo(URI.create("http://localhost:18092/"));
        assertThat(webClient.clientSecret()).hasSize(48);
        assertThat(properties.application().clients().catalog().clientSecret()).hasSize(48);
        assertThat(session.encryptionKeyBytes()).hasSize(32);
        assertThat(session.secureCookieEnabled()).isFalse();
    }

    private IamProperties properties() throws Exception {
        MutablePropertySources sources = new MutablePropertySources();
        new YamlPropertySourceLoader().load(
                "application.yml",
                new ClassPathResource("application.yml"))
                .forEach(sources::addLast);
        return new Binder(ConfigurationPropertySources.from(sources))
                .bind("im.iam", Bindable.of(IamProperties.class))
                .orElseThrow(() -> new AssertionError("IAM configuration is missing"));
    }
}
