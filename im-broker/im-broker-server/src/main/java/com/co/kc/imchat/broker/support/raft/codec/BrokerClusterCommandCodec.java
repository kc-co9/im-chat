package com.co.kc.imchat.broker.support.raft.codec;

import com.co.kc.imchat.broker.model.cqrs.command.AdvanceClusterOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.model.cqrs.command.BeginBrokerClusterOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CommitBrokerOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CompleteBrokerDrainCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CommitRoutingEpochFenceCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ForceDrainOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.UpdateBrokerDrainProgressCommand;
import com.co.kc.imchat.broker.model.cqrs.command.VerifyRouteRecoveryCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CompleteRouteRecoveryCommand;
import com.co.kc.imchat.common.utils.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.co.kc.imchat.plugin.raft.spi.RaftCommandCodec;

/** 使用显式版本和类型判别编码可重放的 BrokerCluster 共识命令。 */
public final class BrokerClusterCommandCodec implements RaftCommandCodec<BrokerClusterConsensusCommand> {

    public static final BrokerClusterCommandCodec INSTANCE = new BrokerClusterCommandCodec();

    private static final int CURRENT_FORMAT_VERSION = 2;

    /* 唯一的稳定协议白名单，同时驱动编码与解码；不允许按输入加载 Class。 */
    private static final Map<String, Class<? extends BrokerClusterConsensusCommand>> COMMAND_TYPES = Map.ofEntries(
            Map.entry("BEGIN_BROKER_CLUSTER_OPERATION", BeginBrokerClusterOperationCommand.class),
            Map.entry("COMMIT_BROKER_OPERATION", CommitBrokerOperationCommand.class),
            Map.entry("ADVANCE_CLUSTER_OPERATION", AdvanceClusterOperationCommand.class),
            Map.entry("FENCE_ROUTING_EPOCH", CommitRoutingEpochFenceCommand.class),
            Map.entry("UPDATE_BROKER_DRAIN_PROGRESS", UpdateBrokerDrainProgressCommand.class),
            Map.entry("COMPLETE_BROKER_DRAIN", CompleteBrokerDrainCommand.class),
            Map.entry("FORCE_BROKER_DRAIN", ForceDrainOperationCommand.class),
            Map.entry("VERIFY_ROUTE_RECOVERY", VerifyRouteRecoveryCommand.class),
            Map.entry("COMPLETE_ROUTE_RECOVERY", CompleteRouteRecoveryCommand.class));

    private BrokerClusterCommandCodec() {
    }

    @Override
    public byte[] encode(BrokerClusterConsensusCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("broker cluster command is required");
        }
        return JsonUtils.toJson(new CommandEnvelope(
                CURRENT_FORMAT_VERSION, typeOf(command), JsonUtils.toJson(command)))
                .getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public BrokerClusterConsensusCommand decode(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("broker cluster command content is empty");
        }
        CommandEnvelope envelope = JsonUtils.fromJson(
                new String(content, StandardCharsets.UTF_8), CommandEnvelope.class);
        if (envelope.formatVersion() != CURRENT_FORMAT_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported broker cluster command format: " + envelope.formatVersion());
        }
        if (envelope.commandType() == null || envelope.payload() == null) {
            throw new IllegalArgumentException("broker cluster command envelope is incomplete");
        }
        Class<? extends BrokerClusterConsensusCommand> commandType = COMMAND_TYPES.get(envelope.commandType());
        if (commandType == null) {
            throw new IllegalArgumentException("unsupported broker cluster command type: " + envelope.commandType());
        }
        return JsonUtils.fromJson(envelope.payload(), commandType);
    }

    private static String typeOf(BrokerClusterConsensusCommand command) {
        return COMMAND_TYPES.entrySet().stream()
                .filter(entry -> entry.getValue().equals(command.getClass()))
                .map(Map.Entry::getKey).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported broker cluster command type"));
    }

    private record CommandEnvelope(int formatVersion, String commandType, String payload) {
    }
}
