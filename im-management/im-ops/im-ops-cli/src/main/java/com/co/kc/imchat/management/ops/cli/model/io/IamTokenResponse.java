package com.co.kc.imchat.management.ops.cli.model.io;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.co.kc.imchat.common.utils.JsonUtils;

import java.io.IOException;

/** 已通过 IAM 响应边界校验的短期访问令牌。 */
public record IamTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Integer expiresIn,
        @JsonProperty("scope") String scope) {

    /** 解析并校验 IAM 换码响应，只返回可安全保存的 Token 对象。 */
    public static IamTokenResponse read(byte[] body) throws IOException {
        final IamTokenResponse response;
        try {
            response = JsonUtils.fromStrictJson(body, IamTokenResponse.class);
        } catch (IOException failure) {
            throw invalid();
        }
        if (response == null) {
            throw invalid();
        }
        response.validate();
        return response;
    }

    /** 校验 Token 字符集、类型和有效期，避免把不可信响应写入会话状态。 */
    private void validate() throws IOException {
        if (accessToken == null || !accessToken.matches("[A-Za-z0-9._~+/-]+=*")) {
            throw invalid();
        }
        if (!"Bearer".equalsIgnoreCase(tokenType)) {
            throw invalid();
        }
        if (expiresIn == null || expiresIn <= 0 || expiresIn > 300) {
            throw invalid();
        }
    }

    private static IOException invalid() {
        return new IOException("Invalid IAM token response");
    }
}
