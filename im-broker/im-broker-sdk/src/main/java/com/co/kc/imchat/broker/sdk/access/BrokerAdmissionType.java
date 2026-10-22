package com.co.kc.imchat.broker.sdk.access;



/**
 * Broker RPC 操作的准入规则分类，用于选择责任链中的身份、成员资格和请求权限检查。
 * 每个操作必须声明一个分类；分类描述请求需要经过的规则，不表示调用方角色或已经获得授权。
 */
public enum BrokerAdmissionType {
    /** 查询集群节点或已提交拓扑，要求调用方属于允许读取集群信息的内部角色。 */
    CLUSTER_READ,

    /** Broker 注册、心跳、注销及 Gossip 同步，校验调用方身份与请求节点身份和成员资格。 */
    BROKER_PEER,

    /** Broker 间的 Lease、epoch 隔离及 quorum ReadIndex 操作，按操作检查成员及控制权限。 */
    BROKER_CONTROL,

    /** 仅接受真实 TLS 唯一 Broker URI 的当前 voter 观察协议，不使用 Lease 或可信网络回退。 */
    BROKER_OBSERVATION,

    /** Gateway 首次注册，在尚无注册记录时验证调用方身份并建立 Gateway 身份绑定。 */
    GATEWAY_BOOTSTRAP,

    /** 已注册 Gateway 的心跳、注销和连接路由维护，校验注册有效性及调用方身份绑定。 */
    GATEWAY_ROUTE,

    /** Broker 间的连接路由查询和排空清理，校验调用方成员资格及具体操作的执行条件。 */
    BROKER_DATA_PLANE,

    /** 帧处理和连接关闭，按请求业务语义校验 Gateway、Message 或 Account 等调用方角色。 */
    SERVICE_DATA_PLANE
}
