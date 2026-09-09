package com.co.kc.imchat.service.account.transformer.db;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.service.account.domain.user.model.UserStatus;
import com.co.kc.imchat.service.account.domain.user.model.UserPassword;
import com.co.kc.imchat.service.account.domain.user.model.ManagedUser;
import com.co.kc.imchat.service.account.domain.user.model.UserQueryCondition;
import com.co.kc.imchat.service.account.infrastructure.mybatis.enums.DbUserStatus;
import com.co.kc.imchat.service.account.infrastructure.mybatis.query.DbUserQueryCondition;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserDbTransformerTest {

    @Test
    void mapsAdminStatusToPersistenceStatus() {
        assertThat(UserDbTransformer.INSTANCE.dbUserStatusFrom(UserStatus.BANNED))
                .isEqualTo(DbUserStatus.BANNED);
        assertThat(UserDbTransformer.INSTANCE.dbUserStatusFrom(null)).isNull();
    }

    @Test
    void mapsDomainQueryConditionToPersistenceValues() {
        UserQueryCondition source = new UserQueryCondition(
                Optional.of(new UserId(1001L)),
                Optional.of(new UserName("alice")),
                Optional.of(new UserEmail("alice@example.com")),
                Optional.of(UserStatus.BANNED));

        DbUserQueryCondition result = UserDbTransformer.INSTANCE
                .dbUserQueryConditionFrom(source);

        assertThat(result.userId()).contains(1001L);
        assertThat(result.username()).contains("alice");
        assertThat(result.email()).contains("alice@example.com");
        assertThat(result.status()).contains(DbUserStatus.BANNED);
    }

    @Test
    void mapsManagedUserPasswordForAggregatePersistence() {
        ManagedUser managedUser = new ManagedUser(
                new UserId(1001L), new UserName("alice"),
                new UserEmail("alice@example.com"), new UserPassword("encrypted-password"),
                UserStatus.NORMAL, false, Instant.EPOCH, Instant.EPOCH);
        managedUser.setPkId(9L);

        assertThat(UserDbTransformer.INSTANCE.dbUserFrom(managedUser).getPassword())
                .isEqualTo("encrypted-password");
    }
}
