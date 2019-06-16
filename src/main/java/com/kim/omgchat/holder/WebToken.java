package com.kim.omgchat.holder;

import com.kim.omgchat.utils.EncodeUtil;
import lombok.Data;

import java.util.Date;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/13 15:48
 */
@Data
public class WebToken {

    private Long userId;

    private String email;

    private Date createTime = new Date();

    public WebToken() {
    }

    public WebToken(Long userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    /**
     * 生成TOKEN
     */
    public String generate() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.userId);
        sb.append(this.email);
        sb.append(this.createTime);

        return EncodeUtil.encoderByMd5(sb.toString());
    }
}
