package com.kim.omgchat.message;

import com.kim.omgchat.enums.MessageTypeEnum;
import lombok.Data;

/**
 * <p>
 * 消息
 * </p>
 *
 * @author kim
 * @since 2019/6/16 16:23
 */
@Data
public class Message<T> {
    /**
     * 消息类型
     **/
    private Integer type;

    /**
     * 消息主体
     */
    private T msgBody;

    public void setType(MessageTypeEnum messageTypeEnum) {
        this.type = messageTypeEnum.getType();
    }
}
