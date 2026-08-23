package com.co.kc.imchat.service.account.domain.session.model;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.common.utils.AssertUtils;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

/**
 * 聚合根：用户在线会话。
 */
@Getter
public class Session {
    private final UserId userId;

    private SessionStatus status;
    private Instant signInTime;
    private Instant signOutTime;
    private SessionVersion sessionVersion;
    private RefreshFingerprint refreshFingerprint;
    private Instant refreshTokenExpiresAt;

    public Session(UserId userId) {
        AssertUtils.domainPropNotNull("userId must not be null", userId);
        this.userId = userId;
    }

    /**
     * 创建用于重建持久化会话的 Builder。
     *
     * @return 会话重建 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 建立新登录会话，并返回被替换的在线会话版本。
     */
    public SessionVersion signIn(
            SessionVersion newVersion,
            RefreshFingerprint fingerprint,
            Instant refreshExpiresAt,
            Instant signedInAt
    ) {
        AssertUtils.argNotNull("version must not be null", newVersion);
        AssertUtils.argNotNull("refreshFingerprint must not be null", fingerprint);
        AssertUtils.argNotNull("refreshTokenExpiresAt must not be null", refreshExpiresAt);
        AssertUtils.argNotNull("signedInAt must not be null", signedInAt);

        SessionVersion oldVersion = matchesVersion(sessionVersion) ? sessionVersion : null;
        status = SessionStatus.ONLINE;
        signInTime = signedInAt;
        signOutTime = null;
        sessionVersion = newVersion;
        refreshFingerprint = fingerprint;
        refreshTokenExpiresAt = refreshExpiresAt;
        return oldVersion;
    }

    public void signOut(Instant signedOutAt) {
        AssertUtils.argNotNull("signedOutAt must not be null", signedOutAt);
        status = SessionStatus.OFFLINE;
        signInTime = null;
        signOutTime = signedOutAt;
        refreshFingerprint = null;
        refreshTokenExpiresAt = null;
    }

    public boolean isSignIn() {
        return status == SessionStatus.ONLINE;
    }

    public boolean matchesVersion(SessionVersion expectedVersion) {
        return isSignIn() && sessionVersion != null && sessionVersion.equals(expectedVersion);
    }

    public void rotateCredential(
            SessionVersion version,
            RefreshFingerprint fingerprint,
            RefreshFingerprint newFingerprint,
            Instant refreshExpiresAt,
            Instant rotatedAt
    ) {
        AssertUtils.argNotNull("newFingerprint must not be null", newFingerprint);
        AssertUtils.argNotNull("refreshExpiresAt must not be null", refreshExpiresAt);
        AssertUtils.argNotNull("rotatedAt must not be null", rotatedAt);
        if (!canRotateCredential(version, fingerprint, rotatedAt)) {
            throw new AuthException("用户认证失败");
        }
        refreshFingerprint = newFingerprint;
        refreshTokenExpiresAt = refreshExpiresAt;
    }

    private boolean canRotateCredential(
            SessionVersion version,
            RefreshFingerprint fingerprint,
            Instant rotatedAt) {
        return matchesVersion(version)
                && Objects.equals(refreshFingerprint, fingerprint)
                && refreshTokenExpiresAt != null
                && refreshTokenExpiresAt.isAfter(rotatedAt);
    }

    /**
     * 用于从持久化状态重建会话的 Builder。
     */
    public static final class Builder {
        private UserId userId;
        private SessionStatus status;
        private Instant signInTime;
        private Instant signOutTime;
        private SessionVersion sessionVersion;
        private RefreshFingerprint refreshFingerprint;
        private Instant refreshTokenExpiresAt;

        public Builder userId(UserId userId) {
            this.userId = userId;
            return this;
        }

        public Builder status(SessionStatus status) {
            this.status = status;
            return this;
        }

        public Builder signInTime(Instant signInTime) {
            this.signInTime = signInTime;
            return this;
        }

        public Builder signOutTime(Instant signOutTime) {
            this.signOutTime = signOutTime;
            return this;
        }

        public Builder sessionVersion(SessionVersion sessionVersion) {
            this.sessionVersion = sessionVersion;
            return this;
        }

        public Builder refreshFingerprint(RefreshFingerprint refreshFingerprint) {
            this.refreshFingerprint = refreshFingerprint;
            return this;
        }

        public Builder refreshTokenExpiresAt(Instant refreshTokenExpiresAt) {
            this.refreshTokenExpiresAt = refreshTokenExpiresAt;
            return this;
        }

        public Session build() {
            Session session = new Session(userId);
            session.status = status;
            session.signInTime = signInTime;
            session.signOutTime = signOutTime;
            session.sessionVersion = sessionVersion;
            session.refreshFingerprint = refreshFingerprint;
            session.refreshTokenExpiresAt = refreshTokenExpiresAt;
            return session;
        }
    }

}
