package com.co.kc.imchat.service.account.interfaces.rpc;

import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.service.account.admin.facade.AccountAdminService;
import com.co.kc.imchat.service.account.admin.facade.dto.AccountUserDTO;
import com.co.kc.imchat.service.account.admin.facade.dto.AccountUserListDTO;
import com.co.kc.imchat.service.account.admin.facade.params.UserBanParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserDeleteParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserGetParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserPageParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserPasswordResetParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserUnbanParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserUpdateParams;
import com.co.kc.imchat.service.account.application.ManagedUserAppService;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserBanCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserDeleteCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserPasswordResetCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserUnbanCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserUpdateCmd;
import com.co.kc.imchat.service.account.model.cqrs.dto.ManagedUserDTO;
import com.co.kc.imchat.service.account.model.cqrs.dto.ManagedUserListDTO;
import com.co.kc.imchat.service.account.model.cqrs.query.ManagedUserGetQuery;
import com.co.kc.imchat.service.account.model.cqrs.query.ManagedUserPageQuery;
import com.co.kc.imchat.service.account.transformer.interfaces.AccountAdminRpcTransformer;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

@Component
@DubboService(interfaceClass = AccountAdminService.class, version = "1.0.0")
public class AccountAdminRpcService implements AccountAdminService {
    private static final AccountAdminRpcTransformer TRANSFORMER = AccountAdminRpcTransformer.INSTANCE;

    private final ManagedUserAppService managedUserAppService;

    public AccountAdminRpcService(ManagedUserAppService managedUserAppService) {
        this.managedUserAppService = managedUserAppService;
    }

    @Override
    public PagingResult<AccountUserListDTO> pageUsers(UserPageParams params) {
        ManagedUserPageQuery query = TRANSFORMER.managedUserPageQueryFrom(params);
        PagingResult<ManagedUserListDTO> page = managedUserAppService.page(query);
        return TRANSFORMER.accountUserPageFrom(page);
    }

    @Override
    public AccountUserDTO getUser(UserGetParams params) {
        ManagedUserGetQuery query = TRANSFORMER.managedUserGetQueryFrom(params);
        ManagedUserDTO user = managedUserAppService.get(query);
        return TRANSFORMER.accountUserDtoFrom(user);
    }

    @Override
    public void updateUser(UserUpdateParams params) {
        ManagedUserUpdateCmd command = TRANSFORMER.managedUserUpdateCmdFrom(params);
        managedUserAppService.update(command);
    }

    @Override
    public void resetPassword(UserPasswordResetParams params) {
        ManagedUserPasswordResetCmd command = TRANSFORMER.managedUserPasswordResetCmdFrom(params);
        managedUserAppService.resetPassword(command);
    }

    @Override
    public void banUser(UserBanParams params) {
        ManagedUserBanCmd command = TRANSFORMER.managedUserBanCmdFrom(params);
        managedUserAppService.ban(command);
    }

    @Override
    public void unbanUser(UserUnbanParams params) {
        ManagedUserUnbanCmd command = TRANSFORMER.managedUserUnbanCmdFrom(params);
        managedUserAppService.unban(command);
    }

    @Override
    public void deleteUser(UserDeleteParams params) {
        ManagedUserDeleteCmd command = TRANSFORMER.managedUserDeleteCmdFrom(params);
        managedUserAppService.delete(command);
    }
}
