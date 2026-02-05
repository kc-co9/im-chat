package com.co.kc.imchat.infrastructure.domain;

import com.alicp.jetcache.Cache;
import com.co.kc.imchat.domain.session.Session;
import com.co.kc.imchat.domain.session.SessionRepository;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.model.cqrs.dto.user.SessionDTO;
import com.co.kc.imchat.transformer.application.UserAppTransformer;
import com.co.kc.imchat.transformer.domain.UserDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisSessionRepository implements SessionRepository {
    private final Cache<Long, SessionDTO> userSessionCache;

    @Override
    public void save(Session session) {
        SessionDTO sessionDTO = UserAppTransformer.INSTANCE.sessionDtoFrom(session);
        userSessionCache.put(sessionDTO.getUserId(), sessionDTO);
    }

    @Override
    public Session find(UserId userId) {
        SessionDTO sessionDTO = userSessionCache.get(userId.getValue());
        return UserDomainTransformer.INSTANCE.sessionFrom(sessionDTO);
    }
}
