package com.co.kc.imchat.service.account.infrastructure.domain.repository;

import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.service.account.domain.user.model.User;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.account.domain.user.repository.UserRepository;
import com.co.kc.imchat.service.account.infrastructure.mybatis.entity.DbUser;
import com.co.kc.imchat.service.account.infrastructure.mybatis.service.DbUserService;
import com.co.kc.imchat.service.account.transformer.db.UserDbTransformer;
import com.co.kc.imchat.service.account.transformer.domain.UserDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class MysqlUserRepository implements UserRepository {
    private final DbUserService dbUserService;

    @Override
    public Optional<User> find(UserId userId) {
        return dbUserService.getByUserId(userId.value())
                .map(UserDomainTransformer.INSTANCE::userFrom);
    }

    @Override
    public List<User> find(List<UserId> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyList();
        }
        List<Long> userIdList = FunctionUtils.mappingList(userIds, UserId::value);
        List<DbUser> userList = dbUserService.getListByUserIds(userIdList);
        return UserDomainTransformer.INSTANCE.userListFrom(userList);
    }

    @Override
    public Optional<User> find(UserEmail email) {
        return dbUserService.getByEmail(email.value())
                .map(UserDomainTransformer.INSTANCE::userFrom);
    }

    @Override
    public void save(User user) {
        DbUser dbUser = UserDbTransformer.INSTANCE.dbUserFrom(user);
        boolean persisted = dbUserService.saveOrUpdate(dbUser);
        if (!persisted) {
            if (user.getPkId() != null) {
                throw new OptimisticLockingFailureException(
                        "User was modified concurrently: " + user.getId().value());
            }
            throw new DataAccessResourceFailureException(
                    "User was not inserted: " + user.getId().value());
        }
        if (persisted) {
            if (dbUser.getId() != null) {
                user.setPkId(dbUser.getId());
            }
            user.setRowVersion(dbUser.getVersion());
        }
    }

    @Override
    public void remove(User user) {
        dbUserService.removeByUserId(user.getId().value());
    }

    @Override
    public boolean contain(UserEmail email) {
        return dbUserService.isExist(dbUserService.getQueryWrapper()
                .select(DbUser::getId)
                .eq(DbUser::getEmail, email.value()));
    }
}
