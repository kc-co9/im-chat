package com.co.kc.imchat.plugin.web.context;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 当前 HTTP 请求中允许跨线程传播的非安全元数据。 */
public record HttpRequestContext(
        /* 客户端地址。 */
        String clientAddress,
        /* 客户端 User-Agent。 */
        String userAgent
) {
    public HttpRequestContext {
        AssertUtils.argNotBlank("clientAddress must not be blank", clientAddress);
        AssertUtils.argNotBlank("userAgent must not be blank", userAgent);
    }
}
