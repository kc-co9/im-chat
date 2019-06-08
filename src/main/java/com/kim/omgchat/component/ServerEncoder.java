package com.kim.omgchat.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.omgchat.dto.UserMessageSendDTO;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;

/**
 * <p>
 * 配置WebSocket解码器，用于发送请求的时候可以发送Object对象，实则是json数据
 * sendObject()
 * </p>
 *
 * @author kim
 * @since 2019/6/8 23:04
 */
public class ServerEncoder  implements Encoder.Text<UserMessageSendDTO>{

    @Override
    public void destroy() {
        // TODO Auto-generated method stub

    }

    @Override
    public void init(EndpointConfig arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public String encode(UserMessageSendDTO messageSendDTO) throws EncodeException {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(messageSendDTO);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return "";
        }
    }
}
