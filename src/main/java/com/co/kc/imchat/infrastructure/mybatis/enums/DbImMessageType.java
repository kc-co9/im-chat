package com.co.kc.imchat.infrastructure.mybatis.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DbImMessageType {
    /**
     * 0-未知
     */
    NONE(0),
    /**
     * 1-文本消息
     */
    TEXT(1),
    /**
     * 2-图片消息
     */
    IMAGE(2),
    /**
     * 3-语音消息
     */
    AUDIO(3),
    /**
     * 4-视频消息
     */
    VIDEO(4),
    /**
     * 5-文件消息
     */
    FILE(5),
    /**
     * 6-表情包消息
     */
    STICKER(6),
    /**
     * 7-系统消息
     */
    SYSTEM(7);

    @EnumValue
    private final int code;
}
