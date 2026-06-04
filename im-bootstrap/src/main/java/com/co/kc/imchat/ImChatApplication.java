package com.co.kc.imchat;

import com.alicp.jetcache.anno.config.EnableMethodCache;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author kim
 */
@SpringBootApplication
@EnableMethodCache(basePackages = "com.co.kc.imchat.infrastructure.domain.repository", proxyTargetClass = true)
public class ImChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImChatApplication.class, args);
    }

}
