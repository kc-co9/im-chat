package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * 绑定 operationId 与规范化管理命令内容的稳定摘要值对象。
 *
 * <p>摘要用于区分幂等重试和同一 operationId 被错误复用两种情况。它不用于恢复原始请求，
 * 也不承担权限校验或业务字段解析。</p>
 */
public record CommandDigest(
        /* 规范化管理命令内容的稳定摘要。 */
        String value) implements Serializable {
    public CommandDigest {
        AssertUtils.domainPropNotBlank("command digest must not be blank", value);
    }
}
