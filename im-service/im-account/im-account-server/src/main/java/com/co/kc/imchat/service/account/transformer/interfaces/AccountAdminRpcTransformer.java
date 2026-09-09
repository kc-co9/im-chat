package com.co.kc.imchat.service.account.transformer.interfaces;

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
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserBanCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserDeleteCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserPasswordResetCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserUnbanCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserUpdateCmd;
import com.co.kc.imchat.service.account.model.cqrs.dto.ManagedUserDTO;
import com.co.kc.imchat.service.account.model.cqrs.dto.ManagedUserListDTO;
import com.co.kc.imchat.service.account.model.cqrs.query.ManagedUserGetQuery;
import com.co.kc.imchat.service.account.model.cqrs.query.ManagedUserPageQuery;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface AccountAdminRpcTransformer {
    AccountAdminRpcTransformer INSTANCE = Mappers.getMapper(AccountAdminRpcTransformer.class);

    ManagedUserGetQuery managedUserGetQueryFrom(UserGetParams params);

    ManagedUserUpdateCmd managedUserUpdateCmdFrom(UserUpdateParams params);

    ManagedUserPasswordResetCmd managedUserPasswordResetCmdFrom(UserPasswordResetParams params);

    ManagedUserBanCmd managedUserBanCmdFrom(UserBanParams params);

    ManagedUserUnbanCmd managedUserUnbanCmdFrom(UserUnbanParams params);

    ManagedUserDeleteCmd managedUserDeleteCmdFrom(UserDeleteParams params);

    AccountUserDTO accountUserDtoFrom(ManagedUserDTO dto);

    AccountUserListDTO accountUserListDtoFrom(ManagedUserListDTO dto);

    PagingResult<AccountUserListDTO> accountUserPageFrom(PagingResult<ManagedUserListDTO> page);

    ManagedUserPageQuery managedUserPageQueryFrom(UserPageParams params);

}
