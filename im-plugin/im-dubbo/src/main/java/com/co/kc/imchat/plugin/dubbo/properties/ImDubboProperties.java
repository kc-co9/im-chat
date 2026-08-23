package com.co.kc.imchat.plugin.dubbo.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dubbo 插件配置。
 */
@ConfigurationProperties(prefix = "im.dubbo")
@Getter
public class ImDubboProperties {

    private final Registry registry = new Registry();
    private final Protocol protocol = new Protocol();
    private final Consumer consumer = new Consumer();

    @Getter
    @Setter
    public static class Registry {
        private String address;
        private String namespace;
        private String group = "DUBBO_GROUP";

    }

    @Getter
    @Setter
    public static class Protocol {
        private String name = "dubbo";
        private int port = -1;

    }

    @Getter
    @Setter
    public static class Consumer {
        private int timeout = 3000;
        private boolean check;

    }
}
