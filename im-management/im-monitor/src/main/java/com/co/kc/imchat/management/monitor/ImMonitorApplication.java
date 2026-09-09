package com.co.kc.imchat.management.monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Broker 集群只读监控应用。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ImMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImMonitorApplication.class, args);
    }
}
