package com.co.kc.imchat.broker.domain.authority.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * 绑定一次 Lease prepare、grant 与 install 流程的随机值对象。
 *
 * <p>nonce 用于把同一次租约申请的多个阶段关联起来，并阻止旧申请的 grant 被错误安装。
 * 它是不透明凭据，只做相等性匹配，不承担时间、权限或路由 epoch 语义。</p>
 */
public record LeaseNonce(
        /* 不透明且不可为空的随机值。 */
        String value) implements Serializable {

    public LeaseNonce {
        AssertUtils.domainPropNotBlank("lease nonce must not be blank", value);
    }
}
