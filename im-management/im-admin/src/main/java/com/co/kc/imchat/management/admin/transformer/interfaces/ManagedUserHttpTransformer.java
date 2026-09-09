package com.co.kc.imchat.management.admin.transformer.interfaces;

import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserStatus;
import com.co.kc.imchat.management.admin.model.cqrs.dto.ManagedUserDTO;
import com.co.kc.imchat.management.admin.model.enums.ManagedUserStatusEnum;
import com.co.kc.imchat.management.admin.model.io.ManagedUserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

@Mapper
public interface ManagedUserHttpTransformer {
    ManagedUserHttpTransformer INSTANCE = Mappers.getMapper(ManagedUserHttpTransformer.class);

    ManagedUserResponse responseFrom(ManagedUserDTO user);

    ManagedUserStatusEnum managedUserStatusEnumFrom(ManagedUserStatus status);

    default Long epochMilliFrom(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}
