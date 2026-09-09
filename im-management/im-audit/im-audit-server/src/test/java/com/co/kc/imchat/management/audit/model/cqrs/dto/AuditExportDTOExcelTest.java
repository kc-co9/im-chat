package com.co.kc.imchat.management.audit.model.cqrs.dto;

import com.co.kc.imchat.management.audit.domain.model.AuditOutcome;
import com.co.kc.imchat.management.audit.domain.model.AuditType;
import com.co.kc.imchat.plugin.excel.core.ExcelTemplate;
import com.co.kc.imchat.plugin.excel.core.ExcelWriteSession;
import org.apache.fesod.sheet.FastExcel;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditExportDTOExcelTest {

    @Test
    void writesAuditHeadersAndTextSafeRowsWithoutInternalFields() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AuditExportDTO dto = new AuditExportDTO(
                "audit-1", "imAdmin", AuditType.BUSINESS, "USER_UPDATE", AuditOutcome.SUCCESS,
                "ADMIN", "1001", "'=formula", "USER", "2001", null,
                "updated", "127.0.0.1", "agent", "trace-1", "key=value",
                "2026-08-01T01:00:00Z");

        try (ExcelWriteSession<AuditExportDTO> session = new ExcelTemplate()
                .open(output, AuditExportDTO.class, "审计记录")) {
            session.write(List.of(dto));
        }

        List<Map<Integer, String>> rows = readRows(output);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).values())
                .contains("审计标识", "来源应用", "发生时间")
                .doesNotContain("pkId", "updateTime", "isDeleted");
        assertThat(rows.get(1).get(2)).isEqualTo("BUSINESS");
        assertThat(rows.get(1).get(4)).isEqualTo("SUCCESS");
        assertThat(rows.get(1).get(7)).isEqualTo("'=formula");
    }

    @SuppressWarnings("unchecked")
    private List<Map<Integer, String>> readRows(ByteArrayOutputStream output) {
        return (List<Map<Integer, String>>) (List<?>) FastExcel
                .read(new ByteArrayInputStream(output.toByteArray()))
                .headRowNumber(0)
                .doReadAllSync();
    }
}
