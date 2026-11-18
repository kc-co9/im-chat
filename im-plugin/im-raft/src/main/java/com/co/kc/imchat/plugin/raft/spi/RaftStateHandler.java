package com.co.kc.imchat.plugin.raft.spi;

/** 业务提供的已提交日志和快照回调；相同日志必须在所有节点产生相同状态变化。 */
public interface RaftStateHandler<C, R> {
    /** 只应用已经提交的日志，不执行 RPC、读当前时间或产生随机值。 */
    R apply(C command);

    /** 返回当前已应用状态的完整快照编码。 */
    byte[] snapshot();

    /** 在恢复或 follower 追赶时原子恢复业务状态。 */
    void restore(byte[] snapshot);
}
