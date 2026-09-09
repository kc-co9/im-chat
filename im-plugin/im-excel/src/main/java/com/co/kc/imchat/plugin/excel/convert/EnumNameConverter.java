package com.co.kc.imchat.plugin.excel.convert;

import org.apache.fesod.sheet.converters.Converter;
import org.apache.fesod.sheet.metadata.GlobalConfiguration;
import org.apache.fesod.sheet.metadata.data.WriteCellData;
import org.apache.fesod.sheet.metadata.property.ExcelContentProperty;

/** 将枚举按名称写入 Excel 文本单元格。 */
public class EnumNameConverter implements Converter<Enum<?>> {

    @Override
    public Class<?> supportJavaTypeKey() {
        return Enum.class;
    }

    @Override
    public WriteCellData<?> convertToExcelData(
            Enum<?> value,
            ExcelContentProperty contentProperty,
            GlobalConfiguration globalConfiguration
    ) {
        return new WriteCellData<>(value.name());
    }
}
