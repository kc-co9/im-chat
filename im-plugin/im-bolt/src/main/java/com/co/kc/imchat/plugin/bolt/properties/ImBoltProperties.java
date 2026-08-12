package com.co.kc.imchat.plugin.bolt.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "im.bolt")
public class ImBoltProperties {
    private Client client = new Client();

    private Server server = new Server();

    @Data
    public static class Client {
        private boolean enabled;
    }

    @Data
    public static class Server {
        private boolean enabled;

        private int port = 12200;
    }
}
