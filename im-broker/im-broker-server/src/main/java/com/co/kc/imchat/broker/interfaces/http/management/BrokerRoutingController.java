package com.co.kc.imchat.broker.interfaces.http.management;

import com.co.kc.imchat.broker.support.context.BrokerWriteAccess;
import com.co.kc.imchat.broker.support.context.BrokerWriteAccessScope;
import com.co.kc.imchat.broker.support.context.BrokerWriteAccessContext;
import com.co.kc.imchat.broker.support.context.BrokerWriteAccessContextHolder;
import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.model.cqrs.command.ActivateBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.DeactivateBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.DrainBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ForceDeactivateBrokerCommand;
import com.co.kc.imchat.broker.model.io.BrokerManagementOperationRequest;
import com.co.kc.imchat.broker.model.io.BrokerManagementOperationResponse;
import com.co.kc.imchat.broker.transformer.interfaces.BrokerManagementHttpTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletionStage;

/**
 * 接受受信 im-ops 服务发起的 Broker 路由状态变更。
 */
@RestController
@RequiredArgsConstructor
@BrokerWriteAccess(BrokerWriteAccessScope.CLUSTER_STATE_WRITE)
@RequestMapping("/management/cluster-state/brokers/{brokerId}")
public class BrokerRoutingController {
    private final BrokerClusterAppService operationService;

    @PostMapping("/activate")
    public CompletionStage<BrokerManagementOperationResponse> activate(@PathVariable String brokerId) {
        BrokerWriteAccessContext context = BrokerWriteAccessContextHolder.get();
        ActivateBrokerCommand command = BrokerManagementHttpTransformer.INSTANCE.activateCommand(
                context.operationId(), brokerId, context.expectedEpoch());
        return operationService.activate(command).thenApply(BrokerManagementHttpTransformer.INSTANCE::activateBrokerResponseFrom);
    }

    @PostMapping("/deactivate")
    public CompletionStage<BrokerManagementOperationResponse> deactivate(@PathVariable String brokerId) {
        BrokerWriteAccessContext context = BrokerWriteAccessContextHolder.get();
        DeactivateBrokerCommand command = BrokerManagementHttpTransformer.INSTANCE.deactivateCommand(
                context.operationId(), brokerId, context.expectedEpoch());
        return operationService.deactivate(command).thenApply(BrokerManagementHttpTransformer.INSTANCE::deactivateBrokerResponseFrom);
    }

    @PostMapping("/drain")
    public CompletionStage<BrokerManagementOperationResponse> drain(@PathVariable String brokerId) {
        BrokerWriteAccessContext context = BrokerWriteAccessContextHolder.get();
        DrainBrokerCommand command = BrokerManagementHttpTransformer.INSTANCE.drainCommand(
                context.operationId(), brokerId, context.expectedEpoch());
        return operationService.drain(command).thenApply(BrokerManagementHttpTransformer.INSTANCE::drainBrokerResponseFrom);
    }

    /** 强制摘流需独立的 FORCE 权限，不继承普通路由状态写权限。 */
    @BrokerWriteAccess(BrokerWriteAccessScope.FORCE)
    @PostMapping("/force-deactivate")
    public CompletionStage<BrokerManagementOperationResponse> forceDeactivate(
            @PathVariable String brokerId,
            @RequestBody(required = false) BrokerManagementOperationRequest request) {
        BrokerWriteAccessContext context = BrokerWriteAccessContextHolder.get();
        BrokerManagementOperationRequest body = request == null
                ? new BrokerManagementOperationRequest(null, null) : request;
        ForceDeactivateBrokerCommand command = BrokerManagementHttpTransformer.INSTANCE.forceDeactivateCommand(
                context.operationId(), brokerId, context.expectedEpoch(), body.reason());
        return operationService.forceDeactivate(command)
                .thenApply(BrokerManagementHttpTransformer.INSTANCE::forceDeactivateBrokerResponseFrom);
    }
}
