package com.co.kc.imchat.service.account.transformer.domain;

import com.co.kc.imchat.service.account.domain.session.model.AccessCredential;
import com.co.kc.imchat.service.account.domain.session.model.DecodedSessionToken;
import com.co.kc.imchat.service.account.domain.session.model.RefreshFingerprint;
import com.co.kc.imchat.service.account.domain.session.model.RefreshCredential;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/** Session 领域模型转换器。 */
@Mapper
public interface SessionDomainTransformer {
    SessionDomainTransformer INSTANCE = Mappers.getMapper(SessionDomainTransformer.class);

    /**
     * 将已解码的会话令牌转换为 Access 凭证。
     *
     * @param token 已解码的会话令牌
    * @return Access 凭证
     */
    @Mapping(target = "sessionVersion", source = "version")
    AccessCredential accessCredentialFrom(DecodedSessionToken token);

    /**
     * 将已解码的会话令牌和 Refresh Token 指纹转换为 Refresh 凭证。
     *
     * @param token       已解码的会话令牌
     * @param fingerprint Refresh Token 指纹
     * @return Refresh 凭证
     */
    @Mapping(target = "userId", source = "token.userId")
    @Mapping(target = "sessionVersion", source = "token.version")
    @Mapping(target = "expiresAt", source = "token.expiresAt")
    @Mapping(target = "fingerprint", source = "fingerprint")
    RefreshCredential refreshCredentialFrom(DecodedSessionToken token, RefreshFingerprint fingerprint);
}
