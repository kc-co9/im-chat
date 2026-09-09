package com.co.kc.imchat.service.account.interfaces.rpc;

import com.co.kc.imchat.service.account.application.SessionAppService;
import com.co.kc.imchat.service.account.application.UserAppService;
import com.co.kc.imchat.service.account.facade.dto.UserProfileDTO;
import com.co.kc.imchat.service.account.facade.params.UserProfilesGetParams;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountRpcServiceTest {

    @Test
    void batchProfileQueryDelegatesToUserApplicationService() {
        UserAppService userAppService = mock(UserAppService.class);
        UserProfilesGetParams params = new UserProfilesGetParams(List.of(42L, 43L));
        List<UserProfileDTO> profiles = List.of(
                new UserProfileDTO(42L, "first", "first@example.com"),
                new UserProfileDTO(43L, "second", "second@example.com"));
        when(userAppService.getUserProfiles(params)).thenReturn(profiles);
        AccountRpcService service = new AccountRpcService(mock(SessionAppService.class), userAppService);

        List<UserProfileDTO> result = service.getUserProfiles(params);

        assertThat(result).isSameAs(profiles);
        verify(userAppService).getUserProfiles(params);
    }
}
