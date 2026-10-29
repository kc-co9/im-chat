package com.co.kc.imchat.plugin.bolt.connection;

import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.co.kc.imchat.plugin.bolt.security.BoltTlsSessionFacts;

import com.alipay.remoting.Connection;
import com.alipay.remoting.ConnectionEventListener;
import com.alipay.remoting.ConnectionPool;
import com.alipay.remoting.DefaultClientConnectionManager;
import com.alipay.remoting.RandomSelectStrategy;
import com.alipay.remoting.Url;
import com.alipay.remoting.exception.RemotingException;
import com.alipay.remoting.rpc.RpcAddressParser;
import com.alipay.remoting.rpc.RpcConnectionEventHandler;
import com.alipay.remoting.util.RunStateRecordedFutureTask;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 调度原生 connTasks/ConnectionPool 的连接创建、认证复用和退休，不维护平行连接池。
 * <p>
 * 同一目标的等待者共享一次有界创建任务；单个 caller 超时不能取消其他等待者仍依赖的任务。
 */
public final class BoltControlledConnectionManager extends DefaultClientConnectionManager {
    /**
     * 受控 Url 属性中存储共享创建截止时间的键。
     */
    private static final String DEADLINE = "im.bolt.creation.deadline";
    /**
     * 连接创建、资源上限与身份校验使用的固定策略。
     */
    private final ImBoltProperties.Client policy;
    /**
     * 负责会话模式判定及连接排空的所属客户端。
     */
    private final BoltControlledClient client;
    /**
     * 线程数与队列容量均受策略约束的共享创建执行器。
     */
    private final ThreadPoolExecutor creation;
    /**
     * 已登记且关联创建任务尚未结束的 caller 数，caller 超时不提前释放。
     */
    private final AtomicInteger pending = new AtomicInteger();
    /**
     * 停机标记，阻止新增创建及迟到候选发布。
     */
    private volatile boolean stopped;
    /**
     * 仅在所属 Client 同线程同步关闭期间延后 Manager 失败，让原生拥有者继续清理。
     */
    private final ThreadLocal<ShutdownScope> shutdownScope = new ThreadLocal<>();

    /** 保存一次拥有者关闭的首个失败，不作为跨线程状态或关闭重试记录。 */
    private static final class ShutdownScope {
        private Throwable failure;
    }

    /**
     * 装配原生连接池管理及有界创建线程池；不启用额外的平行连接池。
     */
    public BoltControlledConnectionManager(BoltControlledClient client, BoltControlledConnectionFactory factory, ImBoltProperties.Client policy) {
        super(new RandomSelectStrategy(client), factory, new RpcConnectionEventHandler(client), new ConnectionEventListener());
        this.policy = policy;
        this.client = client;
        setAddressParser(new RpcAddressParser());
        creation = new ThreadPoolExecutor(
                policy.getCreationThreads(),
                policy.getCreationThreads(),
                0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(policy.getCreationQueueCapacity()),
                runnable -> new Thread(runnable, "im-bolt-creation"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 返回管理器是否已进入停机状态。
     */
    public boolean stopped() {
        return stopped;
    }

    /**
     * 为键分量添加字符长度前缀，避免内容中的分隔符造成歧义。
     */
    private String encodePoolComponent(String value) {
        return value.length() + ":" + value;
    }

    /**
     * 由地址、身份、模式及允许前缀生成池键，防止跨身份或策略复用。
     */
    private String encodePoolKey(BoltTarget target) {
        return encodePoolComponent(target.address())
                + encodePoolComponent(target.expectedPeerIdentity())
                + encodePoolComponent(policy.getMode().name())
                + encodePoolComponent(String.join("\n", policy.getAllowedPeerUriPrefixes()));
    }

    /**
     * 为目标创建单连接、禁预热的原生 Url，并附带池键及共享纳秒截止时间。
     */
    private Url createControlledUrl(BoltTarget target, long deadline) {
        /* 1. 解析地址并使用安全池键创建独立 Url，避免直接复用外部池配置。 */
        Url parsed = new RpcAddressParser().parse(target.address());
        Url owned = new Url(target.address(), parsed.getIp(), parsed.getPort(), encodePoolKey(target), new Properties());
        /* 2. 固定协议、单连接与禁预热策略，将共享截止时间写入属性并换算连接超时。 */
        owned.setProtocol((byte) 2);
        owned.setConnNum(1);
        owned.setConnWarmup(false);
        owned.setConnectTimeout((int) Math.max(1, Math.min(Integer.MAX_VALUE, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()))));
        owned.getProperties().setProperty(DEADLINE, Long.toString(deadline));
        return owned;
    }

    /**
     * 解析并重验受控 Url 的键、来源及池配置；不匹配或无法解析时抛出 SecurityException。
     */
    BoltConnectionBinding binding(Url url) {
        /* 1. 按长度前缀拆解池键，要求地址、身份、模式和前缀策略四个分量齐全。 */
        String key = url.getUniqueKey();
        try {
            List<String> components = decodePoolComponents(key);
            /* 2. 重建目标并核对当前策略、原始地址及池配置，拒绝伪造或串用的 Url。 */
            BoltTarget target = new BoltTarget(components.get(0), components.get(1));
            if (!encodePoolKey(target).equals(key) || !target.address().equals(url.getOriginUrl()) || url.getConnNum() != 1 || url.isConnWarmup()) {
                throw new IllegalArgumentException("binding mismatch");
            }
            /* 3. 重新选择允许的身份前缀并读取截止时间；解析或校验失败统一拒绝。 */
            return new BoltConnectionBinding(target, policy.selectPeerUriPrefix(target), Long.parseLong(url.getProperties().getProperty(DEADLINE)));
        } catch (RuntimeException failure) {
            throw new SecurityException("Uncontrolled Bolt target Url", failure);
        }
    }

    /**
     * 解码由 encodePoolComponent 拼接的池键，每段格式为「字符长度:内容」，长度按 Java String.length() 计算。
     * 内容允许包含冒号，因此按长度截取，不能直接按冒号拆分。
     * <p>
     * 本方法仅还原四个分量；目标合法性、当前策略与规范编码的一致性仍由 binding 校验。
     *
     * @param key 按地址、预期对端身份、运行模式、允许前缀策略顺序编码的池键
     * @return 保持编码顺序的四个分量；前缀策略分量内部仍保留原换行符
     * @throws RuntimeException 长度无法解析、截取越界或分量数量不符；由 binding 包装为 SecurityException
     */
    private List<String> decodePoolComponents(String key) {
        List<String> components = new ArrayList<>(4);
        int idx = 0;
        for (; idx < key.length(); ) {
            /* 1. 读取冒号前的长度，拒绝空长度、非数字、整数溢出及缺失分隔符。 */
            if (key.charAt(idx) == ':') {
                throw new IllegalArgumentException("Missing pool key length or separator");
            }
            int size = 0;
            for (; idx < key.length(); idx++) {
                char c = key.charAt(idx);
                if (c == ':') {
                    break;
                }
                if (c < '0' || c > '9') {
                    throw new IllegalArgumentException("Invalid pool key length");
                }
                int digit = c - '0';
                if (size > (Integer.MAX_VALUE - digit) / 10) {
                    throw new IllegalArgumentException("Pool key length overflow");
                }
                size = size * 10 + digit;
            }
            if (idx == key.length()) {
                throw new IllegalArgumentException("Missing pool key length or separator");
            }

            /* 2. 跳过冒号并按声明长度读取内容；不足时拒绝，读满后游标移至下一分量起点。 */
            StringBuilder component = new StringBuilder();
            for (int i = 0; ++idx < key.length() && i < size; i++) {
                component.append(key.charAt(idx));
            }
            if (component.length() != size) {
                throw new IllegalArgumentException("Pool key component exceeds remaining content");
            }

            components.add(component.toString());
        }

        /* 3. 要求四个分量齐全；具体值是否可信，交由 binding 继续核验。 */
        if (components.size() != 4) {
            throw new IllegalArgumentException("binding components");
        }
        return components;
    }

    /**
     * 在 timeoutMillis 毫秒内等待同目标共享任务并重验所选连接；超时不取消创建，失效连接仅退休观察到的任务代次并排空。
     */
    public Connection acquire(BoltTarget target, int timeoutMillis) throws Exception {
        CompletableFuture<Void> completion;
        RunStateRecordedFutureTask<ConnectionPool> task;
        /* 1. 在管理器锁内检查停机与 caller 上限，查找可共享的目标任务。 */
        synchronized (this) {
            if (stopped) {
                throw new RejectedExecutionException("Bolt creation stopped");
            }
            if (pending.get() >= policy.getMaxPendingCallers()) {
                throw new RejectedExecutionException("Bolt pending caller limit");
            }
            task = connTasks.get(encodePoolKey(target));
            /* 2. 无现有任务时检查池上限，以独立共享预算登记一次有界创建。 */
            if (task == null) {
                if (connTasks.size() >= policy.getMaxTargetPools()) {
                    throw new RejectedExecutionException("Bolt target pool limit");
                }
                long deadline = System.nanoTime() + policy.getConnectionCreationTimeout().toNanos();
                Url owned = createControlledUrl(target, deadline);
                CompletableFuture<Void> ended = new CompletableFuture<>();
                AtomicReference<RunStateRecordedFutureTask<ConnectionPool>> own = new AtomicReference<>();
                task = new CreationTask(() -> createAndPublishPool(target, owned, deadline, own, ended), ended);
                own.set(task);
                connTasks.put(encodePoolKey(target), task);
                // Completion belongs to the actual callable, not a caller timeout/cancel.
                try {
                    creation.execute(task);
                } catch (RuntimeException failure) {
                    connTasks.remove(encodePoolKey(target), task);
                    ended.complete(null);
                    throw failure;
                }
            }
            /* 3. 登记 caller，并关联实际创建结束信号；caller 超时不提前释放此计数。 */
            pending.incrementAndGet();
            completion = completionOf(task);
        }
        completion.whenComplete((ignored, failure) -> pending.decrementAndGet());
        try {
            /* 4. 按 caller 预算等待并选择连接；空池只退休本次观察到的任务代次。 */
            Connection connection = task.get(timeoutMillis, TimeUnit.MILLISECONDS).get();
            if (connection == null) {
                // Native selection can observe a retired pool; never remove a newer task.
                synchronized (this) {
                    connTasks.remove(encodePoolKey(target), task);
                }
                throw new BoltInvocationException(BoltInvocationException.Failure.LOCAL,
                        "Bolt native pool selected no connection",
                        new IllegalStateException("Bolt native pool selected no connection"));
            }
            /* 5. 重验目标、会话有效性和 Channel 活性，失效连接退休后交由调用追踪器排空。 */
            validatePooledConnection(target, task, connection);
            return connection;
        } catch (InterruptedException failure) {
            /* 6. 等待中断恢复标志并传播，不取消其他 caller 共享的创建任务。 */
            Thread.currentThread().interrupt();
            throw failure;
        }
    }

    /**
     * 重验池中连接的目标、会话及活性；失效时仅退休观察到的任务代次，并在锁外排空连接。
     */
    private void validatePooledConnection(BoltTarget target, RunStateRecordedFutureTask<ConnectionPool> task, Connection connection) {
        BoltConnectionBinding binding = connection.getChannel().attr(BoltControlledConnectionFactory.BINDING).get();
        BoltTlsSessionFacts facts = connection.getChannel().attr(BoltControlledConnectionFactory.FACTS).get();
        boolean development = client.developmentMode() && Boolean.TRUE.equals(connection.getChannel().attr(BoltControlledConnectionFactory.DEVELOPMENT).get());
        if (binding == null || !binding.target().equals(target) || (!development && (facts == null || !facts.allows(Instant.now()))) || !connection.getChannel().isActive()) {
            // 仅移除观察到的任务代次，避免旧连接事件退休替代任务。
            synchronized (this) {
                connTasks.remove(encodePoolKey(target), task);
            }
            client.callTracker().drain(connection.getChannel());
            throw new SecurityException("Bolt pooled connection is stale or unbound");
        }
    }

    /**
     * 创建并认证候选，锁内发布池；未发布时仅清理本代任务，最后发出实际结束信号。
     */
    private ConnectionPool createAndPublishPool(BoltTarget target, Url owned, long deadline,
                                                AtomicReference<RunStateRecordedFutureTask<ConnectionPool>> own, CompletableFuture<Void> ended) throws Exception {
        Connection candidate = null;
        boolean published = false;
        try {
            /* 1. 执行前检查排队是否耗尽预算，再委托工厂创建并认证候选。 */
            if (stopped || System.nanoTime() >= deadline) {
                throw new TimeoutException("Bolt queued creation expired");
            }
            candidate = super.create(owned);
            /* 2. 锁内重验后发布单连接池，拒绝停机或截止时间之后完成的候选。 */
            synchronized (this) {
                if (stopped || System.nanoTime() >= deadline) {
                    throw new TimeoutException("Bolt creation expired or stopped");
                }
                ConnectionPool pool = new ConnectionPool(connectionSelectStrategy);
                pool.add(candidate);
                published = true;
                return pool;
            }
        } finally {
            /* 3. 未发布时关闭候选且只移除本代任务，最后发出实际创建结束信号。 */
            if (!published) {
                if (candidate != null) {
                    candidate.close();
                }
                connTasks.remove(encodePoolKey(target), own.get());
            }
            ended.complete(null);
        }
    }

    /**
     * 在原生任务上携带 callable 的实际结束信号，不另建共享任务索引。
     */
    private static final class CreationTask extends RunStateRecordedFutureTask<ConnectionPool> {
        /**
         * 实际创建工作结束的信号，用于释放关联 caller 计数，而非表示创建成功。
         */
        final CompletableFuture<Void> completion;

        /**
         * 将原生创建 callable 与其实际结束信号绑定。
         */
        CreationTask(Callable<ConnectionPool> callable, CompletableFuture<Void> completion) {
            super(callable);
            this.completion = completion;
        }
    }

    /**
     * 获取受控创建任务携带的结束信号。
     */
    private CompletableFuture<Void> completionOf(RunStateRecordedFutureTask<ConnectionPool> task) {
        return ((CreationTask) task).completion;
    }

    /**
     * 仅移除仍包含该连接的已完成池任务，避免旧连接事件退休新代次；关闭退休池中的该连接。
     */
    @Override
    public void remove(Connection connection) {
        /* 1. 从连接取得目标绑定；无绑定时不操作受控池任务。 */
        BoltConnectionBinding binding = connection.getChannel().attr(BoltControlledConnectionFactory.BINDING).get();
        if (binding == null) {
            return;
        }
        String key = encodePoolKey(binding.target());
        /* 2. 锁内只退休已完成且仍包含该连接的任务，避免旧连接事件误删新代次。 */
        ConnectionPool retired = null;
        synchronized (this) {
            RunStateRecordedFutureTask<ConnectionPool> task = connTasks.get(key);
            if (task == null || !task.isDone()) {
                return;
            }
            try {
                ConnectionPool pool = task.get();
                if (!pool.contains(connection)) {
                    return;
                }
                if (connTasks.remove(key, task)) {
                    retired = pool;
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException failure) {
                connTasks.remove(key, task);
            }
        }
        /* 3. 在管理器锁外移除并尝试关闭退休池中的连接。 */
        if (retired != null) {
            retired.removeAndTryClose(connection);
        }
    }

    /**
     * 拒绝原生按 Url 自动取建入口，始终抛出 SecurityException。
     */
    @Override
    public Connection getAndCreateIfAbsent(Url url) {
        throw new SecurityException("Use controlled Bolt target acquisition");
    }

    /**
     * 停机时拒绝创建，否则重验 Url 绑定后委托原生创建，传播创建异常。
     */
    @Override
    public Connection create(Url url) throws RemotingException {
        /* 1. 停机后拒绝创建；运行中也必须重新验证 Url 的受控绑定。 */
        if (stopped) {
            throw new SecurityException("Bolt creation stopped");
        }
        binding(url);
        /* 2. 绑定通过后委托原生创建，继续由受控工厂承担认证与失败清理。 */
        return super.create(url);
    }

    /**
     * 拒绝无身份绑定的裸地址创建，始终抛出 SecurityException。
     */
    @Override
    public Connection create(String address, int timeout) {
        throw new SecurityException("Bare Bolt manager address disabled");
    }

    /**
     * 拒绝无身份绑定的主机端口创建，始终抛出 SecurityException。
     */
    @Override
    public Connection create(String host, int port, int timeout) {
        throw new SecurityException("Bare Bolt manager address disabled");
    }

    /**
     * 禁止原生自动补连绕过共享创建预算，始终抛出 SecurityException。
     */
    @Override
    public void createConnectionAndHealIfNeed(Url url) {
        throw new SecurityException("Bolt native heal disabled");
    }

    /**
     * 禁止原生管理预热绕过受控创建，始终抛出 SecurityException。
     */
    @Override
    public void createConnectionInManagement(Url url) {
        throw new SecurityException("Bolt management warmup disabled");
    }

    /**
     * 同线程调用原生 Client 关闭；延后 Manager 失败至原生后续清理尝试完成，嵌套调用拒绝。
     */
    public void shutdownOwnedClient(Runnable nativeClientShutdown) {
        if (shutdownScope.get() != null) {
            throw new IllegalStateException("Bolt owned client shutdown reentered");
        }
        ShutdownScope scope = new ShutdownScope();
        shutdownScope.set(scope);
        try {
            nativeClientShutdown.run();
        } catch (RuntimeException | Error failure) {
            if (scope.failure == null) {
                scope.failure = failure;
            } else if (scope.failure != failure) {
                scope.failure.addSuppressed(failure);
            }
        } finally {
            shutdownScope.remove();
        }
        if (scope.failure instanceof RuntimeException failure) {
            throw failure;
        }
        if (scope.failure instanceof Error failure) {
            throw failure;
        }
    }

    /**
     * 停止接单并有界等待创建线程，始终尝试原生关闭；拥有者作用域外直接传播失败。
     */
    @Override
    public void shutdown() {
        boolean interrupted = Thread.interrupted();
        Throwable failure = null;
        try {
            stopped = true;
            creation.shutdown();
            if (!creation.awaitTermination(policy.getConnectionCreationTimeout().toMillis() + 1000, TimeUnit.MILLISECONDS)) {
                failure = new IllegalStateException("Bolt creation workers have not terminated");
            }
        } catch (InterruptedException cause) {
            interrupted = true;
            failure = new IllegalStateException("Bolt shutdown interrupted", cause);
        } catch (RuntimeException | Error cause) {
            failure = cause;
        } finally {
            try {
                super.shutdown();
            } catch (RuntimeException | Error cleanup) {
                if (failure == null) {
                    failure = cleanup;
                } else if (failure != cleanup) {
                    failure.addSuppressed(cleanup);
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        ShutdownScope scope = shutdownScope.get();
        if (scope != null) {
            if (scope.failure == null) {
                scope.failure = failure;
            } else if (failure != null && scope.failure != failure) {
                scope.failure.addSuppressed(failure);
            }
        } else if (failure instanceof RuntimeException cause) {
            throw cause;
        } else if (failure instanceof Error cause) {
            throw cause;
        }
    }
}
