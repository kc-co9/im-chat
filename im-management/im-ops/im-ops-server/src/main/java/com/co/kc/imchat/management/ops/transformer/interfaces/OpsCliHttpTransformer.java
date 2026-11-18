package com.co.kc.imchat.management.ops.transformer.interfaces;

import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerOperationDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerClusterStateDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.SourcedValueDTO;
import com.co.kc.imchat.management.ops.model.io.OpsCliClusterResponse;
import com.co.kc.imchat.management.ops.model.io.OpsCliOperationResponse;
import com.co.kc.imchat.management.ops.model.io.OpsCliSubmissionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

/** CLI 查询响应的声明式边界转换。 */
@Mapper
public interface OpsCliHttpTransformer {
    /** MapStruct 生成的无状态转换器实例。 */
    OpsCliHttpTransformer INSTANCE = Mappers.getMapper(OpsCliHttpTransformer.class);

    @Mapping(target = "createdAtMs", source = "createdAt")
    @Mapping(target = "completedAtMs", source = "completedAt")
    /** 将 operation 查询投影转换为 CLI 响应，并把时间转换为毫秒。 */
    OpsCliOperationResponse operationResponseFrom(BrokerOperationDTO.Query query);

    /** 将应用层提交结果转换为 CLI 窄响应。 */
    OpsCliSubmissionResponse submissionResponseFrom(BrokerOperationDTO result);

    /** 将带来源的集群运行投影转换为 CLI 响应。 */
    @Mapping(target = "revision", source = "value.revision")
    @Mapping(target = "epoch", source = "value.epoch")
    @Mapping(target = "algorithm", source = "value.algorithm")
    @Mapping(target = "brokers", source = "value.brokers")
    OpsCliClusterResponse clusterResponseFrom(SourcedValueDTO<BrokerClusterStateDTO> state);

    default Long epochMilliFrom(Instant instant) {
        // HTTP 边界统一使用毫秒时间戳，空值保留为空。
        return instant == null ? null : instant.toEpochMilli();
    }
}
