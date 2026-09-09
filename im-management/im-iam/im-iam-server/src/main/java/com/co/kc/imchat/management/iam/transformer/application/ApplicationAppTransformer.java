package com.co.kc.imchat.management.iam.transformer.application;

import com.co.kc.imchat.management.iam.domain.application.model.Application;
import com.co.kc.imchat.management.iam.model.cqrs.dto.ApplicationDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ApplicationAppTransformer {
    ApplicationAppTransformer INSTANCE = Mappers.getMapper(ApplicationAppTransformer.class);

    @Mapping(target = "appId", source = "appId.value")
    @Mapping(target = "appKey", source = "appKey.value")
    @Mapping(target = "name", source = "name.value")
    ApplicationDTO applicationDtoFrom(Application application);
}
