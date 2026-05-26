package com.co.kc.imchat.infrastructure.domain.repository;

import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.domain.user.model.User;
import com.co.kc.imchat.domain.user.model.UserEmail;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.domain.user.repository.UserRepository;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbUser;
import com.co.kc.imchat.infrastructure.mybatis.service.DbUserService;
import com.co.kc.imchat.infrastructure.transformer.db.UserDbTransformer;
import com.co.kc.imchat.infrastructure.transformer.domain.UserDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
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
        dbUserService.saveOrUpdate(dbUser);
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
