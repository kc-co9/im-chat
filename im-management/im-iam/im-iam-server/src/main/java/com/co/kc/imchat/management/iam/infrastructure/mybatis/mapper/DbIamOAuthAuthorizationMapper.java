package com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamOAuthAuthorization;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
import java.time.Instant;

@Mapper
public interface DbIamOAuthAuthorizationMapper extends BaseMapper<DbIamOAuthAuthorization> {
    @Update("""
            UPDATE db_iam_authorization a
            JOIN db_iam_oauth_client c ON c.oauth_client_id = a.oauth_client_id AND c.is_deleted = 0
            SET a.status = 2, a.revoked_at = #{revokedAt}, a.version = a.version + 1
            WHERE c.audience_app_id = #{appId} AND a.status = 1 AND a.is_deleted = 0
              AND a.principal_type = 'ADMINISTRATOR'
            """)
    int revokeAudience(@Param("appId") Long appId, @Param("revokedAt") Instant revokedAt);
}
