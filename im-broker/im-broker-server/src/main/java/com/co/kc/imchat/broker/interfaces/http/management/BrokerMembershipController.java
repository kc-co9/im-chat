package com.co.kc.imchat.broker.interfaces.http.management;

import com.co.kc.imchat.broker.support.context.BrokerWriteAccess;
import com.co.kc.imchat.broker.support.context.BrokerWriteAccessScope;
import com.co.kc.imchat.broker.support.context.BrokerWriteAccessContext;
import com.co.kc.imchat.broker.support.context.BrokerWriteAccessContextHolder;
import com.co.kc.imchat.broker.application.cluster.BrokerMembershipAppService;
import com.co.kc.imchat.broker.model.cqrs.command.AddLearnerCommand;
import com.co.kc.imchat.broker.model.io.AddLearnerRequest;
import com.co.kc.imchat.broker.model.cqrs.command.DemoteVoterCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ConfirmLearnerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.PromoteLearnerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.RemoveBrokerCommand;
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
 * 接受受信 im-ops 服务发起的 Broker Raft 成员变更。
 */
@RestController
@RequiredArgsConstructor
@BrokerWriteAccess(BrokerWriteAccessScope.RAFT_WRITE)
@RequestMapping("/management/cluster-state/brokers/{brokerId}")
public class BrokerMembershipController {
    private final BrokerMembershipAppService membershipService;

    @PostMapping("/add-learner")
    public CompletionStage<BrokerManagementOperationResponse> addLearner(@PathVariable String brokerId,
                                                                         @RequestBody AddLearnerRequest request
    ) {
        BrokerWriteAccessContext context = BrokerWriteAccessContextHolder.get();
        AddLearnerCommand command = BrokerManagementHttpTransformer.INSTANCE.addLearnerCommand(
                context.operationId(), brokerId, context.expectedEpoch(), request.membershipEndpoint());
        return membershipService.addLearner(command)
                .thenApply(BrokerManagementHttpTransformer.INSTANCE::addLearnerResponseFrom);
    }

    @PostMapping("/complete-learner-sync")
    public CompletionStage<BrokerManagementOperationResponse> confirmLearner(@PathVariable String brokerId,
                                                                             @RequestBody BrokerManagementOperationRequest request
    ) {
        BrokerWriteAccessContext context = BrokerWriteAccessContextHolder.get();
        ConfirmLearnerCommand command = BrokerManagementHttpTransformer.INSTANCE.completeLearnerSyncCommand(
                context, brokerId, request);
        return membershipService.confirmLearner(command)
                .thenApply(BrokerManagementHttpTransformer.INSTANCE::completeLearnerSyncResponseFrom);
    }

    @PostMapping("/promote")
    public CompletionStage<BrokerManagementOperationResponse> promote(@PathVariable String brokerId,
                                                                      @RequestBody BrokerManagementOperationRequest request
    ) {
        BrokerWriteAccessContext context = BrokerWriteAccessContextHolder.get();
        PromoteLearnerCommand command = BrokerManagementHttpTransformer.INSTANCE.promoteLearnerCommand(
                context, brokerId, request);
        return membershipService.promoteLearner(command)
                .thenApply(BrokerManagementHttpTransformer.INSTANCE::promoteLearnerResponseFrom);
    }

    @PostMapping("/demote")
    public CompletionStage<BrokerManagementOperationResponse> demote(@PathVariable String brokerId,
                                                                     @RequestBody BrokerManagementOperationRequest request
    ) {
        BrokerWriteAccessContext context = BrokerWriteAccessContextHolder.get();
        DemoteVoterCommand command = BrokerManagementHttpTransformer.INSTANCE.demoteVoterCommand(
                context, brokerId, request);
        return membershipService.demoteVoter(command)
                .thenApply(BrokerManagementHttpTransformer.INSTANCE::demoteVoterResponseFrom);
    }

    @PostMapping("/remove")
    public CompletionStage<BrokerManagementOperationResponse> remove(@PathVariable String brokerId,
                                                                     @RequestBody BrokerManagementOperationRequest request
    ) {
        BrokerWriteAccessContext context = BrokerWriteAccessContextHolder.get();
        RemoveBrokerCommand command = BrokerManagementHttpTransformer.INSTANCE.removeBrokerCommand(
                context, brokerId, request);
        return membershipService.removeBroker(command)
                .thenApply(BrokerManagementHttpTransformer.INSTANCE::removeBrokerResponseFrom);
    }

}
