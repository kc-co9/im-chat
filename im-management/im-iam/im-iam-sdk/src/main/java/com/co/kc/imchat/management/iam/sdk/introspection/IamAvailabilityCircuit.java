package com.co.kc.imchat.management.iam.sdk.introspection;

import java.time.Duration;
import java.time.Instant;

/** 连续三次可用性失败后打开，每五秒仅允许一次恢复探测。 */
public class IamAvailabilityCircuit {
    private static final int FAILURE_THRESHOLD = 3;
    private static final Duration PROBE_INTERVAL = Duration.ofSeconds(5);

    private int consecutiveFailures;
    private Instant nextProbeAt;
    private boolean probeInProgress;

    public synchronized boolean allowRequest(Instant now) {
        if (nextProbeAt == null) {
            return true;
        }
        if (now.isBefore(nextProbeAt) || probeInProgress) {
            return false;
        }
        probeInProgress = true;
        return true;
    }

    public synchronized void success() {
        consecutiveFailures = 0;
        nextProbeAt = null;
        probeInProgress = false;
    }

    public synchronized void availabilityFailure(Instant now) {
        consecutiveFailures++;
        probeInProgress = false;
        if (consecutiveFailures >= FAILURE_THRESHOLD) {
            nextProbeAt = now.plus(PROBE_INTERVAL);
        }
    }

    public synchronized void rejected() {
        probeInProgress = false;
    }
}
