package com.kim.omgchat.message.body;

import com.kim.omgchat.domain.UserMessageDO;
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
public class ChatMsg extends UserMessageDO {
    private Integer notReadCount;
}
