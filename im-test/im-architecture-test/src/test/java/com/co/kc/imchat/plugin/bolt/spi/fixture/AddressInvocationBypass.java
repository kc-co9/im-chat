package com.co.kc.imchat.plugin.bolt.spi.fixture;

import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;

public interface AddressInvocationBypass extends BoltInvoker {
    Object invoke(String address, Object request);
}
