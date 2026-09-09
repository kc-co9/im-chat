package com.co.kc.imchat.management.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 管理端统一审计服务启动入口。 */
@SpringBootApplication
public class ImAuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImAuditApplication.class, args);
    }
}
