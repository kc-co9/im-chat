package com.co.kc.imchat.management.ops.cli.model.io;

import com.co.kc.imchat.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/** Ops 接口统一响应 envelope；只有 code 为零且 data 为对象时才交给调用方。 */
public record OpsResponse(
        /* 服务端业务状态码。 */
        Integer code,
        /* 服务端提示文本，成功响应也可能携带。 */
        String msg,
        /* 查询或提交结果对象。 */
        JsonNode data) {

    /** 使用严格对象解析，拒绝未知字段、重复字段和尾随 JSON。 */
    public static OpsResponse read(byte[] body) throws IOException {
        final OpsResponse response;
        try {
            response = JsonUtils.fromStrictJson(body, OpsResponse.class);
        } catch (IOException failure) {
            // 对调用方统一暴露协议错误，避免把服务端响应内容或解析细节泄露到 CLI 输出。
            throw new IOException("Invalid Ops response");
        }
        if (response == null || response.code == null || response.code != 0
                || response.data == null || !response.data.isObject()) {
            throw new IOException("Invalid Ops response");
        }
        return response;
    }
}
