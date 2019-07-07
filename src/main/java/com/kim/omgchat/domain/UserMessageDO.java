package com.kim.omgchat.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kim.omgchat.enums.MessageStatusEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/5 14:18
 */
@Data
@ApiModel("用户消息模块")
@TableName("user_message")
public class UserMessageDO {
    @ApiModelProperty("用户主键ID")
    @TableField("id")
    private Long id;

    @ApiModelProperty("发送信息的用户ID")
    @TableField("from_uid")
    private Long fromUid;

    @ApiModelProperty("接收消息的用户ID")
    @TableField("to_uid")
    private Long toUid;

    @ApiModelProperty("消息的内容")
    @TableField("content")
    private String content;

    @ApiModelProperty("消息状态")
    @TableField("status")
    private Integer status;

    @ApiModelProperty("创建的时间")
    @TableField("create_time")
    private Date createTime;


    public void setStatus(Integer status){
        this.status = status;
    }

    @JsonIgnore
    public void setStatus(MessageStatusEnum messageStatusEnum) {
        this.status = messageStatusEnum.getValue();
    }
}
