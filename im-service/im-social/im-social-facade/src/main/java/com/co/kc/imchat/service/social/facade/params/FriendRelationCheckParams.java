package com.co.kc.imchat.service.social.facade.params;

import java.io.Serializable;

public record FriendRelationCheckParams(Long userId, Long friendUserId) implements Serializable {
}
