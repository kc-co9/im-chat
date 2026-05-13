package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.domain.shared.Identification;
import com.co.kc.imchat.domain.shared.Validator;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImGroup extends Identification implements Validator {
    private ImGroupId id;
    private ImChatType type;
    private UserId ownerId;
    private ImGroupName name;
    private ImGroupNotification notification;

    @Override
    public void validate() {
        if (id == null || type == null || ownerId == null || name == null) {
            throw new IllegalStateException("群缺少 id、type、ownerId 或 name");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ImGroup group = new ImGroup();

        public Builder id(ImGroupId id) {
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

        public Builder name(ImGroupName name) {
            group.setName(name);
            return this;
        }

        public Builder notification(ImGroupNotification notification) {
            group.setNotification(notification);
            return this;
        }

        public ImGroup build() {
            group.validate();
            return group;
        }
    }
}
