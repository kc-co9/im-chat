package com.co.kc.imchat.broker.sdk.access;

/**
 * 调用 Broker 请求入口的已认证主体类别。
 * <p>
 * 该枚举只描述传输身份归属，不代表某个具体 operation 已经获得授权；最终权限仍由
 * {@link BrokerAdmissionType} 和请求语义共同决定。
 */
public enum BrokerCallerRole {
    /** WS Gateway，用于 Gateway 注册、连接路由维护和上行帧请求。 */
    GATEWAY,

    /** Message 服务，用于下行实时帧等服务侧请求。 */
    MESSAGE,

    /** Account 服务，用于关闭连接等账户侧请求。 */
    ACCOUNT,

    /** 其他 Broker 节点，用于成员、Gossip、路由查询和共识控制请求。 */
    BROKER,

    /** 运维或内部管理服务，用于受控的集群读取请求。 */
    OPS,

    /** 仅在配置允许的受信网络回退场景使用，不代表具体业务服务身份。 */
    TRUSTED_NETWORK
}
