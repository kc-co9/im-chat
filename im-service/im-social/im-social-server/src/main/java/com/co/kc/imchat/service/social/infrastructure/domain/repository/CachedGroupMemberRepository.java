package com.co.kc.imchat.service.social.infrastructure.domain.repository;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.anno.CacheInvalidate;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.service.social.domain.group.model.GroupMember;
import com.co.kc.imchat.service.social.domain.group.repository.GroupMemberRepository;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.social.infrastructure.support.constant.SocialCacheNames;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class CachedGroupMemberRepository implements GroupMemberRepository {
    private final MysqlGroupMemberRepository delegate;
    private final Cache<String, GroupMember> groupMemberCache;

    public CachedGroupMemberRepository(MysqlGroupMemberRepository delegate, Cache<String, GroupMember> groupMemberCache) {
        this.delegate = delegate;
        this.groupMemberCache = groupMemberCache;
    }

    @Override
    @Cached(name = SocialCacheNames.GROUP_MEMBERS, key = "#groupId.value()", cacheType = CacheType.REMOTE,
            expire = 5, timeUnit = TimeUnit.MINUTES, postCondition = "!#result.isEmpty()")
    public List<GroupMember> find(GroupId groupId) {
        return delegate.find(groupId);
    }

    @Override
    public Optional<GroupMember> find(GroupId groupId, UserId userId) {
        String key = relationKey(groupId, userId);
        GroupMember cachedMember = groupMemberCache.get(key);
        if (cachedMember != null) {
            return Optional.of(cachedMember);
        }
        Optional<GroupMember> member = delegate.find(groupId, userId);
        member.ifPresent(it -> groupMemberCache.put(key, it));
        return member;
    }

    @Override
    @Cached(name = SocialCacheNames.GROUP_MEMBER_CONTAIN, key = "#groupId.value() + ':' + #userId.value()",
            cacheType = CacheType.REMOTE, expire = 5, timeUnit = TimeUnit.MINUTES)
    public boolean contain(GroupId groupId, UserId userId) {
        return delegate.contain(groupId, userId);
    }

    @Override
    @CacheInvalidate(name = SocialCacheNames.GROUP_MEMBERS, key = "#member.getGroupId().value()")
    @CacheInvalidate(name = SocialCacheNames.GROUP_MEMBER, key = "#member.getGroupId().value() + ':' + #member.getUserId().value()")
    @CacheInvalidate(name = SocialCacheNames.GROUP_MEMBER_CONTAIN, key = "#member.getGroupId().value() + ':' + #member.getUserId().value()")
    public void save(GroupMember member) {
        delegate.save(member);
    }

    @Override
    @CacheInvalidate(name = SocialCacheNames.GROUP_MEMBERS, key = "#members.![getGroupId().value()]", multi = true)
    @CacheInvalidate(name = SocialCacheNames.GROUP_MEMBER, key = "#members.![getGroupId().value() + ':' + getUserId().value()]", multi = true)
    @CacheInvalidate(name = SocialCacheNames.GROUP_MEMBER_CONTAIN, key = "#members.![getGroupId().value() + ':' + getUserId().value()]", multi = true)
    public void save(List<GroupMember> members) {
        delegate.save(members);
    }

    @Override
    @CacheInvalidate(name = SocialCacheNames.GROUP_MEMBERS, key = "#groupMember.getGroupId().value()")
    @CacheInvalidate(name = SocialCacheNames.GROUP_MEMBER, key = "#groupMember.getGroupId().value() + ':' + #groupMember.getUserId().value()")
    @CacheInvalidate(name = SocialCacheNames.GROUP_MEMBER_CONTAIN, key = "#groupMember.getGroupId().value() + ':' + #groupMember.getUserId().value()")
    public void remove(GroupMember groupMember) {
        delegate.remove(groupMember);
    }

    private String relationKey(GroupId groupId, UserId userId) {
        return groupId.value() + ":" + userId.value();
    }

}
