package com.co.kc.imchat.management.iam.interfaces.http;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.application.OAuthClientAppService;
import com.co.kc.imchat.management.iam.model.cqrs.command.*;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthClientDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthClientListDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthClientPageQuery;
import com.co.kc.imchat.management.iam.model.io.*;
import com.co.kc.imchat.management.iam.transformer.interfaces.OAuthClientHttpTransformer;
import com.co.kc.imchat.management.iam.support.security.RequiresPermission;
import com.co.kc.imchat.management.audit.sdk.annotation.Audited;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import static com.co.kc.imchat.management.iam.support.security.IamPermission.Code.CLIENT_WRITE;
import static com.co.kc.imchat.management.iam.support.security.IamPermission.Code.APPLICATION_READ;

/**
 * IAM OAuth 客户端 HTTP 接口。
 */
@RestController
@RequestMapping("/api/iam")
@RequiredArgsConstructor
public class OAuthClientController {
    private final OAuthClientAppService oauthClientAppService;

    @GetMapping("/oauth-clients/page")
    @RequiresPermission(APPLICATION_READ)
    public PagingResult<OAuthClientListResponse> oauthClients(
            @RequestParam Long appId,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        OAuthClientPageQuery query = new OAuthClientPageQuery(
                appId,
                new Paging(pageNo, pageSize));
        PagingResult<OAuthClientListDTO> page = oauthClientAppService.page(query);
        return page.map(OAuthClientHttpTransformer.INSTANCE::listResponseFrom);
    }

    @PostMapping("/oauth-clients/create")
    @RequiresPermission(CLIENT_WRITE)
    @Audited(type = AuditType.SECURITY, action = "OAUTH_CLIENT_REGISTER", targetType = "OAUTH_CLIENT",
            targetId = "#request.clientId()", description = "注册 IAM OAuth 客户端")
    public OAuthClientResponse registerOAuthClient(@RequestBody OAuthClientRegisterRequest request) {
        OAuthClientRegisterCmd command = OAuthClientHttpTransformer.INSTANCE.registerCommandFrom(request);
        OAuthClientDTO client = oauthClientAppService.register(command);
        return OAuthClientHttpTransformer.INSTANCE.responseFrom(client);
    }

    @PostMapping("/oauth-clients/rotate-secret")
    @RequiresPermission(CLIENT_WRITE)
    @Audited(type = AuditType.SECURITY, action = "OAUTH_CLIENT_SECRET_ROTATE",
            targetType = "OAUTH_CLIENT", targetId = "#request.clientId()",
            description = "轮换 IAM OAuth 客户端密钥")
    public void rotateOAuthClientSecret(@RequestBody OAuthClientSecretRotateRequest request) {
        oauthClientAppService.rotateSecret(
                new OAuthClientSecretRotateCmd(request.clientId(), request.clientSecret()));
    }

    @PostMapping("/oauth-clients/update")
    @RequiresPermission(CLIENT_WRITE)
    @Audited(type = AuditType.SECURITY, action = "OAUTH_CLIENT_ACCESS_UPDATE",
            targetType = "OAUTH_CLIENT", targetId = "#request.clientId()",
            description = "更新 IAM OAuth 客户端访问配置")
    public void updateOAuthClient(@RequestBody OAuthClientAccessUpdateRequest request) {
        oauthClientAppService.updateAccess(new OAuthClientAccessUpdateCmd(
                request.clientId(),
                request.scopes(),
                request.redirectUris(),
                request.postLogoutRedirectUris()));
    }

    @PostMapping("/oauth-clients/disable")
    @RequiresPermission(CLIENT_WRITE)
    @Audited(type = AuditType.SECURITY, action = "OAUTH_CLIENT_DISABLE",
            targetType = "OAUTH_CLIENT", targetId = "#request.clientId()",
            description = "停用 IAM OAuth 客户端")
    public void disableOAuthClient(@RequestBody OAuthClientIdRequest request) {
        oauthClientAppService.disable(new OAuthClientDisableCmd(request.clientId()));
    }
}
