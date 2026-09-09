package com.co.kc.imchat.management.iam.model.io;

/** IAM 应用注册请求。 */
public record ApplicationRegisterRequest(
        String appKey,
        String name
) {
}
