package com.co.kc.imchat.plugin.nacos.validation;

import com.alibaba.cloud.nacos.registry.NacosAutoServiceRegistration;
import com.alibaba.cloud.nacos.registry.NacosRegistration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * 校验 Nacos 服务注册基础设施和注册开关。
 */
public final class NacosRegistrationValidator implements SmartInitializingSingleton {

    private final ObjectProvider<NacosAutoServiceRegistration> autoRegistrationProvider;
    private final ObjectProvider<NacosRegistration> registrationProvider;

    public NacosRegistrationValidator(ObjectProvider<NacosAutoServiceRegistration> autoRegistrationProvider,
                                      ObjectProvider<NacosRegistration> registrationProvider) {
        this.autoRegistrationProvider = autoRegistrationProvider;
        this.registrationProvider = registrationProvider;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (autoRegistrationProvider.getIfAvailable() == null) {
            throw new IllegalStateException(
                    "Nacos registration infrastructure is unavailable while discovery is enabled");
        }
        NacosRegistration registration = registrationProvider.getIfAvailable();
        if (registration == null) {
            throw new IllegalStateException(
                    "Nacos registration infrastructure is unavailable while discovery is enabled");
        }
        if (!registration.isRegisterEnabled()) {
            throw new IllegalStateException(
                    "Nacos registration must remain enabled; spring.cloud.nacos.discovery.register-enabled=false is not allowed");
        }
    }
}
