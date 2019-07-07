package com.kim.omgchat.controller.ws;

import com.google.common.base.Objects;
import com.kim.omgchat.config.HttpSessionConfig;
import com.kim.omgchat.dto.UserMessageQueryDTO;
import com.kim.omgchat.dto.UserMessageReceiveDTO;
import com.kim.omgchat.enums.MessageStatusEnum;
import com.kim.omgchat.enums.MessageTypeEnum;
import com.kim.omgchat.holder.WebOnlineUser;
import com.kim.omgchat.holder.WsChatSession;
import com.kim.omgchat.holder.WsSessionHolder;
import com.kim.omgchat.message.Message;
import com.kim.omgchat.message.body.ChatMsg;
import com.kim.omgchat.message.body.CountMsg;
import com.kim.omgchat.service.UserMessageService;
import com.kim.omgchat.service.UserService;
import com.kim.omgchat.utils.JsonUtil;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;


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
     * 当前用户信息
     */
    private WebOnlineUser myUserInfo;
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
                       @PathParam("toUserId") Long toUserId) throws IOException {
        WsSessionHolder.saveChatSession(fromUserId, toUserId, session);

        //获取用户信息
        myUserInfo = WebOnlineUser.generateWebOnlineUser(redisTemplate, fromUserId);

        this.userSession = session;

        //保存session,锁定聊天用户
        WsSessionHolder.saveChatSession(fromUserId, toUserId, session);
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        WsSessionHolder.removeChatSession(myUserInfo.getUserId());
    }


    /**
     * 接收客户端的message
     *
     * @param messageJson 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String messageJson) throws IOException, EncodeException {
        //解析对象
        UserMessageReceiveDTO userMessageReceiveDTO = JsonUtil.json2Obj(messageJson, UserMessageReceiveDTO.class);
        if (userMessageReceiveDTO == null) {
            return;
        }

        MessageStatusEnum messageStatusEnum = MessageStatusEnum.NOT_READ;

        //获取对方userId
        Long toUserId = userMessageReceiveDTO.getToUid();
        //获取对方index session
        Session friendSession = WsSessionHolder.getIndexSession(toUserId);
        //获取对方chat session
        WsChatSession wsChatSession = WsSessionHolder.getChatSession(toUserId);


        /* 对方已经登录了 */
        if (wsChatSession == null && friendSession != null) {
            /* 对方没有在聊天，在主页上 **/
            sendToIndex(friendSession, userMessageReceiveDTO);
        } else if (friendSession == null && wsChatSession != null) {
            /* 对方在和我聊天 */
            if (Objects.equal(myUserInfo.getUserId(), wsChatSession.getToChatUserId())) {
                messageStatusEnum = MessageStatusEnum.READ;

                sendToChatWithMe(wsChatSession.getSession(), userMessageReceiveDTO);
            } else {
                /* 对方在和别人聊天 */
                sendToChatWithOther(wsChatSession.getSession(), userMessageReceiveDTO);
            }
        } else {/* 对方还没有登录或者出错 */}


        //存进数据库
        userMessageService.saveUserMessage(userMessageReceiveDTO, messageStatusEnum);

        /** 发送给自己 **/
        sendToMyself(userSession, userMessageReceiveDTO);
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
     * 发送给对方,对方在index页上
     *
     * @param session               发送的session
     * @param userMessageReceiveDTO 接受的短信
     */
    private void sendToIndex(Session session, UserMessageReceiveDTO userMessageReceiveDTO) throws IOException, EncodeException {
        UserMessageQueryDTO userMessageQueryDTO = new UserMessageQueryDTO();
        userMessageQueryDTO.setFromUserId(userMessageReceiveDTO.getFromUid());
        userMessageQueryDTO.setToUserId(userMessageReceiveDTO.getToUid());
        userMessageQueryDTO.setStatus(MessageStatusEnum.NOT_READ);
        //查找用户未读数量
        Integer count = userMessageService.countUserMessage(userMessageQueryDTO);

        //构建最新信息

        //存储登录信息
        WebOnlineUser friendUserInfo = WebOnlineUser.generateWebOnlineUser(redisTemplate, userMessageReceiveDTO.getFromUid());

        ChatMsg chatMsg = new ChatMsg();
        chatMsg.setFromUid(myUserInfo.getUserId());
        chatMsg.setFromUserNickname(friendUserInfo.getNickname());
        chatMsg.setFromUserAvatar(friendUserInfo.getAvatar());

        chatMsg.setToUid(userMessageReceiveDTO.getToUid());
        chatMsg.setContent(userMessageReceiveDTO.getContent());
        chatMsg.setStatus(MessageStatusEnum.NOT_READ);
        chatMsg.setNotReadCount(count);

        Message<ChatMsg> message = new Message<>();
        message.setMsgBody(chatMsg);
        message.setType(MessageTypeEnum.CHAT_MSG);

        session.getBasicRemote().sendObject(message);
    }

    /**
     * 发送给对方,对方正在和我聊天
     *
     * @param session               发送的session
     * @param userMessageReceiveDTO 接受的短信
     */
    private void sendToChatWithMe(Session session, UserMessageReceiveDTO userMessageReceiveDTO) throws IOException, EncodeException {
        //数量消息
        UserMessageQueryDTO userMessageQueryDTO = new UserMessageQueryDTO();
        userMessageQueryDTO.setToUserId(userMessageReceiveDTO.getToUid());
        userMessageQueryDTO.setStatus(MessageStatusEnum.NOT_READ);
        Integer count = userMessageService.countUserMessage(userMessageQueryDTO);

        WebOnlineUser friendUserInfo = WebOnlineUser.generateWebOnlineUser(redisTemplate, userMessageReceiveDTO.getFromUid());


        //最新信息
        ChatMsg chatMsg = new ChatMsg();
        chatMsg.setFromUid(userMessageReceiveDTO.getFromUid());
        chatMsg.setFromUserNickname(friendUserInfo.getNickname());
        chatMsg.setFromUserAvatar(friendUserInfo.getAvatar());

        chatMsg.setToUid(userMessageReceiveDTO.getToUid());
        chatMsg.setToUserNickname(myUserInfo.getNickname());
        chatMsg.setToUserAvatar(myUserInfo.getAvatar());

        chatMsg.setContent(userMessageReceiveDTO.getContent());
        chatMsg.setStatus(MessageStatusEnum.READ);
        chatMsg.setNotReadCount(count);

        Message<ChatMsg> message = new Message<>();
        message.setMsgBody(chatMsg);
        message.setType(MessageTypeEnum.CHAT_MSG);

        session.getBasicRemote().sendObject(message);
    }

    /**
     * 发送给对方,对方和别人在聊天
     *
     * @param session               发送的session
     * @param userMessageReceiveDTO 接受的短信
     */
    private void sendToChatWithOther(Session session, UserMessageReceiveDTO userMessageReceiveDTO) throws IOException, EncodeException {
        //数量消息
        UserMessageQueryDTO userMessageQueryDTO = new UserMessageQueryDTO();
        userMessageQueryDTO.setToUserId(userMessageReceiveDTO.getToUid());
        userMessageQueryDTO.setStatus(MessageStatusEnum.NOT_READ);
        Integer count = userMessageService.countUserMessage(userMessageQueryDTO);

        CountMsg countMsg = new CountMsg();
        countMsg.setMsgCount(count);

        Message<CountMsg> message = new Message<>();
        message.setMsgBody(countMsg);
        message.setType(MessageTypeEnum.COUNT_MSG);

        session.getBasicRemote().sendObject(message);
    }

    /**
     * 发送给我自己
     *
     * @param session               发送的session
     * @param userMessageReceiveDTO 接受的短信
     */
    private void sendToMyself(Session session, UserMessageReceiveDTO userMessageReceiveDTO) throws IOException, EncodeException {
        UserMessageQueryDTO userMessageQueryDTO = new UserMessageQueryDTO();
        userMessageQueryDTO.setToUserId(userMessageReceiveDTO.getToUid());
        userMessageQueryDTO.setStatus(MessageStatusEnum.NOT_READ);
        Integer count = userMessageService.countUserMessage(userMessageQueryDTO);

        // 查找用户信息
        ChatMsg chatMsg = new ChatMsg();
        chatMsg.setFromUid(userMessageReceiveDTO.getFromUid());
        chatMsg.setFromUserNickname(myUserInfo.getNickname());
        chatMsg.setFromUserAvatar(myUserInfo.getAvatar());
        chatMsg.setToUid(userMessageReceiveDTO.getToUid());
        chatMsg.setContent(userMessageReceiveDTO.getContent());
        chatMsg.setStatus(MessageStatusEnum.READ);
        chatMsg.setNotReadCount(count);

        Message<ChatMsg> message = new Message<>();
        message.setMsgBody(chatMsg);
        message.setType(MessageTypeEnum.CHAT_MSG);

        session.getBasicRemote().sendObject(message);
    }


}
