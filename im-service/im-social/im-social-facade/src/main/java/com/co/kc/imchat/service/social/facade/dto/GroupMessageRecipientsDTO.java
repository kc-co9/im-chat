package com.co.kc.imchat.service.social.facade.dto;

import java.io.Serializable;
import java.util.List;

public record GroupMessageRecipientsDTO(Long groupId, List<GroupMessageRecipientDTO> recipients) implements Serializable {
}
