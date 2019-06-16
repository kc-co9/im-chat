package com.kim.omgchat.controller.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.omgchat.dto.UserMessageReceiveDTO;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;
import java.io.IOException;

/**
 * <p>
 * 解码器执，它读入Websocket消息，然后输出java对象
 * </p>
 *
 * @author kim
 * @since 2019/6/8 23:08
 */
public class ServerDecoder implements Decoder.Text<UserMessageReceiveDTO> {

    @Override
    public void init(EndpointConfig ec) {
    }

    @Override
    public void destroy() {
    }

    @Override
    public UserMessageReceiveDTO decode(String string) throws DecodeException {
        // Read message...
        ObjectMapper objectMapper=  new ObjectMapper();
        UserMessageReceiveDTO userMessageReceiveDTO = null;
        try {
            userMessageReceiveDTO = objectMapper.readValue(string , UserMessageReceiveDTO.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return userMessageReceiveDTO;
    }

    @Override
    public boolean willDecode(String string) {
        // Determine if the message can be converted into either a
        // MessageA object or a MessageB object...
        return false;
    }
}
