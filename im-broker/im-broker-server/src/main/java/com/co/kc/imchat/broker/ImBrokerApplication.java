package com.co.kc.imchat.broker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication(scanBasePackages = "com.co.kc.imchat.broker")
public class ImBrokerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImBrokerApplication.class, args);
    }
}
