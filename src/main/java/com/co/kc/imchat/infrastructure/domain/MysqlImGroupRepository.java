package com.co.kc.imchat.infrastructure.domain;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.co.kc.imchat.domain.group.Group;
import com.co.kc.imchat.domain.group.GroupId;
import com.co.kc.imchat.domain.group.GroupRepository;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroup;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupChatService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupService;
import com.co.kc.imchat.support.utils.FunctionUtils;
import com.co.kc.imchat.transformer.db.ImChatDbTransformer;
import com.co.kc.imchat.transformer.domain.ImChatDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MysqlImGroupRepository implements GroupRepository {
    private final DbImGroupService dbImGroupService;
    private final DbImGroupChatService dbImGroupChatService;

    @Override
    public Optional<Group> find(GroupId groupId) {
        return dbImGroupService.getByGroupId(groupId.getValue())
                .map(ImChatDomainTransformer.INSTANCE::imGroupFrom);
    }

    @Override
    public List<Group> find(List<GroupId> groupIds) {
        if (CollectionUtils.isEmpty(groupIds)) {
            return Collections.emptyList();
        }
        List<Long> groupIdValues = FunctionUtils.mappingList(groupIds, GroupId::getValue);
        List<DbImGroup> rows = dbImGroupService.listByGroupIds(groupIdValues);
        return ImChatDomainTransformer.INSTANCE.imGroupListFrom(rows);
    }

    @Override
    public List<Group> find(UserId userId) {
        List<DbImGroup> dbImGroups = dbImGroupService.getListByUserId(userId.getValue());
        return ImChatDomainTransformer.INSTANCE.imGroupListFrom(dbImGroups);
    }

    @Override
    public void save(Group group) {
        DbImGroup row = ImChatDbTransformer.INSTANCE.dbImGroupFrom(group);
        dbImGroupService.saveOrUpdate(row);
    }
}
