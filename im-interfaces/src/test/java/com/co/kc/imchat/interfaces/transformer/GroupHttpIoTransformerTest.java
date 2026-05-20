package com.co.kc.imchat.interfaces.transformer;

import com.co.kc.imchat.application.model.cqrs.dto.group.GroupItemDTO;
import com.co.kc.imchat.interfaces.transformer.GroupHttpIoTransformer;
import com.co.kc.imchat.interfaces.model.io.group.GroupListResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GroupHttpIoTransformerTest {

    @Test
    void groupItemResponseContainsMemberCount() {
        GroupItemDTO dto = new GroupItemDTO();
        dto.setGroupId(1001L);
        dto.setMemberCount(3);

        GroupListResponse.GroupItem response = GroupHttpIoTransformer.INSTANCE.groupItemFrom(dto);

        assertThat(response.getMemberCount()).isEqualTo(3);
    }
}
