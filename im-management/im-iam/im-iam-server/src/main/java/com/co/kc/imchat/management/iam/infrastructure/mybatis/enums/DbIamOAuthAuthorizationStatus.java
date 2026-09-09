package com.co.kc.imchat.management.iam.infrastructure.mybatis.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DbIamOAuthAuthorizationStatus {
    ACTIVE(1), REVOKED(2), EXPIRED(3);

    @EnumValue
    private final int value;
}
