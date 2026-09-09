package com.co.kc.imchat.management.iam.domain.administrator.model;

import com.co.kc.imchat.common.domain.shared.model.StringIdentifiable;
import com.co.kc.imchat.common.utils.AssertUtils;

/** IAM 管理员标识。 */
public record AdministratorId(Long value) implements StringIdentifiable {
    public AdministratorId {
        AssertUtils.domainPropNotNull("administrator id must not be null", value);
        AssertUtils.domainPropTrue("administrator id must be positive", value > 0);
    }

    @Override
    public String stringValue() {
        return String.valueOf(value);
    }
}
