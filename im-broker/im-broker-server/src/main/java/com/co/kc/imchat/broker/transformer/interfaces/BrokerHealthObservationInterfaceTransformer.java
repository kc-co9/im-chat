package com.co.kc.imchat.broker.transformer.interfaces;

import com.co.kc.imchat.broker.model.cqrs.dto.BrokerHealthObservationDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerHealthReportDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerProbeDTO;
import com.co.kc.imchat.broker.model.cqrs.query.BrokerHealthReportQuery;
import com.co.kc.imchat.broker.model.cqrs.query.BrokerProbeQuery;
import com.co.kc.imchat.broker.sdk.model.params.BrokerHealthReportParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerProbeParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerHealthReportResult;
import com.co.kc.imchat.broker.sdk.model.result.BrokerHealthReportRow;
import com.co.kc.imchat.broker.sdk.model.result.BrokerProbeResult;
import com.co.kc.imchat.common.utils.AssertUtils;
import java.time.Duration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

/** 观察协议与应用模型在入口转换；状态枚举由生成映射转换。 */
@Mapper
public interface BrokerHealthObservationInterfaceTransformer {
    BrokerHealthObservationInterfaceTransformer INSTANCE = Mappers.getMapper(BrokerHealthObservationInterfaceTransformer.class);

    BrokerProbeQuery probeQueryFrom(BrokerProbeParams request);

    BrokerHealthReportQuery reportQueryFrom(BrokerHealthReportParams request);

    BrokerProbeResult probeResultFrom(BrokerProbeDTO dto);

    BrokerHealthReportResult reportResultFrom(BrokerHealthReportDTO dto);

    @Mapping(target = "observationAgeMs", source = "observationAge", qualifiedByName = "ceilAgeMs")
    @Mapping(target = "unreachableDurationMs", source = "unreachableDuration", qualifiedByName = "floorFailureMs")
    BrokerHealthReportRow reportRowFrom(BrokerHealthObservationDTO dto);

    @Named("ceilAgeMs")
    default Long ceilAgeMs(Duration duration) {
        AssertUtils.argTrue("observation age must be nonnegative", duration != null && !duration.isNegative());
        long floor = duration.toMillis();
        return duration.minusMillis(floor).isZero() ? floor : Math.addExact(floor, 1L);
    }

    @Named("floorFailureMs")
    default Long floorFailureMs(Duration duration) {
        AssertUtils.argTrue("failure duration must be nonnegative", duration != null && !duration.isNegative());
        return duration.toMillis();
    }
}
