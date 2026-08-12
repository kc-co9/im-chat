package com.co.kc.imchat.service.message.infrastructure.config.beans;

import com.co.kc.imchat.service.message.facade.ChatService;
import com.co.kc.imchat.service.message.facade.MessageService;
import com.co.kc.imchat.service.message.interfaces.rpc.ChatRpcService;
import com.co.kc.imchat.service.message.interfaces.rpc.MessageRpcService;
import org.apache.dubbo.config.annotation.DubboService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeBeansTest {

    @Test
    void exposesMessageRpcServiceAsDubboService() {
        DubboService dubboService = MessageRpcService.class.getAnnotation(DubboService.class);

        assertThat(dubboService).isNotNull();
        assertThat(dubboService.interfaceClass()).isEqualTo(MessageService.class);
        assertThat(dubboService.version()).isEqualTo("1.0.0");
    }

    @Test
    void exposesChatRpcServiceAsDubboService() {
        DubboService dubboService = ChatRpcService.class.getAnnotation(DubboService.class);

        assertThat(dubboService).isNotNull();
        assertThat(dubboService.interfaceClass()).isEqualTo(ChatService.class);
        assertThat(dubboService.version()).isEqualTo("1.0.0");
    }
}
