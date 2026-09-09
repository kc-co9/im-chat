package com.co.kc.imchat.management.iam.domain.administrator.service;

import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorPassword;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorRawPassword;

/**
 * IAM 管理员密码加密与校验能力。
 */
public interface PasswordService {
    AdministratorPassword encrypt(AdministratorRawPassword password);

    boolean verify(AdministratorRawPassword password, AdministratorPassword encryptedPassword);

    /** 为不存在的账号执行等价密码校验，避免通过响应耗时枚举账号。 */
    void verifyUnknown(AdministratorRawPassword password);
}
