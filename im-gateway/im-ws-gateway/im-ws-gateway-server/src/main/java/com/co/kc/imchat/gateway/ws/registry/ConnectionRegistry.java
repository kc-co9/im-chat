package com.co.kc.imchat.gateway.ws.registry;

import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.gateway.ws.sdk.model.dto.GatewayFrameWriteDTO;
import com.co.kc.imchat.gateway.ws.protocol.JsonFrameCodec;
import com.co.kc.imchat.plugin.metrics.annotation.Observed;
import io.netty.channel.ChannelFuture;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 当前 WS 网关进程内的连接注册表。
 * <p>
 * 只保存本机 Netty Channel，用于本机实时帧写入和向 Broker 上报活跃连接快照。
 */
public class ConnectionRegistry {
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    private final Map<String, ConnectionState> connectionStates = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> userConnections = new ConcurrentHashMap<>();
    private final AtomicLong routeRevision = new AtomicLong();

    /**
     * 注册当前进程持有的连接。
     */
    public synchronized long register(Long userId, String sessionVersion,
                                      String connectionId, Channel channel) {
        channels.put(connectionId, channel);
        connectionStates.put(connectionId, new ConnectionState(userId, sessionVersion, connectionId));
        userConnections.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(connectionId);
        return routeRevision.incrementAndGet();
    }

    /**
     * 移除当前进程持有的连接。
     *
     * @return 当前连接所属用户是否已经没有本机活跃连接
     */
    public synchronized RouteMutation unregister(String connectionId) {
        channels.remove(connectionId);
        ConnectionState connectionState = connectionStates.remove(connectionId);
        if (connectionState == null) {
            return new RouteMutation(routeRevision.get(), false);
        }
        long revision = routeRevision.incrementAndGet();
        Long userId = connectionState.userId();
        Set<String> connectionIds = userConnections.get(userId);
        if (connectionIds == null) {
            return new RouteMutation(revision, true);
        }
        connectionIds.remove(connectionId);
        if (!connectionIds.isEmpty()) {
            return new RouteMutation(revision, false);
        }
        userConnections.remove(userId, connectionIds);
        return new RouteMutation(revision, true);
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
     * 在同一临界区读取 routeRevision 水位和完整活跃用户集合。
     */
    public synchronized RouteSnapshot routeSnapshot() {
        return new RouteSnapshot(routeRevision.get(), activeUserIds());
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

    /** 查询指定用户当前活跃的本机连接 ID。 */
    public List<String> activeConnectionIds(Long userId) {
        Set<String> connectionIds = userConnections.get(userId);
        if (connectionIds == null) {
            return List.of();
        }
        return connectionIds.stream()
                .filter(connectionId -> {
                    Channel channel = channels.get(connectionId);
                    return channel != null && channel.isActive();
                })
                .sorted()
                .toList();
    }

    /**
     * 查询活跃连接的只读身份元数据，不暴露 Netty Channel。
     */
    public List<ConnectionState> activeConnections() {
        return connectionStates.values().stream()
                .filter(state -> {
                    Channel channel = channels.get(state.connectionId());
                    return channel != null && channel.isActive();
                })
                .sorted(java.util.Comparator.comparing(ConnectionState::connectionId))
                .toList();
    }

    /**
     * 关闭符合指定用户和会话版本条件的本机连接。
     */
    public List<String> closeConnections(Long userId, String sessionVersion) {
        List<String> connectionIds = connectionStates.values().stream()
                .filter(state -> state.userId().equals(userId)
                        && state.sessionVersion().equals(sessionVersion))
                .map(ConnectionState::connectionId)
                .sorted()
                .toList();
        for (String connectionId : connectionIds) {
            Channel channel = channels.get(connectionId);
            unregister(connectionId);
            if (channel != null) {
                channel.close();
            }
        }
        return connectionIds;
    }

    /**
     * 关闭并清空当前进程持有的所有连接。
     */
    public void closeAll() {
        channels.values().forEach(Channel::close);
        channels.clear();
        connectionStates.clear();
        userConnections.clear();
    }

    /**
     * 向指定连接写入实时帧。
     *
     * @return 当前网关对本次连接写入的处理结果
     */
    @Observed(name = "im.gateway.frame.write")
    public List<GatewayFrameWriteDTO> writeFrame(Long userId, FrameResponse frame) {
        return activeConnectionIds(userId).stream()
                .map(connectionId -> writeFrame(userId, connectionId, frame))
                .toList();
    }

    /** 向指定用户的一条本机连接写入实时帧。 */
    public GatewayFrameWriteDTO writeFrame(
            Long userId, String connectionId, FrameResponse frame) {
        ConnectionState state = connectionStates.get(connectionId);
        Channel channel = channels.get(connectionId);
        if (state == null || !state.userId().equals(userId)
                || channel == null || !channel.isActive()) {
            return GatewayFrameWriteDTO.failed(connectionId);
        }
        ChannelFuture future = channel.writeAndFlush(
                new TextWebSocketFrame(JsonFrameCodec.encodeResponse(frame)));
        future.addListener(result -> {
            if (!result.isSuccess()) {
                closeFailedConnection(connectionId, channel);
            }
        });
        if (!channel.eventLoop().inEventLoop()) {
            future.awaitUninterruptibly();
        }
        if (!future.isDone() || future.isSuccess()) {
            return GatewayFrameWriteDTO.accepted(connectionId);
        }
        closeFailedConnection(connectionId, channel);
        return GatewayFrameWriteDTO.failed(connectionId);
    }

    private void closeFailedConnection(String connectionId, Channel channel) {
        unregister(connectionId);
        channel.close();
    }

    public record RouteMutation(long routeRevision, boolean routeAbsent) {
    }

    public record RouteSnapshot(long routeRevision, List<Long> userIds) {
        public RouteSnapshot {
            userIds = List.copyOf(userIds);
        }
    }

    public record ConnectionState(Long userId, String sessionVersion, String connectionId) {
        public ConnectionState {
            java.util.Objects.requireNonNull(userId, "userId");
            if (sessionVersion == null || sessionVersion.isBlank()) {
                throw new IllegalArgumentException("sessionVersion must not be blank");
            }
            if (connectionId == null || connectionId.isBlank()) {
                throw new IllegalArgumentException("connectionId must not be blank");
            }
        }
    }
}
