package com.kim.omgchat.holder;

import com.google.common.base.Objects;

import javax.websocket.Session;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/16 13:36
 */
public class WsSessionHolder {
    /**
     * 用户ID和websocket的session绑定的路由表
     **/
    private final static ConcurrentHashMap<Long,Session> INDEX_ROUTE_TABLE = new ConcurrentHashMap<>();
    private final static ConcurrentHashMap<Long,WsChatSession> CHAT_ROUTE_TABLE = new ConcurrentHashMap<>();

    public static Session getIndexSession(Long userId){
        return INDEX_ROUTE_TABLE.get(userId);
    }

    public static void saveIndexSession(Long userId, Session session) {
        INDEX_ROUTE_TABLE.put(userId, session);
    }

    public static void removeIndexSession(Long userId) {
        INDEX_ROUTE_TABLE.remove(userId);
    }

    public static WsChatSession getChatSession(Long userId){
        return CHAT_ROUTE_TABLE.get(userId);
    }


    public static void saveChatSession(Long fromUserId, Long toUserId, Session session) {
        WsChatSession wsChatSession = new WsChatSession();
        wsChatSession.setToChatUserId(toUserId);
        wsChatSession.setSession(session);

        CHAT_ROUTE_TABLE.put(fromUserId, wsChatSession);
    }



    public static void removeChatSession(Long userId) {
        CHAT_ROUTE_TABLE.remove(userId);
//        WsChatSession wsChatSession = CHAT_ROUTE_TABLE.get(userId);
//        wsChatSession.setSession(null);
//        wsChatSession.setToChatUserId(null);
//
//        CHAT_ROUTE_TABLE.put(userId, wsChatSession);
    }
}
