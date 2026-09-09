package com.co.kc.imchat.service.account.infrastructure.mybatis.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.service.account.infrastructure.mybatis.entity.DbUser;
import com.co.kc.imchat.service.account.infrastructure.mybatis.enums.DbUserStatus;
import com.co.kc.imchat.service.account.infrastructure.mybatis.mapper.DbUserMapper;
import com.co.kc.imchat.service.account.infrastructure.mybatis.query.DbUserQueryCondition;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbUserServiceTest {

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                DbUser.class);
    }

    @Test
    void pageUsersKeepsTheLogicalDeleteAwareMybatisPlusQuery() {
        DbUserMapper mapper = mock(DbUserMapper.class);
        DbUserService service = service(mapper);
        DbUserQueryCondition condition = new DbUserQueryCondition(
                Optional.of(1001L), Optional.empty(), Optional.empty(),
                Optional.of(DbUserStatus.BANNED));
        IPage<DbUser> result = new Page<DbUser>(1, 20).setTotal(1);
        doReturn(result).when(mapper).selectPage(any(Page.class), any(Wrapper.class));

        assertThat(service.pageUsers(new Paging(1, 20), condition)).isSameAs(result);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<DbUser>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectPage(any(Page.class), wrapperCaptor.capture());
        @SuppressWarnings("unchecked")
        LambdaQueryWrapper<DbUser> wrapper = (LambdaQueryWrapper<DbUser>) wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("user_id", "status", "ORDER BY user_id DESC");
    }

    @Test
    void pageRawUsersUsesDedicatedMapperQuery() {
        DbUserMapper mapper = mock(DbUserMapper.class);
        DbUserService service = service(mapper);
        DbUserQueryCondition condition = new DbUserQueryCondition(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        IPage<DbUser> result = new Page<DbUser>(1, 20).setTotal(3);
        when(mapper.selectRawUsers(any(Page.class), eq(condition))).thenReturn(result);

        IPage<DbUser> actual = service.pageRawUsers(new Paging(1, 20), condition);

        assertThat(actual).isSameAs(result);
        verify(mapper).selectRawUsers(any(Page.class), eq(condition));
    }

    @Test
    void getAdminUserCanReturnLogicalDeletedRow() {
        DbUserMapper mapper = mock(DbUserMapper.class);
        DbUserService service = service(mapper);
        DbUser deleted = new DbUser();
        deleted.setUserId(1001L);
        deleted.setIsDeleted(9L);
        when(mapper.selectRawByUserId(1001L)).thenReturn(deleted);

        Optional<DbUser> result = service.getRawByUserId(1001L);

        assertThat(result).containsSame(deleted);
    }

    private static DbUserService service(DbUserMapper mapper) {
        DbUserService service = new DbUserService();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        return service;
    }
}
