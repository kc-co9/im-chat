package com.kim.omgchat.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

@Data
@ApiModel("字典实体")
@TableName("dictionary")
public class DictionaryDO {
    @ApiModelProperty("主键ID")
    @TableField("id")
    private Long id;

    @ApiModelProperty("键")
    @TableField("key")
    private String key;

    @ApiModelProperty("值")
    @TableField("value")
    private String value;

    @ApiModelProperty("创建时间")
    @TableField("create_time")
    private Date createTime;

    @ApiModelProperty("最新更新时间")
    @TableField("last_update_time")
    private Date lastUpdateTime;
}
