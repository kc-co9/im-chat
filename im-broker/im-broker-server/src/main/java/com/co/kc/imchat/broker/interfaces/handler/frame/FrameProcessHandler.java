package com.co.kc.imchat.broker.interfaces.handler.frame;

import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.domain.registry.connection.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.registry.gateway.GatewayRegistry;
import com.co.kc.imchat.broker.domain.service.BrokerConnectionService;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerFrameWriteDTO;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.enums.BrokerFrameWriteStatus;
import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.dto.UserGatewayDTO;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.support.client.BrokerPeerClient;
import com.co.kc.imchat.broker.transformer.MessageFrameTransformer;
import com.co.kc.imchat.common.constant.FrameCommand;
import com.co.kc.imchat.common.constant.FrameErrorCode;
import com.co.kc.imchat.common.exception.BaseException;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import com.co.kc.imchat.gateway.ws.sdk.model.dto.GatewayFrameWriteDTO;
import com.co.kc.imchat.gateway.ws.sdk.model.result.GatewayFrameWriteResult;
import com.co.kc.imchat.gateway.ws.sdk.model.params.GatewayFrameWriteParams;
import com.co.kc.imchat.service.message.facade.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 实时帧处理 RPC 处理器。
 * <p>
 * 处理客户端上行实时帧、服务端下行用户推送。Broker 只负责路由和协议转换，具体业务语义由消息服务处理。
 */
@Slf4j
@Component
public class FrameProcessHandler extends AbstractBrokerRpcHandler<BrokerFrameWriteParams, BrokerFrameWriteResult> {
    private static final MessageFrameTransformer MESSAGE_FRAME_TRANSFORMER = MessageFrameTransformer.INSTANCE;

    private final ConnectionRegistry connectionRegistry;
    private final GatewayRegistry gatewayRegistry;
    private final MessageService messageService;
    private final GatewayClient gatewayClient;
    private final BrokerConnectionService brokerConnectionService;
    private final BrokerPeerClient brokerPeerClient;

    public FrameProcessHandler(ConnectionRegistry connectionRegistry,
                               GatewayRegistry gatewayRegistry,
                               MessageService messageService,
                               GatewayClient gatewayClient,
                               BrokerConnectionService brokerConnectionService,
                               BrokerPeerClient brokerPeerClient) {
        super(BrokerBoltOperation.WRITE_FRAME);
        this.connectionRegistry = connectionRegistry;
        this.gatewayRegistry = gatewayRegistry;
        this.messageService = messageService;
        this.gatewayClient = gatewayClient;
        this.brokerConnectionService = brokerConnectionService;
        this.brokerPeerClient = brokerPeerClient;
    }

    @Override
    protected BrokerFrameWriteResult process(BrokerFrameWriteParams params) {
        if (params == null || params.direction() == null) {
            return BrokerFrameWriteResult.failed(FrameErrorCode.BAD_REQUEST, "实时帧处理请求不能为空");
        }
        return switch (params.direction()) {
            case INBOUND -> processInboundFrame(params);
            case OUTBOUND -> processOutboundFrame(params);
        };
    }

    private BrokerFrameWriteResult processInboundFrame(BrokerFrameWriteParams params) {
        if (params.userId() == null || params.connectionId() == null
                || params.connectionId().isBlank() || params.request() == null) {
            return BrokerFrameWriteResult.failed(FrameErrorCode.UNAUTHORIZED);
        }
        Optional<FrameCommand> frameCommand = FrameCommand.from(params.request().cmd());
        if (frameCommand.isEmpty()) {
            return BrokerFrameWriteResult.failed(FrameErrorCode.UNKNOWN_CMD);
        }

        try {
            switch (frameCommand.get()) {
                case PRIVATE_MESSAGE_SEND -> messageService.sendPrivateMessage(
                        MESSAGE_FRAME_TRANSFORMER.privateMessageSendParamsFrom(params.userId(), params.request()));
                case PRIVATE_MESSAGE_READ -> messageService.readPrivateMessage(
                        MESSAGE_FRAME_TRANSFORMER.privateMessageReadParamsFrom(params.userId(), params.request()));
                case PRIVATE_MESSAGE_REVOKE -> messageService.revokePrivateMessage(
                        MESSAGE_FRAME_TRANSFORMER.privateMessageRevokeParamsFrom(params.userId(), params.request()));
                case GROUP_MESSAGE_SEND -> messageService.sendGroupMessage(
                        MESSAGE_FRAME_TRANSFORMER.groupMessageSendParamsFrom(params.userId(), params.request()));
                case GROUP_MESSAGE_READ -> messageService.readGroupMessage(
                        MESSAGE_FRAME_TRANSFORMER.groupMessageReadParamsFrom(params.userId(), params.request()));
                case GROUP_MESSAGE_REVOKE -> messageService.revokeGroupMessage(
                        MESSAGE_FRAME_TRANSFORMER.groupMessageRevokeParamsFrom(params.userId(), params.request()));
                case NOTIFICATION_ACK -> messageService.ackNotification(
                        MESSAGE_FRAME_TRANSFORMER.notificationAckParamsFrom(params.userId(), params.request()));
            }
        } catch (IllegalArgumentException ex) {
            return BrokerFrameWriteResult.failed(FrameErrorCode.BAD_REQUEST, ex.getMessage());
        } catch (BaseException ex) {
            return BrokerFrameWriteResult.failed(String.valueOf(ex.getCode()), exceptionMessage(ex));
        } catch (RuntimeException ex) {
            log.warn("failed to process message frame, cmd:{}, error:{}", params.request().cmd(), ex.toString());
            return BrokerFrameWriteResult.failed(FrameErrorCode.BROKER_UNAVAILABLE);
        }
        return BrokerFrameWriteResult.ok();
    }

    private BrokerFrameWriteResult processOutboundFrame(BrokerFrameWriteParams params) {
        if (params.userId() == null || params.response() == null) {
            return BrokerFrameWriteResult.failed(FrameErrorCode.BAD_REQUEST, "下行帧目标用户和内容不能为空");
        }
        Optional<BrokerFrameWriteResult> forwardedResult = forwardOutboundToOwner(params);
        if (forwardedResult.isPresent()) {
            return forwardedResult.get();
        }
        List<UserGatewayDTO> connections = connectionRegistry.find(params.userId());
        List<BrokerFrameWriteDTO> results = new ArrayList<>();
        for (UserGatewayDTO connection : connections) {
            try {
                Optional<GatewayEndpointDTO> gateway = gatewayRegistry.find(connection.gatewayId());
                if (gateway.isEmpty()) {
                    continue;
                }
                GatewayFrameWriteResult writeResult = gatewayClient.writeFrame(gateway.get(),
                        new GatewayFrameWriteParams(params.userId(), params.response()));
                results.addAll(writeResult.writeList().stream()
                        .map(this::toBrokerWriteResult)
                        .toList());
            } catch (RuntimeException ex) {
                log.warn("failed to write outbound frame to gateway:{}, userId:{}, error:{}",
                        connection.gatewayId(), params.userId(), ex.toString());
            }
        }
        return BrokerFrameWriteResult.writeResult(params.userId(), results);
    }

    private Optional<BrokerFrameWriteResult> forwardOutboundToOwner(BrokerFrameWriteParams params) {
        Optional<BrokerEndpointDTO> owner = brokerConnectionService.decideBroker(params.userId());
        if (owner.isEmpty() || brokerConnectionService.isCurrentBroker(owner.get())) {
            return Optional.empty();
        }
        try {
            return Optional.of(brokerPeerClient.writeFrame(owner.get(), params));
        } catch (RuntimeException ex) {
            log.warn("failed to forward outbound frame to broker:{}, userId:{}, error:{}",
                    owner.get().brokerId(), params.userId(), ex.toString());
            return Optional.of(BrokerFrameWriteResult.failed(FrameErrorCode.BROKER_UNAVAILABLE));
        }
    }

    private BrokerFrameWriteDTO toBrokerWriteResult(GatewayFrameWriteDTO result) {
        return new BrokerFrameWriteDTO(result.connectionId(),
                BrokerFrameWriteStatus.valueOf(result.status().name()));
    }

    private String exceptionMessage(BaseException ex) {
        if (StringUtils.isNotBlank(ex.getReason())) {
            return ex.getReason();
        }
        return ex.getMsg();
    }
}
