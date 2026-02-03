package com.kim.omgchat.model.cqrs.dto.im;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("IM 消息响应")
public class ImMessageDTO {

    @ApiModelProperty("会话ID")
    private String chatId;

    @ApiModelProperty("客户端消息ID")
    private String clientMsgId;

    @ApiModelProperty("服务器消息ID")
    private String serverMsgId;

    @ApiModelProperty("发送方ID")
    private Long senderId;

    @ApiModelProperty("接收方ID")
    private Long receiverId;

    @ApiModelProperty("发送方角色（1-用户，2-宠物，3-系统）")
    private Integer senderRole;

    @ApiModelProperty("发送方昵称")
    private String senderNickname;

    @ApiModelProperty("发送方头像")
    private String senderAvatar;

    @ApiModelProperty("消息类型（1-文本，2-图片，3-语音，4-视频，5-文件，6-链接，7-表情包，8-报备）")
    private Integer msgType;

    @ApiModelProperty("消息内容")
    private String msgContent;

    @ApiModelProperty("媒体URL（图片、语音、视频、文件）")
    private String mediaUrl;

    @ApiModelProperty("语音时长（秒）")
    private Integer audioDuration;

    @ApiModelProperty("表情包ID")
    private String stickerId;

    @ApiModelProperty("回复的消息ID")
    private String replyMsgId;

    @ApiModelProperty("是否紧急消息（0-否，1-是）")
    private Integer isUrgent;

    @ApiModelProperty("是否已接收（0-未接收，1-已接收）")
    private Integer isReceived;

    @ApiModelProperty("撤回状态（0-未撤回，1-用户自己撤回，2-违规撤回）")
    private Integer revokeStatus;

    @ApiModelProperty("发送时间")
    private Long sendTime;
}
