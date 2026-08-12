package com.co.kc.imchat.service.social.domain.group.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.shared.model.Identification;
import com.co.kc.imchat.common.domain.shared.model.Validator;
import com.co.kc.imchat.common.exception.BusinessException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 聚合根：群组。
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class Group extends Identification implements Validator, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private GroupId id;
    private UserId ownerId;
    private GroupName name;
    private GroupNotification notification;
    private MemberCount memberCount;
    private GroupStatus status;

    public void dismiss(UserId operatorId) {
        ensureActive();
        ensureOwner(operatorId);
        if (status == GroupStatus.DISMISSED) {
            throw new BusinessException("群聊已解散");
        }
        status = GroupStatus.DISMISSED;
    }

    public void transferOwner(UserId operatorId, UserId newOwnerId) {
        ensureActive();
        if (!ownerId.equals(operatorId)) {
            throw new BusinessException("只有群主才能转让群主");
        }
        if (ownerId.equals(newOwnerId)) {
            throw new BusinessException("新群主不能是当前群主");
        }
        ownerId = newOwnerId;
    }

    public void memberLeave(UserId userId) {
        ensureActive();
        ensureCanLeave(userId);
        decreaseMemberCount(1);
    }

    public void kickMember(UserId operatorId, UserId memberUserId) {
        ensureActive();
        ensureCanKick(operatorId, memberUserId);
        decreaseMemberCount(1);
    }

    public void decreaseMemberCount(int count) {
        changeMemberCount(new MemberCount(memberCount.value() - count));
    }

    public void changeNotification(UserId operatorId, GroupNotification notification) {
        ensureActive();
        ensureOwner(operatorId);
        this.notification = notification;
    }

    public boolean isDismissed() {
        return status == GroupStatus.DISMISSED;
    }

    public void ensureActive() {
        if (isDismissed()) {
            throw new BusinessException("群聊已解散");
        }
    }

    public void ensureOwner(UserId operatorId) {
        if (!ownerId.equals(operatorId)) {
            throw new BusinessException("只有群主可以操作");
        }
    }

    public void ensureCanLeave(UserId userId) {
        if (ownerId.equals(userId)) {
            throw new BusinessException("群主不能直接退群，请先转让群主");
        }
    }

    public void ensureCanKick(UserId operatorId, UserId memberId) {
        ensureOwner(operatorId);
        if (memberId.equals(operatorId)) {
            throw new BusinessException("不能踢出群主");
        }
    }


    public void changeMemberCount(MemberCount memberCount) {
        AssertUtils.domainPropNotNull("群人数不能为空", memberCount);
        this.memberCount = memberCount;
    }

    @Override
    public void validate() {
        if (id == null || ownerId == null || name == null || memberCount == null || status == null) {
            throw new IllegalStateException("群缺少 id、ownerId、name、memberCount 或 status");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Group group = new Group();

        public Builder id(GroupId id) {
            group.id = id;
            return this;
        }

        public Builder ownerId(UserId ownerId) {
            group.ownerId = ownerId;
            return this;
        }

        public Builder name(GroupName name) {
            group.name = name;
            return this;
        }

        public Builder notification(GroupNotification notification) {
            group.notification = notification;
            return this;
        }

        public Builder memberCount(MemberCount memberCount) {
            group.memberCount = memberCount;
            return this;
        }

        public Builder status(GroupStatus status) {
            group.status = status;
            return this;
        }

        public Group build() {
            if (group.getStatus() == null) {
                group.status = GroupStatus.ACTIVE;
            }
            if (group.getMemberCount() == null) {
                group.memberCount = new MemberCount(1);
            }
            group.validate();
            return group;
        }
    }
}
