package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * 一次权威集群变更的稳定幂等身份值对象。
 *
 * <p>管理请求、共识命令和操作历史都使用该标识关联同一变更。相同 operationId 必须绑定
 * 相同请求摘要；重复提交返回已有操作结果，摘要不一致则视为身份冲突。</p>
 */
public record ClusterOperationId(/* 一次集群变更的稳定幂等标识值 */ String value) implements Serializable {
    public ClusterOperationId {
        AssertUtils.domainPropNotBlank("cluster operation id must not be blank", value);
    }
}
