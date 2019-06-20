package com.kim.omgchat.controller.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Objects;
import com.kim.omgchat.config.HttpSessionConfig;
import com.kim.omgchat.dto.UserMessageAddDTO;
import com.kim.omgchat.dto.UserMessageQueryDTO;
import com.kim.omgchat.dto.UserMessageReceiveDTO;
import com.kim.omgchat.enums.MessageStatusEnum;
import com.kim.omgchat.enums.MessageTypeEnum;
import com.kim.omgchat.holder.WebOnlineUser;
import com.kim.omgchat.holder.WebUser;
import com.kim.omgchat.holder.WsChatSession;
import com.kim.omgchat.holder.WsSessionHolder;
import com.kim.omgchat.message.Message;
import com.kim.omgchat.message.body.ChatMsg;
import com.kim.omgchat.message.body.CountMsg;
import com.kim.omgchat.service.UserMessageService;
import com.kim.omgchat.service.UserService;
import com.kim.omgchat.utils.JsonUtil;
import org.dozer.DozerBeanMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;

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
@ServerEndpoint(value = "/chatServer/{fromUserId}/{toUserId}",
        configurator = HttpSessionConfig.class, encoders = {ServerEncoder.class})
public class WsChatServer {

    public static UserService userService;

    public static UserMessageService userMessageService;

    public static StringRedisTemplate redisTemplate;

    /**
     * 当前用户userId
     */
    private Long userId;

    private WebUser webUser;
    /**
     * 当前用户session
     */
    private Session userSession;


    //此处是解决无法注入的关键
    private static ApplicationContext applicationContext;

    public static void setApplicationContext(ApplicationContext applicationContext) {
        WsChatServer.applicationContext = applicationContext;
    }


    /**
     * 连接建立成功调用的方法
     * <p>
     * 登录成功前端就发起请求，这个方法被调用
     *
     * @param session 可选的参数。session为与某个客户端的连接会话，需要通过它来给客户端发送数据
     */
    @OnOpen
    public void onOpen(Session session, EndpointConfig config,
                       @PathParam("fromUserId") Long fromUserId,
                       @PathParam("toUserId") Long toUserId) {
        WsSessionHolder.saveChatSession(fromUserId, toUserId, session);

        //记录当前用户ID
        this.userId = fromUserId;

        this.userSession = session;

        //保存session,锁定聊天用户
        WsSessionHolder.saveChatSession(fromUserId, toUserId, session);
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        WsSessionHolder.removeChatSession(userId);
    }


    /**
     * 接收客户端的message
     *
     * @param messageJson 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String messageJson) throws IOException, EncodeException {
        //解析对象
        ObjectMapper mapper = new ObjectMapper();
        UserMessageReceiveDTO userMessageReceiveDTO = null;
        try {
            userMessageReceiveDTO = mapper.readValue(messageJson, UserMessageReceiveDTO.class);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        DozerBeanMapper dozerBeanMapper = new DozerBeanMapper();
        UserMessageAddDTO userMessageAddDTO = dozerBeanMapper.map(userMessageReceiveDTO, UserMessageAddDTO.class);

        //获取对方userId
        Long toUserId = userMessageReceiveDTO.getToUid();
        //获取对方index session
        Session friendSession = WsSessionHolder.getIndexSession(toUserId);
        //获取对方chat session
        WsChatSession wsChatSession = WsSessionHolder.getChatSession(toUserId);

        if (friendSession == null && wsChatSession == null) {
            /* 对方还没有登录 */
            //存进数据库
            userMessageAddDTO.setStatus(MessageStatusEnum.NOT_READ);
            userMessageService.saveUserMessage(userMessageAddDTO);
        } else {
            /* 对方已经登录了 */
            if (wsChatSession == null) {
                /* 对方没有在聊天，在主页上 **/

                //存进数据库
                userMessageAddDTO.setStatus(MessageStatusEnum.NOT_READ);
                userMessageService.saveUserMessage(userMessageAddDTO);

                //查出每个人的未读信息数量
                UserMessageQueryDTO userMessageQueryDTO = new UserMessageQueryDTO();
                userMessageQueryDTO.setFromUserId(userId);
                userMessageQueryDTO.setToUserId(toUserId);
                userMessageQueryDTO.setStatus(MessageStatusEnum.NOT_READ);
                Integer count = userMessageService.countUserMessage(userMessageQueryDTO);

                //构建最新信息

                //存储登录信息
                String onlineKey = generateOnlineUserKey(Long.toString(userId));
                String json = redisTemplate.opsForValue().get(onlineKey);
                WebOnlineUser webOnlineUser = JsonUtil.json2Obj(json, WebOnlineUser.class);
                if (webOnlineUser == null) {
                    webOnlineUser = new WebOnlineUser();
                    webOnlineUser.setNickname("甲");
                }

                ChatMsg chatMsg = new ChatMsg();
                chatMsg.setFromUid(userId);
                chatMsg.setFromUserNickname(webOnlineUser.getNickname());
                chatMsg.setToUid(userMessageReceiveDTO.getToUid());
                chatMsg.setContent(userMessageReceiveDTO.getContent());
                chatMsg.setStatus(MessageStatusEnum.NOT_READ);
                chatMsg.setNotReadCount(count);

                Message<ChatMsg> message = new Message<>();
                message.setMsgBody(chatMsg);
                message.setType(MessageTypeEnum.CHAT_MSG);

                friendSession.getBasicRemote().sendObject(message);
            } else {
                /* 对方在和我聊天 */
                if (Objects.equal(userId, wsChatSession.getToChatUserId())) {
                    //存进数据库
                    userMessageAddDTO.setStatus(MessageStatusEnum.READ);
                    userMessageService.saveUserMessage(userMessageAddDTO);

                    //最新信息
                    ChatMsg chatMsg = new ChatMsg();
                    chatMsg.setFromUid(userId);
                    chatMsg.setToUid(userMessageReceiveDTO.getToUid());
                    chatMsg.setContent(userMessageReceiveDTO.getContent());
                    chatMsg.setStatus(MessageStatusEnum.READ);

                    Message<ChatMsg> message = new Message<>();
                    message.setMsgBody(chatMsg);
                    message.setType(MessageTypeEnum.CHAT_MSG);

                    wsChatSession.getSession().getBasicRemote().sendObject(message);
                } else {
                    /* 对方在和别人聊天 */
                    //存进数据库
                    userMessageAddDTO.setStatus(MessageStatusEnum.NOT_READ);
                    userMessageService.saveUserMessage(userMessageAddDTO);

                    friendSession = wsChatSession.getSession();
                    //数量消息
                    UserMessageQueryDTO userMessageQueryDTO = new UserMessageQueryDTO();
                    userMessageQueryDTO.setToUserId(toUserId);
                    userMessageQueryDTO.setStatus(MessageStatusEnum.NOT_READ);
                    Integer count = userMessageService.countUserMessage(userMessageQueryDTO);
                    CountMsg countMsg = new CountMsg();
                    countMsg.setMsgCount(count);

                    Message<CountMsg> message = new Message<>();
                    message.setMsgBody(countMsg);
                    message.setType(MessageTypeEnum.COUNT_MSG);

                    friendSession.getBasicRemote().sendObject(message);
                }
            }
        }


        /** 发送给自己 **/
        ChatMsg chatMsg = new ChatMsg();
        chatMsg.setFromUid(userId);
        chatMsg.setToUid(userMessageReceiveDTO.getToUid());
        chatMsg.setContent(userMessageReceiveDTO.getContent());
        chatMsg.setStatus(MessageStatusEnum.READ);

        Message<ChatMsg> message = new Message<>();
        message.setMsgBody(chatMsg);
        message.setType(MessageTypeEnum.CHAT_MSG);
        userSession.getBasicRemote().sendObject(message);
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
