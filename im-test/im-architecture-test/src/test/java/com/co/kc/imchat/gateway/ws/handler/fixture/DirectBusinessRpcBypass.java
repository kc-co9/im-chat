package com.co.kc.imchat.gateway.ws.handler.fixture;

import com.alipay.remoting.codec.Codec;
import com.alipay.remoting.config.BoltClientOption;
import com.alipay.remoting.connection.DefaultConnectionFactory;
import com.alipay.remoting.rpc.RpcClient;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

public class DirectBusinessRpcBypass {
    public Object invoke(RpcClient client, String address, Object request) throws Exception {
        return client.invokeSync(address, request, 1000);
    }

    public DefaultConnectionFactory replaceCodec(RpcClient client, Codec codec) {
        return new DefaultConnectionFactory(codec, null, null, client);
    }

    public void selectTransport(RpcClient client) {
        client.option(BoltClientOption.NETTY_EPOLL_SWITCH, true);
    }

    public static class RawBusinessHelper {
        public void writeRaw(ChannelHandlerContext context, Object bytes) {
            context.writeAndFlush(bytes);
        }

        public void replacePipeline(Channel channel) {
            channel.pipeline().remove("encoder");
        }

        public void writeUnsafe(Channel channel, Object bytes) {
            channel.unsafe().write(bytes, channel.newPromise());
        }
    }
}
