package com.co.kc.imchat.management.iam.infrastructure.mybatis.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** IAM 机器客户端数据库状态。 */
@Getter
@RequiredArgsConstructor
public enum DbIamOAuthClientStatus {
    ACTIVE(1),
    DISABLED(2);

    @EnumValue
    private final int value;
}
