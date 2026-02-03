package com.kim.omgchat.support;

import com.kim.omgchat.model.cqrs.dto.user.TokenDTO;

public interface TokenService {
    /**
     * 生成TOKEN
     *
     * @param tokenDTO 生成TOKEN的DTO
     * @return 返回Token
     */
    String create(TokenDTO tokenDTO);

    /**
     * 解析TOKEN
     *
     * @param token 生成的TOKEN
     * @return 返回TOKEN信息
     */
    TokenDTO parse(String token);
}
