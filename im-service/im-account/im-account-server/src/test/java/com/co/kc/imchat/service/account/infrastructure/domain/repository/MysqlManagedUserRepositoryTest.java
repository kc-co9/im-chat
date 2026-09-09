package com.co.kc.imchat.service.account.infrastructure.domain.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.service.account.domain.user.model.UserQueryCondition;
import com.co.kc.imchat.service.account.domain.user.model.ManagedUser;
import com.co.kc.imchat.service.account.domain.user.model.UserStatus;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.service.account.domain.user.model.UserPassword;
import com.co.kc.imchat.service.account.infrastructure.mybatis.entity.DbUser;
import com.co.kc.imchat.service.account.infrastructure.mybatis.enums.DbUserStatus;
import com.co.kc.imchat.service.account.infrastructure.mybatis.query.DbUserQueryCondition;
import com.co.kc.imchat.service.account.infrastructure.mybatis.service.DbUserService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlManagedUserRepositoryTest {

    @Test
    void deletedPersistenceRowRetainsBusinessStatusAndSetsDeletedFact() {
        DbUserService service = mock(DbUserService.class);
        DbUser row = row(DbUserStatus.BANNED, 9L);
        when(service.getRawByUserId(1001L)).thenReturn(Optional.of(row));
        MysqlManagedUserRepository repository = new MysqlManagedUserRepository(service);

        assertThat(repository.find(new UserId(1001L)))
                .get()
                .satisfies(user -> {
                    assertThat(user.getStatus()).isEqualTo(UserStatus.BANNED);
                    assertThat(user.getDeleted()).isTrue();
                    assertThat(user.getCreatedAt()).isEqualTo(Instant.parse("2026-08-20T00:00:00Z"));
                });
    }

    @Test
    void pageConvertsDomainCriteriaAndPersistenceRows() {
        DbUserService service = mock(DbUserService.class);
        Paging paging = new Paging(2, 20);
        UserQueryCondition condition = new UserQueryCondition(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(UserStatus.BANNED));
        DbUserQueryCondition dbCondition = new DbUserQueryCondition(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(DbUserStatus.BANNED));
        Page<DbUser> dbPage = new Page<DbUser>(2, 20).setRecords(List.of(row(DbUserStatus.BANNED, 0L)));
        dbPage.setTotal(21L);
        when(service.pageRawUsers(paging, dbCondition)).thenReturn(dbPage);
        MysqlManagedUserRepository repository = new MysqlManagedUserRepository(service);

        PagingResult<ManagedUser> result = repository.page(paging, condition);

        assertThat(result.total()).isEqualTo(21L);
        assertThat(result.paging()).isEqualTo(new Paging(2, 20));
        assertThat(result.records()).singleElement()
                .satisfies(user -> assertThat(user.getStatus()).isEqualTo(UserStatus.BANNED));
        verify(service).pageRawUsers(paging, dbCondition);
    }

    @Test
    void savePreservesPersistenceIdentityAndManagedState() {
        DbUserService service = mock(DbUserService.class);
        MysqlManagedUserRepository repository = new MysqlManagedUserRepository(service);
        ManagedUser managedUser = managedUser(
                new UserName("alice-new"),
                new UserEmail("new@example.com"),
                UserStatus.BANNED);

        repository.save(managedUser);

        verify(service).saveOrUpdate(org.mockito.ArgumentMatchers.argThat(row ->
                row.getId().equals(9L)
                        && row.getUserId().equals(1001L)
                        && row.getUsername().equals("alice-new")
                        && row.getEmail().equals("new@example.com")
                        && row.getPassword().equals("encrypted-password")
                        && row.getStatus() == DbUserStatus.BANNED));
    }

    private static DbUser row(DbUserStatus status, long deleted) {
        DbUser row = new DbUser();
        row.setId(9L);
        row.setUserId(1001L);
        row.setUsername("alice");
        row.setEmail("alice@example.com");
        row.setPassword("encrypted-password");
        row.setStatus(status);
        row.setCreateTime(Instant.parse("2026-08-20T00:00:00Z"));
        row.setUpdateTime(Instant.parse("2026-08-24T00:00:00Z"));
        row.setIsDeleted(deleted);
        return row;
    }

    private static ManagedUser managedUser(UserName username, UserEmail email, UserStatus status) {
        ManagedUser managedUser = new ManagedUser(
                new UserId(1001L),
                username,
                email,
                new UserPassword("encrypted-password"),
                status,
                false,
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-24T00:00:00Z"));
        managedUser.setPkId(9L);
        return managedUser;
    }
}
