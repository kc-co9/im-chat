package com.co.kc.imchat.service.account.infrastructure.mybatis.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.plugin.datasource.dao.BaseMybatisService;
import com.co.kc.imchat.service.account.infrastructure.mybatis.entity.DbUser;
import com.co.kc.imchat.service.account.infrastructure.mybatis.enums.DbUserStatus;
import com.co.kc.imchat.service.account.infrastructure.mybatis.mapper.DbUserMapper;
import com.co.kc.imchat.service.account.infrastructure.mybatis.query.DbUserQueryCondition;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 用户表(DbUser)表服务接口
 *
 * @author kc
 * @since 2026-02-03 11:11:22
 */
public class DbUserService extends BaseMybatisService<DbUserMapper, DbUser> {

    public Optional<DbUser> getByUserId(Long userId) {
        return getFirst(this.getQueryWrapper().eq(DbUser::getUserId, userId));
    }

    public List<DbUser> getListByUserIds(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyList();
        }
        return list(this.getQueryWrapper().in(DbUser::getUserId, userIds));
    }

    public Optional<DbUser> getByEmail(String email) {
        return getFirst(this.getQueryWrapper().eq(DbUser::getEmail, email));
    }

    /**
     * 分页查询未删除用户，使用 MyBatis-Plus 逻辑删除规则自动排除删除记录。
     *
     * @param paging    分页边界
     * @param condition 用户查询条件
     * @return 未删除用户分页结果
     */
    public IPage<DbUser> pageUsers(Paging paging, DbUserQueryCondition condition) {
        Page<DbUser> page = new Page<>(paging.pageNo(), paging.pageSize());
        Optional<DbUserStatus> status = condition.status();
        return this.page(page, getQueryWrapper()
                .select(DbUser::getId,
                        DbUser::getUserId,
                        DbUser::getUsername,
                        DbUser::getEmail,
                        DbUser::getStatus,
                        DbUser::getCreateTime,
                        DbUser::getUpdateTime,
                        DbUser::getIsDeleted)
                .eq(condition.userId().isPresent(), DbUser::getUserId, condition.userId().orElse(null))
                .like(condition.username().isPresent(), DbUser::getUsername, condition.username().orElse(null))
                .eq(condition.email().isPresent(), DbUser::getEmail, condition.email().orElse(null))
                .eq(status.isPresent(), DbUser::getStatus, status.orElse(null))
                .orderByDesc(DbUser::getUserId));
    }

    /**
     * 分页查询管理端用户，使用显式 SQL 读取全部逻辑删除状态。
     *
     * @param paging    分页边界
     * @param condition 管理端查询条件
     * @return 用户分页结果
     */
    public IPage<DbUser> pageRawUsers(
            Paging paging,
            DbUserQueryCondition condition
    ) {
        Page<DbUser> page = new Page<>(paging.pageNo(), paging.pageSize());
        return getBaseMapper().selectRawUsers(page, condition);
    }

    /**
     * 按用户 ID 查询管理端用户，包括已逻辑删除的用户。
     *
     * @param userId 用户 ID
     * @return 用户记录
     */
    public Optional<DbUser> getRawByUserId(Long userId) {
        return Optional.ofNullable(getBaseMapper().selectRawByUserId(userId));
    }

    public void removeByUserId(Long userId) {
        this.remove(this.getQueryWrapper().eq(DbUser::getUserId, userId));
    }

}
