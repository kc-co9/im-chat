package com.co.kc.imchat.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.domain.group.model.Group;
import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.group.repository.GroupRepository;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroup;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupService;
import com.co.kc.imchat.infrastructure.transformer.db.ImChatDbTransformer;
import com.co.kc.imchat.infrastructure.transformer.domain.ImChatDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MysqlGroupRepository implements GroupRepository {
    private final DbImGroupService dbImGroupService;

    @Override
    public Optional<Group> find(GroupId groupId) {
        return dbImGroupService.getByGroupId(groupId.value())
                .map(ImChatDomainTransformer.INSTANCE::imGroupFrom);
    }

    @Override
    public List<Group> find(List<GroupId> groupIds) {
        if (CollectionUtils.isEmpty(groupIds)) {
            return Collections.emptyList();
        }
        List<Long> groupIdValues = FunctionUtils.mappingList(groupIds, GroupId::value);
        List<DbImGroup> rows = dbImGroupService.listByGroupIds(groupIdValues);
        return ImChatDomainTransformer.INSTANCE.imGroupListFrom(rows);
    }

    @Override
    public List<Group> find(UserId userId) {
        List<DbImGroup> dbImGroups = dbImGroupService.getListByUserId(userId.value());
        return ImChatDomainTransformer.INSTANCE.imGroupListFrom(dbImGroups);
    }

    @Override
    public void save(Group group) {
        DbImGroup row = ImChatDbTransformer.INSTANCE.dbImGroupFrom(group);
        dbImGroupService.saveOrUpdate(row);
    }
}
