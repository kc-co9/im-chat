package com.co.kc.imchat.plugin.bolt.invocation;

import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.alipay.remoting.Connection;
import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.exception.InvokeTimeoutException;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException.Failure;
import com.co.kc.imchat.plugin.bolt.model.BoltRequest;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import com.co.kc.imchat.plugin.bolt.model.BoltResponse;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import io.netty.channel.ConnectTimeoutException;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.security.cert.CertificateException;

import com.alipay.remoting.InvokeContext;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;

/**
 * 统一执行受控目标调用，贯穿调用者预算并归类失败原因。
 */
public final class BoltClientInvoker implements BoltInvoker {
    /**
     * 由装配方管理生命周期的原生客户端，typed 调用要求受控实现。
     */
    private final RpcClient rpcClient;
    /**
     * 提供纳秒单调时间，用于计算调用者剩余预算。
     */
    private final LongSupplier monotonicNanos;
    /**
     * 构造时固定的目标准入策略快照。
     */
    private final ImBoltProperties.Client client;

    /**
     * 防御性快照固定调用策略；调用方仍拥有传入RpcClient的初始化/关闭生命周期。
     * typed入口要求实际受控客户端，不提供无身份地址入口。
     */
    public BoltClientInvoker(RpcClient rpcClient, LongSupplier monotonicNanos, ImBoltProperties.Client client) {
        this.rpcClient = rpcClient;
        this.monotonicNanos = monotonicNanos;
        this.client = client.snapshot();
    }

    /**
     * typed入口复用受控连接工厂和逐命令屏障，调用者不重新从地址选择连接。
     * target 携带地址与身份，service/operation 标识远端操作，request 序列化为 JSON；
     * responseType 决定结果类型（Void 返回 null），timeoutMillis 为含序列化、等待及解码的 caller 总预算。
     * 失败统一抛出带分类和原始 cause 的 BoltInvocationException；发现中断时恢复线程标志。
     */
    @Override
    public <T, R> R invoke(BoltTarget target, String service, String operation, T request,
                           Class<R> responseType, int timeoutMillis) {
        /* 1. 校验目标身份策略，拒绝不符合允许前缀的调用目标。 */
        try {
            client.selectPeerUriPrefix(target);
        } catch (IllegalArgumentException failure) {
            throw new BoltInvocationException(Failure.LOCAL, "Invalid Bolt invocation target policy", failure);
        }
        /* 2. 记录单调时间起点并建立 caller 总预算，要求使用受控客户端。 */
        long started = monotonicNanos.getAsLong();
        BoltInvocationContext invocation;
        try {
            invocation = new BoltInvocationContext(target, started, timeoutMillis);
        } catch (IllegalArgumentException failure) {
            throw new BoltInvocationException(Failure.LOCAL, "Invalid Bolt caller budget", failure);
        }
        if (!(rpcClient instanceof BoltControlledClient controlled)) {
            throw new BoltInvocationException(Failure.LOCAL, "Typed Bolt invocation requires controlled client", null);
        }
        Failure phase = Failure.LOCAL;
        try {
            /* 3. 序列化计入 caller 预算；成功后切换到连接获取与发送阶段。 */
            String payload = JsonUtils.toJson(request);
            phase = Failure.TRANSPORT_UNAVAILABLE;
            /* 4. 按剩余预算获取受控连接，并将同一调用上下文交给逐命令发送屏障。 */
            Connection connection = controlled.acquire(target, invocation.remainingMillis(monotonicNanos.getAsLong()));
            int remaining = invocation.remainingMillis(monotonicNanos.getAsLong());
            InvokeContext context = new InvokeContext();
            context.put(BoltInvocationContext.KEY, invocation);
            Object response = controlled.invokeSync(connection, new BoltRequest(service, operation, payload), context, remaining);
            /* 5. 校验响应并解码；返回前再检查预算，解码耗时同样计入总预算。 */
            phase = Failure.PROTOCOL;
            if (!(response instanceof BoltResponse result) || !result.isSuccess()) {
                throw new IllegalArgumentException("Invalid or unsuccessful Bolt response");
            }
            R decoded = responseType == Void.class ? null : JsonUtils.fromJson(result.getData(), responseType);
            invocation.remainingMillis(monotonicNanos.getAsLong());
            return decoded;
        } catch (Exception failure) {
            /* 6. 恢复中断标志；预算耗尽或本地阶段失败优先归 LOCAL，否则沿原因链分类。 */
            if (hasCause(failure, InterruptedException.class)) {
                Thread.currentThread().interrupt();
            }
            Failure category = invocation.expired(monotonicNanos.getAsLong()) || phase == Failure.LOCAL
                    ? Failure.LOCAL : typedFailure(failure);
            throw new BoltInvocationException(category, "Controlled Bolt invocation failed", failure);
        }
    }

    /**
     * 判断当前 Invoker 是否持有指定的受控客户端实例。
     */
    public boolean usesControlledClient(BoltControlledClient client) {
        return rpcClient == client;
    }

    /**
     * 沿异常因果链优先保留显式分类及本地、认证失败；否则归为传输或协议失败。
     */
    private static Failure typedFailure(Throwable error) {
        /* 1. 默认协议失败，逐层检查 cause；显式分类、本地失败或认证失败命中即返回。 */
        Failure transport = Failure.PROTOCOL;
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof BoltInvocationException invocation) {
                return invocation.failure();
            }
            if (cause instanceof TimeoutException || cause instanceof InterruptedException || cause instanceof RejectedExecutionException) {
                return Failure.LOCAL;
            }
            if (cause instanceof SSLException || cause instanceof CertificateException || cause instanceof SecurityException) {
                return Failure.AUTHENTICATION;
            }
            /* 2. 传输原因先记为候选分类，继续检查更深层的显式、本地或认证原因。 */
            if (cause instanceof ConnectException || cause instanceof SocketTimeoutException || cause instanceof InvokeTimeoutException
                    || cause instanceof ConnectTimeoutException) {
                transport = Failure.TRANSPORT_UNAVAILABLE;
            }
        }
        /* 3. 无优先分类时返回已识别的传输失败，否则保留协议失败。 */
        return transport;
    }

    /**
     * 检查异常自身及因果链中是否包含指定异常类型。
     */
    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }
}
