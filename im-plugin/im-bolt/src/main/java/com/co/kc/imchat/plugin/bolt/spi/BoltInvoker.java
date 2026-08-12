package com.co.kc.imchat.plugin.bolt.spi;

/**
 * Bolt 远程调用入口。
 * <p>
 * 统一封装服务名、操作名、序列化和超时控制，业务侧不直接依赖 Bolt 客户端细节。
 */
public interface BoltInvoker {

    /**
     * 调用远端 Bolt 服务。
     *
     * @param address       远端地址，格式通常为 host:port
     * @param service       服务名
     * @param operation     操作名
     * @param request       请求对象
     * @param responseType  响应类型
     * @param timeoutMillis 调用超时时间，单位毫秒
     * @param <T>           请求类型
     * @param <R>           响应类型
     * @return 响应对象
     */
    <T, R> R invoke(String address, String service, String operation, T request,
                    Class<R> responseType, int timeoutMillis);

}
