package com.co.kc.imchat.management.iam.transformer.interfaces;

import com.co.kc.imchat.management.iam.model.cqrs.dto.AdministratorDTO;
import com.co.kc.imchat.management.iam.model.io.IamAdministratorResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/** IAM 管理员 HTTP 边界转换器。 */
@Mapper
public interface AdministratorHttpTransformer {
    AdministratorHttpTransformer INSTANCE = Mappers.getMapper(AdministratorHttpTransformer.class);

    IamAdministratorResponse responseFrom(AdministratorDTO administrator);
}
