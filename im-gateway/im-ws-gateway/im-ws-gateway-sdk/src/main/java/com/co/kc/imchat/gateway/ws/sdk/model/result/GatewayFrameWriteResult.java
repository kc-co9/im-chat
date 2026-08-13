package com.co.kc.imchat.gateway.ws.sdk.model.result;

import java.io.Serializable;
import com.co.kc.imchat.gateway.ws.sdk.model.dto.GatewayFrameWriteDTO;
import com.co.kc.imchat.gateway.ws.sdk.enums.FrameWriteStatus;

import java.util.List;
import java.util.ArrayList;

/**
 * 网关实时帧写入结果。
 *
 * @param writeList 每个连接的写入处理结果
 */
public record GatewayFrameWriteResult(List<GatewayFrameWriteDTO> writeList) implements Serializable {
    public GatewayFrameWriteResult {
        writeList = writeList == null ? List.of() : List.copyOf(writeList);
    }

    public GatewayFrameWriteResult(List<String> acceptedConnectionIds, List<String> failedConnectionIds) {
        this(toResults(acceptedConnectionIds, failedConnectionIds));
    }

    public List<String> acceptedConnectionIds() {
        return connectionIdsOf(FrameWriteStatus.ACCEPTED);
    }

    public List<String> failedConnectionIds() {
        return connectionIdsOf(FrameWriteStatus.FAILED);
    }

    private List<String> connectionIdsOf(FrameWriteStatus status) {
        return writeList.stream()
                .filter(result -> result.status() == status)
                .map(GatewayFrameWriteDTO::connectionId)
                .toList();
    }

    private static List<GatewayFrameWriteDTO> toResults(List<String> acceptedConnectionIds,
                                                        List<String> failedConnectionIds) {
        List<GatewayFrameWriteDTO> results = new ArrayList<>();
        for (String connectionId : safeList(acceptedConnectionIds)) {
            results.add(GatewayFrameWriteDTO.accepted(connectionId));
        }
        for (String connectionId : safeList(failedConnectionIds)) {
            results.add(GatewayFrameWriteDTO.failed(connectionId));
        }
        return results;
    }

    private static List<String> safeList(List<String> source) {
        return source == null ? List.of() : source;
    }
}
