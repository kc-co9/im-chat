package com.co.kc.imchat.management.admin.interfaces.http;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedUserControllerTest {

    @Test
    void exposesManagedUserApiUnderUsersPath() {
        RequestMapping requestMapping =
                ManagedUserController.class.getAnnotation(RequestMapping.class);

        assertThat(requestMapping).isNotNull();
        assertThat(requestMapping.value()).containsExactly("/api/users");
    }
}
