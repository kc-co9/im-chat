package com.co.kc.imchat.plugin.bolt.client;

import com.co.kc.imchat.plugin.bolt.model.enums.BoltTransportMode;
import com.co.kc.imchat.plugin.bolt.invocation.BoltClientCallTracker;
import com.co.kc.imchat.plugin.bolt.connection.BoltControlledConnectionFactory;
import com.co.kc.imchat.plugin.bolt.connection.BoltControlledConnectionManager;
import com.co.kc.imchat.plugin.bolt.channel.BoltOutboundHandler;
import com.co.kc.imchat.plugin.bolt.security.BoltDevelopmentValidator;

import com.alipay.remoting.CommandFactory;
import com.alipay.remoting.Connection;
import com.alipay.remoting.ConnectionManager;
import com.alipay.remoting.ExtendedNettyChannelHandler;
import com.alipay.remoting.InvokeCallback;
import com.alipay.remoting.InvokeContext;
import com.alipay.remoting.InvokeFuture;
import com.alipay.remoting.RemotingAddressParser;
import com.alipay.remoting.RemotingCommand;
import com.alipay.remoting.config.BoltClientOption;
import com.alipay.remoting.config.BoltOption;
import com.alipay.remoting.config.Configuration;
import com.alipay.remoting.exception.RemotingException;
import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.exception.InvokeTimeoutException;
import com.alipay.remoting.rpc.RpcClientRemoting;
import com.alipay.remoting.rpc.RpcCommandFactory;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;


import java.util.List;

/**
 * 封闭的原生客户端装配拥有工厂、manager与typed屏障，不接纳外部codec/transport/pipeline。
 * 原生静态workerGroup为进程共享资源，不由此实例关闭。
 */
public final class BoltControlledClient extends RpcClient {
    /**
     * 构造时校验并固定的客户端策略快照。
     */
    private final ImBoltProperties.Client client;
    /**
     * 本实例创建并拥有的受控连接管理器。
     */
    private BoltControlledConnectionManager manager;
    /**
     * 调用追踪状态的唯一拥有者；实例 monitor 与初始化和关闭交接共享。
     */
    private final BoltClientCallTracker calls;
    /**
     * 构造期间保持 null 以放行父类 option 回调；此后由 lifecycleMonitor 串行迁移，提交与准入复合边界共享 calls monitor，volatile 发布运行装配。
     * 唯一关闭准入状态仍归 calls，不由本字段复制。
     */
    private volatile LifecycleState state;

    /**
     * 完整生命周期串行执行；资源操作不持调用追踪的 calls monitor。
     */
    private final Object lifecycleMonitor = new Object();

    /**
     * 校验并快照 configured 策略，固定 TLS、禁重连及发送屏障；策略非法时拒绝构造。
     */
    public BoltControlledClient(ImBoltProperties.Client configured) {
        configured.validateTypedPolicy();
        client = configured.snapshot();
        calls = new BoltClientCallTracker(client.getMaxInflightRpcs());
        super.option(BoltClientOption.CONN_RECONNECT_SWITCH, false);
        super.option(BoltClientOption.CONN_MONITOR_SWITCH, false);
        super.option(BoltClientOption.CLI_SSL_ENABLE, client.getMode() == BoltTransportMode.MTLS);
        super.option(BoltClientOption.CLI_SSL_KEYSTORE, client.getTls().getKeyStore());
        super.option(BoltClientOption.CLI_SSL_KEYSTORE_PASS, client.getTls().getKeyStorePassword());
        super.option(BoltClientOption.CLI_SSL_KEYSTORE_TYPE, client.getTls().getKeyStoreType());
        super.option(BoltClientOption.EXTENDED_NETTY_CHANNEL_HANDLER, new ControlledChannelHandler(this));
        state = LifecycleState.READY;
    }

    /**
     * 构造期间委托设置原生选项；装配冻结后抛出 IllegalArgumentException，防止绕过受控配置。
     */
    @Override
    public <T> Configuration option(BoltOption<T> key, T value) {
        if (state != null) {
            throw new IllegalArgumentException("Controlled Bolt client options are fixed at construction");
        }
        return super.option(key, value);
    }

    /**
     * 禁止外部替换受控管理器，始终抛出 IllegalArgumentException。
     */
    @Override
    public void setConnectionManager(ConnectionManager replacement) {
        throw new IllegalArgumentException("Controlled Bolt manager cannot be replaced");
    }

    /**
     * 单次初始化受控工厂、管理器及完成追踪；与停机原子交接关闭责任，失败时回滚已分配资源并传播异常。
     */
    @Override
    public void startup() {
        /* 1. 独立 monitor 串行生命周期，竞争不因超时或中断退出。 */
        synchronized (lifecycleMonitor) {
            /* 2. 短 calls 临界区取得初始化责任，拒绝时不终结外层初始化。 */
            beginInitialization();
            boolean startupAttempted = false;
            try {
                /* 3. 仅持生命周期锁创建和启动资源，不阻塞调用追踪回调。 */
                BoltControlledConnectionFactory factory = new BoltControlledConnectionFactory(this);
                manager = new BoltControlledConnectionManager(this, factory, client);
                manager.startup();
                super.setConnectionManager(manager);
                startupAttempted = true;
                super.startup();
                rpcRemoting = new TrackedRpcClientRemoting(new RpcCommandFactory(), getAddressParser(), manager, calls);
                commitInitialization();
            } catch (RuntimeException | Error failure) {
                /* 4. 关闭准入后按实际进度回滚，保留主异常及清理原因。 */
                calls.closeAdmission();
                try {
                    rollbackInitialization(startupAttempted);
                } catch (RuntimeException | Error cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            } finally {
                /* 5. 只终结本次取得的初始化责任，不覆盖成功提交的 RUNNING。 */
                finishFailedInitialization();
            }
        }
    }

    /**
     * 调用方持有 lifecycleMonitor；原子检查准入与 READY 并取得初始化责任。
     */
    private void beginInitialization() {
        synchronized (calls) {
            if (calls.isClosed() || state != LifecycleState.READY) {
                throw new IllegalStateException("Controlled Bolt lifecycle already entered");
            }
            state = LifecycleState.INITIALIZING;
        }
    }

    /**
     * 调用方持有 lifecycleMonitor；原子重验关闭准入并提交运行状态。
     */
    private void commitInitialization() {
        synchronized (calls) {
            if (calls.isClosed()) {
                throw new IllegalStateException("Controlled Bolt closed during initialization");
            }
            state = LifecycleState.RUNNING;
        }
    }

    /**
     * 调用方持有 lifecycleMonitor；仅终结仍未提交的初始化责任。
     */
    private void finishFailedInitialization() {
        if (state == LifecycleState.INITIALIZING) {
            state = LifecycleState.TERMINATED;
        }
    }

    /**
     * 按实际初始化进度回滚已启动资源，清理异常由调用方附加到初始化主异常。
     */
    private void rollbackInitialization(boolean startupAttempted) {
        if (startupAttempted && isStarted()) {
            manager.shutdownOwnedClient(() -> super.shutdown());
        } else if (manager != null && manager.isStarted()) {
            manager.shutdown();
        }
    }

    /**
     * 返回本客户端拥有的唯一追踪器，供连接与出站协作者操作同一状态。
     */
    public BoltClientCallTracker callTracker() {
        return calls;
    }

    /**
     * 返回本实例拥有的受控连接管理器。
     */
    public BoltControlledConnectionManager manager() {
        return manager;
    }

    /**
     * 返回固定策略是否使用开发明文模式。
     */
    public boolean developmentMode() {
        return client.getMode() == BoltTransportMode.DEVELOPMENT_PLAINTEXT;
    }

    /**
     * 要求客户端运行并重验目标策略；开发模式额外验证地址，在 remainingMillis 毫秒内取得受控连接，失败向上传播。
     */
    public Connection acquire(BoltTarget target, int remainingMillis) throws Exception {
        if (state != LifecycleState.RUNNING || calls.isClosed()) {
            throw new BoltInvocationException(BoltInvocationException.Failure.LOCAL, "Controlled Bolt client is not running", new IllegalStateException("Controlled Bolt client is not running"));
        }
        client.selectPeerUriPrefix(target);
        if (client.getMode() == BoltTransportMode.DEVELOPMENT_PLAINTEXT) {
            BoltDevelopmentValidator.validateTarget(target.address(), client.getDevelopment());
        }
        return manager.acquire(target, remainingMillis);
    }

    /**
     * 返回构造时固定的客户端运行模式。
     */
    public BoltTransportMode configuredMode() {
        return client.getMode();
    }

    /**
     * 在 calls 锁内检查运行状态、调用准入及原生资源状态；RUNNING 已保证自有管理器完成安装。
     */
    public boolean isRunningControlledAssembly() {
        synchronized (calls) {
            return state == LifecycleState.RUNNING
                    && !calls.isClosed()
                    && isStarted()
                    && manager.isStarted()
                    && !manager.stopped();
        }
    }

    /**
     * 取得生命周期 monitor 后关闭自有资源；初始化同线程重入明确拒绝，不中途取消其他初始化。
     */
    @Override
    public void shutdown() {
        /* 1. 独立 monitor 串行关闭，不持 calls 等待其他生命周期调用。 */
        synchronized (lifecycleMonitor) {
            /* 2. 可重入锁内 INITIALIZING 只能来自同线程；拒绝不污染外层启动责任。 */
            ShutdownAction action = beginShutdown();
            if (action == ShutdownAction.NONE) {
                return;
            }
            /* 3. 持生命周期锁关闭资源，但不持 calls 锁等待资源或回调。 */
            try {
                if (action == ShutdownAction.CLOSE_CLIENT) {
                    manager.shutdownOwnedClient(() -> super.shutdown());
                } else if (manager != null && manager.isStarted()) {
                    manager.shutdown();
                }
            } finally {
                /* 4. 清理尝试结束后终结生命周期，不表示清理一定成功。 */
                state = LifecycleState.TERMINATED;
            }
        }
    }

    /**
     * 调用方持有 lifecycleMonitor，在 calls 内关闭准入并选择资源关闭责任。
     */
    private ShutdownAction beginShutdown() {
        synchronized (calls) {
            if (state == LifecycleState.INITIALIZING) {
                throw new IllegalStateException("Controlled Bolt shutdown reentered during initialization");
            }
            if (calls.isClosed()) {
                return ShutdownAction.NONE;
            }
            calls.closeAdmission();
            if (state == LifecycleState.RUNNING) {
                return ShutdownAction.CLOSE_CLIENT;
            }
            if (state == LifecycleState.READY) {
                state = LifecycleState.TERMINATED;
            }
            return ShutdownAction.CLOSE_MANAGER;
        }
    }

    /**
     * 提供前后置安装器，固定受控客户端的出站屏障位置。
     *
     * @param client 安装器所属的受控客户端，供屏障共享模式及调用追踪状态。
     */
    private record ControlledChannelHandler(BoltControlledClient client) implements ExtendedNettyChannelHandler {
        /**
         * 提供前置安装器，在 pipeline 头部加入字节出站屏障。
         */
        @Override
        public List<ChannelHandler> frontChannelHandlers() {
            return List.of(new ChannelInboundHandlerAdapter() {
                /** 在当前 Channel 头部安装禁止裸请求命令的出站屏障。 */
                @Override
                public void handlerAdded(ChannelHandlerContext context) {
                    context.pipeline().addFirst("im-bolt-head", new BoltOutboundHandler(client, false));
                }
            });
        }

        /**
         * 提供后置安装器，装配 TLS 前字节屏障和编码前命令屏障。
         */
        @Override
        public List<ChannelHandler> backChannelHandlers() {
            return List.of(new ChannelInboundHandlerAdapter() {
                /** 按模式安装 TLS 前屏障，并始终安装编码前的受控命令校验。 */
                @Override
                public void handlerAdded(ChannelHandlerContext context) {
                    if (!client.developmentMode()) {
                        context.pipeline().addAfter("sslHandler", "im-bolt-before-tls", new BoltOutboundHandler(client, false));
                    }
                    context.pipeline().addAfter("encoder", "im-bolt-command", new BoltOutboundHandler(client, true));
                }
            });
        }
    }

    /**
     * 在原生 future 与同步终结点接入同一个追踪器，不持有外层客户端。
     */
    private static final class TrackedRpcClientRemoting extends RpcClientRemoting {
        /* 由所属客户端创建的唯一调用追踪器。 */
        private final BoltClientCallTracker callTracker;

        /**
         * 原样传递原生装配参数，并绑定已有追踪器。
         */
        private TrackedRpcClientRemoting(CommandFactory commandFactory, RemotingAddressParser addressParser, ConnectionManager connectionManager, BoltClientCallTracker callTracker) {
            super(commandFactory, addressParser, connectionManager);
            this.callTracker = callTracker;
        }

        /**
         * 包装原生同步 future 并存入调用上下文，供同步终结时释放准入计数。
         */
        @Override
        protected InvokeFuture createInvokeFuture(RemotingCommand command, InvokeContext context) {
            return callTracker.track(super.createInvokeFuture(command, context), context);
        }

        /**
         * 为带回调的原生 future 添加响应到达时的准入释放逻辑。
         */
        @Override
        protected InvokeFuture createInvokeFuture(Connection connection, RemotingCommand command, InvokeContext context, InvokeCallback callback) {
            return callTracker.track(super.createInvokeFuture(connection, command, context, callback));
        }

        /**
         * 委托原生同步调用并返回响应；正常返回、原生调用超时或 future 已完成时释放准入，其他异常保留原生生命周期。
         */
        @Override
        public Object invokeSync(Connection connection, Object request, InvokeContext context, int timeout) throws RemotingException, InterruptedException {
            boolean terminal = false;
            try {
                Object response = super.invokeSync(connection, request, context, timeout);
                terminal = true;
                return response;
            } catch (InvokeTimeoutException failure) {
                terminal = true;
                throw failure;
            } finally {
                callTracker.finishSync(context, terminal);
            }
        }
    }

    /**
     * 本次 shutdown 选定的资源关闭责任。
     */
    private enum ShutdownAction {
        /** 准入已关闭，本次无需清理。 */
        NONE,
        /** 关闭已运行的原生客户端及其资源。 */
        CLOSE_CLIENT,
        /** 仅尝试关闭已启动的自有管理器。 */
        CLOSE_MANAGER
    }

    /**
     * 生命周期阶段及资源清理责任；TERMINATED 仅表示清理尝试结束，不保证清理成功。
     */
    private enum LifecycleState {
        /**
         * 构造装配已冻结，尚未初始化。
         */
        READY,
        /**
         * 初始化线程拥有启动及失败回滚责任。
         */
        INITIALIZING,
        /**
         * 初始化已提交，关闭责任已交给 shutdown。
         */
        RUNNING,
        /**
         * 本次生命周期已终结，不可重新初始化。
         */
        TERMINATED
    }
}
