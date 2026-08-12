package com.co.kc.imchat.gateway.ws.registry;

import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.gateway.ws.sdk.model.dto.GatewayFrameWriteDTO;
import com.co.kc.imchat.gateway.ws.protocol.JsonFrameCodec;
import io.netty.channel.ChannelFuture;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 当前 WS 网关进程内的连接注册表。
 * <p>
 * 只保存本机 Netty Channel，用于本机实时帧写入和向 Broker 上报活跃连接快照。
 */
public class ConnectionRegistry {
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    private final Map<String, Long> connectionUsers = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> userConnections = new ConcurrentHashMap<>();

    /**
     * 注册当前进程持有的连接。
     */
    public void register(Long userId, String connectionId, Channel channel) {
        channels.put(connectionId, channel);
        connectionUsers.put(connectionId, userId);
        userConnections.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(connectionId);
    }

    /**
     * 移除当前进程持有的连接。
     *
     * @return 当前连接所属用户是否已经没有本机活跃连接
     */
    public boolean unregister(String connectionId) {
        channels.remove(connectionId);
        Long userId = connectionUsers.remove(connectionId);
        if (userId == null) {
            return false;
        }
        Set<String> connectionIds = userConnections.get(userId);
        if (connectionIds == null) {
            return true;
        }
        connectionIds.remove(connectionId);
        if (!connectionIds.isEmpty()) {
            return false;
        }
        userConnections.remove(userId, connectionIds);
        return true;
    }

    /**
     * 查询当前进程仍处于活跃状态的用户 ID。
     */
    public List<Long> activeUserIds() {
        return userConnections.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .map(channels::get)
                        .anyMatch(channel -> channel != null && channel.isActive()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /**
     * 查询当前进程仍处于活跃状态的连接 ID。
     */
    public List<String> activeConnectionIds() {
        return channels.entrySet().stream()
                .filter(entry -> entry.getValue().isActive())
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /**
     * 关闭并清空当前进程持有的所有连接。
     */
    public void closeAll() {
        channels.values().forEach(Channel::close);
        channels.clear();
        connectionUsers.clear();
        userConnections.clear();
    }

    /**
     * 向指定连接写入实时帧。
     *
     * @return 当前网关对本次连接写入的处理结果
     */
    public List<GatewayFrameWriteDTO> writeFrame(Long userId, FrameResponse frame) {
        Set<String> connectionIds = userConnections.get(userId);
        if (connectionIds == null || connectionIds.isEmpty()) {
            return List.of();
        }
        List<GatewayFrameWriteDTO> results = new ArrayList<>();
        for (String connectionId : connectionIds) {
            Channel channel = channels.get(connectionId);
            if (channel == null || !channel.isActive()) {
                results.add(GatewayFrameWriteDTO.failed(connectionId));
                continue;
            }
            ChannelFuture future = channel.writeAndFlush(new TextWebSocketFrame(JsonFrameCodec.encodeResponse(frame)));
            future.addListener(result -> {
                if (!result.isSuccess()) {
                    closeFailedConnection(connectionId, channel);
                }
            });
            if (!channel.eventLoop().inEventLoop()) {
                future.awaitUninterruptibly();
            }
            if (!future.isDone() || future.isSuccess()) {
                results.add(GatewayFrameWriteDTO.accepted(connectionId));
                continue;
            }
            results.add(GatewayFrameWriteDTO.failed(connectionId));
            closeFailedConnection(connectionId, channel);
        }
        return results;
    }

    private void closeFailedConnection(String connectionId, Channel channel) {
        unregister(connectionId);
        channel.close();
    }
}
