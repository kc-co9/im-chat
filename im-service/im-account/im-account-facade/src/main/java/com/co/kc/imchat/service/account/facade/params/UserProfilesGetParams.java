package com.co.kc.imchat.service.account.facade.params;

import java.io.Serializable;
import java.util.List;

/**
 * 批量查询用户基础资料请求。
 *
 * @param userIds 用户 ID 列表
 */
public record UserProfilesGetParams(List<Long> userIds) implements Serializable {
}
