package com.co.kc.imchat.management.iam.transformer.interfaces;

import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationRoleCreateCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationRoleUpdateCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.ApplicationRoleDTO;
import com.co.kc.imchat.management.iam.model.io.ApplicationRoleResponse;
import com.co.kc.imchat.management.iam.model.io.RoleCreateRequest;
import com.co.kc.imchat.management.iam.model.io.RoleUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/** IAM 角色 HTTP 边界转换器。 */
@Mapper
public interface RoleHttpTransformer {
    RoleHttpTransformer INSTANCE = Mappers.getMapper(RoleHttpTransformer.class);

    ApplicationRoleResponse applicationRoleResponseFrom(ApplicationRoleDTO role);

    ApplicationRoleCreateCmd applicationRoleCreateCmdFrom(RoleCreateRequest request);

    ApplicationRoleUpdateCmd applicationRoleUpdateCmdFrom(RoleUpdateRequest request);
}
