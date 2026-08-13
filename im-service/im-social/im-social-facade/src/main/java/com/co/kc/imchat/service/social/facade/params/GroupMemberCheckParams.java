package com.co.kc.imchat.service.social.facade.params;

import java.io.Serializable;

public record GroupMemberCheckParams(Long groupId, Long userId) implements Serializable {
}
