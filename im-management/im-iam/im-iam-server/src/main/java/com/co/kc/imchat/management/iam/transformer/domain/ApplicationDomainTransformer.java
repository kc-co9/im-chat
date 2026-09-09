package com.co.kc.imchat.management.iam.transformer.domain;

import com.co.kc.imchat.management.iam.domain.application.model.Application;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApp;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/** IAM 接入应用领域对象与持久化实体转换器。 */
@Mapper
public interface ApplicationDomainTransformer {
    ApplicationDomainTransformer INSTANCE = Mappers.getMapper(ApplicationDomainTransformer.class);
    @Mapping(target = "appId.value", source = "appId")
    @Mapping(target = "appKey.value", source = "appKey")
    @Mapping(target = "name.value", source = "name")
    @Mapping(target = "pkId", source = "id")
    Application applicationFrom(DbIamApp app);

    @Mapping(target = "id", source = "pkId")
    @Mapping(target = "appId", source = "appId.value")
    @Mapping(target = "appKey", source = "appKey.value")
    @Mapping(target = "name", source = "name.value")
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    DbIamApp dbApplicationFrom(Application app);
}
