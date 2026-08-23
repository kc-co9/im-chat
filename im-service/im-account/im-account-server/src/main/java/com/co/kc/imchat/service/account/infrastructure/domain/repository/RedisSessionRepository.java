package com.co.kc.imchat.service.account.infrastructure.domain.repository;

import com.alicp.jetcache.Cache;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.account.domain.session.model.Session;
import com.co.kc.imchat.service.account.domain.session.repository.SessionRepository;
import com.co.kc.imchat.service.account.model.cqrs.dto.SessionDTO;
import com.co.kc.imchat.service.account.transformer.domain.UserDomainTransformer;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@RequiredArgsConstructor
public class RedisSessionRepository implements SessionRepository {
    private final Cache<Long, SessionDTO> userSessionCache;

    @Override
    public void save(Session session) {
        SessionDTO sessionDTO = UserDomainTransformer.INSTANCE.sessionDtoFrom(session);
        userSessionCache.put(sessionDTO.getUserId(), sessionDTO);
    }

    @Override
    public Optional<Session> find(UserId userId) {
        SessionDTO sessionDTO = userSessionCache.get(userId.value());
        return Optional.ofNullable(UserDomainTransformer.INSTANCE.sessionFrom(sessionDTO));
    }
}
