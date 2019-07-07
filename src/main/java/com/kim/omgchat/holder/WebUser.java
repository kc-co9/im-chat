package com.kim.omgchat.holder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.dozer.DozerBeanMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;

import static com.kim.omgchat.constant.RedisKeyConstant.generateOnlineUserKey;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/13 15:49
 */
@Data
public class WebUser {
    private Long userId;

    private String email;

    private String nickname;

    private String avatar;

    @Override
    public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "";
        }
    }


}
