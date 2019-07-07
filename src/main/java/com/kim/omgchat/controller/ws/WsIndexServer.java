package com.kim.omgchat.controller.ws;

import com.alibaba.druid.util.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.omgchat.config.HttpSessionConfig;
import com.kim.omgchat.constant.RedisKeyConstant;
import com.kim.omgchat.domain.UserDO;
import com.kim.omgchat.dto.UserOnlineDTO;
import com.kim.omgchat.enums.MessageTypeEnum;
import com.kim.omgchat.enums.UserStatusEnum;
import com.kim.omgchat.holder.WebOnlineUser;
import com.kim.omgchat.holder.WebUser;
import com.kim.omgchat.holder.WsSessionHolder;
import com.kim.omgchat.message.Message;
import com.kim.omgchat.message.body.LoginMsg;
import com.kim.omgchat.service.UserService;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.kim.omgchat.constant.RedisKeyConstant.generateOnlineUserKey;

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
@ServerEndpoint(value = "/onlineServer/{userId}", configurator = HttpSessionConfig.class, encoders = {ServerEncoder.class})
public class WsIndexServer {

    public static UserService userService;

    public static StringRedisTemplate redisTemplate;

    /**
     * 当前用户ID
     */
    private WebOnlineUser myUserInfo;


    //此处是解决无法注入的关键
    private static ApplicationContext applicationContext;

    public static void setApplicationContext(ApplicationContext applicationContext) {
        WsIndexServer.applicationContext = applicationContext;
    }


    /**
     * 连接建立成功调用的方法
     * <p>
     * 登录成功前端就发起请求，这个方法被调用
     *
     * @param session 可选的参数。session为与某个客户端的连接会话，需要通过它来给客户端发送数据
     */
    @OnOpen
    public void onOpen(Session session, EndpointConfig config, @PathParam("userId") Long userId) throws IOException {
        //记录起当前登录用户的session
        WsSessionHolder.saveIndexSession(userId, session);

        //获取用户信息
        String onlineKey = generateOnlineUserKey(Long.toString(userId));
        String userOnlineJson = redisTemplate.opsForValue().get(onlineKey);
        ObjectMapper mapper = new ObjectMapper();
        WebOnlineUser webOnlineUser = mapper.readValue(userOnlineJson, WebOnlineUser.class);

        myUserInfo = new WebOnlineUser();
        myUserInfo.setUserId(webOnlineUser.getUserId());
        myUserInfo.setEmail(webOnlineUser.getEmail());
        myUserInfo.setNickname(webOnlineUser.getNickname());
        myUserInfo.setAvatar(webOnlineUser.getAvatar());

        //提醒用户上线
        List<WebOnlineUser> list = listUserOnlineFriends(userId);
        notifyOnline(list);
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        WsSessionHolder.removeIndexSession(myUserInfo.getUserId());

        //查找用户在线的好友
        List<WebOnlineUser> onlineUserList = listUserOnlineFriends(myUserInfo.getUserId());

        //通知用户下线
        notifyOffline(onlineUserList);
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
    public void notifyOnline(List<WebOnlineUser> onlineUserList) {
        for (WebOnlineUser webOnlineUser : onlineUserList) {
            Session session = WsSessionHolder.getIndexSession(webOnlineUser.getUserId());
            if (session == null) {
                session = WsSessionHolder.getChatSession(webOnlineUser.getUserId()).getSession();
                if (session == null) {
                    continue;
                }
            }
            try {
                LoginMsg msg = new LoginMsg();
                msg.setUserId(myUserInfo.getUserId());
                msg.setNickname(myUserInfo.getNickname());
                msg.setStatus(UserStatusEnum.ONLINE);

                Message<LoginMsg> msgMessage = new Message<>();
                msgMessage.setMsgBody(msg);
                msgMessage.setType(MessageTypeEnum.LOGIN_MSG);

                session.getBasicRemote().sendObject(msgMessage);

            } catch (IOException | EncodeException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 通知好友我下线了
     */
    private void notifyOffline(List<WebOnlineUser> onlineUserList) {
        ObjectMapper mapper = new ObjectMapper();

        for (WebOnlineUser webOnlineUser : onlineUserList) {
            Session session = WsSessionHolder.getIndexSession(webOnlineUser.getUserId());
            try {
                LoginMsg msg = new LoginMsg();
                msg.setUserId(myUserInfo.getUserId());
                msg.setNickname(myUserInfo.getNickname());
                msg.setStatus(UserStatusEnum.OFFLINE);

                Message<LoginMsg> msgMessage = new Message<>();
                msgMessage.setMsgBody(msg);
                msgMessage.setType(MessageTypeEnum.LOGIN_MSG);

                session.getBasicRemote().sendObject(msgMessage);
            } catch (IOException | EncodeException e) {
                e.printStackTrace();
            }
        }
    }

    private List<WebOnlineUser> listUserOnlineFriends(Long uid) {
        //先查找所有好友
        List<UserDO> userDOS = userService.listFriends(uid);
        //获取在线好友
        List<WebOnlineUser> onlineUserList = new ArrayList<>();
        for (UserDO userDO : userDOS) {
            String onlineKey = RedisKeyConstant.generateOnlineUserKey(Long.toString(userDO.getId()));
            String json = redisTemplate.opsForValue().get(onlineKey);
            //用户在线
            if (!StringUtils.isEmpty(json)) {
                WebOnlineUser webOnlineUser = new WebOnlineUser();
                webOnlineUser.setUserId(userDO.getId());
                webOnlineUser.setEmail(userDO.getEmail());
                webOnlineUser.setNickname(userDO.getNickname());

                onlineUserList.add(webOnlineUser);
            }
        }

        return onlineUserList;
    }

}
