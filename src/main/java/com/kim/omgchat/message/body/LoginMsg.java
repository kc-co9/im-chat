package com.kim.omgchat.message.body;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kim.omgchat.enums.UserStatusEnum;
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
public class LoginMsg {
    private Long userId;
    private String nickname;
    private String status;

    @JsonIgnore
    public void setStatus(UserStatusEnum userStatusEnum) {
        this.status = userStatusEnum.getStatus();
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
