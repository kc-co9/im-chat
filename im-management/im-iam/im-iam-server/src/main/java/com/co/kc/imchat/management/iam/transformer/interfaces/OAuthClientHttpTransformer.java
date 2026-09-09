package com.co.kc.imchat.management.iam.transformer.interfaces;

import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthClientRegisterCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthClientDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthClientListDTO;
import com.co.kc.imchat.management.iam.model.io.OAuthClientListResponse;
import com.co.kc.imchat.management.iam.model.io.OAuthClientRegisterRequest;
import com.co.kc.imchat.management.iam.model.io.OAuthClientResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/** IAM OAuth Client HTTP 边界转换器。 */
@Mapper
public interface OAuthClientHttpTransformer {
    OAuthClientHttpTransformer INSTANCE = Mappers.getMapper(OAuthClientHttpTransformer.class);

    OAuthClientResponse responseFrom(OAuthClientDTO client);

    OAuthClientListResponse listResponseFrom(OAuthClientListDTO client);

    OAuthClientRegisterCmd registerCommandFrom(OAuthClientRegisterRequest request);
}
