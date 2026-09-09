package com.co.kc.imchat.common.exception;

import com.co.kc.imchat.common.constant.HttpErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RpcExceptionTest {

    @Test
    void exposesStableCodeAndSafeMessage() {
        RpcException exception = new RpcException(
                HttpErrorCode.NOT_FOUND.getCode(), "查找失败，请稍后重试");

        assertThat(exception.getCode()).isEqualTo(HttpErrorCode.NOT_FOUND.getCode());
        assertThat(exception).hasMessage("查找失败，请稍后重试");
    }

    @Test
    void rejectsMissingContractValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RpcException(0, "查找失败，请稍后重试"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RpcException(HttpErrorCode.NOT_FOUND.getCode(), " "));
    }
}
