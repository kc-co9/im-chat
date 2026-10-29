package com.co.kc.imchat.plugin.bolt.channel;

import com.co.kc.imchat.plugin.bolt.invocation.BoltInvocationContext;
import com.co.kc.imchat.plugin.bolt.connection.BoltControlledConnectionFactory;
import com.co.kc.imchat.plugin.bolt.connection.BoltConnectionBinding;
import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.co.kc.imchat.plugin.bolt.security.BoltTlsSessionFacts;

import com.alipay.remoting.Connection;
import com.alipay.remoting.rpc.HeartbeatAckCommand;
import com.alipay.remoting.rpc.HeartbeatCommand;
import com.alipay.remoting.rpc.protocol.RpcRequestCommand;
import com.alipay.remoting.rpc.protocol.RpcResponseCommand;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

/**
 * D11 受控原生协议来源的 typed 发送屏障，位于命令及编码字节两个出站位置。
 * <p>
 * 命令位置校验本次调用目标、预算与已认证会话事实；两个位置都只放行编码字节、心跳和原生响应，
 * 拒绝其他未知出站对象。直接写入的 ByteBuf 与编码结果无法区分而放行，
 * 因此不声称隔离同 JVM 特权代码主动构造的预编码业务 bytes。
 */
public final class BoltOutboundHandler extends ChannelOutboundHandlerAdapter {
    /**
     * 提供模式判定、调用准入计数与连接排空的受控客户端。
     */
    private final BoltControlledClient client;
    /**
     * 标识是否位于编码前的命令校验位置；其他位置不接纳 RPC 请求命令。
     */
    private final boolean commandPosition;

    /**
     * 绑定受控客户端及屏障所在位置，不创建或持有独立连接。
     */
    public BoltOutboundHandler(BoltControlledClient client, boolean commandPosition) {
        this.client = client;
        this.commandPosition = commandPosition;
    }

    /**
     * 校验请求的目标、会话和预算并登记在途计数；拒绝时使 promise 失败，允许的原生协议对象继续出站。
     */
    @Override
    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
        try {
            if (message instanceof RpcRequestCommand command) {
                /* 目标、会话及预算通过后才登记在途准入，拒绝不占用发送额度。 */
                Optional<BoltInvocationException> failure = checkInvocation(context, command);
                if (failure.isPresent()) {
                    throw failure.get();
                }

                Connection connection = context.channel().attr(Connection.CONNECTION).get();
                client.callTracker().admit(connection, command.getId());
            } else if (!(message instanceof ByteBuf)
                    && !(message instanceof HeartbeatCommand)
                    && !(message instanceof HeartbeatAckCommand)
                    && !(message instanceof RpcResponseCommand)) {
                /* 非请求消息只允许编码字节、心跳和原生响应继续出站。 */
                throw new SecurityException("Unsupported Bolt outbound protocol command");
            }
        } catch (RuntimeException rejected) {
            /* 本地校验、排空或准入失败统一拒绝当前 promise，不继续出站。 */
            promise.setFailure(rejected);
            return;
        }

        /* 原生出站位于本地拒绝边界之外，下游异常保留原传播方式。 */
        super.write(context, message, promise);
    }

    /**
     * 按上下文、目标、会话区间和调用预算顺序校验；会话到期时沿用原排空动作。
     */
    private Optional<BoltInvocationException> checkInvocation(ChannelHandlerContext context, RpcRequestCommand command) {
        /* 先提取本机调用上下文和 Channel 绑定、会话事实，不从远端响应补充信任。 */
        BoltInvocationContext invocation = command.getInvokeContext() == null ? null : command.getInvokeContext().get(BoltInvocationContext.KEY);
        BoltConnectionBinding binding = context.channel().attr(BoltControlledConnectionFactory.BINDING).get();
        BoltTlsSessionFacts facts = context.channel().attr(BoltControlledConnectionFactory.FACTS).get();
        boolean development = client.developmentMode() && Boolean.TRUE.equals(context.channel().attr(BoltControlledConnectionFactory.DEVELOPMENT).get());
        /* 只有命令位置且携带受控上下文，才继续核对该调用的目标和认证事实。 */
        BoltInvocationException exception = null;
        if (!commandPosition || invocation == null) {
            exception = new BoltInvocationException(BoltInvocationException.Failure.LOCAL, "Bolt command requires controlled invocation context", null);
        } else if ((!development && facts == null) || binding == null || !binding.target().equals(invocation.target())) {
            /* 绑定与本次目标必须一致；非开发模式还要求已有认证事实。 */
            exception = new BoltInvocationException(BoltInvocationException.Failure.AUTHENTICATION, "Bolt channel pending or target mismatch", null);
        } else if (!development && !facts.allows(Instant.now())) {
            /* 证书时间窗失效先排空连接，再拒绝当前请求，避免新调用继续复用。 */
            client.callTracker().drain(context.channel());
            exception = new BoltInvocationException(BoltInvocationException.Failure.AUTHENTICATION, "Bolt session certificate interval expired", null);
        } else if (invocation.expired(System.nanoTime())) {
            /* 最后以 caller 的单调预算判定是否仍允许发送。 */
            exception = new BoltInvocationException(BoltInvocationException.Failure.LOCAL, "Bolt command caller deadline exhausted", new TimeoutException("caller gate budget"));
        }
        return Optional.ofNullable(exception);
    }
}
