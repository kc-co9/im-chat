package com.co.kc.imchat.plugin.bolt.spi;

import com.co.kc.imchat.plugin.bolt.model.BoltTarget;

/**
 * Bolt 远程调用入口。
 * <p>
 * 统一封装服务名、操作名、序列化和超时控制，业务侧不直接依赖 Bolt 客户端细节。
 */
public interface BoltInvoker {

    /**
     * 以地址和完整预期身份调用；目标不携带URI前缀或信任开关。
     * 前缀、模式及预算资源策略由客户端启动配置拥有，不由业务请求覆盖。
     * 运行期失败保留cause并通过BoltInvocationException的四分类表达，本机目标/策略错误为LOCAL。
     * 连接由受控装配认证；调用只在所选身份绑定连接上发送，不提供地址绕过入口。
     */
    <T, R> R invoke(BoltTarget target, String service, String operation,
                    T request, Class<R> responseType, int timeoutMillis);

}
