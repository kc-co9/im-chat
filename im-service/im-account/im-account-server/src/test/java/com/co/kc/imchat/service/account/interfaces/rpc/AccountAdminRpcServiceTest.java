package com.co.kc.imchat.service.account.interfaces.rpc;

import com.co.kc.imchat.service.account.admin.facade.params.UserDeleteParams;
import com.co.kc.imchat.service.account.application.ManagedUserAppService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccountAdminRpcServiceTest {

    @Test
    void deleteDelegatesAsScalarCqrsCommand() {
        ManagedUserAppService appService = mock(ManagedUserAppService.class);
        AccountAdminRpcService service = new AccountAdminRpcService(appService);

        service.deleteUser(new UserDeleteParams(1001L));

        verify(appService).delete(org.mockito.ArgumentMatchers.argThat(
                command -> command.userId().equals(1001L)));
    }
}
