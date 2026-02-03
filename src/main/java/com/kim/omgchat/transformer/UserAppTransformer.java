package com.kim.omgchat.transformer;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserAppTransformer {
    UserAppTransformer INSTANCE = Mappers.getMapper(UserAppTransformer.class);
}
