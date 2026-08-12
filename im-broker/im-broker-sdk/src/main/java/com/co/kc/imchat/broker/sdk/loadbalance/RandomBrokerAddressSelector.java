package com.co.kc.imchat.broker.sdk.loadbalance;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机 Broker 地址选择器。
 */
public class RandomBrokerAddressSelector implements BrokerAddressSelector {

    @Override
    public String select(List<String> addresses, String routeKey) {
        return addresses.get(ThreadLocalRandom.current().nextInt(addresses.size()));
    }
}
