package com.co.kc.imchat.management.iam.sdk.interfaces.http;

import com.co.kc.imchat.management.iam.sdk.oauth.IamAuthorizedSessionService;
import com.co.kc.imchat.management.iam.sdk.oauth.IamAuthorizationCompletion;
import com.co.kc.imchat.management.iam.sdk.security.IamCsrfTokenRepository;
import com.co.kc.imchat.management.iam.sdk.security.IamSecurityContext;
import com.co.kc.imchat.management.iam.sdk.security.IamSessionCookie;
import com.co.kc.imchat.management.iam.sdk.security.model.IamPrincipal;
import com.co.kc.imchat.management.iam.sdk.session.model.IamApplicationSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/** 管理应用共用的 OAuth2 BFF 登录、回调、主体与退出协议端点。 */
@RestController
@RequestMapping("/iam")
@RequiredArgsConstructor
public class IamBffController {
    private final IamAuthorizedSessionService sessionService;
    private final IamSessionCookie sessionCookie;
    private final IamCsrfTokenRepository csrfTokenRepository;

    @GetMapping("/login")
    public void login(
            @RequestParam(name = "continue", required = false) String continuePath,
            HttpServletResponse response
    ) throws IOException {
        response.sendRedirect(sessionService.beginAuthorization(continuePath).toString());
    }

    @GetMapping("/callback")
    public void callback(
            @RequestParam String state,
            @RequestParam String code,
            HttpServletResponse response
    ) throws IOException {
        IamAuthorizationCompletion completion =
                sessionService.completeAuthorization(state, code);
        IamApplicationSession session = completion.session();
        sessionCookie.write(session.sessionId(), response);
        csrfTokenRepository.publish(session.csrfToken(), response);
        response.sendRedirect(completion.continuePath());
    }

    @GetMapping("/me")
    public IamPrincipal currentPrincipal() {
        return IamSecurityContext.currentPrincipal()
                .orElseThrow(() -> new IamUnauthenticatedException("IAM Session is missing"));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        sessionCookie.read(request).ifPresent(sessionService::logout);
        sessionCookie.clear(response);
        csrfTokenRepository.clear(response);
    }

    @PostMapping("/platform-logout")
    public void platformLogout(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String sessionId = sessionCookie.read(request)
                .orElseThrow(() -> new IamUnauthenticatedException("IAM Session is missing"));
        java.net.URI logoutUri = sessionService.platformLogoutUri(sessionId);
        sessionCookie.clear(response);
        csrfTokenRepository.clear(response);
        response.sendRedirect(logoutUri.toString());
    }
}
