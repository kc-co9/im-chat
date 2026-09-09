package com.co.kc.imchat.management.iam.transformer.interfaces;

import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationRegisterCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationUpdateCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.ApplicationDTO;
import com.co.kc.imchat.management.iam.model.io.ApplicationRegisterRequest;
import com.co.kc.imchat.management.iam.model.io.ApplicationUpdateRequest;
import com.co.kc.imchat.management.iam.model.io.ApplicationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/** IAM 应用 HTTP 边界转换器。 */
@Mapper
public interface ApplicationHttpTransformer {
    ApplicationHttpTransformer INSTANCE = Mappers.getMapper(ApplicationHttpTransformer.class);

    ApplicationResponse applicationResponseFrom(ApplicationDTO application);

    ApplicationRegisterCmd applicationRegisterCmdFrom(ApplicationRegisterRequest request);

    ApplicationUpdateCmd applicationUpdateCmdFrom(ApplicationUpdateRequest request);
}
