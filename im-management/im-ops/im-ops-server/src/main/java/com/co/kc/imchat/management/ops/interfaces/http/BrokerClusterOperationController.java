package com.co.kc.imchat.management.ops.interfaces.http;

import com.co.kc.imchat.management.iam.sdk.security.RequiresPermission;
import com.co.kc.imchat.management.ops.application.BrokerClusterOperationAppService;
import com.co.kc.imchat.management.ops.domain.authorization.model.OpsPermission;
import com.co.kc.imchat.management.ops.model.cqrs.command.BrokerClusterOperationCommand;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerClusterStateDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerOperationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ops 页面发起受控 Broker 集群操作的入口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class BrokerClusterOperationController {
    private final BrokerClusterOperationAppService operationAppService;

    @GetMapping("/cluster-state")
    @RequiresPermission(OpsPermission.Code.BROKER_READ)
    public BrokerClusterStateDTO clusterState() {
        return operationAppService.clusterState();
    }

    @PostMapping("/brokers/{brokerId}/cluster-state/{action}")
    @RequiresPermission(OpsPermission.Code.BROKER_CLUSTER_STATE_WRITE)
    public BrokerOperationDTO clusterState(@PathVariable String brokerId,
                                                @PathVariable String action,
                                                @RequestBody OperationRequest request) {
        requireAction(action, "activate", "drain", "deactivate");
        return operate(brokerId, action, request,
                OpsPermission.Code.BROKER_CLUSTER_STATE_WRITE);
    }

    @PostMapping("/brokers/{brokerId}/raft/{action}")
    @RequiresPermission(OpsPermission.Code.BROKER_RAFT_WRITE)
    public BrokerOperationDTO raft(@PathVariable String brokerId,
                                       @PathVariable String action,
                                       @RequestBody OperationRequest request) {
        requireAction(action, "add-learner", "complete-learner-sync", "promote", "demote", "remove");
        return operate(brokerId, action, request, OpsPermission.Code.BROKER_RAFT_WRITE);
    }

    @PostMapping("/brokers/{brokerId}/force-deactivate")
    @RequiresPermission(OpsPermission.Code.BROKER_FORCE)
    public BrokerOperationDTO forceDeactivate(@PathVariable String brokerId,
                                                  @RequestBody OperationRequest request) {
        return operate(brokerId, "force-deactivate", request,
                OpsPermission.Code.BROKER_FORCE);
    }

    private BrokerOperationDTO operate(String brokerId, String action,
                                           OperationRequest request, String scope) {
        return operationAppService.operate(new BrokerClusterOperationCommand(
                brokerId, action, request.operationId(), request.expectedEpoch(),
                request.membershipEndpoint(), request.reason(), scope));
    }

    private void requireAction(String action, String... allowed) {
        if (java.util.Arrays.stream(allowed).noneMatch(action::equals)) {
            throw new IllegalArgumentException("unsupported operation action");
        }
    }

    public record OperationRequest(String operationId, long expectedEpoch,
                                   String membershipEndpoint, String reason) {
    }
}
