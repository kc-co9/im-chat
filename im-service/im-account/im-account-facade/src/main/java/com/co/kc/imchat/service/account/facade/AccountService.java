package com.co.kc.imchat.service.account.facade;

import com.co.kc.imchat.service.account.facade.params.TokenValidateParams;
import com.co.kc.imchat.service.account.facade.params.UserProfileFindParams;
import com.co.kc.imchat.service.account.facade.params.UserProfileGetParams;
import com.co.kc.imchat.service.account.facade.dto.TokenValidateDTO;
import com.co.kc.imchat.service.account.facade.dto.UserProfileFindDTO;
import com.co.kc.imchat.service.account.facade.dto.UserProfileDTO;

/**
 * 账号服务契约。
 * <p>
 * 提供用户身份和用户资料查询能力。
 */
public interface AccountService {
    /**
     * 校验登录令牌并解析用户身份。
     *
     * @param params 令牌校验请求
     * @return 令牌校验结果
     */
    TokenValidateDTO validateToken(TokenValidateParams params);

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
