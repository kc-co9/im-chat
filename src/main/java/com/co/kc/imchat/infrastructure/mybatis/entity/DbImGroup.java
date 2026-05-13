package com.co.kc.imchat.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("db_im_group")
public class DbImGroup extends BaseEntity {
    private Long groupId;
    private Long ownerId;
    private String name;
    private String notification;
}
