package com.co.kc.imchat.management.admin.application;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.admin.adapter.AccountAdminAdapter;
import com.co.kc.imchat.management.audit.sdk.annotation.Audited;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUser;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserEmail;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserQueryCondition;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserId;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserName;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserPassword;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserStatus;
import com.co.kc.imchat.management.admin.model.cqrs.command.ManagedUserBanCmd;
import com.co.kc.imchat.management.admin.model.cqrs.command.ManagedUserDeleteCmd;
import com.co.kc.imchat.management.admin.model.cqrs.command.ManagedUserPasswordResetCmd;
import com.co.kc.imchat.management.admin.model.cqrs.command.ManagedUserUnbanCmd;
import com.co.kc.imchat.management.admin.model.cqrs.command.ManagedUserUpdateCmd;
import com.co.kc.imchat.management.admin.model.cqrs.query.ManagedUserGetQuery;
import com.co.kc.imchat.management.admin.model.cqrs.query.ManagedUserPageQuery;
import com.co.kc.imchat.management.admin.model.cqrs.dto.ManagedUserDTO;
import com.co.kc.imchat.management.admin.transformer.application.ManagedUserAppTransformer;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

/**
 * 普通用户管理应用服务。
 */
@RequiredArgsConstructor
public class ManagedUserAppService {
    private final AccountAdminAdapter accountAdapter;

    public PagingResult<ManagedUserDTO> page(ManagedUserPageQuery query) {
        Paging paging = query.paging();
        ManagedUserQueryCondition condition = new ManagedUserQueryCondition(
                Optional.ofNullable(query.userId()).map(ManagedUserId::new),
                Optional.ofNullable(query.username()).map(ManagedUserName::new),
                Optional.ofNullable(query.email()).map(ManagedUserEmail::new),
                Optional.ofNullable(query.status()).map(ManagedUserStatus::valueOf));
        PagingResult<ManagedUser> page = accountAdapter.pageUsers(paging, condition);
        return page.map(ManagedUserAppTransformer.INSTANCE::managedUserDtoFrom);
    }

    public ManagedUserDTO get(ManagedUserGetQuery query) {
        ManagedUser user = accountAdapter.get(new ManagedUserId(query.userId()));
        return ManagedUserAppTransformer.INSTANCE.managedUserDtoFrom(user);
    }

    @Audited(type = AuditType.BUSINESS, action = "USER_UPDATE",
            targetType = "USER", targetId = "#command.userId()",
            description = "修改普通用户资料")
    public void update(ManagedUserUpdateCmd command) {
        ManagedUserId userId = new ManagedUserId(command.userId());
        ManagedUserName username = new ManagedUserName(command.username());
        ManagedUserEmail email = new ManagedUserEmail(command.email());
        accountAdapter.update(userId, username, email);
    }

    @Audited(type = AuditType.BUSINESS, action = "USER_PASSWORD_RESET",
            targetType = "USER", targetId = "#command.userId()",
            description = "重置普通用户密码")
    public void resetPassword(ManagedUserPasswordResetCmd command) {
        ManagedUserId userId = new ManagedUserId(command.userId());
        ManagedUserPassword password = new ManagedUserPassword(command.password());
        accountAdapter.resetPassword(userId, password);
    }

    @Audited(type = AuditType.BUSINESS, action = "USER_BAN",
            targetType = "USER", targetId = "#command.userId()",
            description = "封禁普通用户")
    public void ban(ManagedUserBanCmd command) {
        accountAdapter.ban(new ManagedUserId(command.userId()));
    }

    @Audited(type = AuditType.BUSINESS, action = "USER_UNBAN",
            targetType = "USER", targetId = "#command.userId()",
            description = "解除普通用户封禁")
    public void unban(ManagedUserUnbanCmd command) {
        accountAdapter.unban(new ManagedUserId(command.userId()));
    }

    @Audited(type = AuditType.BUSINESS, action = "USER_DELETE",
            targetType = "USER", targetId = "#command.userId()",
            description = "删除普通用户")
    public void delete(ManagedUserDeleteCmd command) {
        accountAdapter.delete(new ManagedUserId(command.userId()));
    }
}
