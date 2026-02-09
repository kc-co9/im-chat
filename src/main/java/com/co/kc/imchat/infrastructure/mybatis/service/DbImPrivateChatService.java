package com.co.kc.imchat.infrastructure.mybatis.service;

import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateChat;
import com.co.kc.imchat.infrastructure.mybatis.mapper.DbImPrivateChatMapper;
import org.springframework.stereotype.Service;

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

    public List<DbImPrivateChat> getListByUserId(Long userId) {
        return list(getQueryWrapper()
                .eq(DbImPrivateChat::getMember1, userId)
                .or()
                .eq(DbImPrivateChat::getMember2, userId));
    }

    public Optional<DbImPrivateChat> getByMember1AndMember2(Long member1, Long member2) {
        return getFirst(getQueryWrapper()
                .eq(DbImPrivateChat::getMember1, member1)
                .eq(DbImPrivateChat::getMember2, member2));
    }
}
