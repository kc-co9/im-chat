package com.co.kc.imchat.management.ops.interfaces.http;

import com.co.kc.imchat.management.ops.application.BrokerClusterOperationAppService;
import com.co.kc.imchat.management.ops.model.cqrs.query.BrokerOperationQuery;
import com.co.kc.imchat.management.ops.model.io.OpsCliClusterResponse;
import com.co.kc.imchat.management.ops.model.io.OpsCliOperationResponse;
import com.co.kc.imchat.management.ops.model.io.OpsCliSubmissionResponse;
import com.co.kc.imchat.management.ops.transformer.interfaces.OpsCliHttpTransformer;
import com.co.kc.imchat.management.ops.model.cqrs.command.BrokerClusterOperationCommand;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.co.kc.imchat.management.ops.domain.authorization.model.OpsPermission;
import com.co.kc.imchat.management.ops.support.security.RequiresCliPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 交互式 CLI 的窄管理入口，同时校验可信 scope 与管理员权限。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cli")
@Tag(name = "Ops CLI", description = "管理员 Bearer 窄管理接口；每个用例同时校验 scope 与管理员权限")
public class OpsCliController {
    /** 编排 Broker 集群操作和查询的应用服务。 */
    private final BrokerClusterOperationAppService operationAppService;

    @Operation(summary = "查询既有 operation", description = "需要 ops:broker:read；UNKNOWN 不表示未提交，时间使用毫秒字符串")
    @GetMapping("/operations/{operationId}")
    @RequiresCliPermission(OpsPermission.Code.BROKER_READ)
    public OpsCliOperationResponse queryOperation(
            @PathVariable String operationId) {
        // 查询既有 operation，不根据网络失败推断提交结果。
        return OpsCliHttpTransformer.INSTANCE
                .operationResponseFrom(operationAppService.queryOperation(new BrokerOperationQuery(operationId)));
    }

    @Operation(summary = "查询集群运行投影", description = "需要 ops:broker:read；携带来源与 revision/epoch，不声称线性一致")
    @GetMapping("/cluster-state")
    @RequiresCliPermission(OpsPermission.Code.BROKER_READ)
    public OpsCliClusterResponse clusterState() {
        // 返回带来源和版本的运行投影，供 CLI 展示当前观测事实。
        return OpsCliHttpTransformer.INSTANCE.clusterResponseFrom(operationAppService.sourcedClusterState());
    }

    @Operation(summary = "加入 learner", description = "需要 ops:broker:raft:write；显式提交 operationId 与 expectedEpoch，不自动刷新或更换操作身份")
    @PostMapping("/brokers/{brokerId}/raft/add-learner")
    @RequiresCliPermission(OpsPermission.Code.BROKER_RAFT_WRITE)
    public OpsCliSubmissionResponse addLearner(@PathVariable String brokerId,
                                        @RequestBody AddLearnerRequest request) {
        // 将 HTTP 请求转换为应用命令，保持 operationId 和 expectedEpoch 不变。
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                brokerId, "add-learner", request.operationId(), request.expectedEpoch(),
                request.membershipEndpoint(), null, OpsPermission.Code.BROKER_RAFT_WRITE);
        return OpsCliHttpTransformer.INSTANCE.submissionResponseFrom(operationAppService.operate(command));
    }

    @Operation(summary = "确认 learner 同步", description = "需要 ops:broker:raft:write；显式提交 operationId 与 expectedEpoch，不自动刷新或更换操作身份")
    @PostMapping("/brokers/{brokerId}/raft/complete-learner-sync")
    @RequiresCliPermission(OpsPermission.Code.BROKER_RAFT_WRITE)
    public OpsCliSubmissionResponse completeLearnerSync(@PathVariable String brokerId,
                                                 @RequestBody CompleteLearnerSyncRequest request) {
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                brokerId, "complete-learner-sync", request.operationId(), request.expectedEpoch(),
                request.membershipEndpoint(), request.reason(), OpsPermission.Code.BROKER_RAFT_WRITE);
        return OpsCliHttpTransformer.INSTANCE.submissionResponseFrom(operationAppService.operate(command));
    }

    @Operation(summary = "晋升 learner", description = "需要 ops:broker:raft:write；显式提交 operationId 与 expectedEpoch，不自动刷新或更换操作身份")
    @PostMapping("/brokers/{brokerId}/raft/promote")
    @RequiresCliPermission(OpsPermission.Code.BROKER_RAFT_WRITE)
    public OpsCliSubmissionResponse promote(@PathVariable String brokerId,
                                      @RequestBody MembershipOperationRequest request) {
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                brokerId, "promote", request.operationId(), request.expectedEpoch(),
                request.membershipEndpoint(), request.reason(), OpsPermission.Code.BROKER_RAFT_WRITE);
        return OpsCliHttpTransformer.INSTANCE.submissionResponseFrom(operationAppService.operate(command));
    }

    @Operation(summary = "降级 voter", description = "需要 ops:broker:raft:write；显式提交 operationId 与 expectedEpoch，不自动刷新或更换操作身份")
    @PostMapping("/brokers/{brokerId}/raft/demote")
    @RequiresCliPermission(OpsPermission.Code.BROKER_RAFT_WRITE)
    public OpsCliSubmissionResponse demote(@PathVariable String brokerId,
                                     @RequestBody MembershipOperationRequest request) {
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                brokerId, "demote", request.operationId(), request.expectedEpoch(),
                request.membershipEndpoint(), request.reason(), OpsPermission.Code.BROKER_RAFT_WRITE);
        return OpsCliHttpTransformer.INSTANCE.submissionResponseFrom(operationAppService.operate(command));
    }

    @Operation(summary = "移除成员", description = "需要 ops:broker:raft:write；显式提交 operationId 与 expectedEpoch，不自动刷新或更换操作身份")
    @PostMapping("/brokers/{brokerId}/raft/remove")
    @RequiresCliPermission(OpsPermission.Code.BROKER_RAFT_WRITE)
    public OpsCliSubmissionResponse remove(@PathVariable String brokerId,
                                     @RequestBody MembershipOperationRequest request) {
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                brokerId, "remove", request.operationId(), request.expectedEpoch(),
                request.membershipEndpoint(), request.reason(), OpsPermission.Code.BROKER_RAFT_WRITE);
        return OpsCliHttpTransformer.INSTANCE.submissionResponseFrom(operationAppService.operate(command));
    }

    /** 同步确认保留原请求端点；可选原因不替代请求身份。 */
    public record CompleteLearnerSyncRequest(
            String operationId,
            Long expectedEpoch,
            String membershipEndpoint,
            String reason) {
        public CompleteLearnerSyncRequest {
            if (expectedEpoch == null) {
                throw new IllegalArgumentException("expectedEpoch must not be null");
            }
            if (membershipEndpoint == null || membershipEndpoint.isBlank()) {
                throw new IllegalArgumentException("membershipEndpoint must not be blank");
            }
        }
    }

    public record MembershipOperationRequest(
            /* 调用者显式提供的操作身份。 */
            String operationId,
            /* 不自动刷新的预期 epoch。 */
            Long expectedEpoch,
            /* 原请求成员端点，禁止重试时替换。 */
            String membershipEndpoint,
            /* 其他成员操作的显式原因。 */
            String reason) {
        /** 其余成员操作共用的请求体，必须显式携带预期 epoch 和成员端点。 */
        public MembershipOperationRequest {
            if (expectedEpoch == null) {
                throw new IllegalArgumentException("expectedEpoch must not be null");
            }
            if (membershipEndpoint == null || membershipEndpoint.isBlank()) {
                throw new IllegalArgumentException("membershipEndpoint must not be blank");
            }
        }
    }

    public record AddLearnerRequest(
            /* 调用者显式提供的操作身份。 */
            String operationId,
            /* 不自动刷新的预期纪元。 */
            Long expectedEpoch,
            /* 新 learner 的成员端点。 */
            String membershipEndpoint
    ) {
        /** learner 加入请求必须同时提供固定操作身份、epoch 和成员端点。 */
        public AddLearnerRequest {
            if (expectedEpoch == null) {
                throw new IllegalArgumentException("expectedEpoch must not be null");
            }
            if (membershipEndpoint == null || membershipEndpoint.isBlank()) {
                throw new IllegalArgumentException("membershipEndpoint must not be blank");
            }
        }
    }
}
