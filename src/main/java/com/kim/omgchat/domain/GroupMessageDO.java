package com.kim.omgchat.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
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
 * @since 2019/6/5 15:09
 */
@Data
@ApiModel("群消息表")
@TableName("group_message")
public class GroupMessageDO {
    @ApiModelProperty("主键ID")
    @TableField("id")
    private Long id;

    @ApiModelProperty("发送的用户ID")
    @TableField("from_uid")
    private Long fromUid;

    @ApiModelProperty("群ID")
    @TableField("to_group_id")
    private Long toGroupId;

    @ApiModelProperty("消息内容")
    @TableField("content")
    private String content;

    @ApiModelProperty("创建时间")
    @TableField("create_time")
    private Date createTime;
}
