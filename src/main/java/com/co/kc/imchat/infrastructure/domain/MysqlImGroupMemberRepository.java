package com.co.kc.imchat.infrastructure.domain;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.co.kc.imchat.domain.group.ImGroupId;
import com.co.kc.imchat.domain.group.ImGroupMember;
import com.co.kc.imchat.domain.group.ImGroupMemberRepository;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupMemberService;
import com.co.kc.imchat.transformer.db.ImChatDbTransformer;
import com.co.kc.imchat.transformer.domain.ImChatDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MysqlImGroupMemberRepository implements ImGroupMemberRepository {
    private final DbImGroupMemberService dbImGroupMemberService;

    @Override
    public List<ImGroupMember> find(ImGroupId groupId) {
        List<DbImGroupMember> rows = dbImGroupMemberService.getByGroupId(groupId.getValue());
        return ImChatDomainTransformer.INSTANCE.imGroupMemberListFrom(rows);
    }

    @Override
    public ImGroupMember find(ImGroupId groupId, UserId userId) {
        return dbImGroupMemberService.getByGroupIdAndUserId(groupId.getValue(), userId.getValue())
                .map(ImChatDomainTransformer.INSTANCE::imGroupMemberFrom)
                .orElse(null);
    }

    @Override
    public Map<ImGroupId, Integer> countByGroupIds(List<ImGroupId> groupIds) {
        if (CollectionUtils.isEmpty(groupIds)) {
            return Collections.emptyMap();
        }
        List<Long> groupIdValues = groupIds.stream()
                .map(ImGroupId::getValue)
                .collect(Collectors.toList());
        return dbImGroupMemberService.countByGroupIds(groupIdValues).stream()
                .collect(Collectors.toMap(
                        row -> new ImGroupId(((Number) row.get("group_id")).longValue()),
                        row -> ((Number) row.get("member_count")).intValue()));
    }

    @Override
    public void saveAll(List<ImGroupMember> members) {
        if (CollectionUtils.isEmpty(members)) {
            return;
        }
        List<DbImGroupMember> rows = ImChatDbTransformer.INSTANCE.dbImGroupMemberListFrom(members);
        dbImGroupMemberService.saveOrUpdateBatch(rows);
    }
}
