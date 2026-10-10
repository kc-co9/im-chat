package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerManagementProperties;
import com.co.kc.imchat.broker.support.context.BrokerWriteAccessContextInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 注册 Broker 管理写入口的统一授权拦截器。 */
@Configuration
@RequiredArgsConstructor
public class BrokerManagementWebConfiguration implements WebMvcConfigurer {
    private final BrokerManagementProperties properties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new BrokerWriteAccessContextInterceptor(properties))
                .addPathPatterns("/management/cluster-state/**");
    }
}
