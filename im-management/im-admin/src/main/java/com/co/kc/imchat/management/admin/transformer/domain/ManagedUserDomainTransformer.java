package com.co.kc.imchat.management.admin.transformer.domain;

import com.co.kc.imchat.management.admin.domain.user.model.ManagedUser;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserEmail;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserId;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserName;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserStatus;
import com.co.kc.imchat.service.account.admin.facade.dto.AccountUserDTO;
import com.co.kc.imchat.service.account.admin.facade.dto.AccountUserListDTO;
import com.co.kc.imchat.service.account.admin.facade.enums.AccountUserStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ManagedUserDomainTransformer {
    ManagedUserDomainTransformer INSTANCE = Mappers.getMapper(ManagedUserDomainTransformer.class);

    @Mapping(target = "id", source = "userId")
    ManagedUser managedUserFrom(AccountUserDTO user);

    @Mapping(target = "id", source = "userId")
    ManagedUser managedUserFrom(AccountUserListDTO user);

    default ManagedUserId managedUserIdFrom(Long userId) {
        return new ManagedUserId(userId);
    }

    default ManagedUserName managedUserNameFrom(String username) {
        return new ManagedUserName(username);
    }

    default ManagedUserEmail managedUserEmailFrom(String email) {
        return new ManagedUserEmail(email);
    }

    AccountUserStatus accountUserStatusFrom(ManagedUserStatus status);
}
