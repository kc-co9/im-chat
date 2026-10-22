package com.co.kc.imchat.plugin.bolt.core;

import com.alipay.remoting.Connection;
import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.exception.InvokeTimeoutException;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException.Failure;
import com.co.kc.imchat.plugin.bolt.model.BoltRequest;
import com.co.kc.imchat.plugin.bolt.model.BoltResponse;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;

public class BoltClientInvoker implements BoltInvoker {
    private final RpcClient rpcClient;
    private final LongSupplier monotonicNanos;

    public BoltClientInvoker(RpcClient rpcClient, LongSupplier monotonicNanos) {
        this.rpcClient = rpcClient;
        this.monotonicNanos = monotonicNanos;
    }

    @Override
    public <T, R> R invoke(String address, String service, String operation, T request,
                           Class<R> responseType, int timeoutMillis) {
        try {
            String payload = JsonUtils.toJson(request);
            Object response = rpcClient.invokeSync(address, new BoltRequest(service, operation, payload), timeoutMillis);
            if (!(response instanceof BoltResponse boltResponse)) {
                throw new IllegalStateException("Unexpected Bolt response type: " + response);
            }
            if (!boltResponse.isSuccess()) {
                throw new IllegalStateException("Bolt response failed, code=" + boltResponse.getCode()
                        + ", message=" + boltResponse.getMessage());
            }
            if (responseType == Void.class) {
                return null;
            }
            return JsonUtils.fromJson(boltResponse.getData(), responseType);
        } catch (Exception ex) {
            throw new IllegalStateException("Bolt invocation failed: " + address + " " + service + "#" + operation, ex);
        }
    }

    @Override
    public <T, R> R invokeAuthenticated(String address, String service, String operation, T request,
                                        Class<R> responseType, int timeoutMillis,
                                        String expectedPeerIdentity, String identityPrefix) {
        long startedAtNanos = monotonicNanos.getAsLong();
        Failure failurePhase = Failure.LOCAL;
        try {
            // 1. 校验身份约束并序列化；本机准备也消耗同一接受预算。
            if (timeoutMillis <= 0 || identityPrefix == null || identityPrefix.isBlank()
                    || expectedPeerIdentity == null || !expectedPeerIdentity.startsWith(identityPrefix)
                    || expectedPeerIdentity.length() == identityPrefix.length()) {
                throw new IllegalArgumentException("Invalid authentication identity or timeout");
            }
            String payload = JsonUtils.toJson(request);
            int connectionBudget = remainingMillis(startedAtNanos, timeoutMillis);
            Connection connection;
            // 2. 获取池内连接；迟到获取或迟到失败属于本机预算耗尽，不作为不可达证据。
            failurePhase = Failure.TRANSPORT_UNAVAILABLE;
            try {
                connection = rpcClient.getConnection(address, connectionBudget);
            } catch (Exception ex) {
                if (budgetExpired(startedAtNanos, timeoutMillis)) {
                    throw new BoltInvocationException(Failure.LOCAL, "Connection acquisition exceeded caller budget", ex);
                }
                throw ex;
            }
            remainingMillis(startedAtNanos, timeoutMillis);
            // 3. 在该连接上完成握手和唯一 URI 身份验证，不回退到地址或载荷身份。
            failurePhase = Failure.AUTHENTICATION;
            verifyPeerIdentity(connection, startedAtNanos, timeoutMillis, expectedPeerIdentity, identityPrefix);
            int requestBudget = remainingMillis(startedAtNanos, timeoutMillis);
            // 4. 使用刚验证过的同一连接调用，扣除之前阶段已使用的预算。
            failurePhase = Failure.TRANSPORT_UNAVAILABLE;
            Object response = rpcClient.invokeSync(connection, new BoltRequest(service, operation, payload), requestBudget);
            remainingMillis(startedAtNanos, timeoutMillis);
            // 5. 校验并解码响应；解码后的迟到结果仍不接受。
            failurePhase = Failure.PROTOCOL;
            if (!(response instanceof BoltResponse boltResponse) || !boltResponse.isSuccess()) {
                throw new IllegalStateException("Invalid or unsuccessful Bolt response");
            }
            R result = responseType == Void.class ? null : JsonUtils.fromJson(boltResponse.getData(), responseType);
            remainingMillis(startedAtNanos, timeoutMillis);
            return result;
        } catch (BoltInvocationException ex) {
            if (hasCause(ex, InterruptedException.class)) {
                Thread.currentThread().interrupt();
            }
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BoltInvocationException(Failure.LOCAL, "Bolt invocation interrupted", ex);
        } catch (Exception ex) {
            throw new BoltInvocationException(classifyFailure(failurePhase, ex),
                    "Authenticated Bolt invocation failed: " + address, ex);
        }
    }

    /** 只验证已获取连接的 TLS 身份；验证后仍由调用方在原连接上发起 RPC。 */
    private void verifyPeerIdentity(Connection connection, long startedAtNanos, int timeoutMillis,
                                    String expectedPeerIdentity, String identityPrefix) throws Exception {
        SslHandler ssl = connection.getChannel().pipeline().get(SslHandler.class);
        if (ssl == null) {
            throw new SSLException("TLS is required");
        }
        if (!ssl.handshakeFuture().await(remainingMillis(startedAtNanos, timeoutMillis))) {
            throw new TimeoutException("TLS handshake acceptance budget exhausted");
        }
        if (!ssl.handshakeFuture().isSuccess()) {
            throw new SSLException("TLS handshake failed", ssl.handshakeFuture().cause());
        }
        Set<String> identities = BoltRequestProcessor.verifiedUriIdentities(ssl);
        List<String> peerIdentities = identities.stream()
                .filter(identity -> identity.startsWith(identityPrefix)).toList();
        if (peerIdentities.size() != 1 || !expectedPeerIdentity.equals(peerIdentities.getFirst())) {
            throw new SSLException("TLS peer URI identity is missing, ambiguous or unexpected");
        }
    }

    /** 执行阶段只提供归因背景；本机预算耗尽、认证失败与明确传输异常按原优先级分类。 */
    private static Failure classifyFailure(Failure failurePhase, Exception exception) {
        Failure failure = failurePhase == Failure.AUTHENTICATION ? Failure.AUTHENTICATION
                : failurePhase == Failure.PROTOCOL ? Failure.PROTOCOL : Failure.LOCAL;
        if (exception instanceof TimeoutException) {
            failure = Failure.LOCAL;
        } else if (hasCause(exception, SSLException.class) || hasCause(exception, CertificateException.class)) {
            failure = Failure.AUTHENTICATION;
        } else if (failurePhase == Failure.TRANSPORT_UNAVAILABLE && (hasCause(exception, ConnectException.class)
                || hasCause(exception, SocketTimeoutException.class) || hasCause(exception, InvokeTimeoutException.class)
                || hasCause(exception, io.netty.channel.ConnectTimeoutException.class))) {
            failure = Failure.TRANSPORT_UNAVAILABLE;
        }
        return failure;
    }

    private int remainingMillis(long started, int timeoutMillis) throws TimeoutException {
        long remaining = TimeUnit.MILLISECONDS.toNanos(timeoutMillis) - (monotonicNanos.getAsLong() - started);
        if (remaining < TimeUnit.MILLISECONDS.toNanos(1)) {
            throw new TimeoutException("Bolt invocation budget exhausted");
        }
        return (int) TimeUnit.NANOSECONDS.toMillis(remaining);
    }

    private boolean budgetExpired(long started, int timeoutMillis) {
        return monotonicNanos.getAsLong() - started >= TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

}
