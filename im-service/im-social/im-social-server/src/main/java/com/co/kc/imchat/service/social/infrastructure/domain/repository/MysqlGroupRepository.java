package com.co.kc.imchat.service.social.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.service.social.domain.group.model.Group;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.service.social.domain.group.repository.GroupRepository;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.social.infrastructure.mybatis.entity.DbImGroup;
import com.co.kc.imchat.service.social.infrastructure.mybatis.service.DbImGroupService;
import com.co.kc.imchat.service.social.transformer.db.GroupDbTransformer;
import com.co.kc.imchat.service.social.transformer.domain.GroupDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class MysqlGroupRepository implements GroupRepository {
    private final DbImGroupService dbImGroupService;

    @Override
    public Optional<Group> find(GroupId groupId) {
        return dbImGroupService.getByGroupId(groupId.value())
                .map(GroupDomainTransformer.INSTANCE::imGroupFrom);
    }

    @Override
    public List<Group> find(List<GroupId> groupIds) {
        if (CollectionUtils.isEmpty(groupIds)) {
            return Collections.emptyList();
        }
        List<Long> groupIdValues = FunctionUtils.mappingList(groupIds, GroupId::value);
        List<DbImGroup> rows = dbImGroupService.listByGroupIds(groupIdValues);
        return GroupDomainTransformer.INSTANCE.imGroupListFrom(rows);
    }

    @Override
    public List<Group> find(UserId userId) {
        List<DbImGroup> dbImGroups = dbImGroupService.getListByUserId(userId.value());
        return GroupDomainTransformer.INSTANCE.imGroupListFrom(dbImGroups);
    }

    @Override
    public void save(Group group) {
        DbImGroup row = GroupDbTransformer.INSTANCE.dbImGroupFrom(group);
        boolean persisted = dbImGroupService.saveOrUpdate(row);
        if (!persisted) {
            if (group.getPkId() != null) {
                throw new OptimisticLockingFailureException(
                        "Group was modified concurrently: " + group.getId().value());
            }
            throw new DataAccessResourceFailureException(
                    "Group was not inserted: " + group.getId().value());
        }
        if (persisted) {
            if (row.getId() != null) {
                group.setPkId(row.getId());
            }
            group.setRowVersion(row.getVersion());
        }
    }
}
