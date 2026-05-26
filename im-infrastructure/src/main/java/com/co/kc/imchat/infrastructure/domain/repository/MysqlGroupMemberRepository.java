package com.co.kc.imchat.infrastructure.domain.repository;

import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.group.model.GroupMember;
import com.co.kc.imchat.domain.group.repository.GroupMemberRepository;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupMemberService;
import com.co.kc.imchat.infrastructure.transformer.db.ImChatDbTransformer;
import com.co.kc.imchat.infrastructure.transformer.domain.ImChatDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MysqlGroupMemberRepository implements GroupMemberRepository {
    private final DbImGroupMemberService dbImGroupMemberService;

    @Override
    public List<GroupMember> find(GroupId groupId) {
        List<DbImGroupMember> rows = dbImGroupMemberService.getByGroupId(groupId.value());
        return ImChatDomainTransformer.INSTANCE.imGroupMemberListFrom(rows);
    }

    @Override
    public Optional<GroupMember> find(GroupId groupId, UserId userId) {
        return dbImGroupMemberService.getByGroupIdAndUserId(groupId.value(), userId.value())
                .map(ImChatDomainTransformer.INSTANCE::imGroupMemberFrom);
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
        DbImGroupMember row = ImChatDbTransformer.INSTANCE.dbImGroupMemberFrom(member);
        dbImGroupMemberService.saveOrUpdate(row);
    }

    @Override
    public void save(List<GroupMember> members) {
        if (com.baomidou.mybatisplus.core.toolkit.CollectionUtils.isEmpty(members)) {
            return;
        }
        List<DbImGroupMember> rows = ImChatDbTransformer.INSTANCE.dbImGroupMemberListFrom(members);
        dbImGroupMemberService.saveOrUpdateBatch(rows);
    }

    @Override
    public void remove(GroupMember groupMember) {
        dbImGroupMemberService.removeByGroupIdAndUserId(groupMember.getGroupId().value(), groupMember.getUserId().value());
    }
}
