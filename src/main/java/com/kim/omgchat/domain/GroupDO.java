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
@ApiModel("群组表")
@TableName("char_group")
public class GroupDO {
    @ApiModelProperty("主键ID")
    @TableField("id")
    private Long id;

    @ApiModelProperty("群名")
    @TableField("name")
    private String name;

    @ApiModelProperty("成员数量")
    @TableField("group_number")
    private Integer groupNumber;

    @ApiModelProperty("群介绍")
    @TableField("introduction")
    private String introduction;

    @ApiModelProperty("群公告")
    @TableField("notification")
    private String notification;

    @ApiModelProperty("创建时间")
    @TableField("create_time")
    private Date createTime;
}
