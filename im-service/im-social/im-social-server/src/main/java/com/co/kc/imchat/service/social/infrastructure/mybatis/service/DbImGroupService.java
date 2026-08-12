package com.co.kc.imchat.service.social.infrastructure.mybatis.service;

import com.co.kc.imchat.service.social.infrastructure.mybatis.entity.DbImGroup;
import com.co.kc.imchat.service.social.infrastructure.mybatis.mapper.DbImGroupMapper;
import com.co.kc.imchat.plugin.datasource.dao.BaseMybatisService;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

    public List<DbImGroup> getListByUserId(Long userId) {
        return this.baseMapper.selectByUserId(userId);
    }
}
