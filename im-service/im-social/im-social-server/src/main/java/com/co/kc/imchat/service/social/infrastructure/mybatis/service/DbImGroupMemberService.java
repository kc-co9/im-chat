package com.co.kc.imchat.service.social.infrastructure.mybatis.service;

import com.co.kc.imchat.service.social.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.service.social.infrastructure.mybatis.mapper.DbImGroupMemberMapper;
import com.co.kc.imchat.plugin.datasource.dao.BaseMybatisService;

import java.util.List;
import java.util.Optional;

public class DbImGroupMemberService extends BaseMybatisService<DbImGroupMemberMapper, DbImGroupMember> {

    public List<DbImGroupMember> getByGroupId(Long groupId) {
        return list(getQueryWrapper().eq(DbImGroupMember::getGroupId, groupId));
    }

    public Optional<DbImGroupMember> getByGroupIdAndUserId(Long groupId, Long userId) {
        return getFirst(getQueryWrapper()
                .eq(DbImGroupMember::getGroupId, groupId)
                .eq(DbImGroupMember::getUserId, userId));
    }

    public void removeByGroupIdAndUserId(Long groupId, Long userId) {
        remove(getQueryWrapper()
                .eq(DbImGroupMember::getGroupId, groupId)
                .eq(DbImGroupMember::getUserId, userId));
    }

}
