package com.co.kc.imchat.management.iam.interfaces.http;

import com.co.kc.imchat.management.iam.model.io.IamPrincipalResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IamPrincipalControllerTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exposesCurrentAdministratorAndAuthorities() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "1001",
                        null,
                        List.of(new SimpleGrantedAuthority("iam:application:read"))));
        IamPrincipalController controller = new IamPrincipalController();

        IamPrincipalResponse response = controller.principal();

        assertThat(response.administratorId()).isEqualTo(1001L);
        assertThat(response.authorities()).containsExactly("iam:application:read");
    }
}
