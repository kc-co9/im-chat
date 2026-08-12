package com.co.kc.imchat.service.account.transformer;

import com.co.kc.imchat.plugin.session.token.TokenDTO;
import com.co.kc.imchat.service.account.domain.user.model.User;
import com.co.kc.imchat.service.account.facade.dto.TokenValidateDTO;
import com.co.kc.imchat.service.account.facade.dto.UserChattingCheckDTO;
import com.co.kc.imchat.service.account.facade.dto.UserOnlineCheckDTO;
import com.co.kc.imchat.service.account.facade.dto.UserProfileDTO;
import com.co.kc.imchat.service.account.facade.dto.UserProfileFindDTO;
import com.co.kc.imchat.service.account.model.cqrs.dto.SignInDTO;
import com.co.kc.imchat.service.account.model.cqrs.dto.UserDetailDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface AccountAppTransformer {
    AccountAppTransformer INSTANCE = Mappers.getMapper(AccountAppTransformer.class);

    @Mapping(target = "userId", source = "user.id.value")
    @Mapping(target = "token", source = "token")
    SignInDTO signInDtoFrom(User user, String token);

    @Mapping(target = "userId", source = "id.value")
    @Mapping(target = "email", source = "email.value")
    @Mapping(target = "username", source = "username.value")
    UserDetailDTO userDetailDtoFrom(User user);

    @Mapping(target = "userId", source = "id.value")
    @Mapping(target = "username", source = "username.value")
    @Mapping(target = "email", source = "email.value")
    UserProfileDTO userProfileDtoFrom(User user);

    @Mapping(target = "found", expression = "java(true)")
    @Mapping(target = "userId", source = "id.value")
    @Mapping(target = "username", source = "username.value")
    @Mapping(target = "email", source = "email.value")
    UserProfileFindDTO userProfileFindDtoFrom(User user);

    default TokenValidateDTO tokenValidateDtoFrom(TokenDTO token) {
        return new TokenValidateDTO(token != null, token == null ? null : token.getUserId());
    }

    default UserOnlineCheckDTO userOnlineCheckDtoFrom(boolean online) {
        return new UserOnlineCheckDTO(online);
    }

    default UserChattingCheckDTO userChattingCheckDtoFrom(boolean chatting) {
        return new UserChattingCheckDTO(chatting);
    }
}
