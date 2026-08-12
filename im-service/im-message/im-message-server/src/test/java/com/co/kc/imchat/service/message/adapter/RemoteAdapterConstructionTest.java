package com.co.kc.imchat.service.message.adapter;

import com.co.kc.imchat.service.message.adapter.account.AccountAdapter;
import com.co.kc.imchat.service.message.adapter.social.SocialAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteAdapterConstructionTest {

    @Test
    void accountAdapterRequiresRemoteServicesAtConstructionTime() {
        assertThat(AccountAdapter.class.getDeclaredConstructors())
                .noneMatch(constructor -> constructor.getParameterCount() == 0);
        assertThatThrownBy(() -> new AccountAdapter(null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void socialAdapterRequiresRemoteServiceAtConstructionTime() {
        assertThat(SocialAdapter.class.getDeclaredConstructors())
                .noneMatch(constructor -> constructor.getParameterCount() == 0);
        assertThatThrownBy(() -> new SocialAdapter(null))
                .isInstanceOf(NullPointerException.class);
    }
}
