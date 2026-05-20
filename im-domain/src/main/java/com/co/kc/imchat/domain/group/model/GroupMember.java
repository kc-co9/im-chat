package com.co.kc.imchat.domain.group.model;

import com.co.kc.imchat.domain.shared.model.Identification;
import com.co.kc.imchat.domain.shared.model.Validator;
import com.co.kc.imchat.domain.user.model.UserId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * IM群成员
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class GroupMember extends Identification implements Validator {
    private MemberId id;
    private UserId userId;
    private GroupId groupId;
    private GroupAlias groupAlias;
    private GroupUserAlias userAlias;
    private LocalDateTime joinTime;

    @Override
    public void validate() {
        if (id == null || userId == null || groupId == null || joinTime == null) {
            throw new IllegalStateException("群成员缺少 id、userId、groupId 或 joinTime");
        }
    }

    public boolean isMember(UserId userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    public void changeUserAlias(GroupUserAlias alias) {
        userAlias = alias;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final GroupMember member = new GroupMember();

        public Builder pkId(Long pkId) {
            if (pkId != null) {
                member.setPkId(pkId);
            }
            return this;
        }

        public Builder id(MemberId id) {
            member.setId(id);
            return this;
        }

        public Builder userId(UserId userId) {
            member.setUserId(userId);
            return this;
        }

        public Builder groupId(GroupId groupId) {
            member.setGroupId(groupId);
            return this;
        }

        public Builder groupAlias(GroupAlias groupAlias) {
            member.setGroupAlias(groupAlias);
            return this;
        }

        public Builder userAlias(GroupUserAlias userAlias) {
            member.setUserAlias(userAlias);
            return this;
        }

        public Builder joinTime(LocalDateTime joinTime) {
            member.setJoinTime(joinTime);
            return this;
        }

        public GroupMember build() {
            member.validate();
            return member;
        }
    }
}
