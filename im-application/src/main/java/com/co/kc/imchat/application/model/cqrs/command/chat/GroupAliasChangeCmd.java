package com.co.kc.imchat.application.model.cqrs.command.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GroupAliasChangeCmd {
    private Long userId;
    private Long chatId;
    private String groupAlias;
}
