package com.co.kc.imchat.management.admin.adapter;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUser;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserEmail;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserQueryCondition;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserId;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserName;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserPassword;
import com.co.kc.imchat.management.admin.transformer.domain.ManagedUserDomainTransformer;
import com.co.kc.imchat.service.account.admin.facade.AccountAdminService;
import com.co.kc.imchat.service.account.admin.facade.dto.AccountUserDTO;
import com.co.kc.imchat.service.account.admin.facade.dto.AccountUserListDTO;
import com.co.kc.imchat.service.account.admin.facade.params.UserBanParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserDeleteParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserPasswordResetParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserUnbanParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserUpdateParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserGetParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserPageParams;
import lombok.RequiredArgsConstructor;

/**
 * Account 高权限管理契约适配器。
 */
@RequiredArgsConstructor
public class AccountAdminAdapter {
    private final AccountAdminService accountAdminService;

    public PagingResult<ManagedUser> pageUsers(Paging paging, ManagedUserQueryCondition condition) {
        UserPageParams userPageParams = new UserPageParams(
                paging,
                condition.userId().map(ManagedUserId::value).orElse(null),
                condition.username().map(ManagedUserName::value).orElse(null),
                condition.email().map(ManagedUserEmail::value).orElse(null),
                condition.status().map(ManagedUserDomainTransformer.INSTANCE::accountUserStatusFrom).orElse(null));
        PagingResult<AccountUserListDTO> page = accountAdminService.pageUsers(userPageParams);
        return page.map(ManagedUserDomainTransformer.INSTANCE::managedUserFrom);
    }

    public ManagedUser get(ManagedUserId userId) {
        AccountUserDTO user = accountAdminService.getUser(new UserGetParams(userId.value()));
        return ManagedUserDomainTransformer.INSTANCE.managedUserFrom(user);
    }

    public void update(ManagedUserId userId, ManagedUserName username, ManagedUserEmail email) {
        UserUpdateParams params = new UserUpdateParams(userId.value(), username.value(), email.value());
        accountAdminService.updateUser(params);
    }

    public void resetPassword(ManagedUserId userId, ManagedUserPassword password) {
        UserPasswordResetParams params = new UserPasswordResetParams(userId.value(), password.value());
        accountAdminService.resetPassword(params);
    }

    public void ban(ManagedUserId userId) {
        UserBanParams params = new UserBanParams(userId.value());
        accountAdminService.banUser(params);
    }

    public void unban(ManagedUserId userId) {
        UserUnbanParams params = new UserUnbanParams(userId.value());
        accountAdminService.unbanUser(params);
    }

    public void delete(ManagedUserId userId) {
        UserDeleteParams params = new UserDeleteParams(userId.value());
        accountAdminService.deleteUser(params);
    }
}
