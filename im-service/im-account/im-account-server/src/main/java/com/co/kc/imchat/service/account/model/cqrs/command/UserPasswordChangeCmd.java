package com.co.kc.imchat.service.account.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

public record UserPasswordChangeCmd(
        Long userId,
        String oldPassword,
        String newPassword
) {
    public UserPasswordChangeCmd {
        AssertUtils.argNotNull("userId must not be null", userId);
        AssertUtils.argNotBlank("oldPassword must not be blank", oldPassword);
        AssertUtils.argNotBlank("newPassword must not be blank", newPassword);
    }
}
