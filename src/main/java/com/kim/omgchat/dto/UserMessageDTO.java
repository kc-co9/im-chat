package com.kim.omgchat.dto;

import com.kim.omgchat.domain.UserMessageDO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("用户消息DTO")
public class UserMessageDTO extends UserMessageDO {
    @ApiModelProperty("发送方的昵称")
    private String fromUserNickname;
    @ApiModelProperty("发送方的头像")
    private String fromUserAvatar;

    @ApiModelProperty("到达方的昵称")
    private String toUserNickname;
    @ApiModelProperty("到达方的头像")
    private String toUserAvatar;

}
