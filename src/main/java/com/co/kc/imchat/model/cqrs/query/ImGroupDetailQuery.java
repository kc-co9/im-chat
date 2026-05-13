package com.co.kc.imchat.model.cqrs.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImGroupDetailQuery {
    private Long userId;
    private Long groupId;
}
