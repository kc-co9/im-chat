package com.co.kc.imchat.management.iam.domain.application.model;

import com.co.kc.imchat.common.domain.shared.model.Identification;
import com.co.kc.imchat.common.utils.AssertUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

/** 接入 IAM 的业务应用聚合根。 */
@Getter
@EqualsAndHashCode(callSuper = false)
public class Application extends Identification implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private AppId appId;
    private AppKey appKey;
    private AppName name;
    private AppStatus status;

    public Application(AppId appId, AppKey appKey, AppName name, AppStatus status) {
        this.appId = appId;
        this.appKey = appKey;
        this.name = name;
        this.status = status;
        validate();
    }

    private void validate() {
        AssertUtils.allDomainPropNotNull(
                "oauth app required properties must not be null",
                appId, appKey, name, status);
    }

    public void rename(AppName name) {
        AssertUtils.domainPropNotNull("application name must not be null", name);
        this.name = name;
    }

    public void changeStatus(AppStatus status) {
        AssertUtils.domainPropNotNull("application status must not be null", status);
        this.status = status;
    }

    /** 判断应用当前是否允许参与授权。 */
    public boolean isActive() {
        return status == AppStatus.ACTIVE;
    }

}
