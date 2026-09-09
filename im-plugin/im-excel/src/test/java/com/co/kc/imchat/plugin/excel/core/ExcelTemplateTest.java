package com.co.kc.imchat.plugin.excel.core;

import com.co.kc.imchat.plugin.excel.convert.EnumNameConverter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.fesod.sheet.FastExcel;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelTemplateTest {

    @Test
    void writesMultipleBatchesAndEnumNames() {
        TrackingOutputStream output = new TrackingOutputStream();
        ExcelTemplate template = new ExcelTemplate();

        try (ExcelWriteSession<ExampleRow> session =
                     template.open(output, ExampleRow.class, "示例数据")) {
            session.write(List.of(new ExampleRow("first", ExampleStatus.ACTIVE)));
            session.write(List.of(new ExampleRow("second", ExampleStatus.DISABLED)));
        }

        List<Map<Integer, String>> rows = readRows(output);
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).values()).containsExactly("名称", "状态");
        assertThat(rows.get(1).values()).containsExactly("first", "ACTIVE");
        assertThat(rows.get(2).values()).containsExactly("second", "DISABLED");
        assertThat(output.closed).isFalse();
    }

    @Test
    void writesHeadersWhenSessionHasNoRows() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelTemplate template = new ExcelTemplate();

        try (ExcelWriteSession<ExampleRow> ignored =
                     template.open(output, ExampleRow.class, "示例数据")) {
            // Closing an unused session must still create a readable workbook with headers.
        }

        assertThat(readRows(output)).singleElement()
                .satisfies(row -> assertThat(row.values()).containsExactly("名称", "状态"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<Integer, String>> readRows(ByteArrayOutputStream output) {
        return (List<Map<Integer, String>>) (List<?>) FastExcel
                .read(new ByteArrayInputStream(output.toByteArray()))
                .headRowNumber(0)
                .doReadAllSync();
    }

    private enum ExampleStatus {
        ACTIVE,
        DISABLED
    }

    @Getter
    @RequiredArgsConstructor
    private static final class ExampleRow {
        @ExcelProperty(value = "名称", index = 0)
        private final String name;
        @ExcelProperty(value = "状态", index = 1, converter = EnumNameConverter.class)
        private final ExampleStatus status;
    }

    private static final class TrackingOutputStream extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
