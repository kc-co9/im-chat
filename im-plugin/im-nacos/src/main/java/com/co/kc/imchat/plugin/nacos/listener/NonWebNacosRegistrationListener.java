package com.co.kc.imchat.plugin.nacos.listener;

import com.alibaba.cloud.nacos.registry.NacosAutoServiceRegistration;
import com.alibaba.cloud.nacos.registry.NacosRegistration;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationListener;

/**
 * 为非 Web 应用补充 Nacos 服务注册触发入口。
 * <p>
 * Spring Cloud 默认通过 Web 服务启动事件触发注册，非 Web 应用不会产生该事件。
 */
public final class NonWebNacosRegistrationListener implements ApplicationListener<ApplicationStartedEvent> {

    private final NacosAutoServiceRegistration registration;
    private final NacosRegistration nacosRegistration;

    public NonWebNacosRegistrationListener(NacosAutoServiceRegistration registration,
                                           NacosRegistration nacosRegistration) {
        this.registration = registration;
        this.nacosRegistration = nacosRegistration;
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        if (event.getApplicationContext() instanceof WebServerApplicationContext || registration.isRunning()) {
            return;
        }
        if (nacosRegistration.getPort() <= 0) {
            throw new IllegalStateException("Nacos discovery port is not configured for non-web service:"
                    + nacosRegistration.getServiceId() + "; set spring.cloud.nacos.discovery.port");
        }
        registration.start();
    }
}
