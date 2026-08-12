package com.co.kc.imchat.plugin.dubbo.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dubbo 插件配置。
 */
@ConfigurationProperties(prefix = "im.dubbo")
public class ImDubboProperties {

    private final Registry registry = new Registry();
    private final Protocol protocol = new Protocol();
    private final Consumer consumer = new Consumer();

    public Registry getRegistry() {
        return registry;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public static class Registry {
        private String address;
        private String namespace;
        private String group = "DUBBO_GROUP";

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }
    }

    public static class Protocol {
        private String name = "dubbo";
        private int port = -1;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    public static class Consumer {
        private int timeout = 3000;
        private boolean check;

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public boolean isCheck() {
            return check;
        }

        public void setCheck(boolean check) {
            this.check = check;
        }
    }
}
