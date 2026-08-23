package com.co.kc.imchat.gateway.ws.sdk.model.params;

import java.io.Serializable;

public record ConnectionCloseParams(Long userId, String sessionVersion) implements Serializable {
    public ConnectionCloseParams {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (sessionVersion == null || sessionVersion.isBlank()) {
            throw new IllegalArgumentException("sessionVersion must not be blank");
        }
    }
}
