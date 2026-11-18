package com.co.kc.imchat.broker.sdk.model.result;

import java.io.Serializable;

/** 目标 Broker 已取得 quorum ReadIndex 并应用到该位置后的身份与索引证明，不代表最新应用位置。 */
public record QuorumReadIndexResult(
        /* 实际返回该证明的 Broker。 */
        String nodeId,
        /* 已由该节点本机状态机应用的 quorum ReadIndex。 */
        long quorumReadIndex) implements Serializable {
}
