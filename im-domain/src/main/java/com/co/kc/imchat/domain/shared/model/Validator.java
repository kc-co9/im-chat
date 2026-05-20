package com.co.kc.imchat.domain.shared.model;

/**
 * 实体校验共享领域对象
 *
 * @author kc
 */
public interface Validator {

    /**
     * 校验方法
     */
    default void validate(){}

}
