package com.co.kc.imchat.management.iam.sdk.security;

import com.co.kc.imchat.common.constant.HttpErrorCode;
import com.co.kc.imchat.common.model.io.HttpResult;
import com.co.kc.imchat.management.iam.sdk.interfaces.http.IamUnauthenticatedException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.AuthorizationResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IamSecurityExceptionHandlerTest {

    @Test
    void handlesMissingIamSessionAsAuthenticationFailure() {
        IamSecurityExceptionHandler handler = new IamSecurityExceptionHandler();

        HttpResult<?> result = handler.unauthenticated(
                new IamUnauthenticatedException("IAM Session is missing"));

        assertThat(result.getCode()).isEqualTo(HttpErrorCode.AUTH_FAIL.getCode());
    }

    @Test
    void mapsMethodAuthorizationFailureToPermissionDenied() {
        IamSecurityExceptionHandler handler = new IamSecurityExceptionHandler();
        AuthorizationDeniedException exception = new AuthorizationDeniedException(
                "denied", mock(AuthorizationResult.class));

        HttpResult<?> result = handler.authorizationDenied(exception);

        assertThat(result.getCode()).isEqualTo(HttpErrorCode.AUTH_DENY.getCode());
    }
}
