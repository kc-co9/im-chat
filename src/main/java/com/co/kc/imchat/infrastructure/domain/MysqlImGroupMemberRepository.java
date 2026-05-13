package com.co.kc.imchat.infrastructure.domain;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.co.kc.imchat.domain.chat.ImGroupId;
import com.co.kc.imchat.domain.chat.ImGroupMember;
import com.co.kc.imchat.domain.chat.ImGroupMemberRepository;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupMemberService;
import com.co.kc.imchat.transformer.db.ImChatDbTransformer;
import com.co.kc.imchat.transformer.domain.ImChatDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

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
    public void saveAll(List<ImGroupMember> members) {
        if (CollectionUtils.isEmpty(members)) {
            return;
        }
        List<DbImGroupMember> rows = ImChatDbTransformer.INSTANCE.dbImGroupMemberListFrom(members);
        dbImGroupMemberService.saveOrUpdateBatch(rows);
    }
}
