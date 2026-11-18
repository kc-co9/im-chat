package com.co.kc.imchat.broker.sdk.cluster;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 按 revision 原子安装完整 BrokerClusterState，不允许同 revision 出现不同内容。
 */
public final class BrokerClusterStateCache {

    public enum InstallResult {
        INSTALLED,
        UNCHANGED,
        STALE
    }

    private final AtomicReference<BrokerClusterState> current = new AtomicReference<>();

    public Optional<BrokerClusterState> current() {
        return Optional.ofNullable(current.get());
    }

    public InstallResult install(BrokerClusterState candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate state must not be null");
        }
        while (true) {
            BrokerClusterState installed = current.get();
            InstallResult terminal = compare(installed, candidate);
            if (terminal != null) {
                return terminal;
            }
            if (current.compareAndSet(installed, candidate)) {
                return InstallResult.INSTALLED;
            }
        }
    }

    private InstallResult compare(BrokerClusterState installed,
                                  BrokerClusterState candidate) {
        if (installed == null) {
            return null;
        }
        if (candidate.revision() < installed.revision()) {
            return InstallResult.STALE;
        }
        if (candidate.revision() == installed.revision()) {
            if (!candidate.equals(installed)) {
                throw new IllegalStateException(
                        "same BrokerClusterState revision has different content");
            }
            return InstallResult.UNCHANGED;
        }
        if (installed.algorithm() != candidate.algorithm()) {
            throw new IllegalStateException("BrokerClusterState algorithm cannot change");
        }
        return null;
    }
}
