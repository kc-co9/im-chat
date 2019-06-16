package com.kim.omgchat.message.body;

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

    public void setStatus(UserStatusEnum userStatusEnum) {
        this.status = userStatusEnum.getStatus();
    }
}
