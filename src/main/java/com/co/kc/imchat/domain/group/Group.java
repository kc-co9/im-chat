package com.co.kc.imchat.domain.group;

import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.domain.shared.Identification;
import com.co.kc.imchat.domain.shared.Validator;
import com.co.kc.imchat.support.exception.BusinessException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class Group extends Identification implements Validator {
    private GroupId id;
    private ImChatType type;
    private UserId ownerId;
    private GroupName name;
    private GroupNotification notification;
    private MemberCount memberCount;
    private GroupStatus status;

    public void dismiss(UserId operatorId) {
        if (!ownerId.equals(operatorId)) {
            throw new BusinessException("只有群主可以解散群聊");
        }
        if (status == GroupStatus.DISMISSED) {
            throw new BusinessException("群聊已解散");
        }
        status = GroupStatus.DISMISSED;
    }

    public boolean isDismissed() {
        return status == GroupStatus.DISMISSED;
    }

    public void ensureActive() {
        if (isDismissed()) {
            throw new BusinessException("群聊已解散");
        }
    }

    public void changeMemberCount(MemberCount memberCount) {
        if (memberCount == null) {
            throw new IllegalArgumentException("群人数不能为空");
        }
        this.memberCount = memberCount;
    }

    @Override
    public void validate() {
        if (id == null || type == null || ownerId == null || name == null || memberCount == null || status == null) {
            throw new IllegalStateException("群缺少 id、type、ownerId、name、memberCount 或 status");
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

        public Builder type(ImChatType type) {
            group.type = type;
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
                group.status = GroupStatus.NORMAL;
            }
            if (group.getMemberCount() == null) {
                group.memberCount = new MemberCount(1);
            }
            group.validate();
            return group;
        }
    }
}
