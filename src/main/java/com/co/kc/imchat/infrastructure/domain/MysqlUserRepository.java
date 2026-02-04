package com.co.kc.imchat.infrastructure.domain;

import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.domain.user.User;
import com.co.kc.imchat.domain.user.UserEmail;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.domain.user.UserRepository;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbUser;
import com.co.kc.imchat.infrastructure.mybatis.service.DbUserService;
import com.co.kc.imchat.transformer.UserDomainTransformer;
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
