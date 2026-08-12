package com.co.kc.imchat.service.account.model.cqrs.dto;

import com.co.kc.imchat.service.account.model.enums.SessionStatusEnum;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SessionDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    
    private Long userId;
    private Long chatId;
    private SessionStatusEnum status;
    private LocalDateTime signInTime;
    private LocalDateTime signOutTime;
}
