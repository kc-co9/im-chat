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
        private Tls tls = new Tls();
    }

    @Data
    public static class Server {
        private boolean enabled;

        private String host = "0.0.0.0";

        private int port = 12200;
        private Tls tls = new Tls();
    }

    @Data
    public static class Tls {
        private boolean enabled;
        private boolean needClientAuth = true;
        private String keyStore;
        private String keyStorePassword;
        private String keyStoreType = "PKCS12";
    }
}
