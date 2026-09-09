package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationId;
import com.co.kc.imchat.management.iam.domain.session.repository.OAuthSessionRepository;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthSessionRevokeCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthSessionDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthSessionPageQuery;
import com.co.kc.imchat.management.iam.transformer.application.OAuthSessionAppTransformer;
import com.co.kc.imchat.plugin.metrics.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** OAuth 授权会话管理应用服务。 */
@RequiredArgsConstructor
public class OAuthSessionAppService {
    private final OAuthSessionRepository oauthSessionRepository;

    public PagingResult<OAuthSessionDTO> page(OAuthSessionPageQuery query) {
        return oauthSessionRepository.pageActive(query.paging())
                .map(OAuthSessionAppTransformer.INSTANCE::oauthSessionDtoFrom);
    }

    @Observed(name = "im.iam.session.revoke")
    @Transactional(rollbackFor = Exception.class)
    public void revoke(OAuthSessionRevokeCmd command) {
        OAuthAuthorizationId authorizationId = new OAuthAuthorizationId(command.sessionId());
        if (!oauthSessionRepository.revoke(authorizationId, Instant.now())) {
            throw new NotFoundException("IAM 会话不存在");
        }
    }
}
