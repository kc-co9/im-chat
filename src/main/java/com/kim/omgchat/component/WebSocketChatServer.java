package com.kim.omgchat.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.omgchat.config.HttpSessionConfig;
import com.kim.omgchat.dto.UserMessageReceiveDTO;
import com.kim.omgchat.dto.UserMessageSendDTO;
import com.kim.omgchat.service.UserService;
import org.dozer.DozerBeanMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * <p>
 * TODO
 * </p>
 * https://segmentfault.com/q/1010000010103973/a-1020000016388363
 *
 * @author kim
 * @since 2019/6/5 12:26
 */
@Component
@ServerEndpoint(value = "/chatServer/{uid}", configurator = HttpSessionConfig.class, encoders = {ServerEncoder.class})
public class WebSocketChatServer {

    public static UserService userService;

    public static StringRedisTemplate redisTemplate;

    /**
     * 用来给用户主动发送消息
     **/
    private Session session;
    /**
     * 存储登录用户的server
     **/
    private static CopyOnWriteArraySet<WebSocketChatServer> webSocketSet = new CopyOnWriteArraySet<>();
    /**
     * 用户ID和websocket的session绑定的路由表
     **/
    private static Map<Long, Session> routeTable = new HashMap<>();

    private Long userId;


    //此处是解决无法注入的关键
    private static ApplicationContext applicationContext;

    public static void setApplicationContext(ApplicationContext applicationContext) {
        WebSocketChatServer.applicationContext = applicationContext;
    }


    /**
     * 连接建立成功调用的方法
     * <p>
     * 登录成功前端就发起请求，这个方法被调用
     *
     * @param session 可选的参数。session为与某个客户端的连接会话，需要通过它来给客户端发送数据
     */
    @OnOpen
    public void onOpen(Session session, EndpointConfig config, @PathParam("uid") Long uid) {
        //记录当前用户session
        this.session = session;

        //记录当前用户ID
        this.userId = uid;
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        routeTable.remove(userId);
    }


    /**
     * 接收客户端的message,判断是否有接收人而选择进行广播还是指定发送
     *
     * @param messageJson 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String messageJson) {
        //解析对象
        ObjectMapper mapper = new ObjectMapper();
        UserMessageReceiveDTO userMessageReceiveDTO = null;
        try {
            userMessageReceiveDTO = mapper.readValue(messageJson, UserMessageReceiveDTO.class);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        //接收对象 转换 发送对象
        DozerBeanMapper dozerBeanMapper = new DozerBeanMapper();
        UserMessageSendDTO userMessageSendDTO = dozerBeanMapper.map(userMessageReceiveDTO, UserMessageSendDTO.class);

        userMessageSendDTO.setFromUid(userId);

        //获取对应的好友session
        Session friendSession = routeTable.get(userMessageSendDTO.getToUid());
        //自己的session
        Session userSession = routeTable.get(userId);

        try {
            //发送给目标
            friendSession.getBasicRemote().sendObject(userMessageSendDTO);
            userSession.getBasicRemote().sendObject(userMessageSendDTO);
        } catch (IOException | EncodeException e) {
            e.printStackTrace();
        }
    }

    /**
     * 发生错误时调用
     *
     * @param error
     */
    @OnError
    public void onError(Throwable error) {
        error.printStackTrace();
    }


}
