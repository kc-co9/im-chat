package com.kim.omgchat.config;

import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/5 12:47
 */
public class HttpSessionConfig  extends ServerEndpointConfig.Configurator  {
//    @Override
//    public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response){
//        HttpSession httpSession = (HttpSession)request.getHttpSession();
//        config.getUserProperties().put(HttpSession.class.getName(),httpSession);
//    }
}
