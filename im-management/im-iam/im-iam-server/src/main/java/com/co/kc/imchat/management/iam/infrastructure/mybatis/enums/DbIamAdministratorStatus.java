package com.co.kc.imchat.management.iam.infrastructure.mybatis.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DbIamAdministratorStatus {
    ACTIVE(1), DISABLED(2);

    @EnumValue
    private final int value;
}
