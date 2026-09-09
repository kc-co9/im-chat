package com.co.kc.imchat.management.iam.domain.application.repository;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClient;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;

import java.util.Optional;

/** IAM 机器客户端仓储。 */
public interface OAuthClientRepository {
    boolean contains(OAuthClientId clientId);

    Optional<OAuthClient> find(OAuthClientId clientId);

    PagingResult<OAuthClient> page(AppId appId, Paging paging);

    void save(OAuthClient oauthClient);
}
