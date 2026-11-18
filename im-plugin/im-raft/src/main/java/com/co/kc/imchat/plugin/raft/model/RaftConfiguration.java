package com.co.kc.imchat.plugin.raft.model;

import java.util.List;
import java.util.Objects;

/** JRaft 成员配置目标，统一端点顺序并保证角色集合互斥。 */
public record RaftConfiguration(
        /* 投票成员端点集合。 */
        List<String> voters,
        /* learner 成员端点集合。 */
        List<String> learners) {
    /** 复制并校验配置目标，拒绝空 voter 和跨角色重复端点。 */
    public RaftConfiguration {
        voters = normalize(voters);
        learners = normalize(learners);
        if (voters.isEmpty() || voters.stream().anyMatch(learners::contains)) {
            throw new IllegalArgumentException("voters must be nonempty and disjoint from learners");
        }
    }

    private static List<String> normalize(List<String> endpoints) {
        // 先校验端点，再去重排序，使配置摘要不受输入集合顺序影响。
        return Objects.requireNonNull(endpoints, "endpoints").stream().map(endpoint -> {
            Objects.requireNonNull(endpoint, "endpoint");
            if (endpoint.isBlank()) {
                throw new IllegalArgumentException("Raft endpoint must not be blank");
            }
            return endpoint;
        }).distinct().sorted().toList();
    }
}
