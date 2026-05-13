package com.co.kc.imchat.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("db_im_group_member")
public class DbImGroupMember extends BaseEntity {
    private Long groupId;
    private Long userId;
    private String userAlias;
    private LocalDateTime joinTime;
}
