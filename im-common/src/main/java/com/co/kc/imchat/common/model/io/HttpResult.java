package com.co.kc.imchat.common.model.io;

import com.co.kc.imchat.common.constant.HttpErrorCode;
import com.co.kc.imchat.common.constant.HttpResultCode;
import com.co.kc.imchat.common.exception.BaseException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

/**
 * HTTP API 统一返回结果封装。
 * <p>
 * 仅用于 HTTP 请求响应，实时协议下行帧使用 {@link FrameResponse}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HttpResult<T> {

    /**
     * HTTP 业务结果码。
     */
    private Integer code;

    /**
     * HTTP 业务结果描述。
     */
    private String msg;

    /**
     * HTTP 业务响应数据。
     */
    private T data;

    public static HttpResult<Map<String, Object>> success() {
        return new HttpResult<>(HttpResultCode.SUCCESS.getCode(), HttpResultCode.SUCCESS.getMsg(), Collections.emptyMap());
    }

    public static <T> HttpResult<T> success(T data) {
        return new HttpResult<>(HttpResultCode.SUCCESS.getCode(), HttpResultCode.SUCCESS.getMsg(), data);
    }

    public static HttpResult<Map<String, Object>> error(HttpErrorCode errorCode) {
        return new HttpResult<>(errorCode.getCode(), errorCode.getMsg(), Collections.emptyMap());
    }

    public static HttpResult<Map<String, Object>> error(HttpErrorCode errorCode, String msg) {
        return new HttpResult<>(errorCode.getCode(), msg, Collections.emptyMap());
    }

    public static HttpResult<Map<String, Object>> error(BaseException ex) {
        return new HttpResult<>(ex.getCode(), ex.getMsg(), Collections.emptyMap());
    }
}
