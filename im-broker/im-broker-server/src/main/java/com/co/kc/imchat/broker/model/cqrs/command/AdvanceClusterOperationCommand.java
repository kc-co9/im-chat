package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.Instant;

/** 单调推进已有集群 operation 的共识命令，Builder 和 JSON 重建均校验必填事实。 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
@ToString
public final class AdvanceClusterOperationCommand implements BrokerClusterConsensusCommand {
    /** 要推进的操作身份。 */
    private ClusterOperationId operationId;
    /** 必须匹配已登记操作的摘要。 */
    private String commandDigest;
    /** 推进前预期阶段。 */
    private BrokerClusterOperationPhase expectedPhase;
    /** 需要进入的下一阶段。 */
    private BrokerClusterOperationPhase nextPhase;
    /** 失败或降级原因，正常推进时为空。 */
    private String error;
    /** 事实发生时刻，归一到毫秒精度；epoch 零是合法值。 */
    private Instant changedAt;

    private AdvanceClusterOperationCommand() {
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private AdvanceClusterOperationCommand(ClusterOperationId operationId,
                                           String commandDigest,
                                           BrokerClusterOperationPhase expectedPhase,
                                           BrokerClusterOperationPhase nextPhase,
                                           String error,
                                           Instant changedAt) {
        this.operationId = operationId;
        this.commandDigest = commandDigest;
        this.expectedPhase = expectedPhase;
        this.nextPhase = nextPhase;
        this.error = error;
        this.changedAt = changedAt;
        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    private void validate() {
        if (operationId == null || commandDigest == null || commandDigest.isBlank()
                || expectedPhase == null || nextPhase == null || changedAt == null
                || changedAt.isBefore(Instant.EPOCH)) {
            throw new IllegalArgumentException("operation advance is invalid");
        }
        changedAt = Instant.ofEpochMilli(changedAt.toEpochMilli());
    }

    public static final class Builder {
        private final AdvanceClusterOperationCommand command = new AdvanceClusterOperationCommand();

        public Builder operationId(ClusterOperationId value) {
            command.operationId = value;
            return this;
        }

        public Builder commandDigest(String value) {
            command.commandDigest = value;
            return this;
        }

        public Builder expectedPhase(BrokerClusterOperationPhase value) {
            command.expectedPhase = value;
            return this;
        }

        public Builder nextPhase(BrokerClusterOperationPhase value) {
            command.nextPhase = value;
            return this;
        }

        public Builder error(String value) {
            command.error = value;
            return this;
        }

        public Builder changedAt(Instant value) {
            command.changedAt = value;
            return this;
        }

        public AdvanceClusterOperationCommand build() {
            command.validate();
            return command;
        }
    }
}
