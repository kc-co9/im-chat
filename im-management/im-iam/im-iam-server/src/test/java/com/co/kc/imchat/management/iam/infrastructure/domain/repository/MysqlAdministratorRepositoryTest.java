package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorEmail;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorPassword;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorStatus;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorUsername;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamAdministrator;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamAdministratorStatus;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamAdministratorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class MysqlAdministratorRepositoryTest {

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                DbIamAdministrator.class);
    }

    @Test
    void findsByUsernameOrEmailWithoutExposingPersistenceEntity() {
        DbIamAdministratorService service = mock(DbIamAdministratorService.class);
        when(service.getOne(any(Wrapper.class), any(Boolean.class))).thenReturn(dbAdministrator());
        MysqlAdministratorRepository repository = new MysqlAdministratorRepository(service);

        Optional<Administrator> result = repository.find(
                new AdministratorEmail("admin@example.com"));

        assertThat(result).isPresent();
        Administrator administrator = result.orElseThrow();
        assertThat(administrator.getId()).isEqualTo(new AdministratorId(1L));
        assertThat(administrator.getUsername()).isEqualTo(new AdministratorUsername("admin"));
        assertThat(administrator.getEmail()).isEqualTo(new AdministratorEmail("admin@example.com"));
        assertThat(administrator.getPassword()).isEqualTo(new AdministratorPassword("bcrypt-hash"));
        assertThat(administrator.getStatus()).isEqualTo(AdministratorStatus.ACTIVE);
        assertThat(administrator.getPkId()).isEqualTo(1L);
        ArgumentCaptor<Wrapper<DbIamAdministrator>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).getOne(wrapper.capture(), any(Boolean.class));
        assertThat(wrapper.getValue().getSqlSegment())
                .contains("email")
                .doesNotContain("username", "OR");
    }

    @Test
    void insertsNewAdministratorAndUpdatesExistingAdministrator() {
        DbIamAdministratorService service = mock(DbIamAdministratorService.class);
        MysqlAdministratorRepository repository = new MysqlAdministratorRepository(service);
        Administrator administrator = activeAdministrator();

        doAnswer(invocation -> {
            DbIamAdministrator entity = invocation.getArgument(0);
            entity.setId(1L);
            return true;
        }).when(service).save(any(DbIamAdministrator.class));
        repository.save(administrator);
        assertThat(administrator.getPkId()).isEqualTo(1L);
        repository.save(administrator);

        verify(service).save(any(DbIamAdministrator.class));
        verify(service).updateById(any(DbIamAdministrator.class));
    }

    @Test
    void checksWhetherAnyAdministratorExistsWithoutCountingAllRows() {
        DbIamAdministratorService service = mock(DbIamAdministratorService.class);
        when(service.exists(any(Wrapper.class))).thenReturn(true);
        MysqlAdministratorRepository repository = new MysqlAdministratorRepository(service);

        assertThat(repository.exists()).isTrue();

        verify(service).exists(any(Wrapper.class));
    }

    private static Administrator activeAdministrator() {
        return Administrator.builder()
                .id(new AdministratorId(1L))
                .username(new AdministratorUsername("admin"))
                .email(new AdministratorEmail("admin@example.com"))
                .password(new AdministratorPassword("bcrypt-hash"))
                .status(AdministratorStatus.ACTIVE)
                .build();
    }

    private static DbIamAdministrator dbAdministrator() {
        DbIamAdministrator administrator = new DbIamAdministrator();
        administrator.setId(1L);
        administrator.setAdministratorId(1L);
        administrator.setUsername("admin");
        administrator.setEmail("admin@example.com");
        administrator.setPasswordHash("bcrypt-hash");
        administrator.setStatus(DbIamAdministratorStatus.ACTIVE);
        return administrator;
    }
}
