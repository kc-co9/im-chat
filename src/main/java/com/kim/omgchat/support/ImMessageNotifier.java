package com.kim.omgchat.support;

import com.kim.omgchat.model.cqrs.dto.im.ImGroupMessageNotifyDTO;
import com.kim.omgchat.model.cqrs.dto.im.ImPrivateMessageNotifyDTO;
import com.kim.omgchat.model.cqrs.dto.im.ImPrivateMessageReadNotifyDTO;
import com.kim.omgchat.model.cqrs.dto.im.ImPrivateMessageRevokeNotifyDTO;

public interface ImMessageNotifier {

    /**
     * 给指定的单个用户发送私聊消息
     *
     * @param notifyDTO 私聊消息通知DTO
     */
    void notify(ImPrivateMessageNotifyDTO notifyDTO);

    /**
     * 给指定的单个用户发送私聊消息撤销通知
     *
     * @param notifyDTO 私聊消息撤销通知DTO
     */
    void notify(ImPrivateMessageRevokeNotifyDTO notifyDTO);

    /**
     * 给指定的单个用户发送私聊消息已读通知
     *
     * @param notifyDTO 私聊消息已读通知DTO
     */
    void notify(ImPrivateMessageReadNotifyDTO notifyDTO);

    /**
     * 给指定群组的所有成员发送群消息
     *
     * @param notifyDTO 群聊消息通知DTO
     */
    void notify(ImGroupMessageNotifyDTO notifyDTO);

}
