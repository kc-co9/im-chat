package com.co.kc.imchat.broker.adapter.cluster.raft;

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

/** 使用显式版本和类型判别编码可重放的 BrokerCluster 共识命令。 */
public final class BrokerClusterCommandCodec {

    private static final int CURRENT_FORMAT_VERSION = 5;

    private BrokerClusterCommandCodec() {
    }

    public static byte[] encode(BrokerClusterConsensusCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("broker cluster command is required");
        }
        CommandEnvelope envelope = new CommandEnvelope(CURRENT_FORMAT_VERSION,
                typeOf(command), JsonUtils.toJson(command));
        return JsonUtils.toJson(envelope).getBytes(StandardCharsets.UTF_8);
    }

    public static BrokerClusterConsensusCommand decode(byte[] content) {
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
        return switch (envelope.commandType()) {
            case "BEGIN_BROKER_CLUSTER_OPERATION" -> decode(
                    envelope, BeginBrokerClusterOperationCommand.class);
            case "COMMIT_BROKER_OPERATION" -> decode(envelope, CommitBrokerOperationCommand.class);
            case "ADVANCE_CLUSTER_OPERATION" -> decode(envelope, AdvanceClusterOperationCommand.class);
            case "FENCE_ROUTING_EPOCH" -> decode(envelope, CommitRoutingEpochFenceCommand.class);
            case "UPDATE_BROKER_DRAIN_PROGRESS" -> decode(
                    envelope, UpdateBrokerDrainProgressCommand.class);
            case "COMPLETE_BROKER_DRAIN" -> decode(envelope, CompleteBrokerDrainCommand.class);
            case "FORCE_BROKER_DRAIN" -> decode(envelope, ForceDrainOperationCommand.class);
            case "VERIFY_ROUTE_RECOVERY" -> decode(envelope, VerifyRouteRecoveryCommand.class);
            case "COMPLETE_ROUTE_RECOVERY" -> decode(envelope, CompleteRouteRecoveryCommand.class);
            default -> throw new IllegalArgumentException(
                    "unsupported broker cluster command type: " + envelope.commandType());
        };
    }

    private static String typeOf(BrokerClusterConsensusCommand command) {
        if (command instanceof BeginBrokerClusterOperationCommand) {
            return "BEGIN_BROKER_CLUSTER_OPERATION";
        }
        if (command instanceof CommitBrokerOperationCommand) {
            return "COMMIT_BROKER_OPERATION";
        }
        if (command instanceof AdvanceClusterOperationCommand) {
            return "ADVANCE_CLUSTER_OPERATION";
        }
        if (command instanceof CommitRoutingEpochFenceCommand) {
            return "FENCE_ROUTING_EPOCH";
        }
        if (command instanceof UpdateBrokerDrainProgressCommand) {
            return "UPDATE_BROKER_DRAIN_PROGRESS";
        }
        if (command instanceof CompleteBrokerDrainCommand) {
            return "COMPLETE_BROKER_DRAIN";
        }
        if (command instanceof ForceDrainOperationCommand) {
            return "FORCE_BROKER_DRAIN";
        }
        if (command instanceof VerifyRouteRecoveryCommand) {
            return "VERIFY_ROUTE_RECOVERY";
        }
        if (command instanceof CompleteRouteRecoveryCommand) {
            return "COMPLETE_ROUTE_RECOVERY";
        }
        throw new IllegalArgumentException("unsupported broker cluster command type");
    }

    private static <T extends BrokerClusterConsensusCommand> T decode(CommandEnvelope envelope,
                                                                      Class<T> commandType) {
        return JsonUtils.fromJson(envelope.payload(), commandType);
    }

    private record CommandEnvelope(int formatVersion, String commandType, String payload) {
    }
}
