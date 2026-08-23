package com.co.kc.imchat.plugin.metrics.support;

import com.co.kc.imchat.plugin.metrics.annotation.Observed;
import com.co.kc.imchat.plugin.metrics.annotation.IgnoreException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

/**
 * 统一创建和记录 {@link Observed} 使用的 Micrometer 指标。
 *
 * <p>业务代码通过本类记录调用结果和耗时，不直接依赖 {@link MeterRegistry}、
 * {@link Counter} 或 {@link Timer} 的组装细节。</p>
 */
@RequiredArgsConstructor
public class MetricsCollector {
    private final MeterRegistry meterRegistry;

    /**
     * 开始记录一次方法调用耗时。
     *
     * @return 计时采样
     */
    public Timer.Sample start() {
        return Timer.start(meterRegistry);
    }

    /**
     * 记录一次成功调用。
     *
    * @param name 指标基础名称
    * @param tags Micrometer 标签键值对
    */
    @IgnoreException(log = true)
    public void success(String name, String[] tags) {
        counter(name, tags, "success").increment();
    }

    /**
     * 记录一次失败调用。
     *
    * @param name 指标基础名称
    * @param tags Micrometer 标签键值对
    */
    @IgnoreException
    public void failure(String name, String[] tags) {
        counter(name, tags, "failure").increment();
    }

    /**
     * 停止计时并记录执行耗时。
     *
     * @param name 指标基础名称
    * @param tags Micrometer 标签键值对
    * @param sample 待停止的计时采样
    */
    @IgnoreException
    public void stop(String name, String[] tags, Timer.Sample sample) {
        sample.stop(meterRegistry.timer(name + ".duration", tags));
    }

    /**
     * 为结果状态追加 outcome 标签并获取计数器。
     */
    private Counter counter(String name, String[] tags, String outcome) {
        String[] metricTags = new String[tags.length + 2];
        System.arraycopy(tags, 0, metricTags, 0, tags.length);
        metricTags[tags.length] = "outcome";
        metricTags[tags.length + 1] = outcome;
        return meterRegistry.counter(name, metricTags);
    }
}
