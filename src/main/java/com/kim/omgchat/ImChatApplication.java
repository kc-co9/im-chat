package com.kim.omgchat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;

/**
 * @author kim
 */
@SpringBootApplication
@EnableWebSocketMessageBroker
public class ImChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImChatApplication.class, args);
    }

}
