package com.co.kc.imchat.common.exception;

import com.co.kc.imchat.common.utils.AssertUtils;
import lombok.Getter;

import java.io.Serial;

/**
 * 跨 RPC 边界传递的稳定失败信息。
 *
 * <p>只携带调用方可依赖的错误码和安全消息，不暴露 Provider 内部异常原因。</p>
 */
@Getter
public class RpcException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    public RpcException(int code, String message) {
        super(message);
        AssertUtils.argTrue("code must be positive", code > 0);
        AssertUtils.argNotBlank("message must not be blank", message);
        this.code = code;
    }
}
