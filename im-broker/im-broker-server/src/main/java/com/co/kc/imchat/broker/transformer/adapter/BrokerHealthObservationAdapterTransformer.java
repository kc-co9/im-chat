package com.co.kc.imchat.broker.transformer.adapter;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthObservation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.sdk.enums.BrokerHealthStatus;
import com.co.kc.imchat.broker.sdk.model.result.BrokerHealthReportRow;
import java.time.Duration;
import java.time.Instant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 对端观察契约转换；字段映射生成，报告内容校验仍由 Adapter 执行。
 */
@Mapper(imports = {Duration.class, BrokerId.class, BrokerHealthStatus.class})
public interface BrokerHealthObservationAdapterTransformer {
    BrokerHealthObservationAdapterTransformer INSTANCE = Mappers.getMapper(BrokerHealthObservationAdapterTransformer.class);

    @Mapping(target = "brokerId", expression = "java(new BrokerId(row.brokerId()))")
    @Mapping(target = "observerBrokerId", source = "observer")
    @Mapping(target = "observedAt", expression = "java(requestAt.minus(Duration.ofMillis(row.observationAgeMs())))")
    @Mapping(target = "unreachableBeginAt", expression = "java(row.status() == BrokerHealthStatus.UNHEALTHY ? requestAt.minus(Duration.ofMillis(row.observationAgeMs())).minus(Duration.ofMillis(row.unreachableDurationMs())) : null)")
    BrokerHealthObservation observationFrom(BrokerHealthReportRow row, BrokerId observer, Instant requestAt);
}
