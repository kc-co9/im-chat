package com.co.kc.imchat.infrastructure.mybatis.service;

import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateChat;
import com.co.kc.imchat.infrastructure.mybatis.mapper.DbImPrivateChatMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 私聊表(DbImPrivateChat)表服务接口
 *
 * @author kc
 * @since 2026-02-03 11:11:22
 */
@Service
public class DbImPrivateChatService extends BaseMybatisService<DbImPrivateChatMapper, DbImPrivateChat> {

    public Optional<DbImPrivateChat> getByChatId(Long chatId) {
        return getFirst(getQueryWrapper().eq(DbImPrivateChat::getChatId, chatId));
    }

    /**
     * 某用户作为「记录拥有者」的私聊列表（每人每个会话一条记录，user_id = 本人）。
     */
    public List<DbImPrivateChat> getListByUserId(Long userId) {
        return list(getQueryWrapper().eq(DbImPrivateChat::getUserId, userId));
    }

    public Optional<DbImPrivateChat> getByUserIdAndPeerUserId(Long userId, Long peerUserId) {
        return getFirst(getQueryWrapper()
                .eq(DbImPrivateChat::getUserId, userId)
                .eq(DbImPrivateChat::getPeerUserId, peerUserId));
    }

    public boolean contain(Long userId, Long peerUserId) {
        return count(getQueryWrapper()
                .eq(DbImPrivateChat::getUserId, userId)
                .eq(DbImPrivateChat::getPeerUserId, peerUserId)) > 0;
    }

    public List<DbImPrivateChat> listByChatIdsAndUserId(List<Long> chatIds, Long userId) {
        if (chatIds == null || chatIds.isEmpty() || userId == null) {
            return Collections.emptyList();
        }
        return list(getQueryWrapper()
                .eq(DbImPrivateChat::getUserId, userId)
                .in(DbImPrivateChat::getChatId, chatIds));
    }

    public void removeByUserIdAndPeerUserId(Long userId, Long peerUserId) {
        remove(getQueryWrapper()
                .eq(DbImPrivateChat::getUserId, userId)
                .eq(DbImPrivateChat::getPeerUserId, peerUserId));
    }
}
