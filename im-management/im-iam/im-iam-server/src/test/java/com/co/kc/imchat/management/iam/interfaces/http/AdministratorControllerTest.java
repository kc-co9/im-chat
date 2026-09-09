package com.co.kc.imchat.management.iam.interfaces.http;

import com.co.kc.imchat.management.iam.application.AdministratorAppService;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorSessionsRevokeCmd;
import com.co.kc.imchat.management.iam.model.io.AdministratorIdRequest;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdministratorControllerTest {

    @Test
    void revokesAllSessionsThroughTheAdministratorUseCase() {
        AdministratorAppService appService = mock(AdministratorAppService.class);
        AdministratorController controller = new AdministratorController(appService);

        controller.revokeAdministratorSessions(new AdministratorIdRequest(1001L));

        verify(appService).revokeSessions(new AdministratorSessionsRevokeCmd(1001L));
    }
}
