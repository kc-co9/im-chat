package com.co.kc.imchat.management.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * IM 业务管理后台应用。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ImAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImAdminApplication.class, args);
    }
}
