package com.co.kc.imchat.service.account.transformer.interfaces;

import com.co.kc.imchat.service.account.model.cqrs.dto.SignInDTO;
import com.co.kc.imchat.service.account.model.io.TokenPairResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserHttpTransformer {
    UserHttpTransformer INSTANCE = Mappers.getMapper(UserHttpTransformer.class);

    TokenPairResponse tokenPairResponseFrom(SignInDTO signInDTO);
}
