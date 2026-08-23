package com.co.kc.imchat.service.message.adapter;

import com.co.kc.imchat.service.message.adapter.social.SocialAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteAdapterConstructionTest {

    @Test
    void socialAdapterRequiresRemoteServiceAtConstructionTime() {
        assertThat(SocialAdapter.class.getDeclaredConstructors())
                .noneMatch(constructor -> constructor.getParameterCount() == 0);
        assertThatThrownBy(() -> new SocialAdapter(null))
                .isInstanceOf(NullPointerException.class);
    }
}
