package com.co.kc.imchat.management.ops.model.cqrs.query;

/** 查询既有 Broker operation，不据查询失败推断提交结果。 */
public record BrokerOperationQuery(
        /* 要查询的 operation 身份。 */
        String operationId) {
    /** 校验查询必须引用明确的 operation 身份。 */
    public BrokerOperationQuery {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("Invalid operationId");
        }
    }
}
