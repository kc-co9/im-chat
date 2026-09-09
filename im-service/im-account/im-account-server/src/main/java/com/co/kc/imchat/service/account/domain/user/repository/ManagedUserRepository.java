package com.co.kc.imchat.service.account.domain.user.repository;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.service.account.domain.user.model.UserQueryCondition;
import com.co.kc.imchat.service.account.domain.user.model.ManagedUser;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;

import java.util.Optional;

/**
 * 被管理用户仓储，包含逻辑删除事实但不负责用户聚合持久化。
 */
public interface ManagedUserRepository {
    Optional<ManagedUser> find(UserId userId);

    PagingResult<ManagedUser> page(Paging paging, UserQueryCondition condition);

    void save(ManagedUser user);

    void remove(ManagedUser user);

    boolean contain(UserEmail email);
}
