package com.co.kc.imchat.infrastructure.mybatis.service;

import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.mapper.DbImGroupMemberMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class DbImGroupMemberService extends BaseMybatisService<DbImGroupMemberMapper, DbImGroupMember> {

    public List<DbImGroupMember> getListByUserId(Long userId) {
        return list(getQueryWrapper().eq(DbImGroupMember::getUserId, userId));
    }

    public List<DbImGroupMember> getByChatId(Long chatId) {
        return list(getQueryWrapper().eq(DbImGroupMember::getChatId, chatId));
    }

    public List<DbImGroupMember> getListByUserIdAndChatIds(UserId userId, List<Long> chatIds) {
        if (CollectionUtils.isEmpty(chatIds)) {
            return Collections.emptyList();
        }
        return list(getQueryWrapper().eq(DbImGroupMember::getUserId, userId).in(DbImGroupMember::getChatId, chatIds));
    }
}