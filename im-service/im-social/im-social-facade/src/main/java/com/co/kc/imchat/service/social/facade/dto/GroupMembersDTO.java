package com.co.kc.imchat.service.social.facade.dto;

import java.io.Serializable;
import java.util.List;

public record GroupMembersDTO(Long groupId, List<GroupMemberItemDTO> members) implements Serializable {
}
