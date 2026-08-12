package com.co.kc.imchat.broker.sdk.loadbalance;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询 Broker 地址选择器。
 */
public class RoundRobinBrokerAddressSelector implements BrokerAddressSelector {
    private final AtomicInteger index = new AtomicInteger();

    @Override
    public String select(List<String> addresses, String routeKey) {
        int selectedIndex = Math.floorMod(index.getAndIncrement(), addresses.size());
        return addresses.get(selectedIndex);
    }
}
