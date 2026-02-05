package com.co.kc.imchat.model.cqrs.dto.user;

import com.co.kc.imchat.model.enums.SessionStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SessionDTO {
    private Long userId;
    private Long chatId;
    private SessionStatusEnum status;
    private LocalDateTime signInTime;
    private LocalDateTime signOutTime;
}
