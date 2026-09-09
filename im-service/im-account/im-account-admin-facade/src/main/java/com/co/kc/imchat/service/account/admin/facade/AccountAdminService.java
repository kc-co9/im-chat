package com.co.kc.imchat.service.account.admin.facade;

import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.service.account.admin.facade.dto.AccountUserDTO;
import com.co.kc.imchat.service.account.admin.facade.dto.AccountUserListDTO;
import com.co.kc.imchat.service.account.admin.facade.params.UserBanParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserDeleteParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserGetParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserPageParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserPasswordResetParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserUnbanParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserUpdateParams;

/**
 * Account 高权限用户管理契约。
 *
 * <p>该契约只供独立管理后台调用，不属于普通用户认证接口。</p>
 */
public interface AccountAdminService {

    PagingResult<AccountUserListDTO> pageUsers(UserPageParams params);

    AccountUserDTO getUser(UserGetParams params);

    void updateUser(UserUpdateParams params);

    void resetPassword(UserPasswordResetParams params);

    void banUser(UserBanParams params);

    void unbanUser(UserUnbanParams params);

    void deleteUser(UserDeleteParams params);
}
