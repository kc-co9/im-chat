package com.co.kc.imchat.infrastructure.domain.repository;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.anno.CacheInvalidate;
import com.co.kc.imchat.domain.group.model.Group;
import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.group.repository.GroupRepository;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.infrastructure.support.constant.CacheNames;

import java.util.List;
import java.util.Optional;

public class CachedGroupRepository implements GroupRepository {
    private final MysqlGroupRepository delegate;
    private final Cache<Long, Group> groupIdCache;

    public CachedGroupRepository(MysqlGroupRepository delegate, Cache<Long, Group> groupIdCache) {
        this.delegate = delegate;
        this.groupIdCache = groupIdCache;
    }

    @Override
    public Optional<Group> find(GroupId groupId) {
        Group cachedGroup = groupIdCache.get(groupId.value());
        if (cachedGroup != null) {
            return Optional.of(cachedGroup);
        }
        Optional<Group> group = delegate.find(groupId);
        group.ifPresent(it -> groupIdCache.put(groupId.value(), it));
        return group;
    }

    @Override
    public List<Group> find(UserId userId) {
        return delegate.find(userId);
    }

    @Override
    public List<Group> find(List<GroupId> groupIds) {
        return delegate.find(groupIds);
    }

    @Override
    @CacheInvalidate(name = CacheNames.GROUP_ID, key = "#group.getId().value()")
    public void save(Group group) {
        delegate.save(group);
    }
}
