package com.co.kc.imchat.domain.message;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImMessageContent {
    private ImMessageType type;
    private String value;
}
