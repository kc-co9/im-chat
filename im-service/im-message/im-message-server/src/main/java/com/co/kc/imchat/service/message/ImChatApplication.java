package com.co.kc.imchat.service.message;

import com.alicp.jetcache.anno.config.EnableMethodCache;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author kim
 */
@SpringBootApplication(scanBasePackages = {
        "com.co.kc.imchat.plugin",
        "com.co.kc.imchat.service.message"
})
@EnableScheduling
@EnableMethodCache(basePackages = "com.co.kc.imchat.service.message.infrastructure.domain.repository", proxyTargetClass = true)
public class ImChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImChatApplication.class, args);
    }

}
