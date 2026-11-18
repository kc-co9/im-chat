package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.HashUtils;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 一次成员配置步骤的不可变目标及其许可历史。
 *
 * <p>步骤登记后只允许追加许可，目标配置和步骤身份不能被替换。这样重放快照或
 * 重试命令时，执行者只能针对共识中已经登记的目标工作。</p>
 */
public record BrokerMembershipStep(
        /* 成员步骤唯一身份。 */
        String stepId,
        /* 本步骤不可变的配置目标。 */
        ConsensusMembership target,
        /* 按执行顺序保存的许可历史。 */
        List<BrokerMembershipPermit> permits,
        /* 目标配置的规范化摘要。 */
        String targetDigest) implements Serializable {
    /**
     * 校验目标摘要、许可顺序和历史唯一性，并复制许可列表防止外部修改。
     */
    public BrokerMembershipStep {
        if (stepId == null || stepId.isBlank() || target == null || permits == null || target.voters().isEmpty()
                || targetDigest == null || targetDigest.isBlank()
                || permits.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("membership step is incomplete");
        }
        if (!digestOf(target).equals(targetDigest)) {
            throw new IllegalArgumentException("membership target digest does not match fixed target");
        }
        permits = List.copyOf(permits);
        for (int index = 0; index < permits.size(); index++) {
            BrokerMembershipPermit permit = permits.get(index);
            if (!stepId.equals(permit.stepId())
                    || !target.equals(permit.target())
                    || (index < permits.size() - 1 && permit.result() != BrokerMembershipPermit.Result.TARGET_NOT_APPLIED)
                    || permits.subList(0, index).stream().anyMatch(old -> old.permitId().equals(permit.permitId()))) {
                throw new IllegalArgumentException("membership permit history is inconsistent");
            }
        }
    }

    /**
     * 使用固定目标计算摘要，构造新的成员步骤。
     */
    public BrokerMembershipStep(String stepId, ConsensusMembership target, List<BrokerMembershipPermit> permits) {
        this(stepId, target, permits, digestOf(target));
    }

    /**
     * 返回当前步骤最后一条许可；步骤尚未登记许可时返回 null。
     */
    public BrokerMembershipPermit currentPermit() {
        return permits.isEmpty() ? null : permits.getLast();
    }

    /**
     * 判断当前步骤是否已经应用目标配置。
     */
    public boolean successful() {
        return currentPermit() != null && currentPermit().result() == BrokerMembershipPermit.Result.TARGET_APPLIED;
    }

    /**
     * 判断历史中是否仍有许可没有排空。
     */
    public boolean hasUndrainedPermit() {
        return permits.stream().anyMatch(permit -> !permit.drained());
    }

    /**
     * 摘要使用角色分隔及规范化端点，避免集合迭代顺序影响重放。
     */
    private static String digestOf(ConsensusMembership target) {
        String canonical = "voters\n" + target.voters().stream().map(endpoint -> endpoint.value()).sorted()
                .map(value -> value.length() + ":" + value).collect(Collectors.joining()) + "\nlearners\n"
                + target.learners().stream().map(endpoint -> endpoint.value()).sorted().map(value -> value.length() + ":" + value).collect(Collectors.joining());
        return HashUtils.sha256Hex(canonical);
    }
}
