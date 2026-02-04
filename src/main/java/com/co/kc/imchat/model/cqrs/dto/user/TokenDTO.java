package com.co.kc.imchat.model.cqrs.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author kc
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenDTO {
    private Long userId;
    private LocalDateTime createTime;
}
