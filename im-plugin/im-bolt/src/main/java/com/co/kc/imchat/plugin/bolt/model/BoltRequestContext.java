package com.co.kc.imchat.plugin.bolt.model;

import java.util.Set;

/**
 * 真实连接来源事实。peerIdentity仅在完整URI集合恰有一项时提供该项，不回退Subject。
 * 严格准入使用tlsVerified与叶证书完整peerUriIdentities按固定命名空间唯一选择，不从负载补充。
 */
public record BoltRequestContext(
        /* 连接对端地址，包含端口，用于来源诊断，不作为认证身份。 */
        String remoteAddress,
        /* 连接对端主机，不包含端口，受限开发模式可据此核对来源。 */
        String remoteHost,
        /* 对端证书完整 URI 集合仅有一项时的身份；不唯一或不存在时为空，不回退 Subject。 */
        String peerIdentity,
        /* 当前连接是否已通过受控 TLS 会话校验；业务授权仍需独立判断。 */
        boolean tlsVerified,
        /* 对端叶证书提供的完整 URI 身份集合，按命名空间执行唯一匹配，构造时防御性复制。 */
        Set<String> peerUriIdentities,
        /* 是否为受控开发明文连接；不表示证书认证通过或已获业务授权。 */
        boolean developmentPlaintext) {
    public BoltRequestContext {
        peerUriIdentities = Set.copyOf(peerUriIdentities);
    }
}
