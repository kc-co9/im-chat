package com.co.kc.imchat.broker.sdk.loadbalance;

import java.util.List;

/**
 * Broker 地址选择器。
 */
public interface BrokerAddressSelector {

    /**
     * 选择一个 Broker 地址。
     *
     * @param addresses Broker 地址列表
     * @param routeKey  业务路由键，可为空
     * @return Broker 地址
     */
    String select(List<String> addresses, String routeKey);
}
