package com.co.kc.imchat.service.social.adapter.account;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.account.facade.AccountService;
import com.co.kc.imchat.service.account.facade.dto.UserProfileDTO;
import com.co.kc.imchat.service.account.facade.params.UserProfilesGetParams;
import com.co.kc.imchat.service.social.domain.account.model.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountAdapterTest {

    @Test
    void batchProfileQueryMapsAccountContractToSocialModel() {
        AccountService accountService = mock(AccountService.class);
        UserProfilesGetParams params = new UserProfilesGetParams(List.of(42L, 43L));
        when(accountService.getUserProfiles(params)).thenReturn(List.of(
                new UserProfileDTO(42L, "first", "first@example.com"),
                new UserProfileDTO(43L, "second", "second@example.com")));
        AccountAdapter adapter = new AccountAdapter(accountService);

        Map<UserId, UserProfile> result = adapter.findUserProfiles(
                List.of(new UserId(42L), new UserId(43L)));

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry(new UserId(42L), new UserProfile(
                        new UserId(42L), "first", "first@example.com")),
                Map.entry(new UserId(43L), new UserProfile(
                        new UserId(43L), "second", "second@example.com"))));
        verify(accountService).getUserProfiles(params);
    }
}
