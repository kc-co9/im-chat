package com.kim.omgchat.component;

import com.alibaba.druid.util.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.omgchat.config.HttpSessionConfig;
import com.kim.omgchat.constant.RedisKeyConstant;
import com.kim.omgchat.domain.UserDO;
import com.kim.omgchat.dto.UserMessageReceiveDTO;
import com.kim.omgchat.dto.UserMessageSendDTO;
import com.kim.omgchat.dto.UserOnlineDTO;
import com.kim.omgchat.service.UserService;
import org.dozer.DozerBeanMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * <p>
 * TODO
 * </p>
 * https://segmentfault.com/q/1010000010103973/a-1020000016388363
 * @author kim
 * @since 2019/6/5 12:26
 */
@Component
@ServerEndpoint(value = "/chatServer/{uid}", configurator = HttpSessionConfig.class , encoders = {ServerEncoder.class})
public class WebSocketServer {


    public static UserService userService;

    public static StringRedisTemplate redisTemplate;

    /**
     * 用来给用户主动发送消息
     **/
    private Session session;
    /**
     * 存储登录用户的server
     **/
    private static CopyOnWriteArraySet<WebSocketServer> webSocketSet = new CopyOnWriteArraySet<>();
    /**
     * 用户ID和websocket的session绑定的路由表
     **/
    private static Map<Long, Session> routeTable = new HashMap<>();

    private Long userId;


    //此处是解决无法注入的关键
    private static ApplicationContext applicationContext;

    public static void setApplicationContext(ApplicationContext applicationContext) {
        WebSocketServer.applicationContext = applicationContext;
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
        this.session = session;

        //通知用户上线状态(用户的好友和群)
        this.userId = uid;

        //把当前登录用户加进来
        routeTable.put(uid, session);
        //查找用户在线的好友
        List<UserOnlineDTO> onlineUserList = listUserOnlineFriends(uid);
        //发送消息提醒好友我已上线
        notifyOnline(onlineUserList);
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(@PathParam("id") Long uid) {
        routeTable.remove(userId);
        //查找用户在线的好友
        List<UserOnlineDTO> onlineUserList = listUserOnlineFriends(uid);

        //通知用户下线
        notifyOffline(onlineUserList);
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

        //获取对应的好友session
        Session session = routeTable.get(userMessageSendDTO.getToUid());

        try {
            //发送给目标
            session.getBasicRemote().sendObject(userMessageSendDTO);
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


    /**
     * 通知好友我登陆了
     */
    public void notifyOnline(List<UserOnlineDTO> onlineUserList) {
        for (UserOnlineDTO userOnlineDTO : onlineUserList) {
            Session session = routeTable.get(userOnlineDTO.getUserId());
            if (session==null){
                continue;
            }
            try {
                Map<String, Object> map = new HashMap<>();
                map.put("uid", userOnlineDTO.getUserId());
                map.put("type", "online");
                ObjectMapper mapper = new ObjectMapper();
                String text = mapper.writeValueAsString(map);
                session.getBasicRemote().sendText(text);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 通知好友我下线了
     */
    private void notifyOffline(List<UserOnlineDTO> onlineUserList) {
        for (UserOnlineDTO userOnlineDTO : onlineUserList) {
            Session session = routeTable.get(userOnlineDTO.getUserId());
            try {
                Map<String, Object> map = new HashMap<>();
                map.put("uid", userOnlineDTO.getUserId());
                map.put("type", "offline");
                session.getBasicRemote().sendObject(map);

            } catch (IOException | EncodeException e) {
                e.printStackTrace();
            }
        }
    }


    private List<UserOnlineDTO> listUserOnlineFriends(Long uid) {
        //先查找所有好友
        List<UserDO> userDOS = userService.listFriends(uid);
        //获取在线好友
        List<UserOnlineDTO> onlineUserList = new ArrayList<>();
        for (UserDO userDO : userDOS) {
            String onlineKey = RedisKeyConstant.generateOnlineUserKey(Long.toString(userDO.getId()));
            String json = redisTemplate.opsForValue().get(onlineKey);
            //用户在线
            if (!StringUtils.isEmpty(json)) {
                UserOnlineDTO userOnlineDTO = new UserOnlineDTO();
                userOnlineDTO.setUserId(userDO.getId());
                userOnlineDTO.setNickname(userDO.getNickname());

                onlineUserList.add(userOnlineDTO);
            }
        }

        return onlineUserList;
    }

}
