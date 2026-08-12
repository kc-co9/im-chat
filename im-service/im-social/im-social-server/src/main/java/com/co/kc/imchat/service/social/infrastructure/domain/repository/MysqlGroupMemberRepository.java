package com.co.kc.imchat.service.social.infrastructure.domain.repository;

import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.service.social.domain.group.model.GroupMember;
import com.co.kc.imchat.service.social.domain.group.repository.GroupMemberRepository;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.social.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.service.social.infrastructure.mybatis.service.DbImGroupMemberService;
import com.co.kc.imchat.service.social.transformer.db.GroupDbTransformer;
import com.co.kc.imchat.service.social.transformer.domain.GroupDomainTransformer;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class MysqlGroupMemberRepository implements GroupMemberRepository {
    private final DbImGroupMemberService dbImGroupMemberService;

    @Override
    public List<GroupMember> find(GroupId groupId) {
        List<DbImGroupMember> rows = dbImGroupMemberService.getByGroupId(groupId.value());
        return GroupDomainTransformer.INSTANCE.imGroupMemberListFrom(rows);
    }

    @Override
    public Optional<GroupMember> find(GroupId groupId, UserId userId) {
        return dbImGroupMemberService.getByGroupIdAndUserId(groupId.value(), userId.value())
                .map(GroupDomainTransformer.INSTANCE::imGroupMemberFrom);
    }

    @Override
    public boolean contain(GroupId groupId, UserId userId) {
        return dbImGroupMemberService.isExist(dbImGroupMemberService.getQueryWrapper()
                .select(DbImGroupMember::getId)
                .eq(DbImGroupMember::getGroupId, groupId.value())
                .eq(DbImGroupMember::getUserId, userId.value()));
    }

    @Override
    public void save(GroupMember member) {
        DbImGroupMember row = GroupDbTransformer.INSTANCE.dbImGroupMemberFrom(member);
        dbImGroupMemberService.saveOrUpdate(row);
    }

    @Override
    public void save(List<GroupMember> members) {
        if (com.baomidou.mybatisplus.core.toolkit.CollectionUtils.isEmpty(members)) {
            return;
        }
        List<DbImGroupMember> rows = GroupDbTransformer.INSTANCE.dbImGroupMemberListFrom(members);
        dbImGroupMemberService.saveOrUpdateBatch(rows);
    }

    @Override
    public void remove(GroupMember groupMember) {
        dbImGroupMemberService.removeByGroupIdAndUserId(groupMember.getGroupId().value(), groupMember.getUserId().value());
    }
}
