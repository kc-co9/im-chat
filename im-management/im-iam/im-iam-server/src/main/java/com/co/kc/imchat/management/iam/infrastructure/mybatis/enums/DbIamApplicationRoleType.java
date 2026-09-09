package com.co.kc.imchat.management.iam.infrastructure.mybatis.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DbIamApplicationRoleType {
    BUILT_IN(1), CUSTOM(2);

    @EnumValue
    private final int value;
}
