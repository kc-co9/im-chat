package com.co.kc.imchat.management.ops.cli.auth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单次登录拥有的固定 IPv4 loopback 回调监听器。仅处理授权回调，不提供其他本地 HTTP 功能。
 */
public final class LoopbackAuthorizationCallback implements AutoCloseable {

    /** 单次回调请求允许读取的最大字节数。 */
    private static final int MAX_REQUEST_BYTES = 8192;
    /** 绑定的 loopback 监听器。 */
    private final ServerSocket listener;
    /** 是否已经有线程等待当前登录回调。 */
    private final AtomicBoolean awaiting = new AtomicBoolean();
    /** 当前正在读取的连接，用于 close 中断等待。 */
    private Socket activeConnection;

    /** 绑定固定 IPv4 loopback 端口，端口冲突直接返回给 CLI。 */
    public LoopbackAuthorizationCallback(int port) throws IOException {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("A fixed callback port is required");
        }
        listener = new ServerSocket();
        try {
            listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 1);
        } catch (IOException exception) {
            listener.close();
            throw exception;
        }
    }

    /**
     * 在会话原有的 120 秒预算内单次交付授权码。非法请求只关闭该连接，不产生成功结果；
     * 成功、超时或技术失败均关闭监听器。调用 close 可中断 accept 及在途读取。
     */
    public String awaitAuthorizationCode(CliLoginSession session) throws IOException {
        Objects.requireNonNull(session, "session");
        if (!awaiting.compareAndSet(false, true)) {
            throw new IllegalStateException("Authorization callback already awaited");
        }
        long budgetNanos = Math.max(0, Math.min(Duration.ofSeconds(120).toNanos(),
                Duration.between(Instant.now(), session.deadline()).toNanos()));
        long deadlineNanos = System.nanoTime() + budgetNanos;
        try {
            // 步骤一：沿用登录会话的原始截止时间，不因非法请求重新获得预算。
            while (true) {
                listener.setSoTimeout(remainingMillis(session, deadlineNanos));
                try (Socket connection = listener.accept()) {
                    synchronized (this) {
                        if (listener.isClosed()) {
                            throw new IOException("Authorization callback closed");
                        }
                        activeConnection = connection;
                    }
                    if (!connection.getInetAddress().isLoopbackAddress()) {
                        continue;
                    }
                    // 步骤二：只读取有限大小的 HTTP 请求头，并拒绝非 loopback 来源。
                    String request = readRequest(connection, session, deadlineNanos);
                    Map<String, String> parameters;
                    // 步骤三：校验 state/code 后只交付一次授权码。
                    try {
                        parameters = parseRequest(request);
                    } catch (IllegalArgumentException exception) {
                        respond(connection, false);
                        continue;
                    }
                    String code;
                    try {
                        remainingMillis(session, deadlineNanos);
                        code = session.acceptAuthorizationCode(parameters.get("state"), parameters.get("code"));
                    } catch (IllegalArgumentException exception) {
                        respond(connection, false);
                        continue;
                    }
                    try {
                        respond(connection, true);
                    } catch (IOException exception) {
                        /* 授权事实已验证并消费，浏览器断开不能丢弃交付结果。 */
                    }
                    return code;
                } finally {
                    synchronized (this) {
                        activeConnection = null;
                    }
                }
            }
        } finally {
            close();
        }
    }

    private String readRequest(Socket connection, CliLoginSession session, long deadlineNanos) throws IOException {
        // 读取到完整请求头或达到上限即停止，避免本地回调成为无界 HTTP 服务。
        InputStream input = connection.getInputStream();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int tail = 0;
        while (bytes.size() < MAX_REQUEST_BYTES) {
            connection.setSoTimeout(remainingMillis(session, deadlineNanos));
            int value = input.read();
            if (value < 0) {
                return "";
            }
            bytes.write(value);
            tail = (tail << 8) | value;
            if (tail == 0x0d0a0d0a) {
                return bytes.toString(StandardCharsets.US_ASCII);
            }
        }
        return "";
    }

    private Map<String, String> parseRequest(String request) {
        // 严格限制请求方法、路径、Host、请求体和重复参数。
        String[] lines = request.split("\r\n");
        String[] requestLine = lines[0].split(" ", -1);
        if (!request.endsWith("\r\n\r\n") || requestLine.length != 3
                || !requestLine[0].equals("GET") || !requestLine[2].equals("HTTP/1.1")
                || !requestLine[1].startsWith("/oauth/callback?") || requestLine[1].contains("#")) {
            throw new IllegalArgumentException("Invalid callback request");
        }
        int hosts = 0;
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            int colon = line.indexOf(':');
            if (colon < 1 || line.startsWith(" ") || line.startsWith("\t")) {
                throw new IllegalArgumentException("Invalid callback header");
            }
            String name = line.substring(0, colon);
            if (name.equalsIgnoreCase("Host")) {
                hosts++;
                if (!line.substring(colon + 1).trim().equals("127.0.0.1:" + listener.getLocalPort())) {
                    throw new IllegalArgumentException("Invalid callback host");
                }
            }
            if (name.equalsIgnoreCase("Transfer-Encoding")
                    || (name.equalsIgnoreCase("Content-Length") && !line.substring(colon + 1).trim().equals("0"))) {
                throw new IllegalArgumentException("Callback body is not supported");
            }
        }
        if (hosts != 1) {
            throw new IllegalArgumentException("A single callback host is required");
        }
        Map<String, String> parameters = new HashMap<>();
        for (String parameter : requestLine[1].substring("/oauth/callback?".length()).split("&", -1)) {
            String[] pair = parameter.split("=", 2);
            if (pair.length != 2) {
                throw new IllegalArgumentException("Invalid callback parameter");
            }
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            if (parameters.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate callback parameter");
            }
        }
        return parameters;
    }

    private int remainingMillis(CliLoginSession session, long deadlineNanos) throws SocketTimeoutException {
        // 同时受单调时钟和会话绝对截止时间约束，取更早者。
        long remainingNanos = Math.min(deadlineNanos - System.nanoTime(),
                Duration.between(Instant.now(), session.deadline()).toNanos());
        if (remainingNanos <= 0) {
            throw new SocketTimeoutException("Authorization login expired");
        }
        return (int) Math.min(120000, Math.max(1, (remainingNanos + 999999) / 1000000));
    }

    private void respond(Socket connection, boolean accepted) throws IOException {
        // 回调只返回空响应，避免把授权码或其他敏感信息写回浏览器。
        String status = accepted ? "200 OK" : "400 Bad Request";
        connection.getOutputStream().write(("HTTP/1.1 " + status
                + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            listener.close();
        } finally {
            if (activeConnection != null) {
                activeConnection.close();
            }
        }
    }
}
