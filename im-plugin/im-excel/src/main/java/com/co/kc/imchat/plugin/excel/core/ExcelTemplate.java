package com.co.kc.imchat.plugin.excel.core;

import com.co.kc.imchat.common.utils.AssertUtils;
import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FastExcel;
import org.apache.fesod.sheet.write.metadata.WriteSheet;

import java.io.OutputStream;
import java.util.List;

/** Apache Fesod Excel 流式写出模板。 */
public class ExcelTemplate {

    /**
     * 创建一个单工作表流式写出会话。
     *
     * @param outputStream 输出流，生命周期由调用方管理
     * @param rowType 行数据类型
     * @param sheetName 工作表名称
     * @param <T> 行数据类型
     * @return 写出会话
     */
    public <T> ExcelWriteSession<T> open(
            OutputStream outputStream,
            Class<T> rowType,
            String sheetName
    ) {
        AssertUtils.argNotNull("excel output stream must not be null", outputStream);
        AssertUtils.argNotNull("excel row type must not be null", rowType);
        AssertUtils.argNotBlank("excel sheet name must not be blank", sheetName);

        ExcelWriter writer = FastExcel.write(outputStream, rowType)
                .autoCloseStream(false)
                .inMemory(false)
                .build();
        WriteSheet sheet = FastExcel.writerSheet(sheetName).build();
        return new FesodExcelWriteSession<>(writer, sheet);
    }

    private static final class FesodExcelWriteSession<T> implements ExcelWriteSession<T> {
        private final ExcelWriter writer;
        private final WriteSheet sheet;
        private boolean written;

        private FesodExcelWriteSession(ExcelWriter writer, WriteSheet sheet) {
            this.writer = writer;
            this.sheet = sheet;
        }

        @Override
        public void write(List<T> rows) {
            AssertUtils.argNotNull("excel rows must not be null", rows);
            writer.write(rows, sheet);
            written = true;
        }

        @Override
        public void close() {
            if (!written) {
                writer.write(List.of(), sheet);
            }
            writer.finish();
        }
    }
}
