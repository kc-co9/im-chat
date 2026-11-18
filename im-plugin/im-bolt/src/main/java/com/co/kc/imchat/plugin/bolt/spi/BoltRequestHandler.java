package com.co.kc.imchat.plugin.bolt.spi;

import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;

/**
 * Bolt 请求处理器。
 * <p>
 * 每个处理器负责一个 service + operation 组合。
 */
public interface BoltRequestHandler {

    /**
     * 当前处理器所属服务名。
     *
     * @return 服务名
     */
    String service();

    /**
     * 当前处理器处理的操作名。
     *
     * @return 操作名
     */
    String operation();

    /**
     * 处理请求负载及其传输上下文；身份是否可信由具体入口校验。
     *
     * @param context 传输上下文，测试或无连接请求可能为空
     * @param payload 反序列化前的 JSON 负载
     * @return 响应对象
     * @throws Exception 处理失败时抛出
     */
    Object handle(BoltRequestContext context, String payload) throws Exception;
}
