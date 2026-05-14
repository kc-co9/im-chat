package com.co.kc.imchat.domain.group;

import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.domain.shared.Identification;
import com.co.kc.imchat.domain.shared.Validator;
import com.co.kc.imchat.support.exception.BusinessException;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Group extends Identification implements Validator {
    private GroupId id;
    private ImChatType type;
    private UserId ownerId;
    private GroupName name;
    private GroupNotification notification;
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

    @Override
    public void validate() {
        if (id == null || type == null || ownerId == null || name == null || status == null) {
            throw new IllegalStateException("群缺少 id、type、ownerId、name 或 status");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Group group = new Group();

        public Builder id(GroupId id) {
            group.setId(id);
            return this;
        }

        public Builder type(ImChatType type) {
            group.setType(type);
            return this;
        }

        public Builder ownerId(UserId ownerId) {
            group.setOwnerId(ownerId);
            return this;
        }

        public Builder name(GroupName name) {
            group.setName(name);
            return this;
        }

        public Builder notification(GroupNotification notification) {
            group.setNotification(notification);
            return this;
        }

        public Builder status(GroupStatus status) {
            group.setStatus(status);
            return this;
        }

        public Group build() {
            if (group.getStatus() == null) {
                group.setStatus(GroupStatus.NORMAL);
            }
            group.validate();
            return group;
        }
    }
}
