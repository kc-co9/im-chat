package com.co.kc.imchat.service.social.facade.dto;

import java.util.List;

/**
 * 好友展示信息列表响应。
 *
 * @param friends 好友展示信息列表
 */
public record FriendDisplaysDTO(List<FriendDisplayDTO> friends) {
}
