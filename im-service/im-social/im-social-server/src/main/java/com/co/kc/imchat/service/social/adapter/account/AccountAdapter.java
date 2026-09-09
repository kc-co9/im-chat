package com.co.kc.imchat.service.social.adapter.account;

import com.co.kc.imchat.service.account.facade.AccountService;
import com.co.kc.imchat.service.account.facade.dto.UserProfileDTO;
import com.co.kc.imchat.service.account.facade.dto.UserProfileFindDTO;
import com.co.kc.imchat.service.account.facade.params.UserProfileFindParams;
import com.co.kc.imchat.service.account.facade.params.UserProfileGetParams;
import com.co.kc.imchat.service.account.facade.params.UserProfilesGetParams;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.social.domain.account.model.UserProfile;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        return new UserProfile(new UserId(response.userId()), response.username(), response.email());
    }

    /**
     * 批量查询用户资料，不存在的用户不会进入结果。
     *
     * @param userIds 用户 ID 列表
     * @return 已存在的用户资料
     */
    public Map<UserId, UserProfile> findUserProfiles(List<UserId> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> values = userIds.stream()
                .map(UserId::value)
                .toList();
        return accountService.getUserProfiles(new UserProfilesGetParams(values))
                .stream()
                .map(profile -> new UserProfile(
                        new UserId(profile.userId()),
                        profile.username(),
                        profile.email()))
                .collect(Collectors.toUnmodifiableMap(UserProfile::userId, Function.identity()));
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
        return Optional.of(new UserProfile(
                new UserId(response.userId()), response.username(), response.email()));
    }
}
