package com.co.kc.imchat.common.constant;

/**
 * 项目角色固定的对端身份 URI 前缀，不支持部署配置覆盖。
 */
public final class PeerIdentityConstants {
    public static final String BROKER_URI_PREFIX = "spiffe://im-chat/broker/";
    public static final String GATEWAY_URI_PREFIX = "spiffe://im-chat/gateway/";
    public static final String MESSAGE_URI_PREFIX = "spiffe://im-chat/message/";
    public static final String ACCOUNT_URI_PREFIX = "spiffe://im-chat/account/";
    public static final String OPS_URI_PREFIX = "spiffe://im-chat/im-ops/";

    private PeerIdentityConstants() {
    }
}
