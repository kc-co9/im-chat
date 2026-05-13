package com.co.kc.imchat.infrastructure.domain;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.co.kc.imchat.domain.chat.ImGroup;
import com.co.kc.imchat.domain.chat.ImGroupId;
import com.co.kc.imchat.domain.chat.ImGroupRepository;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroup;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupService;
import com.co.kc.imchat.support.utils.FunctionUtils;
import com.co.kc.imchat.transformer.db.ImChatDbTransformer;
import com.co.kc.imchat.transformer.domain.ImChatDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MysqlImGroupRepository implements ImGroupRepository {
    private final DbImGroupService dbImGroupService;

    @Override
    public ImGroup find(ImGroupId groupId) {
        return dbImGroupService.getByGroupId(groupId.getValue())
                .map(ImChatDomainTransformer.INSTANCE::imGroupFrom)
                .orElse(null);
    }

    @Override
    public List<ImGroup> find(List<ImGroupId> groupIds) {
        if (CollectionUtils.isEmpty(groupIds)) {
            return Collections.emptyList();
        }
        List<Long> groupIdValues = FunctionUtils.mappingList(groupIds, ImGroupId::getValue);
        List<DbImGroup> rows = dbImGroupService.listByGroupIds(groupIdValues);
        return ImChatDomainTransformer.INSTANCE.imGroupListFrom(rows);
    }

    @Override
    public void save(ImGroup group) {
        DbImGroup row = ImChatDbTransformer.INSTANCE.dbImGroupFrom(group);
        dbImGroupService.saveOrUpdate(row);
    }
}
