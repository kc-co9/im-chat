package com.co.kc.imchat.domain.user;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    /**
     * 根据userId查找用户
     *
     * @param userId 用户ID
     * @return 用户
     */
    Optional<User> find(UserId userId);

    List<User> find(List<UserId> userId);

    /**
     * 根据邮箱查找用户
     *
     * @param email 邮箱
     * @return 用户
     */
    Optional<User> find(UserEmail email);

    /**
     * 保存用户
     *
     * @param user 用户
     */
    void save(User user);

    /**
     * 移除用户
     *
     * @param user 用户
     */
    void remove(User user);

    /**
     * 判断用户是否存在
     *
     * @param email 邮箱
     * @return 用户是否存在
     */
    boolean contain(UserEmail email);
}
