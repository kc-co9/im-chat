package com.co.kc.imchat.management.iam.domain.application.repository;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.application.model.AppKey;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.Application;

import java.util.Optional;
import java.util.List;
import java.util.Set;

/** IAM 注册应用仓储。 */
public interface ApplicationRepository {
    boolean contains(AppKey appKey);

    Optional<Application> find(AppKey appKey);

    Optional<Application> find(AppId appId);

    List<Application> find(Set<AppId> appIds);

    PagingResult<Application> page(Paging paging);

    void save(Application app);
}
