package com.co.kc.imchat.gateway.ws.protocol;

import com.co.kc.imchat.common.model.io.FrameRequest;
import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.common.model.enums.FrameType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonFrameCodecTest {

    @Test
    void decodesRequestFrame() {
        FrameRequest frame = JsonFrameCodec.decodeRequest("""
                {"version":"1","type":"request","cmd":"message.private.send","seq":"10001","traceId":"abc","body":{}}
                """);

        assertEquals("1", frame.version());
        assertEquals("message.private.send", frame.cmd());
        assertEquals("10001", frame.seq());
        assertEquals("abc", frame.traceId());
    }

    @Test
    void encodesResponseFrame() {
        FrameResponse frame = FrameResponse.ok(new FrameRequest("1", "message.private.send", "10001", "abc", Map.of()));

        String payload = JsonFrameCodec.encodeResponse(frame);

        assertTrue(payload.contains("\"type\":\"response\""));
        assertEquals("message.private.send", JsonFrameCodec.decodeResponse(payload).cmd());
        assertEquals(FrameType.RESPONSE, JsonFrameCodec.decodeResponse(payload).type());
    }
}
