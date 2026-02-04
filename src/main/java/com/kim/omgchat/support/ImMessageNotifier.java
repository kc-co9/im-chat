package com.kim.omgchat.support;

import com.kim.omgchat.model.cqrs.command.notify.ImGroupRevokedNotifyCmd;
import com.kim.omgchat.model.cqrs.command.notify.ImGroupSentNotifyCmd;
import com.kim.omgchat.model.cqrs.command.notify.ImPrivateSentNotifyCmd;
import com.kim.omgchat.model.cqrs.command.notify.ImPrivateReadNotifyCmd;
import com.kim.omgchat.model.cqrs.command.notify.ImPrivateRevokedNotifyCmd;

public interface ImMessageNotifier {

    /**
     * 给指定的单个用户发送私聊消息
     *
     * @param command 私聊消息通知命令
     */
    void notify(ImPrivateSentNotifyCmd command);

    /**
     * 给指定的单个用户发送私聊消息撤销通知
     *
     * @param command 私聊消息撤销通知命令
     */
    void notify(ImPrivateRevokedNotifyCmd command);

    /**
     * 给指定的单个用户发送私聊消息已读通知
     *
     * @param command 私聊消息已读通知命令
     */
    void notify(ImPrivateReadNotifyCmd command);

    /**
     * 给指定群组的所有成员发送群消息
     *
     * @param command 群聊消息通知命令
     */
    void notify(ImGroupSentNotifyCmd command);


    /**
     * 给指定的单个用户发送私聊消息撤销通知
     *
     * @param command 私聊消息撤销通知命令
     */
    void notify(ImGroupRevokedNotifyCmd command);
}
