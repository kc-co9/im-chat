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
 * @since 2019/6/5 15:16
 */
@Data
@ApiModel("用户关系表")
@TableName("user_relationship")
public class UserRelationshipDO {
    @ApiModelProperty("主键ID")
    @TableField("id")
    private Long id;

    @ApiModelProperty("用户1 ID")
    @TableField("user_fir_id")
    private Long userFirId;

    @ApiModelProperty("用户2 ID")
    @TableField("user_sec_id")
    private Long userSecId;

    @ApiModelProperty("用户1 分组")
    @TableField("user_fir_group")
    private String userFirGroup;

    @ApiModelProperty("用户2 分组")
    @TableField("user_sec_group")
    private String userSecGroup;

    @ApiModelProperty("创建时间")
    @TableField("create_time")
    private Date createTime;
}
