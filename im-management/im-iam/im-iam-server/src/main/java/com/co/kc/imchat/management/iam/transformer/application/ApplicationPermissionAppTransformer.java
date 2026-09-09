package com.co.kc.imchat.management.iam.transformer.application;

import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermission;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionDefinition;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionDescription;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionName;
import com.co.kc.imchat.management.iam.model.cqrs.dto.ApplicationPermissionDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.ApplicationPermissionDefinitionDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/** IAM 权限应用层转换器。 */
@Mapper
public interface ApplicationPermissionAppTransformer {
    ApplicationPermissionAppTransformer INSTANCE = Mappers.getMapper(ApplicationPermissionAppTransformer.class);

    @Mapping(target = "id", source = "id.value")
    @Mapping(target = "code", source = "code.value")
    @Mapping(target = "name", source = "name.value")
    @Mapping(target = "description", source = "description.value")
    ApplicationPermissionDTO applicationPermissionDtoFrom(ApplicationPermission permission);

    /** 将权限同步命令中的边界定义转换为领域定义。 */
    default List<ApplicationPermissionDefinition> permissionDefinitionsFrom(
            List<ApplicationPermissionDefinitionDTO> commands
    ) {
        return commands.stream()
                .map(this::permissionDefinitionFrom)
                .toList();
    }

    /** 将单项权限命令转换为领域定义。 */
    default ApplicationPermissionDefinition permissionDefinitionFrom(ApplicationPermissionDefinitionDTO command) {
        return new ApplicationPermissionDefinition(
                new ApplicationPermissionCode(command.code()),
                new ApplicationPermissionName(command.name()),
                new ApplicationPermissionDescription(command.description()));
    }
}
