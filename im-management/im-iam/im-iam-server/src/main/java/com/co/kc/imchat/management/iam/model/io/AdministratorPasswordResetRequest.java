package com.co.kc.imchat.management.iam.model.io;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 管理员密码重置请求。 */
public record AdministratorPasswordResetRequest(Long administratorId, String password) {
    public AdministratorPasswordResetRequest {
        AssertUtils.argNotBlank("administrator password must not be blank", password);
    }
}
