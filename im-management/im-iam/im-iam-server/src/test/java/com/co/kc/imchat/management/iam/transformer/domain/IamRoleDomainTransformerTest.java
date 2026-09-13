package com.co.kc.imchat.management.iam.transformer.domain;

import com.co.kc.imchat.management.iam.domain.authorization.model.IamPermissionCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamInternalRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamInternalRoleStatus;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamInternalRoleType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IamRoleDomainTransformerTest {

    @Test
    void mapsPersistenceMetadataAndPermissionCodesInBothDirections() {
        DbIamInternalRole row = new DbIamInternalRole();
        row.setId(7L);
        row.setVersion(3L);
        row.setRoleId(70L);
        row.setCode("ADMIN");
        row.setName("Administrator");
        row.setType(DbIamInternalRoleType.SUPER_ADMIN);
        row.setStatus(DbIamInternalRoleStatus.ACTIVE);
        row.setPermissions("user:write,user:read");

        IamRole role = IamRoleDomainTransformer.INSTANCE.roleFrom(row);
        DbIamInternalRole mappedBack = IamRoleDomainTransformer.INSTANCE.dbRoleFrom(role);

        assertThat(role.getPkId()).isEqualTo(7L);
        assertThat(role.getRowVersion()).isEqualTo(3L);
        assertThat(role.getPermissions())
                .extracting(IamPermissionCode::value)
                .containsExactlyInAnyOrder("user:read", "user:write");
        assertThat(mappedBack.getId()).isEqualTo(7L);
        assertThat(mappedBack.getVersion()).isEqualTo(3L);
        assertThat(mappedBack.getPermissions()).isEqualTo("user:read,user:write");
    }
}
