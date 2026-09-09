package com.co.kc.imchat.management.iam.interfaces.http;

import com.co.kc.imchat.management.iam.model.io.IamPrincipalResponse;
import com.co.kc.imchat.management.iam.support.security.IamAdministratorContext;
import com.co.kc.imchat.management.iam.support.security.IamAdministratorPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IAM 自身管理页面的当前认证主体接口。
 */
@RestController
@RequestMapping("/api/iam")
public class IamPrincipalController {

    @GetMapping("/me")
    public IamPrincipalResponse principal() {
        IamAdministratorPrincipal principal = IamAdministratorContext.get();
        return new IamPrincipalResponse(
                principal.administratorId(),
                principal.authorities());
    }
}
