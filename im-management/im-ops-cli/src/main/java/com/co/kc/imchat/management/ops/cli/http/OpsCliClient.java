package com.co.kc.imchat.management.ops.cli.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.co.kc.imchat.management.ops.cli.auth.CliLoginSession;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.management.ops.cli.model.io.IamTokenResponse;
import com.co.kc.imchat.management.ops.cli.model.io.OpsResponse;
import com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Objects;

/**
 * 前台会话的窄 IAM／Ops HTTP 客户端；不暴露 Token 或自动重放操作。
 */
public final class OpsCliClient {

    /** IAM 服务 HTTPS origin。 */
    private final URI iamOrigin;
    /** Ops 服务 HTTPS origin。 */
    private final URI opsOrigin;
    /** 当前登录会话的短期 access token。 */
    private String accessToken;
    /** access token 的绝对过期时间。 */
    private Instant tokenDeadline;
    /** OAuth public Client ID。 */
    private final String clientId;
    /** 禁止并发命令的窄 HTTP 客户端。 */
    private final HttpClient http;
    /** 串行化 Token、查询和提交操作。 */
    private final ReentrantLock command = new ReentrantLock();

    /** 校验两个 HTTPS origin 和无 Cookie、无重定向的窄 HTTP 客户端。 */
    public OpsCliClient(URI iamOrigin, URI opsOrigin, String clientId, HttpClient http) {
        Objects.requireNonNull(http, "http");
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        for (URI origin : new URI[]{iamOrigin, opsOrigin}) {
            if (origin == null || !"https".equals(origin.getScheme()) || origin.getHost() == null
                    || origin.getRawUserInfo() != null || origin.getRawQuery() != null
                    || origin.getRawFragment() != null || !origin.getRawPath().isEmpty()
                    || origin.getPort() == 0 || origin.getPort() > 65535) {
                throw new IllegalArgumentException("IAM and Ops must be HTTPS origins");
            }
        }
        if (http.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("HTTP redirects must be disabled");
        }
        Duration connectionTimeout = http.connectTimeout().orElse(Duration.ZERO);
        if (connectionTimeout.isZero() || connectionTimeout.isNegative()
                || connectionTimeout.compareTo(Duration.ofSeconds(10)) > 0) {
            throw new IllegalArgumentException("HTTP connection timeout must be positive and at most 10 seconds");
        }
        if (http.cookieHandler().isPresent() || http.authenticator().isPresent()) {
            throw new IllegalArgumentException("HTTP cookies and authenticators must be disabled");
        }
        this.iamOrigin = iamOrigin;
        this.opsOrigin = opsOrigin;
        this.clientId = clientId;
        this.http = http;
    }

    /** 使用一次性 PKCE 会话交换短期 Bearer Token；失败时不会保留旧 Token。 */
    public void exchangeAuthorizationCode(CliLoginSession session, String code, URI redirectUri)
            throws IOException, InterruptedException {
        if (!command.tryLock()) {
            throw new IllegalStateException("Another HTTP command is in progress");
        }
        try {
            // 步骤一：清除旧会话，使用当前 PKCE verifier 交换短期 access token。
            accessToken = null;
            tokenDeadline = null;
            Instant started = Instant.now();
            String form = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&grant_type=authorization_code&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(redirectUri.toString(), StandardCharsets.UTF_8)
                    + "&code_verifier=" + URLEncoder.encode(session.verifier(), StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(iamOrigin.resolve("/oauth2/token"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            HttpResponse<byte[]> response = send(request);
            if (response.statusCode() != 200) {
                throw new IOException("IAM token exchange failed");
            }
            IamTokenResponse token = IamTokenResponse.read(response.body());
            // 步骤二：严格校验响应结构和有效期上限后才保存 Token。
            accessToken = token.accessToken();
            tokenDeadline = started.plusSeconds(token.expiresIn());
        } finally {
            command.unlock();
        }
    }

    /** 先清理进程内 Token，再尝试撤销远端授权；撤销失败会抛出异常。 */
    public void logout() throws IOException, InterruptedException {
        if (!command.tryLock()) {
            throw new IllegalStateException("Another HTTP command is in progress");
        }
        try {
            // 步骤一：先从内存移除 Token，再请求远端撤销，避免本地继续使用。
            String token = accessToken;
            accessToken = null;
            tokenDeadline = null;
            if (token == null) {
                return;
            }
            String form = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(iamOrigin.resolve("/oauth2/revoke"))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            try {
                if (send(request).statusCode() != 200) {
                    throw new IOException("Remote revocation was not confirmed");
                }
            } catch (IOException failure) {
                throw new IOException("Remote revocation was not confirmed");
            }
        } finally {
            command.unlock();
        }
    }

    /** 查询带版本信息的集群运行投影；服务端 401 会清理本地认证状态。 */
    public JsonNode queryCluster() throws IOException, InterruptedException {
        return query("/api/cli/cluster-state");
    }

    private JsonNode query(String path) throws IOException, InterruptedException {
        if (!command.tryLock()) {
            throw new IllegalStateException("Another HTTP command is in progress");
        }
        try {
            if (accessToken == null || !Instant.now().isBefore(tokenDeadline)) {
                accessToken = null;
                tokenDeadline = null;
                throw new IllegalStateException("Login is required");
            }
            // 查询只携带当前短期 Bearer，并要求固定响应 envelope。
            HttpRequest request = HttpRequest.newBuilder(opsOrigin.resolve(path))
                    .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + accessToken).GET().build();
            HttpResponse<byte[]> response = send(request);
            if (response.statusCode() == 401) {
                accessToken = null;
                tokenDeadline = null;
                throw new IllegalStateException("Login is required");
            }
            if (response.statusCode() != 200) {
                throw new IOException("Ops cluster query failed");
            }
            return opsData(response.body());
        } finally {
            command.unlock();
        }
    }

    private JsonNode opsData(byte[] bytes) throws IOException {
        // 统一解析服务端 envelope，调用方只接收已确认的 data 对象。
        return OpsResponse.read(bytes).data();
    }

    /** 保存不可变操作记录后提交同一 operationId；网络不确定时返回 UNKNOWN。 */
    public JsonNode submit(Path record,
                           OperationRequestFile.Request request)
            throws IOException, InterruptedException {
        if (!command.tryLock()) {
            throw new IllegalStateException("Another HTTP command is in progress");
        }
        try {
            if (accessToken == null || !Instant.now().isBefore(tokenDeadline)) {
                accessToken = null;
                tokenDeadline = null;
                throw new IllegalStateException("Login is required");
            }
            if (!opsOrigin.equals(URI.create(request.environment()))) {
                throw new IllegalArgumentException("Operation environment does not match configured Ops");
            }
            // 先持久化不可变请求记录，再发起网络提交以支持显式恢复。
            OperationRequestFile.write(record, request);
            return submitRequest(request);
        } finally {
            command.unlock();
        }
    }

    /** 读取原始操作记录并显式重试，不刷新 epoch 或生成新的 operationId。 */
    public JsonNode retry(Path record) throws IOException, InterruptedException {
        if (!command.tryLock()) {
            throw new IllegalStateException("Another HTTP command is in progress");
        }
        try {
            if (accessToken == null || !Instant.now().isBefore(tokenDeadline)) {
                accessToken = null;
                tokenDeadline = null;
                throw new IllegalStateException("Login is required");
            }
            OperationRequestFile.Request request = OperationRequestFile.read(record);
            if (!opsOrigin.equals(URI.create(request.environment()))) {
                throw new IllegalArgumentException("Operation environment does not match configured Ops");
            }
            return submitRequest(request);
        } finally {
            command.unlock();
        }
    }

    private JsonNode submitRequest(OperationRequestFile.Request request)
            throws IOException, InterruptedException {
        // 使用记录中的 operationId、epoch 和目标，不自动刷新或生成新身份。
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", request.operationId());
        payload.put("expectedEpoch", request.expectedEpoch());
        payload.put("membershipEndpoint", request.membershipEndpoint());
        if (request.reason() != null) {
            payload.put("reason", request.reason());
        }
        String broker = URLEncoder.encode(request.brokerId(), StandardCharsets.UTF_8).replace("+", "%20");
        HttpRequest submission = HttpRequest.newBuilder(opsOrigin.resolve(
                "/api/cli/brokers/" + broker + "/raft/" + request.action()))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(payload), StandardCharsets.UTF_8)).build();
        try {
            HttpResponse<byte[]> response = send(submission);
            if (response.statusCode() == 401) {
                accessToken = null;
                tokenDeadline = null;
                throw new IllegalStateException("Login is required");
            }
            if (response.statusCode() == 403) {
                throw new IllegalStateException("Ops permission denied");
            }
            if (response.statusCode() != 200) {
                throw new IOException("Ops submission was not confirmed; retain the original operation record");
            }
            JsonNode data = opsData(response.body());
            if (!data.path("operationId").isTextual()
                    || !request.operationId().equals(data.path("operationId").textValue())
                    || !data.path("status").isTextual()
                    || !Set.of("APPLIED", "DUPLICATE", "CONFLICT", "REJECTED", "NOT_LEADER", "UNAVAILABLE", "IN_PROGRESS")
                    .contains(data.path("status").textValue())) {
                throw new IOException("Invalid Ops submission response");
            }
            return data;
        } catch (IOException failure) {
            return JsonUtils.convertValue(Map.of(
                    "operationId", request.operationId(),
                    "status", "UNKNOWN"), JsonNode.class);
        }
    }

    /** 查询指定 operation；调用方必须提供明确且非空的 operationId。 */
    public JsonNode queryOperation(String operationId) throws IOException, InterruptedException {
        if (operationId == null || operationId.isBlank() || operationId.equals(".") || operationId.equals("..")) {
            throw new IllegalArgumentException("Explicit operationId is required");
        }
        return query("/api/cli/operations/" + URLEncoder.encode(operationId, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    private HttpResponse<byte[]> send(HttpRequest request) throws IOException, InterruptedException {
        // 为响应体和总等待时间同时设置上限，超限时取消异步请求。
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        CompletableFuture<HttpResponse<byte[]>> exchange = http.sendAsync(request,
                info -> new LimitedBodySubscriber());
        try {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new TimeoutException();
            }
            HttpResponse<byte[]> response = exchange.get(remaining, TimeUnit.NANOSECONDS);
            if (response.body().length > 1048576) {
                throw new IOException("HTTP response exceeds 1 MiB");
            }
            return response;
        } catch (TimeoutException failure) {
            exchange.cancel(true);
            throw new HttpTimeoutException("HTTP request exceeded 10 seconds");
        } catch (InterruptedException failure) {
            exchange.cancel(true);
            throw failure;
        } catch (ExecutionException failure) {
            throw new IOException("HTTP request failed");
        }
    }
}
