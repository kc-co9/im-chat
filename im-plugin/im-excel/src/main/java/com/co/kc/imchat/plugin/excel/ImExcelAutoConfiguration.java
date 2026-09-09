package com.co.kc.imchat.plugin.excel;

import com.co.kc.imchat.plugin.excel.core.ExcelTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** 自动装配通用 Excel 导出基础能力。 */
@AutoConfiguration
public class ImExcelAutoConfiguration {

    /**
     * 创建 Excel 流式写出模板。
     *
     * @return Excel 模板
     */
    @Bean
    @ConditionalOnMissingBean
    public ExcelTemplate excelTemplate() {
        return new ExcelTemplate();
    }
}
