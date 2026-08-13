package com.co.kc.imchat.service.social.facade.dto;

import java.io.Serializable;

public record GroupMemberItemDTO(Long userId, String alias) implements Serializable {
}
