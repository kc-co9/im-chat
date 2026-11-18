package com.co.kc.imchat.broker.transformer.application;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthObservation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerHealthObservationDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerHealthReportDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerProbeDTO;
import com.co.kc.imchat.broker.model.cqrs.query.BrokerHealthReportQuery;
import com.co.kc.imchat.broker.model.cqrs.query.BrokerProbeQuery;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 观察领域事实到应用输出的声明式映射；保持 Duration 精度，不执行 SDK 舍入。
 */
@Mapper(imports = Duration.class)
public interface BrokerHealthObservationAppTransformer {
    BrokerHealthObservationAppTransformer INSTANCE = Mappers.getMapper(BrokerHealthObservationAppTransformer.class);

    @Mapping(target = "brokerId", source = "brokerId.value")
    BrokerProbeDTO probeFrom(BrokerProbeQuery query, BrokerId brokerId);

    @Mapping(target = "observerBrokerId", source = "observerBrokerId.value")
    @Mapping(target = "observations", source = "observations")
    BrokerHealthReportDTO healthReportFrom(BrokerHealthReportQuery query, BrokerId observerBrokerId, List<BrokerHealthObservationDTO> observations);

    @Mapping(target = "brokerId", expression = "java(observation.brokerId().value())")
    @Mapping(target = "status", expression = "java(observation.status())")
    @Mapping(target = "observationAge", expression = "java(Duration.between(observation.observedAt(), snapshotAt))")
    @Mapping(target = "unreachableDuration", expression = "java(observation.unreachableBeginAt() == null ? Duration.ZERO : Duration.between(observation.unreachableBeginAt(), observation.observedAt()))")
    BrokerHealthObservationDTO healthObservationFrom(BrokerHealthObservation observation, Instant snapshotAt);
}
