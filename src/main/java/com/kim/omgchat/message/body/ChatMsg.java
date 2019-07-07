package com.kim.omgchat.message.body;

import com.kim.omgchat.domain.UserMessageDO;
import com.kim.omgchat.dto.UserMessageReceiveDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/16 16:33
 */
@Data
@ApiModel("聊天消息实体")
public class ChatMsg extends UserMessageDO {

    @ApiModelProperty("发送方的昵称")
    private String fromUserNickname;
    @ApiModelProperty("发送方用户头像")
    private String fromUserAvatar;

    @ApiModelProperty("接受方昵称")
    private String toUserNickname;
    @ApiModelProperty("接受方用户头像")
    private String toUserAvatar;

    @ApiModelProperty("未读数量")
    private Integer notReadCount;


}
