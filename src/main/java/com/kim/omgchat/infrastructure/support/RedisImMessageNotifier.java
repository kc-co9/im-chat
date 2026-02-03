package com.kim.omgchat.infrastructure.support;

import com.kim.omgchat.model.cqrs.dto.im.ImGroupMessageNotifyDTO;
import com.kim.omgchat.model.cqrs.dto.im.ImPrivateMessageNotifyDTO;
import com.kim.omgchat.model.cqrs.dto.im.ImPrivateMessageReadNotifyDTO;
import com.kim.omgchat.model.cqrs.dto.im.ImPrivateMessageRevokeNotifyDTO;
import com.kim.omgchat.support.ImMessageNotifier;

public class RedisImMessageNotifier implements ImMessageNotifier {

    @Override
    public void notify(ImPrivateMessageNotifyDTO notifyDTO) {

    }

    @Override
    public void notify(ImPrivateMessageRevokeNotifyDTO notifyDTO) {

    }

    @Override
    public void notify(ImPrivateMessageReadNotifyDTO notifyDTO) {

    }

    @Override
    public void notify(ImGroupMessageNotifyDTO notifyDTO) {

    }
}
