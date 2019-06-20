package com.kim.omgchat.dto;

import com.kim.omgchat.enums.MessageStatusEnum;
import lombok.Data;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/16 17:13
 */
@Data
public class UserMessageQueryDTO {
    private Long fromUserId;

    private Long toUserId;

    private Integer status;


    /**
     * 查询朋友对我最新消息列表
     */
    private Long userId;
    private Integer innerDay;



    public void setStatus(MessageStatusEnum messageStatusEnum) {
        this.status = messageStatusEnum.getValue();
    }
}
