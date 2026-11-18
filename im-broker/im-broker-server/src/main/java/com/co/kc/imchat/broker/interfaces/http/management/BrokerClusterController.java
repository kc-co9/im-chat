package com.co.kc.imchat.broker.interfaces.http.management;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;

import com.co.kc.imchat.broker.model.io.BrokerClusterOperationResponse;
import com.co.kc.imchat.broker.transformer.interfaces.BrokerManagementHttpTransformer;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Broker 已提交 cluster-state 与有界 operation 的只读投影。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/management/cluster-state")
public class BrokerClusterController {
    private final BrokerRaftAdapter repository;

    @GetMapping
    public BrokerClusterState state() {
        return repository.getCommittedState();
    }

    @GetMapping("/operations/{operationId}")
    public BrokerClusterOperationResponse operation(@PathVariable String operationId) {
        return repository.findOperation(new ClusterOperationId(operationId))
                .map(BrokerManagementHttpTransformer.INSTANCE::operationResponseFrom)
                .orElseThrow(() -> new IllegalArgumentException("operation not found"));
    }
}
