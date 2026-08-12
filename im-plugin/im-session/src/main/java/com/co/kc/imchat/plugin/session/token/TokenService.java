package com.co.kc.imchat.plugin.session.token;

/**
 * 会话令牌服务。
 * <p>
 * 负责令牌签发和解析，业务侧不直接感知具体令牌算法。
 */
public interface TokenService {

    /**
     * 根据令牌载荷生成客户端可携带的令牌字符串。
     *
     * @param tokenDTO 令牌载荷
     * @return 令牌字符串
     */
    String create(TokenDTO tokenDTO);

    /**
     * 解析令牌字符串。解析失败时返回 {@code null}，实现方不应向调用方抛出解析异常。
     *
     * @param token 令牌字符串
     * @return 令牌载荷，解析失败时返回 {@code null}
     */
    TokenDTO parse(String token);
}
