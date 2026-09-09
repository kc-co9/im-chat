package com.co.kc.imchat.management.iam.domain.application.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationTest {

    @Test
    void ownsAppIdentityWithoutOAuthClientConfiguration() {
        Application app = new Application(
                new AppId(1L), new AppKey("imAdmin"),
                new AppName("IM 管理后台"), AppStatus.ACTIVE);

        assertThat(app.getAppId()).isEqualTo(new AppId(1L));
        assertThat(app.getAppKey()).isEqualTo(new AppKey("imAdmin"));
        assertThat(app.getName()).isEqualTo(new AppName("IM 管理后台"));
        assertThat(app.isActive()).isTrue();

        app.changeStatus(AppStatus.DISABLED);

        assertThat(app.isActive()).isFalse();
    }
}
