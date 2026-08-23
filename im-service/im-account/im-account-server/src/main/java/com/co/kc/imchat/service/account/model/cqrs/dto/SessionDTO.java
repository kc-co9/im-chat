package com.co.kc.imchat.service.account.model.cqrs.dto;

import com.co.kc.imchat.service.account.model.enums.SessionStatusEnum;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Data
public class SessionDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    
    private Long userId;
    private SessionStatusEnum status;
    private Instant signInTime;
    private Instant signOutTime;
    private String sessionVersion;
    private String refreshFingerprint;
    private Instant refreshTokenExpiresAt;
}
