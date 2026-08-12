package com.co.kc.imchat.broker.sdk.loadbalance;

import java.util.List;

/**
 * 按路由键取模的 Broker 地址选择器。
 */
public class HashBrokerAddressSelector implements BrokerAddressSelector {
    private final BrokerAddressSelector fallback = new RoundRobinBrokerAddressSelector();

    @Override
    public String select(List<String> addresses, String routeKey) {
        if (routeKey == null || routeKey.isBlank()) {
            return fallback.select(addresses, routeKey);
        }
        int selectedIndex = Math.floorMod(routeKey.hashCode(), addresses.size());
        return addresses.get(selectedIndex);
    }
}
