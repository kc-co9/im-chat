package com.co.kc.imchat.management.ops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Broker 集群只读监控应用。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ImOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImOpsApplication.class, args);
    }
}
