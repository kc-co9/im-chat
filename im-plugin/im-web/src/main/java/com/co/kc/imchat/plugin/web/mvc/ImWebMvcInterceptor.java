package com.co.kc.imchat.plugin.web.mvc;

import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 由业务模块实现，用于把服务自己的 MVC 拦截器注册到通用 Web 配置中。
 */
public interface ImWebMvcInterceptor extends HandlerInterceptor {

    /**
     * 需要拦截的路径。
     */
    default List<String> includePathPatterns() {
        return List.of("/**");
    }

    /**
     * 不需要拦截的路径。
     */
    default List<String> excludePathPatterns() {
        return List.of();
    }
}
