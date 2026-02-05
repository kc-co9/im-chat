package com.co.kc.imchat.infrastructure.mybatis.service;

import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.mapper.DbImGroupMemberMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DbImGroupMemberService extends BaseMybatisService<DbImGroupMemberMapper, DbImGroupMember> {

    public List<DbImGroupMember> getListByUserId(Long userId) {
        return list(getQueryWrapper().eq(DbImGroupMember::getUserId, userId));
    }

    public List<DbImGroupMember> getByChatId(Long chatId) {
        return list(getQueryWrapper().eq(DbImGroupMember::getChatId, chatId));
    }
}