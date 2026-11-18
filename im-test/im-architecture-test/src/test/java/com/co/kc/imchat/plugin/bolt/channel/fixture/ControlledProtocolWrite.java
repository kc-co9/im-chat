package com.co.kc.imchat.plugin.bolt.channel.fixture;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public class ControlledProtocolWrite {
    public void forwardNativeProtocol(ChannelHandlerContext context, Object message, ChannelPromise promise) {
        context.write(message, promise);
    }
}
