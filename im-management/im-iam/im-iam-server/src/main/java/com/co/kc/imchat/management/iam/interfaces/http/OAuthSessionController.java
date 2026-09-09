package com.co.kc.imchat.management.iam.interfaces.http;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.application.OAuthSessionAppService;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthSessionRevokeCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthSessionDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthSessionPageQuery;
import com.co.kc.imchat.management.iam.model.io.OAuthSessionResponse;
import com.co.kc.imchat.management.iam.model.io.OAuthSessionRevokeRequest;
import com.co.kc.imchat.management.iam.transformer.interfaces.OAuthSessionHttpTransformer;
import com.co.kc.imchat.management.iam.support.security.RequiresPermission;
import com.co.kc.imchat.management.audit.sdk.annotation.Audited;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.co.kc.imchat.management.iam.support.security.IamPermission.Code.SESSION_READ;
import static com.co.kc.imchat.management.iam.support.security.IamPermission.Code.SESSION_REVOKE;

/** IAM OAuth 会话 HTTP 接口。 */
@RestController
@RequestMapping("/api/iam")
@RequiredArgsConstructor
public class OAuthSessionController {
    private final OAuthSessionAppService oauthSessionAppService;

    @GetMapping("/sessions/page")
    @RequiresPermission(SESSION_READ)
    public PagingResult<OAuthSessionResponse> sessions(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PagingResult<OAuthSessionDTO> page = oauthSessionAppService.page(
                new OAuthSessionPageQuery(new Paging(pageNo, pageSize)));
        return page.map(OAuthSessionHttpTransformer.INSTANCE::oauthSessionResponseFrom);
    }

    @PostMapping("/sessions/revoke")
    @RequiresPermission(SESSION_REVOKE)
    @Audited(type = AuditType.SECURITY, action = "OAUTH_SESSION_REVOKE",
            targetType = "OAUTH_SESSION", targetId = "#request.sessionId()",
            description = "撤销 IAM OAuth 会话")
    public void revokeSession(@RequestBody OAuthSessionRevokeRequest request) {
        OAuthSessionRevokeCmd command = new OAuthSessionRevokeCmd(request.sessionId());
        oauthSessionAppService.revoke(command);
    }
}
