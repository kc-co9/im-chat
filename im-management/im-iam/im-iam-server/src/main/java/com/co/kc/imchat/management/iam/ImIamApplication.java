package com.co.kc.imchat.management.iam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * IM 管理平台统一身份与权限中心。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ImIamApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImIamApplication.class, args);
    }
}
