package com.co.kc.imchat.infrastructure.mybatis.service;

import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroup;
import com.co.kc.imchat.infrastructure.mybatis.mapper.DbImGroupMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class DbImGroupService extends BaseMybatisService<DbImGroupMapper, DbImGroup> {

    public Optional<DbImGroup> getByGroupId(Long groupId) {
        return getFirst(getQueryWrapper().eq(DbImGroup::getGroupId, groupId));
    }

    public List<DbImGroup> listByGroupIds(List<Long> groupIds) {
        if (CollectionUtils.isEmpty(groupIds)) {
            return Collections.emptyList();
        }
        return list(getQueryWrapper().in(DbImGroup::getGroupId, groupIds));
    }
}
