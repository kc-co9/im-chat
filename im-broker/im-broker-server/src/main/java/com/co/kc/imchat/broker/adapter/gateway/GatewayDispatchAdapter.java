package com.co.kc.imchat.broker.adapter.gateway;

import com.co.kc.imchat.broker.domain.connection.model.GatewayRegistration;
import com.co.kc.imchat.broker.model.cqrs.command.FrameDispatchCommand;
import com.co.kc.imchat.broker.sdk.enums.BrokerFrameWriteStatus;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerFrameWriteDTO;
import com.co.kc.imchat.broker.transformer.registry.RegistryTransformer;
import com.co.kc.imchat.common.utils.GeneratorUtils;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import com.co.kc.imchat.gateway.ws.sdk.model.params.GatewayFrameWriteParams;
import com.co.kc.imchat.gateway.ws.sdk.model.result.GatewayFrameWriteResult;
import com.co.kc.imchat.plugin.metrics.support.MetricsCollector;
import io.micrometer.core.instrument.Timer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * 向应用层已选定的 Gateway 发送下行帧，并转换逐连接写入结果。
 *
 * <p>阻塞式 SDK 调用交给配置层提供的执行器；本类不拥有执行器生命周期，也不读取集群状态、
 * 选择目标、校验路由资格或重试。多 Gateway 的并发编排与结果汇总由应用服务负责。</p>
 *
 * <p>指标仅描述 Broker 到 Gateway 的 RPC 执行，不表示客户端已收到或 ACK；单次 RPC 成功
 * 也不表示每个连接都写入成功，调用方须继续检查逐连接状态。</p>
 */
public final class GatewayDispatchAdapter {
    private final GatewayClient gatewayClient;
    private final Executor executor;
    private final MetricsCollector metricsCollector;

    /** 装配 Gateway SDK、外部管理的执行器与调用指标采集器。 */
    public GatewayDispatchAdapter(GatewayClient gatewayClient,
                                  Executor executor,
                                  MetricsCollector metricsCollector) {
        this.gatewayClient = gatewayClient;
        this.executor = executor;
        this.metricsCollector = metricsCollector;
    }

    /**
     * 异步投递至指定 Gateway，返回该网关逐连接的写入结果。
     *
     * <p>沿用稳定 deliveryId，每次实际 SDK 调用生成新的 deliveryAttemptId。
     * SDK 调用或结果转换失败通过异步阶段传播；同步提交被执行器拒绝时返回失败阶段，
     * 不回退到调用线程执行。排队等待不计入工作线程内采集的调用耗时。</p>
     */
    public CompletionStage<List<BrokerFrameWriteDTO>> dispatch(
            GatewayRegistration gateway,
            FrameDispatchCommand command) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                Timer.Sample sample = metricsCollector.start();
                try {
                    GatewayFrameWriteResult writes = gatewayClient.writeFrame(
                            RegistryTransformer.INSTANCE.gatewayEndpointFrom(gateway),
                            new GatewayFrameWriteParams(command.userId(), command.deliveryId(),
                                    GeneratorUtils.nextUUID(), command.frame()));
                    List<BrokerFrameWriteDTO> result = writes.writeList().stream()
                            .map(write -> new BrokerFrameWriteDTO(write.connectionId(),
                                    BrokerFrameWriteStatus.valueOf(write.status().name())))
                            .toList();
                    // 成功指标表示调用及结果转换完成；连接级失败仍保留在返回列表中。
                    metricsCollector.success("im.broker.gateway.frame.write", new String[0]);
                    return result;
                } catch (RuntimeException | Error failure) {
                    metricsCollector.failure("im.broker.gateway.frame.write", new String[0]);
                    throw failure;
                } finally {
                    metricsCollector.stop("im.broker.gateway.frame.write", new String[0], sample);
                }
            }, executor);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
