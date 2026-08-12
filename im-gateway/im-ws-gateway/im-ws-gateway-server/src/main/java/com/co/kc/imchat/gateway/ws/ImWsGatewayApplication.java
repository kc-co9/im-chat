package com.co.kc.imchat.gateway.ws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ImWsGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImWsGatewayApplication.class, args);
    }
}
