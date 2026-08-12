package com.co.kc.imchat.service.social.adapter.account;

import com.co.kc.imchat.service.account.facade.AccountService;
import com.co.kc.imchat.service.account.facade.dto.UserProfileDTO;
import com.co.kc.imchat.service.account.facade.dto.UserProfileFindDTO;
import com.co.kc.imchat.service.account.facade.params.UserProfileFindParams;
import com.co.kc.imchat.service.account.facade.params.UserProfileGetParams;
import com.co.kc.imchat.service.social.domain.account.model.UserProfile;

import java.util.Optional;

/**
 * 社交服务访问账号服务的适配器。
 */
public class AccountAdapter {

    private AccountService accountService;

    public AccountAdapter() {
    }

    public AccountAdapter(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * 查询用户资料，不存在时抛出业务异常。
     *
     * @param userId 用户 ID
     * @return 用户资料
     */
    public UserProfile getUserProfile(Long userId) {
        UserProfileDTO response = accountService.getUserProfile(new UserProfileGetParams(userId));
        return new UserProfile(response.userId(), response.username(), response.email());
    }

    /**
     * 按邮箱搜索用户资料。
     *
     * @param email 用户邮箱
     * @return 用户资料
     */
    public Optional<UserProfile> findUserProfileByEmail(String email) {
        UserProfileFindDTO response =
                accountService.findUserProfileByEmail(new UserProfileFindParams(email));
        if (!response.found()) {
            return Optional.empty();
        }
        return Optional.of(new UserProfile(response.userId(), response.username(), response.email()));
    }
}
