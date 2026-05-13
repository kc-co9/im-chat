package com.co.kc.imchat.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("db_im_group_chat")
public class DbImGroupChat extends BaseEntity {
    private Long chatId;
    private Long groupId;
    private Long userId;
    private String groupAlias;
    private Long lastMessageId;
    private Long readMessageId;
    private Integer unreadMessageCount;
}
