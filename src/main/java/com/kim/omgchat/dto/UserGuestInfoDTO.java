package com.kim.omgchat.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("用户客态信息")
public class UserGuestInfoDTO {
    @ApiModelProperty("邮件")
    private String email;

    @ApiModelProperty("头像")
    private String avatar;

    @ApiModelProperty("昵称")
    private String nickname;

    @ApiModelProperty("性别")
    private Integer gender;
}
