package com.co.kc.imchat.management.audit.model.io;

import com.co.kc.imchat.management.audit.model.enums.AuditOutcomeEnum;
import com.co.kc.imchat.management.audit.model.enums.AuditTypeEnum;

/** 审计记录分页查询请求。 */
public record AuditPageRequest(
        /* 页码。 */
        Integer pageNo,
        /* 每页记录数。 */
        Integer pageSize,
        /* 来源应用，可选。 */
        String sourceApp,
        /* 审计类别，可选。 */
        AuditTypeEnum type,
        /* 来源动作码，可选。 */
        String action,
        /* 执行结果，可选。 */
        AuditOutcomeEnum outcome,
        /* 操作者标识，可选。 */
        String actorId,
        /* 目标类型，可选。 */
        String targetType,
        /* 目标标识，可选。 */
        String targetId,
        /* 调用链标识，可选。 */
        String traceId,
        /* 查询起始时间。 */
        Long occurredFrom,
        /* 查询结束时间。 */
        Long occurredTo
) {
    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    public AuditPageRequest {
        pageNo = pageNo == null ? DEFAULT_PAGE_NO : pageNo;
        pageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }
}
