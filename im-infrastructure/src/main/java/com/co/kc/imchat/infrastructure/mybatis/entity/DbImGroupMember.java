package com.co.kc.imchat.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("db_im_group_member")
public class DbImGroupMember extends BaseEntity {
    /**
     * 群ID。
     */
    private Long groupId;
    /**
     * 成员用户ID。
     */
    private Long userId;
    /**
     * 用户群昵称。
     */
    private String userAlias;
    /**
     * 入群时间。
     */
    private LocalDateTime joinTime;
}
