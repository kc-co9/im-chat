package com.co.kc.imchat.plugin.gossip.model;

/**
 * Gossip 同步的实体类型。
 */
public enum GossipEntityType {
    BROKER,
    GATEWAY,
    CONNECTION;

    public boolean matches(GossipDeltaEntry entry) {
        return this == entry.entityType();
    }
}
