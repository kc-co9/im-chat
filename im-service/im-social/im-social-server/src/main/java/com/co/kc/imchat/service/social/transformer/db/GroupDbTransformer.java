package com.co.kc.imchat.service.social.transformer.db;

import com.co.kc.imchat.service.social.domain.group.model.Group;
import com.co.kc.imchat.service.social.domain.group.model.GroupMember;
import com.co.kc.imchat.service.social.domain.group.model.GroupStatus;
import com.co.kc.imchat.service.social.infrastructure.mybatis.entity.DbImGroup;
import com.co.kc.imchat.service.social.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.service.social.infrastructure.mybatis.enums.DbImGroupStatus;
import org.mapstruct.Mapper;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface GroupDbTransformer {
    GroupDbTransformer INSTANCE = Mappers.getMapper(GroupDbTransformer.class);

    default DbImGroup dbImGroupFrom(Group group) {
        DbImGroup dbGroup = new DbImGroup();
        dbGroup.setId(group.getPkId());
        dbGroup.setGroupId(group.getId().value());
        dbGroup.setOwnerId(group.getOwnerId().value());
        dbGroup.setName(group.getName().value());
        dbGroup.setNotification(group.getNotification() == null ? "" : group.getNotification().value());
        dbGroup.setMemberCount(group.getMemberCount().value());
        dbGroup.setStatus(dbImGroupStatusFrom(group.getStatus()));
        return dbGroup;
    }

    List<DbImGroupMember> dbImGroupMemberListFrom(List<GroupMember> members);

    default DbImGroupMember dbImGroupMemberFrom(GroupMember member) {
        DbImGroupMember dbGroupMember = new DbImGroupMember();
        dbGroupMember.setId(member.getPkId());
        dbGroupMember.setGroupId(member.getGroupId().value());
        dbGroupMember.setUserId(member.getUserId().value());
        dbGroupMember.setUserAlias(member.getUserAlias() == null ? "" : member.getUserAlias().value());
        dbGroupMember.setJoinTime(member.getJoinTime());
        return dbGroupMember;
    }

    @ValueMappings(value = {
            @ValueMapping(source = "ACTIVE", target = "NORMAL"),
            @ValueMapping(source = "DISMISSED", target = "DISMISSED")
    })
    DbImGroupStatus dbImGroupStatusFrom(GroupStatus status);
}
