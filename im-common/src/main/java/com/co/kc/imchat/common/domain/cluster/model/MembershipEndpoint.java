package com.co.kc.imchat.common.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/** 共享成员通信端点，不包含服务身份或成员资格。 */
public record MembershipEndpoint(String value) implements Serializable {
    public MembershipEndpoint {
        AssertUtils.domainPropNotBlank("membership endpoint must not be blank", value);
    }
}
