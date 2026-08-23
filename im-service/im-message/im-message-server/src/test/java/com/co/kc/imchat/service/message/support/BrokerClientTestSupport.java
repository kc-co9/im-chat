package com.co.kc.imchat.service.message.support;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance;
import com.co.kc.imchat.common.model.enums.ServiceName;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.net.URI;
import java.util.List;
import java.util.Map;

public final class BrokerClientTestSupport {
    private BrokerClientTestSupport() {
    }

    public static BrokerClient client(BoltInvoker invoker) {
        return new BrokerClient(invoker, discovery(), ServiceName.IM_BROKER,
                BrokerLoadBalance.HASH, 3000);
    }

    public static DiscoveryClient discovery() {
        return new DiscoveryClient() {
            private final ServiceInstance instance = new Instance();

            @Override
            public List<ServiceInstance> getInstances(String serviceId) {
                return List.of(instance);
            }

            @Override
            public List<String> getServices() {
                return List.of(ServiceName.IM_BROKER.value());
            }

            @Override
            public String description() {
                return "test discovery";
            }
        };
    }

    private record Instance() implements ServiceInstance {
        @Override public String getInstanceId() { return "broker-test"; }
        @Override public String getServiceId() { return ServiceName.IM_BROKER.value(); }
        @Override public String getHost() { return "127.0.0.1"; }
        @Override public int getPort() { return 12200; }
        @Override public boolean isSecure() { return false; }
        @Override public URI getUri() { return URI.create("http://127.0.0.1:12200"); }
        @Override public String getScheme() { return "http"; }
        @Override public Map<String, String> getMetadata() { return Map.of(); }
    }
}
