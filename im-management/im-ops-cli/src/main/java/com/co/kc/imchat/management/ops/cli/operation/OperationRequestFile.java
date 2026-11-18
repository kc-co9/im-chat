package com.co.kc.imchat.management.ops.cli.operation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.management.ops.cli.security.TrustedFilePath;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * 非敏感操作请求记录；首次提交前持久化，恢复时保留原操作身份和固定语义。
 */
public final class OperationRequestFile {

    /**
     * 操作记录写入使用统一 JSON 映射器；读取使用 JsonUtils 的严格对象入口。
     */
    private static final ObjectMapper JSON = JsonUtils.getMapper();

    private OperationRequestFile() {
    }

    /**
     * 以 owner-only 新文件保存不可变 operation 请求，拒绝覆盖既有记录。
     */
    public static void write(Path path, Request request) throws IOException {
        path = TrustedFilePath.resolve(path, "Operation request ancestor must be a directory without symlinks",
                "Operation request directory must belong to the current user or root",
                "Operation request directory must not be writable by other users");
        byte[] json = JSON.writeValueAsBytes(request);
        if (json.length > 65536) {
            throw new IOException("Operation request exceeds 64 KiB");
        }
        try (FileChannel channel = FileChannel.open(path,
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
            ByteBuffer bytes = ByteBuffer.wrap(json);
            while (bytes.hasRemaining()) {
                channel.write(bytes);
            }
            channel.force(true);
        }
    }

    /**
     * 读取并严格反序列化原始请求，保留 operationId 和 expectedEpoch。
     */
    public static Request read(Path path) throws IOException {
        path = TrustedFilePath.resolve(path, "Operation request ancestor must be a directory without symlinks",
                "Operation request directory must belong to the current user or root",
                "Operation request directory must not be writable by other users");
        TrustedFilePath.requireOwnerOnly(path, "Operation request", "Operation request must belong to the current user",
                "Operation request must be owner-only");
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] json = input.readNBytes(65537);
            if (json.length > 65536) {
                throw new IOException("Operation request exceeds 64 KiB");
            }
            Request request = JsonUtils.fromStrictJson(json, Request.class);
            if (request == null) {
                throw new IOException("Operation request must not be null");
            }
            return request;
        }
    }

    /**
     * 可恢复的非敏感操作请求；不包含 Token 或其他凭据，environment 是明确配置的 Ops HTTPS origin。
     */
    public record Request(
            /* 目标环境的 Ops HTTPS origin。 */
            String environment,
            /* 显式指定且重试不变的操作身份。 */
            String operationId,
            /* 显式预期 epoch，恢复时不刷新。 */
            @JsonProperty(required = true) long expectedEpoch,
            /* 精确目标 Broker。 */
            String brokerId,
            /* 固定成员操作白名单之一。 */
            String action,
            /* 成员操作的原请求端点，重试时不得替换。 */
            String membershipEndpoint,
            /* 其余操作的显式原因，不含凭据。 */
            String reason
    ) {
        /**
         * 校验环境、操作白名单以及不同操作所允许的参数组合。
         */
        public Request {
            if (operationId == null || operationId.isBlank() || brokerId == null || brokerId.isBlank()
                    || expectedEpoch < 0) {
                throw new IllegalArgumentException("Operation identity, broker and nonnegative epoch are required");
            }
            URI origin = environment == null ? URI.create("") : URI.create(environment);
            if (!"https".equals(origin.getScheme()) || origin.getHost() == null || origin.getRawUserInfo() != null
                    || origin.getRawQuery() != null || origin.getRawFragment() != null
                    || !origin.getRawPath().isEmpty() || origin.getPort() == 0 || origin.getPort() > 65535) {
                throw new IllegalArgumentException("Operation environment must be an HTTPS origin");
            }
            if (action == null || !Set.of("add-learner", "complete-learner-sync", "promote", "demote", "remove")
                    .contains(action)) {
                throw new IllegalArgumentException("Unsupported membership action");
            }
            if (membershipEndpoint == null || membershipEndpoint.isBlank()) {
                throw new IllegalArgumentException("Membership operation requires a membership endpoint");
            }
            if (action.equals("add-learner") && reason != null) {
                throw new IllegalArgumentException("Reason is not valid for learner addition");
            }
        }
    }
}
