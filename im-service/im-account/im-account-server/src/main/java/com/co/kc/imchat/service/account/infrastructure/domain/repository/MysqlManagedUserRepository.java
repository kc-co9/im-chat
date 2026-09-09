package com.co.kc.imchat.service.account.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.service.account.domain.user.model.UserQueryCondition;
import com.co.kc.imchat.service.account.domain.user.model.ManagedUser;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.service.account.domain.user.repository.ManagedUserRepository;
import com.co.kc.imchat.service.account.infrastructure.mybatis.entity.DbUser;
import com.co.kc.imchat.service.account.infrastructure.mybatis.query.DbUserQueryCondition;
import com.co.kc.imchat.service.account.infrastructure.mybatis.service.DbUserService;
import com.co.kc.imchat.service.account.transformer.db.UserDbTransformer;
import com.co.kc.imchat.service.account.transformer.domain.ManagedUserDomainTransformer;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@RequiredArgsConstructor
public class MysqlManagedUserRepository implements ManagedUserRepository {
    private final DbUserService dbUserService;

    @Override
    public Optional<ManagedUser> find(UserId userId) {
        return dbUserService.getRawByUserId(userId.value())
                .map(ManagedUserDomainTransformer.INSTANCE::managedUserFrom);
    }

    @Override
    public PagingResult<ManagedUser> page(Paging paging, UserQueryCondition condition) {
        DbUserQueryCondition dbCondition = UserDbTransformer.INSTANCE.dbUserQueryConditionFrom(condition);
        IPage<DbUser> page = dbUserService.pageRawUsers(paging, dbCondition);
        return PagingResult.<DbUser>newBuilder()
                .paging(paging)
                .records(page.getRecords())
                .total(page.getTotal())
                .build()
                .map(ManagedUserDomainTransformer.INSTANCE::managedUserFrom);
    }

    @Override
    public void save(ManagedUser user) {
        dbUserService.saveOrUpdate(UserDbTransformer.INSTANCE.dbUserFrom(user));
    }

    @Override
    public void remove(ManagedUser user) {
        dbUserService.removeByUserId(user.getUserId().value());
    }

    @Override
    public boolean contain(UserEmail email) {
        return dbUserService.isExist(dbUserService.getQueryWrapper()
                .select(DbUser::getId)
                .eq(DbUser::getEmail, email.value()));
    }
}
