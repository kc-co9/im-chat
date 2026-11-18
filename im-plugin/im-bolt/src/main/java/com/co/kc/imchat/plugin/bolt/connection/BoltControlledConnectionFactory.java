package com.co.kc.imchat.plugin.bolt.connection;

import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.co.kc.imchat.plugin.bolt.security.BoltTlsSessionFacts;

import com.alipay.remoting.Connection;
import com.alipay.remoting.Url;
import com.alipay.remoting.rpc.RpcConnectionFactory;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.ConnectTimeoutException;
import io.netty.util.AttributeKey;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 在原生连接创建流程中绑定目标并认证候选；不替换原生 TLS initializer/provider。
 * 工厂只向 manager 交付准入成功的连接，未交付的候选仍由工厂负责关闭。
 */
public final class BoltControlledConnectionFactory extends RpcConnectionFactory {
    /**
     * Channel 上的受控目标、身份前缀与共享创建截止时间。
     */
    public static final AttributeKey<BoltConnectionBinding> BINDING = AttributeKey.valueOf("im.bolt.binding");
    /**
     * 开发明文候选通过受控创建检查后的标记，不代表 TLS 认证。
     */
    public static final AttributeKey<Boolean> DEVELOPMENT = AttributeKey.valueOf("im.bolt.development.channel");
    /**
     * 认证完成后仅发布一次的 TLS 会话事实。
     */
    public static final AttributeKey<BoltTlsSessionFacts> FACTS = AttributeKey.valueOf("im.bolt.session.facts");
    /**
     * 拥有本工厂及目标绑定管理器的受控客户端。
     */
    private final BoltControlledClient client;
    /**
     * 当前线程的连接创建作用域，嵌套创建结束后整体恢复外层状态。
     */
    private final ThreadLocal<ConnectionCreationScope> scope = new ThreadLocal<>();

    /**
     * 使用所属客户端装配原生连接工厂，保留原生 TLS 初始化流程。
     */
    public BoltControlledConnectionFactory(BoltControlledClient client) {
        super(new ConcurrentHashMap<>(), client);
        this.client = client;
    }

    /**
     * 使用 manager 绑定的目标和共享创建预算建立连接，成功后将候选交由 manager 管理。
     * 该预算包含连接与认证，不是单个 caller 的等待预算；caller 停止等待不应终止共享创建。
     * 创建失败保留原始 cause，中断同时恢复线程标志；未交付的候选在 finally 中关闭。
     */
    @Override
    public Connection createConnection(Url url) throws Exception {
        /* 1. 重验目标绑定，保存外层线程上下文，为本次候选单独记录清理责任。 */
        BoltConnectionBinding binding = client.manager().binding(url);
        ConnectionCreationScope previous = scope.get();
        scope.set(new ConnectionCreationScope(binding));
        Connection connection = null;
        boolean delivered = false;
        try {
            /* 2. 委托原生流程建连，扣除已用时间后检查共享创建预算。 */
            connection = super.createConnection(url);
            long remaining = binding.expireAtNanos() - System.nanoTime();
            if (remaining < TimeUnit.MILLISECONDS.toNanos(1)) {
                throw new TimeoutException("Bolt creation budget exhausted");
            }
            /* 3. 开发明文模式重验截止时间与停机状态，仅标记受控候选，不发布 TLS 事实。 */
            if (client.developmentMode()) {
                if (client.manager().stopped() || System.nanoTime() >= binding.expireAtNanos()) {
                    throw new TimeoutException("Bolt development candidate expired or stopped");
                }
                connection.getChannel().attr(DEVELOPMENT).set(true);
                delivered = true;
                return connection;
            }
            /* 4. 使用剩余预算认证预期身份，重验后一次性发布会话事实并交付连接。 */
            BoltTlsSessionFacts facts = BoltTlsSessionFacts.authenticate(connection.getChannel(), binding.target().expectedPeerIdentity(),
                    binding.prefix(), (int) Math.min(Integer.MAX_VALUE, TimeUnit.NANOSECONDS.toMillis(remaining)));
            // 认证成功也可能已经迟到；发布会话事实前重验，避免把过期或停机期间完成的候选交付给池。
            if (client.manager().stopped() || System.nanoTime() >= binding.expireAtNanos()) {
                throw new TimeoutException("Bolt candidate completed after creation deadline or stop");
            }
            if (!connection.getChannel().attr(FACTS).compareAndSet(null, facts)) {
                throw new SSLException("Bolt session facts already published");
            }
            delivered = true;
            return connection;
        } catch (Exception failure) {
            /* 5. 按实际原因分类创建失败，保留 cause；中断须恢复线程标志。 */
            BoltInvocationException.Failure category = classifyCreationFailure(failure);
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BoltInvocationException(category, "Bolt candidate authentication or creation failed", failure);
        } finally {
            /* 6. 关闭未交付候选，并恢复外层绑定及候选，兼容同线程嵌套创建。 */
            if (!delivered) {
                // 原生 createConnection 抛错时可能尚未返回 Connection，仍需回收已发起连接的 Channel。
                Channel owned = connection == null ? scope.get().candidate : connection.getChannel();
                if (owned != null) {
                    owned.close();
                }
            }
            // 同线程重入可能暂时覆盖外层目标与候选；恢复旧值，既避免串用身份，也不丢失外层清理对象。
            if (previous == null) {
                scope.remove();
            } else {
                scope.set(previous);
            }
        }
    }

    /**
     * 按候选创建失败原因分类；中断恢复与异常包装由调用方负责。
     */
    private BoltInvocationException.Failure classifyCreationFailure(Exception failure) {
        if (failure instanceof TimeoutException || failure instanceof InterruptedException) {
            // 共享创建预算耗尽或本机中断不证明对端不可达，不能据此形成故障票。
            return BoltInvocationException.Failure.LOCAL;
        } else if (failure instanceof SSLException || failure instanceof SecurityException) {
            // 身份或 TLS 拒绝必须保留认证语义，不能降为传输不可达。
            return BoltInvocationException.Failure.AUTHENTICATION;
        } else if (failure instanceof ConnectException || failure instanceof SocketTimeoutException
                || failure instanceof ConnectTimeoutException) {
            // SocketTimeoutException 不同于上面的并发 TimeoutException；Netty 连接超时也属于传输失败。
            return BoltInvocationException.Failure.TRANSPORT_UNAVAILABLE;
        } else {
            // 这里只分类候选创建失败；未知原因仍为 LOCAL，不套用 Invoker 的协议失败默认值。
            return BoltInvocationException.Failure.LOCAL;
        }
    }

    /**
     * 在受控绑定内连接 host/port，原生连接超时取 timeout 毫秒；共享纳秒截止时间约束等待，异常由外层分类并回收候选。
     */
    protected Channel doCreateConnection(String host, int port, int timeout) throws Exception {
        /* 1. 要求外层已建立受控作用域，禁止无目标绑定的底层建连。 */
        ConnectionCreationScope current = scope.get();
        if (current == null) {
            throw new SecurityException("Bolt creation requires controlled target scope");
        }
        BoltConnectionBinding binding = current.binding;
        /* 2. 克隆原生 bootstrap 并绑定目标；发起连接后立即登记候选所有权。 */
        ChannelFuture connect = bootstrap.clone().attr(BINDING, binding).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout)
                .connect(new InetSocketAddress(host, port));
        // 在等待前登记所有权；即使连接尚未完成就耗尽预算，外层 finally 也能关闭这个候选。
        scope.get().candidate = connect.channel();
        /* 3. 只在共享剩余预算内等待，超时交给外层分类并关闭候选。 */
        long remaining = binding.expireAtNanos() - System.nanoTime();
        if (remaining <= 0 || !connect.await(remaining, TimeUnit.NANOSECONDS)) {
            throw new TimeoutException("Bolt connect creation budget");
        }
        /* 4. 连接失败保留底层原因，成功才向原生创建流程返回 Channel。 */
        if (!connect.isSuccess()) {
            Throwable cause = connect.cause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new IllegalStateException("Bolt connect failed", cause);
        }
        return connect.channel();
    }

    /**
     * 拒绝无目标身份绑定的裸地址创建，始终抛出 SecurityException。
     */
    public Connection createConnection(String host, int port, int timeout) {
        throw new SecurityException("Bare Bolt factory address disabled");
    }

    /**
     * 拒绝通过指定协议版本绕过目标绑定，始终抛出 SecurityException。
     */
    public Connection createConnection(String host, int port, byte version, int timeout) {
        throw new SecurityException("Bare Bolt factory address disabled");
    }

    /**
     * 同一次连接创建的目标绑定与候选清理责任，随线程作用域整体保存和恢复。
     */
    private static final class ConnectionCreationScope {
        private final BoltConnectionBinding binding;
        private Channel candidate;

        private ConnectionCreationScope(BoltConnectionBinding binding) {
            this.binding = binding;
        }
    }
}
