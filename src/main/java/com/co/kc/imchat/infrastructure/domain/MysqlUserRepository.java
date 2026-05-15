package com.co.kc.imchat.infrastructure.domain;

import com.co.kc.imchat.support.utils.FunctionUtils;
import com.co.kc.imchat.domain.user.User;
import com.co.kc.imchat.domain.user.UserEmail;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.domain.user.UserRepository;
import com.co.kc.imchat.infrastructure.mybatis.entity.BaseEntity;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbUser;
import com.co.kc.imchat.infrastructure.mybatis.service.DbUserService;
import com.co.kc.imchat.transformer.db.UserDbTransformer;
import com.co.kc.imchat.transformer.domain.UserDomainTransformer;
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
        return dbUserService.getByUserId(userId.getValue())
                .map(UserDomainTransformer.INSTANCE::userFrom);
    }

    @Override
    public List<User> find(List<UserId> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyList();
        }
        List<Long> userIdList = FunctionUtils.mappingList(userIds, UserId::getValue);
        List<DbUser> userList = dbUserService.getListByUserIds(userIdList);
        return UserDomainTransformer.INSTANCE.userListFrom(userList);
    }

    @Override
    public Optional<User> find(UserEmail email) {
        return dbUserService.getByEmail(email.getValue())
                .map(UserDomainTransformer.INSTANCE::userFrom);
    }

    @Override
    public void save(User user) {
        DbUser dbUser = UserDbTransformer.INSTANCE.dbUserFrom(user);
        dbUserService.saveOrUpdate(dbUser);
    }

    @Override
    public void remove(User user) {
        dbUserService.removeByUserId(user.getId().getValue());
    }

    @Override
    public boolean contain(UserEmail email) {
        return dbUserService.isExist(dbUserService.getQueryWrapper()
                .select(BaseEntity::getId)
                .eq(DbUser::getEmail, email.getValue()));
    }
}
