package com.co.kc.imchat.service.account.interfaces.http;

import com.co.kc.imchat.service.account.application.SessionAppService;
import com.co.kc.imchat.service.account.application.UserAppService;
import com.co.kc.imchat.service.account.model.cqrs.command.UserSignInCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.RefreshTokenCmd;
import com.co.kc.imchat.service.account.model.cqrs.dto.SignInDTO;
import com.co.kc.imchat.service.account.model.io.TokenPairResponse;
import com.co.kc.imchat.service.account.model.io.TokenRefreshRequest;
import com.co.kc.imchat.service.account.model.io.UserSignInRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {

    @Test
    void signInAndRefreshReturnTheSameTokenPairShape() {
        SessionAppService sessionAppService = mock(SessionAppService.class);
        UserController controller = new UserController(sessionAppService, mock(UserAppService.class));
        SignInDTO pair = new SignInDTO(
                42L,
                "access-token",
                Instant.parse("2026-08-14T12:00:00Z"),
                "refresh-token",
                Instant.parse("2026-09-13T10:00:00Z"));
        UserSignInRequest signInRequest = new UserSignInRequest();
        signInRequest.setEmail("user@example.com");
        signInRequest.setPassword("password");
        when(sessionAppService.signIn(new UserSignInCmd("user@example.com", "password"))).thenReturn(pair);
        when(sessionAppService.refreshToken(new RefreshTokenCmd("refresh-old"))).thenReturn(pair);

        TokenPairResponse signInResponse = controller.signIn(signInRequest);
        TokenPairResponse refreshResponse = controller.refreshToken(new TokenRefreshRequest("refresh-old"));

        assertThat(refreshResponse).isEqualTo(signInResponse);
        assertThat(refreshResponse.accessToken()).isEqualTo("access-token");
        assertThat(refreshResponse.refreshToken()).isEqualTo("refresh-token");
        verify(sessionAppService).refreshToken(new RefreshTokenCmd("refresh-old"));
    }
}
