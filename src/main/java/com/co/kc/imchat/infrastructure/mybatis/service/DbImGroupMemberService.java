package com.co.kc.imchat.infrastructure.mybatis.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.mapper.DbImGroupMemberMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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

    public List<Map<String, Object>> countByGroupIds(List<Long> groupIds) {
        if (com.baomidou.mybatisplus.core.toolkit.CollectionUtils.isEmpty(groupIds)) {
            return java.util.Collections.emptyList();
        }
        QueryWrapper<DbImGroupMember> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("group_id", "count(*) as member_count")
                .in("group_id", groupIds)
                .groupBy("group_id");
        return listMaps(queryWrapper);
    }
}
