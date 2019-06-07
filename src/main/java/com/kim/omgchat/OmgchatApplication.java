package com.kim.omgchat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author kim
 */
@MapperScan(value = "com.kim.omgchat.dao")
@SpringBootApplication
public class OmgchatApplication {

    public static void main(String[] args) {
        SpringApplication.run(OmgchatApplication.class, args);
    }

}
