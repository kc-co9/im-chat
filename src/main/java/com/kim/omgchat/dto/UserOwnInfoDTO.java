package com.kim.omgchat.dto;

import com.kim.omgchat.domain.UserDO;
import io.swagger.annotations.ApiModel;
import lombok.Data;

@Data
@ApiModel("用户主态信息")
public class UserOwnInfoDTO extends UserDO {
}
