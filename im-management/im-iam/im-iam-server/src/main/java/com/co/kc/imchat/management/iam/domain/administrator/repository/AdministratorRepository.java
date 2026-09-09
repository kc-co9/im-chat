package com.co.kc.imchat.management.iam.domain.administrator.repository;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorEmail;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorUsername;

import java.util.Optional;

/** IAM 管理员聚合仓储。 */
public interface AdministratorRepository {
    Optional<Administrator> find(AdministratorUsername username);

    Optional<Administrator> find(AdministratorEmail email);

    Optional<Administrator> find(AdministratorId administratorId);

    void save(Administrator administrator);

    void remove(Administrator administrator);

    PagingResult<Administrator> page(Paging paging);

    boolean exists();
}
