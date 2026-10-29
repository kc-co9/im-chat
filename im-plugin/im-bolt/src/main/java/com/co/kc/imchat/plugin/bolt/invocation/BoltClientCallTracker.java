package com.co.kc.imchat.plugin.bolt.invocation;

import com.alipay.remoting.Connection;
import com.alipay.remoting.InvokeCallback;
import com.alipay.remoting.InvokeContext;
import com.alipay.remoting.InvokeFuture;
import com.alipay.remoting.RemotingCommand;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import io.netty.channel.Channel;
import io.netty.util.Timeout;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 集中管理原生 RPC 准入、排空及完成记账；实例自身也是与客户端生命周期共享的唯一锁。
 */
public final class BoltClientCallTracker {
    /* 同步原生调用上下文中的完成包装器键，仅由本追踪器管理。 */
    private static final String COMPLETION = BoltClientCallTracker.class.getName() + ".completion";
    /* 唯一关闭准入标志；客户端通过受控方法在本实例锁内写入，连接获取允许锁外读取。 */
    private volatile boolean closed;
    /* 由固定策略快照确定的全客户端在途上限。 */
    private final int maxInflightRpcs;
    /* 各 Channel 已登记且未释放的 RPC 数，仅在本实例锁内访问。 */
    private final Map<Channel, Integer> inflight = new HashMap<>();
    /* 物理关闭完成前禁止新准入的 Channel，仅在本实例锁内访问。 */
    private final Set<Channel> draining = new HashSet<>();
    /* 全客户端已登记且未释放的 RPC 总数，仅在本实例锁内访问。 */
    private int active;

    /**
     * 固定准入上限，不创建线程、计时器或独立生命周期资源。
     */
    public BoltClientCallTracker(int maxInflightRpcs) {
        this.maxInflightRpcs = maxInflightRpcs;
    }

    /** 返回唯一关闭准入状态；保留锁外读取所需的 volatile 可见性。 */
    public boolean isClosed() {
        return closed;
    }

    /** 在共享实例 monitor 内单向关闭准入，不执行物理关闭或重开。 */
    public synchronized void closeAdmission() {
        closed = true;
    }

    /**
     * 包装原生异步 future，保留原生响应、回调及计时器协议。
     */
    public InvokeFuture track(InvokeFuture nativeFuture) {
        return new Completion(nativeFuture);
    }

    /**
     * 包装同步原生 future，并在本次上下文保存其完成关联。
     */
    public InvokeFuture track(InvokeFuture nativeFuture, InvokeContext context) {
        /* 1. 包装原生 future，并将同步结束时需要的关联保存在本次上下文。 */
        Completion completion = new Completion(nativeFuture);
        context.put(COMPLETION, completion);
        return completion;
    }

    /**
     * 仅在原生同步终结或 future 已完成时释放；caller 放弃等待不等于底层完成。
     */
    public void finishSync(InvokeContext context, boolean terminal) {
        /* 1. 查找本次同步调用的包装器；仅终态或底层已完成才释放额度，等待退出本身不算完成。 */
        Completion completion = context == null ? null : context.get(COMPLETION);
        if (completion != null && (terminal || completion.isDone())) {
            completion.finish();
        }
    }

    /**
     * 按连接及原生请求 id 校验受控 future 并原子登记在途额度；关闭、排空或超限时拒绝，兼容完成先于准入的竞态。
     */
    public void admit(Connection connection, int id) {
        /* 1. 共享锁内检查关闭、排空及总额度，拒绝后不修改在途计数。 */
        Completion completion;
        synchronized (BoltClientCallTracker.this) {
            if (closed || connection == null || draining.contains(connection.getChannel())
                    || active >= maxInflightRpcs) {
                throw new BoltInvocationException(BoltInvocationException.Failure.LOCAL,
                        "Bolt RPC admission limit or draining", null);
            }
            /* 2. 按原生请求 id 查找受控包装器，防止绕过追踪或重复准入。 */
            InvokeFuture future = connection.getInvokeFuture(id);
            if (!(future instanceof Completion controlled)) {
                throw new SecurityException("Controlled native RPC future required");
            }
            completion = controlled;
            if (completion.admitted) {
                throw new IllegalStateException("Duplicate Bolt RPC admission");
            }
            /* 3. 在同一临界区绑定 Channel，并登记连接与客户端两级计数。 */
            completion.channel = connection.getChannel();
            inflight.merge(completion.channel, 1, Integer::sum);
            active++;
            completion.admitted = true;
        }
        /* 4. 若完成早于准入登记，补做幂等释放，避免遗漏额度归还。 */
        if (completion.ended.get()) {
            completion.finish();
        }
    }

    /**
     * 标记 Channel 禁止新增准入；无在途 RPC 时立即关闭，否则由最后一次完成触发关闭。
     */
    public void drain(Channel channel) {
        /* 1. 锁内标记排空并读取在途数，使新增准入与排空状态保持原子可见。 */
        boolean close;
        boolean firstDrain;
        synchronized (BoltClientCallTracker.this) {
            close = inflight.getOrDefault(channel, 0) == 0;
            firstDrain = draining.add(channel);
        }
        /* 2. 首次排空时登记关闭监听，物理关闭完成后才移除禁止准入标记。 */
        if (firstDrain) {
            channel.closeFuture().addListener(closedChannel -> {
                synchronized (BoltClientCallTracker.this) {
                    draining.remove(channel);
                }
            });
        }
        /* 3. 无在途调用则锁外关闭，否则由最后一次完成负责关闭。 */
        if (close) {
            channel.close();
        }
    }

    /**
     * 薄委托保留原生future/timer/resolver；不是业务callback执行后才释放。
     */
    private final class Completion implements InvokeFuture {
        /**
         * 保留原生响应、回调、计时器及上下文行为的委托。
         */
        private final InvokeFuture nativeFuture;
        /**
         * 记录已观察到调用终结，覆盖完成先于准入的竞态。
         */
        private final AtomicBoolean ended = new AtomicBoolean();
        /**
         * 成功准入时记录的 Channel，在本实例锁内关联计数。
         */
        private Channel channel;
        /**
         * 是否已登记在途额度，由本实例锁保护。
         */
        private boolean admitted;
        /**
         * 是否已释放登记额度，确保多次终结最多扣减一次。
         */
        private boolean released;

        /**
         * 包装原生 future，不替换其计时器或回调执行机制。
         */
        Completion(InvokeFuture nativeFuture) {
            this.nativeFuture = nativeFuture;
        }

        /**
         * 记录终结并幂等释放已准入额度；若排空 Channel 的在途数归零，在锁外关闭它。
         */
        void finish() {
            /* 1. 先记录已终结，供随后才发生的准入识别完成竞态。 */
            ended.set(true);
            Channel close = null;
            /* 2. 锁内只释放一次已登记额度，在途归零时移除计数并检查排空状态。 */
            synchronized (BoltClientCallTracker.this) {
                if (admitted && !released) {
                    released = true;
                    active--;
                    int remaining = inflight.get(channel) - 1;
                    if (remaining == 0) {
                        inflight.remove(channel);
                        if (draining.contains(channel)) {
                            close = channel;
                        }
                    } else {
                        inflight.put(channel, remaining);
                    }
                }
            }
            /* 3. 将物理关闭留在锁外，避免关闭回调占用计数临界区。 */
            if (close != null) {
                close.close();
            }
        }

        /**
         * 先向原生 future 交付响应，再释放准入额度，不等待业务回调执行。
         */
        public void putResponse(RemotingCommand response) {
            /* 1. 先交付原生响应，保留其原有完成协议。 */
            nativeFuture.putResponse(response);
            /* 2. 原生交付返回后释放额度，不等待业务回调执行。 */
            finish();
        }

        /**
         * 委托原生 future 按 timeout 毫秒等待响应，并传播中断。
         */
        public RemotingCommand waitResponse(long timeout) throws InterruptedException {
            return nativeFuture.waitResponse(timeout);
        }

        /**
         * 委托原生 future 等待响应，并传播中断。
         */
        public RemotingCommand waitResponse() throws InterruptedException {
            return nativeFuture.waitResponse();
        }

        /**
         * 委托原生 future 为指定远端地址生成连接关闭响应。
         */
        public RemotingCommand createConnectionClosedResponse(InetSocketAddress address) {
            return nativeFuture.createConnectionClosedResponse(address);
        }

        /**
         * 返回原生调用标识。
         */
        public int invokeId() {
            return nativeFuture.invokeId();
        }

        /**
         * 委托原生 future 执行调用回调。
         */
        public void executeInvokeCallback() {
            nativeFuture.executeInvokeCallback();
        }

        /**
         * 委托原生 future 尝试异步执行异常回调。
         */
        public void tryAsyncExecuteInvokeCallbackAbnormally() {
            nativeFuture.tryAsyncExecuteInvokeCallbackAbnormally();
        }

        /**
         * 向原生 future 记录失败原因。
         */
        public void setCause(Throwable cause) {
            nativeFuture.setCause(cause);
        }

        /**
         * 返回原生 future 的失败原因。
         */
        public Throwable getCause() {
            return nativeFuture.getCause();
        }

        /**
         * 返回原生调用回调。
         */
        public InvokeCallback getInvokeCallback() {
            return nativeFuture.getInvokeCallback();
        }

        /**
         * 向原生 future 登记超时计时任务。
         */
        public void addTimeout(Timeout timeout) {
            nativeFuture.addTimeout(timeout);
        }

        /**
         * 委托原生 future 取消超时计时任务。
         */
        public void cancelTimeout() {
            nativeFuture.cancelTimeout();
        }

        /**
         * 返回原生 future 是否已完成。
         */
        public boolean isDone() {
            return nativeFuture.isDone();
        }

        /**
         * 返回原生 future 使用的应用类加载器。
         */
        public ClassLoader getAppClassLoader() {
            return nativeFuture.getAppClassLoader();
        }

        /**
         * 返回原生 future 的协议编码。
         */
        public byte getProtocolCode() {
            return nativeFuture.getProtocolCode();
        }

        /**
         * 向原生 future 设置调用上下文。
         */
        public void setInvokeContext(InvokeContext context) {
            nativeFuture.setInvokeContext(context);
        }

        /**
         * 返回原生 future 的调用上下文。
         */
        public InvokeContext getInvokeContext() {
            return nativeFuture.getInvokeContext();
        }
    }
}
