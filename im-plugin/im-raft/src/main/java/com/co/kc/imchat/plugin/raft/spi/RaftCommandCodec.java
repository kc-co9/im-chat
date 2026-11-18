package com.co.kc.imchat.plugin.raft.spi;

/**
 * 业务日志协议；实现必须确定性且线程安全，可由多个运行实例共享。
 * encode 与 decode 可并发执行，不得访问网络、墙上时间或随机源。
 */
public interface RaftCommandCodec<C> {
    /** 返回非空且归本次调用独占的编码数组，不允许无同步共享 scratch buffer。 */
    byte[] encode(C command);

    /** 输入归本条日志独占，可修改；返回非空命令。 */
    C decode(byte[] payload);
}
