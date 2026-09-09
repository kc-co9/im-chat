package com.co.kc.imchat.management.iam.domain.session.repository;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthSession;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationId;

import java.time.Instant;

/** OAuth 授权会话仓储。 */
public interface OAuthSessionRepository {
    PagingResult<OAuthSession> pageActive(Paging paging);

    boolean revoke(OAuthAuthorizationId authorizationId, Instant revokedAt);

    void revoke(AdministratorId administratorId, Instant revokedAt);

    void revoke(OAuthClientId clientId, Instant revokedAt);
}
