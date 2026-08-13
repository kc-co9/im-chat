package com.co.kc.imchat.broker.sdk.model.result;

import java.io.Serializable;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerFrameWriteDTO;
import com.co.kc.imchat.broker.sdk.enums.BrokerFrameWriteStatus;
import com.co.kc.imchat.common.constant.FrameErrorCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Broker 实时帧处理结果。
 *
 * @param processed             是否已成功处理
 * @param code                  结果编码
 * @param message               结果描述
 * @param userId                下行目标用户 ID，上行处理结果为空
 * @param writeList               每个连接的写入处理结果
 */
public record BrokerFrameWriteResult(boolean processed,
                                     String code,
                                     String message,
                                     Long userId,
                                     List<BrokerFrameWriteDTO> writeList) implements Serializable {
    public BrokerFrameWriteResult {
        writeList = writeList == null ? Collections.emptyList() : List.copyOf(writeList);
    }

    public static BrokerFrameWriteResult ok() {
        return new BrokerFrameWriteResult(true, "OK", "OK", null, Collections.emptyList());
    }

    public static BrokerFrameWriteResult failed(FrameErrorCode errorCode) {
        return failed(errorCode, errorCode.message());
    }

    public static BrokerFrameWriteResult failed(FrameErrorCode errorCode, String message) {
        return failed(errorCode.code(), message);
    }

    public static BrokerFrameWriteResult failed(String code, String message) {
        return new BrokerFrameWriteResult(false, code, message, null, Collections.emptyList());
    }

    public static BrokerFrameWriteResult writeResult(Long userId, List<BrokerFrameWriteDTO> results) {
        return new BrokerFrameWriteResult(true, "OK", "OK", userId, immutableResultList(results));
    }

    public static BrokerFrameWriteResult writeResult(Long userId,
                                                     List<String> acceptedConnectionIds,
                                                     List<String> failedConnectionIds) {
        return writeResult(userId, toResults(acceptedConnectionIds, failedConnectionIds));
    }

    public List<String> acceptedConnectionIds() {
        return connectionIdsByStatus(BrokerFrameWriteStatus.ACCEPTED);
    }

    public List<String> failedConnectionIds() {
        return connectionIdsByStatus(BrokerFrameWriteStatus.FAILED);
    }

    private List<String> connectionIdsByStatus(BrokerFrameWriteStatus status) {
        return writeList.stream()
                .filter(result -> result.status() == status)
                .map(BrokerFrameWriteDTO::connectionId)
                .toList();
    }

    private static List<BrokerFrameWriteDTO> toResults(List<String> acceptedConnectionIds,
                                                       List<String> failedConnectionIds) {
        List<BrokerFrameWriteDTO> results = new ArrayList<>();
        for (String connectionId : immutableStringList(acceptedConnectionIds)) {
            results.add(BrokerFrameWriteDTO.accepted(connectionId));
        }
        for (String connectionId : immutableStringList(failedConnectionIds)) {
            results.add(BrokerFrameWriteDTO.failed(connectionId));
        }
        return results;
    }

    private static List<BrokerFrameWriteDTO> immutableResultList(List<BrokerFrameWriteDTO> source) {
        return source == null ? Collections.emptyList() : List.copyOf(source);
    }

    private static List<String> immutableStringList(List<String> source) {
        return source == null ? Collections.emptyList() : List.copyOf(source);
    }

}
