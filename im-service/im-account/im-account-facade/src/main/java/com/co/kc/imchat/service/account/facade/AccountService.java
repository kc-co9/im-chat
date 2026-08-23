package com.co.kc.imchat.service.account.facade;

import com.co.kc.imchat.service.account.facade.dto.SessionAuthDTO;
import com.co.kc.imchat.service.account.facade.dto.UserProfileDTO;
import com.co.kc.imchat.service.account.facade.dto.UserProfileFindDTO;
import com.co.kc.imchat.service.account.facade.params.AccessTokenParams;
import com.co.kc.imchat.service.account.facade.params.UserProfileFindParams;
import com.co.kc.imchat.service.account.facade.params.UserProfileGetParams;

/**
 * 账号服务契约。
 * <p>
 * 提供用户身份和用户资料查询能力。
 */
public interface AccountService {
    /**
     * 校验 Access Token 及其关联会话。
     *
     * @param params Access Token 认证参数
     * @return 会话认证成功结果
     */
    SessionAuthDTO authenticate(AccessTokenParams params);

    /**
     * 查询用户基础资料。
     *
     * @param params 用户资料查询请求
     * @return 用户资料
     */
    UserProfileDTO getUserProfile(UserProfileGetParams params);

    /**
     * 按邮箱查询用户基础资料。
     *
     * @param params 邮箱查询请求
     * @return 用户资料查询结果
     */
    UserProfileFindDTO findUserProfileByEmail(UserProfileFindParams params);
}
