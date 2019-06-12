package com.kim.omgchat.component;

import javax.websocket.Session;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * <p>
 * 存储数据
 * </p>
 *
 * @author kim
 * @since 2019/6/9 22:49
 */
public class WebSocketServerDataComponent {
    /**
     * 存储登录用户的server
     **/
    private static CopyOnWriteArraySet<WebSocketChatServer> webSocketSet = new CopyOnWriteArraySet<>();

    /**
     * 用户ID和websocket的session绑定的路由表
     **/
    private final static ConcurrentHashMap<Long, Session> ROUTE_TABLE = new ConcurrentHashMap<>();

    public static Session getSession(Long userId) {
        return ROUTE_TABLE.get(userId);
    }

    public static void saveSession(Long userId, Session session) {
        ROUTE_TABLE.put(userId, session);
    }

    public static void removeSession(Long userId) {
        ROUTE_TABLE.remove(userId);
    }

}
