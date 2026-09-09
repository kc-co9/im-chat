package com.co.kc.imchat.management.monitor.model.cqrs.query;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 查询有限条诊断记录。 */
public record DiagnosticQuery(Integer limit) {
    public DiagnosticQuery {
        AssertUtils.argTrue("limit must be between 1 and 100",
                limit != null && limit > 0 && limit <= 100);
    }
}
