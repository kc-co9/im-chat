package com.co.kc.imchat.service.social.transformer.domain;

import com.co.kc.imchat.service.social.domain.friend.model.Friend;
import com.co.kc.imchat.service.social.infrastructure.mybatis.entity.DbFriend;
import com.co.kc.imchat.service.social.infrastructure.mybatis.enums.DbFriendStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FriendDomainTransformerTest {

    @Test
    void normalizesEmptyListsAndRestoresPersistenceMetadata() {
        assertThat(FriendDomainTransformer.INSTANCE.friendListFrom(null)).isEmpty();
        assertThat(FriendDomainTransformer.INSTANCE.friendListFrom(List.of())).isEmpty();

        DbFriend row = new DbFriend();
        row.setId(7L);
        row.setVersion(3L);
        row.setUserId(10L);
        row.setFriendUserId(20L);
        row.setFriendStatus(DbFriendStatus.NORMAL);

        Friend friend = FriendDomainTransformer.INSTANCE.friendListFrom(List.of(row)).getFirst();

        assertThat(friend.getPkId()).isEqualTo(7L);
        assertThat(friend.getRowVersion()).isEqualTo(3L);
    }
}
