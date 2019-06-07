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
 * @since 2019/6/5 15:42
 */
@Data
@ApiModel("群用户关系表")
@TableName("group_user_relationship")
public class GroupUserRelationshipDO {
    @ApiModelProperty("主键ID")
    @TableField("id")
    private Long id;

    @ApiModelProperty("组ID")
    @TableField("group_id")
    private Long groupId;

    @ApiModelProperty("用户ID")
    @TableField("user_id")
    private Long userId;

    @ApiModelProperty("创建时间")
    @TableField("create_time")
    private Date createTime;
}
