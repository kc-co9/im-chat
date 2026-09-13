package com.co.kc.imchat.plugin.datasource.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityTest {

    @Test
    void declaresOptimisticLockVersionColumn() throws NoSuchFieldException {
        Field field = BaseEntity.class.getDeclaredField("version");

        assertThat(field.getType()).isEqualTo(Long.class);
        assertThat(field.getAnnotation(Version.class)).isNotNull();
        assertThat(field.getAnnotation(TableField.class).value()).isEqualTo("version");
    }

    @Test
    void keepsDatabaseGeneratedTechnicalPrimaryKey() throws NoSuchFieldException {
        TableId tableId = BaseEntity.class.getDeclaredField("id").getAnnotation(TableId.class);

        assertThat(tableId.type()).isEqualTo(IdType.AUTO);
    }

}
