package com.co.kc.imchat.broker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Broker 服务启动入口，启用配置属性扫描、定时任务和模块组件扫描。 */
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication(scanBasePackages = "com.co.kc.imchat.broker")
public class ImBrokerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImBrokerApplication.class, args);
    }
}
