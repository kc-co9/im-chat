package com.co.kc.imchat.model.io.chat;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ImPrivateChatEnterRequest {
    @ApiModelProperty("接收者")
    @NotNull(message = "接收者不能为空")
    private Long receiverId;
}
