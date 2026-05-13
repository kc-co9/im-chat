package com.co.kc.imchat.domain.group;

import com.co.kc.imchat.domain.shared.Identification;
import com.co.kc.imchat.domain.shared.Validator;
import com.co.kc.imchat.domain.user.UserId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * IM群成员
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ImGroupMember extends Identification implements Validator {
    private ImGroupMemberId id;
    private UserId userId;
    private ImGroupId groupId;
    private ImGroupAlias groupAlias;
    private ImGroupUserAlias userAlias;
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ImGroupMember member = new ImGroupMember();

        public Builder pkId(Long pkId) {
            if (pkId != null) {
                member.setPkId(pkId);
            }
            return this;
        }

        public Builder id(ImGroupMemberId id) {
            member.setId(id);
            return this;
        }

        public Builder userId(UserId userId) {
            member.setUserId(userId);
            return this;
        }

        public Builder groupId(ImGroupId groupId) {
            member.setGroupId(groupId);
            return this;
        }

        public Builder groupAlias(ImGroupAlias groupAlias) {
            member.setGroupAlias(groupAlias);
            return this;
        }

        public Builder userAlias(ImGroupUserAlias userAlias) {
            member.setUserAlias(userAlias);
            return this;
        }

        public Builder joinTime(LocalDateTime joinTime) {
            member.setJoinTime(joinTime);
            return this;
        }

        public ImGroupMember build() {
            member.validate();
            return member;
        }
    }
}
