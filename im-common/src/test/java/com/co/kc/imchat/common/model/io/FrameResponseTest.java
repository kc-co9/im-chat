package com.co.kc.imchat.common.model.io;

import com.co.kc.imchat.common.constant.FrameConstants;
import com.co.kc.imchat.common.constant.FrameErrorCode;
import com.co.kc.imchat.common.constant.FrameResultCode;
import com.co.kc.imchat.common.model.enums.FrameType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FrameResponseTest {

    @Test
    void okUsesSuccessResultCode() {
        FrameRequest request = new FrameRequest("1", "message.private.send", "1001", "trace-1", Map.of());

        FrameResponse response = FrameResponse.ok(request);

        assertThat(response.type()).isEqualTo(FrameType.RESPONSE);
        assertThat(response.version()).isEqualTo(request.version());
        assertThat(response.cmd()).isEqualTo(request.cmd());
        assertThat(response.seq()).isEqualTo(request.seq());
        assertThat(response.traceId()).isEqualTo(request.traceId());
        assertThat(response.code()).isEqualTo(FrameResultCode.SUCCESS.code());
        assertThat(response.message()).isEqualTo(FrameResultCode.SUCCESS.message());
        assertThat(response.body()).isEmpty();
    }

    @Test
    void errorUsesDefaultVersionAndErrorResultCodeWhenRequestIsNull() {
        FrameResponse response = FrameResponse.error(null, FrameErrorCode.BAD_FRAME.code(), "非法消息帧");

        assertThat(response.type()).isEqualTo(FrameType.ERROR);
        assertThat(response.version()).isEqualTo(FrameConstants.DEFAULT_VERSION);
        assertThat(response.cmd()).isNull();
        assertThat(response.seq()).isNull();
        assertThat(response.traceId()).isNull();
        assertThat(response.code()).isEqualTo(FrameResultCode.ERROR.code());
        assertThat(response.message()).isEqualTo("非法消息帧");
        assertThat(response.body()).containsEntry(FrameConstants.ERROR_CODE_KEY, FrameErrorCode.BAD_FRAME.code());
    }
}
