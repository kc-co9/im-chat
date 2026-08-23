package com.co.kc.imchat.plugin.session.token.model;

/**
 * Session Token 类型，用于隔离访问认证与凭证续期能力。
 */
public enum TokenType {
    /**
     * 用于访问受保护资源的短期 Token。
     */
    ACCESS,

    /**
     * 用于签发新 Token 对的长期 Token，不直接用于资源访问。
     */
    REFRESH
}
