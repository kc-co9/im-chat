package com.kim.omgchat.component;

import com.kim.omgchat.domain.UserDO;
import com.kim.omgchat.utils.EncodeUtil;
import com.kim.omgchat.vo.user.UserLoginVO;
import org.springframework.stereotype.Component;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/7 9:35
 */
@Component
public class UserComponent {

    /**
     * 生成token
     */
    public String generateToken(UserDO userDO) {
        StringBuilder sb = new StringBuilder();
        sb.append(userDO.getId());
        sb.append(userDO.getEmail());
        sb.append(System.currentTimeMillis());

        return EncodeUtil.encoderByMd5(sb.toString());
    }

    /**
     * 加密密码
     */
    public String encryptPassword(String password) {
        return EncodeUtil.encoderByMd5(insertSalt(password));
    }

    /**
     * 校验密码
     */
    public boolean judgePassword(String password, String input) {
        return encryptPassword(input).equals(password);
    }

    /**
     * 插入盐
     */
    private String insertSalt(String password) {
        return "OMG" + password + "CHAT";
    }

}
