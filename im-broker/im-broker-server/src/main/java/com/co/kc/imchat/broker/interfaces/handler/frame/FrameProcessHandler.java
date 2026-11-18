package com.co.kc.imchat.broker.interfaces.handler.frame;

import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.application.frame.FrameDispatchAppService;
import com.co.kc.imchat.broker.model.cqrs.command.FrameDispatchCommand;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.model.result.RouteBrokerResult;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.transformer.interfaces.MessageFrameTransformer;
import com.co.kc.imchat.common.constant.FrameCommand;
import com.co.kc.imchat.common.constant.FrameErrorCode;
import com.co.kc.imchat.common.exception.BaseException;
import com.co.kc.imchat.common.exception.RpcException;
import com.co.kc.imchat.service.message.facade.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 实时帧处理 RPC 处理器。
 * <p>
 * 处理客户端上行实时帧、服务端下行用户推送。Broker 只负责路由和协议转换，具体业务语义由消息服务处理。
 */
@Slf4j
@Component
public class FrameProcessHandler extends
        AbstractBrokerRpcHandler<BrokerFrameWriteParams, RouteBrokerResult> {
    private static final MessageFrameTransformer MESSAGE_FRAME_TRANSFORMER = MessageFrameTransformer.INSTANCE;

    private final MessageService messageService;
    private final FrameDispatchAppService frameDispatchService;

    public FrameProcessHandler(MessageService messageService,
                               FrameDispatchAppService frameDispatchService) {
        super(BrokerBoltOperation.WRITE_FRAME);
        this.messageService = messageService;
        this.frameDispatchService = frameDispatchService;
    }

    @Override
    protected BrokerFrameWriteResult process(BrokerFrameWriteParams params) {
        if (params == null) {
            throw new IllegalArgumentException("实时帧处理请求不能为空");
        }
        return processFrame(params);
    }

    private BrokerFrameWriteResult processFrame(BrokerFrameWriteParams params) {
        if (params.direction() == null) {
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
        } catch (RpcException ex) {
            return BrokerFrameWriteResult.failed(String.valueOf(ex.getCode()), ex.getMessage());
        } catch (RuntimeException ex) {
            RpcException rpcException = findRpcException(ex);
            if (rpcException != null) {
                return BrokerFrameWriteResult.failed(
                        String.valueOf(rpcException.getCode()), rpcException.getMessage());
            }
            log.warn("failed to process message frame, cmd:{}, error:{}", params.request().cmd(), ex.toString());
            return BrokerFrameWriteResult.failed(FrameErrorCode.BROKER_UNAVAILABLE);
        }
        return BrokerFrameWriteResult.ok();
    }

    private BrokerFrameWriteResult processOutboundFrame(BrokerFrameWriteParams params) {
        if (params.userId() == null || params.response() == null) {
            return BrokerFrameWriteResult.failed(FrameErrorCode.BAD_REQUEST, "下行帧目标用户和内容不能为空");
        }
        return frameDispatchService.dispatch(new FrameDispatchCommand(
                params.userId(), params.response(), params.routeContext().operationId(),
                params.routeContext().routingEpoch()));
    }

    private String exceptionMessage(BaseException ex) {
        if (StringUtils.isNotBlank(ex.getReason())) {
            return ex.getReason();
        }
        return ex.getMsg();
    }

    private RpcException findRpcException(Throwable failure) {
        Throwable current = failure.getCause();
        for (int depth = 0; current != null && current != failure && depth < 10; depth++) {
            if (current instanceof RpcException rpcException) {
                return rpcException;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return null;
    }
}
