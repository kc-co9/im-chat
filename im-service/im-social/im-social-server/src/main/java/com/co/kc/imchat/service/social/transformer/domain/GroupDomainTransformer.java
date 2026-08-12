package com.co.kc.imchat.service.social.transformer.domain;

import com.co.kc.imchat.service.social.domain.group.model.Group;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.service.social.domain.group.model.GroupMember;
import com.co.kc.imchat.service.social.domain.group.model.GroupName;
import com.co.kc.imchat.service.social.domain.group.model.GroupNotification;
import com.co.kc.imchat.service.social.domain.group.model.GroupStatus;
import com.co.kc.imchat.common.domain.group.model.GroupUserAlias;
import com.co.kc.imchat.service.social.domain.group.model.MemberCount;
import com.co.kc.imchat.service.social.domain.group.model.MemberId;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.social.infrastructure.mybatis.entity.DbImGroup;
import com.co.kc.imchat.service.social.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.service.social.infrastructure.mybatis.enums.DbImGroupStatus;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface GroupDomainTransformer {
    GroupDomainTransformer INSTANCE = Mappers.getMapper(GroupDomainTransformer.class);

    List<Group> imGroupListFrom(List<DbImGroup> dbImGroupList);

    default Group imGroupFrom(DbImGroup dbImGroup) {
        Group group = Group.builder()
                .id(new GroupId(dbImGroup.getGroupId()))
                .ownerId(new UserId(dbImGroup.getOwnerId()))
                .name(new GroupName(dbImGroup.getName()))
                .notification(new GroupNotification(dbImGroup.getNotification()))
                .memberCount(new MemberCount(dbImGroup.getMemberCount() == null ? 0 : dbImGroup.getMemberCount()))
                .status(imGroupStatusFrom(dbImGroup.getStatus()))
                .build();
        group.setPkId(dbImGroup.getId());
        return group;
    }

    List<GroupMember> imGroupMemberListFrom(List<DbImGroupMember> dbImGroupMembers);

    default GroupMember imGroupMemberFrom(DbImGroupMember dbImGroupMember) {
        GroupId groupId = new GroupId(dbImGroupMember.getGroupId());
        UserId userId = new UserId(dbImGroupMember.getUserId());
        return GroupMember.builder()
                .pkId(dbImGroupMember.getId())
                .id(new MemberId(groupId, userId))
                .groupId(groupId)
                .userId(userId)
                .userAlias(StringUtils.isBlank(dbImGroupMember.getUserAlias()) ? null : new GroupUserAlias(dbImGroupMember.getUserAlias()))
                .joinTime(dbImGroupMember.getJoinTime())
                .build();
    }

    default GroupStatus imGroupStatusFrom(DbImGroupStatus status) {
        if (status == null || status == DbImGroupStatus.NONE) {
            throw new IllegalStateException("群状态不能为未知");
        }
        return switch (status) {
            case NORMAL -> GroupStatus.ACTIVE;
            case DISMISSED -> GroupStatus.DISMISSED;
            default -> throw new IllegalStateException("不支持的群状态：" + status);
        };
    }
}
