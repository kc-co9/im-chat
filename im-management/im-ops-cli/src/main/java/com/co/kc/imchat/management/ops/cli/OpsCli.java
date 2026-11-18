package com.co.kc.imchat.management.ops.cli;

import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.management.ops.cli.auth.CliLoginSession;
import com.co.kc.imchat.management.ops.cli.auth.LoopbackAuthorizationCallback;
import com.co.kc.imchat.management.ops.cli.config.CliConfiguration;
import com.co.kc.imchat.management.ops.cli.http.OpsCliClient;
import com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile;
import com.co.kc.imchat.management.ops.cli.security.TrustedFilePath;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.BindException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

/**
 * 前台交互 CLI 入口。
 */
public final class OpsCli {

    /**
     * CLI 输入输出使用的严格 JSON 映射器。
     */
    private static final ObjectMapper JSON = JsonUtils.getMapper();

    private OpsCli() {
    }

    /**
     * 解析前台命令并编排登录、查询、提交和显式重试流程。
     */
    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.println("Usage: java -jar im-ops-cli.jar --config <owner-readable JSON file>");
            System.out.println("Commands: login | logout | exit | cluster show | operation show <operationId>");
            System.out.println("raft <add-learner|complete-learner-sync|promote|demote|remove> --environment <Ops HTTPS origin>"
                    + " --broker <brokerId> --operation-id <operationId> --expected-epoch <epoch> --record <new-file>"
                    + " --membership-endpoint <host:port>"
                    + " [--reason <single-token> (optional for other actions)] --confirm");
            System.out.println("operation retry --record <original-file> --confirm");
            System.out.println("Whitespace-separated arguments in the displayed order; quoting and spaces in values are unsupported.");
            System.out.println("APPLIED/DUPLICATE confirm submission, not operation completion; use operation show.");
            System.out.println("UNKNOWN: retain the original record and operationId; query before explicit retry. No automatic replay.");
            return;
        }
        if (args.length != 2 || !args[0].equals("--config")) {
            System.err.println("CLI configuration is required");
            System.exit(2);
            return;
        }
        try {
            // 步骤一：读取并校验仅当前用户可读的 CLI 配置。
            CliConfiguration config = configuration(Path.of(args[1]));
            try (HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofSeconds(10)).build()) {
                OpsCliClient client = new OpsCliClient(URI.create(config.iamOrigin()),
                        URI.create(config.opsOrigin()), config.clientId(), http);
                BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                try {
                    String line;
                    while ((line = input.readLine()) != null && !line.equals("exit")) {
                        try {
                            // 步骤二：按固定命令语法执行只读查询或显式写操作。
                            if (line.equals("cluster show")) {
                                System.out.println(JSON.writeValueAsString(client.queryCluster()));
                            } else if (line.startsWith("operation show ") && line.split(" ").length == 3) {
                                System.out.println(JSON.writeValueAsString(client.queryOperation(line.split(" ")[2])));
                            } else if (line.equals("login")) {
                                try (LoopbackAuthorizationCallback callback = new LoopbackAuthorizationCallback(
                                        config.callbackPort())) {
                                    CliLoginSession session = CliLoginSession.start();
                                    URI redirect = URI.create("http://127.0.0.1:" + config.callbackPort()
                                            + "/oauth/callback");
                                    String authorization = config.iamOrigin() + "/oauth2/authorize?"
                                            + "response_type=code&client_id=" + encode(config.clientId())
                                            + "&redirect_uri=" + encode(redirect.toString())
                                            + "&scope=" + encode(config.scope())
                                            + "&state=" + encode(session.state()) + "&code_challenge=" + encode(session.challenge())
                                            + "&code_challenge_method=S256";
                                    boolean opened = false;
                                    try {
                                        if (Desktop.isDesktopSupported()
                                                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                                            Desktop.getDesktop().browse(URI.create(authorization));
                                            opened = true;
                                        }
                                    } catch (IOException | UnsupportedOperationException | SecurityException failure) {
                                        opened = false;
                                    }
                                    if (!opened) {
                                        System.out.println(authorization);
                                    }
                                    // 步骤三：通过 loopback 回调完成 PKCE 授权码交付，再交换短期 Token。
                                    String code = callback.awaitAuthorizationCode(session);
                                    client.exchangeAuthorizationCode(session, code, redirect);
                                    System.out.println("Login completed");
                                } catch (BindException failure) {
                                    System.err.println("Fixed callback port is unavailable");
                                }
                            } else if (line.startsWith("operation retry ")) {
                                String[] parts = line.split(" ");
                                if (parts.length != 5 || !parts[2].equals("--record") || !parts[4].equals("--confirm")) {
                                    throw new IllegalArgumentException("Original record and confirmation are required");
                                }
                                Path record = Path.of(parts[3]);
                                OperationRequestFile.Request request = OperationRequestFile.read(record);
                                System.out.println("Environment: " + request.environment() + "; broker: "
                                        + request.brokerId() + "; action: " + request.action() + "; operationId: " + request.operationId());
                                try {
                                    JsonNode result = client.retry(record);
                                    System.out.println(JSON.writeValueAsString(result));
                                    verifySubmissionResult(result, record, request.operationId());
                                } catch (InterruptedException failure) {
                                    printRecoveryHint(record, request.operationId());
                                    throw failure;
                                } catch (IllegalStateException failure) {
                                    System.err.println(failure.getMessage());
                                    throw new IOException("Retry command failed");
                                }
                            } else if (line.startsWith("raft promote ") || line.startsWith("raft demote ")
                                    || line.startsWith("raft remove ") || line.startsWith("raft complete-learner-sync ")
                                    || line.startsWith("raft add-learner ")) {
                                String[] parts = line.split(" ");
                                int reasonIndex = 14;
                                boolean reason = !parts[1].equals("add-learner") && parts.length == reasonIndex + 3;
                                int confirmationIndex = reason ? reasonIndex + 2 : reasonIndex;
                                if (parts.length != confirmationIndex + 1 || !parts[2].equals("--environment")
                                        || !parts[4].equals("--broker") || !parts[6].equals("--operation-id")
                                        || !parts[8].equals("--expected-epoch") || !parts[10].equals("--record")
                                        || !parts[12].equals("--membership-endpoint")
                                        || (reason && !parts[reasonIndex].equals("--reason"))
                                        || !parts[confirmationIndex].equals("--confirm")) {
                                    throw new IllegalArgumentException("Explicit write parameters and confirmation are required");
                                }
                                OperationRequestFile.Request request = new OperationRequestFile.Request(
                                        parts[3], parts[7], Long.parseLong(parts[9]), parts[5], parts[1],
                                        parts[13], reason ? parts[reasonIndex + 1] : null);
                                System.out.println("Environment: " + request.environment() + "; broker: "
                                        + request.brokerId() + "; action: " + request.action() + "; operationId: " + request.operationId());
                                try {
                                    // 步骤四：先保存原始 operation 记录，再提交同一 operationId。
                                    JsonNode result = client.submit(Path.of(parts[11]), request);
                                    System.out.println(JSON.writeValueAsString(result));
                                    verifySubmissionResult(result, Path.of(parts[11]), request.operationId());
                                } catch (InterruptedException failure) {
                                    printRecoveryHint(Path.of(parts[11]), request.operationId());
                                    throw failure;
                                } catch (IllegalStateException failure) {
                                    System.err.println(failure.getMessage());
                                    throw new IOException("Write command failed");
                                }
                            } else if (line.equals("logout")) {
                                try {
                                    client.logout();
                                } catch (IOException failure) {
                                    System.err.println("Remote revocation was not confirmed");
                                }
                            } else {
                                throw new IllegalArgumentException("Unsupported command");
                            }
                        } catch (IllegalStateException failure) {
                            System.err.println(failure.getMessage());
                        }
                    }
                } finally {
                    try {
                        client.logout();
                    } catch (IOException failure) {
                        System.err.println("Remote revocation was not confirmed");
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        System.err.println("Remote revocation was not confirmed; cleanup interrupted");
                    }
                }
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            System.err.println("CLI interrupted; retain any original operation record");
            System.exit(1);
        } catch (IOException | IllegalArgumentException failure) {
            System.err.println("CLI configuration or request failed");
            System.exit(1);
        }
    }

    private static String encode(String value) {
        // 授权 URL 的每个参数独立编码，避免值中的保留字符改变协议结构。
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 读取、限制并校验 CLI 配置文件，拒绝软链接和宽松文件权限。
     */
    private static CliConfiguration configuration(Path path) throws IOException {
        path = TrustedFilePath.resolve(path, "Configuration ancestor must be a directory without symlinks",
                "Configuration directory must be trusted",
                "Configuration directory must be trusted and not writable by other users");
        TrustedFilePath.requireOwnerOnly(path, "Configuration");
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(65537);
        }
        if (bytes.length > 65536) {
            throw new IOException("Configuration exceeds 64 KiB");
        }
        return CliConfiguration.read(bytes);
    }

    /**
     * 统一处理写提交结果，并为未知提交保留原始恢复上下文。
     */
    private static void verifySubmissionResult(JsonNode result, Path record, String operationId) throws IOException {
        if (result.path("status").asText().equals("IN_PROGRESS")) {
            System.err.println("Request is in progress locally; registration is unconfirmed; retain " + record
                    + "; operation show " + operationId + " may not find it before registration; query or retry the original record later; do not create a new operationId");
        }
        if (result.path("status").asText().equals("UNKNOWN")) {
            printRecoveryHint(record, operationId);
        }
        if (!Set.of("APPLIED", "DUPLICATE").contains(result.path("status").asText())) {
            throw new IOException("Submission was not confirmed successful");
        }
    }

    /**
     * 输出中断或未知提交后的显式查询和恢复步骤。
     */
    private static void printRecoveryHint(Path record, String operationId) {
        System.err.println("Submission is unconfirmed; retain " + record + "; query: operation show "
                + operationId + "; resubmit original record: operation retry --record "
                + record + " --confirm; do not create a new operationId");
    }
}
