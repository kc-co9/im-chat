package com.co.kc.imchat.plugin.excel.core;

import java.util.List;

/**
 * 单次 Excel 工作簿的流式写出会话。
 *
 * @param <T> 行数据类型
 */
public interface ExcelWriteSession<T> extends AutoCloseable {

    /**
     * 向当前工作表追加一批行数据。
     *
     * @param rows 行数据
     */
    void write(List<T> rows);

    /** 完成并关闭工作簿；不会关闭调用方提供的输出流。 */
    @Override
    void close();
}
