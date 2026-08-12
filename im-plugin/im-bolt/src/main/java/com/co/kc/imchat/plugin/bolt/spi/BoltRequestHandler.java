package com.co.kc.imchat.plugin.bolt.spi;

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
     * 处理反序列化前的 JSON 负载。
     *
     * @param payload 请求负载
     * @return 响应对象
     * @throws Exception 处理失败时抛出
     */
    Object handle(String payload) throws Exception;
}
