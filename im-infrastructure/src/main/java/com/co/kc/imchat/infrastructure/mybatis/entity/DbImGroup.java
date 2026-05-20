package com.co.kc.imchat.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImGroupStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("db_im_group")
public class DbImGroup extends BaseEntity {
    /**
     * 群ID。
     */
    private Long groupId;
    /**
     * 群主ID。
     */
    private Long ownerId;
    /**
     * 群名称。
     */
    private String name;
    /**
     * 群公告。
     */
    private String notification;
    /**
     * 群人数。
     */
    private Integer memberCount;
    /**
     * 群状态：0-未知，1-正常，2-已解散。
     */
    private DbImGroupStatus status;
}
