package com.co.kc.imchat.service.social.transformer;

import com.co.kc.imchat.service.social.model.cqrs.dto.friend.FriendItemDTO;
import com.co.kc.imchat.service.social.model.io.friend.FriendListResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FriendHttpIoTransformerTest {

    @Test
    void convertsAbsoluteTimeToEpochMilliseconds() {
        FriendItemDTO friend = new FriendItemDTO();
        friend.setUserId(1001L);
        friend.setDisplayName("好友");
        friend.setCreateTime(Instant.parse("2026-09-07T08:30:00.123Z"));

        FriendListResponse.FriendItem response =
                FriendHttpIoTransformer.INSTANCE.friendItemFrom(friend);

        assertThat(response.getCreateTime()).isEqualTo(1788769800123L);
    }
}
