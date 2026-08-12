package com.co.kc.imchat.plugin.session.token;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenDTO {
    private Long userId;
    private LocalDateTime createTime;
}
