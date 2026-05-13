package com.co.kc.imchat.infrastructure.mybatis.service;

import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.mapper.DbImGroupMemberMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DbImGroupMemberService extends BaseMybatisService<DbImGroupMemberMapper, DbImGroupMember> {

    public List<DbImGroupMember> getByGroupId(Long groupId) {
        return list(getQueryWrapper().eq(DbImGroupMember::getGroupId, groupId));
    }

    public Optional<DbImGroupMember> getByGroupIdAndUserId(Long groupId, Long userId) {
        return getFirst(getQueryWrapper()
                .eq(DbImGroupMember::getGroupId, groupId)
                .eq(DbImGroupMember::getUserId, userId));
    }
}
