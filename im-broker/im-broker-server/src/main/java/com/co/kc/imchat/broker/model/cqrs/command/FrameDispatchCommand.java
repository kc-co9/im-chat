package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.common.model.io.FrameResponse;

/** 向目标用户当前在线连接投递下行帧的应用命令。 */
public record FrameDispatchCommand(Long userId,
                                   FrameResponse frame,
                                   String deliveryId,
                                   Long routingEpoch) {
}
