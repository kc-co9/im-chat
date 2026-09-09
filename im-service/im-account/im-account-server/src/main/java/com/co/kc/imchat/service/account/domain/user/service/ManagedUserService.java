package com.co.kc.imchat.service.account.domain.user.service;

import com.co.kc.imchat.common.exception.RepeatException;
import com.co.kc.imchat.service.account.domain.user.model.ManagedUser;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.service.account.domain.user.repository.ManagedUserRepository;
import lombok.RequiredArgsConstructor;

/**
 * 管理用户领域服务，负责需要跨用户判断的业务规则。
 */
@RequiredArgsConstructor
public class ManagedUserService {
    private final ManagedUserRepository managedUserRepository;

    public void ensureEmailAvailable(ManagedUser managedUser, UserEmail email) {
        managedUser.ensureModifiable();
        if (!managedUser.getEmail().equals(email) && managedUserRepository.contain(email)) {
            throw new RepeatException("邮箱已被使用");
        }
    }
}
