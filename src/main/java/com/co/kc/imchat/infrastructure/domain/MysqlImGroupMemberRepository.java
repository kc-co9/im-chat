package com.co.kc.imchat.infrastructure.domain;

import com.co.kc.imchat.domain.group.GroupId;
import com.co.kc.imchat.domain.group.GroupMember;
import com.co.kc.imchat.domain.group.GroupMemberRepository;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.BaseEntity;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupMemberService;
import com.co.kc.imchat.transformer.db.ImChatDbTransformer;
import com.co.kc.imchat.transformer.domain.ImChatDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MysqlImGroupMemberRepository implements GroupMemberRepository {
    private final DbImGroupMemberService dbImGroupMemberService;

    @Override
    public List<GroupMember> find(GroupId groupId) {
        List<DbImGroupMember> rows = dbImGroupMemberService.getByGroupId(groupId.getValue());
        return ImChatDomainTransformer.INSTANCE.imGroupMemberListFrom(rows);
    }

    @Override
    public Optional<GroupMember> find(GroupId groupId, UserId userId) {
        return dbImGroupMemberService.getByGroupIdAndUserId(groupId.getValue(), userId.getValue())
                .map(ImChatDomainTransformer.INSTANCE::imGroupMemberFrom);
    }

    @Override
    public boolean contain(GroupId groupId, UserId userId) {
        return dbImGroupMemberService.isExist(dbImGroupMemberService.getQueryWrapper()
                .select(BaseEntity::getId)
                .eq(DbImGroupMember::getGroupId, groupId.getValue())
                .eq(DbImGroupMember::getUserId, userId.getValue()));
    }

    @Override
    public void saveAll(List<GroupMember> members) {
        if (com.baomidou.mybatisplus.core.toolkit.CollectionUtils.isEmpty(members)) {
            return;
        }
        List<DbImGroupMember> rows = ImChatDbTransformer.INSTANCE.dbImGroupMemberListFrom(members);
        dbImGroupMemberService.saveOrUpdateBatch(rows);
    }
}
