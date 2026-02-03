package com.kim.omgchat.infrastructure.domain;

import com.kim.omgchat.common.utils.FunctionUtils;
import com.kim.omgchat.domain.user.User;
import com.kim.omgchat.domain.user.UserEmail;
import com.kim.omgchat.domain.user.UserId;
import com.kim.omgchat.domain.user.UserRepository;
import com.kim.omgchat.infrastructure.mybatis.entity.DbUser;
import com.kim.omgchat.infrastructure.mybatis.service.DbUserService;
import com.kim.omgchat.transformer.UserDomainTransformer;
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
    public User find(UserId userId) {
        Optional<DbUser> user = dbUserService.getByUserId(userId.getValue());
        return user.map(UserDomainTransformer.INSTANCE::userFrom).orElse(null);
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
    public User find(UserEmail email) {
        Optional<DbUser> user = dbUserService.getByEmail(email.getValue());
        return user.map(UserDomainTransformer.INSTANCE::userFrom).orElse(null);
    }

    @Override
    public void save(User user) {

    }

    @Override
    public void remove(User user) {

    }
}
